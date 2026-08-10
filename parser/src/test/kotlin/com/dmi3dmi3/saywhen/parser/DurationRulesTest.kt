package com.dmi3dmi3.saywhen.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

class DurationRulesTest {

    private val parser = RussianEventParser()

    // вторник, 14:00
    private val now = ZonedDateTime.of(2026, 7, 21, 14, 0, 0, 0, ZoneId.of("Europe/Moscow"))

    private fun assertDuration(text: String, expected: Duration) {
        val e = parser.parse(text, now)
        assertEquals(text, expected, e.duration)
    }

    @Test
    fun `на N единиц`() {
        assertDuration("завтра в 15 на час", Duration.ofHours(1))
        assertDuration("завтра в 15 на 2 часа", Duration.ofHours(2))
        assertDuration("в 15 на 30 минут", Duration.ofMinutes(30))
        assertDuration("на 90 минут в 15", Duration.ofMinutes(90))  // порядок свободный
        assertDuration("на полчаса в 16", Duration.ofMinutes(30))
    }

    @Test
    fun `длительность словами`() {
        assertDuration("встреча в 11 на три часа", Duration.ofHours(3))
        assertDuration("в 15 на два часа", Duration.ofHours(2))
        assertDuration("в 15 на двадцать минут", Duration.ofMinutes(20))
        assertDuration("в 15 на сорок пять минут", Duration.ofMinutes(45))
        assertDuration("в 15 на полтора часа", Duration.ofMinutes(90))
    }

    @Test
    fun `длительность без времени не матчится`() {
        val e = parser.parse("завтра на час", now)
        assertTrue(e.allDay)
        assertNull(e.duration)
        assertEquals(1, e.matches.size)  // только DATE, «на час» осталось текстом
    }

    @Test
    fun `matches — DURATION с точным диапазоном`() {
        val e = parser.parse("завтра в 15 на 2 часа", now)
        assertEquals(3, e.matches.size)
        assertEquals(TokenMatch.Field.DURATION, e.matches[2].field)
        assertEquals(12..20, e.matches[2].range)  // «на 2 часа»
    }

    @Test
    fun `абсурдная длительность — не матч`() {
        val e = parser.parse("в 15 на 9999999 часов", now)
        assertNull(e.duration)
    }
}
