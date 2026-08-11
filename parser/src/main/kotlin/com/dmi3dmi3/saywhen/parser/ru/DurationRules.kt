package com.dmi3dmi3.saywhen.parser.ru

import com.dmi3dmi3.saywhen.parser.Confidence
import com.dmi3dmi3.saywhen.parser.DurationCandidate
import com.dmi3dmi3.saywhen.parser.Token
import java.time.Duration

internal object DurationRules {

    private val hourWords = setOf("час", "часа", "часов", "ч")
    private val minuteWords = setOf("минуту", "минуты", "минут", "м", "мин")

    // склейка числа с единицей: «2ч», «45м» (токенайзер не режет цифробуквы)
    private val glued = Regex("""(\d{1,3})(ч|м|мин)""")

    // «2ч45м» одним токеном — программистская нотация
    private val gluedCombo = Regex("""(\d{1,3})ч(\d{1,2})(?:м|мин)""")

    /** Токен-склейка «2ч» / «45м» / «2ч45м» → длительность и «это часы» (для хвоста минут). */
    private fun gluedToken(s: String): Pair<Duration, Boolean>? {
        gluedCombo.matchEntire(s)?.let { m ->
            val h = m.groupValues[1].toLong()
            val mm = m.groupValues[2].toLong()
            if (h in 1..99 && mm in 0..59) return Duration.ofHours(h).plusMinutes(mm) to false
        }
        glued.matchEntire(s)?.let { m ->
            val n = m.groupValues[1].toLong().takeIf { it in 1..999 } ?: return null
            val hours = m.groupValues[2] == "ч"
            return (if (hours) Duration.ofHours(n) else Duration.ofMinutes(n)) to hours
        }
        return null
    }

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
            gluedToken(next)?.let { (base, hours) ->
                var d = base
                var end = i + 1
                if (hours) minutesTail(tokens, used, end + 1)?.let { (mins, last) ->
                    d = d.plusMinutes(mins); end = last
                }
                return DurationCandidate(d, i..end)
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
                minutesTail(tokens, used, j + 1)?.let { (mins, last) ->
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
            gluedToken(tokens[i].lower)?.let { (base, hours) ->
                var d = base
                var end = i
                if (hours) minutesTail(tokens, used, i + 1)?.let { (mins, last) ->
                    d = d.plusMinutes(mins); end = last
                }
                return DurationCandidate(d, i..end)
            }
        }
        return null
    }

    /** Минутный хвост после часов: «30м» или «30 минут»; (минуты, последний токен). */
    private fun minutesTail(tokens: List<Token>, used: BooleanArray, k: Int): Pair<Long, Int>? {
        if (used.getOrNull(k) != false) return null
        val t = tokens.getOrNull(k)?.lower ?: return null
        glued.matchEntire(t)?.let { m ->
            if (m.groupValues[2] != "ч") {
                return m.groupValues[1].toLong().takeIf { it in 1..59 }?.let { it to k }
            }
        }
        val n = t.toLongOrNull()?.takeIf { it in 1..59 } ?: return null
        if (used.getOrNull(k + 1) != false) return null
        if (tokens.getOrNull(k + 1)?.lower !in minuteWords) return null
        return n to k + 1
    }
}
