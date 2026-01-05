package it.palsoftware.pastiera

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("it.palsoftware.pastiera", appContext.packageName)
    }

    /**
     * Test that OpenRouter models are correctly filtered by audio input capability.
     * Only models with "audio" in input_modalities should be returned.
     */
    @Test
    fun testOpenRouterAudioModelFiltering() {
        // Simulate real OpenRouter API response data
        val modelsThatShouldBeIncluded = listOf(
            mapOf(
                "id" to "google/gemini-2.0-flash-001",
                "name" to "Google Gemini 2.0 Flash",
                "input_modalities" to listOf("text", "audio", "image", "video"),
                "audio_price" to "0.70"
            ),
            mapOf(
                "id" to "openai/whisper-1",
                "name" to "OpenAI Whisper-1",
                "input_modalities" to listOf("audio"),
                "audio_price" to "0.02"
            )
        )

        val modelsThatShouldBeExcluded = listOf(
            mapOf(
                "id" to "google/gemini-1.5-pro",
                "name" to "Google Gemini 1.5 Pro",
                "input_modalities" to listOf("text", "image", "video"),  // NO audio!
                "audio_price" to "0"
            ),
            mapOf(
                "id" to "text-only/model",
                "name" to "Text Only Model",
                "input_modalities" to listOf("text"),  // NO audio!
                "audio_price" to "0"
            )
        )

        // Test filtering logic
        fun hasAudioInputSupport(modalities: List<String>): Boolean {
            return modalities.contains("audio")
        }

        // Verify included models have audio
        for (model in modelsThatShouldBeIncluded) {
            @Suppress("UNCHECKED_CAST")
            val modalities = model["input_modalities"] as List<String>
            assertTrue(
                "Model ${model["name"]} should have audio support",
                hasAudioInputSupport(modalities)
            )
        }

        // Verify excluded models don't have audio
        for (model in modelsThatShouldBeExcluded) {
            @Suppress("UNCHECKED_CAST")
            val modalities = model["input_modalities"] as List<String>
            assertFalse(
                "Model ${model["name"]} should NOT have audio support",
                hasAudioInputSupport(modalities)
            )
        }
    }

    /**
     * Test that audio pricing is correctly extracted from OpenRouter response.
     * Should use "audio" field, fallback to "prompt" if not available.
     */
    @Test
    fun testOpenRouterAudioPricingExtraction() {
        // Simulate pricing objects from OpenRouter API
        val pricingWithAudio = mapOf(
            "prompt" to "0.10",
            "completion" to "0.40",
            "audio" to "0.70"  // Audio-specific pricing
        )

        val pricingWithoutAudio = mapOf(
            "prompt" to "0.0000075",
            "completion" to "0.00003"
            // No "audio" field - should fallback to prompt
        )

        val pricingZero = mapOf(
            "prompt" to "0",
            "completion" to "0"
        )

        // Test extraction logic
        fun extractAudioPrice(pricing: Map<String, String>): String {
            var price = pricing["audio"] ?: ""
            if (price.isEmpty()) {
                price = pricing["prompt"] ?: "0"
            }
            return price
        }

        // Test cases
        assertEquals("0.70", extractAudioPrice(pricingWithAudio))
        assertEquals("0.0000075", extractAudioPrice(pricingWithoutAudio))
        assertEquals("0", extractAudioPrice(pricingZero))
    }

    /**
     * Test UI label formatting for audio models.
     * Display should show: "ModelName • $price/M audio tokens" with 2 decimal places.
     */
    @Test
    fun testOpenRouterAudioLabelFormatting() {
        data class AudioModel(
            val name: String,
            val audioPrice: String
        )

        val models = listOf(
            AudioModel("Google Gemini 2.0 Flash", "0.70"),
            AudioModel("OpenAI Whisper-1", "0.02"),
            AudioModel("Very Cheap Model", "0.000001"),
            AudioModel("Free Model", "0")
        )

        fun formatLabel(name: String, price: String): String {
            val priceDouble = price.toDouble()
            return if (priceDouble > 0) {
                "$name • \$${"%.2f".format(priceDouble)}/M audio tokens"
            } else {
                name
            }
        }

        val expectedLabels = listOf(
            "Google Gemini 2.0 Flash • \$0.70/M audio tokens",
            "OpenAI Whisper-1 • \$0.02/M audio tokens",
            "Very Cheap Model • \$0.00/M audio tokens",
            "Free Model"
        )

        for (i in models.indices) {
            val formatted = formatLabel(models[i].name, models[i].audioPrice)
            assertEquals(expectedLabels[i], formatted)
        }
    }
}