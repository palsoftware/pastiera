package it.palsoftware.pastiera.inputmethod.whisper

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * UI Card for displaying transcription usage statistics
 */
@Composable
fun UsageStatsCard(
    modifier: Modifier = Modifier,
    onReset: () -> Unit = {}
) {
    val context = LocalContext.current
    val statsManager = remember { UsageStatsManager(context) }
    
    var totalCost by remember { mutableStateOf(statsManager.getTotalCostUsd()) }
    var tokenCount by remember { mutableStateOf(statsManager.getTotalTokens()) }
    var transcriptionCount by remember { mutableStateOf(statsManager.getTranscriptionCount()) }
    var totalDuration by remember { mutableStateOf(statsManager.getTotalDurationSeconds()) }
    var showModelBreakdown by remember { mutableStateOf(false) }  // Toggle for model stats
    
    // Refresh stats when composable recomposes
    LaunchedEffect(Unit) {
        totalCost = statsManager.getTotalCostUsd()
        tokenCount = statsManager.getTotalTokens()
        transcriptionCount = statsManager.getTranscriptionCount()
        totalDuration = statsManager.getTotalDurationSeconds()
    }
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.BarChart,
                        contentDescription = "Usage Statistics",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Usage Statistics",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                // Reset button
                if (transcriptionCount > 0) {
                    IconButton(
                        onClick = {
                            statsManager.resetStats()
                            totalCost = 0.0
                            tokenCount = 0L
                            transcriptionCount = 0
                            totalDuration = 0L
                            onReset()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Reset stats",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Divider()
            
            // Model Breakdown Toggle Button
            if (transcriptionCount > 0) {
                OutlinedButton(
                    onClick = { showModelBreakdown = !showModelBreakdown },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Text(if (showModelBreakdown) "Hide Model Breakdown ▼" else "Show Model Breakdown ▶")
                }
            }
            
            // Stats Grid
            if (transcriptionCount > 0) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Row 1: Cost and Tokens
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem(
                            label = "Total Cost",
                            value = "${"%.4f".format(totalCost)}",
                            unit = "USD",
                            modifier = Modifier.weight(1f)
                        )
                        
                        StatItem(
                            label = "Total Tokens",
                            value = tokenCount.toString(),
                            unit = "",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    // Row 2: Transcriptions and Duration
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem(
                            label = "Transcriptions",
                            value = transcriptionCount.toString(),
                            unit = "",
                            modifier = Modifier.weight(1f)
                        )
                        
                        StatItem(
                            label = "Total Duration",
                            value = formatDuration(totalDuration),
                            unit = "",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    // Row 3: Cost per transcription and WPM
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem(
                            label = "Avg Cost/Transcription",
                            value = "${"%.5f".format(if (transcriptionCount > 0) totalCost / transcriptionCount else 0.0)}",
                            unit = "USD",
                            modifier = Modifier.weight(1f)
                        )
                        
                        StatItem(
                            label = "Avg WPM",
                            value = calculateAverageWPM(totalDuration, tokenCount),
                            unit = "words/min",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                // Model Breakdown Section
                if (showModelBreakdown) {
                    Divider(modifier = Modifier.padding(vertical = 12.dp))
                    
                    Text(
                        text = "Usage by Model",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                    
                    // Get model breakdown from shared preferences
                    val modelStats = getModelBreakdownStats(context)
                    
                    if (modelStats.isEmpty()) {
                        Text(
                            text = "No model data available",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp)
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            modelStats.forEach { (model, count, words, cost) ->
                                ModelBreakdownItem(
                                    model = model,
                                    count = count,
                                    words = words,
                                    cost = cost
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "No transcriptions yet. Start using speech recognition to see statistics.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            }
        }
    }
}

/**
 * Individual stat display item
 */
@Composable
private fun StatItem(
    label: String,
    value: String,
    unit: String = "",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        if (unit.isNotEmpty()) {
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Format duration in seconds to readable format
 */
private fun formatDuration(seconds: Long): String {
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }
}

/**
 * Calculate average Words Per Minute
 * tokenCount is actually wordCount now, not tokens
 */
private fun calculateAverageWPM(totalDurationSeconds: Long, wordCount: Long): String {
    if (totalDurationSeconds <= 0 || wordCount <= 0) return "0.0"
    val minutes = totalDurationSeconds / 60.0
    val wpm = wordCount / minutes
    return "${"%.1f".format(wpm)}"
}

/**
 * Get model breakdown statistics from SharedPreferences
 * Returns list of (model, count, words, cost)
 */
private fun getModelBreakdownStats(context: android.content.Context): List<Tuple4<String, Int, Int, Double>> {
    val prefs = context.getSharedPreferences("usage_stats", android.content.Context.MODE_PRIVATE)
    val allModels = prefs.all
    
    val modelStats = mutableMapOf<String, Tuple3<Int, Int, Double>>()  // model -> (count, words, cost)
    
    // Parse all stored stats to aggregate by model
    for ((key, value) in allModels) {
        if (key.startsWith("model_")) {
            // Format: model_[modelName]_count, model_[modelName]_words, model_[modelName]_cost
            val parts = key.split("_")
            if (parts.size >= 3) {
                val modelName = parts.drop(1).dropLast(1).joinToString("_")
                val field = parts.last()
                
                when (field) {
                    "count" -> {
                        val current = modelStats[modelName] ?: Tuple3(0, 0, 0.0)
                        modelStats[modelName] = Tuple3((value as? Long)?.toInt() ?: 0, current.second, current.third)
                    }
                    "words" -> {
                        val current = modelStats[modelName] ?: Tuple3(0, 0, 0.0)
                        modelStats[modelName] = Tuple3(current.first, (value as? Long)?.toInt() ?: 0, current.third)
                    }
                    "cost" -> {
                        val current = modelStats[modelName] ?: Tuple3(0, 0, 0.0)
                        modelStats[modelName] = Tuple3(current.first, current.second, (value as? Float)?.toDouble() ?: 0.0)
                    }
                }
            }
        }
    }
    
    return modelStats.map { (model, stats) -> 
        Tuple4(model, stats.first, stats.second, stats.third)
    }.sortedByDescending { it.second }  // Sort by count (descending)
}

// Simple tuple classes
data class Tuple3<A, B, C>(val first: A, val second: B, val third: C)
data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

/**
 * Composable for displaying a single model's breakdown
 */
@Composable
private fun ModelBreakdownItem(
    model: String,
    count: Int,
    words: Int,
    cost: Double
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = model,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "$count ×",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$words words",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (cost > 0) {
                        Text(
                            text = "${"%.4f".format(cost)} USD",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

