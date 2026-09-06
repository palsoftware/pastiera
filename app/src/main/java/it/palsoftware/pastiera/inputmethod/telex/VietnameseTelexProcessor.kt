package it.palsoftware.pastiera.inputmethod.telex

import java.text.Normalizer

internal object VietnameseTelexProcessor {
    const val VIETNAMESE_TELEX_LAYOUT_ID = "vietnamese_telex_qwerty"

    data class Rewrite(
        val replaceCount: Int,
        val replacement: String
    )

    private val toneByKey = mapOf(
        's' to '\u0301', // acute
        'f' to '\u0300', // grave
        'r' to '\u0309', // hook above
        'x' to '\u0303', // tilde
        'j' to '\u0323', // dot below
    )

    private val toneKeys = toneByKey.keys + 'z'
    private val shapeKeys = setOf('a', 'd', 'e', 'o', 'u', 'w')

    private const val BREVE = '\u0306'
    private const val CIRCUMFLEX = '\u0302'
    private const val HORN = '\u031B'

    private val toneMarks = setOf('\u0301', '\u0300', '\u0309', '\u0303', '\u0323')

    fun isActiveForLayout(layoutName: String?): Boolean = layoutName == VIETNAMESE_TELEX_LAYOUT_ID

    fun rewrite(textBeforeCursor: String, keyChar: Char): Rewrite? {
        if (!keyChar.isLetter()) return null
        val lowerKey = keyChar.lowercaseChar()

        val syllableStart = findSyllableStart(textBeforeCursor)
        if (syllableStart == textBeforeCursor.length) {
            toneCancelledPrefix = ""
            return null
        }
        val syllable = textBeforeCursor.substring(syllableStart)
        if (toneCancelledPrefix.isNotEmpty() && !syllable.startsWith(toneCancelledPrefix)) {
            toneCancelledPrefix = ""
        }

        // Only attempt a Vietnamese conversion while this syllable's onset (the consonants
        // before its first vowel) is still a plausible Vietnamese onset. This is what protects
        // words like "wolf"/"zoo" (invalid onset "w"/"z") from ever getting converted in the
        // first place -- and because nothing gets converted for them, there's nothing to correct
        // or roll back later. Once a syllable already contains a REAL conversion (a diacritic
        // actually applied), we never undo it: a keystroke that doesn't fit just becomes a
        // literal character appended after it, so an already-correct word is never corrupted
        // (e.g. "biết" + n -> "biếtn", not a reversion to raw letters).
        if ((lowerKey in toneKeys || lowerKey in shapeKeys) && (lowerKey == 'd' || establishedOnsetIsValid(syllable))) {
            val rewritten = when {
                lowerKey == 'z' -> clearDiacritics(syllable)
                lowerKey in toneByKey -> applyToneKey(syllable, keyChar)
                else -> applyShapeKey(syllable, keyChar)
            }
            if (rewritten != null && rewritten != syllable) {
                return Rewrite(replaceCount = syllable.length, replacement = rewritten)
            }
        }

        return null
    }

    private fun findSyllableStart(text: String): Int {
        var i = text.length
        while (i > 0 && text[i - 1].isLetter()) i--
        return i
    }

    private fun clearDiacritics(syllable: String): String? {
        var changed = false
        val out = buildString {
            for (ch in syllable) {
                val parts = Parts.fromChar(ch)
                val cleared = parts.clearDiacritics()
                if (cleared != ch) changed = true
                append(cleared)
            }
        }
        return if (changed) out else null
    }

