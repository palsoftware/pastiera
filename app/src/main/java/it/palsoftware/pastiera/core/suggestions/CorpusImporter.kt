package it.palsoftware.pastiera.core.suggestions

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.Normalizer
import java.util.Locale

class CorpusImporter(private val context: Context, private val userStore: UserDictionaryStore) {

    private val tag = "CorpusImporter"

    suspend fun importFromUri(uri: Uri, locale: Locale, onProgress: (Float) -> Unit): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext Result.failure(Exception("Failed to open input stream"))
            
            // Read bytes to detect encoding
            val bytes = inputStream.readBytes()
            inputStream.close()
            
            if (bytes.isEmpty()) return@withContext Result.success(0)
            
            // Simple encoding detection
            val charset = when {
                bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> Charsets.UTF_16LE
                bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> Charsets.UTF_16BE
                else -> Charsets.UTF_8
            }
            
            val reader = bytes.inputStream().bufferedReader(charset)
            val totalSize = bytes.size.toLong()
            var bytesRead = 0L
            var ngramsAdded = 0
            
            // Sliding window for ngrams
            val window = mutableListOf<String>()
            val maxWindowSize = 3
            
            // Pre-compile regex for performance
            val whitespaceRegex = "\\s+".toRegex()
            
            reader.forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                
                // Approximate progress based on line length
                bytesRead += line.toByteArray(charset).size.toLong() + 2 // +2 for potential newline
                if (totalSize > 0) {
                    onProgress((bytesRead.toFloat() / totalSize).coerceAtMost(1.0f))
                }
                
                // Process line: tokenize including punctuation as separate tokens
                val tokens = mutableListOf<String>()
                line.split(whitespaceRegex).forEach { part ->
                    val currentWord = StringBuilder()
                    part.forEach { char ->
                        if (char.isLetterOrDigit() || char == '\'') {
                            currentWord.append(char)
                        } else {
                            if (currentWord.isNotEmpty()) {
                                // IMPORTANT: Use cleanWord (preserving umlauts) instead of normalize
                                tokens.add(currentWord.toString())
                                currentWord.setLength(0)
                            }
                            if (char == ',' || char == '.' || char == '!' || char == '?') {
                                tokens.add(char.toString())
                            }
                        }
                    }
                    if (currentWord.isNotEmpty()) {
                        tokens.add(currentWord.toString())
                    }
                }
                
                for (token in tokens) {
                    if (token.isEmpty()) continue
                    
                    // 1. Unigram (Dictionary) - only for actual words
                    if (token.any { it.isLetterOrDigit() }) {
                        userStore.addWord(context, token, autoPersist = false)
                    }
                    
                    // Update sliding window
                    window.add(token)
                    if (window.size > maxWindowSize) {
                        window.removeAt(0)
                    }
                    
                    // 2. NGrams from window (Words and Punctuation)
                    if (window.size >= 2) {
                        // Bigram
                        val bigramContext = listOf(window[window.size - 2])
                        userStore.addNGram(context, bigramContext, token, autoPersist = false)
                        ngramsAdded++
                    }
                    
                    if (window.size >= 3) {
                        // Trigram
                        val trigramContext = listOf(window[window.size - 3], window[window.size - 2])
                        userStore.addNGram(context, trigramContext, token, autoPersist = false)
                        ngramsAdded++
                    }
                }
            }
            
            // Persist all changes once at the end
            userStore.persistManually(context)
            
            Result.success(ngramsAdded)
        } catch (e: Exception) {
            Log.e(tag, "Error importing corpus", e)
            Result.failure(e)
        }
    }

    private fun normalize(word: String, locale: Locale): String {
        val normalized = Normalizer.normalize(word.lowercase(locale), Normalizer.Form.NFD)
        val withoutAccents = normalized.replace("\\p{Mn}".toRegex(), "")
        return withoutAccents.replace("[^\\p{L}]".toRegex(), "")
    }
}

