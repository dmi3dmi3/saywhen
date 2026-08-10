package com.dmi3dmi3.saywhen.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Списки дней в повторах + «каждое 26» (задача 15, шаг 1). */
class RecurrenceListsTest {

    private val parser = RussianEventParser()

    // вторник, 14:00
    private val now = ZonedDateTime.of(2026, 7, 21, 14, 0, 0, 0, ZoneId.of("Europe/Moscow"))

    private fun String.spanOf(sub: String): IntRange {
        val i = indexOf(sub)
        require(i >= 0) { "«$sub» нет в «$this»" }
        return i until i + sub.length
    }

    @Test
    fun `каждый вторник и четверг — BYDAY-список`() {
        val text = "планёрка каждый вторник и четверг в 10"
        val e = parser.parse(text, now)
        assertEquals("FREQ=WEEKLY;BYDAY=TU,TH", e.rrule)
        assertEquals("планёрка", e.title)
        // сегодня-вторник 10:00 прошло → следующее вхождение набора — четверг
        assertEquals(LocalDate.of(2026, 7, 23), e.start.toLocalDate())
        assertEquals(10, e.start.hour)
        val rec = e.matches.single { it.field == TokenMatch.Field.RECURRENCE }
        assertEquals(text.spanOf("каждый вторник и четверг"), rec.range)
    }

    @Test
    fun `по вторникам и четвергам`() {
        val e = parser.parse("по вторникам и четвергам в 19", now)
        assertEquals("FREQ=WEEKLY;BYDAY=TU,TH", e.rrule)
        assertEquals(LocalDate.of(2026, 7, 21), e.start.toLocalDate())  // 19:00 ещё впереди
        assertEquals(19, e.start.hour)
    }

    @Test
    fun `список из трёх дней, часть без союза`() {
        val e = parser.parse("каждый понедельник среду и пятницу", now)
        assertEquals("FREQ=WEEKLY;BYDAY=MO,WE,FR", e.rrule)
        assertTrue(e.allDay)
        assertEquals(LocalDate.of(2026, 7, 22), e.start.toLocalDate())  // ближайший из набора — среда
    }

    @Test
    fun `каждое 26 без слова число — месячный повтор`() {
        val e = parser.parse("аренда каждое 26", now)
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=26", e.rrule)
        assertTrue(e.allDay)
        assertEquals(LocalDate.of(2026, 7, 26), e.start.toLocalDate())
        assertEquals("аренда", e.title)
    }

    @Test
    fun `каждые 26 — не повтор, форма не та`() {
        val e = parser.parse("каждые 26", now)
        assertNull(e.rrule)
        assertEquals("каждые 26", e.title)
    }

    @Test
    fun `список обрывается на не-дне`() {
        val e = parser.parse("каждый вторник и обед в 19", now)
        assertEquals("FREQ=WEEKLY;BYDAY=TU", e.rrule)
        assertEquals("и обед", e.title)
    }
}
