package com.dmi3dmi3.saywhen.parser.de

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

internal object DeDateRules : DateRule {

    private val relative = mapOf("heute" to 0L, "morgen" to 1L, "übermorgen" to 2L, "uebermorgen" to 2L)

    private val dayHalves = mapOf(
        "früh" to DayHalf.MORNING, "frueh" to DayHalf.MORNING, "morgen" to DayHalf.MORNING,
        "vormittag" to DayHalf.MORNING,
        "abend" to DayHalf.EVENING, "nachmittag" to DayHalf.AFTERNOON, "nacht" to DayHalf.EVENING,
    )

    private val months = DeWords.months

    private val nextAdj = setOf("nächsten", "nächste", "naechsten", "naechste", "kommenden", "kommende")
    private val thisAdj = setOf("diesen", "diese", "dieses")

    override fun findAll(tokens: List<Token>, now: ZonedDateTime, used: BooleanArray): List<DateCandidate> {
        val today = now.toLocalDate()
        return dateCandidates(tokens, used) { i -> matchAt(tokens, i, today) }
    }

    private fun matchAt(tokens: List<Token>, i: Int, today: LocalDate): DateCandidate? {
        val t = tokens[i].lower

        matchRange(tokens, i, today)?.let { return it }

        relative[t]?.let { shift ->
            if (t == "morgen" && tokens.getOrNull(i - 1)?.lower == "am") return@let
            dayHalves[tokens.getOrNull(i + 1)?.lower]?.let { half ->
                return DateCandidate(today.plusDays(shift), i..i + 1, dayHalf = half)
            }
            return DateCandidate(today.plusDays(shift), i..i)
        }

        matchWeekday(tokens, i, today)?.let { return it }

        calendarAt(tokens, i, today)?.let { return it }

        if (t == "in") {
            val next = tokens.getOrNull(i + 1)?.lower ?: return null
            val amount = next.toLongOrNull() ?: DeWords.cardinals[next]?.toLong() ?: return null
            if (amount !in OFFSET_DAYS) return null
            val unit = DeWords.calendarUnits[tokens.getOrNull(i + 2)?.lower] ?: return null
            return DateCandidate(today.plus(amount, unit), i..i + 2)
        }

        return null
    }

    private fun matchRange(tokens: List<Token>, i: Int, today: LocalDate): DateCandidate? {
        val t = tokens[i].lower

        dayPair(t)?.let { (d1, d2) ->
            val month = months[tokens.getOrNull(i + 1)?.lower] ?: return@let
            return dateRangeCandidate(today, tokens, d1, month, d2, month, i..i + 1)
        }

        val d1 = dayAt(t) ?: return null
        var j = i + 1
        val startMonth = months[tokens.getOrNull(j)?.lower]
        if (startMonth != null) j++
        if (tokens.getOrNull(j)?.lower != "bis") return null
        var k = j + 1
        if (tokens.getOrNull(k)?.lower == "zum") k++
        val d2 = tokens.getOrNull(k)?.lower?.let(::dayAt) ?: return null
        var end = k
        val endMonth = months[tokens.getOrNull(end + 1)?.lower]
        if (endMonth != null) end++
        if (startMonth == null && endMonth == null) return null
        return dateRangeCandidate(today, tokens, d1, startMonth ?: endMonth!!, d2, endMonth ?: startMonth!!, i..end)
    }

    private fun dayAt(word: String): Int? = DeWords.digitOrdinal.parse(word)

    private fun matchWeekday(tokens: List<Token>, i: Int, today: LocalDate): DateCandidate? {
        var j = i
        val adj = tokens.getOrNull(j)?.lower
        var isNext = adj in nextAdj
        if (isNext || adj in thisAdj) j++
        val dow = DeWords.weekdays[tokens.getOrNull(j)?.lower] ?: return null

        calendarAt(tokens, j + 1, today)?.let { d -> return d.copy(tokens = i..d.tokens.last) }

        var end = j
        run {
            var q = j + 1
            if (tokens.getOrNull(q)?.lower == "der") q++
            val qualifier = tokens.getOrNull(q)?.lower ?: return@run
            val next = qualifier in nextAdj
            if (!next && qualifier !in setOf("dieser", "diese")) return@run
            if (tokens.getOrNull(q + 1)?.lower != "woche") return@run
            if (next) isNext = true
            end = q + 1
        }

        return DateCandidate(weekdayDate(today, dow, isNext), i..end, fromWeekday = true)
    }

    private fun calendarAt(tokens: List<Token>, k: Int, today: LocalDate): DateCandidate? {
        val t = tokens.getOrNull(k)?.lower ?: return null

        months[t]?.let { month ->
            val day = tokens.getOrNull(k + 1)?.lower?.let(::dayAt) ?: return@let
            return withYear(tokens, k, k + 1, month, day, today)
        }

        val day = dayAt(t) ?: DeWords.ordinals[t]?.takeIf { it in DAY_OF_MONTH } ?: return null
        val month = months[tokens.getOrNull(k + 1)?.lower] ?: return null
        return withYear(tokens, k, k + 1, month, day, today)
    }

    private fun withYear(
        tokens: List<Token>,
        from: Int,
        mEnd: Int,
        month: Int,
        day: Int,
        today: LocalDate,
    ): DateCandidate? {
        yearToken(tokens.getOrNull(mEnd + 1)?.lower)?.let { year ->
            return dateOrNull(year, month, day)?.let { DateCandidate(it, from..mEnd + 1) }
        }
        return upcomingDate(today, month, day)?.let { DateCandidate(it, from..mEnd) }
    }
}
