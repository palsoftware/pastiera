package it.palsoftware.pastiera.inputmethod.whisper

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.konovalov.vad.webrtc.Vad
import com.konovalov.vad.webrtc.VadWebRTC
import com.konovalov.vad.webrtc.config.FrameSize
import com.konovalov.vad.webrtc.config.Mode
import com.konovalov.vad.webrtc.config.SampleRate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
        private const val SILENCE_DURATION_MS = 800L // Stop after 800ms of silence (like Whisper+)
        private const val VAD_FRAME_SIZE = 480 // 30ms at 16kHz
    }

    interface RecorderListener {
        fun onRecordingStarted()
        fun onRecording()
        fun onRecordingDone()
        fun onRecordingError(error: String)
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var vad: VadWebRTC? = null
    private var listener: RecorderListener? = null
    private var isRecording = false
    private val audioBuffer = mutableListOf<Byte>()
    private var speechDetected = false
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
                .setMode(Mode.VERY_AGGRESSIVE) // Aggressive mode like Whisper+
                .setSilenceDurationMs(SILENCE_DURATION_MS.toInt())
                .setSpeechDurationMs(200) // Minimum speech duration
                .build()
            
            Log.d(TAG, "VAD initialized")
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

            val finalBufferSize = maxOf(bufferSize, VAD_FRAME_SIZE * 2)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                finalBufferSize * 2 // Double buffer for safety
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                listener?.onRecordingError("AudioRecord not initialized")
                return
            }

            audioBuffer.clear()
            speechDetected = false
            audioRecord?.startRecording()
            isRecording = true
            recordingStartTime = System.currentTimeMillis()
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
        val vadBuffer = ByteArray(VAD_FRAME_SIZE * 2) // 16-bit samples
        val readBuffer = ByteArray(VAD_FRAME_SIZE * 2)
        var totalBytesRead = 0
        val maxBytes = SAMPLE_RATE * 2 * 30 // 30 seconds at 16kHz, 16-bit

        while (isRecording && totalBytesRead < maxBytes) {
            val bytesRead = audioRecord?.read(readBuffer, 0, readBuffer.size) ?: 0

            if (bytesRead > 0) {
                // Add to buffer
                audioBuffer.addAll(readBuffer.take(bytesRead).toList())
                totalBytesRead += bytesRead
                
                // Process with VAD if we have enough data
                if (vad != null && audioBuffer.size >= VAD_FRAME_SIZE * 2) {
                    // Get last VAD_FRAME_SIZE * 2 bytes for VAD analysis
                    val startIdx = audioBuffer.size - VAD_FRAME_SIZE * 2
                    for (i in 0 until VAD_FRAME_SIZE * 2) {
                        vadBuffer[i] = audioBuffer[startIdx + i]
                    }
                    
                    val isSpeech = vad?.isSpeech(vadBuffer) ?: false
                    
                    if (isSpeech) {
                        if (!speechDetected) {
                            Log.d(TAG, "VAD: Speech detected, recording starts")
                            speechDetected = true
                        }
                    } else {
                        if (speechDetected) {
                            Log.d(TAG, "VAD: Silence detected after speech, stopping")
                            stop()
                            break
                        }
                    }
                }

                listener?.onRecording()

                // Check max duration
                val currentTime = System.currentTimeMillis()
                val totalDuration = currentTime - recordingStartTime

                if (totalDuration >= MAX_RECORDING_DURATION_MS) {
                    Log.d(TAG, "Max recording duration reached")
                    stop()
                    break
                }
            } else if (bytesRead < 0) {
                Log.e(TAG, "Error reading audio: $bytesRead")
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

            vad?.close()
            vad = null

            // Check minimum recording length (0.2s = 6400 bytes at 16kHz 16-bit)
            if (audioBuffer.size < 6400) {
                listener?.onRecordingError("Recording too short")
                Log.w(TAG, "Recording too short: ${audioBuffer.size} bytes")
                return
            }

            // Convert ByteArray to FloatArray and store in WhisperRecordBuffer
            WhisperRecordBuffer.setOutputBuffer(audioBuffer.toByteArray())

            listener?.onRecordingDone()
            Log.d(TAG, "Recording stopped. Captured ${audioBuffer.size} bytes (${audioBuffer.size / (SAMPLE_RATE * 2)}s)")
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

