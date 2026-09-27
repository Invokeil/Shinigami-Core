package com.invokeil.shinigami.core.ai

import android.content.Context
import com.invokeil.shinigami.core.actions.ActionEngine
import com.invokeil.shinigami.core.actions.ActionResult
import com.invokeil.shinigami.core.data.db.ActionSource
import com.invokeil.shinigami.core.actions.ToolCall
import com.invokeil.shinigami.core.actions.ToolRegistry
import com.invokeil.shinigami.core.actions.summaryLine
import com.invokeil.shinigami.core.ai.ToolCallParser.Parsed
import com.invokeil.shinigami.core.data.PrefsRepository
import com.invokeil.shinigami.core.data.db.MessageEntity
import com.invokeil.shinigami.core.data.db.MessageRole
import com.invokeil.shinigami.core.data.db.UsageDao
import com.invokeil.shinigami.core.data.db.UsageRecordEntity
import com.invokeil.shinigami.core.offline.OfflineCommandParser
import com.invokeil.shinigami.core.provider.AiRequest
import com.invokeil.shinigami.core.provider.AiStreamEvent
import com.invokeil.shinigami.core.provider.ChatMessage
import com.invokeil.shinigami.core.provider.ProviderRepository
import com.invokeil.shinigami.core.util.AppError
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.firstOrNull

/**
 * The Agent Loop (MASTER SPEC §25, §103, §118).
 *
 * user text → offline parser (deterministic) → [or] provider stream →
 * JSON envelope → Action Engine (validate/policy/confirm/execute) →
 * results fed back (bounded rounds) → final reply.
 *
 * Everything is cancellable; emergency stop propagates through ActionEngine.
 */
