package it.palsoftware.pastiera.core.suggestions

import android.util.Log
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class NextWordPredictor(
    private val store: UserNGramRepository,
    asyncLearning: Boolean = true
) {
    private val pendingLearning = PendingLearningOverlay()
    private val learningQueue = if (asyncLearning) {
        AsyncLearningQueue(store)
    } else {
        null
    }

    /**
     * Which table an n-gram operation targets. The "prefix" string passed around this class is
     * either a single normalized word (bigram) or two normalized words joined by
     * [CONTEXT_SEPARATOR] (trigram) - the two never collide because normalization strips every
     * character that isn't a letter/digit, so the separator can't appear in a real word key.
     */
    private enum class NGramKind { BIGRAM, TRIGRAM }

    // --- Learning -----------------------------------------------------------------------

    /** Backward-compatible single-previous-word overload (bigram-only learning). */
    fun learn(locale: Locale, previousWord: String?, nextWord: String?) {
        learn(locale, listOfNotNull(previousWord), nextWord)
    }

    /**
     * Learns from up to the last two completed words before [nextWord]. Always updates the
     * bigram table (keyed on the single most recent word) and, when two words of context are
     * available, also updates the trigram table so future predictions can use the richer
     * context and fall back to the bigram when the trigram hasn't been seen yet.
     */
    fun learn(locale: Locale, context: List<String>, nextWord: String?) {
        val displayNextWord = cleanDisplayWord(nextWord) ?: return
        val keys = context.mapNotNull { normalizedKey(it, locale) }
        if (keys.isEmpty()) return
        val localeTag = locale.toLanguageTag()

        learnNormalized(localeTag, keys.last(), displayNextWord, NGramKind.BIGRAM)
        if (keys.size >= 2) {
            val contextKey = contextKey(keys[keys.size - 2], keys.last())
            learnNormalized(localeTag, contextKey, displayNextWord, NGramKind.TRIGRAM)
        }
    }

    fun learnSentenceStart(locale: Locale, firstWord: String?) {
        val displayWord = cleanDisplayWord(firstWord) ?: return
        learnNormalized(locale.toLanguageTag(), SENTENCE_START_PREFIX, displayWord, NGramKind.BIGRAM)
    }

    // --- Prediction -----------------------------------------------------------------------

    /** Backward-compatible single-previous-word overload. */
    fun predict(locale: Locale, previousWord: String?, limit: Int = 3): List<SuggestionResult> {
        return predict(locale, listOfNotNull(previousWord), limit)
    }

    /**
     * Predicts the next word given up to the last two completed words of context. Trigram
     * matches (both words known) are weighted well above bigram-only matches so a specific,
     * previously-seen two-word context wins over a generic single-word association, but the
     * two sources are blended (not strictly either/or) so bigram data still fills in when the
     * exact trigram hasn't been learned yet.
     */
    fun predict(locale: Locale, context: List<String>, limit: Int = 3): List<SuggestionResult> {
        if (limit <= 0) return emptyList()
        val keys = context.mapNotNull { normalizedKey(it, locale) }
        if (keys.isEmpty()) return emptyList()
        val localeTag = locale.toLanguageTag()

        // Pull a wider pool from each source before blending/ranking, since the top-N of one
        // source alone may not be the top-N once combined with the other.
        val pool = (limit * CONTEXT_POOL_MULTIPLIER).coerceAtLeast(limit)
        val bigramRaw = rawPredictions(localeTag, keys.last(), pool, NGramKind.BIGRAM)
        val trigramRaw = if (keys.size >= 2) {
            rawPredictions(localeTag, contextKey(keys[keys.size - 2], keys.last()), pool, NGramKind.TRIGRAM)
        } else {
            emptyList()
        }
        return blendContextPredictions(trigramRaw, bigramRaw, locale, limit)
    }

    /**
     * Like [predict], but only returns candidates whose normalized form starts with
     * [typedPrefix]. Used to boost/merge context-aware next-word candidates into the ordinary
     * current-word completion list while the user is still typing the word (e.g. after typing
     * just "b", if the learned context strongly predicts "bin" next, it should be offered
     * alongside/ahead of generic dictionary completions rather than only appearing once the
     * word box is empty).
     */
    fun predictMatchingPrefix(
        locale: Locale,
        context: List<String>,
        typedPrefix: String,
        limit: Int = 3
    ): List<SuggestionResult> {
        if (limit <= 0 || typedPrefix.isBlank()) return emptyList()
        val normalizedPrefix = normalizedKey(typedPrefix, locale) ?: return emptyList()
        if (normalizedPrefix.isEmpty()) return emptyList()

        // Most context predictions won't match an arbitrary typed prefix, so ask for a wider
        // pool up front rather than repeatedly re-querying.
        val candidates = predict(locale, context, limit = (limit * PREFIX_FILTER_POOL_MULTIPLIER).coerceAtLeast(20))
        return candidates
            .filter { result -> normalizedKey(result.candidate, locale)?.startsWith(normalizedPrefix) == true }
            .take(limit)
    }

    fun predictSentenceStart(locale: Locale, limit: Int = 3): List<SuggestionResult> {
        return predictForPrefix(locale, SENTENCE_START_PREFIX, limit)
    }

    // --- Forgetting -----------------------------------------------------------------------

    /** Backward-compatible single-previous-word overload. */
    fun forget(locale: Locale, previousWord: String?, nextWord: String?): Boolean {
        return forget(locale, listOfNotNull(previousWord), nextWord)
    }

    /** Removes a learned association for the given context from both the bigram and (if
     * applicable) trigram tables, plus any not-yet-flushed pending learning for either. */
    fun forget(locale: Locale, context: List<String>, nextWord: String?): Boolean {
        val displayNextWord = cleanDisplayWord(nextWord) ?: return false
        val keys = context.mapNotNull { normalizedKey(it, locale) }
        if (keys.isEmpty()) return false
        val localeTag = locale.toLanguageTag()

        val bigramKey = keys.last()
        val removedPendingBigram = pendingLearning.removeAll(localeTag, bigramKey, displayNextWord)
        val removedBigram = store.delete(localeTag, bigramKey, displayNextWord) > 0

        var removedTrigram = false
        var removedPendingTrigram = false
        if (keys.size >= 2) {
            val triKey = contextKey(keys[keys.size - 2], keys.last())
            removedPendingTrigram = pendingLearning.removeAll(localeTag, triKey, displayNextWord)
            removedTrigram = store.deleteTrigram(localeTag, triKey, displayNextWord) > 0
        }

        return removedBigram || removedPendingBigram || removedTrigram || removedPendingTrigram
    }

    fun forgetSentenceStart(locale: Locale, firstWord: String?): Boolean {
        val displayWord = cleanDisplayWord(firstWord) ?: return false
        val localeTag = locale.toLanguageTag()
        val removedPending = pendingLearning.removeAll(localeTag, SENTENCE_START_PREFIX, displayWord)
        return store.delete(localeTag, SENTENCE_START_PREFIX, displayWord) > 0 || removedPending
    }

    fun forgetNextWordEverywhere(locale: Locale, nextWord: String?): Boolean {
        val displayNextWord = cleanDisplayWord(nextWord) ?: return false
        val localeTag = locale.toLanguageTag()
        val removedPending = pendingLearning.removeNextWord(localeTag, displayNextWord)
        val removedBigram = store.deleteNextWord(localeTag, displayNextWord) > 0
        val removedTrigram = store.deleteTrigramNextWord(localeTag, displayNextWord) > 0
        return removedBigram || removedTrigram || removedPending
    }

    // --- Internals -----------------------------------------------------------------------

    private fun learnNormalized(localeTag: String, prefix: String, displayNextWord: String, kind: NGramKind) {
        val nowMs = System.currentTimeMillis()
        if (learningQueue == null) {
            persist(kind, localeTag, prefix, displayNextWord, nowMs)
            return
        }

        pendingLearning.add(localeTag, prefix, displayNextWord, nowMs)
        val queued = learningQueue.enqueue(
            kind = kind,
            localeTag = localeTag,
            prefix = prefix,
            nextWord = displayNextWord,
            nowMs = nowMs,
            shouldPersist = {
                pendingLearning.contains(localeTag, prefix, displayNextWord)
            },
            onFinished = {
                pendingLearning.removeOne(localeTag, prefix, displayNextWord)
            }
        )
        if (!queued) {
            pendingLearning.removeOne(localeTag, prefix, displayNextWord)
        }
    }

    private fun persist(kind: NGramKind, localeTag: String, prefix: String, nextWord: String, nowMs: Long) {
        when (kind) {
            NGramKind.BIGRAM -> store.learn(localeTag, prefix, nextWord, nowMs)
            NGramKind.TRIGRAM -> store.learnTrigram(localeTag, prefix, nextWord, nowMs)
        }
    }

    private fun rawPredictions(
        localeTag: String,
        prefix: String,
        limit: Int,
        kind: NGramKind
    ): List<UserNGramStore.Prediction> {
        val stored = when (kind) {
            NGramKind.BIGRAM -> store.predict(localeTag, prefix, limit)
            NGramKind.TRIGRAM -> store.predictTrigram(localeTag, prefix, limit)
        }
        val pending = pendingLearning.predict(localeTag, prefix)
        return mergePredictions(stored, pending).take(limit)
    }

    private fun predictForPrefix(locale: Locale, prefix: String, limit: Int): List<SuggestionResult> {
        val localeTag = locale.toLanguageTag()
        return rawPredictions(localeTag, prefix, limit, NGramKind.BIGRAM).map { prediction ->
            SuggestionResult(
                candidate = prediction.word,
                distance = 0,
                score = prediction.count.toDouble(),
                source = SuggestionSource.USER,
                kind = SuggestionKind.NEXT_WORD
            )
        }
    }

    /**
     * Combines trigram and bigram raw counts into a single ranked list. A trigram hit for a
     * given word is worth [TRIGRAM_WEIGHT]x a bigram hit for the same word, and matching counts
     * from both sources for the same candidate simply add - so a word that's both the general
     * (bigram) association *and* the specific (trigram) one for this exact context ranks above
     * either alone.
     */
    private fun blendContextPredictions(
        trigram: List<UserNGramStore.Prediction>,
        bigram: List<UserNGramStore.Prediction>,
        locale: Locale,
        limit: Int
    ): List<SuggestionResult> {
        val score = linkedMapOf<String, Double>()
        val display = mutableMapOf<String, String>()

        fun accumulate(predictions: List<UserNGramStore.Prediction>, weight: Double) {
            for (prediction in predictions) {
                val key = prediction.word.lowercase(locale)
                score[key] = (score[key] ?: 0.0) + prediction.count * weight
                display.putIfAbsent(key, prediction.word)
            }
        }

        accumulate(trigram, TRIGRAM_WEIGHT)
        accumulate(bigram, BIGRAM_WEIGHT)

        return score.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { (key, value) ->
                SuggestionResult(
                    candidate = display.getValue(key),
                    distance = 0,
                    score = value,
                    source = SuggestionSource.USER,
                    kind = SuggestionKind.NEXT_WORD
                )
            }
    }

    private fun contextKey(word1Key: String, word2Key: String): String =
        "$word1Key$CONTEXT_SEPARATOR$word2Key"

    internal fun clearAll() {
        flushLearningForTests()
        pendingLearning.clear()
        store.clearAll()
    }

    internal fun flushLearningForTests() {
        runBlocking {
            learningQueue?.flush()
        }
    }

    fun destroy() {
        learningQueue?.destroy()
        pendingLearning.clear()
    }

    private fun mergePredictions(
        stored: List<UserNGramStore.Prediction>,
        pending: List<UserNGramStore.Prediction>
    ): List<UserNGramStore.Prediction> {
        if (pending.isEmpty()) return stored
        val merged = linkedMapOf<String, UserNGramStore.Prediction>()
        (stored + pending).forEach { prediction ->
            val existing = merged[prediction.word]
            merged[prediction.word] = if (existing == null) {
                prediction
            } else {
                existing.copy(
                    count = existing.count + prediction.count,
                    lastUsed = maxOf(existing.lastUsed, prediction.lastUsed)
                )
            }
        }
        return merged.values.sortedWith(
            compareByDescending<UserNGramStore.Prediction> { it.count }
                .thenByDescending { it.lastUsed }
        )
    }

    private fun cleanDisplayWord(word: String?): String? {
        val trimmed = word?.trim() ?: return null
        if (trimmed.isEmpty() || trimmed.none { it.isLetterOrDigit() }) return null
        return trimmed
    }

    private fun normalizedKey(word: String?, locale: Locale): String? {
        val cleaned = cleanDisplayWord(word) ?: return null
        val withAsciiCompat = WordNormalization
            .foldCompatibilityLetters(WordNormalization.normalizeApostrophes(cleaned).lowercase(locale))
        val normalized = Normalizer.normalize(withAsciiCompat, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS_REGEX, "")
            .replace(NON_WORD_KEY_REGEX, "")
        return normalized.takeIf { it.isNotBlank() }
    }

    companion object {
        private const val TAG = "NextWordPredictor"
        private const val SENTENCE_START_PREFIX = "__sentence_start__"
        private const val CONTEXT_SEPARATOR = "\u001F"
        private const val TRIGRAM_WEIGHT = 3.0
        private const val BIGRAM_WEIGHT = 1.0
        private const val CONTEXT_POOL_MULTIPLIER = 4
        private const val PREFIX_FILTER_POOL_MULTIPLIER = 8
        private val COMBINING_MARKS_REGEX = "\\p{Mn}".toRegex()
        private val NON_WORD_KEY_REGEX = "[^\\p{L}\\p{N}]".toRegex()
    }

    private class PendingLearningOverlay {
        private val lock = Any()
        private val pending = mutableMapOf<Key, PendingPrediction>()

        fun add(localeTag: String, prefix: String, nextWord: String, nowMs: Long) {
            synchronized(lock) {
                val key = Key(localeTag, prefix, nextWord)
                val existing = pending[key]
                pending[key] = PendingPrediction(
                    count = (existing?.count ?: 0) + 1,
                    lastUsed = nowMs
                )
            }
        }

        fun removeOne(localeTag: String, prefix: String, nextWord: String): Boolean {
            synchronized(lock) {
                val key = Key(localeTag, prefix, nextWord)
                val existing = pending[key] ?: return false
                if (existing.count <= 1) {
                    pending.remove(key)
                } else {
                    pending[key] = existing.copy(count = existing.count - 1)
                }
                return true
            }
        }

        fun removeAll(localeTag: String, prefix: String, nextWord: String): Boolean {
            synchronized(lock) {
                return pending.remove(Key(localeTag, prefix, nextWord)) != null
            }
        }

        fun contains(localeTag: String, prefix: String, nextWord: String): Boolean {
            synchronized(lock) {
                return pending.containsKey(Key(localeTag, prefix, nextWord))
            }
        }

        fun removeNextWord(localeTag: String, nextWord: String): Boolean {
            synchronized(lock) {
                val keys = pending.keys
                    .filter { it.localeTag == localeTag && it.nextWord.equals(nextWord, ignoreCase = true) }
                keys.forEach { pending.remove(it) }
                return keys.isNotEmpty()
            }
        }

        fun predict(localeTag: String, prefix: String): List<UserNGramStore.Prediction> {
            synchronized(lock) {
                return pending
                    .filterKeys { it.localeTag == localeTag && it.prefix == prefix }
                    .map { (key, value) ->
                        UserNGramStore.Prediction(
                            word = key.nextWord,
                            count = value.count,
                            lastUsed = value.lastUsed
                        )
                    }
                    .sortedWith(
                        compareByDescending<UserNGramStore.Prediction> { it.count }
                            .thenByDescending { it.lastUsed }
                    )
            }
        }

        fun clear() {
            synchronized(lock) {
                pending.clear()
            }
        }

        private data class Key(
            val localeTag: String,
            val prefix: String,
            val nextWord: String
        )

        private data class PendingPrediction(
            val count: Int,
            val lastUsed: Long
        )
    }

    private class AsyncLearningQueue(
        private val store: UserNGramRepository
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val channel = Channel<Command>(Channel.UNLIMITED)

        init {
            scope.launch {
                for (command in channel) {
                    when (command) {
                        is Command.Learn -> handleLearn(command)
                        is Command.Flush -> command.completed.complete(Unit)
                    }
                }
            }
        }

        fun enqueue(
            kind: NGramKind,
            localeTag: String,
            prefix: String,
            nextWord: String,
            nowMs: Long,
            shouldPersist: () -> Boolean,
            onFinished: () -> Unit
        ): Boolean {
            return channel.trySend(
                Command.Learn(kind, localeTag, prefix, nextWord, nowMs, shouldPersist, onFinished)
            ).isSuccess
        }

        suspend fun flush() {
            val completed = CompletableDeferred<Unit>()
            if (!channel.trySend(Command.Flush(completed)).isSuccess) return
            completed.await()
        }

        fun destroy() {
            channel.close()
            scope.cancel()
        }

        private fun handleLearn(command: Command.Learn) {
            try {
                if (command.shouldPersist()) {
                    when (command.kind) {
                        NGramKind.BIGRAM -> store.learn(command.localeTag, command.prefix, command.nextWord, command.nowMs)
                        NGramKind.TRIGRAM -> store.learnTrigram(command.localeTag, command.prefix, command.nextWord, command.nowMs)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist next-word learning", e)
            } finally {
                command.onFinished()
            }
        }

        private sealed class Command {
            data class Learn(
                val kind: NGramKind,
                val localeTag: String,
                val prefix: String,
                val nextWord: String,
                val nowMs: Long,
                val shouldPersist: () -> Boolean,
                val onFinished: () -> Unit
            ) : Command()

            data class Flush(
                val completed: CompletableDeferred<Unit>
            ) : Command()
        }
    }
}
