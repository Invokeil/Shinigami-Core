package com.invokeil.shinigami.core.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.invokeil.shinigami.core.util.ShiniLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Wrapper over Android SpeechRecognizer (MASTER SPEC §15):
 * partial results, final results, errors, language selection and a
 * preference for on-device recognition where the OEM supports it.
 */
@Singleton
class SpeechInputManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    sealed interface VoiceEvent {
        data object Started : VoiceEvent
        data class Rms(val level: Float) : VoiceEvent
        data class Partial(val text: String) : VoiceEvent
        data class Final(val text: String) : VoiceEvent
        data class Error(val kind: Kind, val message: String) : VoiceEvent {
            enum class Kind { NO_MATCH, BUSY, NO_PERMISSION, UNAVAILABLE, OTHER }
        }

        data object Ended : VoiceEvent
    }

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    val isOnDeviceCapable: Boolean
        get() = android.os.Build.VERSION.SDK_INT >= 31 &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    fun listen(languageTag: String, preferOffline: Boolean = true): Flow<VoiceEvent> = callbackFlow {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            trySend(VoiceEvent.Error(VoiceEvent.Error.Kind.UNAVAILABLE, "No speech recognition service installed."))
            close()
            return@callbackFlow
        }

        val recognizer = if (android.os.Build.VERSION.SDK_INT >= 31 &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            if (languageTag.isNotBlank()) {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            }
            if (preferOffline) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }

        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                trySend(VoiceEvent.Started)
            }

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) {
                trySend(VoiceEvent.Rms(rmsdB))
            }

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() = Unit

            override fun onError(error: Int) {
                val (kind, msg) = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        VoiceEvent.Error.Kind.NO_MATCH to "Didn't catch that."

                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                        VoiceEvent.Error.Kind.BUSY to "Recognizer busy."

                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        VoiceEvent.Error.Kind.NO_PERMISSION to "Microphone permission missing."

                    SpeechRecognizer.ERROR_CLIENT -> VoiceEvent.Error.Kind.OTHER to "Client error."
                    else -> VoiceEvent.Error.Kind.OTHER to "Speech error $error."
                }
                trySend(VoiceEvent.Error(kind, msg))
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    trySend(VoiceEvent.Final(text))
                } else {
                    trySend(VoiceEvent.Error(VoiceEvent.Error.Kind.NO_MATCH, "Nothing heard."))
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) trySend(VoiceEvent.Partial(text))
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }

        recognizer.setRecognitionListener(listener)
        recognizer.startListening(intent)

        awaitClose {
            try {
                recognizer.stopListening()
                recognizer.destroy()
            } catch (t: Throwable) {
                ShiniLog.w(TAG, "recognizer teardown issue")
            }
        }
    }

    private companion object {
        const val TAG = "SpeechInput"
    }
}