    private fun applyShapeKey(syllable: String, keyChar: Char): String? {
        val key = keyChar.lowercaseChar()
        val chars = syllable.toMutableList()

        if (key == 'w') {
            val clusterRewrite = applyUoWCluster(chars)
            if (clusterRewrite != null) return clusterRewrite
        }
        for (idx in chars.indices.reversed()) {
            if (!chars[idx].isLetter()) continue
            val parts = Parts.fromChar(chars[idx])
            val trailing = chars.subList(idx + 1, chars.size).joinToString("")
            val replacement = when (key) {
                // For self-doubling shape keys (aa->â, ee->ê, oo->ô), only allow reaching back
                // through a trailing consonant CODA if that coda is still "open" (i.e. we're not
                // crossing a completed syllable boundary). We approximate this by requiring the
                // trailing text be empty or made up entirely of vowels (e.g. "dau"+a->"dau"-with-
                // circumflex, where trailing is the vowel "u"). A trailing consonant (e.g. "mam"+a)
                // means an earlier syllable already closed, so the new keystroke starts a fresh one.
                'a' -> when {
                    trailing.isNotEmpty() && !trailing.all { Parts.fromChar(it).isVietnameseVowel() } -> null
                    parts.isBase('a') && !parts.hasShape() -> parts.withShape(CIRCUMFLEX).toChar().toString()
                    parts.isBase('a') && parts.hasShape(CIRCUMFLEX) -> "${caseOf(parts.base)}$keyChar"
                    else -> null
                }
                'e' -> when {
                    trailing.isNotEmpty() && !trailing.all { Parts.fromChar(it).isVietnameseVowel() } -> null
                    parts.isBase('e') && !parts.hasShape() -> parts.withShape(CIRCUMFLEX).toChar().toString()
                    parts.isBase('e') && parts.hasShape(CIRCUMFLEX) -> "${caseOf(parts.base)}$keyChar"
                    else -> null
                }
                'o' -> when {
                    trailing.isNotEmpty() && !trailing.all { Parts.fromChar(it).isVietnameseVowel() } -> null
                    parts.isBase('o') && !parts.hasShape() -> parts.withShape(CIRCUMFLEX).toChar().toString()
                    parts.isBase('o') && parts.hasShape(CIRCUMFLEX) -> "${caseOf(parts.base)}$keyChar"
                    else -> null
                }
                // 'd' doubling (dd->đ) requires the two d's to be strictly adjacent: no reaching
                // back through any other letter at all (unlike a/e/o/w, there's no legitimate
                // Vietnamese case where a 'd' modifier applies at a distance). A third 'd' press
                // reverts đ fully back to the literal double letter, matching how the other shape
                // keys escape (e.g. ô + o -> oo), confirmed against real device behavior.
                'd' -> when {
                    trailing.isNotEmpty() -> null
                    chars[idx] == 'd' -> "đ"
                    chars[idx] == 'D' -> "Đ"
                    chars[idx] == 'đ' -> "dd"
                    chars[idx] == 'Đ' -> "DD"
                    else -> null
                }
                'w' -> when {
                    trailing.isNotEmpty() && !isValidVietnameseTail(trailing) -> null
                    parts.isBase('a') && !parts.hasShape() -> parts.withShape(BREVE).toChar().toString()
                    parts.isBase('o') && !parts.hasShape() -> parts.withShape(HORN).toChar().toString()
                    parts.isBase('u') && !parts.hasShape() -> parts.withShape(HORN).toChar().toString()
                    parts.isBase('a') && parts.hasShape(BREVE) -> "${caseOf(parts.base)}$keyChar"
                    parts.isBase('o') && parts.hasShape(HORN) -> "${caseOf(parts.base)}$keyChar"
                    parts.isBase('u') && parts.hasShape(HORN) -> "${caseOf(parts.base)}$keyChar"
                    else -> null
                }
                else -> null
            }
            if (replacement != null) {
                return syllable.substring(0, idx) + replacement + syllable.substring(idx + 1)
            }
        }
        return null
    }

    private fun applyUoWCluster(chars: List<Char>): String? {
        if (chars.size < 2) return null

        for (oIndex in chars.lastIndex downTo 1) {
            val uIndex = oIndex - 1
            val trailing = chars.subList(oIndex + 1, chars.size).joinToString("")
            if (!isValidVietnameseCoda(trailing)) continue

            val o = Parts.fromChar(chars[oIndex])
            val u = Parts.fromChar(chars[uIndex])
            if (!o.isBase('o') || o.hasShape()) continue
            if (!u.isBase('u') || u.hasShape()) continue

            val newU = u.withShape(HORN).toChar()
            val newO = o.withShape(HORN).toChar()
            return buildString(chars.size) {
                append(chars.subList(0, uIndex).joinToString(""))
                append(newU)
                append(newO)
                append(trailing)
            }
        }

        return null
    }

    // Once a tone is explicitly cancelled (same tone key pressed twice in a row), this syllable
    // is remembered as "tone-cancelled" -- the person deliberately said "not that", so no
    // further tone key (fresh or cycling) should touch it again, even though the visible text
    // is now plain and indistinguishable from a vowel that never had a tone attempt at all.
    // Cleared at the next word boundary.
    private var toneCancelledPrefix: String = ""

