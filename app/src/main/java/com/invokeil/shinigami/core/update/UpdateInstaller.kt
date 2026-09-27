package com.invokeil.shinigami.core.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import com.invokeil.shinigami.core.util.ShiniLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Streams the new APK from GitHub Releases, verifies SHA-256 against the
 * release asset `digest` (research §2.5), then opens a PackageInstaller
 * session (research §2.4 — cleaner provenance than ACTION_VIEW + FileProvider).
 * The user always confirms the final install (system dialog).
 */
@Singleton
class UpdateInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    sealed interface InstallState {
        data object Idle : InstallState
        data class Downloading(val progressPct: Int) : InstallState
        data object Verifying : InstallState
        data object NeedUnknownAppsPermission : InstallState
        data class Ready(val sessionIntent: Intent?) : InstallState
        data class Failed(val reason: String) : InstallState
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun canInstall(): Boolean = try {
        context.packageManager.canRequestPackageInstalls()
    } catch (_: Throwable) {
        false
    }

    fun unknownAppsSettingsIntent(): Intent =
        Intent(
            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Download + verify + stage. Returns the installer intent to fire, if any. */
    suspend fun downloadAndStage(
        apkUrl: String,
        expectedSha256: String?,
        onProgress: (InstallState) -> Unit,
    ): InstallState = withContext(Dispatchers.IO) {
        onProgress(InstallState.Downloading(0))
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "update.apk")
        val part = File(dir, "update.apk.part")
        try {
            val req = Request.Builder().url(apkUrl).header("User-Agent", "Shinigami-Core-Updater").build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext InstallState.Failed("Download failed (HTTP ${resp.code}).")
                }
                val body = resp.body ?: return@withContext InstallState.Failed("Empty download.")
                val total = body.contentLength()
                val digest = MessageDigest.getInstance("SHA-256")
                body.byteStream().use { input ->
                    DigestInputStream(input, digest).use { din ->
                        part.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            var read: Int
                            var done = 0L
                            while (din.read(buf).also { read = it } != -1) {
                                out.write(buf, 0, read)
                                done += read
                                if (total > 0) {
                                    val pct = ((done * 100) / total).toInt()
                                    if (pct % 5 == 0) onProgress(InstallState.Downloading(pct))
                                }
                            }
                        }
                    }
                }
            }

            onProgress(InstallState.Verifying)
            if (expectedSha256 != null) {
                val actual = MessageDigest.getInstance("SHA-256").digest(target.readBytes())
                    .joinToString("") { "%02x".format(it) }
                if (!actual.equals(expectedSha256, ignoreCase = true)) {
                    target.delete()
                    return@withContext InstallState.Failed(
                        "Integrity check failed — the download didn't match the published SHA-256.",
                    )
                }
            }

            return@withContext stageSession(target)
        } catch (t: Throwable) {
            ShiniLog.w(TAG, "update download failed: ${t.message}")
            part.delete()
            InstallState.Failed("Couldn't download the update: ${t.message ?: "network error"}")
        } finally {
            part.delete()
        }
    }

    private fun stageSession(apk: File): InstallState {
        return try {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val sessionId = installer.createSession(params)
            val session = installer.openSession(sessionId)
            session.use { s ->
                apk.inputStream().use { input ->
                    s.openWrite("shinigami_update", 0, apk.length()).use { out ->
                        input.copyTo(out)
                        s.fsync(out)
                    }
                }
                val intentSender = UpdateInstallReceiver.intentSender(context, sessionId)
                s.commit(intentSender)
            }
            apk.delete()
            InstallState.Ready(null)
        } catch (t: Throwable) {
            ShiniLog.w(TAG, "staging failed: ${t.message}")
            InstallState.Failed("Couldn't stage the install: ${t.message ?: "unknown error"}")
        }
    }

    private companion object {
        const val TAG = "UpdateInstaller"
    }
}
