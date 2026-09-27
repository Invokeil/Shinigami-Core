package com.invokeil.shinigami.core.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.invokeil.shinigami.core.util.ShiniLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Android system TextToSpeech (MASTER SPEC §16, §17).
 * - configurable speed/pitch, optional language override;
 * - barge-in: [stop] is instant and flushes the queue;
 * - completion callback so the assistant knows when to re-open the mic.
 */
@Singleton
class TtsManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var engine: TextToSpeech? = null
    private var ready = false
    private val initMutex = kotlinx.coroutines.sync.Mutex()
    private var initDeferred: CompletableDeferred<Boolean>? = null

    var speed: Float = 1.0f
    var pitch: Float = 1.0f
    var languageTag: String = ""

    /** Suspends until the engine is initialised (or times out). */
    private suspend fun ensureReady(): Boolean {
        if (ready) return true
        initMutex.withLock {
            if (ready) return true
            if (initDeferred == null) {
                val deferred = CompletableDeferred<Boolean>()
                initDeferred = deferred
                engine = TextToSpeech(context) { status ->
                    ready = status == TextToSpeech.SUCCESS
                    deferred.complete(ready)
                }
            }
        }
        val result = initDeferred?.await() ?: false
        if (!result) initDeferred = null // allow a retry
        return result
    }

    /**
     * Speaks [text], interrupting anything already queued (barge-in).
     * Returns when speech finishes (or fails fast).
     */
    suspend fun speak(text: String): Boolean {
        if (text.isBlank()) return false
        if (!ensureReady()) return false
        val tts = engine ?: return false
        val lang = if (languageTag.isNotBlank()) {
            tts.setLanguage(Locale.forLanguageTag(languageTag))
        } else {
            tts.setLanguage(Locale.getDefault())
        }
        if (lang == TextToSpeech.LANG_NOT_SUPPORTED || lang == TextToSpeech.LANG_MISSING_DATA) {
            tts.setLanguage(Locale.US)
        }
        tts.setSpeechRate(speed)
        tts.setPitch(pitch)

        val done = CompletableDeferred<Boolean>()
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                if (utteranceId == UTTERANCE) done.complete(true)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId == UTTERANCE) done.complete(false)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (utteranceId == UTTERANCE) done.complete(false)
            }
        })
        // Flush: any previous speech stops immediately (barge-in §17)
        val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE)
        if (result != TextToSpeech.SUCCESS) {
            ShiniLog.w(TAG, "tts speak rejected")
            return false
        }
        return withTimeoutOrNull(30_000) { done.await() } ?: false
    }

    /** Immediate silence — used by barge-in and emergency stop. */
    fun stop() {
        try {
            engine?.stop()
        } catch (t: Throwable) {
            ShiniLog.w(TAG, "tts stop failed")
        }
    }

    fun shutdown() {
        try {
            engine?.shutdown()
        } catch (_: Throwable) {
        }
        engine = null
        ready = false
    }

    /** Simple smoke test used by Settings → "Test voice". */
    suspend fun test(): Boolean = speak("Hey, I am Shini. Ready when you are.")

    private companion object {
        const val TAG = "TtsManager"
        const val UTTERANCE = "shini-utterance"
    }
}
