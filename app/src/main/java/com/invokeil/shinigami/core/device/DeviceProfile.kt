package com.invokeil.shinigami.core.device

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Runtime device configuration snapshot (research: device-config matrix).
 *
 * Drives adaptive behavior: animation tier, wake-engine availability, blur
 * support, component downloads, OEM workarounds. This is also the payload
 * included in bug reports (no user content — only hardware/OS facts).
 */
@Singleton
class DeviceProfile @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    enum class RamTier { LOW, MID, HIGH }
    enum class FormFactor { PHONE, TABLET, FOLDABLE, DESKTOP }

    enum class Rom(val label: String) {
        MIUI("MIUI / HyperOS"), ONEUI("One UI"), EMUI("EMUI / HarmonyOS"),
        COLOROS("ColorOS / OxygenOS"), FUNTOUCH("Funtouch / OriginOS"),
        STOCK("Stock Android"),
    }

    data class Snapshot(
        val abi: String = "unknown",
        val apiLevel: Int = Build.VERSION.SDK_INT,
        val androidRelease: String = Build.VERSION.RELEASE ?: "?",
        val securityPatch: String? = Build.VERSION.SECURITY_PATCH,
        val manufacturer: String = Build.MANUFACTURER ?: "?",
        val model: String = Build.MODEL ?: "?",
        val rom: Rom = Rom.STOCK,
        val ramTier: RamTier = RamTier.HIGH,
        val totalRamMb: Long = 0,
        val isLowRam: Boolean = false,
        val isGoEdition: Boolean = false,
        val hasMicrophone: Boolean = true,
        val hasGms: Boolean = true,
        val speechRecognitionAvailable: Boolean = false,
        val onDeviceSpeechAvailable: Boolean = false,
        val blurSupported: Boolean = false,
        val formFactor: FormFactor = FormFactor.PHONE,
        val powerSaveMode: Boolean = false,
        val backgroundRestricted: Boolean = false,
    )

    private val _snapshot = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = _snapshot

    fun refresh(): Snapshot {
        val am = context.getSystemService(ActivityManager::class.java)
        val pm = context.getSystemService(PowerManager::class.java)
        val memInfo = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
        val totalMb = (memInfo?.totalMem ?: 0L) / (1024 * 1024)
        val isLowRam = am?.isLowRamDevice() ?: false
        val isGo = context.packageManager.hasSystemFeature("android.hardware.ram.low")
        val tier = when {
            isLowRam || totalMb in 1 until 3000 -> RamTier.LOW
            totalMb < 6000 -> RamTier.MID
            else -> RamTier.HIGH
        }
        val hasMic = context.packageManager
            .hasSystemFeature(android.content.pm.PackageManager.FEATURE_MICROPHONE)
        val gms = try {
            context.packageManager.getPackageInfo("com.google.android.gms", 0)
            true
        } catch (_: Throwable) {
            false
        }
        val animScale = try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        } catch (_: Throwable) {
            1f
        }
        val snap = Snapshot(
            abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            manufacturer = Build.MANUFACTURER.lowercase(),
            rom = detectRom(),
            ramTier = tier,
            totalRamMb = totalMb,
            isLowRam = isLowRam,
            isGoEdition = isGo,
            hasMicrophone = hasMic,
            hasGms = gms,
            speechRecognitionAvailable =
                android.speech.SpeechRecognizer.isRecognitionAvailable(context),
            onDeviceSpeechAvailable = Build.VERSION.SDK_INT >= 31 &&
                android.speech.SpeechRecognizer.isOnDeviceRecognitionAvailable(context),
            blurSupported = Build.VERSION.SDK_INT >= 31 && animScale > 0f,
            formFactor = detectFormFactor(),
            powerSaveMode = pm?.isPowerSaveMode ?: false,
            backgroundRestricted = if (Build.VERSION.SDK_INT >= 30) {
                am?.isBackgroundRestricted() ?: false
            } else {
                false
            },
        )
        _snapshot.value = snap
        return snap
    }

    fun current(): Snapshot = _snapshot.value.takeIf { it.totalRamMb > 0 } ?: refresh()

    private fun detectRom(): Rom {
        val m = Build.MANUFACTURER.lowercase()
        val b = Build.BRAND.lowercase()
        return when {
            m.contains("xiaomi") || b.contains("redmi") || b.contains("poco") -> Rom.MIUI
            m.contains("samsung") -> Rom.ONEUI
            m.contains("huawei") || m.contains("honor") -> Rom.EMUI
            m.contains("oppo") || m.contains("oneplus") || m.contains("realme") -> Rom.COLOROS
            m.contains("vivo") || m.contains("iqoo") -> Rom.FUNTOUCH
            else -> Rom.STOCK
        }
    }

    private fun detectFormFactor(): FormFactor {
        val pm = context.packageManager
        return when {
            pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PC) -> FormFactor.DESKTOP
            pm.hasSystemFeature("android.hardware.sensor.hinge_angle") ||
                pm.hasSystemFeature("android.hardware.type.foldable") -> FormFactor.FOLDABLE
            context.resources.configuration.smallestScreenWidthDp >= 600 -> FormFactor.TABLET
            else -> FormFactor.PHONE
        }
    }

    companion object {
        @JvmStatic
        fun isLowRam(context: Context): Boolean = try {
            val am = context.getSystemService(ActivityManager::class.java)
            am?.isLowRamDevice() ?: false
        } catch (_: Throwable) {
            false
        }
    }
}

/** System reduced-motion preference ("remove animations" accessibility). */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        try {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
            val transition = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                1f,
            )
            scale == 0f || transition == 0f
        } catch (_: Throwable) {
            false
        }
    }
}
