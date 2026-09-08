package it.palsoftware.pastiera.update

import org.json.JSONArray

internal data class GitHubRelease(
    val tagName: String,
    val name: String?,
    val prerelease: Boolean,
    val draft: Boolean,
    val htmlUrl: String?,
    val downloadUrl: String? = null
)

internal data class ReleaseInfo(
    val tagName: String,
    val displayName: String,
    val releasePageUrl: String?,
    val downloadUrl: String? = null
)

internal fun parseGitHubReleases(releases: JSONArray): List<GitHubRelease> =
    buildList {
        for (index in 0 until releases.length()) {
            val release = releases.optJSONObject(index) ?: continue
            val tagName = release.optString("tag_name").takeIf(String::isNotBlank) ?: continue

            add(
                GitHubRelease(
                    tagName = tagName,
                    name = release.optString("name").takeIf(String::isNotBlank),
                    prerelease = release.optBoolean("prerelease"),
                    draft = release.optBoolean("draft"),
                    htmlUrl = release.optString("html_url").takeIf(String::isNotBlank),
                    downloadUrl = release.optJSONArray("assets")?.let { assets ->
                        (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
                            .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                            ?.optString("browser_download_url")?.takeIf { it.startsWith("https://") }
                    }
                )
            )
        }
    }

internal fun findLatestRelease(releases: List<GitHubRelease>, releaseChannel: String): ReleaseInfo? {
    val normalizedChannel = releaseChannel.lowercase()

    for (release in releases) {
        if (release.draft) continue

        val matchesChannel = when (normalizedChannel) {
            "nightly" -> release.prerelease && release.tagName.startsWith("nightly/")
            else -> !release.prerelease
        }
        if (!matchesChannel) continue

        return ReleaseInfo(
            tagName = release.tagName,
            displayName = release.name?.takeIf(String::isNotBlank) ?: release.tagName,
            releasePageUrl = release.htmlUrl?.takeIf(String::isNotBlank)
        )
    }

    return null
}

internal fun normalizeReleaseVersion(version: String): String =
    version.removePrefix("nightly/").removePrefix("v").removePrefix("V")
