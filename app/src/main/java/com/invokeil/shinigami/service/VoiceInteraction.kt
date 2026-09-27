package com.invokeil.shinigami.service

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.service.voice.VoiceInteractionService
import android.view.Gravity
import android.graphics.PixelFormat
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AbstractComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.invokeil.shinigami.MainActivity
import com.invokeil.shinigami.core.overlay.AssistantSessionCoordinator
import com.invokeil.shinigami.core.overlay.OverlayPrefs
import com.invokeil.shinigami.core.overlay.OverlayPrefsRepository
import com.invokeil.shinigami.core.overlay.ui.OverlaySessionContent
import com.invokeil.shinigami.core.util.ShiniLog
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
/**
 * Default-assistant plumbing (OVERLAY SPEC §3).
 *
 * The session hosts the lightweight assistant overlay directly — it NEVER
 * launches MainActivity for a wake/assist event (§2, §59). The full app only
 * opens when the user explicitly expands (§16, §47).
 */
class ShinigamiVoiceInteractionService : VoiceInteractionService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReady() {
        super.onReady()
        ShiniLog.i(TAG, "VoiceInteractionService ready")
    }

    override fun onShutdown() {
        serviceScope.cancel()
        super.onShutdown()
    }

    private companion object {
        const val TAG = "VoiceInteraction"
    }
}

@AndroidEntryPoint
class ShinigamiVoiceInteractionSessionService : VoiceInteractionSessionService() {

    @Inject lateinit var coordinator: AssistantSessionCoordinator
    @Inject lateinit var overlayPrefs: OverlayPrefsRepository

    override fun onNewSession(args: Bundle?): VoiceInteractionSession =
        ShinigamiVoiceInteractionSession(this, coordinator, overlayPrefs)
}

/**
 * The overlay session window. Transparent, dimmed-behind, blur on S+.
 * Everything visible comes from [AssistantSessionCoordinator]'s controller.
 */
class ShinigamiVoiceInteractionSession(
    context: Context,
    private val coordinator: AssistantSessionCoordinator,
    private val prefsRepository: OverlayPrefsRepository,
) : VoiceInteractionSession(context) {

    private val lifecycleOwner = SessionLifecycleOwner()
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private inner class OverlayContentView(ctx: Context) : AbstractComposeView(ctx) {
        @Composable
        override fun Content() {
            val prefs by prefsRepository.prefs.collectAsState(initial = OverlayPrefs())
            OverlaySessionContent(
                controller = coordinator.controllerRef,
                prefs = prefs,
                onExpandToApp = ::expandToApp,
                onDismiss = { hide() },
            )
        }
    }

    override fun onCreateContentView(): View {
        val view = OverlayContentView(context)
        view.setViewTreeLifecycleOwner(lifecycleOwner)
        view.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        return view
    }

    override fun onPrepareShow(args: Bundle?, showFlags: Int) {
        super.onPrepareShow(args, showFlags)
        window?.window?.apply {
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
            )
            setFormat(PixelFormat.TRANSLUCENT)
            setGravity(Gravity.BOTTOM)
            // Our Compose layer renders its own subtle scrim — no hard dim (§27)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            // Real blur on S+ where enabled; graceful no-op otherwise (§27)
            if (Build.VERSION.SDK_INT >= 31) {
                try { setBackgroundBlurRadius(48) } catch (_: Throwable) {}
            }
            setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
            )
        }
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        lifecycleOwner.start()
        coordinator.beginSession()
    }

    override fun onHide() {
        coordinator.endSession()
        lifecycleOwner.stop()
        super.onHide()
    }

    /** "Open Shinigami" / Expand — the ONLY path to the full app (§2, §16). */
    private fun expandToApp() {
        val intent = android.content.Intent(context, MainActivity::class.java).apply {
            addFlags(
                android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP,
            )
            putExtra(MainActivity.EXTRA_ASSIST_LAUNCH, true)
        }
        try {
            context.startActivity(intent)
        } catch (t: Throwable) {
            ShiniLog.w(TAG, "expand to app failed: ${t.message}")
        }
        hide()
    }

    private companion object {
        const val TAG = "VoiceSession"
    }
}

/** Minimal lifecycle owner so Compose can render inside the session window. */
class SessionLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    fun start() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun stop() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}

/** Android-version helper used by settings UI. */
object AssistantRole {
    fun supported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
}
