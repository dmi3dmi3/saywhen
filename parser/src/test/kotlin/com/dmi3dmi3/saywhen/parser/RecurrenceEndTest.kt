package com.dmi3dmi3.saywhen.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** Конец повторов: UNTIL/COUNT (задача 15, шаг 5). Ищутся только при найденном повторе. */
class RecurrenceEndTest {

    private val parser = RussianEventParser()

    // вторник, 14:00
    private val now = ZonedDateTime.of(2026, 7, 21, 14, 0, 0, 0, ZoneId.of("Europe/Moscow"))

    private fun String.spanOf(sub: String): IntRange {
        val i = indexOf(sub)
        require(i >= 0) { "«$sub» нет в «$this»" }
        return i until i + sub.length
    }

    @Test
    fun `до конца месяца-имени, timed — UNTIL в UTC`() {
        val e = parser.parse("йога каждый вторник в 19 до конца августа", now)
        // 31.08 23:59:59 МСК (UTC+3) → 20:59:59Z
        assertEquals("FREQ=WEEKLY;BYDAY=TU;UNTIL=20260831T205959Z", e.rrule)
        assertEquals("йога", e.title)
        assertEquals(19, e.start.hour)
    }

    @Test
    fun `до конца года, all-day — UNTIL датой`() {
        val e = parser.parse("аренда каждое 26 до конца года", now)
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=26;UNTIL=20261231", e.rrule)
        assertTrue(e.allDay)
        assertEquals("аренда", e.title)
    }

    @Test
    fun `до конца недели — ближайшее воскресенье`() {
        val e = parser.parse("каждый день до конца недели", now)
        assertEquals("FREQ=DAILY;UNTIL=20260726", e.rrule)
    }

    @Test
    fun `до месяца без дня — канун первого числа`() {
        // паритет с en «until september» (ревью 17)
        val e = parser.parse("каждый день до сентября", now)
        assertEquals("FREQ=DAILY;UNTIL=20260831", e.rrule)
    }

    @Test
    fun `до дня с месяцем`() {
        val e = parser.parse("каждый день до 15 сентября", now)
        assertEquals("FREQ=DAILY;UNTIL=20260915", e.rrule)
        assertEquals("Событие", e.title)
    }

    @Test
    fun `сокращение месяца в хвосте`() {
        val e = parser.parse("каждый день до 15 сен", now)
        assertEquals("FREQ=DAILY;UNTIL=20260915", e.rrule)
    }

    @Test
    fun `N раз — COUNT`() {
        val e = parser.parse("стендап по будням 10 раз", now)
        assertEquals("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR;COUNT=10", e.rrule)
        assertEquals("стендап", e.title)
    }

    @Test
    fun `хвост подсвечивается как повтор`() {
        val text = "йога каждый вторник до конца августа"
        val e = parser.parse(text, now)
        val ranges = e.matches.filter { it.field == TokenMatch.Field.RECURRENCE }.map { it.range }
        assertTrue(text.spanOf("каждый вторник") in ranges)
        assertTrue(text.spanOf("до конца августа") in ranges)
    }

    @Test
    fun `UNTIL раньше следующего вхождения — старт не гонится за UNTIL`() {
        // фикс по ревью блока 12–15: иначе создалась бы серия без единого вхождения
        val e = parser.parse("планёрка каждый вторник в 9 до 21 июля", now)
        assertEquals("FREQ=WEEKLY;BYDAY=TU;UNTIL=20260721T205959Z", e.rrule)
        // 9:00 сегодня прошло, но next (28.07) за UNTIL — остаёмся на 21.07
        assertEquals(21, e.start.dayOfMonth)
        assertEquals(9, e.start.hour)
    }

    @Test
    fun `два хвоста в одной фразе — побеждает первый, второй остаётся текстом`() {
        // документация поведения: UNTIL+COUNT вместе запрещены RFC 5545
        val e = parser.parse("зарядка каждый день 10 раз до конца года", now)
        assertEquals("FREQ=DAILY;COUNT=10", e.rrule)
        assertEquals("зарядка до конца года", e.title)
    }

    @Test
    fun `без повтора хвосты — обычный текст`() {
        val e1 = parser.parse("до конца августа", now)
        assertNull(e1.rrule)
        assertEquals("до конца августа", e1.title)

        val e2 = parser.parse("сдать отчёт 10 раз", now)
        assertNull(e2.rrule)
        assertEquals("сдать отчёт 10 раз", e2.title)
    }
}
