package com.dmi3dmi3.saywhen.parser.en

import com.dmi3dmi3.saywhen.parser.DayHalf
import com.dmi3dmi3.saywhen.parser.RecurrenceCandidate
import com.dmi3dmi3.saywhen.parser.Token
import com.dmi3dmi3.saywhen.parser.ordinalMonthlyRecurrence
import com.dmi3dmi3.saywhen.parser.weeklyRecurrence
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Period
import java.time.temporal.TemporalAdjusters

internal object EnRecurrenceRules {

    private val unitFreq = mapOf(
        "day" to "DAILY", "days" to "DAILY",
        "week" to "WEEKLY", "weeks" to "WEEKLY",
        "month" to "MONTHLY", "months" to "MONTHLY",
        "year" to "YEARLY", "years" to "YEARLY",
    )

    private val singleFreq = mapOf(
        "daily" to "DAILY", "weekly" to "WEEKLY", "monthly" to "MONTHLY", "yearly" to "YEARLY",
        "annually" to "YEARLY",
        // разговорное «everyday» вместо «every day» (живой кейс из ревью F-Droid)
        "everyday" to "DAILY",
    )

    // "every morning/evening/night" — ежедневный повтор + половина суток для часа
    private val dayHalves = mapOf(
        "morning" to DayHalf.MORNING, "evening" to DayHalf.EVENING, "night" to DayHalf.EVENING,
    )

    // "every first monday of the month" — порядковые; -1 — последний
    private val ordinals = mapOf(
        "first" to 1, "1st" to 1, "second" to 2, "2nd" to 2, "third" to 3, "3rd" to 3,
        "fourth" to 4, "4th" to 4, "fifth" to 5, "5th" to 5, "last" to -1,
    )

