package com.dmi3dmi3.saywhen.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Пины матрицы RRULE × языки (docs/rrule-matrix.md, задача 25): недостающие
 * позитивы части 2 и негативы вердиктов «не берём» части 1. Основную массу
 * форм части 2 пинуют профильные тесты — Recurrence*Test (ru), EnglishTest
 * (en); здесь только то, чего им не хватало до полного контракта матрицы.
 */
class RruleMatrixTest {

    private val parser = MultilingualEventParser()

    // вторник, 14:00
    private val now = ZonedDateTime.of(2026, 7, 21, 14, 0, 0, 0, ZoneId.of("Europe/Moscow"))

    // --- часть 2, недостающие позитивы ---

    @Test
    fun `наречия частоты en — daily, weekly, yearly`() {
        assertEquals("FREQ=DAILY", parser.parse("standup daily", now).rrule)
        assertEquals("FREQ=WEEKLY", parser.parse("report weekly", now).rrule)
        assertEquals("FREQ=YEARLY", parser.parse("checkup yearly", now).rrule)
    }

    @Test
    fun `годовая дата — дата плюс каждый год, BYMONTH не нужен`() {
        val ru = parser.parse("фильтр 3 августа каждый год", now)
        assertEquals("FREQ=YEARLY", ru.rrule)
        assertEquals(LocalDate.of(2026, 8, 3), ru.start.toLocalDate())

        val en = parser.parse("insurance june 3 every year", now)
        assertEquals("FREQ=YEARLY", en.rrule)
        assertEquals(LocalDate.of(2027, 6, 3), en.start.toLocalDate())  // 3.06 прошло
    }

    // --- часть 1, вердикты «не берём» с живыми фразами ---

    @Test
    fun `HOURLY и MINUTELY — не повтор, текст остаётся заголовком`() {
        assertNull(parser.parse("пить воду каждый час", now).rrule)
        assertNull(parser.parse("проверять каждую минуту", now).rrule)
        assertNull(parser.parse("standup every hour", now).rrule)
        assertNull(parser.parse("check every minute", now).rrule)
    }

    @Test
    fun `порядковый день недели года — BYDAY в YEARLY не собирается`() {
        val ru = parser.parse("парад первый понедельник июня каждый год", now)
        assertFalse(ru.rrule.orEmpty().contains("BYDAY"))

        val en = parser.parse("parade first monday of june every year", now)
        assertFalse(en.rrule.orEmpty().contains("BYDAY"))
    }

    @Test
    fun `последний день месяца — отрицательный BYMONTHDAY не берём`() {
        assertNull(parser.parse("отчёт в последний день месяца", now).rrule)
        assertNull(parser.parse("report on the last day of the month", now).rrule)
    }

    @Test
    fun `N раз в неделю и twice a week — дни неизвестны, повтором не берём`() {
        assertNull(parser.parse("звонить маме 2 раза в неделю", now).rrule)
        assertNull(parser.parse("пить таблетки 5 раз в день", now).rrule)
        assertNull(parser.parse("call mom twice a week", now).rrule)
    }

    @Test
    fun `кроме среды — исключения EXDATE не берём, повтор честно без них`() {
        val ru = parser.parse("зарядка каждый день кроме среды", now)
        assertEquals("FREQ=DAILY", ru.rrule)
        assertTrue(ru.title.contains("кроме среды"))

        // en: голое "wednesday" — датный кандидат, уходит в якорь старта;
        // важен сам вердикт — исключение правилом не становится
        val en = parser.parse("run every day except wednesday", now)
        assertEquals("FREQ=DAILY", en.rrule)
        assertTrue(en.title.contains("except"))
    }
}
