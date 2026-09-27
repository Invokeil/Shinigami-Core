package com.invokeil.shinigami.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.invokeil.shinigami.core.ai.AgentOrchestrator
import com.invokeil.shinigami.core.ai.AgentConfirmationBridge
import com.invokeil.shinigami.core.actions.ActionEngine
import com.invokeil.shinigami.core.data.ChatRepository
import com.invokeil.shinigami.core.data.PrefsRepository
import com.invokeil.shinigami.core.data.db.MessageEntity
import com.invokeil.shinigami.core.data.db.MessageRole
import com.invokeil.shinigami.core.provider.ProviderRepository
import com.invokeil.shinigami.core.util.AppError
import com.invokeil.shinigami.core.voice.TtsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** One row in the assistant conversation view. */
sealed interface ChatRow {
    data class Message(
        val id: Long,
        val role: MessageRole,
        val text: String,
        val streaming: Boolean = false,
    ) : ChatRow

    data class ToolCard(
        val uid: String,
        val label: String,
        val state: ToolCardUiState,
        val detail: String? = null,
    ) : ChatRow

    data object Thinking : ChatRow
}

enum class ToolCardUiState { RUNNING, DONE, FAILED }

data class AssistantUiState(
    val rows: List<ChatRow> = emptyList(),
    val orbState: OrbStateUi = OrbStateUi.IDLE,
    val statusText: String? = null,
    val partialSpeech: String? = null,
    val canSend: Boolean = true,
    val providerLabel: String? = null,
    val hasProvider: Boolean = false,
    val incognito: Boolean = false,
    val error: String? = null,
)