    private fun applyToneKey(syllable: String, keyChar: Char): String? {
        if (toneCancelledPrefix.isNotEmpty() && syllable.startsWith(toneCancelledPrefix)) return null
        if (hasSeparatedVowelGroups(syllable)) return null
        val key = keyChar.lowercaseChar()
        val toneMark = toneByKey[key] ?: return null
        val chars = syllable.toMutableList()
        val targetIndex = findToneTargetIndex(chars) ?: return null
        val parts = Parts.fromChar(chars[targetIndex])

        return if (parts.tone == toneMark) {
            val cleared = parts.withTone(null).toChar()
            val result = syllable.substring(0, targetIndex) + cleared + syllable.substring(targetIndex + 1) + keyChar
            toneCancelledPrefix = result
            result
        } else {
            val toned = parts.withTone(toneMark).toChar()
            syllable.substring(0, targetIndex) + toned + syllable.substring(targetIndex + 1)
        }
    }

    private fun findToneTargetIndex(chars: List<Char>): Int? {
        val allVowelIndices = chars.indices.filter { Parts.fromChar(chars[it]).isVietnameseVowel() }
        if (allVowelIndices.isEmpty()) return null
        if (allVowelIndices.size == 1) return allVowelIndices.first()

        // Drop semivowels in common Vietnamese onset digraphs (qu-, gi-) when another vowel follows.
        val vowelIndices = dropLeadingSemivowels(chars, allVowelIndices)
        if (vowelIndices.size == 1) return vowelIndices.first()

        val vowelParts = vowelIndices.map { Parts.fromChar(chars[it]) }
        val bases = vowelParts.map { it.base.lowercaseChar() }

        // Prefer explicit Vietnamese shaped vowels (ă â ê ô ơ ư) when present.
        val shapedPositions = vowelParts.indices.filter { vowelParts[it].hasShape() }
        if (shapedPositions.isNotEmpty()) {
            val uoHornPos = findUoHornClusterPosition(vowelParts)
            if (uoHornPos != null) return vowelIndices[uoHornPos + 1] // tone on ơ in "ươ"
            if (shapedPositions.size == 1) return vowelIndices[shapedPositions.first()]
            return vowelIndices[shapedPositions.last()]
        }

        // Common raw-vowel triphthongs where tone lands on the middle vowel.
        if (bases.size >= 3) {
            val seq = bases.joinToString("")
            if (seq.startsWith("uy")) return vowelIndices[1] // e.g. khuỷu, khuya
        }

        val lastPos = vowelIndices.lastIndex
        val last = vowelParts[lastPos]
        val prevPos = lastPos - 1
        val prev = vowelParts.getOrNull(prevPos)

        // "oa"/"oe" take tone on 'o' only in an OPEN syllable (nothing after the vowel cluster,
        // e.g. "hoa"->"hòa"). In a CLOSED syllable with a trailing coda (e.g. "toan"->"toán",
        // "thoat"->"thoát"), the tone moves onto the second vowel instead.
        val hasCoda = vowelIndices[lastPos] < chars.lastIndex
        if (prev != null && prev.base.lowercaseChar() == 'o' && last.base.lowercaseChar() in setOf('a', 'e')) {
            return if (hasCoda) vowelIndices[lastPos] else vowelIndices[prevPos]
        }

        // If the final vowel is a semivowel-like glide (i/y/u, and o as in "ao"/"eo"), prefer
        // the previous vowel.
        if (prev != null && last.base.lowercaseChar() in setOf('i', 'y', 'u', 'o')) {
            // Exception: "uy" usually carries tone on y.
            if (!(prev.base.lowercaseChar() == 'u' && last.base.lowercaseChar() == 'y')) {
                return vowelIndices[prevPos]
            }
        }

        return vowelIndices[lastPos]
    }

    private fun dropLeadingSemivowels(chars: List<Char>, vowelIndices: List<Int>): List<Int> {
        if (vowelIndices.size < 2) return vowelIndices
        val firstIdx = vowelIndices.first()
        val first = Parts.fromChar(chars[firstIdx])

        if (firstIdx == 1 && chars[0].lowercaseChar() == 'q' && first.isBase('u')) {
            return vowelIndices.drop(1)
        }
        if (firstIdx == 1 && chars[0].lowercaseChar() == 'g' && first.isBase('i')) {
            return vowelIndices.drop(1)
        }
        return vowelIndices
    }

    private fun findUoHornClusterPosition(vowelParts: List<Parts>): Int? {
        for (i in 0 until vowelParts.lastIndex) {
            val first = vowelParts[i]
            val second = vowelParts[i + 1]
            if (
                first.isBase('u') && first.hasShape(HORN) &&
                second.isBase('o') && second.hasShape(HORN)
            ) {
                return i
            }
        }
        return null
    }

    private fun caseOf(base: Char): String = if (base.isUpperCase()) base.toString() else base.lowercaseChar().toString()

