package it.palsoftware.pastiera.core.composition

internal object HangulJamo {
    val consonants = setOf(
        'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    )
    val vowels = setOf('ㅏ', 'ㅐ', 'ㅑ', 'ㅒ', 'ㅓ', 'ㅔ', 'ㅕ', 'ㅖ', 'ㅗ', 'ㅛ', 'ㅜ', 'ㅠ', 'ㅡ', 'ㅣ')
    val allSupported = consonants + vowels

    val choseong = listOf('ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ')
    val jungseong = listOf('ㅏ', 'ㅐ', 'ㅑ', 'ㅒ', 'ㅓ', 'ㅔ', 'ㅕ', 'ㅖ', 'ㅗ', 'ㅘ', 'ㅙ', 'ㅚ', 'ㅛ', 'ㅜ', 'ㅝ', 'ㅞ', 'ㅟ', 'ㅠ', 'ㅡ', 'ㅢ', 'ㅣ')
    val jongseong = listOf(null, 'ㄱ', 'ㄲ', 'ㄳ', 'ㄴ', 'ㄵ', 'ㄶ', 'ㄷ', 'ㄹ', 'ㄺ', 'ㄻ', 'ㄼ', 'ㄽ', 'ㄾ', 'ㄿ', 'ㅀ', 'ㅁ', 'ㅂ', 'ㅄ', 'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ')

    val medialCombinations = mapOf(
        'ㅗ' to mapOf('ㅏ' to 'ㅘ', 'ㅐ' to 'ㅙ', 'ㅣ' to 'ㅚ'),
        'ㅜ' to mapOf('ㅓ' to 'ㅝ', 'ㅔ' to 'ㅞ', 'ㅣ' to 'ㅟ'),
        'ㅡ' to mapOf('ㅣ' to 'ㅢ')
    )

    val finalCombinations = mapOf(
        'ㄱ' to mapOf('ㅅ' to 'ㄳ'),
        'ㄴ' to mapOf('ㅈ' to 'ㄵ', 'ㅎ' to 'ㄶ'),
        'ㄹ' to mapOf('ㄱ' to 'ㄺ', 'ㅁ' to 'ㄻ', 'ㅂ' to 'ㄼ', 'ㅅ' to 'ㄽ', 'ㅌ' to 'ㄾ', 'ㅍ' to 'ㄿ', 'ㅎ' to 'ㅀ'),
        'ㅂ' to mapOf('ㅅ' to 'ㅄ')
    )

    val initialIndex = choseong.withIndex().associate { it.value to it.index }
    val medialIndex = jungseong.withIndex().associate { it.value to it.index }
    val finalIndex = jongseong.withIndex().associate { it.value to it.index }

    fun isConsonant(c: Char) = c in consonants
    fun isVowel(c: Char) = c in vowels
    fun isSupported(c: Char) = c in allSupported
    fun combineMedial(a: Char, b: Char): Char? = medialCombinations[a]?.get(b)
    fun combineFinal(a: Char, b: Char): Char? = finalCombinations[a]?.get(b)

    fun splitFinal(final: Char): Pair<Char, Char>? = when (final) {
        'ㄳ' -> 'ㄱ' to 'ㅅ'
        'ㄵ' -> 'ㄴ' to 'ㅈ'
        'ㄶ' -> 'ㄴ' to 'ㅎ'
        'ㄺ' -> 'ㄹ' to 'ㄱ'
        'ㄻ' -> 'ㄹ' to 'ㅁ'
        'ㄼ' -> 'ㄹ' to 'ㅂ'
        'ㄽ' -> 'ㄹ' to 'ㅅ'
        'ㄾ' -> 'ㄹ' to 'ㅌ'
        'ㄿ' -> 'ㄹ' to 'ㅍ'
        'ㅀ' -> 'ㄹ' to 'ㅎ'
        'ㅄ' -> 'ㅂ' to 'ㅅ'
        else -> null
    }

    fun compose(initial: Char, medial: Char, final: Char? = null): Char {
        val l = initialIndex[initial] ?: return initial
        val v = medialIndex[medial] ?: return medial
        val t = final?.let { finalIndex[it] } ?: 0
        return (0xAC00 + ((l * 21) + v) * 28 + t).toChar()
    }
}
