package it.palsoftware.pastiera.inputmethod.whisper

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.konovalov.vad.webrtc.Vad
import com.konovalov.vad.webrtc.VadListener
import com.konovalov.vad.webrtc.config.FrameSize
import com.konovalov.vad.webrtc.config.Mode
import com.konovalov.vad.webrtc.config.SampleRate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Manages audio recording for Whisper speech recognition.
 * Includes Voice Activity Detection (VAD) to automatically stop recording when speech ends.
 */
class WhisperRecorder(private val context: Context) {
    companion object {
        private const val TAG = "WhisperRecorder"
        private const val SAMPLE_RATE = 16000 // Whisper requires 16kHz
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val MAX_RECORDING_DURATION_MS = 30000L // 30 seconds max
        private const val SILENCE_DURATION_MS = 1500L // Stop after 1.5s of silence
    }

    interface RecorderListener {
        fun onRecordingStarted()
        fun onRecording()
        fun onRecordingDone()
        fun onRecordingError(error: String)
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var vad: Vad? = null
    private var listener: RecorderListener? = null
    private var isRecording = false
    private val audioBuffer = mutableListOf<Short>()
    private var lastSpeechTime = 0L
    private var recordingStartTime = 0L

    fun setListener(listener: RecorderListener) {
        this.listener = listener
    }

    /**
     * Initializes Voice Activity Detection (VAD).
     */
    fun initVad() {
        try {
            vad = Vad.builder()
                .setSampleRate(SampleRate.SAMPLE_RATE_16K)
                .setFrameSize(FrameSize.FRAME_SIZE_480) // 30ms frames
                .setMode(Mode.NORMAL) // Balance between accuracy and CPU
                .setSilenceDurationMillis(500) // Detect silence after 500ms
                .setSpeechDurationMillis(100) // Minimum speech duration
                .setContext(context)
                .build()
            
            vad?.addListener(object : VadListener {
                override fun onSpeechDetected() {
                    Log.d(TAG, "VAD: Speech detected")
                    lastSpeechTime = System.currentTimeMillis()
                }

                override fun onNoiseDetected() {
                    Log.d(TAG, "VAD: Noise detected (no speech)")
                }
            })
            
            vad?.start()
            Log.d(TAG, "VAD initialized and started")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing VAD", e)
        }
    }

    /**
     * Starts recording audio.
     */
    fun start() {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return
        }

        try {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )

            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                listener?.onRecordingError("Invalid buffer size")
                return
            }

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2 // Double buffer for safety
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                listener?.onRecordingError("AudioRecord not initialized")
                return
            }

            audioBuffer.clear()
            audioRecord?.startRecording()
            isRecording = true
            recordingStartTime = System.currentTimeMillis()
            lastSpeechTime = recordingStartTime
            listener?.onRecordingStarted()

            // Start recording loop in coroutine
            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                recordAudio()
            }

            Log.d(TAG, "Recording started")
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for audio recording", e)
            listener?.onRecordingError("Permission denied")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
            listener?.onRecordingError(e.message ?: "Unknown error")
        }
    }

    /**
     * Recording loop that reads audio data and processes it with VAD.
     */
    private suspend fun recordAudio() {
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )
        val buffer = ShortArray(bufferSize / 2) // PCM16 = 2 bytes per sample

        while (isRecording && isActive) {
            val readCount = audioRecord?.read(buffer, 0, buffer.size) ?: 0

            if (readCount > 0) {
                // Add to buffer
                audioBuffer.addAll(buffer.take(readCount))
                
                // Process with VAD
                vad?.setContinuousSpeechListener(buffer, readCount) { speechActive ->
                    if (speechActive) {
                        lastSpeechTime = System.currentTimeMillis()
                    }
                }

                listener?.onRecording()

                // Check if we should stop recording
                val currentTime = System.currentTimeMillis()
                val silenceDuration = currentTime - lastSpeechTime
                val totalDuration = currentTime - recordingStartTime

                if (totalDuration >= MAX_RECORDING_DURATION_MS) {
                    Log.d(TAG, "Max recording duration reached")
                    stop()
                    break
                }

                if (silenceDuration >= SILENCE_DURATION_MS && audioBuffer.isNotEmpty()) {
                    Log.d(TAG, "Silence detected, stopping recording")
                    stop()
                    break
                }
            } else if (readCount < 0) {
                Log.e(TAG, "Error reading audio: $readCount")
                break
            }

            // Small delay to prevent busy loop
            delay(10)
        }
    }

    /**
     * Stops recording and processes the captured audio.
     */
    fun stop() {
        if (!isRecording) {
            return
        }

        isRecording = false
        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            vad?.stop()
            vad?.close()
            vad = null

            // Convert buffer to FloatArray and store in WhisperRecordBuffer
            val floatSamples = audioBuffer.map { it / 32768.0f }.toFloatArray()
            WhisperRecordBuffer.setOutputBuffer(floatSamples)

            listener?.onRecordingDone()
            Log.d(TAG, "Recording stopped. Captured ${audioBuffer.size} samples (${audioBuffer.size / SAMPLE_RATE}s)")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            listener?.onRecordingError(e.message ?: "Unknown error")
        }
    }

    /**
     * Checks if recording is in progress.
     */
    fun isInProgress(): Boolean = isRecording

    /**
     * Releases all resources.
     */
    fun release() {
        stop()
        audioBuffer.clear()
        WhisperRecordBuffer.clear()
    }
}

