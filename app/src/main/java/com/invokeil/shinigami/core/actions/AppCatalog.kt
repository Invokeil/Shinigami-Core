package com.invokeil.shinigami.core.actions

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Process
import com.invokeil.shinigami.core.util.ShiniLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Catalog of launchable apps used by "open <app>" — both for the offline
 * parser and the open_app executor. Enumerates only launcher activities via
 * LauncherApps; no QUERY_ALL_PACKAGES.
 */
@Singleton
class AppCatalog @Inject constructor() {

    data class AppEntry(
        val packageName: String,
        val label: String,
    )

    suspend fun launchables(context: Context): List<AppEntry> = withContext(Dispatchers.IO) {
        try {
            val pm = context.packageManager
            val launcherApps = context.getSystemService(LauncherApps::class.java)
            if (launcherApps != null) {
                launcherApps.getActivityList(null, Process.myUserHandle())
                    .map { AppEntry(it.componentName.packageName, it.label.toString()) }
                    .distinctBy { it.packageName }
            } else {
                pm.queryIntentActivities(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                    0,
                ).map {
                    AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString())
                }.distinctBy { it.packageName }
            }
        } catch (t: Throwable) {
            ShiniLog.w(TAG, "app catalog failed")
            emptyList()
        }
    }

    /**
     * Best-match an app by fuzzy label or package name. Returns the entry plus
     * a score in [0,1] — callers may require a threshold to avoid wrong apps.
     */
    suspend fun find(context: Context, query: String): AppEntry? {
        val q = query.trim().lowercase().removeSuffix(".apk")
        if (q.isEmpty()) return null
        val apps = launchables(context)
        var best: AppEntry? = null
        var bestScore = 0.0
        for (app in apps) {
            val label = app.label.lowercase()
            val pkg = app.packageName.lowercase()
            val score = when {
                label == q || pkg == q || pkg.substringAfterLast('.') == q -> 1.0
                label.startsWith(q) -> 0.9
                label.contains(q) -> 0.8
                pkg.contains(q) -> 0.7
                tokensMatch(label, q) -> 0.65
                else -> similarity(label, q)
            }
            if (score > bestScore) {
                bestScore = score
                best = app
            }
        }
        return best?.takeIf { bestScore >= 0.6 }
    }

    private fun tokensMatch(label: String, q: String): Boolean =
        label.split(' ', '-', '_').any { it.startsWith(q) } && q.length >= 3

    /** Lightweight edit-distance ratio, good enough for "whatsapp" vs "whatsApp". */
    private fun similarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val s1 = a.take(32)
        val s2 = b.take(32)
        var prev = IntArray(s2.length + 1) { it }
        val cur = IntArray(s2.length + 1)
        for (i in 1..s1.length) {
            cur[0] = i
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            System.arraycopy(cur, 0, prev, 0, cur.size)
        }
        val dist = prev[s2.length]
        return 1.0 - dist.toDouble() / maxOf(s1.length, s2.length)
    }

    private companion object {
        const val TAG = "AppCatalog"
    }
}
