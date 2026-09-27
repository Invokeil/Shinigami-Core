package com.invokeil.shinigami.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.invokeil.shinigami.core.data.ConfirmationPolicy
import com.invokeil.shinigami.core.data.HistoryRetention
import com.invokeil.shinigami.core.data.PrefsRepository
import com.invokeil.shinigami.service.WakeWordService
import dagger.hilt.android.lifecycle.HiltViewModel
import android.content.Context
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: PrefsRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: Context,
) : ViewModel() {

    val ttsEnabled: StateFlow<Boolean> = prefs.ttsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val ttsSpeed: StateFlow<Float> = prefs.ttsSpeed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1f)
    val ttsPitch: StateFlow<Float> = prefs.ttsPitch
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1f)
    val wakeEnabled: StateFlow<Boolean> = prefs.wakeWordEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val wakePhrase: StateFlow<String> = prefs.wakePhrase
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PrefsRepository.DEFAULT_WAKE_PHRASE)
    val wakeVoiceprint: StateFlow<Boolean> = prefs.wakeVoiceprint
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val incognito: StateFlow<Boolean> = prefs.incognito
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val memoryEnabled: StateFlow<Boolean> = prefs.memoryEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val historyRetention: StateFlow<HistoryRetention> = prefs.historyRetention
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryRetention.FOREVER)
    val confirmationPolicy: StateFlow<ConfirmationPolicy> = prefs.confirmationPolicy
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConfirmationPolicy.SENSITIVE)
    val biometricHighRisk: StateFlow<Boolean> = prefs.biometricHighRisk
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setTtsEnabled(on: Boolean) = viewModelScope.launch { prefs.setTtsEnabled(on) }
    fun setTtsSpeed(v: Float) = viewModelScope.launch { prefs.setTtsSpeed(v) }
    fun setTtsPitch(v: Float) = viewModelScope.launch { prefs.setTtsPitch(v) }

    fun setWakeEnabled(on: Boolean) = viewModelScope.launch {
        prefs.setWakeWordEnabled(on)
        if (on) WakeWordService.start(appContext) else WakeWordService.stop(appContext)
    }

    fun setWakePhrase(p: String) = viewModelScope.launch {
        prefs.setWakePhrase(p.ifBlank { PrefsRepository.DEFAULT_WAKE_PHRASE })
    }

    fun setWakeVoiceprint(on: Boolean) = viewModelScope.launch { prefs.setWakeVoiceprint(on) }
    fun setIncognito(on: Boolean) = viewModelScope.launch { prefs.setIncognito(on) }
    fun setMemory(on: Boolean) = viewModelScope.launch { prefs.setMemoryEnabled(on) }
    fun setHistoryRetention(r: HistoryRetention) = viewModelScope.launch { prefs.setHistoryRetention(r) }
    fun setConfirmationPolicy(p: ConfirmationPolicy) = viewModelScope.launch { prefs.setConfirmationPolicy(p) }
    fun setBiometric(on: Boolean) = viewModelScope.launch { prefs.setBiometricHighRisk(on) }
}
