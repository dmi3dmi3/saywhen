package com.dmi3dmi3.saywhen.parser.it

import com.dmi3dmi3.saywhen.parser.DurationCandidate
import com.dmi3dmi3.saywhen.parser.GluedDurations
import com.dmi3dmi3.saywhen.parser.Token
import com.dmi3dmi3.saywhen.parser.decimalNumber
import java.time.Duration
import kotlin.math.roundToLong

internal object ItDurationRules {

    private val hourWords = setOf("ora", "ore", "h")
    private val minuteWords = setOf("minuto", "minuti", "m", "min")

    // склейки "2h" / "45m" / "2h45m" — общая логика в ядре, латинские единицы
    private val glue = GluedDurations(setOf("h"), setOf("m", "min"), minuteWords)

    private val wordNumbers = mapOf(
        "due" to 2L, "tre" to 3L, "quattro" to 4L, "cinque" to 5L, "sei" to 6L,
        "sette" to 7L, "otto" to 8L, "nove" to 9L, "dieci" to 10L,
        "quindici" to 15L, "venti" to 20L, "trenta" to 30L, "quaranta" to 40L,
        "cinquanta" to 50L,
    )

    fun find(tokens: List<Token>, used: BooleanArray): DurationCandidate? {
        for (i in tokens.indices) {
            if (used[i] || tokens[i].lower != "per") continue
            if (used.getOrNull(i + 1) == true) continue
            val next = tokens.getOrNull(i + 1)?.lower ?: continue

            // «per mezz'ora» → per, mezz, ora (апостроф разрезан)
            if (next == "mezz" && used.getOrNull(i + 2) == false &&
                tokens.getOrNull(i + 2)?.lower == "ora"
            ) {
                return DurationCandidate(Duration.ofMinutes(30), i..i + 2)
            }

            // «per un quarto d'ora» → per, un, quarto, d, ora
            if (next == "un" && tokens.getOrNull(i + 2)?.lower == "quarto" &&
                tokens.getOrNull(i + 3)?.lower == "d" && tokens.getOrNull(i + 4)?.lower == "ora" &&
                (i + 2..i + 4).all { used.getOrNull(it) == false }
            ) {
                return DurationCandidate(Duration.ofMinutes(15), i..i + 4)
            }

            // «per un'ora [e mezza]» → per, un, ora [, e, mezza]
            if ((next == "un" || next == "una") && used.getOrNull(i + 2) == false &&
                tokens.getOrNull(i + 2)?.lower in hourWords
            ) {
                if (used.getOrNull(i + 3) == false && used.getOrNull(i + 4) == false &&
                    tokens.getOrNull(i + 3)?.lower == "e" &&
                    tokens.getOrNull(i + 4)?.lower in setOf("mezza", "mezzo")
                ) {
                    return DurationCandidate(Duration.ofMinutes(90), i..i + 4)
                }
                return DurationCandidate(Duration.ofHours(1), i..i + 2)
            }

            // «per 2h» / «per 45m» / «per 2h45m»
            glue.gluedToken(next)?.let { (base, hours) ->
                var d = base
                var end = i + 1
                if (hours) glue.minutesTail(tokens, used, end + 1)?.let { (mins, last) ->
                    d = d.plusMinutes(mins); end = last
                }
                return DurationCandidate(d, i..end)
            }

            // «per 1,5 ore» — десятичные только у часов
            decimalNumber(next)?.let { v ->
                if (used.getOrNull(i + 2) == false && tokens.getOrNull(i + 2)?.lower in hourWords) {
                    return DurationCandidate(Duration.ofMinutes((v * 60).roundToLong()), i..i + 2)
                }
            }

            // «per N ore/minuti», число цифрами или словом
            val amount = next.toLongOrNull() ?: wordNumbers[next] ?: continue
            var j = i + 2
            if (amount !in 1..999) continue
            if (used.getOrNull(j) == true) continue
            var duration = when (tokens.getOrNull(j)?.lower) {
                in hourWords -> Duration.ofHours(amount)
                in minuteWords -> Duration.ofMinutes(amount)
                else -> continue
            }
            // хвост минут: «per 2 ore e 30 [minuti]» — «e» сам маркер хвоста
            if (tokens[j].lower in hourWords) {
                var k = j + 1
                var viaE = false
                if (tokens.getOrNull(k)?.lower == "e" && used.getOrNull(k) == false) {
                    k++; viaE = true
                }
                val tail = glue.minutesTail(tokens, used, k)
                if (tail != null) {
                    duration = duration.plusMinutes(tail.first); j = tail.second
                } else if (viaE && used.getOrNull(k) == false) {
                    tokens.getOrNull(k)?.lower?.toLongOrNull()?.takeIf { it in 1..59 }?.let {
                        duration = duration.plusMinutes(it); j = k
                    }
                }
            }
            return DurationCandidate(duration, i..j)
        }

        // голая склейка без «per» — «riunione alle 11 2h45m»
        for (i in tokens.indices) {
            if (used[i]) continue
            glue.gluedToken(tokens[i].lower)?.let { (base, hours) ->
                var d = base
                var end = i
                if (hours) glue.minutesTail(tokens, used, i + 1)?.let { (mins, last) ->
                    d = d.plusMinutes(mins); end = last
                }
                return DurationCandidate(d, i..end)
            }
        }
        return null
    }
}
