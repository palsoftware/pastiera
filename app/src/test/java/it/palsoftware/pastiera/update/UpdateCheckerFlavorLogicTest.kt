package it.palsoftware.pastiera.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateCheckerFlavorLogicTest {

    @Test
    fun stableChannelUsesLatestNonPrerelease() {
        val release = findLatestRelease(sampleReleases(), "stable")

        requireNotNull(release)
        assertEquals("v0.85", release.tagName)
        assertEquals("Plektra 0.85", release.displayName)
        assertEquals("https://example.com/releases/v0.85", release.releasePageUrl)
    }

    @Test
    fun nightlyChannelUsesLatestNightlyPrerelease() {
        val release = findLatestRelease(sampleReleases(), "nightly")

        requireNotNull(release)
        assertEquals("nightly/v0.85-nightly.20260306.214144", release.tagName)
        assertEquals("Plektra Nightly 0.85", release.displayName)
        assertEquals("https://example.com/releases/nightly-v0.85-nightly.20260306.214144", release.releasePageUrl)
    }

    @Test
    fun nightlyChannelIgnoresNonNightlyPrereleases() {
        val releases = listOf(
            GitHubRelease(
                tagName = "beta/v0.85-beta1",
                name = "Plektra Beta",
                prerelease = true,
                draft = false,
                htmlUrl = "https://example.com/releases/beta"
            )
        )

        assertNull(findLatestRelease(releases, "nightly"))
    }

    @Test
    fun normalizeReleaseVersionStripsKnownPrefixes() {
        assertEquals("0.85", normalizeReleaseVersion("v0.85"))
        assertEquals("0.85", normalizeReleaseVersion("V0.85"))
        assertEquals(
            "0.85-nightly.20260306.214144",
            normalizeReleaseVersion("nightly/v0.85-nightly.20260306.214144")
        )
    }

    private fun sampleReleases(): List<GitHubRelease> =
        listOf(
            GitHubRelease(
                tagName = "nightly/v0.85-nightly.20260306.214144",
                name = "Plektra Nightly 0.85",
                prerelease = true,
                draft = false,
                htmlUrl = "https://example.com/releases/nightly-v0.85-nightly.20260306.214144"
            ),
            GitHubRelease(
                tagName = "v0.85",
                name = "Plektra 0.85",
                prerelease = false,
                draft = false,
                htmlUrl = "https://example.com/releases/v0.85"
            ),
            GitHubRelease(
                tagName = "v0.84",
                name = null,
                prerelease = false,
                draft = false,
                htmlUrl = "https://example.com/releases/v0.84"
            )
        )
}
