package com.invokeil.shinigami.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "shinigami_prefs")

enum class ThemeMode { SYSTEM, DARK, LIGHT }
enum class PermissionMode { STANDARD, ENHANCED, FULL }
enum class ConfirmationPolicy { ALWAYS, SENSITIVE, MINIMAL }
enum class HistoryRetention { FOREVER, DAYS_30, DAYS_7, DAYS_1, OFF }

/** All user preferences, persisted with DataStore. See MASTER SPEC §65. */
@Singleton
class PrefsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store get() = context.dataStore

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val REDUCE_ANIM = booleanPreferencesKey("reduce_animations")
        val ONBOARDED = booleanPreferencesKey("onboarding_done")
        val MODE = stringPreferencesKey("permission_mode")
        val FULL_ACK = booleanPreferencesKey("full_control_ack")
        val CONFIRM = stringPreferencesKey("confirmation_policy")
        val BIOMETRIC = booleanPreferencesKey("biometric_high_risk")
        val TTS_ENABLED = booleanPreferencesKey("tts_enabled")
        val TTS_SPEED = floatPreferencesKey("tts_speed")
        val TTS_PITCH = floatPreferencesKey("tts_pitch")
        val TTS_LANGUAGE = stringPreferencesKey("tts_language")
        val STT_LANGUAGE = stringPreferencesKey("stt_language")
        val WAKE_ENABLED = booleanPreferencesKey("wake_word_enabled")
        val WAKE_PHRASE = stringPreferencesKey("wake_phrase")
        val WAKE_VOICEPRINT = booleanPreferencesKey("wake_voiceprint_check")
        val INCOGNITO = booleanPreferencesKey("incognito")
        val MEMORY = booleanPreferencesKey("memory_enabled")
        val HISTORY = stringPreferencesKey("history_retention")
        val ACTIVE_PROVIDER = intPreferencesKey("active_provider_id")
        val VERBOSE_LOG = booleanPreferencesKey("verbose_log")
    }

    val themeMode: Flow<ThemeMode> = store.data.map {
        runCatching { ThemeMode.valueOf(it[Keys.THEME] ?: ThemeMode.DARK.name) }
            .getOrDefault(ThemeMode.DARK)
    }
    val dynamicColor: Flow<Boolean> = store.data.map { it[Keys.DYNAMIC] ?: false }
    val reduceAnimations: Flow<Boolean> = store.data.map { it[Keys.REDUCE_ANIM] ?: false }
    val onboardingDone: Flow<Boolean> = store.data.map { it[Keys.ONBOARDED] ?: false }
    val permissionMode: Flow<PermissionMode> = store.data.map {
        runCatching { PermissionMode.valueOf(it[Keys.MODE] ?: PermissionMode.STANDARD.name) }
            .getOrDefault(PermissionMode.STANDARD)
    }
    val fullControlAck: Flow<Boolean> = store.data.map { it[Keys.FULL_ACK] ?: false }
    val confirmationPolicy: Flow<ConfirmationPolicy> = store.data.map {
        runCatching { ConfirmationPolicy.valueOf(it[Keys.CONFIRM] ?: ConfirmationPolicy.SENSITIVE.name) }
            .getOrDefault(ConfirmationPolicy.SENSITIVE)
    }
    val biometricHighRisk: Flow<Boolean> = store.data.map { it[Keys.BIOMETRIC] ?: false }
    val ttsEnabled: Flow<Boolean> = store.data.map { it[Keys.TTS_ENABLED] ?: true }
    val ttsSpeed: Flow<Float> = store.data.map { it[Keys.TTS_SPEED] ?: 1.0f }
    val ttsPitch: Flow<Float> = store.data.map { it[Keys.TTS_PITCH] ?: 1.0f }
    val ttsLanguage: Flow<String> = store.data.map { it[Keys.TTS_LANGUAGE] ?: "" }
    val sttLanguage: Flow<String> = store.data.map { it[Keys.STT_LANGUAGE] ?: "" }
    val wakeWordEnabled: Flow<Boolean> = store.data.map { it[Keys.WAKE_ENABLED] ?: false }
    val wakePhrase: Flow<String> = store.data.map { it[Keys.WAKE_PHRASE] ?: DEFAULT_WAKE_PHRASE }
    val wakeVoiceprint: Flow<Boolean> = store.data.map { it[Keys.WAKE_VOICEPRINT] ?: false }
    val incognito: Flow<Boolean> = store.data.map { it[Keys.INCOGNITO] ?: false }
    val memoryEnabled: Flow<Boolean> = store.data.map { it[Keys.MEMORY] ?: false }
    val historyRetention: Flow<HistoryRetention> = store.data.map {
        runCatching { HistoryRetention.valueOf(it[Keys.HISTORY] ?: HistoryRetention.FOREVER.name) }
            .getOrDefault(HistoryRetention.FOREVER)
    }
    val activeProviderId: Flow<Long?> = store.data.map {
        val v = it[Keys.ACTIVE_PROVIDER] ?: return@map null
        v.toLong()
    }
    val verboseLog: Flow<Boolean> = store.data.map { it[Keys.VERBOSE_LOG] ?: false }

    suspend fun current(): Snapshot = Snapshot(
        themeMode = themeMode.first(),
        dynamicColor = dynamicColor.first(),
        reduceAnimations = reduceAnimations.first(),
        onboardingDone = onboardingDone.first(),
        permissionMode = permissionMode.first(),
        confirmationPolicy = confirmationPolicy.first(),
        biometricHighRisk = biometricHighRisk.first(),
        ttsEnabled = ttsEnabled.first(),
        ttsSpeed = ttsSpeed.first(),
        ttsPitch = ttsPitch.first(),
        sttLanguage = sttLanguage.first(),
        wakeWordEnabled = wakeWordEnabled.first(),
        wakePhrase = wakePhrase.first(),
        incognito = incognito.first(),
        memoryEnabled = memoryEnabled.first(),
        historyRetention = historyRetention.first(),
        activeProviderId = activeProviderId.first(),
    )

    data class Snapshot(
        val themeMode: ThemeMode,
        val dynamicColor: Boolean,
        val reduceAnimations: Boolean,
        val onboardingDone: Boolean,
        val permissionMode: PermissionMode,
        val confirmationPolicy: ConfirmationPolicy,
        val biometricHighRisk: Boolean,
        val ttsEnabled: Boolean,
        val ttsSpeed: Float,
        val ttsPitch: Float,
        val sttLanguage: String,
        val wakeWordEnabled: Boolean,
        val wakePhrase: String,
        val incognito: Boolean,
        val memoryEnabled: Boolean,
        val historyRetention: HistoryRetention,
        val activeProviderId: Long?,
    )

    suspend fun setTheme(mode: ThemeMode) = store.edit { it[Keys.THEME] = mode.name }
    suspend fun setDynamicColor(on: Boolean) = store.edit { it[Keys.DYNAMIC] = on }
    suspend fun setReduceAnimations(on: Boolean) = store.edit { it[Keys.REDUCE_ANIM] = on }
    suspend fun setOnboardingDone() = store.edit { it[Keys.ONBOARDED] = true }
    suspend fun setPermissionMode(mode: PermissionMode) = store.edit { it[Keys.MODE] = mode.name }
    suspend fun setFullControlAck(on: Boolean) = store.edit { it[Keys.FULL_ACK] = on }
    suspend fun setConfirmationPolicy(policy: ConfirmationPolicy) =
        store.edit { it[Keys.CONFIRM] = policy.name }

    suspend fun setBiometricHighRisk(on: Boolean) = store.edit { it[Keys.BIOMETRIC] = on }
    suspend fun setTtsEnabled(on: Boolean) = store.edit { it[Keys.TTS_ENABLED] = on }
    suspend fun setTtsSpeed(v: Float) = store.edit { it[Keys.TTS_SPEED] = v }
    suspend fun setTtsPitch(v: Float) = store.edit { it[Keys.TTS_PITCH] = v }
    suspend fun setTtsLanguage(tag: String) = store.edit { it[Keys.TTS_LANGUAGE] = tag }
    suspend fun setSttLanguage(tag: String) = store.edit { it[Keys.STT_LANGUAGE] = tag }
    suspend fun setWakeWordEnabled(on: Boolean) = store.edit { it[Keys.WAKE_ENABLED] = on }
    suspend fun setWakePhrase(phrase: String) = store.edit { it[Keys.WAKE_PHRASE] = phrase }
    suspend fun setWakeVoiceprint(on: Boolean) = store.edit { it[Keys.WAKE_VOICEPRINT] = on }
    suspend fun setIncognito(on: Boolean) = store.edit { it[Keys.INCOGNITO] = on }
    suspend fun setMemoryEnabled(on: Boolean) = store.edit { it[Keys.MEMORY] = on }
    suspend fun setHistoryRetention(r: HistoryRetention) = store.edit { it[Keys.HISTORY] = r.name }
    suspend fun setActiveProvider(id: Long?) = store.edit {
        if (id == null) it.remove(Keys.ACTIVE_PROVIDER) else it[Keys.ACTIVE_PROVIDER] = id.toInt()
    }

    suspend fun setVerboseLog(on: Boolean) = store.edit { it[Keys.VERBOSE_LOG] = on }

    companion object {
        const val DEFAULT_WAKE_PHRASE = "hey shini"
    }
}
