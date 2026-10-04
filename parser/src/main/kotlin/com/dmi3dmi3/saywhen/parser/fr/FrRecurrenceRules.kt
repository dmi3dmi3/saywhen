package com.dmi3dmi3.saywhen.parser.fr

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
import java.time.temporal.ChronoUnit

internal object FrRecurrenceRules : RecurrenceRule {

    private val months = FrWords.months

    private fun unitFreq(w: String?): String? = FrWords.calendarUnits[w]?.let(::freqOf)

    private fun weekday(word: String?): DayOfWeek? =
        word?.let { FrWords.weekdays[it] ?: FrWords.weekdays[it.removeSuffix("s")] }

    private fun monthOrdinal(word: String?): Int? =
        word?.let { FrWords.ordinals[it] ?: FrWords.ordinals[it.removeSuffix("s")] }?.takeIf(::isMonthOrdinal)

    private val dayHalves = mapOf(
        "matin" to DayHalf.MORNING, "matins" to DayHalf.MORNING,
        "après-midi" to DayHalf.AFTERNOON, "apres-midi" to DayHalf.AFTERNOON,
        "après-midis" to DayHalf.AFTERNOON, "apres-midis" to DayHalf.AFTERNOON,
        "soir" to DayHalf.EVENING, "soirs" to DayHalf.EVENING,
        "nuit" to DayHalf.EVENING, "nuits" to DayHalf.EVENING,
    )

    private val weekendWords = setOf("week-end", "week-ends", "weekend", "weekends")

    private val ends = PeriodEnds(
        weekWords = setOf("semaine"), monthWords = setOf("mois"),
        yearWords = setOf("année", "annee", "an"), monthNames = months,
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
        val next = tokens.getOrNull(i + 1)?.lower

        if (t == "hebdo") return RecurrenceCandidate("FREQ=WEEKLY", i..i, period = Period.ofWeeks(1))

        if (t == "en" && next == "semaine" && free(used, i..i + 1, tokens.size)) {
            return weeklyRecurrence(workdays, i..i + 1)
        }

        weekdayRange(tokens, i, used)?.let { (days, end) -> return weeklyRecurrence(days, i..end) }

        if (t == "les" && next != null && next.endsWith("s")) {
            weekday(next)?.let { first ->
                if (free(used, i..i + 1, tokens.size)) {
                    val days = mutableSetOf(first)
                    val end = consumeDays(tokens, i + 2, used, days, "et") { weekday(it)?.let(::setOf) }
                    return weeklyRecurrence(days, i until end)
                }
            }
        }

        if (t == "une" && next == "fois" && tokens.getOrNull(i + 2)?.lower == "par") {
            unitFreq(tokens.getOrNull(i + 3)?.lower)?.let { f ->
                if (free(used, i..i + 3, tokens.size)) {
                    return RecurrenceCandidate("FREQ=$f", i..i + 3, period = periodOf(f, 1))
                }
            }
        }

        if ((t == "un" || t == "une") && tokens.getOrNull(i + 2)?.lower == "sur" &&
            tokens.getOrNull(i + 3)?.lower == "deux" && free(used, i..i + 3, tokens.size)
        ) {
            unitFreq(next)?.let { f ->
                return RecurrenceCandidate("FREQ=$f;INTERVAL=2", i..i + 3, period = periodOf(f, 2))
            }
            FrWords.weekdays[next]?.let { dow ->
                val base = weeklyRecurrence(setOf(dow), i..i + 3)
                return base.copy(
                    rrule = base.rrule.replace("FREQ=WEEKLY;", "FREQ=WEEKLY;INTERVAL=2;"),
                    period = Period.ofWeeks(2),
                )
            }
        }

        run {
            val n = t.toIntOrNull()?.takeIf { it in DAY_OF_MONTH } ?: return@run
            if (next == "de" && tokens.getOrNull(i + 2)?.lower == "chaque" &&
                tokens.getOrNull(i + 3)?.lower == "mois" && free(used, i..i + 3, tokens.size)
            ) {
                return monthDay(n, i..i + 3)
            }
        }

        ordinalDay(tokens, i, used)?.let { return it }

        if ((t == "tous" || t == "toutes") && next == "les") {
            val third = tokens.getOrNull(i + 2)?.lower ?: return null
            if (!free(used, i..i + 2, tokens.size)) return null
            if (third == "jours") {
                weekdayRange(tokens, i + 3, used)?.let { (days, end) -> return weeklyRecurrence(days, i..end) }
            }
            unitFreq(third)?.let { f -> return RecurrenceCandidate("FREQ=$f", i..i + 2, period = periodOf(f, 1)) }
            weekday(third)?.let { first ->
                val days = mutableSetOf(first)
                val end = consumeDays(tokens, i + 3, used, days, "et") { weekday(it)?.let(::setOf) }
                return weeklyRecurrence(days, i until end)
            }
            dayHalves[third]?.let { half ->
                return RecurrenceCandidate("FREQ=DAILY", i..i + 2, period = Period.ofDays(1), dayHalf = half)
            }
            if (third in weekendWords) return weeklyRecurrence(weekend, i..i + 2)
            ordinalDay(tokens, i + 2, used)?.let { return it.copy(tokens = i..it.tokens.last) }

            val n = third.toIntOrNull() ?: FrWords.cardinals[third] ?: return null
            if (tokens.getOrNull(i + 3)?.lower == "du" && tokens.getOrNull(i + 4)?.lower == "mois" &&
                n in DAY_OF_MONTH && free(used, i + 3..i + 4, tokens.size)
            ) {
                return monthDay(n, i..i + 4)
            }
            val unit = FrWords.calendarUnits[tokens.getOrNull(i + 3)?.lower] ?: return null
            if (!free(used, i + 3..i + 3, tokens.size)) return null
            if (unit == ChronoUnit.DAYS && n == 15) {
                return RecurrenceCandidate("FREQ=WEEKLY;INTERVAL=2", i..i + 3, period = Period.ofWeeks(2))
            }
            val freq = freqOf(unit)
            if (n !in INTERVAL) return null
            val rrule = if (n > 1) "FREQ=$freq;INTERVAL=$n" else "FREQ=$freq"
            return RecurrenceCandidate(rrule, i..i + 3, period = periodOf(freq, n))
        }

        if (t != "chaque" || next == null) return null

        unitFreq(next)?.let { f ->
            if (free(used, i..i + 1, tokens.size)) return RecurrenceCandidate("FREQ=$f", i..i + 1, period = periodOf(f, 1))
        }
        FrWords.weekdays[next]?.let { first ->
            if (free(used, i..i + 1, tokens.size)) {
                val days = mutableSetOf(first)
                val end = consumeDays(tokens, i + 2, used, days, "et") { weekday(it)?.let(::setOf) }
                return weeklyRecurrence(days, i until end)
            }
        }
        dayHalves[next]?.let { half ->
            if (free(used, i..i + 1, tokens.size)) {
                return RecurrenceCandidate("FREQ=DAILY", i..i + 1, period = Period.ofDays(1), dayHalf = half)
            }
        }
        if (next in weekendWords && free(used, i..i + 1, tokens.size)) return weeklyRecurrence(weekend, i..i + 1)
        ordinalDay(tokens, i + 1, used)?.let { return it.copy(tokens = i..it.tokens.last) }

        return null
    }

