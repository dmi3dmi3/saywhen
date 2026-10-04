package com.dmi3dmi3.saywhen.parser.ru

import com.dmi3dmi3.saywhen.parser.AMOUNT
import com.dmi3dmi3.saywhen.parser.Confidence
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

internal object RecurrenceRules : RecurrenceRule {

    private val every = setOf("каждый", "каждая", "каждую", "каждое", "каждые")

    private val adverbFreq = mapOf(
        "ежедневно" to "DAILY", "еженедельно" to "WEEKLY",
        "ежемесячно" to "MONTHLY", "ежегодно" to "YEARLY",
    )

    private val dayHalves = mapOf(
        "утро" to DayHalf.MORNING, "вечер" to DayHalf.EVENING, "ночь" to DayHalf.EVENING,
    )
    private val poHalves = mapOf(
        "утрам" to DayHalf.MORNING, "вечерам" to DayHalf.EVENING, "ночам" to DayHalf.EVENING,
    )

    private val poDays: Map<String, Set<DayOfWeek>> = mapOf(
        "будням" to workdays,
        "выходным" to weekend,
        "понедельникам" to setOf(DayOfWeek.MONDAY),
        "вторникам" to setOf(DayOfWeek.TUESDAY),
        "средам" to setOf(DayOfWeek.WEDNESDAY),
        "четвергам" to setOf(DayOfWeek.THURSDAY),
        "пятницам" to setOf(DayOfWeek.FRIDAY),
        "субботам" to setOf(DayOfWeek.SATURDAY),
        "воскресеньям" to setOf(DayOfWeek.SUNDAY),
    )

    private val ends = PeriodEnds(
        weekWords = setOf("недели"), monthWords = setOf("месяца"), yearWords = setOf("года"),
        monthNames = Words.months,
    )

    override fun find(tokens: List<Token>, today: LocalDate, used: BooleanArray): RecurrenceCandidate? {
        for (i in tokens.indices) {
            if (used[i]) continue
            matchAt(tokens, i, used)?.let { return withEnd(tokens, today, used, it) }
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
            if (n != null && n in AMOUNT && !busy(i + 1) &&
                tokens[i + 1].lower in setOf("раз", "раза")
            ) {
                return main.copy(count = n, extraTokens = listOf(i..i + 1))
            }

            if (t != "до" || busy(i + 1)) continue
            val second = tokens[i + 1].lower

            if (second == "конца" && !busy(i + 2)) {
                val date = ends.endOf(tokens[i + 2].lower, today) ?: continue
                return main.copy(untilDate = date, extraTokens = listOf(i..i + 2))
            }

            val day = second.toIntOrNull()
            if (day != null && day in DAY_OF_MONTH && !busy(i + 2)) {
                val month = Words.months[tokens[i + 2].lower] ?: continue
                val date = upcomingDate(today, month, day) ?: continue
                return main.copy(untilDate = date, extraTokens = listOf(i..i + 2))
            }

            Words.months[second]?.let { month ->
                return main.copy(untilDate = eveOfMonth(today, month), extraTokens = listOf(i..i + 1))
            }
        }
        return main
    }

    private fun matchAt(tokens: List<Token>, i: Int, used: BooleanArray): RecurrenceCandidate? {
        val t = tokens[i].lower

        if (t == "по" && free(used, i..i + 1, tokens.size)) {
            poDays[tokens.getOrNull(i + 1)?.lower]?.let { first ->
                val days = first.toMutableSet()
                val end = consumeDays(tokens, i + 2, used, days, "и") { poDays[it] }
                return weeklyRecurrence(days, i until end)
            }
            poHalves[tokens.getOrNull(i + 1)?.lower]?.let { half ->
                return RecurrenceCandidate("FREQ=DAILY", i..i + 1, period = Period.ofDays(1), dayHalf = half)
            }
        }

        adverbFreq[t]?.let {
            return RecurrenceCandidate("FREQ=$it", i..i, period = periodOf(it, 1))
        }

        if (t == "раз" && tokens.getOrNull(i + 1)?.lower == "в" &&
            (i == 0 || tokens[i - 1].lower.toIntOrNull() == null)
        ) {
            val third = tokens.getOrNull(i + 2)?.lower
            Words.calendarUnits[third]?.let(::freqOf)?.let { f ->
                if (free(used, i..i + 2, tokens.size)) {
                    return RecurrenceCandidate("FREQ=$f", i..i + 2, period = periodOf(f, 1))
                }
            }
            val n = third?.toIntOrNull()
            val f = Words.calendarUnits[tokens.getOrNull(i + 3)?.lower]?.let(::freqOf)
            if (n != null && n in INTERVAL && f != null && free(used, i..i + 3, tokens.size)) {
                val rrule = if (n > 1) "FREQ=$f;INTERVAL=$n" else "FREQ=$f"
                return RecurrenceCandidate(rrule, i..i + 3, period = periodOf(f, n))
            }
        }

        if (t !in every) return null
        val next = tokens.getOrNull(i + 1)?.lower ?: return null

        Words.ordinals[next]?.takeIf(::isMonthOrdinal)?.let { ord ->
            Words.weekdays[tokens.getOrNull(i + 2)?.lower]?.let { dow ->
                if (tokens.getOrNull(i + 3)?.lower == "месяца" && free(used, i..i + 3, tokens.size)) {
                    return ordinalMonthlyRecurrence(ord, dow, i..i + 3)
                }
            }
        }

        Words.weekdays[next]?.let { first ->
            if (free(used, i..i + 1, tokens.size)) {
                val days = mutableSetOf(first)
                val end = consumeDays(tokens, i + 2, used, days, "и") { w ->
                    Words.weekdays[w]?.let(::setOf)
                }
                return weeklyRecurrence(days, i until end)
            }
        }

        dayHalves[next]?.let { half ->
            if (free(used, i..i + 1, tokens.size)) {
                return RecurrenceCandidate("FREQ=DAILY", i..i + 1, period = Period.ofDays(1), dayHalf = half)
            }
        }

        if (next == "выходные" && free(used, i..i + 1, tokens.size)) {
            return weeklyRecurrence(weekend, i..i + 1)
        }

        Words.calendarUnits[next]?.let(::freqOf)?.let {
            if (free(used, i..i + 1, tokens.size)) {
                return RecurrenceCandidate("FREQ=$it", i..i + 1, period = periodOf(it, 1))
            }
        }

        val n = next.toIntOrNull() ?: Words.cardinals[next] ?: return null
        val third = tokens.getOrNull(i + 2)?.lower

        if (third in setOf("число", "числа") && n in DAY_OF_MONTH && free(used, i..i + 2, tokens.size)) {
            return RecurrenceCandidate(
                "FREQ=MONTHLY;BYMONTHDAY=$n", i..i + 2,
                period = Period.ofMonths(1), anchorMonthDay = n,
            )
        }

        val freq = Words.calendarUnits[third]?.let(::freqOf)
        if (freq != null && n in INTERVAL && free(used, i..i + 2, tokens.size)) {
            val rrule = if (n > 1) "FREQ=$freq;INTERVAL=$n" else "FREQ=$freq"
            return RecurrenceCandidate(rrule, i..i + 2, period = periodOf(freq, n))
        }

        if (t == "каждое" && n in DAY_OF_MONTH && free(used, i..i + 1, tokens.size)) {
            return RecurrenceCandidate(
                "FREQ=MONTHLY;BYMONTHDAY=$n", i..i + 1,
                period = Period.ofMonths(1), anchorMonthDay = n,
            )
        }

        return null
    }
}
