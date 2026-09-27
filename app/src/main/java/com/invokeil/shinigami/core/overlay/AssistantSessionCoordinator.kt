package com.invokeil.shinigami.core.overlay

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import com.invokeil.shinigami.core.actions.ActionEngine
import com.invokeil.shinigami.core.ai.AgentConfirmationBridge
import com.invokeil.shinigami.core.ai.AgentOrchestrator
import com.invokeil.shinigami.core.data.db.ConversationDao
import com.invokeil.shinigami.core.data.db.ConversationEntity
import com.invokeil.shinigami.core.data.db.MessageDao
import com.invokeil.shinigami.core.data.db.MessageEntity
import com.invokeil.shinigami.core.data.db.MessageRole
import com.invokeil.shinigami.core.util.ShiniLog
import com.invokeil.shinigami.core.voice.SpeechInputManager
import com.invokeil.shinigami.core.voice.TtsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns one assistant session end-to-end (OVERLAY SPEC §1, §54):
 *
 * show → listen → speech → [AgentOrchestrator: offline parse | AI | Tool
 * Registry | Policy | Confirmation | Action Engine] → respond → TTS →
 * auto-dismiss. The underlying app is never left (§59).
 *
 * Also implements: barge-in (§20), follow-up window (§19), no-speech
 * timeout (§37), haptics (§33) and emergency stop (§55).
 */
