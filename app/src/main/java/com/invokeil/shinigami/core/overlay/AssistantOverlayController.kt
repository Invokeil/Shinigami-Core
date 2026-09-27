package com.invokeil.shinigami.core.overlay

import kotlinx.coroutines.flow.StateFlow

/**
 * Central overlay controller (OVERLAY SPEC §41).
 *
 * Voice, AI and action modules communicate through this abstraction; the
 * Compose UI renders [state]. The controller knows nothing about which
 * provider (OpenAI/Gemini/GLM/local) produced a response (§42).
 */
interface AssistantOverlayController {

    val state: StateFlow<AssistantOverlayState>

    /** Local mic amplitude 0..1, only ever used for visuals (§9). */
    val micAmplitude: StateFlow<Float>

    /** True while a multi-step action is running → show STOP (§55). */
    val stopAvailable: StateFlow<Boolean>

    /** Set by the coordinator; invoked on [emergencyStop]. */
    var onStopRequested: (() -> Unit)?

    /** Set by the coordinator; invoked when user answers a confirmation card. */
    var onConfirmAnswered: ((Boolean) -> Unit)?

    fun show()
    fun hide()
    fun startListening()
    fun showTranscribing(text: String)
    fun showThinking()
    fun showResponse(text: String, isFinal: Boolean)
    fun showAction(actionName: String, step: Int = 1, totalSteps: Int = 1)
    fun requestConfirmation(title: String, description: String)
    fun showError(message: String, technical: String? = null, actionLabel: String? = null)
    /** User answered an overlay confirmation card. */
    fun confirmPending(approve: Boolean)

    fun setMicAmplitude(level: Float)
    fun setStopAvailable(available: Boolean)

    /** Emergency stop: cancels actions, TTS and AI generation (§55). */
    fun emergencyStop()
}

/** Single source of truth for overlay state across session/overlay hosts. */
class DefaultAssistantOverlayController @javax.inject.Inject constructor() :
    AssistantOverlayController {

    private val _state = kotlinx.coroutines.flow.MutableStateFlow<AssistantOverlayState>(
        AssistantOverlayState.Hidden,
    )
    override val state: StateFlow<AssistantOverlayState> = _state

    private val _micAmplitude = kotlinx.coroutines.flow.MutableStateFlow(0f)
    override val micAmplitude: StateFlow<Float> = _micAmplitude

    private val _stopAvailable = kotlinx.coroutines.flow.MutableStateFlow(false)
    override val stopAvailable: StateFlow<Boolean> = _stopAvailable

    /** Set by the coordinator; invoked on [emergencyStop]. */
    override var onStopRequested: (() -> Unit)? = null

    /** Set by the coordinator; invoked when user answers a confirmation card. */
    override var onConfirmAnswered: ((Boolean) -> Unit)? = null

    override fun show() {
        _state.value = AssistantOverlayState.Activating
    }

    override fun hide() {
        _state.value = AssistantOverlayState.Hidden
        _micAmplitude.value = 0f
        _stopAvailable.value = false
    }

    override fun startListening() {
        _state.value = AssistantOverlayState.Listening
    }

    override fun showTranscribing(text: String) {
        _state.value = AssistantOverlayState.Transcribing(text)
    }

    override fun showThinking() {
        _state.value = AssistantOverlayState.Thinking
    }

    override fun showResponse(text: String, isFinal: Boolean) {
        _state.value = AssistantOverlayState.Responding(text, isFinal)
    }

    override fun showAction(actionName: String, step: Int, totalSteps: Int) {
        _state.value = AssistantOverlayState.Executing(actionName, step, totalSteps)
    }

    override fun requestConfirmation(title: String, description: String) {
        _state.value = AssistantOverlayState.ConfirmationRequired(title, description)
    }

    override fun showError(message: String, technical: String?, actionLabel: String?) {
        _state.value = AssistantOverlayState.Error(message, technical, actionLabel)
    }

    override fun setMicAmplitude(level: Float) {
        _micAmplitude.value = level.coerceIn(0f, 1f)
    }

    override fun setStopAvailable(available: Boolean) {
        _stopAvailable.value = available
    }

    override fun emergencyStop() {
        onStopRequested?.invoke()
    }

    override fun confirmPending(approve: Boolean) {
        onConfirmAnswered?.invoke(approve)
    }
}
