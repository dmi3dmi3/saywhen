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
