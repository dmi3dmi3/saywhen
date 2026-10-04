package com.dmi3dmi3.saywhen.parser

import java.time.Duration
import java.time.LocalTime

internal object ClockText {

    fun gluedInterval(s: String): Pair<LocalTime, Duration>? {
        if ('-' !in s) return null
        val parts = s.split("-").map { it.trim() }
        if (parts.size != 2 || parts.none { ':' in it }) return null
        val from = clock(parts[0]) ?: return null
        val to = clock(parts[1]) ?: return null
        if (from == to) return null
        return from to span(from, to)
    }

    fun span(from: LocalTime, to: LocalTime): Duration {
        var d = Duration.between(from, to)
        if (d < Duration.ZERO) d = d.plusHours(24)
        return d
    }

    fun pairMinutes(s: String?): Int? =
        s?.takeIf { it.length == 2 }?.toIntOrNull()?.takeIf { it in 0..59 }

    fun dottedClock(s: String?): LocalTime? =
        s?.takeIf { dottedTime.matches(it) }?.let { clock(it.replace('.', ':')) }

    private val dottedTime = Regex("""\d{1,2}\.\d{2}""")

    fun clock(s: String?): LocalTime? {
        s ?: return null
        val parts = s.split(":")
        return when (parts.size) {
            1 -> parts[0].toIntOrNull()?.takeIf { it in 0..23 && s.length <= 2 }
                ?.let { LocalTime.of(it, 0) }
            2 -> {
                val h = parts[0].toIntOrNull() ?: return null
                val m = parts[1].toIntOrNull() ?: return null
                if (h in 0..23 && m in 0..59 && parts[0].length in 1..2 && parts[1].length == 2) {
                    LocalTime.of(h, m)
                } else null
            }
            else -> null
        }
    }
}
