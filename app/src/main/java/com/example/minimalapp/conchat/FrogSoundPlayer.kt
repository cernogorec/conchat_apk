package com.example.minimalapp.conchat

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

object FrogSoundPlayer {

    fun play() {
        Thread {
            try {
                playFrogCroak()
            } catch (_: Exception) {
                // audio errors are non-critical
            }
        }.start()
    }

    private fun playFrogCroak() {
        val sampleRate = 22050
        val chirpMs = 130
        val gapMs = 55
        val pauseMs = 160
        val chirpsPerBurst = 3

        val chirpSamples = sampleRate * chirpMs / 1000
        val gapSamples = sampleRate * gapMs / 1000
        val pauseSamples = sampleRate * pauseMs / 1000

        // Two bursts of 3 chirps each: ribbit … ribbit
        val totalSamples =
            chirpsPerBurst * (chirpSamples + gapSamples) +
            pauseSamples +
            chirpsPerBurst * (chirpSamples + gapSamples)

        val buffer = ShortArray(totalSamples)
        var pos = 0

        // First burst: 420 → 820 Hz
        repeat(chirpsPerBurst) {
            pos = writeChirp(buffer, pos, chirpSamples, sampleRate, 420.0, 820.0, 29000.0)
            pos += gapSamples
        }

        pos += pauseSamples

        // Second burst: 500 → 950 Hz (higher = more realistic frog)
        repeat(chirpsPerBurst) {
            pos = writeChirp(buffer, pos, chirpSamples, sampleRate, 500.0, 950.0, 27000.0)
            pos += gapSamples
        }

        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, totalSamples * 2))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack.write(buffer, 0, totalSamples)
        audioTrack.play()

        val playDurationMs = (totalSamples.toLong() * 1000L) / sampleRate + 300
        Thread.sleep(playDurationMs)

        audioTrack.stop()
        audioTrack.release()
    }

    private fun writeChirp(
        buffer: ShortArray,
        startPos: Int,
        chirpSamples: Int,
        sampleRate: Int,
        startFreq: Double,
        endFreq: Double,
        amplitude: Double
    ): Int {
        val duration = chirpSamples.toDouble() / sampleRate
        for (i in 0 until chirpSamples) {
            val pos = startPos + i
            if (pos >= buffer.size) break
            val progress = i.toDouble() / chirpSamples
            val t = i.toDouble() / sampleRate
            // Proper chirp: integrated phase of linear frequency glide
            val phase = 2.0 * PI * (startFreq + (endFreq - startFreq) * progress / 2.0) * t
            // Envelope: fast attack, sustain, fast decay
            val env = when {
                progress < 0.08 -> progress / 0.08
                progress > 0.78 -> (1.0 - progress) / 0.22
                else -> 1.0
            }
            val sample = (env * amplitude * sin(phase)).toInt()
            buffer[pos] = sample.coerceIn(-32768, 32767).toShort()
        }
        return startPos + chirpSamples
    }
}