enum class OrbStateUi { IDLE, LISTENING, THINKING, SPEAKING, EXECUTING, ERROR }

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val providerRepository: ProviderRepository,
    private val prefs: PrefsRepository,
    private val orchestrator: AgentOrchestrator,
    private val confirmationBridge: AgentConfirmationBridge,
    private val actionEngine: ActionEngine,
    private val tts: TtsManager,
    private val speechInput: com.invokeil.shinigami.core.voice.SpeechInputManager,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
) : ViewModel() {

    private val _ui = MutableStateFlow(AssistantUiState())
    val ui: StateFlow<AssistantUiState> = _ui.asStateFlow()

    val pendingConfirmation: StateFlow<AgentConfirmationBridge.Pending?> =
        orchestrator.uiConfirmation

    private var conversationId: Long = -1
    private var agentJob: Job? = null
    private var voiceJob: Job? = null

    init {
        viewModelScope.launch {
            actionEngine.clearEmergencyStop()
            chatRepository.applyRetention()
            val incognito = prefs.incognito.first()
            conversationId = chatRepository.currentConversationId(incognito)
            val provider = providerRepository.activeProfile()
            _ui.value = _ui.value.copy(
                hasProvider = provider != null,
                providerLabel = provider?.let { "${it.name} · ${it.model}" },
                incognito = incognito,
            )
            if (!incognito && conversationId > 0) {
                loadHistory(conversationId)
            }
            // Apply TTS preferences
            tts.speed = prefs.ttsSpeed.first()
            tts.pitch = prefs.ttsPitch.first()
            tts.languageTag = prefs.ttsLanguage.first()
        }
    }

    private fun loadHistory(conversationId: Long) {
        viewModelScope.launch {
            val history = chatRepository.history(conversationId)
            _ui.value = _ui.value.copy(
                rows = history.map {
                    ChatRow.Message(it.id, it.role, it.content)
                },
            )
        }
    }

    fun sendText(text: String) {
        if (text.isBlank() || !ui.value.canSend) return
        handleUserInput(text)
    }

    fun onMicPressed() {
        if (voiceJob?.isActive == true) return
        voiceJob = viewModelScope.launch {
            val granted = hasMicPermission()
            if (!granted) {
                _ui.value = _ui.value.copy(error = MIC_PERMISSION_MESSAGE)
                return@launch
            }
            tts.stop() // barge-in
            _ui.value = _ui.value.copy(
                orbState = OrbStateUi.LISTENING,
                statusText = "Listening…",
                partialSpeech = null,
            )
            val language = prefs.sttLanguage.first()
            speechInput.listen(language).collect { event ->
                when (event) {
                    is com.invokeil.shinigami.core.voice.SpeechInputManager.VoiceEvent.Rms -> Unit
                    is com.invokeil.shinigami.core.voice.SpeechInputManager.VoiceEvent.Partial ->
                        _ui.value = _ui.value.copy(partialSpeech = event.text)

                    is com.invokeil.shinigami.core.voice.SpeechInputManager.VoiceEvent.Final -> {
                        _ui.value = _ui.value.copy(orbState = OrbStateUi.IDLE, statusText = null, partialSpeech = null)
                        handleUserInput(event.text)
                        voiceJob?.cancel()
                    }

                    is com.invokeil.shinigami.core.voice.SpeechInputManager.VoiceEvent.Error -> {
                        _ui.value = _ui.value.copy(orbState = OrbStateUi.IDLE, statusText = null, partialSpeech = null)
                        if (event.kind != com.invokeil.shinigami.core.voice.SpeechInputManager.VoiceEvent.Error.Kind.NO_MATCH) {
                            _ui.value = _ui.value.copy(error = event.message)
                        }
                        voiceJob?.cancel()
                    }

                    else -> Unit
                }
            }
        }
    }

    fun stopVoice() {
        voiceJob?.cancel()
        voiceJob = null
        _ui.value = _ui.value.copy(orbState = OrbStateUi.IDLE, statusText = null, partialSpeech = null)
    }

    /** Interrupts: speech output, agent generation, and pending automation. */
    fun interruptAll() {
        tts.stop()
        agentJob?.cancel()
        agentJob = null
        voiceJob?.cancel()
        actionEngine.triggerEmergencyStop()
        _ui.value = _ui.value.copy(
            orbState = OrbStateUi.IDLE,
            statusText = null,
            canSend = true,
            rows = _ui.value.rows.filterNot { it is ChatRow.Thinking },
        )
    }

    fun answerConfirmation(approve: Boolean) {
        viewModelScope.launch { confirmationBridge.answer(approve) }
    }

    fun clearError() {
        _ui.value = _ui.value.copy(error = null)
    }

    fun newChat() {
        interruptAll()
        viewModelScope.launch {
            val incognito = prefs.incognito.first()
            conversationId = if (incognito) {
                chatRepository.currentConversationId(true)
            } else {
                chatRepository.currentConversationId(false)
            }
            if (!incognito) {
                conversationId = chatRepository.startNewConversation()
            }
            _ui.value = _ui.value.copy(rows = emptyList(), error = null)
        }
    }

    fun stopTts() = tts.stop()

    private fun handleUserInput(rawText: String) {
        val text = rawText.trim()
        if (text.isEmpty()) return
        agentJob = viewModelScope.launch {
            actionEngine.clearEmergencyStop()
            val incognito = prefs.incognito.first()
            val history = if (!incognito && conversationId > 0) {
                chatRepository.history(conversationId)
            } else {
                emptyList()
            }
            if (!incognito && conversationId > 0) {
                chatRepository.appendMessage(conversationId, MessageRole.USER, text)
            }

            // local echo
            val echoId = System.currentTimeMillis()
            _ui.value = _ui.value.copy(
                rows = _ui.value.rows + ChatRow.Message(echoId, MessageRole.USER, text),
                canSend = false,
                orbState = OrbStateUi.THINKING,
                error = null,
            )

            val collected = StringBuilder()
            val toolCards = mutableListOf<ChatRow.ToolCard>()
            var doneReply: String? = null

            orchestrator.respond(
                userText = text,
                history = history,
                source = com.invokeil.shinigami.core.data.db.ActionSource.OFFLINE_PARSER,
            ) { event ->
                when (event) {
                    is AgentOrchestrator.AgentEvent.Status ->
                        _ui.value = _ui.value.copy(statusText = event.label)

                    is AgentOrchestrator.AgentEvent.Delta -> {
                        collected.append(event.text)
                        updateStreamingMessage(collected.toString())
                    }

                    is AgentOrchestrator.AgentEvent.ActionStarted -> {
                        _ui.value = _ui.value.copy(
                            orbState = OrbStateUi.EXECUTING,
                            statusText = event.label,
                            rows = _ui.value.rows.filterNot { it is ChatRow.Thinking },
                        )
                        val card = ChatRow.ToolCard(event.uid, event.label, ToolCardUiState.RUNNING)
                        toolCards.add(card)
                        _ui.value = _ui.value.copy(rows = _ui.value.rows + card)
                    }

                    is AgentOrchestrator.AgentEvent.ActionFinished -> {
                        toolCards.removeAll { it.uid == event.uid }
                        _ui.value = _ui.value.copy(
                            rows = _ui.value.rows.map { row ->
                                if (row is ChatRow.ToolCard && row.uid == event.uid) {
                                    row.copy(
                                        state = if (event.ok) ToolCardUiState.DONE else ToolCardUiState.FAILED,
                                        detail = event.message,
                                    )
                                } else {
                                    row
                                }
                            },
                        )
                    }

                    is AgentOrchestrator.AgentEvent.NeedConfirmation -> Unit // via state flow

                    is AgentOrchestrator.AgentEvent.Done -> {
                        doneReply = event.reply
                        _ui.value = _ui.value.copy(
                            rows = _ui.value.rows.filterNot { it is ChatRow.Thinking },
                        )
                    }

                    is AgentOrchestrator.AgentEvent.Failed -> {
                        _ui.value = _ui.value.copy(
                            error = event.error.userMessage,
                            orbState = OrbStateUi.IDLE,
                            statusText = null,
                            canSend = true,
                            rows = _ui.value.rows.filterNot { it is ChatRow.Thinking },
                        )
                    }
                }
            }

            // Finalise
            val reply = doneReply ?: collected.toString().ifBlank { null }
            if (reply != null) {
                if (!incognito && conversationId > 0) {
                    chatRepository.appendMessage(
                        conversationId,
                        MessageRole.ASSISTANT,
                        reply,
                        model = null,
                    )
                }
                val replyId = System.currentTimeMillis() + 1
                _ui.value = _ui.value.copy(
                    rows = _ui.value.rows + ChatRow.Message(replyId, MessageRole.ASSISTANT, reply),
                )
                // Strip streaming placeholder
                _ui.value = _ui.value.copy(
                    rows = _ui.value.rows.filterNot { row -> row is ChatRow.Message && row.id == STREAM_ID },
                )
                val speakEnabled = prefs.ttsEnabled.first()
                if (speakEnabled && reply.isNotBlank()) {
                    _ui.value = _ui.value.copy(orbState = OrbStateUi.SPEAKING, statusText = null)
                    tts.speak(reply.replace(Regex("[•*]"), ""))
                    if (_ui.value.orbState == OrbStateUi.SPEAKING) {
                        _ui.value = _ui.value.copy(orbState = OrbStateUi.IDLE)
                    }
                } else {
                    _ui.value = _ui.value.copy(orbState = OrbStateUi.IDLE, statusText = null)
                }
            }
            _ui.value = _ui.value.copy(canSend = true, statusText = null)
        }
    }

    private fun updateStreamingMessage(text: String) {
        val rows = _ui.value.rows.toMutableList()
        val existing = rows.indexOfFirst { it is ChatRow.Message && it.id == STREAM_ID }
        val streamingRow = ChatRow.Message(STREAM_ID, MessageRole.ASSISTANT, text, streaming = true)
        if (existing >= 0) rows[existing] = streamingRow else rows.add(streamingRow)
        _ui.value = _ui.value.copy(rows = rows)
    }

    private suspend fun hasMicPermission(): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(
            appContext,
            android.Manifest.permission.RECORD_AUDIO,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private companion object {
        const val STREAM_ID = -7L
        const val MIC_PERMISSION_MESSAGE =
            "Microphone access lets Shini hear you. Grant it from the Permission Dashboard."
    }
}
