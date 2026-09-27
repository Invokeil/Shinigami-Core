package com.invokeil.shinigami.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.invokeil.shinigami.MainActivity
import com.invokeil.shinigami.R
import com.invokeil.shinigami.ShinigamiApp
import com.invokeil.shinigami.core.data.PrefsRepository
import com.invokeil.shinigami.core.security.SecretVault
import com.invokeil.shinigami.core.util.ShiniLog
import com.invokeil.shinigami.core.voice.VoicePrint
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Optional "Hey Shini" wake-word listener (MASTER SPEC §20).
 *
 * - OFF by default; the user enables it explicitly in Settings.
 * - Runs as a foreground service with a persistent notification and a Stop
 *   action, so Android's mic indicator and the user's control are visible.
 * - Detection uses the platform SpeechRecognizer in offline-preferred mode;
 *   an optional enrolled voice profile adds a coarse same-voice check.
 */
@AndroidEntryPoint
class WakeWordService : Service() {

    @Inject lateinit var prefs: PrefsRepository
    @Inject lateinit var voicePrint: VoicePrint
    @Inject lateinit var vault: SecretVault

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        return when (action) {
            ACTION_STOP -> {
                stopSelf()
                START_NOT_STICKY
            }

            else -> {
                startForegroundCompat()
                if (!running) {
                    running = true
                    scope.launch { wakeLoop() }
                }
                START_STICKY
            }
        }
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val stopIntent = PendingIntent.getService(
            this,
            1001,
            Intent(this, WakeWordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val openIntent = PendingIntent.getActivity(
            this,
            1002,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(this, ShinigamiApp.CHANNEL_WAKE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.wake_notification_title))
            .setContentText(getString(R.string.wake_notification_text))
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.action_cancel), stopIntent)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIF_ID, notification)
        }
    }

    private suspend fun wakeLoop() {
        val phrase = prefs.wakePhrase.first().lowercase().trim()
        val voiceCheck = prefs.wakeVoiceprint.first()
        if (voiceCheck && !voicePrint.enrolled()) {
            ShiniLog.w(TAG, "voice check enabled but no profile enrolled — skipping check")
        }

        while (running) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ShiniLog.w(TAG, "mic permission missing — stopping wake service")
                stopSelf()
                return
            }

            val detected = listenOnce(phrase)
            if (detected && running) {
                val voiceOk = !voiceCheck || !voicePrint.enrolled() ||
                    runCatching {
                        val sample = VoicePrint.captureSample()
                        sample == null || voicePrint.matches(sample)
                    }.getOrDefault(true)
                if (voiceOk) {
                    launchAssistant()
                    // Avoid immediately re-triggering while the assistant is up
                    delay(4000)
                }
            }
            delay(600) // breathing room between recognition windows
        }
    }

    /**
     * One recognition window (~8s). Uses partial results to catch the phrase
     * early; falls back to final results.
     */
    private suspend fun listenOnce(phrase: String): Boolean {
        val manager = com.invokeil.shinigami.core.voice.SpeechInputManager(this@WakeWordService)
        var matched = false
        try {
            kotlinx.coroutines.withTimeoutOrNull(8_000) {
                manager.listen(languageTag = "", preferOffline = true).collect { event ->
                    when (event) {
                        is com.invokeil.shinigami.core.voice.SpeechInputManager.VoiceEvent.Partial ->
                            if (normalize(event.text).contains(phrase)) matched = true

                        is com.invokeil.shinigami.core.voice.SpeechInputManager.VoiceEvent.Final ->
                            if (normalize(event.text).contains(phrase)) matched = true

                        else -> Unit
                    }
                }
            }
        } catch (t: Throwable) {
            ShiniLog.w(TAG, "wake listen cycle issue")
        }
        return matched
    }

    private fun launchAssistant() {
        // OVERLAY SPEC §2: wake NEVER opens the full app — show the
        // transparent overlay/trampoline above whatever is on screen.
        val intent = Intent(this, AssistantTrampolineActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                    Intent.FLAG_ACTIVITY_NO_HISTORY,
            )
        }
        try {
            startActivity(intent)
        } catch (t: Throwable) {
            ShiniLog.w(TAG, "wake overlay failed (device locked?)")
        }
    }

    private fun normalize(text: String): String =
        text.lowercase().replace(Regex("[^a-z ]"), " ").replace(Regex("\\s+"), " ").trim()

    companion object {
        private const val TAG = "WakeWordService"
        private const val NOTIF_ID = 42
        const val ACTION_START = "com.invokeil.shinigami.wake.START"
        const val ACTION_STOP = "com.invokeil.shinigami.wake.STOP"

        fun start(context: Context) {
            val i = Intent(context, WakeWordService::class.java).setAction(ACTION_START)
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, WakeWordService::class.java).setAction(ACTION_STOP))
        }
    }
}
