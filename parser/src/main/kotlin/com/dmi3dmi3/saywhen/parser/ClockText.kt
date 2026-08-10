package com.dmi3dmi3.saywhen.parser

import java.time.Duration
import java.time.LocalTime

/** Языконезависимый разбор цифрового времени — общий для всех трансляторов. */
internal object ClockText {

    /**
     * Склеенный интервал «23:40-23:00» / «10-11:30»; хотя бы одна часть с «:» —
     * иначе «купить 2-3 батарейки» стало бы интервалом. Отрицательная разница —
     * через полночь.
     */
    fun gluedInterval(s: String): Pair<LocalTime, Duration>? {
        if ('-' !in s) return null
        val parts = s.split("-")
        if (parts.size != 2 || parts.none { ':' in it }) return null
        val from = clock(parts[0]) ?: return null
        val to = clock(parts[1]) ?: return null
        if (from == to) return null
        var d = Duration.between(from, to)
        if (d < Duration.ZERO) d = d.plusHours(24)
        return from to d
    }

    /** Минуты отдельным токеном («в 19 30», «11 00»): строго две цифры 00–59. */
    fun pairMinutes(s: String?): Int? =
        s?.takeIf { it.length == 2 }?.toIntOrNull()?.takeIf { it in 0..59 }

    /** «15» → 15:00, «9:30» → 9:30; иначе null. Минуты — строго две цифры. */
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
