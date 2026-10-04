package com.dmi3dmi3.saywhen.parser.es

import com.dmi3dmi3.saywhen.parser.AMOUNT
import com.dmi3dmi3.saywhen.parser.DAY_OF_MONTH
import com.dmi3dmi3.saywhen.parser.DayHalf
import com.dmi3dmi3.saywhen.parser.INTERVAL
import com.dmi3dmi3.saywhen.parser.PeriodEnds
import com.dmi3dmi3.saywhen.parser.RecurrenceCandidate
import com.dmi3dmi3.saywhen.parser.RecurrenceRule
import com.dmi3dmi3.saywhen.parser.Token
import com.dmi3dmi3.saywhen.parser.consumeDays
import com.dmi3dmi3.saywhen.parser.eveOfMonth
import com.dmi3dmi3.saywhen.parser.free
import com.dmi3dmi3.saywhen.parser.freqOf
import com.dmi3dmi3.saywhen.parser.isMonthOrdinal
import com.dmi3dmi3.saywhen.parser.ordinalMonthlyRecurrence
import com.dmi3dmi3.saywhen.parser.periodOf
import com.dmi3dmi3.saywhen.parser.upcomingDate
import com.dmi3dmi3.saywhen.parser.weekend
import com.dmi3dmi3.saywhen.parser.weeklyRecurrence
import com.dmi3dmi3.saywhen.parser.workdays
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Period

internal object EsRecurrenceRules : RecurrenceRule {

    private val months = EsWords.months

    private fun unitFreq(w: String?): String? = EsWords.calendarUnits[w]?.let(::freqOf)

    private fun monthOrdinal(w: String?): Int? = EsWords.ordinals[w]?.takeIf(::isMonthOrdinal)

    private val dayHalves = mapOf(
        "mañana" to DayHalf.MORNING, "manana" to DayHalf.MORNING,
        "tarde" to DayHalf.AFTERNOON, "noche" to DayHalf.EVENING,
    )

    private val ends = PeriodEnds(
        weekWords = setOf("semana"), monthWords = setOf("mes"), yearWords = setOf("año", "ano"), monthNames = months,
    )

    override fun find(tokens: List<Token>, today: LocalDate, used: BooleanArray): RecurrenceCandidate? {
        for (i in tokens.indices) {
            if (used[i]) continue
            matchAt(tokens, i, used)?.let { return withEnd(tokens, today, used, it) }
        }
        return null
    }

    private fun weekday(word: String?): DayOfWeek? =
        word?.let { EsWords.weekdays[it] ?: EsWords.weekdays[it.removeSuffix("s")] }

