package com.dmi3dmi3.saywhen.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenizerTest {

    @Test
    fun `слова получают позиции в исходной строке`() {
        val tokens = Tokenizer.tokenize("Ужин завтра в 19:30")

        assertEquals(listOf("Ужин", "завтра", "в", "19:30"), tokens.map { it.text })
        assertEquals(0..3, tokens[0].range)
        assertEquals(5..10, tokens[1].range)
        assertEquals(14..18, tokens[3].range)  // «19:30» — один токен
    }

    @Test
    fun `пунктуация не попадает в токены`() {
        val tokens = Tokenizer.tokenize("ужин, завтра.")

        assertEquals(listOf("ужин", "завтра"), tokens.map { it.text })
        assertEquals(0..3, tokens[0].range)
        assertEquals(6..11, tokens[1].range)
    }

    @Test
    fun `десятичное число — один токен, с единицей и без`() {
        assertEquals(listOf("на", "1,5", "часа"), Tokenizer.tokenize("на 1,5 часа").map { it.text })
        assertEquals(listOf("sync", "1.5h"), Tokenizer.tokenize("sync 1.5h").map { it.text })
        assertEquals(listOf("отчёт", "3.08"), Tokenizer.tokenize("отчёт 3.08").map { it.text })
    }

    @Test
    fun `точечный дефис дней — один токен`() {
        assertEquals(listOf("13. - 15.", "Juli"), Tokenizer.tokenize("13. - 15. Juli").map { it.text })
    }

    @Test
    fun `бэнг вплотную к числу — один токен, иначе пунктуация`() {
        assertEquals(listOf("звонок", "!10"), Tokenizer.tokenize("звонок !10").map { it.text })
        assertEquals(listOf("sync", "!1h"), Tokenizer.tokenize("sync !1h").map { it.text })
        // бэнг после слова или числа — не токен: «купить корм!», «в 10!»
        assertEquals(listOf("купить", "корм"), Tokenizer.tokenize("купить корм!").map { it.text })
        assertEquals(listOf("в", "10"), Tokenizer.tokenize("в 10!").map { it.text })
    }

    @Test
    fun `дефисные слова — один токен`() {
        val tokens = Tokenizer.tokenize("что-нибудь купить")
        assertEquals(listOf("что-нибудь", "купить"), tokens.map { it.text })
    }

    @Test
    fun `lower — нормализованная форма для правил`() {
        assertEquals("ужин", Tokenizer.tokenize("УЖИН")[0].lower)
    }

    @Test
    fun `пустая строка — ноль токенов`() {
        assertTrue(Tokenizer.tokenize("").isEmpty())
    }
}
