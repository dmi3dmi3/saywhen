package com.dmi3dmi3.saywhen.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Стоп-лист (задача 12): заблокированные диапазоны не распознаются, но остаются в заголовке. */
class BlockedRangesTest {

    private val parser = RussianEventParser()

    // вторник, 14:00
    private val now = ZonedDateTime.of(2026, 7, 21, 14, 0, 0, 0, ZoneId.of("Europe/Moscow"))

    // диапазоны в тестах не считаем руками (см. задачу 12)
    private fun String.spanOf(word: String): IntRange {
        val i = indexOf(word)
        require(i >= 0) { "«$word» нет в «$this»" }
        return i until i + word.length
    }

    private fun parse(text: String, vararg blockedWords: String): ParsedEvent =
        parser.parse(text, now, blockedWords.map { text.spanOf(it) })

    @Test
    fun `заблокированная дата не распознаётся и остаётся в заголовке`() {
        val e = parse("ужин завтра в 15", "завтра")
        assertEquals("ужин завтра", e.title)
        assertEquals(now.toLocalDate(), e.start.toLocalDate())  // даты нет → сегодня
        assertEquals(15, e.start.hour)
        assertTrue(e.matches.none { it.field == TokenMatch.Field.DATE })
    }

    @Test
    fun `заблокированное время — событие all-day`() {
        val e = parse("ужин завтра в 15", "в 15")
        assertTrue(e.allDay)
        assertEquals(LocalDate.of(2026, 7, 22), e.start.toLocalDate())
        assertEquals("ужин в 15", e.title)
    }

    @Test
    fun `предлог не поглощается заблокированным соседом`() {
        // блок частичный — только «15»; «в» свободен, но липнуть к блоку не должен
        val e = parse("ужин завтра в 15", "15")
        assertTrue(e.allDay)
        assertEquals("ужин в 15", e.title)
    }

    @Test
    fun `заблокированный повтор — обычное событие`() {
        val e = parse("йога каждый вторник в 19", "каждый вторник")
        assertNull(e.rrule)
        assertEquals(now.toLocalDate(), e.start.toLocalDate())
        assertEquals(19, e.start.hour)
        assertEquals("йога каждый вторник", e.title)
    }

    @Test
    fun `фокус переезжает на следующего кандидата`() {
        // без блока дату забирает «Сегодня» (первый кандидат); с блоком — «на вторник»
        val e = parse("Сегодня делаем ужин на вторник", "Сегодня")
        assertTrue(e.allDay)
        assertEquals(LocalDate.of(2026, 7, 21), e.start.toLocalDate())  // nextOrSame: сегодня вторник
        assertEquals("Сегодня делаем ужин", e.title)
        val date = e.matches.single { it.field == TokenMatch.Field.DATE }
        assertEquals("Сегодня делаем ужин на вторник".spanOf("на вторник"), date.range)
    }

    @Test
    fun `голый час после заблокированного токена остаётся временем`() {
        // осознанно (ревью 12–15): исключение даты не тащит за собой время —
        // «ужин завтра 19» с исключённым «завтра» остаётся ужином в 19:00
        val e = parse("ужин завтра 19", "завтра")
        assertEquals(19, e.start.hour)
        assertEquals(now.toLocalDate(), e.start.toLocalDate())
        assertEquals("ужин завтра", e.title)
    }

    @Test
    fun `блок нераспознаваемого слова безвреден`() {
        val e = parse("купить корм", "корм")
        assertEquals("купить корм", e.title)
        assertTrue(e.allDay)
        assertTrue(e.matches.isEmpty())
    }

    @Test
    fun `вызов без параметра работает как раньше`() {
        val e = parser.parse("ужин завтра в 15", now)
        assertEquals("ужин", e.title)
        assertEquals(LocalDate.of(2026, 7, 22), e.start.toLocalDate())
    }
}
