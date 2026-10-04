package com.dmi3dmi3.saywhen.parser

import java.time.Duration
import kotlin.math.roundToLong

internal class DurationScanner(
    private val markers: Set<String>,
    private val hourWords: Set<String>,
    private val minuteWords: Set<String>,
    private val cardinals: Map<String, Int>,
    private val tens: Set<Int> = emptySet(),
    private val tailConjunctions: Set<String> = emptySet(),
    private val glue: GluedDurations,
    private val idiomAt: (tokens: List<Token>, i: Int, used: BooleanArray) -> DurationCandidate?,
    private val bareGlue: Boolean = true,
) {
    fun find(tokens: List<Token>, used: BooleanArray): DurationCandidate? {
        for (i in tokens.indices) {
            if (used[i] || tokens[i].lower !in markers) continue
            if (used.getOrNull(i + 1) == true) continue
            val next = tokens.getOrNull(i + 1)?.lower ?: continue

            idiomAt(tokens, i, used)?.let { return it }

            glue.gluedToken(next)?.let { (base, hours) ->
                var d = base
                var end = i + 1
                if (hours) glue.minutesTail(tokens, used, end + 1)?.let { (mins, last) ->
                    d = d.plusMinutes(mins); end = last
                }
                return DurationCandidate(d, i..end)
            }

            decimalNumber(next)?.let { v ->
                if (used.getOrNull(i + 2) == false && tokens.getOrNull(i + 2)?.lower in hourWords) {
                    return DurationCandidate(Duration.ofMinutes((v * 60).roundToLong()), i..i + 2)
                }
            }

            var amount = next.toLongOrNull() ?: cardinals[next]?.toLong() ?: continue
            var j = i + 2
            if (amount.toInt() in tens && used.getOrNull(j) == false) {
                cardinals[tokens.getOrNull(j)?.lower]?.takeIf { it < 10 }
                    ?.let { amount += it; j++ }
            }
            if (amount !in AMOUNT) continue
            if (used.getOrNull(j) == true) continue
            var duration = when (tokens.getOrNull(j)?.lower) {
                in hourWords -> Duration.ofHours(amount)
                in minuteWords -> Duration.ofMinutes(amount)
                else -> continue
            }
            if (tokens[j].lower in hourWords) {
                var k = j + 1
                var viaConjunction = false
                if (tokens.getOrNull(k)?.lower in tailConjunctions && used.getOrNull(k) == false) {
                    k++; viaConjunction = true
                }
                val tail = glue.minutesTail(tokens, used, k)
                if (tail != null) {
                    duration = duration.plusMinutes(tail.first); j = tail.second
                } else if (viaConjunction && used.getOrNull(k) == false) {
                    tokens.getOrNull(k)?.lower?.toLongOrNull()?.takeIf { it in 1..59 }?.let {
                        duration = duration.plusMinutes(it); j = k
                    }
                }
            }
            return DurationCandidate(duration, i..j)
        }

        if (!bareGlue) return null
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
