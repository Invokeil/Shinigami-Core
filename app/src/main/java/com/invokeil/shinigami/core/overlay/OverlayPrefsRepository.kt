package com.invokeil.shinigami.core.overlay

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.overlayDataStore by preferencesDataStore(name = "shinigami_overlay")

/** Overlay style per OVERLAY SPEC §50. */
enum class OverlayStyle { COMPACT, MINIMAL, EXPANDED }

/** Overlay anchor position per §50. */
enum class OverlayPosition { BOTTOM, CENTER, AUTO }

/** Animation quality tiers (device-adaptive, §50 + DeviceProfile). */
enum class AnimationQuality { AUTO, HIGH, BATTERY_SAVER }

data class OverlayPrefs(
    val overlayEnabled: Boolean = true,
    val style: OverlayStyle = OverlayStyle.COMPACT,
    val position: OverlayPosition = OverlayPosition.BOTTOM,
    val showTranscript: Boolean = true,
    val showActionProgress: Boolean = true,
    val activationSound: Boolean = true,
    val haptics: Boolean = true,
    val autoDismiss: Boolean = true,
    val autoDismissDelayMs: Int = 1500,
    val followUp: Boolean = true,
    val followUpTimeoutSec: Int = 8,
    val allowOnLockScreen: Boolean = true,
    val animationQuality: AnimationQuality = AnimationQuality.AUTO,
    val wakeEnabled: Boolean = false,
    val wakeSensitivity: Int = 50,
    val noSpeechTimeoutSec: Int = 4,
    val pauseDuringCalls: Boolean = true,
)

/**
 * Overlay + wake settings (OVERLAY SPEC §50, §51). Stored in a dedicated
 * DataStore so overlay code stays fully decoupled from the app's main prefs.
 */
@Singleton
class OverlayPrefsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val ENABLED = booleanPreferencesKey("overlay_enabled")
        val STYLE = stringPreferencesKey("overlay_style")
        val POSITION = stringPreferencesKey("overlay_position")
        val SHOW_TRANSCRIPT = booleanPreferencesKey("show_transcript")
        val SHOW_ACTION_PROGRESS = booleanPreferencesKey("show_action_progress")
        val ACTIVATION_SOUND = booleanPreferencesKey("activation_sound")
        val HAPTICS = booleanPreferencesKey("haptic_feedback")
        val AUTO_DISMISS = booleanPreferencesKey("auto_dismiss")
        val AUTO_DISMISS_DELAY = intPreferencesKey("auto_dismiss_delay_ms")
        val FOLLOW_UP = booleanPreferencesKey("conversation_follow_up")
        val FOLLOW_UP_TIMEOUT = intPreferencesKey("follow_up_timeout_sec")
        val LOCK_SCREEN = booleanPreferencesKey("allow_on_lock_screen")
        val ANIMATION_QUALITY = stringPreferencesKey("animation_quality")
        val WAKE_ENABLED = booleanPreferencesKey("wake_enabled")
        val WAKE_SENSITIVITY = intPreferencesKey("wake_sensitivity")
        val NO_SPEECH_TIMEOUT = intPreferencesKey("no_speech_timeout_sec")
        val PAUSE_DURING_CALLS = booleanPreferencesKey("pause_during_calls")
    }

    val prefs: Flow<OverlayPrefs> = context.overlayDataStore.data.map { p ->
        OverlayPrefs(
            overlayEnabled = p[Keys.ENABLED] ?: true,
            style = p[Keys.STYLE]?.let { runCatching { OverlayStyle.valueOf(it) }.getOrNull() }
                ?: OverlayStyle.COMPACT,
            position = p[Keys.POSITION]?.let { runCatching { OverlayPosition.valueOf(it) }.getOrNull() }
                ?: OverlayPosition.BOTTOM,
            showTranscript = p[Keys.SHOW_TRANSCRIPT] ?: true,
            showActionProgress = p[Keys.SHOW_ACTION_PROGRESS] ?: true,
            activationSound = p[Keys.ACTIVATION_SOUND] ?: true,
            haptics = p[Keys.HAPTICS] ?: true,
            autoDismiss = p[Keys.AUTO_DISMISS] ?: true,
            autoDismissDelayMs = p[Keys.AUTO_DISMISS_DELAY] ?: 1500,
            followUp = p[Keys.FOLLOW_UP] ?: true,
            followUpTimeoutSec = p[Keys.FOLLOW_UP_TIMEOUT] ?: 8,
            allowOnLockScreen = p[Keys.LOCK_SCREEN] ?: true,
            animationQuality = p[Keys.ANIMATION_QUALITY]
                ?.let { runCatching { AnimationQuality.valueOf(it) }.getOrNull() }
                ?: AnimationQuality.AUTO,
            wakeEnabled = p[Keys.WAKE_ENABLED] ?: false,
            wakeSensitivity = p[Keys.WAKE_SENSITIVITY] ?: 50,
            noSpeechTimeoutSec = p[Keys.NO_SPEECH_TIMEOUT] ?: 4,
            pauseDuringCalls = p[Keys.PAUSE_DURING_CALLS] ?: true,
        )
    }

    suspend fun snapshot(): OverlayPrefs = prefs.first()

    suspend fun setOverlayEnabled(v: Boolean) = edit { it[Keys.ENABLED] = v }
    suspend fun setStyle(v: OverlayStyle) = edit { it[Keys.STYLE] = v.name }
    suspend fun setPosition(v: OverlayPosition) = edit { it[Keys.POSITION] = v.name }
    suspend fun setShowTranscript(v: Boolean) = edit { it[Keys.SHOW_TRANSCRIPT] = v }
    suspend fun setShowActionProgress(v: Boolean) = edit { it[Keys.SHOW_ACTION_PROGRESS] = v }
    suspend fun setActivationSound(v: Boolean) = edit { it[Keys.ACTIVATION_SOUND] = v }
    suspend fun setHaptics(v: Boolean) = edit { it[Keys.HAPTICS] = v }
    suspend fun setAutoDismiss(v: Boolean) = edit { it[Keys.AUTO_DISMISS] = v }
    suspend fun setAutoDismissDelay(ms: Int) = edit { it[Keys.AUTO_DISMISS_DELAY] = ms }
    suspend fun setFollowUp(v: Boolean) = edit { it[Keys.FOLLOW_UP] = v }
    suspend fun setFollowUpTimeout(sec: Int) = edit { it[Keys.FOLLOW_UP_TIMEOUT] = sec }
    suspend fun setAllowOnLockScreen(v: Boolean) = edit { it[Keys.LOCK_SCREEN] = v }
    suspend fun setAnimationQuality(v: AnimationQuality) = edit { it[Keys.ANIMATION_QUALITY] = v.name }
    suspend fun setWakeEnabled(v: Boolean) = edit { it[Keys.WAKE_ENABLED] = v }
    suspend fun setWakeSensitivity(v: Int) = edit { it[Keys.WAKE_SENSITIVITY] = v }
    suspend fun setNoSpeechTimeout(sec: Int) = edit { it[Keys.NO_SPEECH_TIMEOUT] = sec }
    suspend fun setPauseDuringCalls(v: Boolean) = edit { it[Keys.PAUSE_DURING_CALLS] = v }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.overlayDataStore.edit { block(it) }
    }
}
