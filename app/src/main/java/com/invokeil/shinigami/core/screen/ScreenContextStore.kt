package com.invokeil.shinigami.core.screen

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-demand screen context (v0.2, OVERLAY SPEC §34, MASTER SPEC §46).
 *
 * Captured ONLY when a command requires it, ONLY via the user-granted
 * AccessibilityService node tree (no MediaProjection, no screenshots stored),
 * and NEVER for protected apps. Text only — sent to the active provider with
 * the usual redaction pipeline.
 */
@Singleton
class ScreenContextStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class ScreenContext(
        val packageName: String,
        val appLabel: String,
        val text: String,
        val capturedAt: Long = System.currentTimeMillis(),
    )

    private var last: ScreenContext? = null

    fun store(c: ScreenContext) {
        last = c
    }

    fun current(): ScreenContext? = last

    /** Context older than 60s is considered stale and dropped. */
    fun fresh(): ScreenContext? =
        last?.takeIf { System.currentTimeMillis() - it.capturedAt < 60_000 }

    fun clear() {
        last = null
    }

    companion object {
        const val CAPTURE_BUDGET_CHARS = 6000
    }
}
