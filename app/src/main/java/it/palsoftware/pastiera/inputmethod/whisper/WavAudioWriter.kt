package it.palsoftware.pastiera.inputmethod.whisper

import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Writes PCM16 audio data to a WAV file with proper headers.
 * Used for debugging audio capture and transcription issues.
 */
object WavAudioWriter {
    private const val TAG = "WavAudioWriter"

    // Audio parameters matching WhisperRecorder
    private const val SAMPLE_RATE = 16000  // 16kHz
    private const val CHANNELS = 1          // Mono
    private const val BITS_PER_SAMPLE = 16  // PCM16

    /**
     * Writes PCM16 audio bytes to a WAV file
     */
    fun writeWavFile(
        audioBytes: ByteArray,
        outputFile: File
    ): Boolean {
        return try {
            val numSamples = audioBytes.size / 2  // 16-bit = 2 bytes per sample
            
            Log.d(TAG, "Writing WAV file: ${outputFile.absolutePath}")
            Log.d(TAG, "Audio data: ${audioBytes.size} bytes ($numSamples samples)")
            Log.d(TAG, "Duration: ${numSamples / SAMPLE_RATE.toFloat()} seconds")

            RandomAccessFile(outputFile, "rw").use { file ->
                // WAV Header (44 bytes)
                val header = createWavHeader(audioBytes.size)
                file.write(header)
                file.write(audioBytes)

                Log.d(TAG, "WAV file written successfully: ${outputFile.length()} bytes")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write WAV file", e)
            false
        }
    }

    /**
     * Creates a proper WAV file header for PCM16 mono audio
     * Header format:
     * - RIFF header: 12 bytes
     * - fmt subchunk: 24 bytes
     * - data subchunk: 8 bytes + audio data
     */
    private fun createWavHeader(audioDataSize: Int): ByteArray {
        val header = ByteArray(44)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        val byteRate = SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8
        val blockAlign = CHANNELS * BITS_PER_SAMPLE / 8
        val fileSize = 36 + audioDataSize

        // RIFF header
        buffer.position(0)
        buffer.put("RIFF".toByteArray())
        buffer.putInt(fileSize)           // File size - 8
        buffer.put("WAVE".toByteArray())

        // fmt subchunk
        buffer.put("fmt ".toByteArray())  // Subchunk1ID
        buffer.putInt(16)                 // Subchunk1Size (16 for PCM)
        buffer.putShort(1)                // AudioFormat (1 for PCM)
        buffer.putShort(CHANNELS.toShort())
        buffer.putInt(SAMPLE_RATE)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(BITS_PER_SAMPLE.toShort())

        // data subchunk
        buffer.put("data".toByteArray())  // Subchunk2ID
        buffer.putInt(audioDataSize)      // Subchunk2Size

        return header
    }

    /**
     * Converts PCM16 ByteArray to FloatArray for inspection (normalized to [-1.0, 1.0])
     */
    fun pcm16ToFloat(pcm16Bytes: ByteArray): FloatArray {
        val byteBuffer = ByteBuffer.wrap(pcm16Bytes).order(ByteOrder.LITTLE_ENDIAN)
        val shortBuffer = byteBuffer.asShortBuffer()
        val floatBuffer = FloatArray(shortBuffer.remaining())

        for (i in floatBuffer.indices) {
            floatBuffer[i] = shortBuffer.get(i) / 32768.0f
        }

        return floatBuffer
    }

    /**
     * Logs audio statistics for debugging
     */
    fun logAudioStats(audioBytes: ByteArray) {
        try {
            val samples = pcm16ToFloat(audioBytes)
            if (samples.isEmpty()) {
                Log.d(TAG, "Audio Stats: No samples")
                return
            }

            var minValue = samples[0]
            var maxValue = samples[0]
            var rmsSum = 0.0f

            for (sample in samples) {
                if (sample < minValue) minValue = sample
                if (sample > maxValue) maxValue = sample
                rmsSum += sample * sample
            }

            val rms = kotlin.math.sqrt((rmsSum / samples.size).toDouble()).toFloat()
            val peakLevel = kotlin.math.max(kotlin.math.abs(minValue), kotlin.math.abs(maxValue))

            Log.d(TAG, "Audio Stats:")
            Log.d(TAG, "  Samples: ${samples.size}")
            Log.d(TAG, "  Duration: ${samples.size / SAMPLE_RATE.toFloat()}s")
            Log.d(TAG, "  Min: $minValue")
            Log.d(TAG, "  Max: $maxValue")
            Log.d(TAG, "  Peak: $peakLevel")
            Log.d(TAG, "  RMS: $rms")

            // Detect if audio is mostly silent
            if (peakLevel < 0.01f) {
                Log.w(TAG, "⚠️ WARNING: Audio is extremely quiet or silent!")
            } else if (peakLevel < 0.05f) {
                Log.w(TAG, "⚠️ WARNING: Audio is very quiet")
            }

            // Detect if audio is clipping
            if (peakLevel > 0.95f) {
                Log.w(TAG, "⚠️ WARNING: Audio is clipping!")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to calculate audio stats", e)
        }
    }
}


