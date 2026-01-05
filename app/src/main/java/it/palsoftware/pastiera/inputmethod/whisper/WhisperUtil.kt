package it.palsoftware.pastiera.inputmethod.whisper

import android.content.Context
import android.util.Log
import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.math.*

/**
 * Utility class for Whisper model operations.
 * Handles Mel spectrogram calculation, vocabulary loading, and token decoding.
 */
class WhisperUtil(private val context: Context) {
    companion object {
        private const val TAG = "WhisperUtil"
        const val WHISPER_SAMPLE_RATE = 16000
        const val WHISPER_CHUNK_SIZE = 30 // seconds
        private const val N_FFT = 400
        private const val HOP_LENGTH = 160
        private const val N_MEL = 80
        private const val MEL_LOW_HZ = 0f
        private const val MEL_HIGH_HZ = 8000f
    }

    private var vocab: Array<String>? = null
    private var tokenToWord: Map<Int, ByteArray>? = null

    /**
     * Loads vocabulary and filters from file.
     */
    fun loadFiltersAndVocab(isMultilingual: Boolean, vocabFile: File): Boolean {
        return try {
            // Load vocabulary from binary file
            val vocabBytes = vocabFile.readBytes()
            
            // Parse vocabulary (simplified - assumes newline-separated tokens)
            val vocabString = String(vocabBytes, StandardCharsets.UTF_8)
            vocab = vocabString.split("\n").toTypedArray()
            
            // Build token-to-word mapping
            val mapping = mutableMapOf<Int, ByteArray>()
            vocab?.forEachIndexed { index, word ->
                mapping[index] = word.toByteArray(StandardCharsets.UTF_8)
            }
            tokenToWord = mapping
            
            Log.d(TAG, "Loaded ${vocab?.size ?: 0} vocabulary entries")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error loading vocabulary", e)
            false
        }
    }

    /**
     * Gets word bytes from token ID.
     */
    fun getWordFromToken(token: Int): ByteArray {
        return tokenToWord?.get(token) ?: byteArrayOf()
    }

    /**
     * Gets language code from language token (50259-50357).
     */
    fun getLanguageFromToken(token: Int): String {
        // Simplified mapping - in production, use full Whisper language token mapping
        return when (token) {
            50259 -> "en"
            50260 -> "zh"
            50261 -> "de"
            50262 -> "es"
            50263 -> "ru"
            50264 -> "ko"
            50265 -> "fr"
            50266 -> "ja"
            50267 -> "pt"
            50268 -> "tr"
            50269 -> "pl"
            50270 -> "ca"
            50271 -> "nl"
            50272 -> "ar"
            50273 -> "sv"
            50274 -> "it"
            50275 -> "id"
            50276 -> "hi"
            50277 -> "fi"
            50278 -> "vi"
            else -> "auto"
        }
    }

    /**
     * Calculates Mel spectrogram from audio samples.
     * This is a simplified implementation - production code should use
     * optimized DSP libraries.
     */
    fun getMelSpectrogram(samples: FloatArray, sampleCount: Int, numThreads: Int): FloatArray {
        // Calculate number of frames
        val numFrames = (sampleCount - N_FFT) / HOP_LENGTH + 1
        val melSpectrogram = FloatArray(N_MEL * numFrames)
        
        // Create Mel filter bank
        val melFilterBank = createMelFilterBank()
        
        // Process each frame
        for (frame in 0 until numFrames) {
            val frameStart = frame * HOP_LENGTH
            val frameEnd = minOf(frameStart + N_FFT, sampleCount)
            
            // Extract frame
            val frameData = FloatArray(N_FFT)
            for (i in 0 until (frameEnd - frameStart)) {
                frameData[i] = samples[frameStart + i]
            }
            
            // Apply Hann window
            applyHannWindow(frameData)
            
            // Compute FFT (simplified - production should use FFT library)
            val fftMagnitudes = computeFFTMagnitudes(frameData)
            
            // Apply Mel filter bank
            for (mel in 0 until N_MEL) {
                var melValue = 0f
                for (k in fftMagnitudes.indices) {
                    melValue += fftMagnitudes[k] * melFilterBank[mel][k]
                }
                // Convert to log scale
                melSpectrogram[frame * N_MEL + mel] = ln(maxOf(melValue, 1e-10f))
            }
        }
        
        return melSpectrogram
    }

    /**
     * Creates Mel filter bank matrix.
     */
    private fun createMelFilterBank(): Array<FloatArray> {
        val filterBank = Array(N_MEL) { FloatArray(N_FFT / 2 + 1) }
        
        val melLow = hzToMel(MEL_LOW_HZ)
        val melHigh = hzToMel(MEL_HIGH_HZ)
        val melStep = (melHigh - melLow) / (N_MEL + 1)
        
        val melPoints = FloatArray(N_MEL + 2) { i -> melLow + i * melStep }
        val hzPoints = melPoints.map { melToHz(it) }
        val binPoints = hzPoints.map { hz -> (N_FFT + 1) * hz / WHISPER_SAMPLE_RATE }
        
        for (mel in 0 until N_MEL) {
            val left = binPoints[mel].toInt()
            val center = binPoints[mel + 1].toInt()
            val right = binPoints[mel + 2].toInt()
            
            // Rising slope
            for (k in left until center) {
                if (k < filterBank[mel].size) {
                    filterBank[mel][k] = (k - left).toFloat() / (center - left)
                }
            }
            
            // Falling slope
            for (k in center until right) {
                if (k < filterBank[mel].size) {
                    filterBank[mel][k] = (right - k).toFloat() / (right - center)
                }
            }
        }
        
        return filterBank
    }

    /**
     * Converts Hz to Mel scale.
     */
    private fun hzToMel(hz: Float): Float {
        return 2595f * log10(1f + hz / 700f)
    }

    /**
     * Converts Mel to Hz scale.
     */
    private fun melToHz(mel: Float): Float {
        return 700f * (10f.pow(mel / 2595f) - 1f)
    }

    /**
     * Applies Hann window to frame.
     */
    private fun applyHannWindow(frame: FloatArray) {
        for (i in frame.indices) {
            frame[i] *= 0.5f * (1f - cos(2f * PI.toFloat() * i / (frame.size - 1)))
        }
    }

    /**
     * Computes FFT magnitudes (simplified DFT implementation).
     * Production code should use optimized FFT library (e.g., JTransforms).
     */
    private fun computeFFTMagnitudes(frame: FloatArray): FloatArray {
        val n = frame.size
        val magnitudes = FloatArray(n / 2 + 1)
        
        // Simplified DFT - compute only positive frequencies
        for (k in magnitudes.indices) {
            var real = 0f
            var imag = 0f
            
            for (t in 0 until n) {
                val angle = -2f * PI.toFloat() * k * t / n
                real += frame[t] * cos(angle)
                imag += frame[t] * sin(angle)
            }
            
            magnitudes[k] = sqrt(real * real + imag * imag)
        }
        
        return magnitudes
    }
}

