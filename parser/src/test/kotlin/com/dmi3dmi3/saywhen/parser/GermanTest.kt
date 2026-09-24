package com.dmi3dmi3.saywhen.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Немецкий транслятор (задача 27a) — через боевой MultilingualEventParser. */
class GermanTest {

    private val parser = MultilingualEventParser()

    // вторник, 14:00
    private val now = ZonedDateTime.of(2026, 7, 21, 14, 0, 0, 0, ZoneId.of("Europe/Moscow"))

    private fun assertDate(text: String, expected: LocalDate) {
        assertEquals(text, expected, parser.parse(text, now).start.toLocalDate())
    }

    private fun assertTime(text: String, hour: Int, minute: Int = 0) {
        val e = parser.parse(text, now)
        assertEquals(text, hour, e.start.hour)
        assertEquals(text, minute, e.start.minute)
    }

    @Test
    fun `смок — abendessen morgen um 9`() {
        val e = parser.parse("abendessen morgen um 9", now)
        assertEquals("abendessen", e.title)
        assertEquals(ZonedDateTime.of(2026, 7, 22, 9, 0, 0, 0, now.zone), e.start)
    }

    // --- даты ---

    @Test
    fun `дни недели — nächsten, kommenden, diesen`() {
        assertDate("freitag", LocalDate.of(2026, 7, 24))
        assertDate("nächsten dienstag", LocalDate.of(2026, 7, 28))
        assertDate("kommenden montag", LocalDate.of(2026, 7, 27))
        assertDate("diesen montag", LocalDate.of(2026, 7, 27))
    }

    @Test
    fun `календарная дата — ординальная точка, суффиксы, словами, месяц впереди`() {
        assertDate("am 15. februar", LocalDate.of(2027, 2, 15))
        assertDate("februar 15", LocalDate.of(2027, 2, 15))
        assertDate("15te februar", LocalDate.of(2027, 2, 15))
        assertDate("erster märz", LocalDate.of(2027, 3, 1))
        assertDate("am vierten dezember", LocalDate.of(2026, 12, 4))
        assertDate("3. august 2027", LocalDate.of(2027, 8, 3))
    }

    @Test
    fun `ведущий день недели уступает дате`() {
        assertDate("Montag, 18. Februar", LocalDate.of(2027, 2, 18))
        assertDate("Montag, Februar 18", LocalDate.of(2027, 2, 18))
    }

    @Test
    fun `in — датные сдвиги`() {
        assertDate("in 2 wochen", LocalDate.of(2026, 8, 4))
        assertDate("in einer woche", LocalDate.of(2026, 7, 28))
        assertDate("in 7 tagen", LocalDate.of(2026, 7, 28))
        assertDate("in 3 jahren", LocalDate.of(2029, 7, 21))
    }

    @Test
    fun `диапазоны — vom-bis и точечный дефис`() {
        val e = parser.parse("urlaub vom 13 bis 15 august", now)
        assertEquals(LocalDate.of(2026, 8, 13), e.start.toLocalDate())
        assertEquals(Duration.ofDays(3), e.duration)
        // «13. - 15. august» — раньше крался парой как 13:15
        val dotted = parser.parse("13. - 15. august", now)
        assertEquals(LocalDate.of(2026, 8, 13), dotted.start.toLocalDate())
        assertTrue(dotted.allDay)
    }

    @Test
    fun `heute abend и morgen früh — половины суток`() {
        assertTime("essen heute abend um 8", 20)
        assertTime("laufen morgen früh um 9", 9)
        assertEquals(
            LocalDate.of(2026, 7, 22),
            parser.parse("laufen morgen früh um 9", now).start.toLocalDate(),
        )
        assertTrue(parser.parse("hausaufgaben heute abend", now).allDay)
    }

    // --- время ---

    @Test
    fun `halb 4 — русская логика, час минус один`() {
        assertTime("halb 4", 15, 30)
        assertTime("um halb 8", 19, 30)
        assertTime("halb vier uhr nachmittags", 15, 30)
    }

    @Test
    fun `viertel и минуты через nach-vor`() {
        assertTime("viertel nach 3", 15, 15)
        assertTime("viertel vor 12", 11, 45)
        assertTime("20 nach 3", 15, 20)
        assertTime("15 minuten vor 12", 11, 45)
    }

