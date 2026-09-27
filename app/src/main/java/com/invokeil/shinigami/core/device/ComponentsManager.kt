package com.invokeil.shinigami.core.device

import android.content.Context
import com.invokeil.shinigami.core.util.ShiniLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Adaptive component delivery (user requirement: "features auto-download
 * after install based on the device's configuration").
 *
 * DESIGN RULES (Play Protect research §3, §1.2-row-6):
 *  - Downloads are DATA ONLY (models / voices / animation packs). No APKs,
 *    no DEX, no executables — dynamic code loading is never used.
 *  - Manifest (components.json) ships as a GitHub Release asset, matched
 *    against the runtime [DeviceProfile]: ABI, API level, RAM tier, free
 *    storage. Nothing downloads without a matching entry.
 *  - Every file is size-checked then SHA-256-verified against the manifest
 *    before an atomic rename into noBackupFilesDir.
 *  - Fully optional and offline-safe: core features never depend on it.
 */
@Singleton
class ComponentsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceProfile: DeviceProfile,
) {
    @Serializable
    data class ComponentDef(
        val id: String,
        val version: Int,
        val type: String, // wake-model | asr-model | tts-voice | lottie-pack | locale-pack
        val minApi: Int = 29,
        val maxApi: Int = 99,
        val abis: List<String> = emptyList(), // empty = ABI-agnostic (data)
        val ramTier: String = "low",          // low | mid | high (minimum tier)
        val minFreeStorageMb: Long = 100,
        val url: String = "",
        val sizeBytes: Long = 0,
        val sha256: String = "",
        val autoDownload: Boolean = false,
        val description: String = "",
    )

    @Serializable
    data class Manifest(
        val manifestVersion: Int = 1,
        val appMinVersion: Int = 2,
        val components: List<ComponentDef> = emptyList(),
    )

    @Serializable
    data class Installed(
        val id: String,
        val version: Int,
        val sha256: String,
        val path: String,
    )

    data class MatchResult(
        val candidates: List<ComponentDef>,
        val installed: List<Installed>,
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val _match = MutableStateFlow(MatchResult(emptyList(), emptyList()))
    val match: StateFlow<MatchResult> = _match

    private fun componentsDir(): File =
        File(context.noBackupFilesDir, "components").apply { mkdirs() }

    private fun registryFile(): File = File(context.noBackupFilesDir, "installed-components.json")

    private fun loadInstalled(): List<Installed> = try {
        if (registryFile().exists()) {
            json.decodeFromString<List<Installed>>(registryFile().readText())
        } else {
            emptyList()
        }
    } catch (_: Throwable) {
        emptyList()
    }

    private fun saveInstalled(list: List<Installed>) {
        registryFile().writeText(json.encodeToString<List<Installed>>(list))
    }

    /** Fetches components.json from the latest GitHub release assets. */
    suspend fun fetchManifest(): Manifest? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(MANIFEST_URL)
                .header("User-Agent", "Shinigami-Core-Components")
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string() ?: return@use null
                json.decodeFromString<Manifest>(body)
            }
        } catch (t: Throwable) {
            ShiniLog.w(TAG, "manifest fetch failed: ${t.message}")
            null
        }
    }

    /** Match manifest entries against this device's configuration. */
    suspend fun refreshMatches(): MatchResult {
        val snap = deviceProfile.refresh()
        val manifest = fetchManifest() ?: Manifest()
        val minTier = when (snap.ramTier) {
            DeviceProfile.RamTier.LOW -> 0
            DeviceProfile.RamTier.MID -> 1
            DeviceProfile.RamTier.HIGH -> 2
        }
        val candidates = manifest.components.filter { c ->
            snap.apiLevel in c.minApi..c.maxApi &&
                (c.abis.isEmpty() || c.abis.contains(snap.abi)) &&
                tierValue(c.ramTier) <= minTier &&
                freeStorageMb() >= c.minFreeStorageMb &&
                !loadInstalled().any { it.id == c.id && it.version >= c.version }
        }
        val result = MatchResult(candidates, loadInstalled())
        _match.value = result
        return result
    }

    /** Downloads, verifies and activates one component. Returns null on failure. */
    suspend fun download(component: ComponentDef): Installed? = withContext(Dispatchers.IO) {
        if (component.url.isBlank() || component.sha256.isBlank()) return@withContext null
        try {
            val dir = File(componentsDir(), "${component.id}/${component.version}").apply { mkdirs() }
            val part = File(dir, "data.part")
            val final = File(dir, "data.bin")
            val req = Request.Builder().url(component.url)
                .header("User-Agent", "Shinigami-Core-Components").build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body ?: return@withContext null
                // size guard BEFORE consuming (zip-bomb / DoS protection)
                if (body.contentLength() > 0 &&
                    body.contentLength() != component.sizeBytes &&
                    component.sizeBytes > 0
                ) {
                    return@withContext null
                }
                body.byteStream().use { input ->
                    part.outputStream().use { output -> input.copyTo(output) }
                }
            }
            if (part.length() != component.sizeBytes && component.sizeBytes > 0) {
                part.delete()
                return@withContext null
            }
            val digest = MessageDigest.getInstance("SHA-256").digest(part.readBytes())
                .joinToString("") { "%02x".format(it) }
            if (!digest.equals(component.sha256, ignoreCase = true)) {
                part.delete()
                return@withContext null
            }
            if (!part.renameTo(final)) {
                part.delete()
                return@withContext null
            }
            val installed = Installed(
                id = component.id,
                version = component.version,
                sha256 = digest,
                path = final.absolutePath,
            )
            val updated = loadInstalled().filterNot { it.id == component.id } + installed
            saveInstalled(updated)
            _match.value = _match.value.copy(installed = updated)
            installed
        } catch (t: Throwable) {
            ShiniLog.w(TAG, "component download failed: ${t.message}")
            null
        }
    }

    /** Auto-download pass: only entries the publisher marked autoDownload. */
    suspend fun autoDownloadMatching(): Int {
        val m = refreshMatches()
        var done = 0
        m.candidates.filter { it.autoDownload }.forEach { c ->
            if (download(c) != null) done++
        }
        return done
    }

    private fun freeStorageMb(): Long = try {
        context.noBackupFilesDir.usableSpace / (1024 * 1024)
    } catch (_: Throwable) {
        0
    }

    private fun tierValue(t: String): Int = when (t.lowercase()) {
        "low" -> 0
        "mid" -> 1
        else -> 2
    }

    companion object {
        private const val TAG = "Components"
        // components.json published as a release asset on every app release
        const val MANIFEST_URL =
            "https://github.com/Invokeil/Shinigami-Core/releases/latest/download/components.json"
    }
}
