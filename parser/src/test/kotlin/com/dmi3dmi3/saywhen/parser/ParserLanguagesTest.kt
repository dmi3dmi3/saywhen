package com.dmi3dmi3.saywhen.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Month
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Публичный вход по кодам языков (задача 30): app-слой собирает парсер из
 * настроек — набор и порядок включённых языков. Порядок списка — приоритет
 * тай-брейка арбитража (пересмотр пина 16 «фиксированный порядок»: одна
 * фраза *должна* вести себя по-разному у de- и ru-пользователя — меньше
 * кросс-языковых ложных срабатываний).
 */
class ParserLanguagesTest {

    // вторник, 14:00
    private val now = ZonedDateTime.of(2026, 7, 21, 14, 0, 0, 0, ZoneId.of("Europe/Moscow"))

    @Test
    fun `канонический список кодов`() {
        assertEquals(listOf("ru", "en", "it", "es", "de"), MultilingualEventParser.LANGUAGES)
    }

    @Test
    fun `отключённый язык не распознаётся — фраза целиком заголовок`() {
        val e = MultilingualEventParser(listOf("de")).parse("встреча завтра в 15", now)
        assertTrue(e.allDay)
        assertEquals("встреча завтра в 15", e.title)
    }

    @Test
    fun `неизвестные коды игнорируются`() {
        val e = MultilingualEventParser(listOf("en", "xx")).parse("meeting tomorrow at 3pm", now)
        assertEquals("meeting", e.title)
        assertEquals(15, e.start.hour)
    }

    @Test
    fun `пустой итоговый набор запрещён`() {
        assertThrows(IllegalArgumentException::class.java) {
            MultilingualEventParser(listOf("xx"))
        }
    }

    @Test
    fun `порядок списка — приоритет тай-брейка`() {
        // бэклог «Предел шкалы скоров»: у «суббота 15 May» RU- и EN-даты равны
        // по (скор, покрытие) — исход решает порядок
        val ruFirst = MultilingualEventParser(listOf("ru", "en")).parse("суббота 15 May", now)
        assertEquals("May", ruFirst.title)      // RU: дата «суббота», «15» — время
        assertEquals(15, ruFirst.start.hour)

        val enFirst = MultilingualEventParser(listOf("en", "ru")).parse("суббота 15 May", now)
        assertEquals("суббота", enFirst.title)  // EN: дата «15 May» целиком
        assertEquals(Month.MAY, enFirst.start.month)
        assertTrue(enFirst.allDay)
    }
}
