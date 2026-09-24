package com.dmi3dmi3.saywhen.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Порядок парсеров из настроек (задача 30): локальный язык первым, en вторым,
 * остальные включённые — каноническим порядком. null-набор — авто-режим
 * «следовать языку интерфейса»: {язык UI, en}.
 */
class ParserOrderTest {

    @Test
    fun `авто — язык UI и английский, локальный первым`() {
        assertEquals(listOf("de", "en"), parserOrder("de", null))
        assertEquals(listOf("ru", "en"), parserOrder("ru", null))
        assertEquals(listOf("en"), parserOrder("en", null))
    }

    @Test
    fun `непарсерная локаль UI — только английский`() {
        assertEquals(listOf("en"), parserOrder("fr", null))
    }

    @Test
    fun `ручной набор — локальный и en вперёд, остальные каноном`() {
        assertEquals(listOf("es", "en", "ru", "de"), parserOrder("es", setOf("de", "ru", "en", "es")))
        assertEquals(listOf("it"), parserOrder("ru", setOf("it")))
        assertEquals(listOf("en", "it", "de"), parserOrder("en", setOf("de", "it", "en")))
    }

    @Test
    fun `пустой или мусорный ручной набор — как авто`() {
        assertEquals(listOf("ru", "en"), parserOrder("ru", emptySet()))
        assertEquals(listOf("ru", "en"), parserOrder("ru", setOf("xx")))
    }
}
