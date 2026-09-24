package com.dmi3dmi3.saywhen.parser.ru

import com.dmi3dmi3.saywhen.parser.Confidence
import com.dmi3dmi3.saywhen.parser.DurationCandidate
import com.dmi3dmi3.saywhen.parser.GluedDurations
import com.dmi3dmi3.saywhen.parser.Token
import com.dmi3dmi3.saywhen.parser.decimalNumber
import java.time.Duration
import kotlin.math.roundToLong

internal object DurationRules {

    private val hourWords = setOf("час", "часа", "часов", "ч")
    private val minuteWords = setOf("минуту", "минуты", "минут", "м", "мин")

    // склейки «2ч» / «45м» / «2ч45м» — общая логика в ядре, единицы наши
    private val glue = GluedDurations(setOf("ч"), setOf("м", "мин"), minuteWords)

    // числа словами: единицы/десятки — «на три часа», «на сорок [пять] минут»
    private val wordNumbers = mapOf(
        "два" to 2L, "две" to 2L, "три" to 3L, "четыре" to 4L, "пять" to 5L,
        "шесть" to 6L, "семь" to 7L, "восемь" to 8L, "девять" to 9L, "десять" to 10L,
        "одиннадцать" to 11L, "двенадцать" to 12L, "пятнадцать" to 15L,
        "двадцать" to 20L, "тридцать" to 30L, "сорок" to 40L, "пятьдесят" to 50L,
    )
    private val tens = setOf(20L, 30L, 40L, 50L)

    fun find(tokens: List<Token>, used: BooleanArray): DurationCandidate? {
        for (i in tokens.indices) {
            if (used[i] || tokens[i].lower != "на") continue
            if (used.getOrNull(i + 1) == true) continue
            val next = tokens.getOrNull(i + 1)?.lower ?: continue

            // «на час» / «на полчаса» — без числа
            if (next in hourWords) return DurationCandidate(Duration.ofHours(1), i..i + 1)
            if (next == "полчаса") return DurationCandidate(Duration.ofMinutes(30), i..i + 1)

            // «на полтора часа»
            if (next == "полтора" && used.getOrNull(i + 2) == false &&
                tokens.getOrNull(i + 2)?.lower in hourWords
            ) {
                return DurationCandidate(Duration.ofMinutes(90), i..i + 2)
            }

            // «на 2ч» / «на 45м» / «на 2ч45м» — единица приклеена к числу
            glue.gluedToken(next)?.let { (base, hours) ->
                var d = base
                var end = i + 1
                if (hours) glue.minutesTail(tokens, used, end + 1)?.let { (mins, last) ->
                    d = d.plusMinutes(mins); end = last
                }
                return DurationCandidate(d, i..end)
            }

            // «на 1,5 часа» — десятичные только у часов
            decimalNumber(next)?.let { v ->
                if (used.getOrNull(i + 2) == false && tokens.getOrNull(i + 2)?.lower in hourWords) {
                    return DurationCandidate(Duration.ofMinutes((v * 60).roundToLong()), i..i + 2)
                }
            }

            // «на N часов/минут», число цифрами или словом; границы — как у дат в 03
            var amount = next.toLongOrNull() ?: wordNumbers[next] ?: continue
            var j = i + 2
            // составное «сорок пять»
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
            // хвост минут после часов: «на 2ч 30м», «на 2 часа 30 минут»
            if (tokens[j].lower in hourWords) {
                glue.minutesTail(tokens, used, j + 1)?.let { (mins, last) ->
                    duration = duration.plusMinutes(mins); j = last
                }
            }
            return DurationCandidate(duration, i..j)
        }

        // голая склейка без «на» — «встреча в 11 2ч», «2ч45м»: в заголовках
        // токены вида «цифры+ч/м» практически не встречаются; раздельное
        // «2 ч» без маркера намеренно не едим
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
