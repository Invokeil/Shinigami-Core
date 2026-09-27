package com.invokeil.shinigami.feature.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.invokeil.shinigami.core.update.UpdateChecker
import com.invokeil.shinigami.core.update.UpdateInstaller
import com.invokeil.shinigami.core.update.UpdatePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * In-app update popup state (user requirement: prompt on every new APK).
 * Checks GitHub Releases; bottom sheet offers Update now / Remind me /
 * Skip this version. Never interrupts an active overlay session.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val checker: UpdateChecker,
    private val installer: UpdateInstaller,
    private val prefs: UpdatePreferences,
) : ViewModel() {

    data class UiState(
        val checking: Boolean = false,
        val sheetVisible: Boolean = false,
        val release: UpdateChecker.ReleaseInfo? = null,
        val installState: UpdateInstaller.InstallState = UpdateInstaller.InstallState.Idle,
        val needUnknownAppsPermission: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    /** Called on app start; respects skip/snooze and check cadence. */
    fun checkOnLaunch() {
        viewModelScope.launch {
            _state.value = _state.value.copy(checking = true)
            val result = checker.checkLatest(force = false)
            val p = prefs.state.firstOrNull() ?: UpdatePreferences.State()
            val seen = p.skippedVersion
            val snoozed = System.currentTimeMillis() < p.snoozedUntilMs
            val release = result.release
            val showUpdate = result.isUpdateAvailable &&
                release != null &&
                release.tagName != seen &&
                !snoozed &&
                release.apkUrl != null
            _state.value = _state.value.copy(
                checking = false,
                release = if (result.isUpdateAvailable) release else null,
                sheetVisible = showUpdate,
            )
        }
    }

    fun dismissSheet() {
        _state.value = _state.value.copy(sheetVisible = false)
    }

    fun remindLater() {
        viewModelScope.launch {
            prefs.snooze(48)
            dismissSheet()
        }
    }

    fun skipVersion() {
        viewModelScope.launch {
            _state.value.release?.let { prefs.skip(it.tagName) }
            dismissSheet()
        }
    }

    fun startInstall() {
        val release = _state.value.release ?: return
        val url = release.apkUrl ?: return
        val sha = release.apkSha256
        if (!installer.canInstall()) {
            _state.value = _state.value.copy(needUnknownAppsPermission = true)
            return
        }
        viewModelScope.launch {
            installer.downloadAndStage(url, sha) { s ->
                _state.value = _state.value.copy(installState = s)
            }.let { final ->
                _state.value = _state.value.copy(installState = final)
            }
        }
    }

    fun unknownAppsPermissionHandled() {
        _state.value = _state.value.copy(needUnknownAppsPermission = false)
    }
}