@Singleton
class AssistantSessionCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val controller: AssistantOverlayController,
    private val speech: SpeechInputManager,
    private val tts: TtsManager,
    private val orchestrator: AgentOrchestrator,
    private val confirmationBridge: AgentConfirmationBridge,
    private val actionEngine: ActionEngine,
    private val overlayPrefs: OverlayPrefsRepository,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var listenJob: Job? = null
    private var commandJob: Job? = null
    private var dismissJob: Job? = null
    private var followUpJob: Job? = null
    private var conversationId: Long = -1L

    init {
        controller.onStopRequested = { scope.launch { emergencyStop() } }
        controller.onConfirmAnswered = { approve ->
            scope.launch { confirmationBridge.answer(approve) }
        }
        // Mirror AI-path confirmations into overlay state (§17)
        scope.launch {
            orchestrator.uiConfirmation.collect { pending ->
                if (pending != null && controller.state.value.visible) {
                    controller.requestConfirmation(
                        title = pending.toolId.replace('_', ' ')
                            .replaceFirstChar { it.uppercase() },
                        description = pending.summary,
                    )
                }
            }
        }
    }

    /** Exposed so session/overlay windows can render the shared state. */
    val controllerRef: AssistantOverlayController get() = controller

    /** Overlay prefs stream for session/trampoline hosts. */
    val prefsRef get() = overlayPrefs.prefs

    // ------------------------------------------------------------- session --

    /** Wake/assist entry point. Shows overlay, starts listening immediately (§11, §28). */
    fun beginSession() {
        scope.launch {
            val prefs = overlayPrefs.snapshot()
            if (!prefs.overlayEnabled) return@launch
            dismissJob?.cancel()
            followUpJob?.cancel()
            commandJob?.cancel()
            listenJob?.cancel()
            controller.show()
            if (prefs.haptics) haptic()
            // Lazy conversation resolution — never blocks overlay appearance (§28)
            conversationId = resolveConversation()
            delay(180) // activation transition
            startListeningInternal(prefs)
        }
    }

    /** Text entry from overlay keyboard (§23) skips the mic entirely. */
    fun submitText(text: String) {
        if (text.isBlank()) return
        listenJob?.cancel()
        controller.showThinking()
        runCommand(text.trim())
    }

    fun userDismissed() {
        scope.launch { endSession() }
    }

    suspend fun emergencyStop() {
        actionEngine.triggerEmergencyStop()
        tts.stop()
        listenJob?.cancel()
        commandJob?.cancel()
        followUpJob?.cancel()
        dismissJob?.cancel()
        confirmationBridge.cancel()
        controller.hide()
    }

    // ------------------------------------------------------------ listening --

    private fun startListeningInternal(prefs: OverlayPrefs) {
        if (!speech.isAvailable) {
            controller.showError(
                "Voice input isn't available on this device.",
                technical = "SpeechRecognizer.isRecognitionAvailable == false",
                actionLabel = "Type instead",
            )
            return
        }
        controller.startListening()
        listenJob?.cancel()
        val noSpeechTimeoutMs = prefs.noSpeechTimeoutSec * 1000L
        listenJob = scope.launch {
            var gotAnyAudio = false
            try {
                // Barge-in window (§20): RMS while speaking also handled here.
                speech.listen(languageTag = "", preferOffline = true).collect { event ->
                    when (event) {
                        is SpeechInputManager.VoiceEvent.Started -> gotAnyAudio = true
                        is SpeechInputManager.VoiceEvent.Rms -> {
                            // local-only visual amplitude (§9): -2..+10 dB → 0..1
                            val level = ((event.level + 2f) / 12f).coerceIn(0f, 1f)
                            controller.setMicAmplitude(level)
                        }
                        is SpeechInputManager.VoiceEvent.Partial -> {
                            if (prefs.showTranscript) {
                                controller.showTranscribing(event.text)
                            }
                        }
                        is SpeechInputManager.VoiceEvent.Final -> {
                            controller.setMicAmplitude(0f)
                            controller.showThinking()
                            runCommand(event.text.trim())
                            return@collect
                        }
                        is SpeechInputManager.VoiceEvent.Error -> {
                            controller.setMicAmplitude(0f)
                            if (event.kind == SpeechInputManager.VoiceEvent.Error.Kind.NO_PERMISSION) {
                                controller.showError(
                                    "Microphone permission is needed to listen.",
                                    technical = event.message,
                                    actionLabel = "Open Shinigami",
                                )
                            } else if (!gotAnyAudio && event.kind == SpeechInputManager.VoiceEvent.Error.Kind.NO_MATCH) {
                                // silence timeout → quietly dismiss (§37)
                                controller.hide()
                            } else {
                                controller.showError(event.message)
                            }
                            return@collect
                        }
                        is SpeechInputManager.VoiceEvent.Ended -> Unit
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                ShiniLog.w(TAG, "listen failed: ${t.message}")
                controller.showError("Couldn't start listening.", technical = t.message)
            }
        }
        // Hard no-speech cutoff (§37): don't send empty audio anywhere
        scope.launch {
            delay(noSpeechTimeoutMs)
            if (controller.state.value is AssistantOverlayState.Listening ||
                controller.state.value is AssistantOverlayState.Activating
            ) {
                listenJob?.cancel()
                controller.hide()
            }
        }
    }

    // ------------------------------------------------------------ command run --

    private fun runCommand(text: String) {
        followUpJob?.cancel()
        commandJob?.cancel()
        commandJob = scope.launch {
            controller.setStopAvailable(true)
            var finalReply: String? = null
            var hadError = false
            try {
                val history = if (conversationId > 0) messageDao.forConversation(conversationId) else emptyList()
                messageDao.insert(
                    MessageEntity(conversationId = conversationId.coerceAtLeast(0), role = MessageRole.USER, content = text),
                )
                orchestrator.respond(
                    userText = text,
                    history = history.takeLast(12),
                ) { event ->
                    when (event) {
                        is AgentOrchestrator.AgentEvent.Status -> controller.showThinking()
                        is AgentOrchestrator.AgentEvent.Delta -> {
                            if (event.text.isNotBlank()) {
                                controller.showResponse(event.text.takeLast(280), isFinal = false)
                            }
                        }
                        is AgentOrchestrator.AgentEvent.ActionStarted -> {
                            controller.showAction(event.label, step = 1, totalSteps = 1)
                        }
                        is AgentOrchestrator.AgentEvent.ActionFinished -> {
                            if (!event.ok) controller.showError(event.message)
                        }
                        is AgentOrchestrator.AgentEvent.NeedConfirmation -> Unit // mirrored via bridge
                        is AgentOrchestrator.AgentEvent.Done -> finalReply = event.reply
                        is AgentOrchestrator.AgentEvent.Failed -> {
                            hadError = true
                            controller.showError(event.error.userMessage, technical = event.error.detail)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                hadError = true
                ShiniLog.w(TAG, "command failed: ${t.message}")
                controller.showError("Something went wrong.", technical = t.message)
            } finally {
                controller.setStopAvailable(false)
            }

            if (controller.state.value is AssistantOverlayState.Hidden) return@launch

            val prefs = overlayPrefs.snapshot()
            val reply = finalReply
            if (!hadError && !reply.isNullOrBlank()) {
                messageDao.insert(
                    MessageEntity(conversationId = conversationId.coerceAtLeast(0), role = MessageRole.ASSISTANT, content = reply),
                )
                val isShort = reply.length <= 220
                controller.showResponse(if (isShort) reply else reply.take(220) + "…", isFinal = true)
                // TTS then auto-dismiss (§15, §18)
                val ttsDone = scope.launch { tts.speak(if (isShort) reply else reply.take(220)) }
                delay(prefs.autoDismissDelayMs.coerceAtLeast(600).toLong())
                // wait for TTS politely but never forever
                while (ttsDone.isActive && isActive) delay(150)
                maybeFollowUp(prefs, reply)
            } else if (!hadError) {
                maybeFollowUp(prefs, null)
            } else {
                delay(2200) // let the user read the error
                controller.hide()
            }
        }
    }

    /** Follow-up window (§19): stay in listening mode without re-waking. */
    private fun maybeFollowUp(prefs: OverlayPrefs, lastReply: String?) {
        followUpJob?.cancel()
        if (!prefs.followUp) {
            controller.hide()
            return
        }
        followUpJob = scope.launch {
            delay(prefs.followUpTimeoutSec * 1000L)
            if (controller.state.value.visible &&
                controller.state.value !is AssistantOverlayState.Listening
            ) {
                controller.hide()
            }
        }
        // Re-arm the mic so the user can just speak again
        startListeningInternal(prefs)
    }

    // ------------------------------------------------------------- helpers --

    private suspend fun resolveConversation(): Long {
        val temp = conversationDao.latestTemporary()
        return temp?.id ?: conversationDao.insert(ConversationEntity(title = "Voice session", temporary = true))
    }

    private fun haptic() {
        try {
            val v = context.getSystemService(Vibrator::class.java) ?: return
            v.vibrate(VibrationEffect.createOneShot(24, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Throwable) {
        }
    }

    fun endSession() {
        listenJob?.cancel()
        commandJob?.cancel()
        followUpJob?.cancel()
        dismissJob?.cancel()
        tts.stop()
        controller.hide()
    }

    private companion object {
        const val TAG = "OverlayCoordinator"
    }
}
