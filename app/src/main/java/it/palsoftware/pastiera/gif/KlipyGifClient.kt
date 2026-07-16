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

    fun search(query: String, limit: Int = 24, page: Int = 1): List<KlipyGifResult> {
        val apiKey = resolveApiKey()
        if (apiKey.isBlank() || query.isBlank()) return emptyList()

        val locale = Locale.getDefault().toLanguageTag().ifBlank { "en" }
        val urls = listOf(
            buildSearchUrl(apiKey, query, limit, page, locale, useQParam = true),
            buildSearchUrl(apiKey, query, limit, page, locale, useQParam = false)
        )

        var lastFailure: Exception? = null
        for (url in urls.distinct()) {
            try {
                return executeSearch(url)
            } catch (e: Exception) {
                lastFailure = e
            }
        }
        throw lastFailure ?: IllegalStateException("Klipy search failed")
    }

    private fun resolveApiKey(): String {
        val settingsKey = SettingsManager.getKlipyApiKey(context)
        return if (settingsKey.isNotBlank()) settingsKey else BuildConfig.KLIPY_API_KEY
    }

    private fun buildSearchUrl(
        apiKey: String,
        query: String,
        limit: Int,
        page: Int,
        locale: String,
        useQParam: Boolean
    ): HttpUrl {
        val builder = HttpUrl.Builder()
            .scheme("https")
            .host("api.klipy.com")
            .addPathSegment("v1")
            .addPathSegment(apiKey)
            .addPathSegment("gifs")
            .addPathSegment("search")
            .addQueryParameter(if (useQParam) "q" else "query", query)
            .addQueryParameter("per_page", limit.coerceIn(1, 48).toString())
            .addQueryParameter("page", page.coerceAtLeast(1).toString())
            .addQueryParameter("rating", "pg-13")
            .addQueryParameter("locale", locale)
        return builder.build()
    }

    private fun executeSearch(url: HttpUrl): List<KlipyGifResult> {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Klipy search failed: ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            return parseResults(body)
        }
    }

    private fun parseResults(body: String): List<KlipyGifResult> {
        if (body.isBlank()) return emptyList()

        val root = JSONObject(body)
        val items = root.optJSONArray("data")
            ?: root.optJSONArray("results")
            ?: root.optJSONArray("gifs")
            ?: JSONArray()

        val results = mutableListOf<KlipyGifResult>()
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            parseResult(item)?.let(results::add)
        }
        return results
    }

    private fun parseResult(item: JSONObject): KlipyGifResult? {
        val mediaFormats = item.optJSONObject("media_formats")
        val images = item.optJSONObject("images")

        val gifUrl = firstNonBlank(
            extractFormatUrl(mediaFormats, listOf("gif", "mediumgif", "tinygif", "nanogif")),
            extractImageUrl(images, listOf("original", "fixed_width", "downsized"))
        ) ?: return null

        val previewUrl = firstNonBlank(
            extractFormatUrl(mediaFormats, listOf("tinygifpreview", "nanogifpreview", "gifpreview", "tinygif", "nanogif")),
            extractImageUrl(images, listOf("preview_gif", "fixed_width_still", "downsized_still", "original_still")),
            gifUrl
        ) ?: return null

        val shareUrl = firstNonBlank(
            item.optString("itemurl"),
            item.optString("share_url"),
            item.optString("url"),
            gifUrl
        ) ?: gifUrl

        val title = firstNonBlank(
            item.optString("title"),
            item.optString("content_description"),
            item.optString("slug"),
            "GIF"
        ) ?: "GIF"

        return KlipyGifResult(
            id = item.optString("id", shareUrl),
            title = title,
            previewUrl = previewUrl,
            gifUrl = gifUrl,
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

    private fun firstNonBlank(vararg values: String?): String? {
        return values.firstOrNull { !it.isNullOrBlank() }?.trim()
    }
}
