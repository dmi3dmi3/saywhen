package com.dmi3dmi3.saywhen.parser.en

import com.dmi3dmi3.saywhen.parser.DurationCandidate
import com.dmi3dmi3.saywhen.parser.GluedDurations
import com.dmi3dmi3.saywhen.parser.Token
import com.dmi3dmi3.saywhen.parser.decimalNumber
import java.time.Duration
import kotlin.math.roundToLong

internal object EnDurationRules {

    private val hourWords = setOf("hour", "hours", "hr", "hrs", "h")
    private val minuteWords = setOf("minute", "minutes", "min", "mins", "m")

    // склейки "2h" / "45m" / "2h45m" — общая логика в ядре, единицы наши
    private val glue = GluedDurations(setOf("h", "hr", "hrs"), setOf("m", "min", "mins"), minuteWords)

    // числа словами: единицы/десятки — "for three hours", "for forty [five] minutes"
    private val wordNumbers = mapOf(
        "one" to 1L, "two" to 2L, "three" to 3L, "four" to 4L, "five" to 5L,
        "six" to 6L, "seven" to 7L, "eight" to 8L, "nine" to 9L, "ten" to 10L,
        "eleven" to 11L, "twelve" to 12L, "fifteen" to 15L,
        "twenty" to 20L, "thirty" to 30L, "forty" to 40L, "fifty" to 50L,
    )
    private val tens = setOf(20L, 30L, 40L, 50L)

    fun find(tokens: List<Token>, used: BooleanArray): DurationCandidate? {
        for (i in tokens.indices) {
            if (used[i] || tokens[i].lower != "for") continue
            if (used.getOrNull(i + 1) == true) continue
            val next = tokens.getOrNull(i + 1)?.lower ?: continue

            // "for an hour" / "for an hour and a half"
            if ((next == "an" || next == "a") && used.getOrNull(i + 2) == false &&
                tokens.getOrNull(i + 2)?.lower in hourWords
            ) {
                if (used.getOrNull(i + 3) == false && used.getOrNull(i + 4) == false &&
                    used.getOrNull(i + 5) == false &&
                    tokens.getOrNull(i + 3)?.lower == "and" &&
                    tokens.getOrNull(i + 4)?.lower in setOf("an", "a") &&
                    tokens.getOrNull(i + 5)?.lower == "half"
                ) {
                    return DurationCandidate(Duration.ofMinutes(90), i..i + 5)
                }
                return DurationCandidate(Duration.ofHours(1), i..i + 2)
            }

            // "for half an hour"
            if (next == "half" && used.getOrNull(i + 2) == false && used.getOrNull(i + 3) == false &&
                tokens.getOrNull(i + 2)?.lower in setOf("an", "a") &&
                tokens.getOrNull(i + 3)?.lower in hourWords
            ) {
                return DurationCandidate(Duration.ofMinutes(30), i..i + 3)
            }

            // "for 2h" / "for 45m" / "for 2h45m" — единица приклеена к числу
            glue.gluedToken(next)?.let { (base, hours) ->
                var d = base
                var end = i + 1
                if (hours) glue.minutesTail(tokens, used, end + 1)?.let { (mins, last) ->
                    d = d.plusMinutes(mins); end = last
                }
                return DurationCandidate(d, i..end)
            }

            // "for 1.5 hours" — десятичные только у часов
            decimalNumber(next)?.let { v ->
                if (used.getOrNull(i + 2) == false && tokens.getOrNull(i + 2)?.lower in hourWords) {
                    return DurationCandidate(Duration.ofMinutes((v * 60).roundToLong()), i..i + 2)
                }
            }

            // "for N hours/minutes", число цифрами или словом; границы — как у дат
            var amount = next.toLongOrNull() ?: wordNumbers[next] ?: continue
            var j = i + 2
            // составное "forty five"
            if (amount in tens && used.getOrNull(j) == false) {
                wordNumbers[tokens.getOrNull(j)?.lower]?.takeIf { it < 10 }
                    ?.let { amount += it; j++ }
            }
            if (amount !in 1..999) continue
            if (used.getOrNull(j) == true) continue
            var duration = when (tokens.getOrNull(j)?.lower) {
                in hourWords -> Duration.ofHours(amount)
                in minuteWords -> Duration.ofMinutes(amount)
                else -> continue
            }
            // хвост минут после часов: "for 2 hours 30 minutes"
            if (tokens[j].lower in hourWords) {
                glue.minutesTail(tokens, used, j + 1)?.let { (mins, last) ->
                    duration = duration.plusMinutes(mins); j = last
                }
            }
            return DurationCandidate(duration, i..j)
        }

        // голая склейка без "for" — "meeting 11:00 2h", "2h45m": в заголовках
        // токены вида «цифры+h/m» практически не встречаются; раздельное
        // "2 h" без маркера намеренно не едим
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
