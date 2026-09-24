package com.dmi3dmi3.saywhen.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Итальянский транслятор (задача 26) — через боевой MultilingualEventParser. */
class ItalianTest {

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
    fun `смок — cena domani alle 9`() {
        val e = parser.parse("cena domani alle 9", now)
        assertEquals("cena", e.title)
        assertEquals(ZonedDateTime.of(2026, 7, 22, 9, 0, 0, 0, now.zone), e.start)
    }

    // --- даты ---

    @Test
    fun `относительные дни, dopo domani раздельно`() {
        assertDate("oggi", LocalDate.of(2026, 7, 21))
        assertDate("dopodomani", LocalDate.of(2026, 7, 23))
        assertDate("visita dopo domani", LocalDate.of(2026, 7, 23))
    }

    @Test
    fun `дни недели, сокращения, prossimo`() {
        assertDate("venerdì", LocalDate.of(2026, 7, 24))
        assertDate("mercoledi", LocalDate.of(2026, 7, 22))       // без акцента
        assertDate("palestra ven", LocalDate.of(2026, 7, 24))
        assertDate("martedì prossimo", LocalDate.of(2026, 7, 28))
        assertDate("prossimo lunedì", LocalDate.of(2026, 7, 27))
    }

    @Test
    fun `календарная дата — il, primo, явный год`() {
        assertDate("il 3 agosto", LocalDate.of(2026, 8, 3))
        assertDate("primo marzo", LocalDate.of(2027, 3, 1))
        assertDate("il 1º marzo", LocalDate.of(2027, 3, 1))
        assertDate("3 agosto 2027", LocalDate.of(2027, 8, 3))
    }

    @Test
    fun `ведущий день недели уступает дате`() {
        assertDate("lunedì 18 febbraio", LocalDate.of(2027, 2, 18))
    }

    @Test
    fun `tra, fra и in — сдвиги`() {
        assertDate("tra 2 giorni", LocalDate.of(2026, 7, 23))
        assertDate("tra una settimana", LocalDate.of(2026, 7, 28))
        assertDate("in due settimane", LocalDate.of(2026, 8, 4))
        assertDate("tra un anno", LocalDate.of(2027, 7, 21))
    }

    @Test
    fun `диапазоны — dal-al и склейка`() {
        val e = parser.parse("ferie dal 23 al 28 agosto", now)
        assertEquals(LocalDate.of(2026, 8, 23), e.start.toLocalDate())
        assertEquals(Duration.ofDays(6), e.duration)
        assertDate("23-28 agosto", LocalDate.of(2026, 8, 23))
    }

    @Test
    fun `stasera, domattina и половины суток при дне`() {
        assertTime("cena stasera alle 8", 20)
        assertTime("corsa domattina alle 9", 9)
        assertTime("cena domani sera alle 8", 20)
        assertTrue(parser.parse("compiti stasera", now).allDay)
    }

    // --- время ---

    @Test
    fun `alle — пара, постфиксы e N, e mezza, e un quarto, meno`() {
        assertTime("alle 3 20", 15, 20)
        assertTime("alle 3 e 20", 15, 20)
        assertTime("alle tre e mezza", 15, 30)
        assertTime("alle tre e un quarto", 15, 15)
        assertTime("le tre meno un quarto", 14, 45)
        assertTime("alle tre", 15)
        assertTime("all'una", 13)
    }

    @Test
    fun `уточнения половины суток`() {
        assertTime("alle 8 di sera", 20)
        assertTime("8 della sera", 20)
        assertTime("alle 9 del mattino", 9)
        assertTime("alle 3 del pomeriggio", 15)
    }

    @Test
    fun `mezzogiorno e mezzanotte`() {
        assertTime("pranzo a mezzogiorno", 12)
        assertEquals(0, parser.parse("a mezzanotte", now).start.hour)
    }

    @Test
    fun `интервал dalle-alle`() {
        val e = parser.parse("dalle 15 alle 17", now)
        assertEquals(15, e.start.hour)
        assertEquals(Duration.ofHours(2), e.duration)
        val e2 = parser.parse("giovedì dalle 9:30 alle 11:00", now)
        assertEquals(Duration.ofMinutes(90), e2.duration)
        assertEquals(LocalDate.of(2026, 7, 23), e2.start.toLocalDate())
    }

