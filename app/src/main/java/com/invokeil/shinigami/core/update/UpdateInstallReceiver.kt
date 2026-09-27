package com.invokeil.shinigami.core.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import com.invokeil.shinigami.core.util.ShiniLog

/**
 * Receives the PackageInstaller session status and, when the system asks for
 * the pending user action, launches the actual install-confirmation dialog.
 */
class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) {
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            PackageInstaller.STATUS_SUCCESS -> ShiniLog.i(TAG, "update installed")
            else -> ShiniLog.w(TAG, "install status $status: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
        }
    }

    companion object {
        private const val TAG = "UpdateInstall"

        fun intentSender(context: Context, sessionId: Int): IntentSender {
            val intent = Intent(context, UpdateInstallReceiver::class.java)
                .setAction("com.invokeil.shinigami.UPDATE_INSTALL_STATUS")
            val flags = if (Build.VERSION.SDK_INT >= 31) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            return PendingIntent.getBroadcast(context, sessionId, intent, flags).intentSender
        }
    }
}