    @Test
    fun `uhr-формы — с парой, склейкой и половиной суток`() {
        assertTime("3 uhr", 15)
        assertTime("meeting 15 uhr 30", 15, 30)
        assertTime("18uhr", 18)
        assertTime("8 uhr am abend", 20)
        assertTime("um drei", 15)
        assertTime("3:18 früh", 3, 18)
    }

    @Test
    fun `приблизительность — сироты уходят с матчем`() {
        val e = parser.parse("so gegen 15 uhr", now)
        assertEquals(15, e.start.hour)
        assertEquals("Termin", e.title)
    }

    @Test
    fun `viertelstunde — офсеты одним словом`() {
        assertTime("in einer viertelstunde", 14, 15)
        assertTime("in einer dreiviertelstunde", 14, 45)
    }

    @Test
    fun `14-tägig — раз в две недели одним словом`() {
        assertEquals("FREQ=DAILY;INTERVAL=14", parser.parse("backup 14-tägig", now).rrule)
    }

    @Test
    fun `день недели плюс следующая неделя`() {
        assertDate("mittwoch nächste woche", LocalDate.of(2026, 7, 29))
        assertDate("mittwoch der nächsten woche", LocalDate.of(2026, 7, 29))
        assertDate("dienstag dieser woche", LocalDate.of(2026, 7, 21))
    }

    @Test
    fun `mittag und mitternacht`() {
        assertTime("treffen zu mittag", 12)
        assertEquals(0, parser.parse("um mitternacht", now).start.hour)
    }

    @Test
    fun `интервалы von-bis и zwischen-und`() {
        val e = parser.parse("von 9:30 bis 11:00", now)
        assertEquals(9, e.start.hour)
        assertEquals(Duration.ofMinutes(90), e.duration)
        val z = parser.parse("zwischen 9:30 und 11:00", now)
        assertEquals(Duration.ofMinutes(90), z.duration)
        assertEquals(Duration.ofHours(2), parser.parse("von 9 bis 11", now).duration)
    }

    @Test
    fun `in — офсет времени, включая половину и десятичные`() {
        assertTime("anruf in 2 stunden", 16)
        assertTime("in einer halben stunde", 14, 30)
        assertTime("in 30 minuten", 14, 30)
        assertTime("in 2,5 stunden", 16, 30)
    }

    // --- длительности ---

    @Test
    fun `für — единицы, идиомы, десятичные, склейки`() {
        assertEquals(120, parser.parse("anruf um 15 für 2 stunden", now).duration!!.toMinutes())
        assertEquals(30, parser.parse("um 15 für eine halbe stunde", now).duration!!.toMinutes())
        assertEquals(90, parser.parse("kino um 21 für anderthalb stunden", now).duration!!.toMinutes())
        assertEquals(90, parser.parse("um 15 für 1,5 stunden", now).duration!!.toMinutes())
        assertEquals(150, parser.parse("um 15 für 2 stunden und 30", now).duration!!.toMinutes())
        assertEquals(165, parser.parse("meeting um 11 2std45min", now).duration!!.toMinutes())
    }

    // --- повторы ---

    @Test
    fun `jeden и наречия — частоты`() {
        assertEquals("FREQ=DAILY", parser.parse("tabletten jeden tag", now).rrule)
        assertEquals("FREQ=DAILY", parser.parse("täglich", now).rrule)
        assertEquals("FREQ=WEEKLY", parser.parse("wöchentlich", now).rrule)
        assertEquals("FREQ=WEEKLY", parser.parse("jede woche", now).rrule)
        assertEquals("FREQ=MONTHLY", parser.parse("monatlich", now).rrule)
        assertEquals("FREQ=YEARLY", parser.parse("jedes jahr", now).rrule)
    }

    @Test
    fun `jeden dienstag, montags-хабитуал, werktags, wochenende`() {
        assertEquals("FREQ=WEEKLY;BYDAY=TU,TH", parser.parse("yoga jeden dienstag und donnerstag", now).rrule)
        assertEquals("FREQ=WEEKLY;BYDAY=MO", parser.parse("kurs montags", now).rrule)
        assertEquals("FREQ=WEEKLY;BYDAY=MO,FR", parser.parse("kurs montags und freitags", now).rrule)
        assertEquals("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", parser.parse("standup werktags", now).rrule)
        assertEquals("FREQ=WEEKLY;BYDAY=SA,SU", parser.parse("wandern jedes wochenende", now).rrule)
    }

