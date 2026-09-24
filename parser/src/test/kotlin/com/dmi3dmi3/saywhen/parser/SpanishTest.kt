package com.dmi3dmi3.saywhen.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Испанский транслятор (задача 27) — через боевой MultilingualEventParser. */
class SpanishTest {

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
    fun `смок — cena mañana a las 9`() {
        val e = parser.parse("cena mañana a las 9", now)
        assertEquals("cena", e.title)
        assertEquals(ZonedDateTime.of(2026, 7, 22, 9, 0, 0, 0, now.zone), e.start)
    }

    // --- даты ---

    @Test
    fun `дни недели, сокращения, próximo и que viene`() {
        assertDate("viernes", LocalDate.of(2026, 7, 24))
        assertDate("miercoles", LocalDate.of(2026, 7, 22))       // без акцента
        assertDate("gimnasio vie", LocalDate.of(2026, 7, 24))
        assertDate("el próximo viernes", LocalDate.of(2026, 7, 31))
        assertDate("el martes que viene", LocalDate.of(2026, 7, 28))
        assertDate("este lunes", LocalDate.of(2026, 7, 27))
    }

    @Test
    fun `день недели плюс следующая неделя`() {
        assertDate("miercoles de la próxima semana", LocalDate.of(2026, 7, 29))
        assertDate("el miércoles de la semana que viene", LocalDate.of(2026, 7, 29))
        assertDate("el lunes de esta semana", LocalDate.of(2026, 7, 27))
    }

    @Test
    fun `календарная дата — el, de, месяц впереди, primero, явный год`() {
        assertDate("el 3 de agosto", LocalDate.of(2026, 8, 3))
        assertDate("3 de agosto", LocalDate.of(2026, 8, 3))
        assertDate("marzo 3", LocalDate.of(2027, 3, 3))
        assertDate("el primero de marzo", LocalDate.of(2027, 3, 1))
        assertDate("3 de agosto de 2027", LocalDate.of(2027, 8, 3))
    }

    @Test
    fun `ведущий день недели уступает дате`() {
        assertDate("lunes, 18 de febrero", LocalDate.of(2027, 2, 18))
    }

    @Test
    fun `en и dentro de — сдвиги`() {
        assertDate("en 2 días", LocalDate.of(2026, 7, 23))
        assertDate("en una semana", LocalDate.of(2026, 7, 28))
        assertDate("en un año", LocalDate.of(2027, 7, 21))
        assertDate("dentro de dos semanas", LocalDate.of(2026, 8, 4))
    }

    @Test
    fun `диапазоны — del-al и открытая форма`() {
        val e = parser.parse("vacaciones del 23 al 28 de agosto", now)
        assertEquals(LocalDate.of(2026, 8, 23), e.start.toLocalDate())
        assertEquals(Duration.ofDays(6), e.duration)
        assertDate("campamento 13 a 15 de agosto", LocalDate.of(2026, 8, 13))
    }

    @Test
    fun `esta noche и mañana por la mañana`() {
        assertTime("cena esta noche a las 8", 20)
        assertTime("correr mañana por la mañana a las 9", 9)
        assertEquals(
            LocalDate.of(2026, 7, 22),
            parser.parse("correr mañana por la mañana a las 9", now).start.toLocalDate(),
        )
        assertTrue(parser.parse("deberes esta noche", now).allDay)
    }

    // --- время ---

    @Test
    fun `a las — пара, постфиксы y N, y media, y cuarto, menos`() {
        assertTime("a las 3 20", 15, 20)
        assertTime("a las 3 y 20", 15, 20)
        assertTime("a las tres y media", 15, 30)
        assertTime("a las 3 y cuarto", 15, 15)
        assertTime("las tres menos cuarto", 14, 45)
        assertTime("las doce menos cinco", 11, 55)
        assertTime("a las tres", 15)
        assertTime("a la una", 13)
    }

