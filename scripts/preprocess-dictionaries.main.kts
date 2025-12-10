@file:DependsOn("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
@file:DependsOn("org.json:json:20240303")

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.util.Locale
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable

@Serializable
data class SerializableDictionaryEntry(
    val word: String,
    val frequency: Int,
    val source: Int
)

@Serializable
data class DictionaryIndex(
    val normalizedIndex: Map<String, List<SerializableDictionaryEntry>>,
    val prefixCache: Map<String, List<SerializableDictionaryEntry>>
)

fun normalize(word: String, locale: Locale = Locale.ITALIAN): String {
    val normalized = Normalizer.normalize(word.lowercase(locale), Normalizer.Form.NFD)
    val withoutAccents = normalized.replace("\\p{Mn}".toRegex(), "")
    return withoutAccents.replace("[^\\p{L}]".toRegex(), "")
}

fun main(args: Array<String>) {
    // Create log file to capture output IMMEDIATELY
    val logFile = File("dictionary-preprocess.log")
    val logMessages = mutableListOf<String>()
    
    // Write initial message immediately to verify script is running
    try {
        logFile.writeText("Script started at: ${java.util.Date()}\n")
        logFile.appendText("Working directory: ${System.getProperty("user.dir")}\n")
        logFile.appendText("Arguments: ${args.joinToString()}\n\n")
    } catch (e: Exception) {
        System.err.println("CRITICAL: Cannot write log file: ${e.message}")
    }
    
    fun log(message: String) {
        println(message)
        logMessages.add(message)
        // Append to log file immediately
        try {
            logFile.appendText("$message\n")
        } catch (e: Exception) {
            // Ignore write errors, but continue
        }
    }
    
    log("=".repeat(60))
    log("SCRIPT STARTED")
    log("Arguments: ${args.joinToString()}")
    log("Working directory: ${System.getProperty("user.dir")}")
    log("")
    
    // Try to find project root automatically
    val projectRoot = if (args.isNotEmpty()) {
        args[0]
    } else {
        // Find project root by looking for the dictionaries directory
        // Start from current working directory and go up until we find it
        var currentDir = File(System.getProperty("user.dir"))
        var found = false
        var root: File? = null
        
        // Try up to 5 levels up
        for (i in 0..5) {
            val testDir = File(currentDir, "app/src/main/assets/common/dictionaries")
            if (testDir.exists() && testDir.isDirectory) {
                root = currentDir
                found = true
                break
            }
            val parent = currentDir.parentFile
            if (parent == null || parent == currentDir) break
            currentDir = parent
        }
        
        if (found && root != null) {
            root.absolutePath
        } else {
            // Fallback: use current directory
            val fallback = System.getProperty("user.dir")
            log("Warning: Could not auto-detect project root, using: $fallback")
            fallback
        }
    }
    
    val dictionariesDir = File(projectRoot, "app/src/main/assets/common/dictionaries")
    val outputDir = File(projectRoot, "app/src/main/assets/common/dictionaries_serialized")
    
    log("=".repeat(60))
    log("Dictionary Pre-processing Script")
    log("=".repeat(60))
    log("Project root: $projectRoot")
    log("Dictionaries dir: ${dictionariesDir.absolutePath}")
    log("Output dir: ${outputDir.absolutePath}")
    log("Dictionaries dir exists: ${dictionariesDir.exists()}")
    log("Dictionaries dir is directory: ${dictionariesDir.isDirectory}")
    log("=".repeat(60))
    log("")
    
    if (!dictionariesDir.exists()) {
        log("ERROR: Dictionaries directory not found: ${dictionariesDir.absolutePath}")
        log("Current working directory: ${System.getProperty("user.dir")}")
        log("")
        log("Please run the script with the project root as argument:")
        log("  kotlinc -script scripts/preprocess-dictionaries.main.kts <project-root>")
        log("  Or set the working directory to the project root before running.")
        logFile.writeText(logMessages.joinToString("\n"))
        return
    }
    
    if (!dictionariesDir.isDirectory) {
        log("ERROR: Dictionaries path is not a directory: ${dictionariesDir.absolutePath}")
        logFile.writeText(logMessages.joinToString("\n"))
        return
    }
    
    outputDir.mkdirs()
    log("Output directory created/verified: ${outputDir.absolutePath}")
    log("")
    
    val jsonFiles = dictionariesDir.listFiles { _, name -> name.endsWith("_base.json") }
    
    log("Found ${jsonFiles?.size ?: 0} JSON files in ${dictionariesDir.absolutePath}")
    jsonFiles?.forEach { file ->
        log("  - ${file.name}")
    }
    log("")
    
    if (jsonFiles == null || jsonFiles.isEmpty()) {
        log("ERROR: No dictionary JSON files found in ${dictionariesDir.absolutePath}")
        log("Looking for files matching pattern: *_base.json")
        val allFiles = dictionariesDir.listFiles()
        if (allFiles != null && allFiles.isNotEmpty()) {
            log("Files found in directory:")
            allFiles.forEach { file ->
                log("  - ${file.name} (${if (file.isDirectory) "dir" else "file"})")
            }
        } else {
            log("Directory is empty or not accessible")
        }
        logFile.writeText(logMessages.joinToString("\n"))
        return
    }
    
    log("Pre-processing dictionaries...\n")
    
    jsonFiles.forEach { jsonFile ->
        val language = jsonFile.nameWithoutExtension.replace("_base", "")
        log("Processing $language dictionary...")
        
        try {
            val jsonString = jsonFile.readText()
            val jsonArray = JSONArray(jsonString)
            
            val entries = mutableListOf<Pair<String, Int>>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                // IMPORTANT: Preserve original case (uppercase/lowercase) from JSON
                // e.g., {"w": "Mario", "f": 100} -> word="Mario" (not "mario")
                val word = obj.getString("w")  // Original case preserved
                val freq = obj.optInt("f", 1)
                entries.add(Pair(word, freq))
            }
            
            log("  Loaded ${entries.size} entries")
            
            val normalizedIndex = mutableMapOf<String, MutableList<SerializableDictionaryEntry>>()
            val prefixCache = mutableMapOf<String, MutableList<SerializableDictionaryEntry>>()
            val cachePrefixLength = 4
            
            val locale = try {
                Locale(language)
            } catch (e: Exception) {
                Locale.ITALIAN
            }
            
            entries.forEach { (word, freq) ->
                // normalize() only converts to lowercase for indexing purposes
                val normalized = normalize(word, locale)  // lowercase for indexing only
                
                // Save original word with case preserved for dictionary entry
                val normalizedBucket = normalizedIndex.getOrPut(normalized) { mutableListOf() }
                normalizedBucket.add(SerializableDictionaryEntry(word, freq, 0))
                
                val maxPrefixLength = normalized.length.coerceAtMost(cachePrefixLength)
                for (length in 1..maxPrefixLength) {
                    val prefix = normalized.take(length)
                    val prefixList = prefixCache.getOrPut(prefix) { mutableListOf() }
                    // Original word case preserved (e.g., "Mario", "Roma", "casa")
                    prefixList.add(SerializableDictionaryEntry(word, freq, 0))
                }
            }
            
            prefixCache.values.forEach { list ->
                list.sortByDescending { it.frequency }
            }
            
            val serializableIndex = DictionaryIndex(
                normalizedIndex = normalizedIndex,
                prefixCache = prefixCache
            )
            
            val json = Json {
                prettyPrint = false
                ignoreUnknownKeys = true
            }
            // Use inline reified serializer function with explicit type
            val serialized = json.encodeToString(kotlinx.serialization.serializer<DictionaryIndex>(), serializableIndex)
            
            val outputFile = File(outputDir, "${language}_base.dict")
            outputFile.writeText(serialized)
            
            val originalSize = jsonFile.length()
            val newSize = outputFile.length()
            val compressionRatio = (1.0 - newSize.toDouble() / originalSize) * 100
            
            log("  Created ${outputFile.name}")
            log("  Size: ${originalSize / 1024}KB -> ${newSize / 1024}KB (${String.format("%.1f", compressionRatio)}% reduction)")
            log("  Indexes: ${normalizedIndex.size} normalized, ${prefixCache.size} prefixes\n")
        } catch (e: Exception) {
            log("  ERROR processing ${jsonFile.name}: ${e.message}")
            e.printStackTrace()
            log("  Stack trace: ${e.stackTraceToString()}")
        }
    }
    
    log("Dictionary preprocessing completed!")
    log("=".repeat(60))
    log("Check output directory: ${outputDir.absolutePath}")
    val generatedFiles = outputDir.listFiles { _, name -> name.endsWith(".dict") }
    log("Generated ${generatedFiles?.size ?: 0} .dict files")
    generatedFiles?.forEach { file ->
        log("  ✓ ${file.name} (${file.length() / 1024}KB)")
    }
    log("=".repeat(60))
    
    // Write log to file IMMEDIATELY (even if script fails)
    try {
        val logContent = logMessages.joinToString("\n")
        logFile.writeText(logContent)
        // Also write to System.err to ensure it's visible
        System.err.println("Log written to: ${logFile.absolutePath}")
        System.err.println("Total log lines: ${logMessages.size}")
    } catch (e: Exception) {
        System.err.println("CRITICAL: Failed to write log file: ${e.message}")
        e.printStackTrace()
    }
}

