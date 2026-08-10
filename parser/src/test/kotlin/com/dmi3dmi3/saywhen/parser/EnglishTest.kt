package com.dmi3dmi3.saywhen.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Английский транслятор (задача 17) — через боевой MultilingualEventParser. */
class EnglishTest {

    private val parser = MultilingualEventParser()

    // вторник, 14:00
    private val now = ZonedDateTime.of(2026, 7, 21, 14, 0, 0, 0, ZoneId.of("Europe/Moscow"))

    private fun at(day: Int, hour: Int, minute: Int = 0): ZonedDateTime =
        ZonedDateTime.of(2026, 7, day, hour, minute, 0, 0, now.zone)

    private fun assertStart(text: String, expected: ZonedDateTime) {
        assertEquals(text, expected, parser.parse(text, now).start)
    }

    // --- даты и время ---

    @Test
    fun `am-pm — явная половина суток`() {
        val e = parser.parse("dentist tomorrow at 3pm", now)
        assertEquals(at(22, 15), e.start)
        assertEquals("dentist", e.title)
        assertStart("at 5 pm", at(21, 17))  // маркер отдельным токеном
    }

    @Test
    fun `часы словами — круг`() {
        assertStart("meeting at five", at(21, 17))
    }

    @Test
    fun `noon и midnight`() {
        val e = parser.parse("lunch at noon", now)
        assertEquals(at(22, 12), e.start)  // 12:00 прошло → завтра
        assertEquals("lunch", e.title)
        assertStart("midnight", at(22, 0))
    }

    @Test
    fun `день недели с временем`() {
        assertStart("call on friday at 9:30", at(24, 9, 30))  // день впереди → окно → ранний
        assertStart("next tuesday at 9", at(28, 9))
    }

    @Test
    fun `календарная дата в обоих порядках`() {
        for (text in listOf("birthday August 3", "birthday 3 August")) {
            val e = parser.parse(text, now)
            assertTrue(text, e.allDay)
            assertEquals(text, LocalDate.of(2026, 8, 3), e.start.toLocalDate())
            assertEquals(text, "birthday", e.title)
        }
    }

    @Test
    fun `сокращения месяцев`() {
        for (text in listOf("birthday Aug 3", "birthday 3 Aug")) {
            val e = parser.parse(text, now)
            assertTrue(text, e.allDay)
            assertEquals(text, LocalDate.of(2026, 8, 3), e.start.toLocalDate())
        }
        assertEquals(
            "FREQ=DAILY;UNTIL=20260915",
            parser.parse("every day until Sep 15", now).rrule,
        )
    }

    @Test
    fun `сокращения дней недели`() {
        val e = parser.parse("call on fri", now)
        assertTrue(e.allDay)
        assertEquals(LocalDate.of(2026, 7, 24), e.start.toLocalDate())
        assertStart("next tue at 9", at(28, 9))
        assertEquals("FREQ=WEEKLY;BYDAY=TH", parser.parse("gym every thu", now).rrule)
        // "sat"/"sun"/"wed" — омографы, как месяцы: для домена ввода событий принято
        assertEquals(LocalDate.of(2026, 7, 25), parser.parse("brunch sat", now).start.toLocalDate())
    }

    @Test
    fun `смещение in N weeks`() {
        val e = parser.parse("checkup in 2 weeks", now)
        assertTrue(e.allDay)
        assertEquals(LocalDate.of(2026, 8, 4), e.start.toLocalDate())
        assertEquals("checkup", e.title)
    }

    @Test
    fun `half past — круг от половины`() {
        assertStart("half past six", at(21, 18, 30))
    }

    @Test
    fun `голая пара час-минуты и минуты отдельным токеном после at`() {
        assertStart("16 40 lunch", at(21, 16, 40))
        assertStart("lunch tomorrow 11 00", at(22, 11))
        assertStart("call at 11 30", at(22, 11, 30))  // 23:30 вне окна; 11:30 прошло → завтра
        assertStart("at 11 30 pm meeting", at(21, 23, 30))
    }

    @Test
    fun `голый час после клейма — WEAK`() {
        val e = parser.parse("standup tomorrow 11", now)
        assertEquals(at(22, 11), e.start)
        assertEquals("standup", e.title)
    }

    @Test
    fun `fallback и дефолтный заголовок`() {
        val e = parser.parse("buy cat food", now)
        assertTrue(e.allDay)
        assertEquals("buy cat food", e.title)
        // пусто: покрытия нет ни у кого → первый транслятор (русский)
        assertEquals("Событие", parser.parse("", now).title)
    }

    // --- повторы и концы ---

    @Test
    fun `список дней с and`() {
        val e = parser.parse("yoga every tuesday and thursday at 7pm", now)
        assertEquals("FREQ=WEEKLY;BYDAY=TU,TH", e.rrule)
        assertEquals(at(21, 19), e.start)  // 19:00 сегодня-вторника ещё впереди
        assertEquals("yoga", e.title)
    }

    @Test
    fun `on weekdays`() {
        val e = parser.parse("standup on weekdays at 9", now)
        assertEquals("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", e.rrule)
        assertEquals(9, e.start.hour)  // окно: 9:00, не 21:00
        assertEquals("standup", e.title)
    }

