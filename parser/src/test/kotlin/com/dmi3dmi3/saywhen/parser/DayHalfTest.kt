package com.dmi3dmi3.saywhen.parser

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Половины суток и час 12 (фиксы ревью волны 3): «ночью/tonight в 12» —
 * наступающая полночь, не прошедшая; послеполуденные слова («днём»,
 * "afternoon", "pomeriggio", "tarde", "nachmittag") — отдельная половина
 * AFTERNOON, у которой 12 — полдень, а не полночь.
 */
class DayHalfTest {

    private val parser = MultilingualEventParser()

    // вторник, 15:00
    private val now = ZonedDateTime.of(2026, 7, 21, 15, 0, 0, 0, ZoneId.of("Europe/Moscow"))

    private fun start(text: String) = parser.parse(text, now).start

    @Test
    fun `ночь и 12 — наступающая полночь, не прошлое`() {
        assertEquals(
            ZonedDateTime.of(2026, 7, 22, 0, 0, 0, 0, now.zone),
            start("party tonight at 12"),
        )
        assertEquals(
            ZonedDateTime.of(2026, 7, 22, 0, 0, 0, 0, now.zone),
            start("вечеринка сегодня ночью в 12"),
        )
        assertEquals(
            ZonedDateTime.of(2026, 7, 23, 0, 0, 0, 0, now.zone),
            start("вечеринка завтра ночью в 12"),
        )
    }

    @Test
    fun `послеполуденные слова и 12 — полдень во всех пяти языках`() {
        val noon = ZonedDateTime.of(2026, 7, 22, 12, 0, 0, 0, now.zone)
        assertEquals(noon, start("обед завтра днём в 12"))
        assertEquals(noon, start("lunch tomorrow afternoon at 12"))
        assertEquals(noon, start("pranzo domani pomeriggio alle 12"))
        assertEquals(noon, start("almuerzo mañana por la tarde a las 12"))
        assertEquals(noon, start("essen morgen nachmittag um 12"))
    }

    @Test
    fun `послеполуденный обычный час — вторая половина суток`() {
        assertEquals(15, start("встреча завтра днём в 3").hour)
        assertEquals(16, start("meeting tomorrow afternoon at 4").hour)
    }

    @Test
    fun `утро не тронуто фиксом`() {
        assertEquals(5, start("пробежка завтра утром в 5").hour)
        // «12 утра — полночь» той же даты (контракт), не наступающая
        assertEquals(
            ZonedDateTime.of(2026, 7, 22, 0, 0, 0, 0, now.zone),
            start("завтра утром в 12"),
        )
    }
}
