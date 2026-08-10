package com.dmi3dmi3.saywhen.parser.en

import com.dmi3dmi3.saywhen.parser.DurationCandidate
import com.dmi3dmi3.saywhen.parser.Token
import java.time.Duration

internal object EnDurationRules {

    private val hourWords = setOf("hour", "hours")
    private val minuteWords = setOf("minute", "minutes")

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
            val duration = when (tokens.getOrNull(j)?.lower) {
                in hourWords -> Duration.ofHours(amount)
                in minuteWords -> Duration.ofMinutes(amount)
                else -> continue
            }
            return DurationCandidate(duration, i..j)
        }
        return null
    }
}