    @Test
    fun `голое чч-мм с уточнением половины суток`() {
        assertTime("3:15 del pomeriggio", 15, 15)
    }

    @Test
    fun `апостроф вырезанного матча не остаётся заголовком`() {
        assertEquals("Evento", parser.parse("tra un'ora", now).title)
    }

    @Test
    fun `tra — офсет времени от сейчас`() {
        assertTime("chiamata tra 2 ore", 16)
        assertTime("tra un'ora", 15)
        assertTime("tra 30 minuti", 14, 30)
        assertTime("tra mezz'ora", 14, 30)
    }

    // --- длительности ---

    @Test
    fun `per — единицы, идиомы, десятичные, склейки`() {
        assertEquals(120, parser.parse("call alle 15 per 2 ore", now).duration!!.toMinutes())
        assertEquals(30, parser.parse("alle 15 per mezz'ora", now).duration!!.toMinutes())
        assertEquals(60, parser.parse("alle 15 per un'ora", now).duration!!.toMinutes())
        assertEquals(90, parser.parse("cinema alle 21 per un'ora e mezza", now).duration!!.toMinutes())
        assertEquals(90, parser.parse("alle 15 per 1,5 ore", now).duration!!.toMinutes())
        assertEquals(150, parser.parse("alle 15 per 2 ore e 30", now).duration!!.toMinutes())
        assertEquals(165, parser.parse("riunione alle 11 2h45m", now).duration!!.toMinutes())
    }

    // --- повторы ---

    @Test
    fun `ogni — частоты, дни, интервал, будни, выходные`() {
        assertEquals("FREQ=DAILY", parser.parse("pillole ogni giorno", now).rrule)
        assertEquals("FREQ=WEEKLY;BYDAY=TU,TH", parser.parse("palestra ogni martedì e giovedì", now).rrule)
        assertEquals("FREQ=WEEKLY;INTERVAL=2", parser.parse("ogni 2 settimane", now).rrule)
        assertEquals("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", parser.parse("standup nei giorni feriali", now).rrule)
        assertEquals("FREQ=WEEKLY;BYDAY=SA,SU", parser.parse("brunch ogni weekend", now).rrule)
        assertEquals("FREQ=WEEKLY;BYDAY=SA,SU", parser.parse("gita ogni fine settimana", now).rrule)
    }

    @Test
    fun `una volta a — частота без ogni`() {
        assertEquals("FREQ=WEEKLY", parser.parse("pulizie una volta alla settimana", now).rrule)
        assertEquals("FREQ=MONTHLY", parser.parse("report una volta al mese", now).rrule)
    }

    @Test
    fun `число месяца — ogni 15 del mese и il 15 di ogni mese`() {
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=15", parser.parse("stipendio ogni 15 del mese", now).rrule)
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=15", parser.parse("il 15 di ogni mese", now).rrule)
    }

    @Test
    fun `порядковый день недели месяца`() {
        assertEquals("FREQ=MONTHLY;BYDAY=1MO", parser.parse("riunione il primo lunedì del mese", now).rrule)
        assertEquals("FREQ=MONTHLY;BYDAY=-1FR", parser.parse("report l'ultimo venerdì del mese", now).rrule)
        assertEquals("FREQ=MONTHLY;BYDAY=2SU", parser.parse("brunch ogni seconda domenica del mese", now).rrule)
    }

    @Test
    fun `ogni mattina — ежедневно с половиной суток`() {
        val e = parser.parse("caffè ogni mattina alle 8", now)
        assertEquals("FREQ=DAILY", e.rrule)
        assertEquals(8, e.start.hour)
    }