    private fun matchAt(tokens: List<Token>, i: Int, used: BooleanArray): RecurrenceCandidate? {
        val t = tokens[i].lower

        if (t == "entre" && tokens.getOrNull(i + 1)?.lower == "semana" &&
            free(used, i..i + 1, tokens.size)
        ) {
            return weeklyRecurrence(workdays, i..i + 1)
        }

        if (t == "fines" && tokens.getOrNull(i + 1)?.lower == "de" &&
            tokens.getOrNull(i + 2)?.lower == "semana" && free(used, i..i + 2, tokens.size)
        ) {
            return weeklyRecurrence(weekend, i..i + 2)
        }

        if (t == "los") {
            weekday(tokens.getOrNull(i + 1)?.lower)?.let { first ->
                if (free(used, i..i + 1, tokens.size)) {
                    val days = mutableSetOf(first)
                    val end = consumeDays(tokens, i + 2, used, days, "y") { weekday(it)?.let(::setOf) }
                    return weeklyRecurrence(days, i until end)
                }
            }
            if (tokens.getOrNull(i + 1)?.lower in setOf("finde", "findes") &&
                free(used, i..i + 1, tokens.size)
            ) {
                return weeklyRecurrence(weekend, i..i + 1)
            }
        }

        if (t == "una" && tokens.getOrNull(i + 1)?.lower == "vez") {
            var j = i + 2
            when (tokens.getOrNull(j)?.lower) {
                "a" -> {
                    j++
                    if (tokens.getOrNull(j)?.lower in setOf("la", "el")) j++
                }
                "al" -> j++
                else -> {}
            }
            unitFreq(tokens.getOrNull(j)?.lower)?.let { f ->
                if (free(used, i..j, tokens.size)) {
                    return RecurrenceCandidate("FREQ=$f", i..j, period = periodOf(f, 1))
                }
            }
        }

        run {
            val n = t.toIntOrNull()?.takeIf { it in DAY_OF_MONTH } ?: return@run
            if (tokens.getOrNull(i + 1)?.lower == "de" && tokens.getOrNull(i + 2)?.lower == "cada" &&
                tokens.getOrNull(i + 3)?.lower == "mes" && free(used, i..i + 3, tokens.size)
            ) {
                return RecurrenceCandidate(
                    "FREQ=MONTHLY;BYMONTHDAY=$n", i..i + 3,
                    period = Period.ofMonths(1), anchorMonthDay = n,
                )
            }
        }

        monthOrdinal(t)?.let { ord ->
            weekday(tokens.getOrNull(i + 1)?.lower)?.let { dow ->
                if (tokens.getOrNull(i + 2)?.lower == "del" && tokens.getOrNull(i + 3)?.lower == "mes" &&
                    free(used, i..i + 3, tokens.size)
                ) {
                    return ordinalMonthlyRecurrence(ord, dow, i..i + 3)
                }
            }
        }

        if ((t == "todos" || t == "todas") &&
            tokens.getOrNull(i + 1)?.lower in setOf("los", "las")
        ) {
            val third = tokens.getOrNull(i + 2)?.lower
            weekday(third)?.let { first ->
                if (free(used, i..i + 2, tokens.size)) {
                    val days = mutableSetOf(first)
                    val end = consumeDays(tokens, i + 3, used, days, "y") { weekday(it)?.let(::setOf) }
                    return weeklyRecurrence(days, i until end)
                }
            }
            unitFreq(third)?.let { f ->
                if (free(used, i..i + 2, tokens.size)) {
                    return RecurrenceCandidate("FREQ=$f", i..i + 2, period = periodOf(f, 1))
                }
            }
            if (third == "fines" && tokens.getOrNull(i + 3)?.lower == "de" &&
                tokens.getOrNull(i + 4)?.lower == "semana" && free(used, i..i + 4, tokens.size)
            ) {
                return weeklyRecurrence(weekend, i..i + 4)
            }
        }

        if (t != "cada") return null
        val next = tokens.getOrNull(i + 1)?.lower ?: return null

        monthOrdinal(next)?.let { ord ->
            weekday(tokens.getOrNull(i + 2)?.lower)?.let { dow ->
                if (tokens.getOrNull(i + 3)?.lower == "del" && tokens.getOrNull(i + 4)?.lower == "mes" &&
                    free(used, i..i + 4, tokens.size)
                ) {
                    return ordinalMonthlyRecurrence(ord, dow, i..i + 4)
                }
            }
        }

        weekday(next)?.let { first ->
            if (free(used, i..i + 1, tokens.size)) {
                val days = mutableSetOf(first)
                val end = consumeDays(tokens, i + 2, used, days, "y") { weekday(it)?.let(::setOf) }
                return weeklyRecurrence(days, i until end)
            }
        }

        dayHalves[next]?.let { half ->
            if (free(used, i..i + 1, tokens.size)) {
                return RecurrenceCandidate("FREQ=DAILY", i..i + 1, period = Period.ofDays(1), dayHalf = half)
            }
        }

        if (next == "fin" && tokens.getOrNull(i + 2)?.lower == "de" &&
            tokens.getOrNull(i + 3)?.lower == "semana" && free(used, i..i + 3, tokens.size)
        ) {
            return weeklyRecurrence(weekend, i..i + 3)
        }
        if (next == "finde" && free(used, i..i + 1, tokens.size)) {
            return weeklyRecurrence(weekend, i..i + 1)
        }

        unitFreq(next)?.let {
            if (free(used, i..i + 1, tokens.size)) {
                return RecurrenceCandidate("FREQ=$it", i..i + 1, period = periodOf(it, 1))
            }
        }

        if (next == "quince" && tokens.getOrNull(i + 2)?.lower in setOf("días", "dias") &&
            free(used, i..i + 2, tokens.size)
        ) {
            return RecurrenceCandidate("FREQ=WEEKLY;INTERVAL=2", i..i + 2, period = Period.ofWeeks(2))
        }

        val n = next.toIntOrNull() ?: EsWords.cardinals[next] ?: return null

        if (tokens.getOrNull(i + 2)?.lower == "del" && tokens.getOrNull(i + 3)?.lower == "mes" &&
            n in DAY_OF_MONTH && free(used, i..i + 3, tokens.size)
        ) {
            return RecurrenceCandidate(
                "FREQ=MONTHLY;BYMONTHDAY=$n", i..i + 3,
                period = Period.ofMonths(1), anchorMonthDay = n,
            )
        }

        val freq = unitFreq(tokens.getOrNull(i + 2)?.lower)
        if (freq != null && n in INTERVAL && free(used, i..i + 2, tokens.size)) {
            val rrule = if (n > 1) "FREQ=$freq;INTERVAL=$n" else "FREQ=$freq"
            return RecurrenceCandidate(rrule, i..i + 2, period = periodOf(freq, n))
        }

        return null
    }

    private fun withEnd(
        tokens: List<Token>,
        today: LocalDate,
        used: BooleanArray,
        main: RecurrenceCandidate,
    ): RecurrenceCandidate {
        fun busy(j: Int) = j !in tokens.indices || used[j] || j in main.tokens
        for (i in tokens.indices) {
            if (busy(i)) continue
            val t = tokens[i].lower

            val n = t.toIntOrNull()
            if (n != null && n in AMOUNT && !busy(i + 1) && tokens[i + 1].lower == "veces") {
                return main.copy(count = n, extraTokens = listOf(i..i + 1))
            }

            if (t != "hasta") continue
            var j = i + 1
            while (!busy(j) && tokens.getOrNull(j)?.lower in setOf("el", "la", "los", "las")) j++
            if (busy(j)) continue
            val first = tokens[j].lower

            if (first in setOf("fin", "finales") && !busy(j + 1) &&
                tokens.getOrNull(j + 1)?.lower == "de"
            ) {
                var k = j + 2
                if (tokens.getOrNull(k)?.lower in setOf("la", "el") && !busy(k)) k++
                if (busy(k)) continue
                val date = ends.endOf(tokens[k].lower, today) ?: continue
                return main.copy(untilDate = date, extraTokens = listOf(i..k))
            }

            val day = first.toIntOrNull()
            if (day != null && day in DAY_OF_MONTH && !busy(j + 1)) {
                var m = j + 1
                if (tokens.getOrNull(m)?.lower == "de" && !busy(m)) m++
                val month = months[tokens.getOrNull(m)?.lower ?: continue] ?: continue
                if (busy(m)) continue
                val date = upcomingDate(today, month, day) ?: continue
                return main.copy(untilDate = date, extraTokens = listOf(i..m))
            }

            months[first]?.let { month ->
                return main.copy(untilDate = eveOfMonth(today, month), extraTokens = listOf(i..j))
            }
        }
        return main
    }
}
