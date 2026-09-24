package com.dmi3dmi3.saywhen.parser.de

import com.dmi3dmi3.saywhen.parser.DayHalf
import com.dmi3dmi3.saywhen.parser.RecurrenceCandidate
import com.dmi3dmi3.saywhen.parser.Token
import com.dmi3dmi3.saywhen.parser.free
import com.dmi3dmi3.saywhen.parser.ordinalMonthlyRecurrence
import com.dmi3dmi3.saywhen.parser.periodOf
import com.dmi3dmi3.saywhen.parser.weeklyRecurrence
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Period
import java.time.temporal.TemporalAdjusters

internal object DeRecurrenceRules {

    private val every = setOf("jeden", "jede", "jedes")

    private val unitFreq = mapOf(
        "tag" to "DAILY", "tage" to "DAILY",
        "woche" to "WEEKLY", "wochen" to "WEEKLY",
        "monat" to "MONTHLY", "monate" to "MONTHLY",
        "jahr" to "YEARLY", "jahre" to "YEARLY",
    )

    // «täglich» — наречия частоты, живые в отличие от романских
    private val adverbFreq = mapOf(
        "täglich" to "DAILY", "taeglich" to "DAILY",
        "wöchentlich" to "WEEKLY", "woechentlich" to "WEEKLY",
        "monatlich" to "MONTHLY",
        "jährlich" to "YEARLY", "jaehrlich" to "YEARLY",
    )

    // «montags» — хабитуал-наречие суффиксом -s
    private val habitualDays = mapOf(
        "montags" to DayOfWeek.MONDAY, "dienstags" to DayOfWeek.TUESDAY,
        "mittwochs" to DayOfWeek.WEDNESDAY, "donnerstags" to DayOfWeek.THURSDAY,
        "freitags" to DayOfWeek.FRIDAY,
        "samstags" to DayOfWeek.SATURDAY, "sonnabends" to DayOfWeek.SATURDAY,
        "sonntags" to DayOfWeek.SUNDAY,
    )

    // «jeden ersten montag im monat»; letzte(n) — последний
    private val ordinals: Map<String, Int> = buildMap {
        val stems = listOf(
            "erst" to 1, "zweit" to 2, "dritt" to 3, "viert" to 4, "fünft" to 5, "fuenft" to 5,
            "letzt" to -1,
        )
        for ((stem, n) in stems) for (suffix in listOf("e", "en", "er", "es")) put(stem + suffix, n)
    }

    // «jeden morgen/abend» — ежедневно + половина суток
    private val dayHalves = mapOf(
        "morgen" to DayHalf.MORNING, "vormittag" to DayHalf.MORNING,
        "abend" to DayHalf.EVENING, "nachmittag" to DayHalf.AFTERNOON, "nacht" to DayHalf.EVENING,
    )

    // «alle zwei wochen» — интервал числительным словом
    private val intervalWords = mapOf(
        "zwei" to 2, "drei" to 3, "vier" to 4, "fünf" to 5, "fuenf" to 5,
        "sechs" to 6, "sieben" to 7, "acht" to 8, "neun" to 9, "zehn" to 10,
    )