    @Test
    fun `конец повтора — fino, entro, volte`() {
        assertEquals(
            "FREQ=DAILY;UNTIL=20260831",
            parser.parse("ogni giorno fino a fine agosto", now).rrule,
        )
        assertEquals(
            "FREQ=DAILY;UNTIL=20260731",
            parser.parse("ogni giorno entro fine mese", now).rrule,
        )
        assertEquals(
            "FREQ=WEEKLY;BYDAY=TU;COUNT=10",
            parser.parse("yoga ogni martedì 10 volte", now).rrule,
        )
        assertEquals(
            "FREQ=DAILY;UNTIL=20260915",
            parser.parse("ogni giorno fino al 15 settembre", now).rrule,
        )
    }

    @Test
    fun `годовая дата`() {
        val e = parser.parse("assicurazione 3 agosto ogni anno", now)
        assertEquals("FREQ=YEARLY", e.rrule)
        assertEquals(LocalDate.of(2026, 8, 3), e.start.toLocalDate())
    }

    // --- хвост разговорных форм (A–D, F) ---

    @Test
    fun `prossima — женская форма следующей недели`() {
        assertDate("domenica prossima", LocalDate.of(2026, 8, 2))
        assertDate("prossima domenica", LocalDate.of(2026, 8, 2))
    }

    @Test
    fun `день недели плюс следующая неделя`() {
        assertDate("mercoledì della prossima settimana", LocalDate.of(2026, 7, 29))
        assertDate("martedì di questa settimana", LocalDate.of(2026, 7, 21))
    }

    @Test
    fun `questa sera, sta sera, stamattina`() {
        assertTime("cena questa sera alle 8", 20)
        assertTime("film sta sera alle 9", 21)
        val e = parser.parse("appunti stamattina", now)
        assertTrue(e.allDay)
        assertEquals(LocalDate.of(2026, 7, 21), e.start.toLocalDate())
        assertEquals("appunti", e.title)
    }

    @Test
    fun `quarto d'ora — офсет и длительность`() {
        assertTime("tra un quarto d'ora", 14, 15)
        assertTime("tra tre quarti d'ora", 14, 45)
        assertEquals(15, parser.parse("alle 15 per un quarto d'ora", now).duration!!.toMinutes())
    }

    @Test
    fun `ore 15 — административная форма`() {
        assertTime("riunione ore 15", 15)
        assertTime("alle ore 15:30", 15, 30)
    }

    @Test
    fun `ogni due settimane — числительное словом`() {
        assertEquals("FREQ=WEEKLY;INTERVAL=2", parser.parse("ogni due settimane", now).rrule)
    }

    @Test
    fun `голое 3 e 20 без маркера — не время`() {
        assertTrue(parser.parse("comprare 3 e 20", now).allDay)
    }

    // --- негативы вердиктов ---

    @Test
    fun `негативы — периоды, хабитуал, N volte, числовые даты, день словами`() {
        for (phrase in listOf("questa settimana", "la settimana prossima", "il mese prossimo")) {
            val e = parser.parse(phrase, now)
            assertTrue(phrase, e.allDay)
            assertEquals(phrase, LocalDate.of(2026, 7, 21), e.start.toLocalDate())
            assertNull(phrase, e.rrule)
        }
        assertNull(parser.parse("chiamare 2 volte a settimana", now).rrule)
        // хабитуал «il martedì» — дата (ближайший вторник), не повтор
        val hab = parser.parse("il martedì", now)
        assertNull(hab.rrule)
        assertEquals(LocalDate.of(2026, 7, 21), hab.start.toLocalDate())
        assertTrue(parser.parse("appunti 15/2", now).allDay)
        assertNull(parser.parse("il tredici marzo", now).rrule)
        assertEquals(LocalDate.of(2026, 7, 21), parser.parse("il tredici marzo", now).start.toLocalDate())
    }

    // --- смешение языков ---

    @Test
    fun `смешанные фразы — кандидаты разных языков сливаются`() {
        val e = parser.parse("standup domani at 5pm", now)
        assertEquals(LocalDate.of(2026, 7, 22), e.start.toLocalDate())
        assertEquals(17, e.start.hour)

        val r = parser.parse("спорт ogni martedì в 19", now)
        assertEquals("FREQ=WEEKLY;BYDAY=TU", r.rrule)
        assertEquals(19, r.start.hour)
    }
}
