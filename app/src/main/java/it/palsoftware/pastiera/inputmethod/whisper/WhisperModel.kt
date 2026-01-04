package it.palsoftware.pastiera.inputmethod.whisper

/**
 * Represents available Whisper ONNX models for speech recognition.
 * All models are from DocWolle's curated ONNX-accelerated collection:
 * https://huggingface.co/DocWolle/whisperOnnx
 * 
 * Optimized for mobile with INT8 quantization and KV-Cache support.
 */
enum class WhisperModel(
    val displayName: String,
    val fileName: String,
    val sizeBytes: Long,
    val isMultilingual: Boolean,
    val description: String
) {
    SMALL(
        displayName = "Small (Multilingual) - Recommended",
        fileName = "whisper_small_int8.zip",
        sizeBytes = 243 * 1024 * 1024, // 243 MB
        isMultilingual = true,
        description = "Excellent quality, INT8 quantized, supports 99 languages"
    );

    companion object {
        fun fromFileName(fileName: String): WhisperModel? {
            return values().firstOrNull { it.fileName == fileName }
        }
    }
}

/**
 * Download URLs for Whisper models from Hugging Face.
 * Models are from DocWolle's curated ONNX-accelerated collection:
 * https://huggingface.co/DocWolle/whisperOnnx
 */
object WhisperModelUrls {
    private const val BASE_URL = "https://huggingface.co/DocWolle/whisperOnnx/resolve/main"
    
    fun getDownloadUrl(model: WhisperModel): String {
        return "$BASE_URL/${model.fileName}"
    }
    
    const val VOCAB_MULTILINGUAL_URL = "$BASE_URL/filters_vocab_multilingual.bin"
    const val VOCAB_EN_URL = "$BASE_URL/filters_vocab_en.bin"
}

/**
 * Result from Whisper speech recognition.
 */
data class WhisperResult(
    val text: String,
    val language: String,
    val confidence: Float = 1.0f,
    val model: String = "unknown",
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val costUsd: Double = 0.0
)

data class UsageInfo(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val costUsd: Double = 0.0
)

