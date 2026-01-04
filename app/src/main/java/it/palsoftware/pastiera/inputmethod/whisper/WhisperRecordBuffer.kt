package it.palsoftware.pastiera.inputmethod.whisper

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Buffer for audio recording data.
 * Stores PCM audio samples and provides conversion utilities.
 */
object WhisperRecordBuffer {
    private var outputBuffer: ByteArray? = null
    
    /**
     * Sets the output buffer with PCM_16BIT samples as ByteArray.
     */
    fun setOutputBuffer(buffer: ByteArray) {
        outputBuffer = buffer
    }
    
    /**
     * Gets the output buffer as ByteArray.
     */
    fun getOutputBuffer(): ByteArray? {
        return outputBuffer
    }
    
    /**
     * Gets samples as FloatArray for Whisper processing (normalized to [-1.0, 1.0]).
     */
    fun getSamples(): FloatArray? {
        val bytes = outputBuffer ?: return null
        return convertPCM16ToFloat(bytes)
    }
    
    /**
     * Clears the buffer.
     */
    fun clear() {
        outputBuffer = null
    }
    
    /**
     * Converts PCM16 ByteArray to FloatArray (normalized to [-1.0, 1.0]).
     */
    private fun convertPCM16ToFloat(pcm16Bytes: ByteArray): FloatArray {
        val byteBuffer = ByteBuffer.wrap(pcm16Bytes).order(ByteOrder.LITTLE_ENDIAN)
        val shortBuffer = byteBuffer.asShortBuffer()
        val floatBuffer = FloatArray(shortBuffer.remaining())
        
        for (i in floatBuffer.indices) {
            // Normalize PCM16 samples to [-1.0, 1.0]
            floatBuffer[i] = shortBuffer.get(i) / 32768.0f
        }
        
        return floatBuffer
    }
}

