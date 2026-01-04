package it.palsoftware.pastiera.inputmethod.whisper

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Buffer for audio recording data.
 * Stores PCM audio samples and provides conversion utilities.
 */
object WhisperRecordBuffer {
    private var outputBuffer: FloatArray? = null
    
    /**
     * Sets the output buffer with PCM_FLOAT samples.
     */
    fun setOutputBuffer(buffer: FloatArray) {
        outputBuffer = buffer
    }
    
    /**
     * Gets the output buffer with PCM_FLOAT samples.
     */
    fun getOutputBuffer(): FloatArray? {
        return outputBuffer
    }
    
    /**
     * Gets samples as FloatArray for processing.
     */
    fun getSamples(): FloatArray? {
        return outputBuffer
    }
    
    /**
     * Clears the buffer.
     */
    fun clear() {
        outputBuffer = null
    }
    
    /**
     * Converts PCM16 ByteBuffer to FloatArray (normalized to [-1.0, 1.0]).
     */
    fun convertPCM16ToFloat(pcm16Buffer: ByteBuffer): FloatArray {
        pcm16Buffer.order(ByteOrder.LITTLE_ENDIAN)
        val shortBuffer = pcm16Buffer.asShortBuffer()
        val floatBuffer = FloatArray(shortBuffer.remaining())
        
        for (i in floatBuffer.indices) {
            // Normalize PCM16 samples to [-1.0, 1.0]
            floatBuffer[i] = shortBuffer.get(i) / 32768.0f
        }
        
        return floatBuffer
    }
}