    @Test
    fun `уточнения половины суток и pm`() {
        assertTime("a las 9 de la noche", 21)
        assertTime("6 de la mañana", 6)
        assertTime("nueve de la noche", 21)
        assertTime("reunión a las 3 pm", 15)
        assertTime("a las 3 de la tarde", 15)
    }

    @Test
    fun `mediodía, medianoche и a las 15 horas`() {
        assertTime("almuerzo al mediodía", 12)
        assertEquals(0, parser.parse("a medianoche", now).start.hour)
        assertTime("reunión a las 15 horas", 15)
    }

    @Test
    fun `интервал de las - a las`() {
        val e = parser.parse("de las 15 a las 17", now)
        assertEquals(15, e.start.hour)
        assertEquals(Duration.ofHours(2), e.duration)
    }

    @Test
    fun `en и dentro de — офсет времени`() {
        assertTime("llamada en 2 horas", 16)
        assertTime("en media hora", 14, 30)
        assertTime("en un cuarto de hora", 14, 15)
        assertTime("dentro de tres horas", 17)
        assertTime("en 30 minutos", 14, 30)
    }

    // --- длительности ---

    @Test
    fun `por y durante — единицы, идиомы, десятичные, склейки`() {
        assertEquals(120, parser.parse("llamada a las 15 por 2 horas", now).duration!!.toMinutes())
        assertEquals(60, parser.parse("a las 15 durante una hora", now).duration!!.toMinutes())
        assertEquals(30, parser.parse("a las 15 por media hora", now).duration!!.toMinutes())
        assertEquals(90, parser.parse("cine a las 21 por hora y media", now).duration!!.toMinutes())
        assertEquals(15, parser.parse("a las 15 por un cuarto de hora", now).duration!!.toMinutes())
        assertEquals(90, parser.parse("a las 15 por 1,5 horas", now).duration!!.toMinutes())
        assertEquals(150, parser.parse("a las 15 por 2 horas y 30", now).duration!!.toMinutes())
        assertEquals(165, parser.parse("reunión a las 11 2h45m", now).duration!!.toMinutes())
    }

    // --- повторы ---

    @Test
    fun `cada — частоты, дни, интервал, будни, выходные`() {
        assertEquals("FREQ=DAILY", parser.parse("pastillas cada día", now).rrule)
        assertEquals("FREQ=WEEKLY;BYDAY=TU,TH", parser.parse("gimnasio cada martes y jueves", now).rrule)
        assertEquals("FREQ=WEEKLY;INTERVAL=2", parser.parse("cada 2 semanas", now).rrule)
        assertEquals("FREQ=WEEKLY;INTERVAL=2", parser.parse("cada dos semanas", now).rrule)
        assertEquals("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", parser.parse("standup entre semana", now).rrule)
        assertEquals("FREQ=WEEKLY;BYDAY=SA,SU", parser.parse("brunch cada fin de semana", now).rrule)
    }

    @Test
    fun `todos los — повторы с явным маркером`() {
        assertEquals("FREQ=DAILY", parser.parse("pastillas todos los días", now).rrule)
        assertEquals("FREQ=WEEKLY", parser.parse("todas las semanas", now).rrule)
        assertEquals("FREQ=WEEKLY;BYDAY=TU", parser.parse("yoga todos los martes", now).rrule)
        assertEquals("FREQ=WEEKLY;BYDAY=SA", parser.parse("mercado todos los sábados", now).rrule)
        assertEquals("FREQ=WEEKLY;BYDAY=SA,SU", parser.parse("paseo los fines de semana", now).rrule)
    }

    @Test
    fun `una vez a — частота`() {
        assertEquals("FREQ=WEEKLY", parser.parse("limpieza una vez a la semana", now).rrule)
        assertEquals("FREQ=MONTHLY", parser.parse("informe una vez al mes", now).rrule)
    }

