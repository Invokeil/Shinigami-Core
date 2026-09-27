package com.invokeil.shinigami

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point for Shinigami Core.
 *
 * Creates notification channels and wires Hilt into WorkManager.
 */
@HiltAndroidApp
class ShinigamiApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val wake = NotificationChannel(
            CHANNEL_WAKE,
            getString(R.string.channel_wake),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        val general = NotificationChannel(
            CHANNEL_GENERAL,
            getString(R.string.channel_general),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        manager.createNotificationChannels(listOf(wake, general))
    }

    companion object {
        const val CHANNEL_WAKE = "wake_word"
        const val CHANNEL_GENERAL = "general"
    }
}
