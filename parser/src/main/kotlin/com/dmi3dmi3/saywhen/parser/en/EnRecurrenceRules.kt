package com.dmi3dmi3.saywhen.parser.en

import com.dmi3dmi3.saywhen.parser.AMOUNT
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

internal object EnRecurrenceRules : RecurrenceRule {

    private val weekdays = EnWords.weekdays
    private val months = EnWords.months

    private fun unitFreq(w: String?): String? = EnWords.calendarUnits[w]?.let(::freqOf)

    private val singleFreq = mapOf(
        "daily" to "DAILY", "weekly" to "WEEKLY", "monthly" to "MONTHLY", "yearly" to "YEARLY",
        "annually" to "YEARLY",
        "everyday" to "DAILY",
    )

    private val dayHalves = mapOf(
        "morning" to DayHalf.MORNING, "evening" to DayHalf.EVENING, "night" to DayHalf.EVENING,
    )

    private val onDays: Map<String, Set<DayOfWeek>> = mapOf("weekdays" to workdays, "weekends" to weekend)

    private val ends = PeriodEnds(
        weekWords = setOf("week"), monthWords = setOf("month"), yearWords = setOf("year"), monthNames = months,
    )

    private fun monthOrdinal(w: String?): Int? =
        (EnWords.ordinals[w] ?: EnWords.digitOrdinal.parse(w, requireSuffix = true))?.takeIf(::isMonthOrdinal)

    override fun find(tokens: List<Token>, today: LocalDate, used: BooleanArray): RecurrenceCandidate? {
        for (i in tokens.indices) {
            if (used[i]) continue
            matchAt(tokens, i, used)?.let { return withEnd(tokens, today, used, it) }
        }
        return null
    }

