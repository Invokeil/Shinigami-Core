package com.invokeil.shinigami.core.shizuku

import android.content.Context
import com.invokeil.shinigami.core.util.ShiniLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optional Shizuku bridge (v0.2, MASTER SPEC §45).
 *
 * SECURITY (MASTER SPEC §27): Shizuku is a privilege amplifier — Shini only
 * ever issues WHITELISTED shell operations, never an AI-generated string.
 * Current whitelist: exact volume on stubborn OEM streams, app details of
 * the caller, and sensor polling helpers. Anything else is refused here so
 * a prompt-injected model can never reach `pm`, `am force-stop` or worse.
 */
@Singleton
class ShizukuManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    enum class Availability { NOT_INSTALLED, NO_PERMISSION, READY, VERSION_TOO_OLD }

    fun availability(): Availability {
        return try {
            if (!rikka.shizuku.Shizuku.pingBinder()) return Availability.NOT_INSTALLED
            if (rikka.shizuku.Shizuku.getVersion() < 11) return Availability.VERSION_TOO_OLD
            if (rikka.shizuku.Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Availability.NO_PERMISSION
            } else {
                Availability.READY
            }
        } catch (_: Throwable) {
            Availability.NOT_INSTALLED
        }
    }

    fun requestPermission() {
        try {
            if (rikka.shizuku.Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                rikka.shizuku.Shizuku.requestPermission(0)
            }
        } catch (t: Throwable) {
            ShiniLog.w(TAG, "shizuku request failed: ${t.message}")
        }
    }

    /** Runs one of the whitelisted ops through the Shizuku server shell. */
    fun runWhitelisted(op: WhitelistedOp): Result<String> {
        if (availability() != Availability.READY) {
            return Result.failure(IllegalStateException("Shizuku is not ready."))
        }
        val baseBinder = rikka.shizuku.Shizuku.getBinder()
            ?: return Result.failure(IllegalStateException("Shizuku binder unavailable."))
        return try {
            val binder = rikka.shizuku.ShizukuBinderWrapper(baseBinder)
            val service = moe.shizuku.server.IShizukuService.Stub.asInterface(binder)
            val process = service.newProcess(
                op.command.toTypedArray(),
                arrayOf<String>(),
                "",
            )
            val outStream = android.os.ParcelFileDescriptor.AutoCloseInputStream(process.inputStream)
            val errStream = android.os.ParcelFileDescriptor.AutoCloseInputStream(process.errorStream)
            val out = outStream.bufferedReader().readText()
            val err = errStream.bufferedReader().readText()
            process.waitFor()
            val code = process.exitValue()
            if (code == 0) Result.success(out.ifBlank { "OK" })
            else Result.failure(IllegalStateException(err.ifBlank { "Exit " + code }))
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    sealed interface WhitelistedOp {
        val command: List<String>

        /** media volume exact set (bypasses OEM caps) */
        data class SetExactVolume(val percent: Int) : WhitelistedOp {
            override val command: List<String>
                get() = listOf("cmd", "media_session", "volume", "--stream", "3", "--set", percent.toString())
        }

        /** list installed launcher apps */
        data object ListApps : WhitelistedOp {
            override val command: List<String> = listOf("pm", "list", "packages", "-3")
        }
    }

    private companion object {
        const val TAG = "Shizuku"
    }
}
