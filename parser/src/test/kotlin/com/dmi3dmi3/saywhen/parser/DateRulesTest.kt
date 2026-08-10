package com.dmi3dmi3.saywhen.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class DateRulesTest {

    private val parser = RussianEventParser()

    // вторник, 14:00
    private val now = ZonedDateTime.of(2026, 7, 21, 14, 0, 0, 0, ZoneId.of("Europe/Moscow"))

    private fun assertDate(text: String, expected: LocalDate) {
        val e = parser.parse(text, now)
        assertEquals(text, expected.atStartOfDay(now.zone), e.start)  // полночь в зоне запроса
        assertTrue(text, e.allDay)
    }

    @Test
    fun `относительные даты`() {
        assertDate("сегодня", LocalDate.of(2026, 7, 21))
        assertDate("завтра", LocalDate.of(2026, 7, 22))
        assertDate("послезавтра", LocalDate.of(2026, 7, 23))
        assertDate("Ужин завтра", LocalDate.of(2026, 7, 22))
    }

    @Test
    fun `дни недели — ближайший будущий, сегодня подходит`() {
        assertDate("в пятницу", LocalDate.of(2026, 7, 24))
        assertDate("в среду", LocalDate.of(2026, 7, 22))
        assertDate("во вторник", LocalDate.of(2026, 7, 21))   // сегодня вторник → сегодня
        assertDate("в понедельник", LocalDate.of(2026, 7, 27)) // прошёл → следующая неделя
        assertDate("суббота", LocalDate.of(2026, 7, 25))       // голый день недели
        assertDate("уборка в воскресенье", LocalDate.of(2026, 7, 26))
    }

    @Test
    fun `календарные даты — родительный падеж, прошедшие уходят на следующий год`() {
        assertDate("3 августа", LocalDate.of(2026, 8, 3))
        assertDate("обед 21 июля", LocalDate.of(2026, 7, 21))    // сегодня — ещё не прошло
        assertDate("1 января", LocalDate.of(2027, 1, 1))         // прошло → следующий год
        assertDate("отчёт 15 декабря", LocalDate.of(2026, 12, 15))
    }

    @Test
    fun `сокращения месяцев`() {
        assertDate("1 авг", LocalDate.of(2026, 8, 1))
        assertDate("1 авг.", LocalDate.of(2026, 8, 1))  // точку отбрасывает токенайзер
        assertDate("отчёт 15 дек", LocalDate.of(2026, 12, 15))
        assertDate("5 янв", LocalDate.of(2027, 1, 5))    // прошло → следующий год
        assertDate("встреча 30 сент", LocalDate.of(2026, 9, 30))
    }

    @Test
    fun `сокращения дней недели`() {
        assertDate("встреча в пт", LocalDate.of(2026, 7, 24))
        assertDate("в пт.", LocalDate.of(2026, 7, 24))  // точку отбрасывает токенайзер
        assertDate("сб", LocalDate.of(2026, 7, 25))
        assertDate("уборка в вск", LocalDate.of(2026, 7, 26))
        assertDate("в следующий пн", LocalDate.of(2026, 7, 27))
        // словарь общий с повторами
        assertEquals("FREQ=WEEKLY;BYDAY=TU", parser.parse("планёрка каждый вт", now).rrule)
    }

    @Test
    fun `следующая неделя — день следующей календарной недели`() {
        assertDate("в следующую пятницу", LocalDate.of(2026, 7, 31))
        assertDate("следующая пятница", LocalDate.of(2026, 7, 31))
        assertDate("в следующий вторник", LocalDate.of(2026, 7, 28))  // не сегодня, хоть сегодня и вторник
        assertDate("следующее воскресенье", LocalDate.of(2026, 8, 2))
        assertDate("созвон в следующий понедельник", LocalDate.of(2026, 7, 27))
    }

    @Test
    fun `эта неделя — синоним ближайшего дня`() {
        assertDate("в эту пятницу", LocalDate.of(2026, 7, 24))
        assertDate("эта суббота", LocalDate.of(2026, 7, 25))
        assertDate("в этот вторник", LocalDate.of(2026, 7, 21))  // сегодня
    }

    @Test
    fun `матч следующей пятницы — целиком с предлогом и прилагательным`() {
        val e = parser.parse("обед в следующую пятницу", now)
        assertEquals(1, e.matches.size)
        assertEquals(5..23, e.matches[0].range)  // «в следующую пятницу»
    }

    @Test
    fun `несуществующая дата — не матч, fallback на сегодня`() {
        val e = parser.parse("31 февраля", now)
        assertEquals(LocalDate.of(2026, 7, 21), e.start.toLocalDate())
        assertTrue(e.matches.isEmpty())
    }

    @Test
    fun `через N единиц`() {
        assertDate("через неделю", LocalDate.of(2026, 7, 28))
        assertDate("через день", LocalDate.of(2026, 7, 22))
        assertDate("через 3 дня", LocalDate.of(2026, 7, 24))
        assertDate("через 2 недели", LocalDate.of(2026, 8, 4))
        assertDate("через месяц", LocalDate.of(2026, 8, 21))
    }

    @Test
    fun `абсурдные смещения — не матч и не крэш`() {
        for (text in listOf("через 999999999999 месяцев", "через 99999999 дней", "через 0 дней")) {
            val e = parser.parse(text, now)  // не должно кинуть
            assertEquals(text, LocalDate.of(2026, 7, 21), e.start.toLocalDate())
            assertTrue(text, e.matches.isEmpty())
        }
    }

    @Test
    fun `первый кандидат побеждает`() {
        val e = parser.parse("Сегодня делаем ужин на вторник", now)

        assertEquals(LocalDate.of(2026, 7, 21), e.start.toLocalDate())
        assertEquals(1, e.matches.size)  // второй кандидат («вторник») не матчится
    }

    @Test
    fun `matches — точный диапазон с предлогом`() {
        val e = parser.parse("ужин в пятницу", now)

        assertEquals(1, e.matches.size)
        assertEquals(5..13, e.matches[0].range)  // «в пятницу»
        assertEquals(TokenMatch.Field.DATE, e.matches[0].field)
    }

    @Test
    fun `текст без даты — по-прежнему сегодня без matches`() {
        val e = parser.parse("купить корм коту", now)
        assertEquals(LocalDate.of(2026, 7, 21), e.start.toLocalDate())
        assertTrue(e.matches.isEmpty())
    }

    // --- диапазоны дат: «с 23 по 28 августа» → многодневное all-day ---

    private fun assertRange(text: String, start: LocalDate, days: Long) {
        val e = parser.parse(text, now)
        assertEquals(text, start.atStartOfDay(now.zone), e.start)
        assertTrue(text, e.allDay)
        assertEquals(text, Duration.ofDays(days), e.duration)
    }

    @Test
    fun `диапазон дат — с … по …, конец включительно`() {
        assertRange("с 23 по 28 августа белые ключи", LocalDate.of(2026, 8, 23), 6)
        assertRange("отпуск с 1 по 3 сентября", LocalDate.of(2026, 9, 1), 3)
        assertRange("с 23 августа по 28 августа", LocalDate.of(2026, 8, 23), 6)
    }

    @Test
    fun `диапазон — заголовок и подсветка целиком, «с» поглощается`() {
        val e = parser.parse("с 23 по 28 августа белые ключи", now)
        assertEquals("белые ключи", e.title)
        assertEquals(1, e.matches.size)
        assertEquals(0..17, e.matches[0].range)  // «с 23 по 28 августа»
    }

    @Test
    fun `диапазон через границу месяца`() {
        assertRange("с 30 августа по 2 сентября", LocalDate.of(2026, 8, 30), 4)
    }

    @Test
    fun `диапазон дефисом`() {
        assertRange("23-28 августа", LocalDate.of(2026, 8, 23), 6)
        assertRange("ремонт 10-11 августа", LocalDate.of(2026, 8, 10), 2)  // не интервал времени
    }

    @Test
    fun `прошедший диапазон — следующий год, идущий сейчас — остаётся`() {
        assertRange("с 1 по 5 марта", LocalDate.of(2027, 3, 1), 5)
        assertRange("с 20 по 25 июля", LocalDate.of(2026, 7, 20), 6)  // конец впереди, начало уже идёт
    }

    @Test
    fun `конец не позже начала — не диапазон`() {
        val e = parser.parse("с 28 по 23 августа", now)
        assertEquals(LocalDate.of(2026, 8, 23), e.start.toLocalDate())  // одиночное «23 августа»
        assertNull(e.duration)
    }

    @Test
    fun `диапазон с явным временем — timed на дату старта`() {
        val e = parser.parse("заезд с 23 по 28 августа в 14", now)
        assertEquals(ZonedDateTime.of(2026, 8, 23, 14, 0, 0, 0, now.zone), e.start)
        assertTrue(!e.allDay)
        assertNull(e.duration)  // длительность диапазона — только для all-day
    }
}
