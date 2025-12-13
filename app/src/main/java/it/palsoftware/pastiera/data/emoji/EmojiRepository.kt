package it.palsoftware.pastiera.data.emoji

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Loads emoji categories from asset text files.
 * Files live under assets/common/emoji (one emoji per line; first token is the base, the rest are variants).
 * Applies minApi filtering based on minApi.txt where each line starts with the API level followed by emojis.
 *
 * UI-agnostic: can be reused for the picker dialog and future emoji keyboard.
 */
object EmojiRepository {
    private const val EMOJI_ASSET_DIR = "common/emoji"
    private const val MIN_API_FILE = "minApi.txt"

    data class EmojiEntry(val base: String, val variants: List<String>)
    data class EmojiCategory(
        val id: String,
        val displayNameRes: Int?,
        val emojis: List<EmojiEntry>
    )

    private var cachedCategories: List<EmojiCategory>? = null

    suspend fun getEmojiCategories(context: Context): List<EmojiCategory> {
        return cachedCategories ?: loadEmojiCategories(context).also { cachedCategories = it }
    }

    fun clearCache() {
        cachedCategories = null
    }

    /**
     * Utility for future keyboard pagination/chunking without re-parsing assets.
     */
    fun asPaged(
        categories: List<EmojiCategory>,
        pageSize: Int = 50
    ): Map<String, List<List<EmojiEntry>>> {
        return categories.associate { category ->
            category.id to category.emojis.chunked(pageSize)
        }
    }

    private suspend fun loadEmojiCategories(context: Context): List<EmojiCategory> = withContext(Dispatchers.IO) {
        val assetManager = context.assets
        val files = assetManager.list(EMOJI_ASSET_DIR)
            ?.filter { it.endsWith(".txt") && it != MIN_API_FILE }
            .orEmpty()

        // Define custom category order
        val categoryOrder = listOf(
            "SMILEYS_AND_EMOTION.txt",
            "PEOPLE_AND_BODY.txt",
            "ANIMALS_AND_NATURE.txt",
            "FOOD_AND_DRINK.txt",
            "TRAVEL_AND_PLACES.txt",
            "ACTIVITIES.txt",
            "OBJECTS.txt",
            "SYMBOLS.txt",
            "FLAGS.txt"
        )

        val sortedFiles = files.sortedBy { fileName ->
            val index = categoryOrder.indexOf(fileName)
            if (index >= 0) index else Int.MAX_VALUE // Unknown files go to the end
        }

        val minApiMap = loadMinApiMap(context)

        sortedFiles.mapNotNull { fileName ->
            val emojis = parseEmojiFile(context, fileName, minApiMap)
            if (emojis.isEmpty()) return@mapNotNull null
            EmojiCategory(
                id = fileName.substringBefore(".txt"),
                displayNameRes = mapCategoryRes(fileName),
                emojis = emojis
            )
        }
    }

    private fun loadMinApiMap(context: Context): Map<String, Int> {
        val assetPath = "$EMOJI_ASSET_DIR/$MIN_API_FILE"
        return runCatching {
            context.assets.open(assetPath).use { input ->
                BufferedReader(InputStreamReader(input)).lineSequence().flatMap { line ->
                    val parts = line.split(" ").filter { it.isNotBlank() }
                    if (parts.isEmpty()) return@flatMap emptySequence()
                    val api = parts.first().toIntOrNull() ?: return@flatMap emptySequence()
                    parts.drop(1).asSequence().map { emoji -> emoji to api }
                }.toMap()
            }
        }.getOrElse { emptyMap() }
    }

    private fun parseEmojiFile(
        context: Context,
        fileName: String,
        minApiMap: Map<String, Int>
    ): List<EmojiEntry> {
        val assetPath = "$EMOJI_ASSET_DIR/$fileName"
        val sdk = Build.VERSION.SDK_INT

        return runCatching {
            context.assets.open(assetPath).use { input ->
                BufferedReader(InputStreamReader(input)).lineSequence().mapNotNull { line ->
                    val tokens = line.split(" ").filter { it.isNotBlank() }
                    if (tokens.isEmpty()) return@mapNotNull null

                    val base = tokens.first()
                    val variants = tokens.drop(1)

                    val allowedBase = isEmojiAllowed(base, sdk, minApiMap)
                    val allowedVariants = variants.filter { isEmojiAllowed(it, sdk, minApiMap) }

                    if (!allowedBase) {
                        null
                    } else {
                        EmojiEntry(base = base, variants = allowedVariants)
                    }
                }.toList()
            }
        }.getOrElse { emptyList() }
    }

    private fun isEmojiAllowed(emoji: String, sdk: Int, minApiMap: Map<String, Int>): Boolean {
        val minApi = minApiMap[emoji] ?: return true
        return sdk >= minApi
    }

    private fun mapCategoryRes(fileName: String): Int? {
        return when (fileName.substringBefore(".txt")) {
            "SMILEYS_AND_EMOTION" -> it.palsoftware.pastiera.R.string.emoji_category_smileys_and_emotion
            "PEOPLE_AND_BODY" -> it.palsoftware.pastiera.R.string.emoji_category_people_and_body
            "ANIMALS_AND_NATURE" -> it.palsoftware.pastiera.R.string.emoji_category_animals_and_nature
            "FOOD_AND_DRINK" -> it.palsoftware.pastiera.R.string.emoji_category_food_and_drink
            "TRAVEL_AND_PLACES" -> it.palsoftware.pastiera.R.string.emoji_category_travel_and_places
            "ACTIVITIES" -> it.palsoftware.pastiera.R.string.emoji_category_activities
            "OBJECTS" -> it.palsoftware.pastiera.R.string.emoji_category_objects
            "SYMBOLS" -> it.palsoftware.pastiera.R.string.emoji_category_symbols
            "FLAGS" -> it.palsoftware.pastiera.R.string.emoji_category_flags
            else -> null
        }
    }
}

