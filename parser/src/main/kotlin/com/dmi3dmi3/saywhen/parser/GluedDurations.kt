package com.dmi3dmi3.saywhen.parser

import java.time.Duration
import kotlin.math.roundToLong

internal class GluedDurations(
    private val hourUnits: Set<String>,
    private val minuteUnits: Set<String>,
    private val minuteWords: Set<String>,
) {
    private val glued =
        Regex("""(\d{1,3}(?:[.,]\d{1,2})?)(${(hourUnits + minuteUnits).joinToString("|")})""")

    private val gluedCombo = Regex(
        """(\d{1,3})(?:${hourUnits.joinToString("|")})(\d{1,2})(?:${minuteUnits.joinToString("|")})"""
    )

    fun gluedToken(s: String): Pair<Duration, Boolean>? {
        gluedCombo.matchEntire(s)?.let { m ->
            val h = m.groupValues[1].toLong()
            val mm = m.groupValues[2].toLong()
            if (h in 1..99 && mm in 0..59) return Duration.ofHours(h).plusMinutes(mm) to false
        }
        glued.matchEntire(s)?.let { m ->
            val raw = m.groupValues[1]
            val hours = m.groupValues[2] in hourUnits
            decimalNumber(raw)?.let { v ->
                if (!hours) return null
                return Duration.ofMinutes((v * 60).roundToLong()) to false
            }
            val n = raw.toLong().takeIf { it in 1..999 } ?: return null
            return (if (hours) Duration.ofHours(n) else Duration.ofMinutes(n)) to hours
        }
        return null
    }

    fun minutesTail(tokens: List<Token>, used: BooleanArray, k: Int): Pair<Long, Int>? {
        if (used.getOrNull(k) != false) return null
        val t = tokens.getOrNull(k)?.lower ?: return null
        glued.matchEntire(t)?.let { m ->
            if (m.groupValues[2] in minuteUnits) {
                return m.groupValues[1].toLongOrNull()?.takeIf { it in 1..59 }?.let { it to k }
            }
        }
        val n = t.toLongOrNull()?.takeIf { it in 1..59 } ?: return null
        if (used.getOrNull(k + 1) != false) return null
        if (tokens.getOrNull(k + 1)?.lower !in minuteWords) return null
        return n to k + 1
    }
}
