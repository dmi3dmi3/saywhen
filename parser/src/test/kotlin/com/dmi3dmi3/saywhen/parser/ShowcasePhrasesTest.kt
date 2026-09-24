package com.dmi3dmi3.saywhen.parser

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Витринные фразы главного экрана (задача 19a) и README зеркала (задача 22):
 * лицо приложения обязано распознаваться целиком — заголовок, повтор, время,
 * длительность. Меняешь примеры в strings.xml или README.md — меняй и здесь.
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
    fun `кино завтра в 11 на полтора часа !10`() {
        val e = parser.parse("Кино завтра в 11 на полтора часа !10", now)
        assertEquals("Кино", e.title)
        assertEquals(ZonedDateTime.of(2026, 7, 22, 11, 0, 0, 0, now.zone), e.start)
        assertEquals(90, e.duration!!.toMinutes())
        assertEquals(10, e.reminderMinutes)
    }

    @Test
    fun `gym every tuesday and friday at 9`() {
        val e = parser.parse("Gym every tuesday and friday at 9", now)
        assertEquals("Gym", e.title)
        assertEquals("FREQ=WEEKLY;BYDAY=TU,FR", e.rrule)
        assertEquals(9, e.start.hour)
    }

    @Test
    fun `movie tomorrow at 11 for 1_5h !10`() {
        val e = parser.parse("Movie tomorrow at 11 for 1.5h !10", now)
        assertEquals("Movie", e.title)
        assertEquals(ZonedDateTime.of(2026, 7, 22, 11, 0, 0, 0, now.zone), e.start)
        assertEquals(90, e.duration!!.toMinutes())
        assertEquals(10, e.reminderMinutes)
    }

    // локализованные примеры it/es/de (задача 28; «!10» — задача 29)

    @Test
    fun `cinema domani alle 11 per un'ora e mezza !10`() {
        val e = parser.parse("Cinema domani alle 11 per un'ora e mezza !10", now)
        assertEquals("Cinema", e.title)
        assertEquals(ZonedDateTime.of(2026, 7, 22, 11, 0, 0, 0, now.zone), e.start)
        assertEquals(90, e.duration!!.toMinutes())
        assertEquals(10, e.reminderMinutes)
    }

    @Test
    fun `cine mañana a las 11 por hora y media !10`() {
        val e = parser.parse("Cine mañana a las 11 por hora y media !10", now)
        assertEquals("Cine", e.title)
        assertEquals(ZonedDateTime.of(2026, 7, 22, 11, 0, 0, 0, now.zone), e.start)
        assertEquals(90, e.duration!!.toMinutes())
        assertEquals(10, e.reminderMinutes)
    }

    @Test
    fun `kino morgen um 11 für anderthalb stunden !10`() {
        val e = parser.parse("Kino morgen um 11 für anderthalb Stunden !10", now)
        assertEquals("Kino", e.title)
        assertEquals(ZonedDateTime.of(2026, 7, 22, 11, 0, 0, 0, now.zone), e.start)
        assertEquals(90, e.duration!!.toMinutes())
        assertEquals(10, e.reminderMinutes)
    }

    @Test
    fun `palestra ogni martedì e venerdì alle 9`() {
        val e = parser.parse("Palestra ogni martedì e venerdì alle 9", now)
        assertEquals("Palestra", e.title)
        assertEquals("FREQ=WEEKLY;BYDAY=TU,FR", e.rrule)
        assertEquals(9, e.start.hour)
    }

    @Test
    fun `gimnasio cada martes y viernes a las 9`() {
        val e = parser.parse("Gimnasio cada martes y viernes a las 9", now)
        assertEquals("Gimnasio", e.title)
        assertEquals("FREQ=WEEKLY;BYDAY=TU,FR", e.rrule)
        assertEquals(9, e.start.hour)
    }

    @Test
    fun `sport jeden dienstag und freitag um 9`() {
        val e = parser.parse("Sport jeden Dienstag und Freitag um 9", now)
        assertEquals("Sport", e.title)
        assertEquals("FREQ=WEEKLY;BYDAY=TU,FR", e.rrule)
        assertEquals(9, e.start.hour)
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

    @Test
    fun `dentista il 3 giugno alle 12-30`() {
        val e = parser.parse("Dentista il 3 giugno alle 12:30", now)
        assertEquals("Dentista", e.title)
        assertEquals(ZonedDateTime.of(2027, 6, 3, 12, 30, 0, 0, now.zone), e.start)
    }

    @Test
    fun `dentista el 3 de junio a las 12-30`() {
        val e = parser.parse("Dentista el 3 de junio a las 12:30", now)
        assertEquals("Dentista", e.title)
        assertEquals(ZonedDateTime.of(2027, 6, 3, 12, 30, 0, 0, now.zone), e.start)
    }

    @Test
    fun `zahnarzt am 3 juni um 12-30`() {
        val e = parser.parse("Zahnarzt am 3. Juni um 12:30", now)
        assertEquals("Zahnarzt", e.title)
        assertEquals(ZonedDateTime.of(2027, 6, 3, 12, 30, 0, 0, now.zone), e.start)
    }
}
