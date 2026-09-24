package com.dmi3dmi3.saywhen.parser.de

import com.dmi3dmi3.saywhen.parser.DurationCandidate
import com.dmi3dmi3.saywhen.parser.GluedDurations
import com.dmi3dmi3.saywhen.parser.Token
import com.dmi3dmi3.saywhen.parser.decimalNumber
import java.time.Duration
import kotlin.math.roundToLong

internal object DeDurationRules {

    private val markers = setOf("für", "fuer")
    private val hourWords = setOf("stunde", "stunden", "std", "h")
    private val minuteWords = setOf("minute", "minuten", "min", "m")

    // склейки "2h" / "45m" / «2std45min» — общая логика в ядре, единицы наши
    private val glue = GluedDurations(setOf("h", "std"), setOf("m", "min"), minuteWords)

    private val wordNumbers = mapOf(
        "zwei" to 2L, "drei" to 3L, "vier" to 4L, "fünf" to 5L, "fuenf" to 5L,
        "sechs" to 6L, "sieben" to 7L, "acht" to 8L, "neun" to 9L, "zehn" to 10L,
        "fünfzehn" to 15L, "fuenfzehn" to 15L, "zwanzig" to 20L, "dreißig" to 30L,
        "dreissig" to 30L, "vierzig" to 40L, "fünfzig" to 50L, "fuenfzig" to 50L,
    )

    fun find(tokens: List<Token>, used: BooleanArray): DurationCandidate? {
        for (i in tokens.indices) {
            if (used[i] || tokens[i].lower !in markers) continue
            if (used.getOrNull(i + 1) == true) continue
            val next = tokens.getOrNull(i + 1)?.lower ?: continue

            // «für eine halbe stunde»
            if (next in setOf("eine", "einer") && tokens.getOrNull(i + 2)?.lower == "halbe" &&
                tokens.getOrNull(i + 3)?.lower == "stunde" &&
                (i + 2..i + 3).all { used.getOrNull(it) == false }
            ) {
                return DurationCandidate(Duration.ofMinutes(30), i..i + 3)
            }

            // «für anderthalb/eineinhalb stunden» — полтора часа
            if (next in setOf("anderthalb", "eineinhalb") && used.getOrNull(i + 2) == false &&
                tokens.getOrNull(i + 2)?.lower in hourWords
            ) {
                return DurationCandidate(Duration.ofMinutes(90), i..i + 2)
            }

            // «für eine stunde»
            if (next in setOf("eine", "einer") && used.getOrNull(i + 2) == false &&
                tokens.getOrNull(i + 2)?.lower in hourWords
            ) {
                return DurationCandidate(Duration.ofHours(1), i..i + 2)
            }

            // «für 2h» / «für 45m» / «für 2std45min»
            glue.gluedToken(next)?.let { (base, hours) ->
                var d = base
                var end = i + 1
                if (hours) glue.minutesTail(tokens, used, end + 1)?.let { (mins, last) ->
                    d = d.plusMinutes(mins); end = last
                }
                return DurationCandidate(d, i..end)
            }

            // «für 1,5 stunden» — десятичные только у часов
            decimalNumber(next)?.let { v ->
                if (used.getOrNull(i + 2) == false && tokens.getOrNull(i + 2)?.lower in hourWords) {
                    return DurationCandidate(Duration.ofMinutes((v * 60).roundToLong()), i..i + 2)
                }
            }

            // «für N stunden/minuten», число цифрами или словом
            val amount = next.toLongOrNull() ?: wordNumbers[next] ?: continue
            var j = i + 2
            if (amount !in 1..999) continue
            if (used.getOrNull(j) == true) continue
            var duration = when (tokens.getOrNull(j)?.lower) {
                in hourWords -> Duration.ofHours(amount)
                in minuteWords -> Duration.ofMinutes(amount)
                else -> continue
            }
            // хвост минут: «für 2 stunden [und] 30 [minuten]»
            if (tokens[j].lower in hourWords) {
                var k = j + 1
                var viaUnd = false
                if (tokens.getOrNull(k)?.lower == "und" && used.getOrNull(k) == false) {
                    k++; viaUnd = true
                }
                val tail = glue.minutesTail(tokens, used, k)
                if (tail != null) {
                    duration = duration.plusMinutes(tail.first); j = tail.second
                } else if (viaUnd && used.getOrNull(k) == false) {
                    tokens.getOrNull(k)?.lower?.toLongOrNull()?.takeIf { it in 1..59 }?.let {
                        duration = duration.plusMinutes(it); j = k
                    }
                }
            }
            return DurationCandidate(duration, i..j)
        }

        // голая склейка без маркера — «meeting um 11 2std45min»
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