    private fun hasSeparatedVowelGroups(syllable: String): Boolean {
        var groups = 0
        var inVowelGroup = false
        for (ch in syllable) {
            val isVowel = Parts.fromChar(ch).isVietnameseVowel()
            if (isVowel) {
                if (!inVowelGroup) {
                    groups++
                    if (groups > 1) return true
                }
                inVowelGroup = true
            } else {
                inVowelGroup = false
            }
        }
        return false
    }

    private fun isValidVietnameseCoda(trailing: String): Boolean {
        if (trailing.isEmpty()) return true
        val tail = trailing.lowercase()
        return tail in setOf("c", "ch", "m", "n", "ng", "nh", "p", "t")
    }

    private fun isValidVietnameseTail(trailing: String): Boolean {
        if (trailing.isEmpty()) return true
        if (trailing.all { Parts.fromChar(it).isVietnameseVowel() }) return true
        return isValidVietnameseCoda(trailing)
    }

    // The (~25) legal Vietnamese syllable-initial consonant clusters. Anything else (pr, dr,
    // str, sh, bl, fr...) never legitimately starts a Vietnamese syllable.
    private val validOnsets = setOf(
        "b", "c", "ch", "d", "đ", "g", "gh", "gi", "h", "k", "kh", "l", "m", "n", "ng", "ngh",
        "nh", "p", "ph", "q", "r", "s", "t", "th", "tr", "v", "x"
    )

    private fun isValidOnsetPrefix(text: String): Boolean {
        if (text.isEmpty()) return true
        val t = text.lowercase()
        return validOnsets.any { it == t || it.startsWith(t) }
    }

    private fun isCompleteValidOnset(text: String): Boolean {
        if (text.isEmpty()) return true // onsetless syllable (e.g. "anh", "em") is valid
        return text.lowercase() in validOnsets
    }

    private fun extractOnset(syllable: String): String {
        val firstVowelIdx = syllable.indices.firstOrNull { Parts.fromChar(syllable[it]).isVietnameseVowel() }
            ?: return syllable
        return syllable.substring(0, firstVowelIdx)
    }

    // Whether the syllable's already-typed onset (the consonants before its first vowel, if
    // any) is still a legal Vietnamese onset. If a vowel has already appeared, the onset is
    // "closed" and must be a COMPLETE valid onset; otherwise it just needs to still be a
    // growable prefix of one. This runs before ANY shape/tone conversion is attempted -- an
    // invalid onset (e.g. "w", "z", "str") means this syllable was never really Vietnamese, so
    // every subsequent letter just inserts literally instead of being converted.
    private fun establishedOnsetIsValid(syllable: String): Boolean {
        val onset = extractOnset(syllable)
        val hasVowel = syllable.length > onset.length
        return if (hasVowel) isCompleteValidOnset(onset) else isValidOnsetPrefix(onset)
    }

    private data class Parts(
        val base: Char,
        val shape: Char? = null,
        val tone: Char? = null,
        val dStroke: Boolean = false
    ) {
        fun isBase(ch: Char): Boolean = !dStroke && base.lowercaseChar() == ch
        fun hasShape(): Boolean = shape != null
        fun hasShape(mark: Char): Boolean = shape == mark

        fun isVietnameseVowel(): Boolean {
            if (dStroke) return false
            return base.lowercaseChar() in setOf('a', 'e', 'i', 'o', 'u', 'y')
        }

        fun withShape(newShape: Char?): Parts = copy(shape = newShape)
        fun withTone(newTone: Char?): Parts = copy(tone = newTone)

        fun clearDiacritics(): Char {
            return when {
                dStroke && base == 'd' -> 'd'
                dStroke && base == 'D' -> 'D'
                else -> copy(shape = null, tone = null).toChar()
            }
        }

        fun toChar(): Char {
            if (dStroke) return if (base.isUpperCase()) 'Đ' else 'đ'
            val sb = StringBuilder().append(base)
            shape?.let { sb.append(it) }
            tone?.let { sb.append(it) }
            return Normalizer.normalize(sb.toString(), Normalizer.Form.NFC).first()
        }

        companion object {
            fun fromChar(ch: Char): Parts {
                if (ch == 'đ' || ch == 'Đ') {
                    return Parts(base = if (ch == 'Đ') 'D' else 'd', dStroke = true)
                }
                val nfd = Normalizer.normalize(ch.toString(), Normalizer.Form.NFD)
                val base = nfd.firstOrNull() ?: ch
                var shape: Char? = null
                var tone: Char? = null
                for (m in nfd.drop(1)) {
                    when {
                        m in toneMarks -> tone = m
                        m == BREVE || m == CIRCUMFLEX || m == HORN -> shape = m
                    }
                }
                return Parts(base = base, shape = shape, tone = tone)
            }
        }
    }
}
