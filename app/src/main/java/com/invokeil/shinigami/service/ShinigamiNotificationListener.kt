package com.invokeil.shinigami.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.invokeil.shinigami.core.actions.NotificationCenter
import com.invokeil.shinigami.core.data.db.ProtectedAppDao
import com.invokeil.shinigami.core.util.ShiniLog
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Notification assistant backend (MASTER SPEC §38). Populates the in-memory
 * [NotificationCenter] ring so the assistant can read / summarise / dismiss
 * / reply on demand. Nothing is persisted and protected apps are skipped.
 */
@AndroidEntryPoint
class ShinigamiNotificationListener : NotificationListenerService() {

    @Inject lateinit var center: NotificationCenter
    @Inject lateinit var protectedApps: ProtectedAppDao

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        center.listener = this
        ShiniLog.i(TAG, "notification listener connected")
        // Seed with active notifications
        scope.launch {
            activeNotifications?.forEach { sbn -> record(sbn) }
        }
    }

    override fun onDestroy() {
        center.listener = null
        scope.launch { center.clear() }
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName == packageName) return
        scope.launch { record(sbn) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        center.remove(sbn.key)
    }

    private suspend fun record(sbn: StatusBarNotification) {
        try {
            if (protectedApps.isProtected(sbn.packageName)) return
            if (sbn.isOngoing) return
            val extras = sbn.notification.extras
            val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                ?: extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            val canReply = sbn.notification.actions?.any { a ->
                a.remoteInputs != null && a.remoteInputs.isNotEmpty()
            } == true
            center.push(
                NotificationCenter.Notif(
                    key = sbn.key,
                    packageName = sbn.packageName,
                    appLabel = appLabel(sbn.packageName),
                    title = title,
                    text = text,
                    postedAt = sbn.postTime,
                    canReply = canReply,
                    sbn = sbn,
                ),
            )
        } catch (t: Throwable) {
            ShiniLog.w(TAG, "notification record failed")
        }
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(
            packageManager.getApplicationInfo(pkg, 0),
        ).toString()
    } catch (t: Throwable) {
        pkg.substringAfterLast('.')
    }

    private companion object {
        const val TAG = "NotifListener"
    }
}
