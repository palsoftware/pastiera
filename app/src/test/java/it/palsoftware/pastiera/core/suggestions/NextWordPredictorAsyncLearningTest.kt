package it.palsoftware.pastiera.core.suggestions

import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NextWordPredictorAsyncLearningTest {

    @Test
    fun learnDoesNotBlockOnStoreWrite_andPredictsPendingBigramImmediately() {
        val store = BlockingNGramRepository()
        val predictor = NextWordPredictor(store)

        try {
            val elapsedMs = measureTimeMillis {
                predictor.learn(Locale.GERMANY, "ich", "bin")
            }

            assertTrue("learn should only enqueue work", elapsedMs < 100)
            assertEquals(
                listOf("bin"),
                predictor.predict(Locale.GERMANY, "ich").map { it.candidate }
            )
            assertTrue(store.writeStarted.await(1, TimeUnit.SECONDS))

            store.releaseWrites()
            predictor.flushLearningForTests()

            assertEquals(listOf("bin"), store.predict("de-DE", "ich", 3).map { it.word })
        } finally {
            store.releaseWrites()
            predictor.destroy()
        }
    }

    @Test
    fun forgetCancelsQueuedWriteBeforeItReachesStore() {
        val store = BlockingNGramRepository()
        val predictor = NextWordPredictor(store)

        try {
            predictor.learn(Locale.GERMANY, "block", "first")
            assertTrue(store.writeStarted.await(1, TimeUnit.SECONDS))

            predictor.learn(Locale.GERMANY, "ich", "bin")
            assertEquals(
                listOf("bin"),
                predictor.predict(Locale.GERMANY, "ich").map { it.candidate }
            )

            predictor.forget(Locale.GERMANY, "ich", "bin")
            assertTrue(predictor.predict(Locale.GERMANY, "ich").isEmpty())

            store.releaseWrites()
            predictor.flushLearningForTests()

            assertEquals(listOf("first"), store.predict("de-DE", "block", 3).map { it.word })
            assertFalse(store.learnedWords().contains("bin"))
        } finally {
            store.releaseWrites()
            predictor.destroy()
        }
    }

    private class BlockingNGramRepository : UserNGramRepository {
        val writeStarted = CountDownLatch(1)
        private val releaseWrites = CountDownLatch(1)
        private val lock = Any()
        private val learned = mutableListOf<LearnedBigram>()

        override fun learn(locale: String, prefix: String, nextWord: String, nowMs: Long) {
            writeStarted.countDown()
            releaseWrites.await(2, TimeUnit.SECONDS)
            synchronized(lock) {
                learned.add(LearnedBigram(locale, prefix, nextWord, nowMs))
            }
        }

        override fun predict(locale: String, prefix: String, limit: Int): List<UserNGramStore.Prediction> {
            synchronized(lock) {
                return learned
                    .filter { it.locale == locale && it.prefix == prefix }
                    .groupBy { it.nextWord }
                    .map { (word, rows) ->
                        UserNGramStore.Prediction(
                            word = word,
                            count = rows.size,
                            lastUsed = rows.maxOf { it.nowMs }
                        )
                    }
                    .sortedWith(
                        compareByDescending<UserNGramStore.Prediction> { it.count }
                            .thenByDescending { it.lastUsed }
                    )
                    .take(limit)
            }
        }

        override fun delete(locale: String, prefix: String, nextWord: String): Int {
            synchronized(lock) {
                val before = learned.size
                learned.removeAll {
                    it.locale == locale &&
                        it.prefix == prefix &&
                        it.nextWord.equals(nextWord, ignoreCase = true)
                }
                return before - learned.size
            }
        }

        override fun deleteNextWord(locale: String, nextWord: String): Int {
            synchronized(lock) {
                val before = learned.size
                learned.removeAll {
                    it.locale == locale && it.nextWord.equals(nextWord, ignoreCase = true)
                }
                return before - learned.size
            }
        }

        private val learnedTrigrams = mutableListOf<LearnedBigram>()

        override fun learnTrigram(locale: String, contextKey: String, nextWord: String, nowMs: Long) {
            synchronized(lock) {
                learnedTrigrams.add(LearnedBigram(locale, contextKey, nextWord, nowMs))
            }
        }

        override fun predictTrigram(locale: String, contextKey: String, limit: Int): List<UserNGramStore.Prediction> {
            synchronized(lock) {
                return learnedTrigrams
                    .filter { it.locale == locale && it.prefix == contextKey }
                    .groupBy { it.nextWord }
                    .map { (word, rows) ->
                        UserNGramStore.Prediction(
                            word = word,
                            count = rows.size,
                            lastUsed = rows.maxOf { it.nowMs }
                        )
                    }
                    .sortedWith(
                        compareByDescending<UserNGramStore.Prediction> { it.count }
                            .thenByDescending { it.lastUsed }
                    )
                    .take(limit)
            }
        }

        override fun deleteTrigram(locale: String, contextKey: String, nextWord: String): Int {
            synchronized(lock) {
                val before = learnedTrigrams.size
                learnedTrigrams.removeAll {
                    it.locale == locale &&
                        it.prefix == contextKey &&
                        it.nextWord.equals(nextWord, ignoreCase = true)
                }
                return before - learnedTrigrams.size
            }
        }

        override fun deleteTrigramNextWord(locale: String, nextWord: String): Int {
            synchronized(lock) {
                val before = learnedTrigrams.size
                learnedTrigrams.removeAll {
                    it.locale == locale && it.nextWord.equals(nextWord, ignoreCase = true)
                }
                return before - learnedTrigrams.size
            }
        }

        override fun clearAll() {
            synchronized(lock) {
                learned.clear()
                learnedTrigrams.clear()
            }
        }

        fun releaseWrites() {
            releaseWrites.countDown()
        }

        fun learnedWords(): List<String> {
            synchronized(lock) {
                return learned.map { it.nextWord }
            }
        }

        private data class LearnedBigram(
            val locale: String,
            val prefix: String,
            val nextWord: String,
            val nowMs: Long
        )
    }
}
