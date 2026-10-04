package com.dmi3dmi3.saywhen.parser.fr

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
import java.time.temporal.ChronoUnit

internal object FrDateRules : DateRule {

    private val relative = mapOf("demain" to 1L, "après-demain" to 2L, "apres-demain" to 2L)

    private val dayHalves = mapOf(
        "matin" to DayHalf.MORNING, "matinée" to DayHalf.MORNING, "matinee" to DayHalf.MORNING,
        "après-midi" to DayHalf.AFTERNOON, "apres-midi" to DayHalf.AFTERNOON, "aprem" to DayHalf.AFTERNOON,
        "soir" to DayHalf.EVENING, "soirée" to DayHalf.EVENING, "soiree" to DayHalf.EVENING,
        "nuit" to DayHalf.EVENING,
    )

    private val months = FrWords.months
    private val weekdays = FrWords.weekdays

    private val nextWeekAdj = setOf("prochaine", "suivante")
    private val after = setOf("après", "apres")

    override fun findAll(tokens: List<Token>, now: ZonedDateTime, used: BooleanArray): List<DateCandidate> {
        val today = now.toLocalDate()
        return dateCandidates(tokens, used) { i -> matchAt(tokens, i, today) }
    }

    private fun matchAt(tokens: List<Token>, i: Int, today: LocalDate): DateCandidate? {
        val t = tokens[i].lower

        matchRange(tokens, i, today)?.let { return it }

        if (t == "aujourd" && tokens.getOrNull(i + 1)?.lower == "hui") return withDayHalf(today, i, i + 1, tokens)

        relative[t]?.let { shift -> return withDayHalf(today.plusDays(shift), i, i, tokens) }

        if (t == "ce" || t == "cet" || t == "cette") {
            dayHalfAt(tokens, i + 1)?.let { (half, end) -> return DateCandidate(today, i..end, dayHalf = half) }
        }

        matchWeekday(tokens, i, today)?.let { return it }

        calendarAt(tokens, i, today)?.let { return it }

        if (t == "dans") {
            val next = tokens.getOrNull(i + 1)?.lower ?: return null
            val amount = next.toLongOrNull() ?: FrWords.cardinals[next]?.toLong() ?: return null
            if (amount !in OFFSET_DAYS) return null
            val unit = FrWords.calendarUnits[tokens.getOrNull(i + 2)?.lower] ?: return null
            val n = if (unit == ChronoUnit.DAYS && amount == 15L) 14L else amount
            return DateCandidate(today.plus(n, unit), i..i + 2)
        }

        return null
    }

    private fun matchRange(tokens: List<Token>, i: Int, today: LocalDate): DateCandidate? {
        val t = tokens[i].lower

        dayPair(t)?.let { (d1, d2) ->
            val month = months[tokens.getOrNull(i + 1)?.lower] ?: return@let
            return dateRangeCandidate(today, tokens, d1, month, d2, month, i..i + 1)
        }

        val entre = t == "entre"
        var j = i
        if (t == "du" || entre) j++
        if (entre && tokens.getOrNull(j)?.lower == "le") j++
        if (tokens.getOrNull(j)?.lower in weekdays) j++
        val d1 = dayAt(tokens.getOrNull(j)?.lower) ?: return null
        var k = j + 1
        val startMonth = months[tokens.getOrNull(k)?.lower]
        if (startMonth != null) k++
        when {
            tokens.getOrNull(k)?.lower == "au" -> k++
            tokens.getOrNull(k)?.lower == "jusqu" && tokens.getOrNull(k + 1)?.lower == "au" -> k += 2
            entre && tokens.getOrNull(k)?.lower == "et" -> {
                k++
                if (tokens.getOrNull(k)?.lower == "le") k++
            }
            startMonth != null && tokens.getOrNull(k + 1)?.lower in months -> {}
            else -> return null
        }
        if (tokens.getOrNull(k)?.lower in weekdays) k++
        val d2 = dayAt(tokens.getOrNull(k)?.lower) ?: return null
        var end = k
        val endMonth = months[tokens.getOrNull(end + 1)?.lower]
        if (endMonth != null) end++
        if (startMonth == null && endMonth == null) return null
        return dateRangeCandidate(today, tokens, d1, startMonth ?: endMonth!!, d2, endMonth ?: startMonth!!, i..end)
    }