    private val allWeekdays = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )
    private val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    // «10 mal» / «10-mal» — счёт повторов
    private val gluedMal = Regex("""(\d{1,3})-?mal""")

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

        // наречия: täglich / wöchentlich / monatlich / jährlich
        adverbFreq[t]?.let {
            return RecurrenceCandidate("FREQ=$it", i..i, period = periodOf(it, 1))
        }

        // хабитуал: montags [und freitags]
        habitualDays[t]?.let { first ->
            val days = mutableSetOf(first)
            val end = consumeDays(tokens, i + 1, used, days) { habitualDays[it] }
            return weeklyRecurrence(days, i until end)
        }

        // werktags / wochentags — будни
        if (t == "werktags" || t == "wochentags") {
            return weeklyRecurrence(allWeekdays, i..i)
        }

        // «14-tägig» — раз в N дней одним словом (дефис держит токенайзер)
        Regex("""(\d{1,3})-?(?:tägig|taegig)""").matchEntire(t)?.let { m ->
            val n = m.groupValues[1].toInt()
            if (n in 2..99) {
                return RecurrenceCandidate("FREQ=DAILY;INTERVAL=$n", i..i, period = Period.ofDays(n))
            }
        }

        // «einmal pro woche / die woche / im monat / am tag»
        if (t == "einmal") {
            var j = i + 1
            if (tokens.getOrNull(j)?.lower in setOf("pro", "die", "im", "am", "der")) j++
            unitFreq[tokens.getOrNull(j)?.lower]?.let { f ->
                if (free(used, i..j, tokens.size)) {
                    return RecurrenceCandidate("FREQ=$f", i..j, period = periodOf(f, 1))
                }
            }
        }

        // «[am] 15. jedes monats» — матч от числа (точку съел токенайзер)
        run {
            val n = t.toIntOrNull()?.takeIf { it in 1..31 } ?: return@run
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

        // «alle 2 wochen» / «alle zwei wochen»
        if (t == "alle") {
            val next = tokens.getOrNull(i + 1)?.lower ?: return null
            val n = next.toIntOrNull() ?: intervalWords[next] ?: return null
            val freq = unitFreq[tokens.getOrNull(i + 2)?.lower] ?: return null
            if (n in 1..99 && free(used, i..i + 2, tokens.size)) {
                val rrule = if (n > 1) "FREQ=$freq;INTERVAL=$n" else "FREQ=$freq"
                return RecurrenceCandidate(rrule, i..i + 2, period = periodOf(freq, n))
            }
        }

        if (t !in every) return null
        val next = tokens.getOrNull(i + 1)?.lower ?: return null

        // «jeden ersten montag im monat» («im monat» обязательно — иначе
        // «jeden zweiten dienstag» двусмысленен, не берём)
        ordinals[next]?.let { ord ->
            DeDateRules.weekdays[tokens.getOrNull(i + 2)?.lower]?.let { dow ->
                if (tokens.getOrNull(i + 3)?.lower == "im" &&
                    tokens.getOrNull(i + 4)?.lower == "monat" && free(used, i..i + 4, tokens.size)
                ) {
                    return ordinalMonthlyRecurrence(ord, dow, i..i + 4)
                }
            }
        }

        // «jede zweite woche» — единственный однозначный словесный интервал
        if (next in setOf("zweite", "zweiten") && tokens.getOrNull(i + 2)?.lower == "woche" &&
            free(used, i..i + 2, tokens.size)
        ) {
            return RecurrenceCandidate(
                "FREQ=WEEKLY;INTERVAL=2", i..i + 2, period = Period.ofWeeks(2),
            )
        }

        // «jeden dienstag [und donnerstag …]»
        DeDateRules.weekdays[next]?.let { first ->
            if (free(used, i..i + 1, tokens.size)) {
                val days = mutableSetOf(first)
                val end = consumeDays(tokens, i + 2, used, days) { w ->
                    DeDateRules.weekdays[w]
                }
                return weeklyRecurrence(days, i until end)
            }
        }

        // «jeden morgen/abend»
        dayHalves[next]?.let { half ->
            if (free(used, i..i + 1, tokens.size)) {
                return RecurrenceCandidate("FREQ=DAILY", i..i + 1, period = Period.ofDays(1), dayHalf = half)
            }
        }

        // «jedes wochenende»
        if (next == "wochenende" && free(used, i..i + 1, tokens.size)) {
            return weeklyRecurrence(weekend, i..i + 1)
        }

        // «jeden tag / jede woche / jeden monat / jedes jahr»
        unitFreq[next]?.let {
            if (free(used, i..i + 1, tokens.size)) {
                return RecurrenceCandidate("FREQ=$it", i..i + 1, period = periodOf(it, 1))
            }
        }

        // «jeden 15ten» — суффикс и есть маркер числа месяца
        Regex("""(\d{1,2})(?:te|ten)""").matchEntire(next)?.let { m ->
            val day = m.groupValues[1].toInt()
            if (day in 1..31 && free(used, i..i + 1, tokens.size)) {
                return RecurrenceCandidate(
                    "FREQ=MONTHLY;BYMONTHDAY=$day", i..i + 1,
                    period = Period.ofMonths(1), anchorMonthDay = day,
                )
            }
        }

        return null
    }

    /**
     * Конец повтора: «bis ende august», «bis zum ende des monats», «bis zum
     * 15. september», «bis september», «10 mal». Только при найденном повторе.
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

            // «10 mal» / «10-mal»
            gluedMal.matchEntire(t)?.let { m ->
                val n = m.groupValues[1].toInt()
                if (n in 1..999) return main.copy(count = n, extraTokens = listOf(i..i))
            }
            val n = t.toIntOrNull()
            if (n != null && n in 1..999 && !busy(i + 1) && tokens[i + 1].lower == "mal") {
                return main.copy(count = n, extraTokens = listOf(i..i + 1))
            }

            if (t != "bis") continue
            var j = i + 1
            if (!busy(j) && tokens.getOrNull(j)?.lower == "zum") j++
            if (busy(j)) continue
            val first = tokens[j].lower

            // «ende august» / «ende des monats/der woche/des jahres»
            if (first == "ende" && !busy(j + 1)) {
                var k = j + 1
                if (tokens.getOrNull(k)?.lower in setOf("des", "der") && !busy(k)) k++
                if (busy(k)) continue
                val date = endOf(tokens[k].lower, today) ?: continue
                return main.copy(untilDate = date, extraTokens = listOf(i..k))
            }

            // «bis zum 15. september»; прошло → следующий год
            val day = Regex("""(\d{1,2})(?:te|ten)?""").matchEntire(first)
                ?.groupValues?.get(1)?.toIntOrNull()
            if (day != null && day in 1..31 && !busy(j + 1)) {
                val month = DeDateRules.months[tokens[j + 1].lower] ?: continue
                val date = DeDateRules.dateOrNull(today.year, month, day)?.takeIf { !it.isBefore(today) }
                    ?: DeDateRules.dateOrNull(today.year + 1, month, day) ?: continue
                return main.copy(untilDate = date, extraTokens = listOf(i..j + 1))
            }

            // «bis september» — канун первого числа
            DeDateRules.months[first]?.let { month ->
                var firstOfMonth = LocalDate.of(today.year, month, 1)
                if (!firstOfMonth.isAfter(today)) firstOfMonth = firstOfMonth.plusYears(1)
                return main.copy(untilDate = firstOfMonth.minusDays(1), extraTokens = listOf(i..j))
            }
        }
        return main
    }

    private fun endOf(word: String, today: LocalDate): LocalDate? = when (word) {
        "woche" -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        "monats", "monat" -> today.withDayOfMonth(today.lengthOfMonth())
        "jahres", "jahr" -> LocalDate.of(today.year, 12, 31)
        else -> DeDateRules.months[word]?.let { m ->
            val thisYear = LocalDate.of(today.year, m, 1).with(TemporalAdjusters.lastDayOfMonth())
            if (thisYear.isBefore(today)) thisYear.plusYears(1).with(TemporalAdjusters.lastDayOfMonth())
            else thisYear
        }
    }

    /** Хвост списка дней `[und] <день>`… начиная с start. */
    private fun consumeDays(
        tokens: List<Token>,
        start: Int,
        used: BooleanArray,
        days: MutableSet<DayOfWeek>,
        lookup: (String) -> DayOfWeek?,
    ): Int {
        var j = start
        while (true) {
            val k = if (tokens.getOrNull(j)?.lower == "und") j + 1 else j
            val word = tokens.getOrNull(k)?.lower ?: break
            if ((j..k).any { used[it] }) break
            days += lookup(word) ?: break
            j = k + 1
        }
        return j
    }
}
