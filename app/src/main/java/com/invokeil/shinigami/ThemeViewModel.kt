package com.invokeil.shinigami

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.invokeil.shinigami.core.data.PrefsRepository
import com.invokeil.shinigami.core.data.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Holds theme + global UI signals for the root composable. */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val prefs: PrefsRepository,
) : ViewModel() {

    data class ThemeState(
        val mode: ThemeMode = ThemeMode.DARK,
        val dynamicColor: Boolean = false,
        val reduceAnimations: Boolean = false,
        val onboardingDone: Boolean = false,
    )

    private val voiceStartSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val voiceStarts: SharedFlow<Unit> = voiceStartSignal

    fun requestVoiceStart() {
        voiceStartSignal.tryEmit(Unit)
    }

    val themeState: StateFlow<ThemeState> =
        combine(
            prefs.themeMode,
            prefs.dynamicColor,
            prefs.reduceAnimations,
            prefs.onboardingDone,
        ) { mode, dynamic, reduce, onboarded ->
            ThemeState(mode, dynamic, reduce, onboarded)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeState())

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { prefs.setTheme(mode) }
    fun setDynamicColor(on: Boolean) = viewModelScope.launch { prefs.setDynamicColor(on) }
    fun setReduceAnimations(on: Boolean) = viewModelScope.launch { prefs.setReduceAnimations(on) }
}
