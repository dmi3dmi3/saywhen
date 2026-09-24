package com.dmi3dmi3.saywhen.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration

/**
 * Ядровая склейка «число+единица»: логика общая, единицы приходят из языка.
 * Языковое поведение целиком пинуют DurationRulesTest/EnglishTest — здесь
 * только конструкция регексов из переданных единиц.
 */
class GluedDurationsTest {

    private val glue = GluedDurations(
        hourUnits = setOf("ч"),
        minuteUnits = setOf("м", "мин"),
        minuteWords = setOf("минуту", "минуты", "минут", "м", "мин"),
    )

    @Test
    fun `склейки часы, минуты и комбо`() {
        assertEquals(Duration.ofHours(2) to true, glue.gluedToken("2ч"))
        assertEquals(Duration.ofMinutes(45) to false, glue.gluedToken("45мин"))
        assertEquals(Duration.ofHours(2).plusMinutes(45) to false, glue.gluedToken("2ч45м"))
    }

    @Test
    fun `чужая единица и мусор — не длительность`() {
        assertNull(glue.gluedToken("2x"))
        assertNull(glue.gluedToken("ч2"))
        assertNull(glue.gluedToken("2ч99м"))  // минуты комбо только 0..59
    }

    @Test
    fun `десятичные часы — только часы, дробных минут не бывает`() {
        assertEquals(Duration.ofMinutes(90) to false, glue.gluedToken("1,5ч"))
        assertEquals(Duration.ofMinutes(150) to false, glue.gluedToken("2,5ч"))
        assertNull(glue.gluedToken("45,5м"))
    }

    @Test
    fun `минутный хвост — склейкой и словом`() {
        val glued = Tokenizer.tokenize("2ч 30м")
        assertEquals(30L to 1, glue.minutesTail(glued, BooleanArray(glued.size), 1))

        val worded = Tokenizer.tokenize("2ч 30 минут")
        assertEquals(30L to 2, glue.minutesTail(worded, BooleanArray(worded.size), 1))
    }

    @Test
    fun `часовая склейка хвостом минут не считается`() {
        val tokens = Tokenizer.tokenize("2ч 3ч")
        assertNull(glue.minutesTail(tokens, BooleanArray(tokens.size), 1))
    }
}
