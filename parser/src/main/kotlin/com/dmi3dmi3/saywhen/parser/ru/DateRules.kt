package com.dmi3dmi3.saywhen.parser.ru

import com.dmi3dmi3.saywhen.parser.Confidence
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

internal object DateRules : DateRule {

    private val relative = mapOf(
        "сегодня" to 0L,
        "завтра" to 1L,
        "послезавтра" to 2L,
    )

    private val dayHalves = mapOf(
        "утром" to DayHalf.MORNING, "вечером" to DayHalf.EVENING,
        "ночью" to DayHalf.EVENING, "днём" to DayHalf.AFTERNOON, "днем" to DayHalf.AFTERNOON,
    )

    override fun findAll(tokens: List<Token>, now: ZonedDateTime, used: BooleanArray): List<DateCandidate> {
        val today = now.toLocalDate()
        return dateCandidates(tokens, used) { i -> matchAt(tokens, i, today) }
    }

    private fun matchAt(tokens: List<Token>, i: Int, today: LocalDate): DateCandidate? {
        val t = tokens[i].lower

        matchRange(tokens, i, today)?.let { return it }

        relative[t]?.let { shift ->
            dayHalves[tokens.getOrNull(i + 1)?.lower]?.let { half ->
                return DateCandidate(today.plusDays(shift), i..i + 1, dayHalf = half)
            }
            return DateCandidate(today.plusDays(shift), i..i)
        }

        matchWeekday(tokens, i, today)?.let { return it }

        calendarAt(tokens, i, today)?.let { return it }

        if (t == "через") {
            val next = tokens.getOrNull(i + 1)?.lower ?: return null
            val amount = next.toLongOrNull()
            if (amount != null) {
                if (amount !in OFFSET_DAYS) return null
                val unit = Words.calendarUnits[tokens.getOrNull(i + 2)?.lower] ?: return null
                return DateCandidate(today.plus(amount, unit), i..i + 2)
            }
            Words.calendarUnits[next]?.let { return DateCandidate(today.plus(1, it), i..i + 1) }
        }

        return null
    }

    private fun matchRange(tokens: List<Token>, i: Int, today: LocalDate): DateCandidate? {
        val t = tokens[i].lower

        dayPair(t)?.let { (d1, d2) ->
            val month = Words.months[tokens.getOrNull(i + 1)?.lower] ?: return@let
            return dateRangeCandidate(today, tokens, d1, month, d2, month, i..i + 1)
        }

        val startDay = t.toIntOrNull()?.takeIf { it in DAY_OF_MONTH } ?: return null
        var j = i + 1
        val startMonth = Words.months[tokens.getOrNull(j)?.lower]
        if (startMonth != null) j++
        if (tokens.getOrNull(j)?.lower != "по") return null
        val endDay = tokens.getOrNull(j + 1)?.lower?.toIntOrNull()?.takeIf { it in DAY_OF_MONTH } ?: return null
        val endMonth = Words.months[tokens.getOrNull(j + 2)?.lower] ?: return null
        return dateRangeCandidate(today, tokens, startDay, startMonth ?: endMonth, endDay, endMonth, i..j + 2)
    }

    private fun dayAt(tokens: List<Token>, k: Int): Pair<Int, Int>? {
        val t = tokens.getOrNull(k)?.lower ?: return null
        Words.digitOrdinal.parse(t)?.let { return it to k }
        Words.cardinals[t]?.takeIf { it in Words.tens }?.let { tens ->
            Words.ordinals[tokens.getOrNull(k + 1)?.lower]?.takeIf { it in 1..9 }
                ?.let { return ((tens + it) to (k + 1)).takeIf { p -> p.first in DAY_OF_MONTH } }
        }
        Words.ordinals[t]?.takeIf { it in DAY_OF_MONTH }?.let { return it to k }
        return null
    }

    private fun calendarAt(tokens: List<Token>, k: Int, today: LocalDate): DateCandidate? {
        val (day, dEnd) = dayAt(tokens, k) ?: return null
        val month = Words.months[tokens.getOrNull(dEnd + 1)?.lower] ?: return null
        yearToken(tokens.getOrNull(dEnd + 2)?.lower)?.let { y ->
            var end = dEnd + 2
            if (tokens.getOrNull(end + 1)?.lower in setOf("года", "г")) end++
            return dateOrNull(y, month, day)?.let { DateCandidate(it, k..end) }
        }
        return upcomingDate(today, month, day)?.let { DateCandidate(it, k..dEnd + 1) }
    }

    private val nextAdj = setOf("следующий", "следующая", "следующую", "следующее")
    private val thisAdj = setOf("этот", "эта", "эту", "это")

    private fun matchWeekday(tokens: List<Token>, i: Int, today: LocalDate): DateCandidate? {
        var j = i
        if (tokens[j].lower == "в" || tokens[j].lower == "во") j++
        val adj = tokens.getOrNull(j)?.lower
        var isNextWeek = adj in nextAdj
        if (isNextWeek || adj in thisAdj) j++
        val dow = Words.weekdays[tokens.getOrNull(j)?.lower] ?: return null

        calendarAt(tokens, j + 1, today)?.let { d -> return d.copy(tokens = i..d.tokens.last) }

        var end = j
        weekQualifier(tokens, j + 1)?.let { (next, qEnd) ->
            if (next) isNextWeek = true
            end = qEnd
        }

        return DateCandidate(weekdayDate(today, dow, isNextWeek), i..end, fromWeekday = true)
    }

    private fun weekQualifier(tokens: List<Token>, k: Int): Pair<Boolean, Int>? {
        var j = k
        if (tokens.getOrNull(j)?.lower == "на") j++
        val adj = tokens.getOrNull(j)?.lower ?: return null
        val next = adj in setOf("следующей", "будущей")
        if (!next && adj !in setOf("этой", "текущей")) return null
        if (tokens.getOrNull(j + 1)?.lower !in setOf("неделе", "недели")) return null
        return next to j + 1
    }
}
