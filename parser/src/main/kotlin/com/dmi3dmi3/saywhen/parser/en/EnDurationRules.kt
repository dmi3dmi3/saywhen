package com.dmi3dmi3.saywhen.parser.en

import com.dmi3dmi3.saywhen.parser.DurationCandidate
import com.dmi3dmi3.saywhen.parser.Token
import java.time.Duration

internal object EnDurationRules {

    private val hourWords = setOf("hour", "hours", "hr", "hrs", "h")
    private val minuteWords = setOf("minute", "minutes", "min", "mins", "m")

    // склейка числа с единицей: "2h", "45m", "2hrs" (токенайзер не режет цифробуквы)
    private val glued = Regex("""(\d{1,3})(h|hr|hrs|m|min|mins)""")

    // "2h45m" одним токеном — программистская нотация
    private val gluedCombo = Regex("""(\d{1,3})(?:h|hr|hrs)(\d{1,2})(?:m|min|mins)""")

    /** Токен-склейка "2h" / "45m" / "2h45m" → длительность и «это часы» (для хвоста минут). */
    private fun gluedToken(s: String): Pair<Duration, Boolean>? {
        gluedCombo.matchEntire(s)?.let { m ->
            val h = m.groupValues[1].toLong()
            val mm = m.groupValues[2].toLong()
            if (h in 1..99 && mm in 0..59) return Duration.ofHours(h).plusMinutes(mm) to false
        }
        glued.matchEntire(s)?.let { m ->
            val n = m.groupValues[1].toLong().takeIf { it in 1..999 } ?: return null
            val hours = m.groupValues[2].first() == 'h'
            return (if (hours) Duration.ofHours(n) else Duration.ofMinutes(n)) to hours
        }
        return null
    }

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
            gluedToken(next)?.let { (base, hours) ->
                var d = base
                var end = i + 1
                if (hours) minutesTail(tokens, used, end + 1)?.let { (mins, last) ->
                    d = d.plusMinutes(mins); end = last
                }
                return DurationCandidate(d, i..end)
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
                minutesTail(tokens, used, j + 1)?.let { (mins, last) ->
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

    /** Минутный хвост после часов: "30m" или "30 minutes"; (минуты, последний токен). */
    private fun minutesTail(tokens: List<Token>, used: BooleanArray, k: Int): Pair<Long, Int>? {
        if (used.getOrNull(k) != false) return null
        val t = tokens.getOrNull(k)?.lower ?: return null
        glued.matchEntire(t)?.let { m ->
            if (m.groupValues[2].first() == 'm') {
                return m.groupValues[1].toLong().takeIf { it in 1..59 }?.let { it to k }
            }
        }
        val n = t.toLongOrNull()?.takeIf { it in 1..59 } ?: return null
        if (used.getOrNull(k + 1) != false) return null
        if (tokens.getOrNull(k + 1)?.lower !in minuteWords) return null
        return n to k + 1
    }
}
