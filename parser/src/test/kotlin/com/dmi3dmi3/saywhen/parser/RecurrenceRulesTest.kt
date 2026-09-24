package com.dmi3dmi3.saywhen.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class RecurrenceRulesTest {

    private val parser = RussianEventParser()

    // вторник, 14:00
    private val now = ZonedDateTime.of(2026, 7, 21, 14, 0, 0, 0, ZoneId.of("Europe/Moscow"))

    private fun assertRrule(text: String, rrule: String, startDate: LocalDate) {
        val e = parser.parse(text, now)
        assertEquals(text, rrule, e.rrule)
        assertEquals(text, startDate, e.start.toLocalDate())
    }

    @Test
    fun `простые частоты — all-day с сегодня`() {
        assertRrule("каждый день", "FREQ=DAILY", LocalDate.of(2026, 7, 21))
        assertRrule("каждую неделю", "FREQ=WEEKLY", LocalDate.of(2026, 7, 21))
        assertRrule("каждый месяц", "FREQ=MONTHLY", LocalDate.of(2026, 7, 21))
        assertRrule("каждый год", "FREQ=YEARLY", LocalDate.of(2026, 7, 21))
        assertTrue(parser.parse("каждый день", now).allDay)
    }

    @Test
    fun `порядковый день недели месяца — слово месяца обязательно`() {
        assertRrule(
            "правление каждый первый понедельник месяца",
            "FREQ=MONTHLY;BYDAY=1MO", LocalDate.of(2026, 8, 3),
        )
        assertRrule(
            "бранч каждое второе воскресенье месяца",
            "FREQ=MONTHLY;BYDAY=2SU", LocalDate.of(2026, 8, 9),
        )
        assertRrule(
            "отчёт каждую последнюю пятницу месяца",
            "FREQ=MONTHLY;BYDAY=-1FR", LocalDate.of(2026, 7, 31),
        )
        assertEquals("правление", parser.parse("правление каждый первый понедельник месяца", now).title)
        // без «месяца» — двусмысленно («каждый второй вторник» ≈ раз в две недели), не берём
        assertNull(parser.parse("бранч каждое второе воскресенье", now).rrule)
    }

    @Test
    fun `каждый день недели — WEEKLY BYDAY со стартом в ближайший такой день`() {
        assertRrule("каждый вторник", "FREQ=WEEKLY;BYDAY=TU", LocalDate.of(2026, 7, 21))
        assertRrule("каждую пятницу", "FREQ=WEEKLY;BYDAY=FR", LocalDate.of(2026, 7, 24))
        assertRrule("каждое воскресенье", "FREQ=WEEKLY;BYDAY=SU", LocalDate.of(2026, 7, 26))
    }

    @Test
    fun `по будням, выходным и дням недели`() {
        assertRrule("по будням", "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", LocalDate.of(2026, 7, 21))
        assertRrule("по выходным", "FREQ=WEEKLY;BYDAY=SA,SU", LocalDate.of(2026, 7, 25))
        assertRrule("по вторникам", "FREQ=WEEKLY;BYDAY=TU", LocalDate.of(2026, 7, 21))
        assertRrule("уборка по субботам", "FREQ=WEEKLY;BYDAY=SA", LocalDate.of(2026, 7, 25))
    }

    @Test
    fun `каждые N единиц — INTERVAL`() {
        assertRrule("каждые 2 недели", "FREQ=WEEKLY;INTERVAL=2", LocalDate.of(2026, 7, 21))
        assertRrule("каждые 3 дня", "FREQ=DAILY;INTERVAL=3", LocalDate.of(2026, 7, 21))
        assertRrule("каждые 2 месяца", "FREQ=MONTHLY;INTERVAL=2", LocalDate.of(2026, 7, 21))
        assertRrule("каждые две недели", "FREQ=WEEKLY;INTERVAL=2", LocalDate.of(2026, 7, 21))
        assertRrule("каждые три дня", "FREQ=DAILY;INTERVAL=3", LocalDate.of(2026, 7, 21))
    }

    @Test
    fun `каждое N число — MONTHLY BYMONTHDAY со стартом в ближайшее такое число`() {
        assertRrule("каждое 15 число", "FREQ=MONTHLY;BYMONTHDAY=15", LocalDate.of(2026, 8, 15))
        assertRrule("каждое 21 число", "FREQ=MONTHLY;BYMONTHDAY=21", LocalDate.of(2026, 7, 21))
        assertRrule("зарплата каждое 10 числа", "FREQ=MONTHLY;BYMONTHDAY=10", LocalDate.of(2026, 8, 10))
    }

    @Test
    fun `повтор с временем — прошедшее время уходит на следующее вхождение`() {
        val yoga = parser.parse("йога каждый вторник в 19", now)
        assertEquals(LocalDate.of(2026, 7, 21), yoga.start.toLocalDate())  // 19 ещё впереди
        assertEquals(19, yoga.start.hour)

        val standup = parser.parse("каждый вторник в 9", now)
        assertEquals(LocalDate.of(2026, 7, 28), standup.start.toLocalDate())  // 9 прошло → след. вторник

        val daily = parser.parse("по будням в 9", now)
        assertEquals(LocalDate.of(2026, 7, 22), daily.start.toLocalDate())  // завтра среда
    }

    @Test
    fun `повтор без якоря — прошедшее время сдвигает на период, а не на день`() {
        // сказано во вторник после 9 — паттерн должен остаться вторничным
        assertEquals(LocalDate.of(2026, 7, 28), parser.parse("каждую неделю в 9", now).start.toLocalDate())
        assertEquals(LocalDate.of(2026, 8, 21), parser.parse("каждый месяц в 9", now).start.toLocalDate())
        assertEquals(LocalDate.of(2027, 7, 21), parser.parse("каждый год в 9", now).start.toLocalDate())
        assertEquals(LocalDate.of(2026, 8, 4), parser.parse("каждые 2 недели в 9", now).start.toLocalDate())
        assertEquals(LocalDate.of(2026, 7, 22), parser.parse("каждый день в 9", now).start.toLocalDate())
    }

    @Test
    fun `INTERVAL=1 не пишется — это дефолт RFC 5545`() {
        assertEquals("FREQ=WEEKLY", parser.parse("каждую 1 неделю", now).rrule)
    }

    @Test
    fun `повтор с явной датой — дата задаёт базу якоря`() {
        val e = parser.parse("каждый вторник с 3 августа", now)
        assertEquals("FREQ=WEEKLY;BYDAY=TU", e.rrule)
        assertEquals(LocalDate.of(2026, 8, 4), e.start.toLocalDate())  // 3.08 — пн, ближайший вт — 4.08
    }

    @Test
    fun `matches — RECURRENCE с точным диапазоном`() {
        val e = parser.parse("йога каждый вторник в 19", now)
        assertEquals(2, e.matches.size)
        assertEquals(TokenMatch.Field.RECURRENCE, e.matches[0].field)
        assertEquals(5..18, e.matches[0].range)  // «каждый вторник»
        assertEquals(TokenMatch.Field.TIME, e.matches[1].field)
    }

    @Test
    fun `наречия частоты — одним словом`() {
        assertRrule("зарядка ежедневно", "FREQ=DAILY", LocalDate.of(2026, 7, 21))
        assertRrule("отчёт еженедельно", "FREQ=WEEKLY", LocalDate.of(2026, 7, 21))
        assertRrule("аренда ежемесячно", "FREQ=MONTHLY", LocalDate.of(2026, 7, 21))
        assertRrule("осмотр ежегодно", "FREQ=YEARLY", LocalDate.of(2026, 7, 21))
    }

    @Test
    fun `каждое утро, вечер, ночь — DAILY с половиной суток для часа`() {
        val m = parser.parse("кофе каждое утро в 8", now)
        assertEquals("FREQ=DAILY", m.rrule)
        assertEquals(8, m.start.hour)

        val e = parser.parse("созвон каждый вечер в 8", now)
        assertEquals("FREQ=DAILY", e.rrule)
        assertEquals(20, e.start.hour)

        val n2 = parser.parse("проверка каждую ночь в 11", now)
        assertEquals("FREQ=DAILY", n2.rrule)
        assertEquals(23, n2.start.hour)
    }

    @Test
    fun `по утрам и вечерам — дательный ежедневный`() {
        val m = parser.parse("пробежка по утрам в 7", now)
        assertEquals("FREQ=DAILY", m.rrule)
        assertEquals(7, m.start.hour)
        assertEquals("FREQ=DAILY", parser.parse("чтение по вечерам", now).rrule)
    }

    @Test
    fun `каждое пятое воскресенье месяца`() {
        assertRrule(
            "бранч каждое пятое воскресенье месяца",
            "FREQ=MONTHLY;BYDAY=5SU", LocalDate.of(2026, 8, 30),
        )
    }

    @Test
    fun `каждые выходные`() {
        assertRrule("дача каждые выходные", "FREQ=WEEKLY;BYDAY=SA,SU", LocalDate.of(2026, 7, 25))
    }

    @Test
    fun `раз в единицу — частота без каждый`() {
        assertRrule("уборка раз в неделю", "FREQ=WEEKLY", LocalDate.of(2026, 7, 21))
        assertRrule("отчёт раз в месяц", "FREQ=MONTHLY", LocalDate.of(2026, 7, 21))
        assertRrule("стрижка раз в 2 недели", "FREQ=WEEKLY;INTERVAL=2", LocalDate.of(2026, 7, 21))
    }

    @Test
    fun `не повтор — не матчится`() {
        assertNull(parser.parse("каждый раз хорошо", now).rrule)
        assertNull(parser.parse("каждые 500 недель", now).rrule)
        assertNull(parser.parse("завтра в 15", now).rrule)
    }
}
