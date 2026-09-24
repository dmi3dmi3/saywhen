package com.dmi3dmi3.saywhen.parser

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Окно активности как параметр (задача 30a): правило выбора пары {h, h+12}
 * не меняется, меняется окно — из приватной константы в параметр парсера.
 * Правило одной функцией [resolveTwelveHour] — ею же пользуются живые
 * примеры в настройках.
 */
class ActivityWindowTest {

    // вторник, 14:00
    private val now = ZonedDateTime.of(2026, 7, 21, 14, 0, 0, 0, ZoneId.of("Europe/Moscow"))

    private fun hourOf(text: String, window: IntRange): Int =
        MultilingualEventParser(listOf("ru"), window).parse(text, now).start.hour

    @Test
    fun `дефолтное окно — сегодняшнее поведение`() {
        assertEquals(8..21, MultilingualEventParser.DEFAULT_ACTIVITY_WINDOW)
        val default = MultilingualEventParser().parse("встреча в 9", now)
        assertEquals(9, default.start.hour)
        assertEquals(19, MultilingualEventParser().parse("встреча в 7", now).start.hour)
    }

    @Test
    fun `кастомное окно меняет разрешение пары`() {
        assertEquals(20, hourOf("завтрак в 8", 9..20))   // 8 вне, 20 в окне
        assertEquals(7, hourOf("пробежка в 7", 7..23))   // 7 теперь в окне
    }

    @Test
    fun `оба кандидата в окне или ни одного — буквальный час`() {
        assertEquals(5, hourOf("в 5", 0..23))   // оба в окне
        assertEquals(5, hourOf("в 5", 22..23))  // ни одного
    }

    @Test
    fun `явная половина суток бьёт окно`() {
        // «в 8 утра» при окне 9..20 — уважаем сказанное, не окно
        assertEquals(8, hourOf("завтрак в 8 утра", 9..20))
        assertEquals(20, hourOf("встреча в 8 вечера", 9..20))
    }

    @Test
    fun `resolveTwelveHour — правило напрямую, для примеров UI`() {
        assertEquals(9, resolveTwelveHour(9, 8..21))
        assertEquals(19, resolveTwelveHour(7, 8..21))
        assertEquals(20, resolveTwelveHour(8, 9..20))
        // «в 12»: пара {12:00, полночь} — полночь побеждает только ночным окном
        assertEquals(12, resolveTwelveHour(12, 8..21))
        assertEquals(0, resolveTwelveHour(12, 0..7))
    }
}
