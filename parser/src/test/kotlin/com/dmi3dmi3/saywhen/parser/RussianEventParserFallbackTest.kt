package com.dmi3dmi3.saywhen.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class RussianEventParserFallbackTest {

    private val parser = RussianEventParser()

    // вторник, 14:00
    private val now = ZonedDateTime.of(2026, 7, 21, 14, 0, 0, 0, ZoneId.of("Europe/Moscow"))

    @Test
    fun `текст без дат — all-day сегодня, весь текст — заголовок`() {
        val e = parser.parse("купить корм коту", now)

        assertEquals("купить корм коту", e.title)
        assertTrue(e.allDay)
        assertEquals(now.toLocalDate(), e.start.toLocalDate())
        assertEquals(now.zone, e.start.zone)
        assertNull(e.duration)
        assertNull(e.rrule)
        assertTrue(e.matches.isEmpty())
    }

    @Test
    fun `заголовок обрезается по краям`() {
        val e = parser.parse("  стоматолог  ", now)
        assertEquals("стоматолог", e.title)
    }

    @Test
    fun `пустой текст — заголовок Событие`() {
        val e = parser.parse("   ", now)
        assertEquals("Событие", e.title)
        assertTrue(e.allDay)
    }
}
