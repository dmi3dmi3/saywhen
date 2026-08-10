package com.dmi3dmi3.saywhen.parser

import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Ядро контракта из спеки: парсер не может упасть по построению —
 * любой текст даёт валидный ParsedEvent. Android-слой доверяет этому слепо.
 */
class NeverThrowTest {

    private val parser = RussianEventParser()
    private val now = ZonedDateTime.of(2026, 7, 21, 14, 0, 0, 0, ZoneId.of("Europe/Moscow"))

    @Test
    fun `парсер не падает ни на каком входе`() {
        val inputs = listOf(
            "",
            "   ",
            "🎉🎉🎉",
            "через 99999999999999999999 дней",
            "каждые 99999999999 недель",
            "каждое 99999999999 число",
            "на 99999999999 минут в 15",
            "в 99:99",
            "в :::",
            "с до до с",
            "в в в в в",
            "через через через",
            "‮текст справа налево",
            "مرحبا 世界 завтра",
            "а".repeat(10_000),
            "1 2 3 4 5 6 7 8 9 0",
        )
        for (text in inputs) {
            assertNotNull(text.take(40), parser.parse(text, now))
        }
    }
}
