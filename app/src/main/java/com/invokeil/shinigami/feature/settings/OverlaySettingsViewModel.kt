package com.invokeil.shinigami.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.invokeil.shinigami.core.overlay.AnimationQuality
import com.invokeil.shinigami.core.overlay.OverlayPosition
import com.invokeil.shinigami.core.overlay.OverlayPrefs
import com.invokeil.shinigami.core.overlay.OverlayPrefsRepository
import com.invokeil.shinigami.core.overlay.OverlayStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class OverlaySettingsViewModel @Inject constructor(
    private val repo: OverlayPrefsRepository,
) : ViewModel() {

    val prefs = repo.prefs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverlayPrefs())

    fun setOverlayEnabled(v: Boolean) = viewModelScope.launch { repo.setOverlayEnabled(v) }
    fun setStyle(v: OverlayStyle) = viewModelScope.launch { repo.setStyle(v) }
    fun setPosition(v: OverlayPosition) = viewModelScope.launch { repo.setPosition(v) }
    fun setTranscript(v: Boolean) = viewModelScope.launch { repo.setShowTranscript(v) }
    fun setActionProgress(v: Boolean) = viewModelScope.launch { repo.setShowActionProgress(v) }
    fun setActivationSound(v: Boolean) = viewModelScope.launch { repo.setActivationSound(v) }
    fun setHaptics(v: Boolean) = viewModelScope.launch { repo.setHaptics(v) }
    fun setAutoDismiss(v: Boolean) = viewModelScope.launch { repo.setAutoDismiss(v) }
    fun setFollowUp(v: Boolean) = viewModelScope.launch { repo.setFollowUp(v) }
    fun setFollowUpTimeout(sec: Int) = viewModelScope.launch { repo.setFollowUpTimeout(sec) }
    fun setLockScreen(v: Boolean) = viewModelScope.launch { repo.setAllowOnLockScreen(v) }
    fun setQuality(v: AnimationQuality) = viewModelScope.launch { repo.setAnimationQuality(v) }
}
