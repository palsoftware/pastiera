package it.palsoftware.pastiera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlavorBuildConfigTest {

    @Test
    fun releaseChannelBuildConfigMatchesFlavor() {
        assertTrue(BuildConfig.SUCCESSOR_GITHUB_REPOSITORY.matches(Regex("[^/]+/[^/]+")))
        when (BuildConfig.RELEASE_CHANNEL) {
            "stable" -> {
                assertEquals(BuildConfig.IS_FDROID_BUILD, !BuildConfig.ENABLE_GITHUB_UPDATE_CHECKS)
                assertFalse(BuildConfig.VERSION_NAME.contains("nightly"))
                assertEquals("Ver. ${BuildConfig.VERSION_NAME} - Stable", BuildInfo.getBuildInfoString())
            }
            "nightly" -> {
                assertEquals(BuildConfig.IS_FDROID_BUILD, !BuildConfig.ENABLE_GITHUB_UPDATE_CHECKS)
                assertTrue(BuildConfig.VERSION_NAME.contains("nightly"))
                assertEquals("Ver. ${BuildConfig.VERSION_NAME} - Nightly", BuildInfo.getBuildInfoString())
            }
            else -> error("Unexpected release channel: ${BuildConfig.RELEASE_CHANNEL}")
        }
    }
}
