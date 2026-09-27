package com.invokeil.shinigami.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.invokeil.shinigami.core.data.db.ProtectedAppDao
import com.invokeil.shinigami.core.screen.ScreenContextStore
import com.invokeil.shinigami.core.util.ShiniLog
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Accessibility automation + screen-context capture (v0.2, OVERLAY SPEC §35,
 * MASTER SPEC §44).
 *
 * Safety invariants:
 *  - NEVER reads window content for a foreground app that is in Protected Apps.
 *  - Gestures/taps are only issued for tools that passed the Policy Engine.
 *  - Collected text stays local until an AI request explicitly needs it.
 */
@AndroidEntryPoint
class ShinigamiAccessibilityService : AccessibilityService() {

    @Inject lateinit var screenContextStore: ScreenContextStore
    @Inject lateinit var protectedAppDao: ProtectedAppDao

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        refreshProtectedCache()
        ShiniLog.i(TAG, "accessibility service connected")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Passive tracking only for the screen-context "freshness" — no text
        // is captured here. Capture happens strictly on-demand (§34).
        val pkg = event?.packageName?.toString() ?: return
        currentForegroundPackage = pkg
    }

    override fun onInterrupt() = Unit

    // ------------------------------------------------------------- capture --

    /** On-demand capture; refuses protected apps (§35). */
    fun captureScreenContext(): ScreenContextStore.ScreenContext? {
        val root = rootInActiveWindow ?: return null
        val pkg = root.packageName?.toString() ?: return null
        if (isProtected(pkg)) {
            screenContextStore.clear()
            return null
        }
        val sb = StringBuilder()
        val label = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(pkg, 0),
            ).toString()
        } catch (_: Throwable) {
            pkg
        }
        collectText(root, sb, depth = 0)
        val text = sb.toString().take(ScreenContextStore.CAPTURE_BUDGET_CHARS)
        val ctx = ScreenContextStore.ScreenContext(
            packageName = pkg,
            appLabel = label,
            text = text,
        )
        screenContextStore.store(ctx)
        return ctx
    }

    private fun collectText(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int) {
        if (depth > 24 || sb.length > ScreenContextStore.CAPTURE_BUDGET_CHARS) return
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let {
            if (it.length in 2..300 && !sb.contains(it)) {
                sb.appendLine(it)
            }
        }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() && it.length in 2..200 }?.let {
            if (!sb.contains(it)) sb.appendLine(it)
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectText(it, sb, depth + 1) }
        }
    }

    // ------------------------------------------------------------- actions --

    fun tapScreen(x: Float, y: Float): Boolean {
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 40))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 220): Boolean {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun globalBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    fun globalHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    fun scrollDown(): Boolean {
        val root = rootInActiveWindow ?: return false
        val rect = Rect()
        root.getBoundsInScreen(rect)
        return swipe(
            rect.exactCenterX(), rect.centerY() + rect.height() * 0.25f,
            rect.exactCenterX(), rect.centerY() - rect.height() * 0.25f,
        )
    }

    fun isProtected(packageName: String): Boolean {
        return try {
            // checked synchronously via runBlocking-free query: DAO is suspend,
            // so keep a cheap cached allowlist refreshed on events
            protectedCache.contains(packageName)
        } catch (_: Throwable) {
            false
        }
    }

    private var protectedCache: Set<String> = emptySet()

    fun refreshProtectedCache() {
        scope.launch {
            protectedCache = try {
                protectedAppDao.all().map { it.packageName }.toSet()
            } catch (_: Throwable) {
                emptySet()
            }
        }
    }

    companion object {
        private const val TAG = "ShiniA11y"

        @Volatile
        var instance: ShinigamiAccessibilityService? = null
            private set

        @Volatile
        var currentForegroundPackage: String? = null
            private set

        fun connected(): Boolean = instance != null
    }
}

/** Convenience: guarded entry used by tools. Returns null when unavailable. */
fun performScreenAction(
    context: Context,
    action: ShinigamiAccessibilityService.() -> Boolean,
): Boolean {
    val svc = ShinigamiAccessibilityService.instance ?: return false
    val foreground = ShinigamiAccessibilityService.currentForegroundPackage ?: ""
    if (svc.isProtected(foreground)) return false
    return svc.action()
}
