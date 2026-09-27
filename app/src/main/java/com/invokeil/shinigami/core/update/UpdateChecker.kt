package com.invokeil.shinigami.core.update

import android.content.Context
import com.invokeil.shinigami.core.util.ShiniLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * GitHub Releases update check (research §2).
 *
 * - GET /repos/Invokeil/Shinigami-Core/releases/latest
 * - ETag + If-None-Match → 304 responses don't count against the 60 req/h
 *   anonymous quota, so the check can run at every cold start safely.
 * - Compares by [versionCode] shipped in the release body marker
 *   `versionCode: N` (falls back to semantic tag comparison).
 */
@Singleton
class UpdateChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: UpdatePreferences,
) {
    @Serializable
    data class ReleaseInfo(
        val tagName: String,
        val versionCode: Long,
        val name: String,
        val changelog: String,
        val apkUrl: String?,
        val apkSizeBytes: Long,
        val apkSha256: String?,
        val prerelease: Boolean,
    )

    data class CheckResult(
        val release: ReleaseInfo?,
        val isUpdateAvailable: Boolean,
        val currentVersionName: String,
        val currentVersionCode: Long,
        val wasRateLimited: Boolean = false,
    )

    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkLatest(force: Boolean = false): CheckResult {
        val currentCode = currentVersionCode()
        val currentName = currentVersionName()
        if (!force && System.currentTimeMillis() - prefs.state.first().lastCheckMs < 6 * 3_600_000L) {
            // Checked recently; re-derive purely from prefs
            return CheckResult(null, false, currentName, currentCode)
        }
        return withContext(Dispatchers.IO) {
            try {
                val builder = Request.Builder()
                    .url("https://api.github.com/repos/Invokeil/Shinigami-Core/releases/latest")
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "Shinigami-Core-Updater/${currentName}")
                prefs.etag()?.let { builder.header("If-None-Match", it) }

                http.newCall(builder.build()).execute().use { resp ->
                    if (resp.code == 304) {
                        prefs.setLastCheck(currentName)
                        return@use CheckResult(null, false, currentName, currentCode)
                    }
                    if (resp.code == 403 || resp.code == 429) {
                        return@use CheckResult(null, false, currentName, currentCode, wasRateLimited = true)
                    }
                    if (!resp.isSuccessful) {
                        return@use CheckResult(null, false, currentName, currentCode)
                    }
                    prefs.setEtag(resp.header("ETag"))
                    val body = resp.body?.string() ?: return@use CheckResult(null, false, currentName, currentCode)
                    val root = json.parseToJsonElement(body).let { it as? kotlinx.serialization.json.JsonObject }
                        ?: return@use CheckResult(null, false, currentName, currentCode)

                    val tagName = (root["tag_name"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
                    val relName = (root["name"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: tagName
                    val releaseBody = (root["body"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
                    val prerelease = (root["prerelease"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toBoolean() ?: false
                    val assets = root["assets"] as? kotlinx.serialization.json.JsonArray

                    var apkUrl: String? = null
                    var apkSize = 0L
                    var apkSha: String? = null
                    assets?.forEach { a ->
                        val obj = a as? kotlinx.serialization.json.JsonObject ?: return@forEach
                        val nm = (obj["name"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
                        if (nm.endsWith(".apk") && nm.contains("release", ignoreCase = true) || nm.endsWith(".apk") && apkUrl == null) {
                            apkUrl = (obj["browser_download_url"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                            apkSize = (obj["size"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull() ?: 0L
                            apkSha = (obj["digest"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                                ?.removePrefix("sha256:")
                        }
                    }

                    // versionCode: marker in release body (repo convention), else semver tag
                    val declaredCode = Regex("versionCode:\\s*(\\d+)", RegexOption.IGNORE_CASE)
                        .find(releaseBody)?.groupValues?.get(1)?.toLongOrNull()
                        ?: tagToCode(tagName)
                    val release = ReleaseInfo(
                        tagName = tagName,
                        versionCode = declaredCode,
                        name = relName,
                        changelog = releaseBody,
                        apkUrl = apkUrl,
                        apkSizeBytes = apkSize,
                        apkSha256 = apkSha,
                        prerelease = prerelease,
                    )
                    val isUpdate = declaredCode > currentCode
                    if (isUpdate) prefs.setLastCheck(tagName)
                    CheckResult(release, isUpdate, currentName, currentCode)
                }
            } catch (t: Throwable) {
                ShiniLog.w(TAG, "update check failed: ${t.message}")
                CheckResult(null, false, currentName, currentCode)
            }
        }
    }

    /** "v0.2.0" → 200L (major*10000 + minor*100 + patch) — monotonic fallback. */
    private fun tagToCode(tag: String): Long {
        val parts = tag.removePrefix("v").split(".", "-")[0].split(".")
        val maj = parts.getOrNull(0)?.toLongOrNull() ?: 0
        val min = parts.getOrNull(1)?.toLongOrNull() ?: 0
        val pat = parts.getOrNull(2)?.toLongOrNull() ?: 0
        return maj * 10_000 + min * 100 + pat
    }

    private fun currentVersionName(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (_: Throwable) {
        "?"
    }

    private fun currentVersionCode(): Long = try {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
    } catch (_: Throwable) {
        0L
    }

    private companion object {
        const val TAG = "UpdateChecker"
    }
}
