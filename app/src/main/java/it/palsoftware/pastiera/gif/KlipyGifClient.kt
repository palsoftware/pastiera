package it.palsoftware.pastiera.gif

import android.content.Context
import it.palsoftware.pastiera.BuildConfig
import it.palsoftware.pastiera.SettingsManager
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class KlipyGifClient(
    private val context: Context,
    private val okHttpClient: OkHttpClient = OkHttpClient()
) {

    fun hasConfiguredApiKey(): Boolean = resolveApiKey().isNotBlank()

    fun search(
        query: String,
        mediaType: KlipyMediaType = KlipyMediaType.GIF,
        limit: Int = 24,
        page: Int = 1
    ): List<KlipyGifResult> {
        val apiKey = resolveApiKey()
        if (apiKey.isBlank() || query.isBlank()) return emptyList()

        val locale = Locale.getDefault().toLanguageTag().ifBlank { "en" }
        val urls = listOf(
            buildSearchUrl(apiKey, query, mediaType, limit, page, locale, useQParam = true),
            buildSearchUrl(apiKey, query, mediaType, limit, page, locale, useQParam = false)
        )

        var lastFailure: Exception? = null
        for (url in urls.distinct()) {
            try {
                return executeSearch(url, mediaType)
            } catch (e: Exception) {
                lastFailure = e
            }
        }
        throw lastFailure ?: IllegalStateException("Klipy search failed")
    }

    fun trending(
        mediaType: KlipyMediaType = KlipyMediaType.GIF,
        limit: Int = 24,
        page: Int = 1
    ): List<KlipyGifResult> {
        val apiKey = resolveApiKey()
        if (apiKey.isBlank()) return emptyList()

        val locale = Locale.getDefault().toLanguageTag().ifBlank { "en" }
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("api.klipy.com")
            .addPathSegment("api")
            .addPathSegment("v1")
            .addPathSegment(apiKey)
            .addPathSegment(mediaType.apiPathSegment)
            .addPathSegment("trending")
            .addQueryParameter("per_page", limit.coerceIn(1, 48).toString())
            .addQueryParameter("page", page.coerceAtLeast(1).toString())
            .addQueryParameter("rating", "pg-13")
            .addQueryParameter("locale", locale)
            .build()
        return executeSearch(url, mediaType)
    }

    private fun resolveApiKey(): String {
        val settingsKey = SettingsManager.getKlipyApiKey(context)
        return if (settingsKey.isNotBlank()) settingsKey else BuildConfig.KLIPY_API_KEY
    }

    private fun buildSearchUrl(
        apiKey: String,
        query: String,
        mediaType: KlipyMediaType,
        limit: Int,
        page: Int,
        locale: String,
        useQParam: Boolean
    ): HttpUrl {
        val builder = HttpUrl.Builder()
            .scheme("https")
            .host("api.klipy.com")
            .addPathSegment("api")
            .addPathSegment("v1")
            .addPathSegment(apiKey)
            .addPathSegment(mediaType.apiPathSegment)
            .addPathSegment("search")
            .addQueryParameter(if (useQParam) "q" else "query", query)
            .addQueryParameter("per_page", limit.coerceIn(1, 48).toString())
            .addQueryParameter("page", page.coerceAtLeast(1).toString())
            .addQueryParameter("rating", "pg-13")
            .addQueryParameter("locale", locale)
        return builder.build()
    }

    private fun executeSearch(url: HttpUrl, mediaType: KlipyMediaType): List<KlipyGifResult> {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Klipy search failed: ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            return parseResults(body, mediaType)
        }
    }

    private fun parseResults(body: String, mediaType: KlipyMediaType): List<KlipyGifResult> {
        if (body.isBlank()) return emptyList()

        val root = JSONObject(body)
        val nestedData = root.optJSONObject("data")
        val items = nestedData?.optJSONArray("data")
            ?: root.optJSONArray("data")
            ?: root.optJSONArray("results")
            ?: root.optJSONArray("gifs")
            ?: JSONArray()

        val results = mutableListOf<KlipyGifResult>()
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            parseResult(item, mediaType)?.let(results::add)
        }
        return results
    }

    private fun parseResult(item: JSONObject, mediaType: KlipyMediaType): KlipyGifResult? {
        val mediaFormats = item.optJSONObject("media_formats")
        val images = item.optJSONObject("images")
        val file = item.optJSONObject("file")

        val mediaUrl = firstNonBlank(
            extractFormatUrl(mediaFormats, listOf("gif", "mediumgif", "tinygif", "nanogif")),
            extractFormatUrl(mediaFormats, listOf("webp", "tinywebp", "nanowebp")),
            extractFormatUrl(mediaFormats, listOf("jpg", "png")),
            extractImageUrl(images, listOf("original", "fixed_width", "downsized")),
            extractFileUrl(file, listOf("sm", "md", "hd", "xs"), "gif"),
            extractFileUrl(file, listOf("sm", "md", "hd", "xs"), "webp"),
            extractFileUrl(file, listOf("sm", "md", "hd", "xs"), "png"),
            extractFileUrl(file, listOf("sm", "md", "hd", "xs"), "jpg"),
            extractFileUrl(file, listOf("sm", "md", "hd", "xs"), "jpeg")
        ) ?: return null

        val previewUrl = firstNonBlank(
            extractFormatUrl(mediaFormats, listOf("tinygifpreview", "nanogifpreview", "gifpreview", "tinygif", "nanogif")),
            extractFormatUrl(mediaFormats, listOf("tinywebp", "nanowebp", "webp", "jpg", "png")),
            extractImageUrl(images, listOf("preview_gif", "fixed_width_still", "downsized_still", "original_still")),
            extractFileUrl(file, listOf("sm", "xs", "md", "hd"), "gif"),
            extractFileUrl(file, listOf("sm", "xs", "md", "hd"), "webp"),
            extractFileUrl(file, listOf("sm", "xs", "md", "hd"), "png"),
            extractFileUrl(file, listOf("sm", "xs", "md", "hd"), "jpg"),
            extractFileUrl(file, listOf("sm", "xs", "md", "hd"), "jpeg"),
            mediaUrl
        ) ?: return null

        val shareUrl = firstNonBlank(
            item.optString("itemurl"),
            item.optString("share_url"),
            item.optString("url"),
            mediaUrl
        ) ?: mediaUrl

        val title = firstNonBlank(
            item.optString("title"),
            item.optString("content_description"),
            item.optString("slug"),
            mediaType.displayName.dropLast(1)
        ) ?: mediaType.displayName.dropLast(1)

        val mimeType = inferMimeType(mediaUrl, mediaType)

        return KlipyGifResult(
            id = item.optString("id", shareUrl),
            title = title,
            mediaType = mediaType,
            previewUrl = previewUrl,
            gifUrl = mediaUrl,
            mimeType = mimeType,
            shareUrl = shareUrl
        )
    }

    private fun extractFormatUrl(mediaFormats: JSONObject?, names: List<String>): String? {
        mediaFormats ?: return null
        names.forEach { formatName ->
            val candidate = mediaFormats.optJSONObject(formatName)?.optString("url")
            if (!candidate.isNullOrBlank()) {
                return candidate
            }
        }
        return null
    }

    private fun extractImageUrl(images: JSONObject?, names: List<String>): String? {
        images ?: return null
        names.forEach { imageName ->
            val candidate = images.optJSONObject(imageName)?.optString("url")
            if (!candidate.isNullOrBlank()) {
                return candidate
            }
        }
        return null
    }

    private fun extractFileUrl(file: JSONObject?, sizeNames: List<String>, formatName: String): String? {
        file ?: return null
        sizeNames.forEach { sizeName ->
            val sizeObject = file.optJSONObject(sizeName) ?: return@forEach
            val candidate = sizeObject.optJSONObject(formatName)?.optString("url")
            if (!candidate.isNullOrBlank()) {
                return candidate
            }
        }
        return null
    }

    private fun firstNonBlank(vararg values: String?): String? {
        return values.firstOrNull { !it.isNullOrBlank() }?.trim()
    }

    private fun inferMimeType(url: String, mediaType: KlipyMediaType): String {
        val normalized = url.lowercase(Locale.ROOT)
        return when {
            normalized.endsWith(".gif") -> "image/gif"
            normalized.endsWith(".webp") -> "image/webp"
            normalized.endsWith(".png") -> "image/png"
            normalized.endsWith(".jpg") || normalized.endsWith(".jpeg") -> "image/jpeg"
            mediaType == KlipyMediaType.GIF || mediaType == KlipyMediaType.STICKER -> "image/gif"
            else -> "image/jpeg"
        }
    }
}