    private fun weekdayRange(tokens: List<Token>, k: Int, used: BooleanArray): Pair<Set<DayOfWeek>, Int>? {
        if (tokens.getOrNull(k)?.lower != "du" || tokens.getOrNull(k + 2)?.lower != "au") return null
        val from = weekday(tokens.getOrNull(k + 1)?.lower) ?: return null
        val to = weekday(tokens.getOrNull(k + 3)?.lower) ?: return null
        if (from == to || !free(used, k..k + 3, tokens.size)) return null
        val days = mutableSetOf(from)
        var d: DayOfWeek = from
        while (d != to) { d = d.plus(1); days += d }
        return days to k + 3
    }

    private fun ordinalDay(tokens: List<Token>, k: Int, used: BooleanArray): RecurrenceCandidate? {
        val ord = monthOrdinal(tokens.getOrNull(k)?.lower) ?: return null
        val dow = weekday(tokens.getOrNull(k + 1)?.lower) ?: return null
        if (tokens.getOrNull(k + 2)?.lower != "du" || tokens.getOrNull(k + 3)?.lower != "mois") return null
        if (tokens.getOrNull(k + 4)?.lower == "prochain") return null
        if (!free(used, k..k + 3, tokens.size)) return null
        return ordinalMonthlyRecurrence(ord, dow, k..k + 3)
    }

    private fun monthDay(n: Int, range: IntRange) = RecurrenceCandidate(
        "FREQ=MONTHLY;BYMONTHDAY=$n", range, period = Period.ofMonths(1), anchorMonthDay = n,
    )

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
            if (n != null && n in AMOUNT && !busy(i + 1) && tokens[i + 1].lower == "fois") {
                return main.copy(count = n, extraTokens = listOf(i..i + 1))
            }

            if (t != "jusqu" || busy(i + 1)) continue
            val link = tokens[i + 1].lower
            if (link != "à" && link != "au" && link != "en") continue
            var j = i + 2
            if (busy(j)) continue

            if (tokens[j].lower == "la" && !busy(j + 1) && tokens[j + 1].lower == "fin") j++
            if (tokens[j].lower == "fin" && !busy(j + 1)) {
                var k = j + 1
                when (tokens[k].lower) {
                    "du" -> k++
                    "de" -> {
                        k++
                        if (!busy(k) && tokens.getOrNull(k)?.lower in setOf("la", "l")) k++
                    }
                }
                if (busy(k)) continue
                val date = ends.endOf(tokens[k].lower, today) ?: continue
                return main.copy(untilDate = date, extraTokens = listOf(i..k))
            }

            val day = FrWords.digitOrdinal.parse(tokens[j].lower)
            if (day != null && !busy(j + 1)) {
                val month = months[tokens[j + 1].lower] ?: continue
                val date = upcomingDate(today, month, day) ?: continue
                return main.copy(untilDate = date, extraTokens = listOf(i..j + 1))
            }

            months[tokens[j].lower]?.let { month ->
                return main.copy(untilDate = eveOfMonth(today, month), extraTokens = listOf(i..j))
            }
        }
        return main
    }
}
