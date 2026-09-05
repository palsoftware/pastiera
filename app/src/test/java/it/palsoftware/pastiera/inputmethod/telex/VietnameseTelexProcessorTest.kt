package it.palsoftware.pastiera.inputmethod.telex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VietnameseTelexProcessorTest {

    @Test
    fun `shape keys convert base vowels`() {
        assertRewrite("ca", 'a', "câ")
        assertRewrite("trang", 'w', "trăng")
        assertRewrite("de", 'e', "dê")
        assertRewrite("mo", 'w', "mơ")
        assertRewrite("tu", 'w', "tư")
        assertRewrite("d", 'd', "đ")
        assertRewrite("đ", 'd', "dd")
    }

    @Test
    fun `uow creates uo horn cluster`() {
        assertRewrite("tuo", 'w', "tươ")
        assertRewrite("tuong", 'w', "tương")
    }

    @Test
    fun `dau plus a becomes dau with circumflex`() {
        assertRewrite("đau", 'a', "đâu")
    }

    @Test
    fun `tone keys apply and replace last tone`() {
        assertRewrite("ta", 's', "tá")
        assertRewrite("tá", 'f', "tà")
    }

    @Test
    fun `tone target handles common Vietnamese clusters`() {
        assertRewrite("hoa", 'f', "hòa")
        assertRewrite("qua", 's', "quá")
        assertRewrite("gia", 'f', "già")
        assertRewrite("giai", 'r', "giải")
        assertRewrite("thuy", 's', "thuý")
        assertRewrite("huê", 's', "huế")
        assertRewrite("tươ", 'r', "tưở")
    }

    @Test
    fun `z clears diacritics in syllable`() {
        assertRewrite("tưở", 'z', "tuo")
    }

    @Test
    fun `repeating tone key emits literal key`() {
        assertRewrite("hẻ", 'r', "her")
    }

    @Test
    fun `repeating shape key can escape transformed vowel`() {
        assertRewrite("xô", 'o', "xoo")
        assertRewrite("mơ", 'w', "mow")
    }

    @Test
    fun `uppercase keys preserve case in transformations and escapes`() {
        assertRewrite("TA", 'S', "TÁ")
        assertRewrite("D", 'D', "Đ")
        assertRewrite("Ô", 'O', "OO")
        assertRewrite("Ư", 'W', "UW")
    }

    @Test
    fun `non telex key returns null`() {
        assertNull(VietnameseTelexProcessor.rewrite("ta", 'k'))
    }

    @Test
    fun `foreign word sequences like telex are not rewritten`() {
        assertNull(VietnameseTelexProcessor.rewrite("tel", 'e'))
        assertNull(VietnameseTelexProcessor.rewrite("tele", 'x'))
        assertNull(VietnameseTelexProcessor.rewrite("Tele", 'x'))
    }

    @Test
    fun `layout activation is tied to layout id`() {
        assertEquals(true, VietnameseTelexProcessor.isActiveForLayout("vietnamese_telex_qwerty"))
        assertEquals(false, VietnameseTelexProcessor.isActiveForLayout("qwerty"))
    }

    private fun assertRewrite(textBeforeCursor: String, keyChar: Char, expected: String) {
        val rewrite = VietnameseTelexProcessor.rewrite(textBeforeCursor, keyChar)
        requireNotNull(rewrite) { "Expected rewrite for '$textBeforeCursor' + '$keyChar'" }
        assertEquals(textBeforeCursor.length, rewrite.replaceCount)
        assertEquals(expected, rewrite.replacement)
    }
}
