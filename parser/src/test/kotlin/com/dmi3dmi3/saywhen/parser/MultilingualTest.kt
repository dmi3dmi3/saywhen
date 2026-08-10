package com.dmi3dmi3.saywhen.parser

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** Автоопределение языка и кросс-язычные фразы — бесплатно из арбитража (задача 17). */
class MultilingualTest {

    private val parser = MultilingualEventParser()

    // вторник, 14:00
    private val now = ZonedDateTime.of(2026, 7, 21, 14, 0, 0, 0, ZoneId.of("Europe/Moscow"))

    private fun at(day: Int, hour: Int): ZonedDateTime =
        ZonedDateTime.of(2026, 7, day, hour, 0, 0, 0, now.zone)

    @Test
    fun `русский путь не пострадал`() {
        val e = parser.parse("обед завтра в 15", now)
        assertEquals(at(22, 15), e.start)
        assertEquals("обед", e.title)
    }

    @Test
    fun `смешанная фраза — дата en, время ru`() {
        val e = parser.parse("созвон tomorrow в 15", now)
        assertEquals(at(22, 15), e.start)
        assertEquals("созвон", e.title)
    }

    @Test
    fun `смешанная фраза — повтор ru, время en`() {
        val e = parser.parse("yoga каждый вторник at 7pm", now)
        assertEquals("FREQ=WEEKLY;BYDAY=TU", e.rrule)
        assertEquals(19, e.start.hour)
        assertEquals("yoga", e.title)
    }

    @Test
    fun `дефолтный заголовок — от лидера по покрытию`() {
        assertEquals("Событие", parser.parse("завтра в 15", now).title)
        assertEquals("Event", parser.parse("tomorrow at 3pm", now).title)
    }
}
