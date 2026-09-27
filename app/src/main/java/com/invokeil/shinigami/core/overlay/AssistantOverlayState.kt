package com.invokeil.shinigami.core.overlay

/**
 * Assistant overlay state machine (OVERLAY SPEC §10).
 *
 * The overlay is a presentation layer only — it consumes these states and
 * never talks to providers or executors directly (§42). Voice, AI and action
 * modules communicate through [AssistantOverlayController].
 */
sealed interface AssistantOverlayState {

    /** Not showing. Underlying app untouched. */
    data object Hidden : AssistantOverlayState

    /** Wake detected, overlay fading in (~100–300 ms, §11). */
    data object Activating : AssistantOverlayState

    /** Microphone open, orb pulsing with local amplitude. */
    data object Listening : AssistantOverlayState

    /** Partial speech transcript shown under the orb (§12). */
    data class Transcribing(val partialText: String) : AssistantOverlayState

    /** Speech captured, waiting for AI/local parse (§13). */
    data object Thinking : AssistantOverlayState

    /** Streaming or short final response (§15). */
    data class Responding(
        val text: String,
        val isFinal: Boolean,
    ) : AssistantOverlayState

    /** Deterministic action running, e.g. "Opening Spotify…" (§14). */
    data class Executing(
        val actionName: String,
        val step: Int,
        val totalSteps: Int,
    ) : AssistantOverlayState

    /** Sensitive action needs an explicit user decision (§17). */
    data class ConfirmationRequired(
        val title: String,
        val description: String,
    ) : AssistantOverlayState

    /** Human-friendly error with optional technical details (§49). */
    data class Error(
        val message: String,
        val technical: String? = null,
        val actionLabel: String? = null,
    ) : AssistantOverlayState

    /** Whether the overlay should render at all. */
    val visible: Boolean
        get() = this !is Hidden

    val statusLine: String
        get() = when (this) {
            is Activating -> "Shini…"
            is Listening -> "Listening…"
            is Transcribing -> "Listening…"
            is Thinking -> "Thinking…"
            is Responding -> if (isFinal) "Shini" else "Speaking…"
            is Executing -> actionName
            is ConfirmationRequired -> "Confirm"
            is Error -> "Something went wrong"
            is Hidden -> ""
        }
}
