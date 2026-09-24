package com.dmi3dmi3.saywhen.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Напоминания (задача 29): компактная форма «!N» — универсальная, вербозная —
 * по языкам; вердикты и формы — секция «Напоминания» datetime-matrix.md.
 * Дефолт из настроек и гейт all-day — app-слой; ядро честно возвращает
 * распознанную форму.
 */
class ReminderTest {

    private val parser = MultilingualEventParser()

    // вторник, 14:00
    private val now = ZonedDateTime.of(2026, 7, 21, 14, 0, 0, 0, ZoneId.of("Europe/Moscow"))

    // --- компактная форма ---

    @Test
    fun `!10 — за 10 минут, выкусывается из заголовка`() {
        val e = parser.parse("звонок завтра в 15 !10", now)
        assertEquals(10, e.reminderMinutes)
        assertEquals("звонок", e.title)
        assertTrue(e.matches.any { it.field == TokenMatch.Field.REMINDER })
    }

    @Test
    fun `!0 — при событии`() {
        assertEquals(0, parser.parse("звонок завтра в 15 !0", now).reminderMinutes)
    }

    @Test
    fun `час-юниты — из словарей длительностей языков`() {
        assertEquals(60, parser.parse("звонок завтра в 15 !1ч", now).reminderMinutes)
        assertEquals(60, parser.parse("call tomorrow at 3pm !1h", now).reminderMinutes)
        assertEquals(120, parser.parse("zahnarzt morgen um 15 !2std", now).reminderMinutes)
    }

    @Test
    fun `неизвестный юнит — не напоминание, токен остаётся в заголовке`() {
        val e = parser.parse("звонок завтра в 15 !10x", now)
        assertNull(e.reminderMinutes)
        assertEquals("звонок !10x", e.title)
    }

    @Test
    fun `последняя форма побеждает, ранние остаются текстом`() {
        val e = parser.parse("!10 звонок завтра в 15 !20", now)
        assertEquals(20, e.reminderMinutes)
        assertEquals("!10 звонок", e.title)
    }

    @Test
    fun `бэнг не вплотную к числу — не напоминание`() {
        // хвостовой «!» — пользовательская пунктуация, в заголовке остаётся
        val e = parser.parse("позвонить маме в 10!", now)
        assertNull(e.reminderMinutes)
        assertEquals("позвонить маме!", e.title)
        assertEquals(10, e.start.hour)
    }

    @Test
    fun `all-day — ядро честно возвращает минуты, гейт в app-слое`() {
        val e = parser.parse("купить корм !10", now)
        assertTrue(e.allDay)
        assertEquals(10, e.reminderMinutes)
        assertEquals("купить корм", e.title)
    }

    // --- вербозные формы по языкам ---

    @Test
    fun `ru — напомни за N, юнит опционален`() {
        assertEquals(10, parser.parse("встреча завтра в 15 напомни за 10 минут", now).reminderMinutes)
        assertEquals(10, parser.parse("встреча завтра в 15 напомните за 10", now).reminderMinutes)
        assertEquals(60, parser.parse("встреча завтра в 15 напомни за час", now).reminderMinutes)
        assertEquals(120, parser.parse("встреча завтра в 15 напомни за 2 часа", now).reminderMinutes)
        assertEquals("встреча", parser.parse("встреча завтра в 15 напомни за 10 минут", now).title)
    }

    @Test
    fun `ru — короткие юниты как у длительностей`() {
        // фикс ревью волны 3: «за 2 ч» давал 2 минуты и мусорное «ч» в заголовке
        val hours = parser.parse("встреча завтра в 15 напомни за 2 ч", now)
        assertEquals(120, hours.reminderMinutes)
        assertEquals("встреча", hours.title)
        assertEquals(30, parser.parse("встреча завтра в 15 напомни за 30 м", now).reminderMinutes)
    }

    @Test
    fun `ru guard — напомни без числа-хвоста остаётся заголовком`() {
        val e = parser.parse("напомни маме про торт завтра в 12", now)
        assertNull(e.reminderMinutes)
        assertEquals("напомни маме про торт", e.title)
    }

    @Test
    fun `en — remind me N unit before`() {
        assertEquals(30, parser.parse("dentist tomorrow at 3pm remind me 30 minutes before", now).reminderMinutes)
        assertEquals(60, parser.parse("standup tomorrow at 9 remind an hour before", now).reminderMinutes)
        assertEquals("dentist", parser.parse("dentist tomorrow at 3pm remind me 30 minutes before", now).title)
    }

    @Test
    fun `en guard — remind без хвоста остаётся заголовком`() {
        val e = parser.parse("remind mom tomorrow at 5pm", now)
        assertNull(e.reminderMinutes)
        assertEquals("remind mom", e.title)
    }

    @Test
    fun `it — ricordamelo N minuti prima`() {
        assertEquals(15, parser.parse("cena domani alle 20 ricordamelo 15 minuti prima", now).reminderMinutes)
        assertEquals(60, parser.parse("riunione domani alle 9 avvisami un'ora prima", now).reminderMinutes)
        assertEquals("cena", parser.parse("cena domani alle 20 ricordamelo 15 minuti prima", now).title)
    }

    @Test
    fun `es — recuérdame N minutos antes`() {
        assertEquals(10, parser.parse("cena mañana a las 21 recuérdame 10 minutos antes", now).reminderMinutes)
        assertEquals(60, parser.parse("reunión mañana a las 9 avísame una hora antes", now).reminderMinutes)
        assertEquals("cena", parser.parse("cena mañana a las 21 recuérdame 10 minutos antes", now).title)
    }

    @Test
    fun `de — erinnere mich N Minuten vorher`() {
        assertEquals(10, parser.parse("zahnarzt morgen um 15 erinnere mich 10 minuten vorher", now).reminderMinutes)
        assertEquals(60, parser.parse("besprechung morgen um 9 erinnere mich eine stunde davor", now).reminderMinutes)
        assertEquals("zahnarzt", parser.parse("zahnarzt morgen um 15 erinnere mich 10 minuten vorher", now).title)
    }

    @Test
    fun `it-es guard — глагол без числа-хвоста остаётся заголовком`() {
        val it = parser.parse("ricordami la spesa domani alle 15", now)
        assertNull(it.reminderMinutes)
        assertTrue(it.title.contains("ricordami la spesa"))
        val es = parser.parse("avísame mañana a las 15", now)
        assertNull(es.reminderMinutes)
        assertTrue(es.title.contains("avísame"))
    }

    @Test
    fun `de guard — erinnere без mich не срабатывает`() {
        val e = parser.parse("besprechung morgen um 9 erinnere 10 minuten vorher", now)
        assertNull(e.reminderMinutes)
        assertTrue(e.title.contains("erinnere"))
    }

    @Test
    fun `en guard — число с юнитом, но без before — не напоминание`() {
        val e = parser.parse("dentist tomorrow at 3pm remind me 30 minutes", now)
        assertNull(e.reminderMinutes)
        assertTrue(e.title.contains("remind me 30 minutes"))
    }

    @Test
    fun `напоминание живёт рядом с повтором`() {
        val e = parser.parse("йога каждый вторник в 19 напомни за 10 минут", now)
        assertEquals("FREQ=WEEKLY;BYDAY=TU", e.rrule)
        assertEquals(10, e.reminderMinutes)
        assertEquals("йога", e.title)
    }

    @Test
    fun `вербозная и компактная вместе — последняя побеждает`() {
        val e = parser.parse("встреча завтра в 15 !5 напомни за 20 минут", now)
        assertEquals(20, e.reminderMinutes)
    }
}
