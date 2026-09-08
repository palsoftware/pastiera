package it.palsoftware.pastiera.update

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class UpdateGithubParserTest {

    @Test
    fun releaseNameIsParsedAndFallsBackToTag() {
        val releases = parseGitHubReleases(
            JSONArray(
                """[
                    {"tag_name":"v2","name":"Plektra 2","prerelease":false,"draft":false,"html_url":"https://example.com/v2"},
                    {"tag_name":"v1","name":"","prerelease":false,"draft":false,"html_url":"https://example.com/v1"}
                ]"""
            )
        )

        assertEquals("Plektra 2", findLatestRelease(releases, "stable")?.displayName)
        assertEquals("v1", findLatestRelease(releases.drop(1), "stable")?.displayName)
    }
}