    @Test
    fun `every Nth — месячный повтор`() {
        val e = parser.parse("rent every 26th", now)
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=26", e.rrule)
        assertTrue(e.allDay)
        assertEquals(LocalDate.of(2026, 7, 26), e.start.toLocalDate())
    }

    @Test
    fun `every N units — интервал`() {
        assertEquals("FREQ=WEEKLY;INTERVAL=2", parser.parse("backup every 2 weeks", now).rrule)
    }

    @Test
    fun `weekly и N times`() {
        val e = parser.parse("report weekly 10 times", now)
        assertEquals("FREQ=WEEKLY;COUNT=10", e.rrule)
        assertEquals("report", e.title)
    }

    @Test
    fun `until the end of month-name`() {
        assertEquals(
            "FREQ=DAILY;UNTIL=20260831",
            parser.parse("every day until the end of august", now).rrule,
        )
    }

    @Test
    fun `until с датой, timed — UTC`() {
        assertEquals(
            "FREQ=WEEKLY;BYDAY=TU;UNTIL=20260915T205959Z",
            parser.parse("every tuesday at 7pm until September 15", now).rrule,
        )
    }

    @Test
    fun `длительности for`() {
        val e = parser.parse("sync tomorrow at 2pm for half an hour", now)
        assertEquals(30, e.duration!!.toMinutes())
        assertEquals("sync", e.title)
        assertEquals(120, parser.parse("at 2pm for 2 hours", now).duration!!.toMinutes())
    }

    @Test
    fun `длительности словами`() {
        assertEquals(180, parser.parse("meeting at 11 for three hours", now).duration!!.toMinutes())
        assertEquals(90, parser.parse("at 2pm for an hour and a half", now).duration!!.toMinutes())
        assertEquals(45, parser.parse("at 2pm for forty five minutes", now).duration!!.toMinutes())
    }

    @Test
    fun `интервал from-to`() {
        val e = parser.parse("workshop tomorrow from 3pm to 5pm", now)
        assertEquals(at(22, 15), e.start)
        assertEquals(120, e.duration!!.toMinutes())
        assertEquals("workshop", e.title)
    }

    @Test
    fun `смешанный интервал — am-pm конца распространяется на голое начало`() {
        // ревью 17: «from 3 to 5pm» — это 15:00–17:00, не 03:00 + 14 часов
        val e = parser.parse("workshop tomorrow from 3 to 5pm", now)
        assertEquals(at(22, 15), e.start)
        assertEquals(120, e.duration!!.toMinutes())
        // выбор половины суток — по минимальной положительной длительности
        val e2 = parser.parse("shift tomorrow from 11 to 1pm", now)
        assertEquals(at(22, 11), e2.start)
        assertEquals(120, e2.duration!!.toMinutes())
    }

    @Test
    fun `until с месяцем без дня — канун первого числа`() {
        // ревью 17: «until september» = пока не наступит сентябрь
        assertEquals(
            "FREQ=DAILY;UNTIL=20260831",
            parser.parse("every day until september", now).rrule,
        )
    }

    @Test
    fun `until the Nth of Month`() {
        assertEquals(
            "FREQ=DAILY;UNTIL=20260915",
            parser.parse("every day until the 15th of september", now).rrule,
        )
    }

    @Test
    fun `формы из спеки без отдельных веток тестов`() {
        assertEquals("FREQ=DAILY;UNTIL=20260915", parser.parse("every day until 15 September", now).rrule)
        assertEquals("FREQ=DAILY;UNTIL=20260726", parser.parse("every day till the end of the week", now).rrule)
        assertEquals("FREQ=WEEKLY;BYDAY=SA,SU", parser.parse("brunch on weekends", now).rrule)
        assertEquals("FREQ=MONTHLY", parser.parse("review monthly", now).rrule)
        assertStart("call in a week at 5:30pm", ZonedDateTime.of(2026, 7, 28, 17, 30, 0, 0, now.zone))
    }

    @Test
    fun `месяцы-омографы — осознанный трейдофф`() {
        // «may»/«march» как глаголы дают ложную биграмму «месяц+число»;
        // для домена ввода событий принято (ревью 17), фиксируем поведение
        val e = parser.parse("you may 3 want this", now)
        assertEquals(LocalDate.of(2027, 5, 3), e.start.toLocalDate())
        assertEquals("you want this", e.title)
    }

    // --- диапазоны дат ---

    @Test
    fun `диапазон дат — to и дефис, месяц с любой стороны`() {
        for (text in listOf(
            "vacation Aug 23 to 28", "vacation Aug 23-28",
            "vacation 23-28 August", "vacation from 23 to 28 August",
        )) {
            val e = parser.parse(text, now)
            assertTrue(text, e.allDay)
            assertEquals(text, LocalDate.of(2026, 8, 23), e.start.toLocalDate())
            assertEquals(text, Duration.ofDays(6), e.duration)
            assertEquals(text, "vacation", e.title)
        }
    }

    @Test
    fun `диапазон через границу месяца`() {
        val e = parser.parse("trip Aug 30 to Sep 2", now)
        assertTrue(e.allDay)
        assertEquals(LocalDate.of(2026, 8, 30), e.start.toLocalDate())
        assertEquals(Duration.ofDays(4), e.duration)
    }
}