    private val onDays: Map<String, Set<DayOfWeek>> = mapOf(
        "weekdays" to setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        ),
        "weekends" to setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
    )

    // "every 15th" — порядковый суффикс обязателен: он и есть маркер числа месяца
    private val ordinalDay = Regex("""(\d{1,2})(?:st|nd|rd|th)""")

    /** Первый матч по свободным токенам + хвост конца повтора; used не трогает. */
    fun find(tokens: List<Token>, today: LocalDate, used: BooleanArray): RecurrenceCandidate? {
        for (i in tokens.indices) {
            if (used[i]) continue
            matchAt(tokens, i, used)?.let { return withEnd(tokens, today, used, it) }
        }
        return null
    }

    private fun matchAt(tokens: List<Token>, i: Int, used: BooleanArray): RecurrenceCandidate? {
        val t = tokens[i].lower

        // "on weekdays" / "on weekends"
        if (t == "on" && free(used, i..i + 1, tokens.size)) {
            onDays[tokens.getOrNull(i + 1)?.lower]?.let { return weeklyRecurrence(it, i..i + 1) }
        }

        // "daily" / "weekly" / "monthly" / "yearly"
        singleFreq[t]?.let {
            return RecurrenceCandidate("FREQ=$it", i..i, period = periodOf(it, 1))
        }

        if (t != "every" && t != "each") return null
        val next = tokens.getOrNull(i + 1)?.lower ?: return null

        // "every tuesday [and thursday …]"
        EnDateRules.weekdays[next]?.let { first ->
            if (free(used, i..i + 1, tokens.size)) {
                val days = mutableSetOf(first)
                val end = consumeDays(tokens, i + 2, used, days)
                return weeklyRecurrence(days, i until end)
            }
        }

        // "every second sunday of the month" — раньше "every 15th": иначе "2nd"
        // съелся бы числом месяца. "of the month" обязателен: голое "every
        // second tuesday" двусмысленно (раз в две недели vs день месяца)
        ordinals[next]?.let { ord ->
            EnDateRules.weekdays[tokens.getOrNull(i + 2)?.lower]?.let { dow ->
                var j = i + 3
                if (tokens.getOrNull(j)?.lower == "of") {
                    if (tokens.getOrNull(j + 1)?.lower == "the") j++
                    if (tokens.getOrNull(j + 1)?.lower == "month" && free(used, i..j + 1, tokens.size)) {
                        return ordinalMonthlyRecurrence(ord, dow, i..j + 1)
                    }
                }
            }
        }

        // "every weekday" — будни, как "on weekdays"
        if (next == "weekday" && free(used, i..i + 1, tokens.size)) {
            return weeklyRecurrence(onDays.getValue("weekdays"), i..i + 1)
        }

        // "every morning/evening/night" — ежедневно + половина суток для часа
        dayHalves[next]?.let { half ->
            if (free(used, i..i + 1, tokens.size)) {
                return RecurrenceCandidate("FREQ=DAILY", i..i + 1, period = Period.ofDays(1), dayHalf = half)
            }
        }

        // "every other day/week/month/monday" — интервал 2
        if (next == "other" && free(used, i..i + 2, tokens.size)) {
            val third = tokens.getOrNull(i + 2)?.lower
            unitFreq[third]?.let { f ->
                return RecurrenceCandidate("FREQ=$f;INTERVAL=2", i..i + 2, period = periodOf(f, 2))
            }
            EnDateRules.weekdays[third]?.let { dow ->
                val base = weeklyRecurrence(setOf(dow), i..i + 2)
                return base.copy(
                    rrule = base.rrule.replace("FREQ=WEEKLY;", "FREQ=WEEKLY;INTERVAL=2;"),
                    period = Period.ofWeeks(2),
                )
            }
        }

        // "every day/week/month/year"
        unitFreq[next]?.let {
            if (free(used, i..i + 1, tokens.size)) {
                return RecurrenceCandidate("FREQ=$it", i..i + 1, period = periodOf(it, 1))
            }
        }

        // "every 15th" — месячный повтор по числу
        ordinalDay.matchEntire(next)?.let { m ->
            val day = m.groupValues[1].toInt()
            if (day in 1..31 && free(used, i..i + 1, tokens.size)) {
                return RecurrenceCandidate(
                    "FREQ=MONTHLY;BYMONTHDAY=$day", i..i + 1,
                    period = Period.ofMonths(1), anchorMonthDay = day,
                )
            }
        }

        // "every 2 weeks"; INTERVAL=1 не пишем — это дефолт RFC 5545
        val n = next.toIntOrNull() ?: return null
        val freq = unitFreq[tokens.getOrNull(i + 2)?.lower]
        if (freq != null && n in 1..99 && free(used, i..i + 2, tokens.size)) {
            val rrule = if (n > 1) "FREQ=$freq;INTERVAL=$n" else "FREQ=$freq"
            return RecurrenceCandidate(rrule, i..i + 2, period = periodOf(freq, n))
        }

        return null
    }

    /**
     * Конец повтора: "until/till [the] end of [the] week/month/year/<month>",
     * "until September 15" / "until 15 September", "10 times". Только при уже
     * найденном повторе.
     */
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

            // "10 times"
            val n = t.toIntOrNull()
            if (n != null && n in 1..999 && !busy(i + 1) && tokens[i + 1].lower == "times") {
                return main.copy(count = n, extraTokens = listOf(i..i + 1))
            }

            if (t != "until" && t != "till") continue
            var j = i + 1
            if (!busy(j) && tokens[j].lower == "the") j++
            if (busy(j)) continue

            // "until [the] end of [the] X"
            if (tokens[j].lower == "end" && !busy(j + 1) && tokens[j + 1].lower == "of") {
                var k = j + 2
                if (!busy(k) && tokens.getOrNull(k)?.lower == "the") k++
                if (busy(k)) continue
                val date = endOf(tokens[k].lower, today) ?: continue
                return main.copy(untilDate = date, extraTokens = listOf(i..k))
            }

            val first = tokens[j].lower

            // "until the 15th of September"
            val ordDay = dayNumber(first)
            if (ordDay != null && ordDay in 1..31 && !busy(j + 1) && !busy(j + 2) &&
                tokens[j + 1].lower == "of"
            ) {
                val month = EnDateRules.months[tokens[j + 2].lower]
                val date = month?.let { futureDate(today, it, ordDay) }
                if (date != null) return main.copy(untilDate = date, extraTokens = listOf(i..j + 2))
            }

            // "until September 15[th]" / "until 15[th] September"
            if (!busy(j + 1)) {
                val second = tokens[j + 1].lower
                val monthDay = when {
                    EnDateRules.months[first] != null -> EnDateRules.months[first]!! to dayNumber(second)
                    EnDateRules.months[second] != null -> EnDateRules.months[second]!! to dayNumber(first)
                    else -> null
                }
                val day = monthDay?.second
                if (monthDay != null && day != null && day in 1..31) {
                    futureDate(today, monthDay.first, day)?.let { date ->
                        return main.copy(untilDate = date, extraTokens = listOf(i..j + 1))
                    }
                }
            }

            // "until September" — канун первого числа: серия до наступления месяца
            EnDateRules.months[first]?.let { month ->
                var firstOfMonth = LocalDate.of(today.year, month, 1)
                if (!firstOfMonth.isAfter(today)) firstOfMonth = firstOfMonth.plusYears(1)
                return main.copy(untilDate = firstOfMonth.minusDays(1), extraTokens = listOf(i..j))
            }
        }
        return main
    }

    /** Дата этого года, если не прошла, иначе следующего; невалидная → null. */
    private fun futureDate(today: LocalDate, month: Int, day: Int): LocalDate? =
        EnDateRules.dateOrNull(today.year, month, day)?.takeIf { !it.isBefore(today) }
            ?: EnDateRules.dateOrNull(today.year + 1, month, day)

    private fun endOf(word: String, today: LocalDate): LocalDate? = when (word) {
        "week" -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        "month" -> today.withDayOfMonth(today.lengthOfMonth())
        "year" -> LocalDate.of(today.year, 12, 31)
        else -> EnDateRules.months[word]?.let { m ->
            val thisYear = LocalDate.of(today.year, m, 1).with(TemporalAdjusters.lastDayOfMonth())
            if (thisYear.isBefore(today)) thisYear.plusYears(1).with(TemporalAdjusters.lastDayOfMonth())
            else thisYear
        }
    }

    /** Хвост списка дней `[and] <day>`… начиная с start. */
    private fun consumeDays(
        tokens: List<Token>,
        start: Int,
        used: BooleanArray,
        days: MutableSet<DayOfWeek>,
    ): Int {
        var j = start
        while (true) {
            val k = if (tokens.getOrNull(j)?.lower == "and") j + 1 else j
            val word = tokens.getOrNull(k)?.lower ?: break
            if ((j..k).any { used[it] }) break
            days += EnDateRules.weekdays[word] ?: break
            j = k + 1
        }
        return j
    }

    private fun periodOf(freq: String, n: Int): Period = when (freq) {
        "DAILY" -> Period.ofDays(n)
        "WEEKLY" -> Period.ofWeeks(n)
        "MONTHLY" -> Period.ofMonths(n)
        else -> Period.ofYears(n)
    }

    private fun free(used: BooleanArray, range: IntRange, size: Int): Boolean =
        range.last < size && range.all { !used[it] }
}