@Singleton
class AgentOrchestrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val providers: ProviderRepository,
    private val registry: ToolRegistry,
    private val actionEngine: ActionEngine,
    private val offlineParser: OfflineCommandParser,
    private val prefs: PrefsRepository,
    private val usageDao: UsageDao,
    private val confirmationBridge: AgentConfirmationBridge,
) {

    sealed interface AgentEvent {
        data class Status(val label: String) : AgentEvent
        data class Delta(val text: String) : AgentEvent
        data class ActionStarted(val uid: String, val label: String, val toolId: String) : AgentEvent
        data class ActionFinished(
            val uid: String,
            val label: String,
            val ok: Boolean,
            val message: String,
        ) : AgentEvent

        data class NeedConfirmation(val uid: String, val summary: String) : AgentEvent
        data class Done(
            val reply: String,
            val executedActions: List<String>,
            val model: String?,
            val providerName: String?,
        ) : AgentEvent

        data class Failed(val error: AppError) : AgentEvent
    }

    /** Max provider rounds per user request (bounded agent loop, §25). */
    private val maxRounds = 3

    /** Max tool executions per user request. */
    private val maxActions = 8

    fun confirmationRequester() = object : com.invokeil.shinigami.core.actions.ConfirmationRequester {
        override suspend fun requestConfirmation(
            call: com.invokeil.shinigami.core.actions.ValidatedToolCall,
            summary: String,
        ): Boolean {
            val (pending, deferred) = confirmationBridge.open(call.def.id, summary)
            _uiConfirmation.tryEmit(pending)
            val approved = confirmationBridge.await(pending, deferred)
            _uiConfirmation.tryEmit(null)
            return approved
        }
    }

    /** Hot state mirroring confirmations so the UI can show the sheet. */
    private val _uiConfirmation = kotlinx.coroutines.flow.MutableStateFlow<AgentConfirmationBridge.Pending?>(null)
    val uiConfirmation: kotlinx.coroutines.flow.StateFlow<AgentConfirmationBridge.Pending?> = _uiConfirmation

    // ------------------------------------------------------------------ //

    suspend fun respond(
        userText: String,
        history: List<MessageEntity>,
        source: ActionSource = ActionSource.OFFLINE_PARSER,
        onEvent: suspend (AgentEvent) -> Unit,
    ) {
        // Phase 0 — deterministic offline understanding first (§21)
        val offline = try {
            offlineParser.parse(context, userText)
        } catch (t: Throwable) {
            null
        }
        if (offline != null) {
            onEvent(AgentEvent.Status("Offline command"))
            val executed = mutableListOf<String>()
            var finalReply = offline.directReply ?: offline.spoken
            for ((index, call) in offline.toolCalls.withIndex()) {
                if (index >= maxActions) break
                val def = registry.byId(call.tool)
                val uid = "off$index"
                onEvent(AgentEvent.ActionStarted(uid, def?.displayName ?: call.tool, call.tool))
                val result = actionEngine.execute(context, call, ActionSource.OFFLINE_PARSER, confirmationRequester())
                when (result) {
                    is ActionResult.Success -> {
                        executed.add(def?.displayName ?: call.tool)
                        if (offline.directReply == null) finalReply = result.message
                        onEvent(AgentEvent.ActionFinished(uid, def?.displayName ?: call.tool, true, result.message))
                    }

                    is ActionResult.Denied -> {
                        finalReply = result.humanMessage
                        onEvent(AgentEvent.ActionFinished(uid, def?.displayName ?: call.tool, false, result.humanMessage))
                    }

                    is ActionResult.Failure -> {
                        if (offline.directReply == null) finalReply = result.humanMessage
                        onEvent(AgentEvent.ActionFinished(uid, def?.displayName ?: call.tool, false, result.humanMessage))
                    }

                    is ActionResult.CancelledByUser -> {
                        finalReply = result.humanMessage
                        onEvent(AgentEvent.ActionFinished(uid, def?.displayName ?: call.tool, false, result.humanMessage))
                    }
                }
            }
            onEvent(AgentEvent.Done(finalReply, executed, model = null, providerName = "Offline"))
            return
        }

        // Phase 1 — AI provider path
        val provider = providers.activeProvider()
        if (provider == null) {
            onEvent(
                AgentEvent.Failed(
                    AppError.InvalidConfig(
                        "No AI provider configured. Basic offline commands still work — " +
                            "add a provider in Providers to unlock conversation.",
                    ),
                ),
            )
            return
        }
        val profile = providers.activeProfile()
        val modelName = profile?.model ?: "unknown"

        val systemPrompt = buildSystemPrompt()
        val baseMessages = mutableListOf<ChatMessage>(ChatMessage(ChatMessage.Role.SYSTEM, systemPrompt))
        ContextBudgeter.select(history).forEach { m ->
            val role = when (m.role) {
                MessageRole.USER -> ChatMessage.Role.USER
                MessageRole.ASSISTANT -> ChatMessage.Role.ASSISTANT
                MessageRole.TOOL -> ChatMessage.Role.TOOL
                MessageRole.SYSTEM -> ChatMessage.Role.SYSTEM
            }
            baseMessages.add(ChatMessage(role, m.content))
        }
        baseMessages.add(ChatMessage(ChatMessage.Role.USER, userText))

        var executedLabels = mutableListOf<String>()
        var finalText = ""
        var pendingActions: List<Parsed.Action> = listOf(Parsed.Action("__noop__", emptyMap()))
        var round = 0
        var workingMessages = baseMessages.toList()
        var totalUsageIn = 0
        var totalUsageOut = 0
        var firstTokenAt = 0L

        try {
            while (round < maxRounds && pendingActions.isNotEmpty()) {
                round++
                val request = AiRequest(
                    messages = workingMessages,
                    model = modelName,
                    temperature = profile?.temperature ?: 0.7f,
                    maxTokens = profile?.maxTokens ?: 1024,
                )
                onEvent(if (round == 1) AgentEvent.Status("Thinking…") else AgentEvent.Status("Continuing…"))

                val extractor = ReplyStreamExtractor()
                val buffer = StringBuilder()
                var usageIn: Int? = null
                var usageOut: Int? = null
                var failure: AppError? = null
                val roundStart = System.currentTimeMillis()

                provider.stream(request).collect { event ->
                    when (event) {
                        is AiStreamEvent.Delta -> {
                            buffer.append(event.text)
                            val replyChunk = extractor.feed(event.text)
                            if (replyChunk.isNotEmpty()) onEvent(AgentEvent.Delta(replyChunk))
                        }

                        is AiStreamEvent.Usage -> {
                            usageIn = event.inputTokens
                            usageOut = event.outputTokens
                        }
                        is AiStreamEvent.Failed -> failure = event.error
                        AiStreamEvent.Done -> Unit
                    }
                }

                failure?.let { f ->
                    onEvent(AgentEvent.Failed(f))
                    return
                }

                totalUsageIn += usageIn ?: 0
                totalUsageOut += usageOut ?: 0
                if (firstTokenAt == 0L) firstTokenAt = roundStart

                val fullText = buffer.toString()
                val parsed = ToolCallParser.parse(fullText)
                if (parsed.malformed && round == 1) {
                    // One repair round max (§24)
                    onEvent(AgentEvent.Status("Repairing response…"))
                    workingMessages = workingMessages + ChatMessage(ChatMessage.Role.ASSISTANT, fullText) +
                        ChatMessage(
                            ChatMessage.Role.USER,
                            "Your last message was not valid JSON per the format. " +
                                "Respond again with ONLY the JSON object {\"reply\": …, \"actions\": []}.",
                        )
                    pendingActions = emptyList()
                    finalText = parsed.reply
                    continue
                }

                finalText = parsed.reply.ifBlank { finalText }

                // Execute actions (bounded)
                val resultsForModel = mutableListOf<ChatMessage>()
                pendingActions = parsed.actions.take(maxActions)
                for ((index, action) in pendingActions.withIndex()) {
                    if (action.tool == "__noop__") continue
                    val def = registry.byId(action.tool)
                    val uid = "r${round}a$index"
                    val label = def?.displayName ?: action.tool
                    onEvent(AgentEvent.ActionStarted(uid, label, action.tool))
                    val result = actionEngine.execute(
                        context,
                        ToolCall(action.tool, action.arguments),
                        ActionSource.AI_AGENT,
                        confirmationRequester(),
                    )
                    executedLabels.add("$label: ${result.summaryLine()}")
                    val ok = result is ActionResult.Success
                    onEvent(AgentEvent.ActionFinished(uid, label, ok, result.summaryLine()))
                    resultsForModel.add(
                        ChatMessage(
                            ChatMessage.Role.TOOL,
                            "tool=${action.tool} success=$ok message=\"${result.summaryLine().take(300)}\"",
                        ),
                    )
                }

                if (resultsForModel.isNotEmpty() && round < maxRounds) {
                    workingMessages = workingMessages +
                        ChatMessage(ChatMessage.Role.ASSISTANT, fullText) +
                        resultsForModel +
                        ChatMessage(
                            ChatMessage.Role.USER,
                            "Tool results are above. Give the user a very short final reply. " +
                                "JSON envelope as always.",
                        )
                }
            }

            if (totalUsageIn > 0 || totalUsageOut > 0) {
                usageDao.insert(
                    UsageRecordEntity(
                        providerId = profile?.id ?: 0,
                        model = modelName,
                        inputTokens = totalUsageIn,
                        outputTokens = totalUsageOut,
                        latencyMs = System.currentTimeMillis() - (firstTokenAt.takeIf { it > 0 } ?: System.currentTimeMillis()),
                    ),
                )
            }
            onEvent(
                AgentEvent.Done(
                    reply = finalText.ifBlank { "Done." },
                    executedActions = executedLabels,
                    model = modelName,
                    providerName = profile?.name,
                ),
            )
        } catch (c: CancellationException) {
            onEvent(AgentEvent.Done("Stopped.", executedLabels, modelName, profile?.name))
            throw c
        } catch (t: Throwable) {
            onEvent(AgentEvent.Failed(AppError.Unknown(t.message ?: t::class.java.simpleName)))
        }
    }

    private suspend fun buildSystemPrompt(): String {
        val core = SystemPrompts.core(registry.manifestJson())
        val profile = providers.activeProfile()
        val persona = profile?.customInstructions?.takeIf { it.isNotBlank() }?.let {
            SystemPrompts.persona(it)
        }
        val memoryBlock = if (prefs.memoryEnabled.firstOrNull() == true) {
            // Memory content is appended by the caller in future phases.
            null
        } else {
            null
        }
        return listOfNotNull(core, persona, memoryBlock).joinToString("\n\n")
    }
}
