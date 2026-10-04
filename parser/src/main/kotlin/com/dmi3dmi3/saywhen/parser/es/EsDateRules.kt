package com.dmi3dmi3.saywhen.parser.es

import com.dmi3dmi3.saywhen.parser.DAY_OF_MONTH
import com.dmi3dmi3.saywhen.parser.DateCandidate
import com.dmi3dmi3.saywhen.parser.DateRule
import com.dmi3dmi3.saywhen.parser.DayHalf
import com.dmi3dmi3.saywhen.parser.OFFSET_DAYS
import com.dmi3dmi3.saywhen.parser.Token
import com.dmi3dmi3.saywhen.parser.dateCandidates
import com.dmi3dmi3.saywhen.parser.dateOrNull
import com.dmi3dmi3.saywhen.parser.dateRangeCandidate
import com.dmi3dmi3.saywhen.parser.dayPair
import com.dmi3dmi3.saywhen.parser.upcomingDate
import com.dmi3dmi3.saywhen.parser.weekdayDate
import com.dmi3dmi3.saywhen.parser.yearToken
import java.time.LocalDate
import java.time.ZonedDateTime

internal object EsDateRules : DateRule {

    private val relative = mapOf("hoy" to 0L, "mañana" to 1L, "manana" to 1L)

    private val dayHalves = mapOf(
        "mañana" to DayHalf.MORNING, "manana" to DayHalf.MORNING,
        "tarde" to DayHalf.AFTERNOON, "noche" to DayHalf.EVENING,
    )

    private val months = EsWords.months

    private val nextAdj = setOf("próximo", "proximo", "próxima", "proxima")

    override fun findAll(tokens: List<Token>, now: ZonedDateTime, used: BooleanArray): List<DateCandidate> {
        val today = now.toLocalDate()
        return dateCandidates(tokens, used) { i -> matchAt(tokens, i, today) }
    }

    private fun matchAt(tokens: List<Token>, i: Int, today: LocalDate): DateCandidate? {
        val t = tokens[i].lower

        matchRange(tokens, i, today)?.let { return it }

        if (t == "pasado" && tokens.getOrNull(i + 1)?.lower in setOf("mañana", "manana")) {
            return DateCandidate(today.plusDays(2), i..i + 1)
        }

        relative[t]?.let { shift ->
            if (t != "hoy" && tokens.getOrNull(i - 1)?.lower == "la") return@let
            if (tokens.getOrNull(i + 1)?.lower == "por" && tokens.getOrNull(i + 2)?.lower == "la") {
                dayHalves[tokens.getOrNull(i + 3)?.lower]?.let { half ->
                    return DateCandidate(today.plusDays(shift), i..i + 3, dayHalf = half)
                }
            }
            return DateCandidate(today.plusDays(shift), i..i)
        }

        if (t == "esta") {
            dayHalves[tokens.getOrNull(i + 1)?.lower]?.let { half ->
                return DateCandidate(today, i..i + 1, dayHalf = half)
            }
        }

        matchWeekday(tokens, i, today)?.let { return it }

        calendarAt(tokens, i, today)?.let { return it }

        if (t == "en" || t == "dentro") {
            var j = i + 1
            if (t == "dentro") {
                if (tokens.getOrNull(j)?.lower != "de") return null
                j++
            }
            val next = tokens.getOrNull(j)?.lower ?: return null
            val amount = next.toLongOrNull() ?: EsWords.cardinals[next]?.toLong() ?: return null
            if (amount !in OFFSET_DAYS) return null
            val unit = EsWords.calendarUnits[tokens.getOrNull(j + 1)?.lower] ?: return null
            return DateCandidate(today.plus(amount, unit), i..j + 1)
        }

        return null
    }