    @Test
    fun `alle N wochen и jede zweite woche — интервалы`() {
        assertEquals("FREQ=WEEKLY;INTERVAL=2", parser.parse("alle 2 wochen", now).rrule)
        assertEquals("FREQ=WEEKLY;INTERVAL=2", parser.parse("alle zwei wochen", now).rrule)
        assertEquals("FREQ=WEEKLY;INTERVAL=2", parser.parse("jede zweite woche", now).rrule)
    }

    @Test
    fun `einmal pro — частота без jeden`() {
        assertEquals("FREQ=WEEKLY", parser.parse("putzen einmal pro woche", now).rrule)
        assertEquals("FREQ=MONTHLY", parser.parse("bericht einmal im monat", now).rrule)
    }

    @Test
    fun `число месяца и порядковый день недели`() {
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=15", parser.parse("gehalt jeden 15ten", now).rrule)
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=15", parser.parse("am 15. jedes monats", now).rrule)
        assertEquals("FREQ=MONTHLY;BYDAY=1MO", parser.parse("treffen jeden ersten montag im monat", now).rrule)
        assertEquals("FREQ=MONTHLY;BYDAY=-1FR", parser.parse("bericht jeden letzten freitag im monat", now).rrule)
    }

    @Test
    fun `jeden morgen — ежедневно с половиной суток`() {
        val e = parser.parse("kaffee jeden morgen um 8", now)
        assertEquals("FREQ=DAILY", e.rrule)
        assertEquals(8, e.start.hour)
    }

    @Test
    fun `конец повтора — bis и mal`() {
        assertEquals("FREQ=DAILY;UNTIL=20260831", parser.parse("jeden tag bis ende august", now).rrule)
        assertEquals(
            "FREQ=DAILY;UNTIL=20260915",
            parser.parse("jeden tag bis zum 15. september", now).rrule,
        )
        assertEquals("FREQ=DAILY;UNTIL=20260831", parser.parse("jeden tag bis september", now).rrule)
        assertEquals("FREQ=WEEKLY;BYDAY=TU;COUNT=10", parser.parse("yoga jeden dienstag 10 mal", now).rrule)
    }

    @Test
    fun `годовая дата`() {
        val e = parser.parse("versicherung jedes jahr am 3. august", now)
        assertEquals("FREQ=YEARLY", e.rrule)
        assertEquals(LocalDate.of(2026, 8, 3), e.start.toLocalDate())
    }

    // --- негативы вердиктов ---

    @Test
    fun `негативы — периоды, zweimal, jeden zweiten dienstag, числовые даты`() {
        for (phrase in listOf("diese woche", "nächste woche", "am wochenende")) {
            val e = parser.parse(phrase, now)
            assertTrue(phrase, e.allDay)
            assertEquals(phrase, LocalDate.of(2026, 7, 21), e.start.toLocalDate())
            assertNull(phrase, e.rrule)
        }
        assertNull(parser.parse("anrufen zweimal pro woche", now).rrule)
        assertNull(parser.parse("jeden zweiten dienstag", now).rrule)
        assertTrue(parser.parse("notizen 15.2", now).allDay)
        // двухбуквенные сокращения дней — не берём: «do» — живое слово
        assertEquals(LocalDate.of(2026, 7, 22), parser.parse("do homework tomorrow", now).start.toLocalDate())
    }

    // --- смешение языков ---

    @Test
    fun `смешанные фразы`() {
        val e = parser.parse("созвон morgen at 5pm", now)
        assertEquals(LocalDate.of(2026, 7, 22), e.start.toLocalDate())
        assertEquals(17, e.start.hour)
    }

    @Test
    fun `wochentags — синоним werktags`() {
        assertEquals(
            "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR",
            parser.parse("sport wochentags um 9", now).rrule,
        )
    }

    @Test
    fun `einmal am tag — ежедневно`() {
        assertEquals("FREQ=DAILY", parser.parse("tabletten einmal am tag um 8", now).rrule)
    }
}
