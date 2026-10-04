package com.dmi3dmi3.saywhen.parser.en

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

internal object EnDateRules : DateRule {

    private val relative = mapOf("today" to 0L, "tomorrow" to 1L)

    private val dayHalves = mapOf(
        "morning" to DayHalf.MORNING, "evening" to DayHalf.EVENING,
        "night" to DayHalf.EVENING, "afternoon" to DayHalf.AFTERNOON,
    )

    private val months = EnWords.months

    private fun dayNumber(s: String?): Int? = EnWords.digitOrdinal.parse(s)

    override fun findAll(tokens: List<Token>, now: ZonedDateTime, used: BooleanArray): List<DateCandidate> {
        val today = now.toLocalDate()
        return dateCandidates(tokens, used) { i -> matchAt(tokens, i, today) }
    }

    private fun matchAt(tokens: List<Token>, i: Int, today: LocalDate): DateCandidate? {
        val t = tokens[i].lower

        matchRange(tokens, i, today)?.let { return it }

        if (t == "day" && tokens.getOrNull(i + 1)?.lower == "after" &&
            tokens.getOrNull(i + 2)?.lower == "tomorrow"
        ) {
            return DateCandidate(today.plusDays(2), i..i + 2)
        }

        relative[t]?.let { shift ->
            dayHalves[tokens.getOrNull(i + 1)?.lower]?.let { half ->
                return DateCandidate(today.plusDays(shift), i..i + 1, dayHalf = half)
            }
            return DateCandidate(today.plusDays(shift), i..i)
        }

        if (t == "tonight") return DateCandidate(today, i..i, dayHalf = DayHalf.EVENING)

        if (t == "this") {
            dayHalves[tokens.getOrNull(i + 1)?.lower]?.let { half ->
                return DateCandidate(today, i..i + 1, dayHalf = half)
            }
        }

        matchWeekday(tokens, i, today)?.let { return it }

        calendarAt(tokens, i, today)?.let { return it }

        if (t == "in") {
            val next = tokens.getOrNull(i + 1)?.lower ?: return null
            val amount = next.toLongOrNull() ?: if (next == "a" || next == "an") 1L else return null
            if (amount !in OFFSET_DAYS) return null
            val unit = EnWords.calendarUnits[tokens.getOrNull(i + 2)?.lower] ?: return null
            return DateCandidate(today.plus(amount, unit), i..i + 2)
        }

        return null
    }

    private fun matchRange(tokens: List<Token>, i: Int, today: LocalDate): DateCandidate? {
        val t = tokens[i].lower

        months[t]?.let { month ->
            val next = tokens.getOrNull(i + 1)?.lower
            dayPair(next)?.let { (d1, d2) ->
                return dateRangeCandidate(today, tokens, d1, month, d2, month, i..i + 1)
            }
            val d1 = dayNumber(next) ?: return@let
            if (tokens.getOrNull(i + 2)?.lower != "to") return@let
            var j = i + 3
            val endMonth = months[tokens.getOrNull(j)?.lower]
            if (endMonth != null) j++
            val d2 = dayNumber(tokens.getOrNull(j)?.lower) ?: return@let
            return dateRangeCandidate(today, tokens, d1, month, d2, endMonth ?: month, i..j)
        }

        dayPair(t)?.let { (d1, d2) ->
            val month = months[tokens.getOrNull(i + 1)?.lower] ?: return@let
            return dateRangeCandidate(today, tokens, d1, month, d2, month, i..i + 1)
        }
        val d1 = dayNumber(t) ?: return null
        var j = i + 1
        val startMonth = months[tokens.getOrNull(j)?.lower]
        if (startMonth != null) j++
        if (tokens.getOrNull(j)?.lower != "to") return null
        val d2 = dayNumber(tokens.getOrNull(j + 1)?.lower) ?: return null
        var end = j + 1
        var m = end + 1
        if (tokens.getOrNull(m)?.lower == "of") m++
        if (tokens.getOrNull(m)?.lower == "the") m++
        val endMonth = months[tokens.getOrNull(m)?.lower]
        if (endMonth != null) end = m
        if (startMonth == null && endMonth == null) return null
        return dateRangeCandidate(today, tokens, d1, startMonth ?: endMonth!!, d2, endMonth ?: startMonth!!, i..end)
    }

    private fun calendarAt(tokens: List<Token>, k: Int, today: LocalDate): DateCandidate? {
        var p = k
        if (tokens.getOrNull(p)?.lower == "the") p++
        val t = tokens.getOrNull(p)?.lower ?: return null
        months[t]?.let { month ->
            var d = p + 1
            if (tokens.getOrNull(d)?.lower == "the") d++
            val day = dayNumber(tokens.getOrNull(d)?.lower)
            if (day != null) {
                explicitYear(tokens, d + 1, k, month, day)?.let { return it }
                return upcomingDate(today, month, day)?.let { DateCandidate(it, k..d) }
            }
        }
        val day = dayNumber(t)
        if (day != null) {
            var m = p + 1
            if (tokens.getOrNull(m)?.lower == "of") m++
            months[tokens.getOrNull(m)?.lower]?.let { month ->
                explicitYear(tokens, m + 1, k, month, day)?.let { return it }
                return upcomingDate(today, month, day)?.let { DateCandidate(it, k..m) }
            }
        }
        return null
    }

    private fun matchWeekday(tokens: List<Token>, i: Int, today: LocalDate): DateCandidate? {
        var j = i
        if (tokens[j].lower == "on") j++
        val adj = tokens.getOrNull(j)?.lower
        var isNextWeek = adj == "next"
        if (isNextWeek || adj == "this") j++
        val dow = EnWords.weekdays[tokens.getOrNull(j)?.lower] ?: return null

        calendarAt(tokens, j + 1, today)?.let { d -> return d.copy(tokens = i..d.tokens.last) }

        var end = j
        run {
            var q = j + 1
            if (tokens.getOrNull(q)?.lower == "of") q++
            val qualifier = tokens.getOrNull(q)?.lower ?: return@run
            val next = qualifier == "next"
            if (!next && qualifier != "this" && qualifier != "current") return@run
            if (tokens.getOrNull(q + 1)?.lower != "week") return@run
            if (next) isNextWeek = true
            end = q + 1
        }

        return DateCandidate(weekdayDate(today, dow, isNextWeek), i..end, fromWeekday = true)
    }

    private fun explicitYear(tokens: List<Token>, at: Int, from: Int, month: Int, day: Int): DateCandidate? =
        yearToken(tokens.getOrNull(at)?.lower)?.let { y ->
            dateOrNull(y, month, day)?.let { DateCandidate(it, from..at) }
        }
}