    @Test
    fun `число месяца и порядковый день недели`() {
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=15", parser.parse("nómina cada 15 del mes", now).rrule)
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=15", parser.parse("el 15 de cada mes", now).rrule)
        assertEquals("FREQ=MONTHLY;BYDAY=1MO", parser.parse("reunión el primer lunes del mes", now).rrule)
        assertEquals("FREQ=MONTHLY;BYDAY=-1FR", parser.parse("informe el último viernes del mes", now).rrule)
        assertEquals("FREQ=MONTHLY;BYDAY=2SU", parser.parse("brunch cada segundo domingo del mes", now).rrule)
    }

    @Test
    fun `cada mañana — ежедневно с половиной суток`() {
        val e = parser.parse("café cada mañana a las 8", now)
        assertEquals("FREQ=DAILY", e.rrule)
        assertEquals(8, e.start.hour)
    }

    @Test
    fun `конец повтора — hasta и veces`() {
        assertEquals("FREQ=DAILY;UNTIL=20260731", parser.parse("cada día hasta fin de mes", now).rrule)
        assertEquals(
            "FREQ=DAILY;UNTIL=20260915",
            parser.parse("cada día hasta el 15 de septiembre", now).rrule,
        )
        assertEquals("FREQ=DAILY;UNTIL=20260831", parser.parse("cada día hasta septiembre", now).rrule)
        assertEquals("FREQ=WEEKLY;BYDAY=TU;COUNT=10", parser.parse("yoga cada martes 10 veces", now).rrule)
    }

    @Test
    fun `годовая дата`() {
        val e = parser.parse("seguro 3 de agosto cada año", now)
        assertEquals("FREQ=YEARLY", e.rrule)
        assertEquals(LocalDate.of(2026, 8, 3), e.start.toLocalDate())
    }

    // --- хвост разговорных форм (ревизия, вердикты владельца) ---

    @Test
    fun `los sábados — хабитуал с артиклем множественного`() {
        assertEquals("FREQ=WEEKLY;BYDAY=SA", parser.parse("mercado los sábados", now).rrule)
        assertEquals("FREQ=WEEKLY;BYDAY=MO", parser.parse("clase los lunes", now).rrule)
        // единственное с артиклем — дата, не повтор
        assertNull(parser.parse("el sábado", now).rrule)
    }

    @Test
    fun `finde — разговорные выходные`() {
        assertEquals("FREQ=WEEKLY;BYDAY=SA,SU", parser.parse("paseo cada finde", now).rrule)
        assertEquals("FREQ=WEEKLY;BYDAY=SA,SU", parser.parse("fútbol los findes", now).rrule)
    }

    @Test
    fun `cada quince días — идиома двух недель`() {
        // идиома «раз в две недели»: день недели держится, не дрейфует (фикс ревью)
        assertEquals("FREQ=WEEKLY;INTERVAL=2", parser.parse("cada quince días", now).rrule)
    }

    @Test
    fun `mañana a 11 — голое a без артикля не время`() {
        assertTrue(parser.parse("mañana a 11", now).allDay)
    }

    // --- негативы вердиктов ---

    @Test
    fun `негативы — периоды, N veces, числовые даты, голое 15-00`() {
        for (phrase in listOf("esta semana", "la semana que viene", "el mes que viene")) {
            val e = parser.parse(phrase, now)
            assertTrue(phrase, e.allDay)
            assertEquals(phrase, LocalDate.of(2026, 7, 21), e.start.toLocalDate())
            assertNull(phrase, e.rrule)
        }
        assertNull(parser.parse("llamar 2 veces por semana", now).rrule)
        assertTrue(parser.parse("notas 15.00", now).allDay)
        assertTrue(parser.parse("apuntes 17/2", now).allDay)
    }

    // --- смешение языков ---

    @Test
    fun `смешанные фразы`() {
        val e = parser.parse("деплой el viernes at 5pm", now)
        assertEquals(LocalDate.of(2026, 7, 24), e.start.toLocalDate())
        assertEquals(17, e.start.hour)
    }
}
