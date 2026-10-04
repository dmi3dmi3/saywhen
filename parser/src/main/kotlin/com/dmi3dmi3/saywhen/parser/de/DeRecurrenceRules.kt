package com.dmi3dmi3.saywhen.parser.de

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

internal object DeRecurrenceRules : RecurrenceRule {

    private val every = setOf("jeden", "jede", "jedes")

    private val weekdays = DeWords.weekdays
    private val months = DeWords.months

    private fun unitFreq(w: String?): String? = DeWords.calendarUnits[w]?.let(::freqOf)

    private fun monthOrdinal(w: String?): Int? = DeWords.ordinals[w]?.takeIf(::isMonthOrdinal)

    private val adverbFreq = mapOf(
        "täglich" to "DAILY", "taeglich" to "DAILY",
        "wöchentlich" to "WEEKLY", "woechentlich" to "WEEKLY",
        "monatlich" to "MONTHLY",
        "jährlich" to "YEARLY", "jaehrlich" to "YEARLY",
    )

    private val habitualDays = mapOf(
        "montags" to DayOfWeek.MONDAY, "dienstags" to DayOfWeek.TUESDAY,
        "mittwochs" to DayOfWeek.WEDNESDAY, "donnerstags" to DayOfWeek.THURSDAY,
        "freitags" to DayOfWeek.FRIDAY,
        "samstags" to DayOfWeek.SATURDAY, "sonnabends" to DayOfWeek.SATURDAY,
        "sonntags" to DayOfWeek.SUNDAY,
    )

    private val dayHalves = mapOf(
        "morgen" to DayHalf.MORNING, "vormittag" to DayHalf.MORNING,
        "abend" to DayHalf.EVENING, "nachmittag" to DayHalf.AFTERNOON, "nacht" to DayHalf.EVENING,
    )

    private val ends = PeriodEnds(
        weekWords = setOf("woche"), monthWords = setOf("monats", "monat"), yearWords = setOf("jahres", "jahr"),
        monthNames = months,
    )

    private val gluedMal = Regex("""(\d{1,3})-?mal""")

    override fun find(tokens: List<Token>, today: LocalDate, used: BooleanArray): RecurrenceCandidate? {
        for (i in tokens.indices) {
            if (used[i]) continue
            matchAt(tokens, i, used)?.let { return withEnd(tokens, today, used, it) }
        }
        return null
    }

