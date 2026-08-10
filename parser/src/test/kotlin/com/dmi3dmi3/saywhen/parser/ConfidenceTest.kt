package com.dmi3dmi3.saywhen.parser

import com.dmi3dmi3.saywhen.parser.ru.DateRules
import com.dmi3dmi3.saywhen.parser.ru.RecurrenceRules
import com.dmi3dmi3.saywhen.parser.ru.TimeRules
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Скоры специфичности (задача 16): цифровые и словарные формы — EXPLICIT,
 * словесные часы — STRONG, голый час по смежности — WEAK. Скор — знание
 * правила-транслятора; мердж сравнивает числа, не зная форм.
 */
class ConfidenceTest {

    // вторник, 14:00
    private val now = ZonedDateTime.of(2026, 7, 21, 14, 0, 0, 0, ZoneId.of("Europe/Moscow"))
    private val today: LocalDate = now.toLocalDate()

    private fun time(text: String): TimeCandidate {
        val tokens = Tokenizer.tokenize(text)
        return TimeRules.find(tokens, BooleanArray(tokens.size))!!
    }

    @Test
    fun `цифровое время — EXPLICIT`() {
        assertEquals(Confidence.EXPLICIT, time("в 19").confidence)
        assertEquals(Confidence.EXPLICIT, time("9:30").confidence)
        assertEquals(Confidence.EXPLICIT, time("в 7 вечера").confidence)
    }

    @Test
    fun `время словами — STRONG`() {
        assertEquals(Confidence.STRONG, time("в три часа").confidence)
        assertEquals(Confidence.STRONG, time("пол седьмого").confidence)
        assertEquals(Confidence.STRONG, time("в полдень").confidence)
    }

    @Test
    fun `голый час по смежности — WEAK`() {
        val tokens = Tokenizer.tokenize("завтра 11 планёрка")
        val used = BooleanArray(tokens.size)
        used[0] = true  // «завтра» занята датой
        assertEquals(Confidence.WEAK, TimeRules.bareHourAfterClaim(tokens, used)!!.confidence)
    }

    @Test
    fun `даты и повторы — EXPLICIT`() {
        val tokens = Tokenizer.tokenize("завтра каждый вторник")
        assertEquals(
            Confidence.EXPLICIT,
            DateRules.findAll(tokens, now, BooleanArray(tokens.size)).first().confidence,
        )
        assertEquals(
            Confidence.EXPLICIT,
            RecurrenceRules.find(tokens, today, BooleanArray(tokens.size))!!.confidence,
        )
    }
}