    private fun matchRange(tokens: List<Token>, i: Int, today: LocalDate): DateCandidate? {
        val t = tokens[i].lower

        dayPair(t)?.let { (d1, d2) ->
            var m = i + 1
            if (tokens.getOrNull(m)?.lower == "de") m++
            val month = months[tokens.getOrNull(m)?.lower] ?: return@let
            return dateRangeCandidate(today, tokens, d1, month, d2, month, i..m)
        }

        val marked = t == "del"
        val d1Tok = if (marked) tokens.getOrNull(i + 1)?.lower else t
        val d1 = d1Tok?.toIntOrNull()?.takeIf { it in DAY_OF_MONTH } ?: return null
        var j = if (marked) i + 2 else i + 1
        if (tokens.getOrNull(j)?.lower !in setOf("al", "a")) return null
        val d2 = tokens.getOrNull(j + 1)?.lower?.toIntOrNull()?.takeIf { it in DAY_OF_MONTH } ?: return null
        var end = j + 1
        var m = end + 1
        if (tokens.getOrNull(m)?.lower == "de") m++
        val month = months[tokens.getOrNull(m)?.lower]
        if (month != null) end = m
        if (month == null && !marked) return null
        return month?.let { dateRangeCandidate(today, tokens, d1, it, d2, it, i..end) }
    }

    private fun matchWeekday(tokens: List<Token>, i: Int, today: LocalDate): DateCandidate? {
        var j = i
        if (tokens[j].lower == "el") j++
        var isNext = false
        when (tokens.getOrNull(j)?.lower) {
            in nextAdj -> { isNext = true; j++ }
            "este" -> j++
            else -> {}
        }
        val dow = EsWords.weekdays[tokens.getOrNull(j)?.lower] ?: return null
        var end = j
        if (!isNext && tokens.getOrNull(j + 1)?.lower == "que" &&
            tokens.getOrNull(j + 2)?.lower == "viene"
        ) {
            isNext = true; end = j + 2
        }

        calendarAt(tokens, end + 1, today)?.let { d -> return d.copy(tokens = i..d.tokens.last) }

        if (tokens.getOrNull(end + 1)?.lower == "de") {
            var q = end + 2
            if (tokens.getOrNull(q)?.lower == "la") q++
            val adj = tokens.getOrNull(q)?.lower
            when {
                adj in nextAdj && tokens.getOrNull(q + 1)?.lower == "semana" -> {
                    isNext = true; end = q + 1
                }
                adj == "esta" && tokens.getOrNull(q + 1)?.lower == "semana" -> end = q + 1
                adj == "semana" && tokens.getOrNull(q + 1)?.lower == "que" &&
                    tokens.getOrNull(q + 2)?.lower == "viene" -> {
                    isNext = true; end = q + 2
                }
            }
        }

        return DateCandidate(weekdayDate(today, dow, isNext), i..end, fromWeekday = true)
    }

    private fun calendarAt(tokens: List<Token>, k: Int, today: LocalDate): DateCandidate? {
        var p = k
        if (tokens.getOrNull(p)?.lower == "el") p++
        val t = tokens.getOrNull(p)?.lower ?: return null

        months[t]?.let { month ->
            val day = tokens.getOrNull(p + 1)?.lower?.toIntOrNull()?.takeIf { it in DAY_OF_MONTH }
                ?: return@let
            return withYear(tokens, k, p + 1, month, day, today)
        }

        val day = when (t) {
            "primero", "uno" -> 1
            else -> t.toIntOrNull()?.takeIf { it in DAY_OF_MONTH } ?: return null
        }
        var m = p + 1
        if (tokens.getOrNull(m)?.lower == "de") m++
        val month = months[tokens.getOrNull(m)?.lower] ?: return null
        return withYear(tokens, k, m, month, day, today)
    }

    private fun withYear(
        tokens: List<Token>,
        from: Int,
        mEnd: Int,
        month: Int,
        day: Int,
        today: LocalDate,
    ): DateCandidate? {
        var y = mEnd + 1
        if (tokens.getOrNull(y)?.lower == "de") y++
        yearToken(tokens.getOrNull(y)?.lower)?.let { year ->
            return dateOrNull(year, month, day)?.let { DateCandidate(it, from..y) }
        }
        return upcomingDate(today, month, day)?.let { DateCandidate(it, from..mEnd) }
    }
}
