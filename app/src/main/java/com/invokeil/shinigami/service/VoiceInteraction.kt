package com.invokeil.shinigami.service

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.service.voice.VoiceInteractionService
import com.invokeil.shinigami.MainActivity
import com.invokeil.shinigami.core.util.ShiniLog

/**
 * Minimal VoiceInteraction plumbing so Shini can be selected as the system
 * default assistant (MASTER SPEC §18). The session hands off to the main
 * activity in assistant mode — speech handling is done there with the
 * standard SpeechRecognizer, not through the VIS session machinery.
 */
class ShinigamiVoiceInteractionService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()
        ShiniLog.i(TAG, "VoiceInteractionService ready")
    }

    companion object {
        private const val TAG = "VoiceInteraction"
    }
}

class ShinigamiVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession =
        ShinigamiVoiceInteractionSession(this)
}

class ShinigamiVoiceInteractionSession(context: android.content.Context) :
    VoiceInteractionSession(context) {

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MainActivity.EXTRA_ASSIST_LAUNCH, true)
        }
        try {
            context.startActivity(intent)
        } catch (t: Throwable) {
            ShiniLog.w(TAG, "assistant launch failed")
        }
        finish()
    }

    private companion object {
        const val TAG = "VoiceSession"
    }
}

/** Android-version helpers used by settings UI. */
object AssistantRole {
    fun supported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
}
