package com.invokeil.shinigami.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.invokeil.shinigami.core.data.PrefsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val prefs: PrefsRepository,
) : ViewModel() {

    fun completeOnboarding() {
        viewModelScope.launch { prefs.setOnboardingDone() }
    }
}
