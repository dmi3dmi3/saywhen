package com.dmi3dmi3.saywhen.parser.it

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
import java.time.LocalDate
import java.time.Period

internal object ItRecurrenceRules : RecurrenceRule {

    private val weekdays = ItWords.weekdays
    private val months = ItWords.months

    private fun unitFreq(w: String?): String? = ItWords.calendarUnits[w]?.let(::freqOf)

    private fun monthOrdinal(w: String?): Int? = ItWords.ordinals[w]?.takeIf(::isMonthOrdinal)

    private val dayHalves = mapOf(
        "mattina" to DayHalf.MORNING, "sera" to DayHalf.EVENING, "notte" to DayHalf.EVENING,
    )

    private val ends = PeriodEnds(
        weekWords = setOf("settimana"), monthWords = setOf("mese"), yearWords = setOf("anno"), monthNames = months,
    )

    override fun find(tokens: List<Token>, today: LocalDate, used: BooleanArray): RecurrenceCandidate? {
        for (i in tokens.indices) {
            if (used[i]) continue
            matchAt(tokens, i, used)?.let { return withEnd(tokens, today, used, it) }
        }
        return null
    }

    private fun matchAt(tokens: List<Token>, i: Int, used: BooleanArray): RecurrenceCandidate? {
        val t = tokens[i].lower

        if (t == "giorni" && tokens.getOrNull(i + 1)?.lower == "feriali" &&
            free(used, i..i + 1, tokens.size)
        ) {
            return weeklyRecurrence(workdays, i..i + 1)
        }

        if (t == "una" && tokens.getOrNull(i + 1)?.lower == "volta") {
            var j = i + 2
            if (tokens.getOrNull(j)?.lower in setOf("a", "al", "alla", "all")) j++
            unitFreq(tokens.getOrNull(j)?.lower)?.let { f ->
                if (free(used, i..j, tokens.size)) {
                    return RecurrenceCandidate("FREQ=$f", i..j, period = periodOf(f, 1))
                }
            }
        }

        run {
            val n = t.toIntOrNull()?.takeIf { it in DAY_OF_MONTH } ?: return@run
            if (tokens.getOrNull(i + 1)?.lower == "di" && tokens.getOrNull(i + 2)?.lower == "ogni" &&
                tokens.getOrNull(i + 3)?.lower == "mese" && free(used, i..i + 3, tokens.size)
            ) {
                return RecurrenceCandidate(
                    "FREQ=MONTHLY;BYMONTHDAY=$n", i..i + 3,
                    period = Period.ofMonths(1), anchorMonthDay = n,
                )
            }
        }

        monthOrdinal(t)?.let { ord ->
            weekdays[tokens.getOrNull(i + 1)?.lower]?.let { dow ->
                if (tokens.getOrNull(i + 2)?.lower == "del" && tokens.getOrNull(i + 3)?.lower == "mese" &&
                    free(used, i..i + 3, tokens.size)
                ) {
                    return ordinalMonthlyRecurrence(ord, dow, i..i + 3)
                }
            }
        }

        if (t != "ogni") return null
        val next = tokens.getOrNull(i + 1)?.lower ?: return null

        monthOrdinal(next)?.let { ord ->
            weekdays[tokens.getOrNull(i + 2)?.lower]?.let { dow ->
                if (tokens.getOrNull(i + 3)?.lower == "del" && tokens.getOrNull(i + 4)?.lower == "mese" &&
                    free(used, i..i + 4, tokens.size)
                ) {
                    return ordinalMonthlyRecurrence(ord, dow, i..i + 4)
                }
            }
        }

        weekdays[next]?.let { first ->
            if (free(used, i..i + 1, tokens.size)) {
                val days = mutableSetOf(first)
                val end = consumeDays(tokens, i + 2, used, days, "e") { weekdays[it]?.let(::setOf) }
                return weeklyRecurrence(days, i until end)
            }
        }

        dayHalves[next]?.let { half ->
            if (free(used, i..i + 1, tokens.size)) {
                return RecurrenceCandidate("FREQ=DAILY", i..i + 1, period = Period.ofDays(1), dayHalf = half)
            }
        }

        if ((next == "weekend" || next == "finesettimana") && free(used, i..i + 1, tokens.size)) {
            return weeklyRecurrence(weekend, i..i + 1)
        }
        if (next == "fine" && tokens.getOrNull(i + 2)?.lower == "settimana" &&
            free(used, i..i + 2, tokens.size)
        ) {
            return weeklyRecurrence(weekend, i..i + 2)
        }

        unitFreq(next)?.let {
            if (free(used, i..i + 1, tokens.size)) {
                return RecurrenceCandidate("FREQ=$it", i..i + 1, period = periodOf(it, 1))
            }
        }

        val n = next.toIntOrNull() ?: ItWords.cardinals[next] ?: return null

        if (tokens.getOrNull(i + 2)?.lower == "del" && tokens.getOrNull(i + 3)?.lower == "mese" &&
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
            if (n != null && n in AMOUNT && !busy(i + 1) && tokens[i + 1].lower == "volte") {
                return main.copy(count = n, extraTokens = listOf(i..i + 1))
            }

            if (t != "fino" && t != "entro") continue
            var j = i + 1
            while (!busy(j) && tokens.getOrNull(j)?.lower in setOf("a", "al", "alla", "il", "la")) j++
            if (busy(j)) continue
            val first = tokens[j].lower

            if (first == "fine" && !busy(j + 1)) {
                var k = j + 1
                if (tokens.getOrNull(k)?.lower in setOf("di", "del", "della", "dell") && !busy(k)) k++
                if (busy(k)) continue
                val date = ends.endOf(tokens[k].lower, today) ?: continue
                return main.copy(untilDate = date, extraTokens = listOf(i..k))
            }

            val day = first.toIntOrNull()
            if (day != null && day in DAY_OF_MONTH && !busy(j + 1)) {
                val month = months[tokens[j + 1].lower] ?: continue
                val date = upcomingDate(today, month, day) ?: continue
                return main.copy(untilDate = date, extraTokens = listOf(i..j + 1))
            }

            months[first]?.let { month ->
                return main.copy(untilDate = eveOfMonth(today, month), extraTokens = listOf(i..j))
            }
        }
        return main
    }
}
