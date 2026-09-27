package com.invokeil.shinigami.core.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.invokeil.shinigami.core.security.SecretVault
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Experimental speaker-consistency gate for the wake phrase (user request:
 * "the same type of voice must say 'Hey Shini'").
 *
 * Honest scope: this is a lightweight heuristic — a coarse pitch + energy
 * envelope profile captured at enrolment, compared against a short sample at
 * wake time. It is NOT biometric authentication and MUST NOT be presented as
 * a security boundary; it merely reduces accidental wakes by other people and
 * by TV voices. Documented as experimental everywhere it is surfaced.
 */
@Singleton
class VoicePrint @Inject constructor(
    private val vault: SecretVault,
) {

    data class Profile(
        val medianPitchHz: Float,
        val rms: Float,
        val zeroCrossRate: Float,
    ) {
        fun serialise(): String = "$medianPitchHz|$rms|$zeroCrossRate"

        companion object {
            fun deserialise(raw: String): Profile? {
                val parts = raw.split("|")
                if (parts.size != 3) return null
                val p = parts[0].toFloatOrNull() ?: return null
                val r = parts[1].toFloatOrNull() ?: return null
                val z = parts[2].toFloatOrNull() ?: return null
                return Profile(p, r, z)
            }
        }
    }

    fun enroll(profile: Profile) {
        vault.put(VAULT_KEY, profile.serialise())
    }

    fun enrolled(): Boolean = vault.get(VAULT_KEY) != null

    fun forget() = vault.remove(VAULT_KEY)

    /**
     * Compares a fresh sample to the enrolled profile.
     * @return true when within tolerance of the enrolled voice signature.
     */
    fun matches(sample: Profile): Boolean {
        val stored = vault.get(VAULT_KEY)?.let { Profile.deserialise(it) } ?: return true
        // Pitch within 25%, energy within a wide factor, ZCR within 50%
        val pitchOk = sample.medianPitchHz > 0 && stored.medianPitchHz > 0 &&
            abs(sample.medianPitchHz - stored.medianPitchHz) / stored.medianPitchHz <= 0.25f
        val rmsOk = sample.rms <= 0 || stored.rms <= 0 ||
            sample.rms / stored.rms in 0.25f..4f
        val zcrOk = stored.zeroCrossRate <= 0 ||
            abs(sample.zeroCrossRate - stored.zeroCrossRate) / stored.zeroCrossRate <= 0.5f
        return pitchOk && rmsOk && zcrOk
    }

    companion object {
        private const val VAULT_KEY = "wake_voiceprint"

        /**
         * Records ~1.4s from the mic and derives a coarse voice profile.
         * Requires RECORD_AUDIO; runs on the caller's thread — call from IO.
         */
        @SuppressLint("MissingPermission")
        fun captureSample(sampleRate: Int = 16_000): Profile? {
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBuf <= 0) return null
            val bufferSize = maxOf(minBuf, sampleRate) // ≥1s buffer
            val record = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                )
            } catch (_: Throwable) {
                return null
            }
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                return null
            }
            return try {
                val samples = ShortArray(sampleRate * 14 / 10) // 1.4s
                record.startRecording()
                var read = 0
                while (read < samples.size) {
                    val n = record.read(samples, read, samples.size - read)
                    if (n <= 0) break
                    read += n
                }
                record.stop()
                analyse(samples, sampleRate)
            } catch (_: Throwable) {
                null
            } finally {
                record.release()
            }
        }

        internal fun analyse(samples: ShortArray, sampleRate: Int): Profile? {
            if (samples.size < sampleRate / 4) return null
            // RMS energy
            var sumSq = 0.0
            var crossings = 0
            var prev = samples[0].toFloat()
            for (i in samples.indices) {
                val v = samples[i].toFloat()
                sumSq += v * v
                if ((v < 0 && prev >= 0) || (v >= 0 && prev < 0)) crossings++
                prev = v
            }
            val rms = sqrt(sumSq / samples.size).toFloat()
            val zcr = crossings.toFloat() / samples.size
            // Median pitch over 40ms windows using autocorrelation
            val window = sampleRate * 40 / 1000
            val hop = window
            val pitches = mutableListOf<Float>()
            var idx = 0
            while (idx + window <= samples.size && pitches.size < 35) {
                val slice = samples.copyOfRange(idx, idx + window)
                // Only analyse voiced-ish windows
                val wRms = sqrt(slice.map { it * it }.average()).toFloat()
                if (wRms > 300) {
                    autocorrelationPitch(slice, sampleRate)?.let { pitches.add(it) }
                }
                idx += hop
            }
            if (pitches.isEmpty()) return Profile(0f, rms, zcr)
            pitches.sort()
            val median = pitches[pitches.size / 2]
            return Profile(median, rms, zcr)
        }

        private fun autocorrelationPitch(window: ShortArray, sampleRate: Int): Float? {
            val minLag = sampleRate / 400 // 400 Hz
            val maxLag = sampleRate / 60 // 60 Hz
            if (maxLag >= window.size) return null
            var bestLag = -1
            var bestCorr = 0f
            var energy = 0f
            for (i in window.indices) energy += window[i] * window[i]
            if (energy <= 0f) return null
            for (lag in minLag..maxLag) {
                var corr = 0f
                for (i in 0 until window.size - lag) {
                    corr += window[i] * window[i + lag]
                }
                corr /= window.size - lag
                if (corr > bestCorr) {
                    bestCorr = corr
                    bestLag = lag
                }
            }
            if (bestLag <= 0) return null
            return sampleRate.toFloat() / bestLag
        }
    }
}