    private fun matchAt(tokens: List<Token>, i: Int, used: BooleanArray): RecurrenceCandidate? {
        val t = tokens[i].lower

        if (t == "on" && free(used, i..i + 1, tokens.size)) {
            val second = tokens.getOrNull(i + 1)?.lower
            onDays[second]?.let { return weeklyRecurrence(it, i..i + 1) }
            pluralDay(second)?.let { first ->
                val days = mutableSetOf(first)
                val end = consumeDays(tokens, i + 2, used, days, "and") { w -> pluralDay(w)?.let(::setOf) }
                return weeklyRecurrence(days, i until end)
            }
        }

        singleFreq[t]?.let {
            return RecurrenceCandidate("FREQ=$it", i..i, period = periodOf(it, 1))
        }

        if (t == "once" && tokens.getOrNull(i + 1)?.lower in setOf("a", "an")) {
            unitFreq(tokens.getOrNull(i + 2)?.lower)?.let { f ->
                if (free(used, i..i + 2, tokens.size)) {
                    return RecurrenceCandidate("FREQ=$f", i..i + 2, period = periodOf(f, 1))
                }
            }
        }

        if (t != "every" && t != "each") return null
        val next = tokens.getOrNull(i + 1)?.lower ?: return null

        weekdays[next]?.let { first ->
            if (free(used, i..i + 1, tokens.size)) {
                val days = mutableSetOf(first)
                val end = consumeDays(tokens, i + 2, used, days, "and") { w ->
                    weekdays[w]?.let(::setOf)
                }
                return weeklyRecurrence(days, i until end)
            }
        }

        if (next == "weekend" && free(used, i..i + 1, tokens.size)) {
            return weeklyRecurrence(weekend, i..i + 1)
        }

        monthOrdinal(next)?.let { ord ->
            weekdays[tokens.getOrNull(i + 2)?.lower]?.let { dow ->
                var j = i + 3
                if (tokens.getOrNull(j)?.lower == "of") {
                    if (tokens.getOrNull(j + 1)?.lower == "the") j++
                    if (tokens.getOrNull(j + 1)?.lower == "month" && free(used, i..j + 1, tokens.size)) {
                        return ordinalMonthlyRecurrence(ord, dow, i..j + 1)
                    }
                }
            }
        }

        if (next == "weekday" && free(used, i..i + 1, tokens.size)) {
            return weeklyRecurrence(workdays, i..i + 1)
        }

        dayHalves[next]?.let { half ->
            if (free(used, i..i + 1, tokens.size)) {
                return RecurrenceCandidate("FREQ=DAILY", i..i + 1, period = Period.ofDays(1), dayHalf = half)
            }
        }

        if (next == "other" && free(used, i..i + 2, tokens.size)) {
            val third = tokens.getOrNull(i + 2)?.lower
            unitFreq(third)?.let { f ->
                return RecurrenceCandidate("FREQ=$f;INTERVAL=2", i..i + 2, period = periodOf(f, 2))
            }
            weekdays[third]?.let { dow ->
                val base = weeklyRecurrence(setOf(dow), i..i + 2)
                return base.copy(
                    rrule = base.rrule.replace("FREQ=WEEKLY;", "FREQ=WEEKLY;INTERVAL=2;"),
                    period = Period.ofWeeks(2),
                )
            }
        }

        unitFreq(next)?.let {
            if (free(used, i..i + 1, tokens.size)) {
                return RecurrenceCandidate("FREQ=$it", i..i + 1, period = periodOf(it, 1))
            }
        }

        EnWords.digitOrdinal.parse(next, requireSuffix = true)?.let { day ->
            if (free(used, i..i + 1, tokens.size)) {
                return RecurrenceCandidate(
                    "FREQ=MONTHLY;BYMONTHDAY=$day", i..i + 1,
                    period = Period.ofMonths(1), anchorMonthDay = day,
                )
            }
        }

        val n = next.toIntOrNull() ?: EnWords.cardinals[next] ?: return null
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
            if (n != null && n in AMOUNT && !busy(i + 1) && tokens[i + 1].lower == "times") {
                return main.copy(count = n, extraTokens = listOf(i..i + 1))
            }

            if (t != "until" && t != "till") continue
            var j = i + 1
            if (!busy(j) && tokens[j].lower == "the") j++
            if (busy(j)) continue

            if (tokens[j].lower == "end" && !busy(j + 1) && tokens[j + 1].lower == "of") {
                var k = j + 2
                if (!busy(k) && tokens.getOrNull(k)?.lower == "the") k++
                if (busy(k)) continue
                val date = ends.endOf(tokens[k].lower, today) ?: continue
                return main.copy(untilDate = date, extraTokens = listOf(i..k))
            }

            val first = tokens[j].lower

            val ordDay = dayNumber(first)
            if (ordDay != null && !busy(j + 1) && !busy(j + 2) && tokens[j + 1].lower == "of") {
                val month = months[tokens[j + 2].lower]
                val date = month?.let { upcomingDate(today, it, ordDay) }
                if (date != null) return main.copy(untilDate = date, extraTokens = listOf(i..j + 2))
            }

            if (!busy(j + 1)) {
                val second = tokens[j + 1].lower
                val monthDay = when {
                    months[first] != null -> months[first]!! to dayNumber(second)
                    months[second] != null -> months[second]!! to dayNumber(first)
                    else -> null
                }
                val day = monthDay?.second
                if (monthDay != null && day != null) {
                    upcomingDate(today, monthDay.first, day)?.let { date ->
                        return main.copy(untilDate = date, extraTokens = listOf(i..j + 1))
                    }
                }
            }

            months[first]?.let { month ->
                return main.copy(untilDate = eveOfMonth(today, month), extraTokens = listOf(i..j))
            }
        }
        return main
    }

    private fun pluralDay(w: String?): DayOfWeek? =
        w?.takeIf { it.length > 2 && it.endsWith("s") }
            ?.dropLast(1)?.let { weekdays[it] }

    private fun dayNumber(s: String?): Int? = EnWords.digitOrdinal.parse(s)
}
