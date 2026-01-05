package it.palsoftware.pastiera.inputmethod.whisper

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Data class for tracking transcription usage statistics
 */
data class TranscriptionStats(
    val id: String = "",                    // Unique ID for this transcription
    val engine: String = "",                // "openai" or "openrouter"
    val model: String = "",                 // Model name used (e.g. "gpt-4o", "google/gemini-2.5-flash")
    val timestamp: String = "",             // ISO 8601 timestamp
    val audioLengthMs: Long = 0L,          // Duration of audio in milliseconds
    val textLength: Int = 0,                 // Character count of result
    val wordCount: Int = 0,                 // Word count of result (calculated from spaces)
    val costUsd: Double = 0.0,              // Cost in USD
    val successFul: Boolean = false         // Whether transcription was successful
) {
    /**
     * Calculate Words Per Minute
     */
    fun getWordsPerMinute(): Double {
        if (audioLengthMs <= 0 || wordCount <= 0) return 0.0
        val minutes = audioLengthMs / 1000.0 / 60.0
        return wordCount / minutes
    }
    
    /**
     * Calculate Characters Per Second
     */
    fun getCharactersPerSecond(): Double {
        if (audioLengthMs <= 0 || textLength <= 0) return 0.0
        val seconds = audioLengthMs / 1000.0
        return textLength / seconds
    }
}

/**
 * Aggregated statistics by model
 */
data class ModelStats(
    val model: String = "",
    val transcriptionCount: Int = 0,
    val totalWords: Int = 0,
    val totalCostUsd: Double = 0.0,
    val totalDurationSeconds: Long = 0L,
    val averageWPM: Double = 0.0
)

/**
 * Manages transcription usage statistics persistence
 */
class UsageStatsManager(private val context: Context) {
    companion object {
        private const val PREFS_NAME = "usage_stats"
        private const val KEY_TOTAL_COST = "total_cost_usd"
        private const val KEY_TOTAL_TOKENS = "total_tokens"
        private const val KEY_TRANSCRIPTION_COUNT = "transcription_count"
        private const val KEY_TOTAL_DURATION = "total_duration_ms"
        private const val KEY_STATS_LIST = "stats_list"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Add a transcription to the stats
     */
    fun addStat(stat: TranscriptionStats) {
        if (!stat.successFul) return
        
        // Update totals
        val currentCost = prefs.getFloat(KEY_TOTAL_COST, 0f).toDouble()
        val currentTokens = prefs.getLong(KEY_TOTAL_TOKENS, 0L)
        val currentCount = prefs.getInt(KEY_TRANSCRIPTION_COUNT, 0)
        val currentDuration = prefs.getLong(KEY_TOTAL_DURATION, 0L)
        
        prefs.edit().apply {
            putFloat(KEY_TOTAL_COST, (currentCost + stat.costUsd).toFloat())
            putLong(KEY_TOTAL_TOKENS, currentTokens + stat.wordCount)  // Store word count here (not tokens)
            putInt(KEY_TRANSCRIPTION_COUNT, currentCount + 1)
            putLong(KEY_TOTAL_DURATION, currentDuration + stat.audioLengthMs)
            apply()
        }
    }
    
    /**
     * Get total cost in USD
     */
    fun getTotalCostUsd(): Double {
        return prefs.getFloat(KEY_TOTAL_COST, 0f).toDouble()
    }
    
    /**
     * Get total tokens used
     */
    fun getTotalTokens(): Long {
        return prefs.getLong(KEY_TOTAL_TOKENS, 0L)
    }
    
    /**
     * Get number of transcriptions
     */
    fun getTranscriptionCount(): Int {
        return prefs.getInt(KEY_TRANSCRIPTION_COUNT, 0)
    }
    
    /**
     * Get total audio duration in seconds
     */
    fun getTotalDurationSeconds(): Long {
        return prefs.getLong(KEY_TOTAL_DURATION, 0L) / 1000
    }
    
    /**
     * Get average WPM across all transcriptions
     */
    fun getAverageWPM(): Double {
        val count = getTranscriptionCount()
        if (count == 0) return 0.0
        
        val totalDurationMin = getTotalDurationSeconds() / 60.0
        if (totalDurationMin <= 0) return 0.0
        
        // Estimate: ~5 characters per word average in transcript
        val estimatedWords = getTotalCostUsd() * 100000  // Placeholder - needs actual word count
        return estimatedWords / totalDurationMin
    }
    
    /**
     * Reset all stats
     */
    fun resetStats() {
        prefs.edit().clear().apply()
    }
}

