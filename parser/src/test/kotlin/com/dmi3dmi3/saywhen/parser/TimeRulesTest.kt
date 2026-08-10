package com.dmi3dmi3.saywhen.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

class TimeRulesTest {

    private val parser = RussianEventParser()

    // вторник, 14:00
    private val now = ZonedDateTime.of(2026, 7, 21, 14, 0, 0, 0, ZoneId.of("Europe/Moscow"))

    private fun assertStart(text: String, expected: ZonedDateTime) {
        val e = parser.parse(text, now)
        assertEquals(text, expected, e.start)
        assertFalse(text, e.allDay)
    }

    private fun at(day: Int, hour: Int, minute: Int = 0): ZonedDateTime =
        ZonedDateTime.of(2026, 7, day, hour, minute, 0, 0, now.zone)

    @Test
    fun `время с датой`() {
        assertStart("завтра в 15", at(22, 15))
        assertStart("в 9:30 завтра", at(22, 9, 30))
        assertStart("стоматолог завтра в 15 часов", at(22, 15))
        assertStart("завтра 9:30", at(22, 9, 30))  // голое HH:MM
        assertStart("в пятницу в 9", at(24, 9))
    }

    @Test
    fun `минуты отдельным токеном — строго две цифры`() {
        assertStart("завтра в 19 30", at(22, 19, 30))
        assertStart("в 19 05", at(21, 19, 5))
        assertStart("в 19 5", at(21, 19))  // «5» — не минуты, остаётся текстом
    }

    @Test
    fun `время без даты — сегодня, если ещё впереди`() {
        assertStart("в 15", at(21, 15))       // сейчас 14:00 → сегодня
        assertStart("обед в 16:45", at(21, 16, 45))
    }

    @Test
    fun `прошедшее время без даты`() {
        assertStart("в 9", at(22, 9))         // окно читает буквально; 9:00 прошло → завтра
        assertStart("в 14", at(22, 14))       // 14 вне круга; ровно сейчас — сдвиг на завтра
    }

    @Test
    fun `прошедшее время + день недели — следующая неделя`() {
        assertStart("во вторник в 9", at(28, 9))   // сегодня вторник, 9 прошло
        assertStart("во вторник в 15", at(21, 15)) // 15 ещё впереди → сегодня
    }

    @Test
    fun `явное сегодня — окно решает, за пределы дня не уходим`() {
        // правило окна (июль 2026): выбор часа от «сейчас» не зависит,
        // прошедшее при явном дне уважаем — как и вне круга
        assertStart("сегодня в 9", at(21, 9))
        assertStart("21 июля в 9", at(21, 9))
        assertStart("сегодня в 14", at(21, 14))
    }

    @Test
    fun `интервал с-до`() {
        val e = parser.parse("с 15 до 17", now)
        assertEquals(at(21, 15), e.start)
        assertEquals(Duration.ofHours(2), e.duration)
        assertEquals(1, e.matches.size)
        assertEquals(TokenMatch.Field.TIME, e.matches[0].field)
        assertEquals(0..9, e.matches[0].range)  // «с 15 до 17» целиком

        val e2 = parser.parse("завтра с 9:30 до 11:00", now)
        assertEquals(at(22, 9, 30), e2.start)
        assertEquals(Duration.ofMinutes(90), e2.duration)
    }

    @Test
    fun `интервал за полночь`() {
        val e = parser.parse("с 23 до 1", now)
        assertEquals(at(21, 23), e.start)
        assertEquals(Duration.ofHours(2), e.duration)
    }

    @Test
    fun `прошедший интервал без даты — завтра`() {
        val e = parser.parse("с 9 до 11", now)
        assertEquals(at(22, 9), e.start)
    }

    @Test
    fun `склеенный интервал через дефис`() {
        val e = parser.parse("23:40-23:00 что-то", now)
        assertEquals(at(21, 23, 40), e.start)
        assertEquals(Duration.ofMinutes(23 * 60 + 20), e.duration)  // реверс → через полночь

        val e2 = parser.parse("завтра 10-11:30", now)
        assertEquals(at(22, 10), e2.start)
        assertEquals(Duration.ofMinutes(90), e2.duration)
    }

    @Test
    fun `голая пара час-минуты — якорь не хуже двоеточия`() {
        assertStart("16 40 обед", at(21, 16, 40))
        assertStart("11 00 обед", at(22, 11))          // 23:00 вне окна; 11:00 прошло → завтра
        assertStart("завтра 11 00 обед", at(22, 11))
        assertStart("11 00 утра завтрак", at(22, 11))  // «утра» снял пару; прошло → завтра
    }

    @Test
    fun `голая пара — минуты строго две цифры, час валидный`() {
        assertTrue(parser.parse("маршрут 11 5", now).allDay)
        assertTrue(parser.parse("24 00 столб", now).allDay)
        assertTrue(parser.parse("гараж 11 60", now).allDay)
    }

    @Test
    fun `голое число сразу после даты или повтора — час`() {
        assertStart("завтра 11 планёрка", at(22, 11))
        // повтор без якоря, 11 сегодня прошло → следующее вхождение
        val e = parser.parse("каждую неделю 11 треня", now)
        assertEquals(at(28, 11), e.start)
        assertEquals("FREQ=WEEKLY", e.rrule)
    }

    @Test
    fun `голое число без матча перед ним — не время`() {
        assertTrue(parser.parse("купить 15 яиц", now).allDay)
        assertTrue(parser.parse("11 треня", now).allDay)
        assertTrue(parser.parse("купить 2-3 батарейки", now).allDay)  // дефис без «:» — не интервал
    }

    @Test
    fun `не время — не матчится`() {
        assertTrue(parser.parse("в 25", now).allDay)      // не час
        assertTrue(parser.parse("в 9:75", now).allDay)    // не минуты
        assertTrue(parser.parse("встреча 15", now).allDay) // голое число без предлога
        assertTrue(parser.parse("в 007:30", now).allDay)  // часы — максимум две цифры
        assertTrue(parser.parse("с 15 до 15", now).allDay) // пустой интервал — не интервал
        assertNull(parser.parse("в 25", now).duration)
    }

    @Test
    fun `matches — TIME с точным диапазоном, отсортированы по позиции`() {
        val e = parser.parse("завтра в 15", now)
        assertEquals(2, e.matches.size)
        assertEquals(TokenMatch.Field.DATE, e.matches[0].field)
        assertEquals(TokenMatch.Field.TIME, e.matches[1].field)
        assertEquals(7..10, e.matches[1].range)  // «в 15»
    }
}
