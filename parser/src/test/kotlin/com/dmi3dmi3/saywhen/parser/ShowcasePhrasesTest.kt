package com.dmi3dmi3.saywhen.parser

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Витринные фразы главного экрана (задача 19a) и README зеркала (задача 22):
 * лицо приложения обязано распознаваться целиком — заголовок, повтор, время,
 * длительность. Меняешь примеры в strings.xml или mirror/README.md — меняй и здесь.
 */
class ShowcasePhrasesTest {

    private val parser = MultilingualEventParser()

    // вторник, 14:00
    private val now = ZonedDateTime.of(2026, 7, 21, 14, 0, 0, 0, ZoneId.of("Europe/Moscow"))

    @Test
    fun `спорт каждый вторник и пятницу в 9`() {
        val e = parser.parse("Спорт каждый вторник и пятницу в 9", now)
        assertEquals("Спорт", e.title)
        assertEquals("FREQ=WEEKLY;BYDAY=TU,FR", e.rrule)
        assertEquals(9, e.start.hour)
    }

    @Test
    fun `кино завтра в 11 на полтора часа`() {
        val e = parser.parse("Кино завтра в 11 на полтора часа", now)
        assertEquals("Кино", e.title)
        assertEquals(ZonedDateTime.of(2026, 7, 22, 11, 0, 0, 0, now.zone), e.start)
        assertEquals(90, e.duration!!.toMinutes())
    }

    @Test
    fun `gym every tuesday and friday at 9`() {
        val e = parser.parse("Gym every tuesday and friday at 9", now)
        assertEquals("Gym", e.title)
        assertEquals("FREQ=WEEKLY;BYDAY=TU,FR", e.rrule)
        assertEquals(9, e.start.hour)
    }

    @Test
    fun `movie tomorrow at 11 for an hour and a half`() {
        val e = parser.parse("Movie tomorrow at 11 for an hour and a half", now)
        assertEquals("Movie", e.title)
        assertEquals(ZonedDateTime.of(2026, 7, 22, 11, 0, 0, 0, now.zone), e.start)
        assertEquals(90, e.duration!!.toMinutes())
    }

    // 3 июня уже прошло (now — 21 июля) → «в будущем» уводит на следующий год

    @Test
    fun `стоматолог 3 июня в 12-30`() {
        val e = parser.parse("Стоматолог 3 июня в 12:30", now)
        assertEquals("Стоматолог", e.title)
        assertEquals(ZonedDateTime.of(2027, 6, 3, 12, 30, 0, 0, now.zone), e.start)
    }

    @Test
    fun `dentist june 3 at 12-30`() {
        val e = parser.parse("Dentist june 3 at 12:30", now)
        assertEquals("Dentist", e.title)
        assertEquals(ZonedDateTime.of(2027, 6, 3, 12, 30, 0, 0, now.zone), e.start)
    }
}
