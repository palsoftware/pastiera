package it.palsoftware.pastiera.inputmethod.whisper

/**
 * Represents available Whisper models for speech recognition.
 */
enum class WhisperModel(
    val displayName: String,
    val fileName: String,
    val sizeBytes: Long,
    val isMultilingual: Boolean,
    val description: String
) {
    TINY_EN(
        displayName = "Tiny (English only)",
        fileName = "whisper-tiny.en.tflite",
        sizeBytes = 42 * 1024 * 1024, // ~42 MB
        isMultilingual = false,
        description = "Fast, English only, good for clear speech"
    ),
    BASE(
        displayName = "Base (Multilingual)",
        fileName = "whisper-base.TOP_WORLD.tflite",
        sizeBytes = 108 * 1024 * 1024, // ~108 MB
        isMultilingual = true,
        description = "Balanced quality and speed, supports top world languages"
    ),
    SMALL(
        displayName = "Small (Multilingual)",
        fileName = "whisper-small.tflite",
        sizeBytes = 388 * 1024 * 1024, // ~388 MB
        isMultilingual = true,
        description = "Excellent quality, may be slower on some devices"
    );

    companion object {
        fun fromFileName(fileName: String): WhisperModel? {
            return values().firstOrNull { it.fileName == fileName }
        }
    }
}

/**
 * Download URLs for Whisper models from Hugging Face.
 */
object WhisperModelUrls {
    private const val BASE_URL = "https://huggingface.co/DocWolle/whisper_tflite_models/resolve/main"
    
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
    val confidence: Float = 1.0f
)

