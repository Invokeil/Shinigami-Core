package com.invokeil.shinigami

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.invokeil.shinigami.core.ui.root.ShiniRoot
import com.invokeil.shinigami.core.ui.theme.ShinigamiTheme
import com.invokeil.shinigami.core.util.ShiniLog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Single-activity entry point. Compose-first; extends FragmentActivity so
 * BiometricPrompt is available for high-risk confirmations (§36).
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val themeViewModel: ThemeViewModel by viewModels()

    /** Set when the wake word launched us → auto-open the mic. */
    var autoStartVoice: Boolean = false
        private set

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            ShiniLog.d(TAG, "POST_NOTIFICATIONS granted=$granted")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        autoStartVoice = intent?.getBooleanExtra(EXTRA_VOICE_LAUNCH, false) == true ||
            intent?.getBooleanExtra(EXTRA_WAKE_LAUNCH, false) == true ||
            intent?.getBooleanExtra(EXTRA_ASSIST_LAUNCH, false) == true

        maybeRequestNotificationPermission()

        setContent {
            val themeState by themeViewModel.themeState.collectAsState()
            ShinigamiTheme(
                themeMode = themeState.mode,
                dynamicColor = themeState.dynamicColor,
            ) {
                ShiniRoot(
                    autoStartVoice = autoStartVoice,
                    onVoiceConsumed = { autoStartVoice = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_VOICE_LAUNCH, false) ||
            intent.getBooleanExtra(EXTRA_WAKE_LAUNCH, false) ||
            intent.getBooleanExtra(EXTRA_ASSIST_LAUNCH, false)
        ) {
            autoStartVoice = true
            themeViewModel.requestVoiceStart()
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        const val EXTRA_VOICE_LAUNCH = "shinigami.extra.VOICE_LAUNCH"
        const val EXTRA_WAKE_LAUNCH = "shinigami.extra.WAKE_LAUNCH"
        const val EXTRA_ASSIST_LAUNCH = "shinigami.extra.ASSIST_LAUNCH"
    }
}