    private fun matchWeekday(tokens: List<Token>, i: Int, today: LocalDate): DateCandidate? {
        var j = i
        if (tokens[j].lower == "le") j++
        if (tokens.getOrNull(j)?.lower == "ce") j++
        val dow = weekdays[tokens.getOrNull(j)?.lower] ?: return null
        if (tokens.getOrNull(j - 1)?.lower in FrWords.ordinals) return null

        calendarAt(tokens, j + 1, today)?.let { d -> return d.copy(tokens = i..d.tokens.last) }

        var end = j
        var nextWeek = false
        var afterToday = false
        when (tokens.getOrNull(end + 1)?.lower) {
            "prochain" -> { afterToday = true; end++ }
            "suivant" -> { nextWeek = true; end++ }
            "d" -> if (tokens.getOrNull(end + 2)?.lower in after) { nextWeek = true; end += 2 }
        }
        run {
            var q = end + 1
            if (tokens.getOrNull(q)?.lower == "de") q++
            when (tokens.getOrNull(q)?.lower) {
                "la" -> {
                    if (tokens.getOrNull(q + 1)?.lower != "semaine") return@run
                    val adj = tokens.getOrNull(q + 2)?.lower
                    when {
                        adj in nextWeekAdj -> { nextWeek = true; end = q + 2 }
                        adj == "d" && tokens.getOrNull(q + 3)?.lower in after -> { nextWeek = true; end = q + 3 }
                    }
                }
                "cette" -> if (tokens.getOrNull(q + 1)?.lower == "semaine") end = q + 1
            }
        }
        var plusWeeks = 0L
        if (tokens.getOrNull(end + 1)?.lower == "en") {
            when (tokens.getOrNull(end + 2)?.lower) {
                "huit", "8" -> { plusWeeks = 1; end += 2 }
                "quinze", "15" -> { plusWeeks = 2; end += 2 }
            }
        }
        var date = weekdayDate(today, dow, nextWeek)
        if (afterToday && date == today) date = date.plusWeeks(1)
        return withDayHalf(date.plusWeeks(plusWeeks), i, end, tokens, fromWeekday = true)
    }

    private fun calendarAt(tokens: List<Token>, k: Int, today: LocalDate): DateCandidate? {
        var p = k
        if (tokens.getOrNull(p)?.lower == "le") p++
        val day = dayAt(tokens.getOrNull(p)?.lower) ?: return null
        val month = months[tokens.getOrNull(p + 1)?.lower] ?: return null
        yearToken(tokens.getOrNull(p + 2)?.lower)?.let { year ->
            return dateOrNull(year, month, day)?.let { withDayHalf(it, k, p + 2, tokens) }
        }
        return upcomingDate(today, month, day)?.let { withDayHalf(it, k, p + 1, tokens) }
    }

    private fun dayAt(word: String?): Int? =
        FrWords.digitOrdinal.parse(word) ?: FrWords.ordinals[word]?.takeIf { it == 1 }

    private fun withDayHalf(
        date: LocalDate,
        from: Int,
        end: Int,
        tokens: List<Token>,
        fromWeekday: Boolean = false,
    ): DateCandidate {
        dayHalfAt(tokens, end + 1)?.let { (half, last) ->
            return DateCandidate(date, from..last, fromWeekday = fromWeekday, dayHalf = half)
        }
        return DateCandidate(date, from..end, fromWeekday = fromWeekday)
    }

    private fun dayHalfAt(tokens: List<Token>, k: Int): Pair<DayHalf, Int>? {
        var j = k
        when (tokens.getOrNull(j)?.lower) {
            "en", "le", "l" -> j++
            "dans" -> {
                if (tokens.getOrNull(j + 1)?.lower != "la" && tokens.getOrNull(j + 1)?.lower != "l") return null
                j += 2
            }
        }
        val half = dayHalves[tokens.getOrNull(j)?.lower] ?: return null
        return half to j
    }
}
