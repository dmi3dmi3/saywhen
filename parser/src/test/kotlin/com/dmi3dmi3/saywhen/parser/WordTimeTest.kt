package com.dmi3dmi3.saywhen.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 12-часовой круг и время словами (задача 15, шаг 3; правило окна — июль 2026).
 * Час 1..12 в любой форме — пара {h, h+12}: побеждает кандидат в дневном
 * окне 8–21, оба/ни один в окне — буквальный (ранний); от момента набора
 * выбор не зависит, прошедшее без даты уезжает на завтра общим сдвигом.
 * Уточнение утра/дня/вечера/ночи снимает пару.
 */
class WordTimeTest {

    private val parser = RussianEventParser()

    // вторник, 14:00
    private val now = ZonedDateTime.of(2026, 7, 21, 14, 0, 0, 0, ZoneId.of("Europe/Moscow"))

    private fun at(day: Int, hour: Int, minute: Int = 0): ZonedDateTime =
        ZonedDateTime.of(2026, 7, day, hour, minute, 0, 0, now.zone)

    private fun assertStart(text: String, expected: ZonedDateTime) {
        val e = parser.parse(text, now)
        assertEquals(text, expected, e.start)
    }

    @Test
    fun `полдень и полночь — фиксированные`() {
        assertStart("обед в полдень", at(22, 12))   // 12:00 прошло → завтра
        assertStart("в полночь", at(22, 0))
        assertEquals("обед", parser.parse("обед в полдень", now).title)
    }

    @Test
    fun `слова и цифры — один круг, окно решает`() {
        assertStart("встреча в три", at(21, 15))
        assertStart("встреча в 3", at(21, 15))
        assertStart("в 9:30", at(22, 9, 30))  // оба в окне — буквально; прошло → завтра
        assertStart("в десять", at(22, 10))   // 22:00 вне окна
        assertStart("в 9", at(22, 9))
    }

    @Test
    fun `час вне окна — плюс двенадцать`() {
        assertStart("в час", at(22, 13))  // 1:00 вне окна, 13:00 в окне; прошло → завтра
    }

    @Test
    fun `двенадцать — всегда полдень, полночь только словами`() {
        assertStart("в 12", at(22, 12))
    }

    @Test
    fun `явное сегодня — окно решает, прошлое уважаем`() {
        assertStart("сегодня в 10", at(21, 10))
        assertStart("сегодня в 3", at(21, 15))
        assertStart("сегодня в час", at(21, 13))
    }

    @Test
    fun `день впереди — дневное окно, оба в окне — ранний`() {
        assertStart("завтра в 3", at(22, 15))
        assertStart("завтра в 9", at(22, 9))
    }

    @Test
    fun `повтор выбирает по окну, прошедший якорь уезжает вперёд`() {
        val e = parser.parse("каждый вторник в 9", now)
        assertEquals(at(28, 9), e.start)  // 9:00 (не 21:00), старт след. вторник
        assertEquals("FREQ=WEEKLY;BYDAY=TU", e.rrule)
    }

    @Test
    fun `уточнение снимает пару`() {
        assertStart("в десять утра", at(22, 10))       // явное, прошло → завтра
        assertStart("в четыре часа дня", at(21, 16))
        assertStart("в 7 вечера", at(21, 19))          // daypart с цифрой
        assertStart("в два ночи", at(22, 2))
    }

    @Test
    fun `пол седьмого во всех написаниях`() {
        for (text in listOf("пол седьмого", "полседьмого", "пол-седьмого", "в пол седьмого")) {
            assertStart(text, at(21, 18, 30))
        }
    }

    @Test
    fun `пол седьмого — выкусывание и подсветка`() {
        val e = parser.parse("треня пол седьмого", now)
        assertEquals("треня", e.title)
        assertEquals(TokenMatch.Field.TIME, e.matches.single().field)
    }

    @Test
    fun `вне круга — как раньше`() {
        assertStart("в 15", at(21, 15))
        assertStart("в 19:30", at(21, 19, 30))
    }

    @Test
    fun `интервалы вне круга — 24-часовые`() {
        val e = parser.parse("с 3 до 5", now)
        assertEquals(at(22, 3), e.start)  // прошло → завтра, круг не применяется
        assertEquals(Duration.ofHours(2), e.duration)
    }

    @Test
    fun `слово-число без предлога — не время`() {
        val e = parser.parse("купить три книги", now)
        assertTrue(e.allDay)
        assertEquals("купить три книги", e.title)
        assertEquals(LocalDate.of(2026, 7, 21), e.start.toLocalDate())
    }
}
