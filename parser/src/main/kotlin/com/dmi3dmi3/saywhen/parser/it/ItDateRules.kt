package com.dmi3dmi3.saywhen.parser.it

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

internal object ItDateRules : DateRule {

    private val relative = mapOf("oggi" to 0L, "domani" to 1L, "dopodomani" to 2L)

    private val dayHalves = mapOf(
        "mattina" to DayHalf.MORNING, "mattino" to DayHalf.MORNING,
        "sera" to DayHalf.EVENING, "notte" to DayHalf.EVENING,
        "pomeriggio" to DayHalf.AFTERNOON,
    )

    private val months = ItWords.months

    private val nextAdj = setOf("prossimo", "prossima")

    override fun findAll(tokens: List<Token>, now: ZonedDateTime, used: BooleanArray): List<DateCandidate> {
        val today = now.toLocalDate()
        return dateCandidates(tokens, used) { i -> matchAt(tokens, i, today) }
    }

    private fun matchAt(tokens: List<Token>, i: Int, today: LocalDate): DateCandidate? {
        val t = tokens[i].lower

        matchRange(tokens, i, today)?.let { return it }

        if (t == "dopo" && tokens.getOrNull(i + 1)?.lower == "domani") {
            return DateCandidate(today.plusDays(2), i..i + 1)
        }

        relative[t]?.let { shift ->
            dayHalves[tokens.getOrNull(i + 1)?.lower]?.let { half ->
                return DateCandidate(today.plusDays(shift), i..i + 1, dayHalf = half)
            }
            return DateCandidate(today.plusDays(shift), i..i)
        }

        when (t) {
            "stasera", "stanotte" -> return DateCandidate(today, i..i, dayHalf = DayHalf.EVENING)
            "stamattina" -> return DateCandidate(today, i..i, dayHalf = DayHalf.MORNING)
            "domattina" -> return DateCandidate(today.plusDays(1), i..i, dayHalf = DayHalf.MORNING)
        }

        if (t == "questa" || t == "sta") {
            dayHalves[tokens.getOrNull(i + 1)?.lower]?.let { half ->
                return DateCandidate(today, i..i + 1, dayHalf = half)
            }
        }

        matchWeekday(tokens, i, today)?.let { return it }

        calendarAt(tokens, i, today)?.let { return it }

        if (t == "tra" || t == "fra" || t == "in") {
            val next = tokens.getOrNull(i + 1)?.lower ?: return null
            val amount = next.toLongOrNull() ?: ItWords.cardinals[next]?.toLong()
            if (amount != null) {
                if (amount !in OFFSET_DAYS) return null
                val unit = ItWords.calendarUnits[tokens.getOrNull(i + 2)?.lower] ?: return null
                return DateCandidate(today.plus(amount, unit), i..i + 2)
            }
            if (t != "in") {
                ItWords.calendarUnits[next]?.let { return DateCandidate(today.plus(1, it), i..i + 1) }
            }
        }

        return null
    }

    private fun matchRange(tokens: List<Token>, i: Int, today: LocalDate): DateCandidate? {
        val t = tokens[i].lower

        dayPair(t)?.let { (d1, d2) ->
            val month = months[tokens.getOrNull(i + 1)?.lower] ?: return@let
            return dateRangeCandidate(today, tokens, d1, month, d2, month, i..i + 1)
        }

        if (t != "dal") return null
        val d1 = tokens.getOrNull(i + 1)?.lower?.toIntOrNull()?.takeIf { it in DAY_OF_MONTH } ?: return null
        var j = i + 2
        val startMonth = months[tokens.getOrNull(j)?.lower]
        if (startMonth != null) j++
        if (tokens.getOrNull(j)?.lower != "al") return null
        val d2 = tokens.getOrNull(j + 1)?.lower?.toIntOrNull()?.takeIf { it in DAY_OF_MONTH } ?: return null
        var end = j + 1
        val endMonth = months[tokens.getOrNull(end + 1)?.lower]
        if (endMonth != null) end++
        if (startMonth == null && endMonth == null) return null
        return dateRangeCandidate(today, tokens, d1, startMonth ?: endMonth!!, d2, endMonth ?: startMonth!!, i..end)
    }

    private fun matchWeekday(tokens: List<Token>, i: Int, today: LocalDate): DateCandidate? {
        var j = i
        if (tokens[j].lower == "il" || tokens[j].lower == "l") j++
        var isNext = false
        if (tokens.getOrNull(j)?.lower in nextAdj) {
            isNext = true; j++
        }
        val dow = ItWords.weekdays[tokens.getOrNull(j)?.lower] ?: return null
        var end = j
        if (!isNext && tokens.getOrNull(j + 1)?.lower in nextAdj) {
            isNext = true; end = j + 1
        }

        calendarAt(tokens, end + 1, today)?.let { d -> return d.copy(tokens = i..d.tokens.last) }

        if (tokens.getOrNull(end + 1)?.lower in setOf("di", "della")) {
            val q = end + 2
            val adj = tokens.getOrNull(q)?.lower
            if (adj in setOf("prossima", "questa") && tokens.getOrNull(q + 1)?.lower == "settimana") {
                if (adj == "prossima") isNext = true
                end = q + 1
            }
        }

        return DateCandidate(weekdayDate(today, dow, isNext), i..end, fromWeekday = true)
    }

    private fun calendarAt(tokens: List<Token>, k: Int, today: LocalDate): DateCandidate? {
        var p = k
        if (tokens.getOrNull(p)?.lower == "il") p++
        val t = tokens.getOrNull(p)?.lower ?: return null
        val day = if (t == "primo") 1 else ItWords.digitOrdinal.parse(t) ?: return null
        var m = p + 1
        if (t == "primo" && tokens.getOrNull(m)?.lower == "di") m++
        val month = months[tokens.getOrNull(m)?.lower] ?: return null
        yearToken(tokens.getOrNull(m + 1)?.lower)?.let { y ->
            return dateOrNull(y, month, day)?.let { DateCandidate(it, k..m + 1) }
        }
        return upcomingDate(today, month, day)?.let { DateCandidate(it, k..m) }
    }
}