    private fun matchAt(tokens: List<Token>, i: Int, used: BooleanArray): RecurrenceCandidate? {
        val t = tokens[i].lower

        adverbFreq[t]?.let {
            return RecurrenceCandidate("FREQ=$it", i..i, period = periodOf(it, 1))
        }

        habitualDays[t]?.let { first ->
            val days = mutableSetOf(first)
            val end = consumeDays(tokens, i + 1, used, days, "und") { habitualDays[it]?.let(::setOf) }
            return weeklyRecurrence(days, i until end)
        }

        if (t == "werktags" || t == "wochentags") {
            return weeklyRecurrence(workdays, i..i)
        }

        Regex("""(\d{1,3})-?(?:tägig|taegig)""").matchEntire(t)?.let { m ->
            val n = m.groupValues[1].toInt()
            if (n in 2..99) {
                return RecurrenceCandidate("FREQ=DAILY;INTERVAL=$n", i..i, period = Period.ofDays(n))
            }
        }

        if (t == "einmal") {
            var j = i + 1
            if (tokens.getOrNull(j)?.lower in setOf("pro", "die", "im", "am", "der")) j++
            unitFreq(tokens.getOrNull(j)?.lower)?.let { f ->
                if (free(used, i..j, tokens.size)) {
                    return RecurrenceCandidate("FREQ=$f", i..j, period = periodOf(f, 1))
                }
            }
        }

        run {
            val n = t.toIntOrNull()?.takeIf { it in DAY_OF_MONTH } ?: return@run
            if (tokens.getOrNull(i + 1)?.lower == "jedes" &&
                tokens.getOrNull(i + 2)?.lower in setOf("monats", "monat") &&
                free(used, i..i + 2, tokens.size)
            ) {
                return RecurrenceCandidate(
                    "FREQ=MONTHLY;BYMONTHDAY=$n", i..i + 2,
                    period = Period.ofMonths(1), anchorMonthDay = n,
                )
            }
        }

        if (t == "alle") {
            val next = tokens.getOrNull(i + 1)?.lower ?: return null
            val n = next.toIntOrNull() ?: DeWords.cardinals[next] ?: return null
            val freq = unitFreq(tokens.getOrNull(i + 2)?.lower) ?: return null
            if (n in INTERVAL && free(used, i..i + 2, tokens.size)) {
                val rrule = if (n > 1) "FREQ=$freq;INTERVAL=$n" else "FREQ=$freq"
                return RecurrenceCandidate(rrule, i..i + 2, period = periodOf(freq, n))
            }
        }

        if (t !in every) return null
        val next = tokens.getOrNull(i + 1)?.lower ?: return null

        monthOrdinal(next)?.let { ord ->
            weekdays[tokens.getOrNull(i + 2)?.lower]?.let { dow ->
                if (tokens.getOrNull(i + 3)?.lower == "im" &&
                    tokens.getOrNull(i + 4)?.lower == "monat" && free(used, i..i + 4, tokens.size)
                ) {
                    return ordinalMonthlyRecurrence(ord, dow, i..i + 4)
                }
            }
        }

        if (next in setOf("zweite", "zweiten") && tokens.getOrNull(i + 2)?.lower == "woche" &&
            free(used, i..i + 2, tokens.size)
        ) {
            return RecurrenceCandidate(
                "FREQ=WEEKLY;INTERVAL=2", i..i + 2, period = Period.ofWeeks(2),
            )
        }

        weekdays[next]?.let { first ->
            if (free(used, i..i + 1, tokens.size)) {
                val days = mutableSetOf(first)
                val end = consumeDays(tokens, i + 2, used, days, "und") { w ->
                    weekdays[w]?.let(::setOf)
                }
                return weeklyRecurrence(days, i until end)
            }
        }

        dayHalves[next]?.let { half ->
            if (free(used, i..i + 1, tokens.size)) {
                return RecurrenceCandidate("FREQ=DAILY", i..i + 1, period = Period.ofDays(1), dayHalf = half)
            }
        }

        if (next == "wochenende" && free(used, i..i + 1, tokens.size)) {
            return weeklyRecurrence(weekend, i..i + 1)
        }

        unitFreq(next)?.let {
            if (free(used, i..i + 1, tokens.size)) {
                return RecurrenceCandidate("FREQ=$it", i..i + 1, period = periodOf(it, 1))
            }
        }

        DeWords.digitOrdinal.parse(next, requireSuffix = true)?.let { day ->
            if (free(used, i..i + 1, tokens.size)) {
                return RecurrenceCandidate(
                    "FREQ=MONTHLY;BYMONTHDAY=$day", i..i + 1,
                    period = Period.ofMonths(1), anchorMonthDay = day,
                )
            }
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

            gluedMal.matchEntire(t)?.let { m ->
                val n = m.groupValues[1].toInt()
                if (n in AMOUNT) return main.copy(count = n, extraTokens = listOf(i..i))
            }
            val n = t.toIntOrNull()
            if (n != null && n in AMOUNT && !busy(i + 1) && tokens[i + 1].lower == "mal") {
                return main.copy(count = n, extraTokens = listOf(i..i + 1))
            }

            if (t != "bis") continue
            var j = i + 1
            if (!busy(j) && tokens.getOrNull(j)?.lower == "zum") j++
            if (busy(j)) continue
            val first = tokens[j].lower

            if (first == "ende" && !busy(j + 1)) {
                var k = j + 1
                if (tokens.getOrNull(k)?.lower in setOf("des", "der") && !busy(k)) k++
                if (busy(k)) continue
                val date = ends.endOf(tokens[k].lower, today) ?: continue
                return main.copy(untilDate = date, extraTokens = listOf(i..k))
            }

            val day = DeWords.digitOrdinal.parse(first)
            if (day != null && !busy(j + 1)) {
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
