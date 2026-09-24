package com.dmi3dmi3.saywhen.parser.it

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

internal object ItRecurrenceRules {

    private val unitFreq = mapOf(
        "giorno" to "DAILY", "giorni" to "DAILY",
        "settimana" to "WEEKLY", "settimane" to "WEEKLY",
        "mese" to "MONTHLY", "mesi" to "MONTHLY",
        "anno" to "YEARLY", "anni" to "YEARLY",
    )

    // «il primo lunedì del mese»; ultimo — последний; женские формы для domenica
    private val ordinals = mapOf(
        "primo" to 1, "prima" to 1, "secondo" to 2, "seconda" to 2,
        "terzo" to 3, "terza" to 3, "quarto" to 4, "quarta" to 4,
        "quinto" to 5, "quinta" to 5, "ultimo" to -1, "ultima" to -1,
    )

    // «ogni mattina/sera/notte» — ежедневно + половина суток
    private val dayHalves = mapOf(
        "mattina" to DayHalf.MORNING, "sera" to DayHalf.EVENING, "notte" to DayHalf.EVENING,
    )

    // «ogni due settimane» — интервал числительным словом
    private val intervalWords = mapOf(
        "due" to 2, "tre" to 3, "quattro" to 4, "cinque" to 5,
        "sei" to 6, "sette" to 7, "otto" to 8, "nove" to 9, "dieci" to 10,
    )

    private val allWeekdays = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )
    private val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

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

        // «[nei] giorni feriali» — будни; «nei» подберёт поглощение сирот
        if (t == "giorni" && tokens.getOrNull(i + 1)?.lower == "feriali" &&
            free(used, i..i + 1, tokens.size)
        ) {
            return weeklyRecurrence(allWeekdays, i..i + 1)
        }

        // «una volta al giorno / alla settimana / al mese / all'anno»
        if (t == "una" && tokens.getOrNull(i + 1)?.lower == "volta") {
            var j = i + 2
            if (tokens.getOrNull(j)?.lower in setOf("a", "al", "alla", "all")) j++
            unitFreq[tokens.getOrNull(j)?.lower]?.let { f ->
                if (free(used, i..j, tokens.size)) {
                    return RecurrenceCandidate("FREQ=$f", i..j, period = periodOf(f, 1))
                }
            }
        }

        // «il 15 di ogni mese» — матч от числа, «il» уходит сиротой
        run {
            val n = t.toIntOrNull()?.takeIf { it in 1..31 } ?: return@run
            if (tokens.getOrNull(i + 1)?.lower == "di" && tokens.getOrNull(i + 2)?.lower == "ogni" &&
                tokens.getOrNull(i + 3)?.lower == "mese" && free(used, i..i + 3, tokens.size)
            ) {
                return RecurrenceCandidate(
                    "FREQ=MONTHLY;BYMONTHDAY=$n", i..i + 3,
                    period = Period.ofMonths(1), anchorMonthDay = n,
                )
            }
        }

        // «[il] primo lunedì del mese» без ogni
        ordinals[t]?.let { ord ->
            ItDateRules.weekdays[tokens.getOrNull(i + 1)?.lower]?.let { dow ->
                if (tokens.getOrNull(i + 2)?.lower == "del" && tokens.getOrNull(i + 3)?.lower == "mese" &&
                    free(used, i..i + 3, tokens.size)
                ) {
                    return ordinalMonthlyRecurrence(ord, dow, i..i + 3)
                }
            }
        }

        if (t != "ogni") return null
        val next = tokens.getOrNull(i + 1)?.lower ?: return null

        // «ogni primo lunedì del mese»
        ordinals[next]?.let { ord ->
            ItDateRules.weekdays[tokens.getOrNull(i + 2)?.lower]?.let { dow ->
                if (tokens.getOrNull(i + 3)?.lower == "del" && tokens.getOrNull(i + 4)?.lower == "mese" &&
                    free(used, i..i + 4, tokens.size)
                ) {
                    return ordinalMonthlyRecurrence(ord, dow, i..i + 4)
                }
            }
        }

        // «ogni martedì [e giovedì …]»
        ItDateRules.weekdays[next]?.let { first ->
            if (free(used, i..i + 1, tokens.size)) {
                val days = mutableSetOf(first)
                val end = consumeDays(tokens, i + 2, used, days)
                return weeklyRecurrence(days, i until end)
            }
        }

        // «ogni mattina/sera/notte»
        dayHalves[next]?.let { half ->
            if (free(used, i..i + 1, tokens.size)) {
                return RecurrenceCandidate("FREQ=DAILY", i..i + 1, period = Period.ofDays(1), dayHalf = half)
            }
        }

        // «ogni weekend / ogni fine settimana / ogni finesettimana»
        if ((next == "weekend" || next == "finesettimana") && free(used, i..i + 1, tokens.size)) {
            return weeklyRecurrence(weekend, i..i + 1)
        }
        if (next == "fine" && tokens.getOrNull(i + 2)?.lower == "settimana" &&
            free(used, i..i + 2, tokens.size)
        ) {
            return weeklyRecurrence(weekend, i..i + 2)
        }

        // «ogni giorno/settimana/mese/anno»
        unitFreq[next]?.let {
            if (free(used, i..i + 1, tokens.size)) {
                return RecurrenceCandidate("FREQ=$it", i..i + 1, period = periodOf(it, 1))
            }
        }

        val n = next.toIntOrNull() ?: intervalWords[next] ?: return null

        // «ogni 15 del mese» — маркер числа обязателен
        if (tokens.getOrNull(i + 2)?.lower == "del" && tokens.getOrNull(i + 3)?.lower == "mese" &&
            n in 1..31 && free(used, i..i + 3, tokens.size)
        ) {
            return RecurrenceCandidate(
                "FREQ=MONTHLY;BYMONTHDAY=$n", i..i + 3,
                period = Period.ofMonths(1), anchorMonthDay = n,
            )
        }

        // «ogni 2 settimane»; INTERVAL=1 не пишем — это дефолт RFC 5545
        val freq = unitFreq[tokens.getOrNull(i + 2)?.lower]
        if (freq != null && n in 1..99 && free(used, i..i + 2, tokens.size)) {
            val rrule = if (n > 1) "FREQ=$freq;INTERVAL=$n" else "FREQ=$freq"
            return RecurrenceCandidate(rrule, i..i + 2, period = periodOf(freq, n))
        }

        return null
    }

    /**
     * Конец повтора: «fino a fine agosto», «fino alla fine del mese»,
     * «fino al 15 settembre», «entro fine mese», «10 volte». Только при
     * уже найденном повторе.
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

            // «10 volte»
            val n = t.toIntOrNull()
            if (n != null && n in 1..999 && !busy(i + 1) && tokens[i + 1].lower == "volte") {
                return main.copy(count = n, extraTokens = listOf(i..i + 1))
            }

            if (t != "fino" && t != "entro") continue
            var j = i + 1
            while (!busy(j) && tokens.getOrNull(j)?.lower in setOf("a", "al", "alla", "il", "la")) j++
            if (busy(j)) continue
            val first = tokens[j].lower

            // «fine [di|del|della|dell] agosto/mese/anno/settimana»
            if (first == "fine" && !busy(j + 1)) {
                var k = j + 1
                if (tokens.getOrNull(k)?.lower in setOf("di", "del", "della", "dell") && !busy(k)) k++
                if (busy(k)) continue
                val date = endOf(tokens[k].lower, today) ?: continue
                return main.copy(untilDate = date, extraTokens = listOf(i..k))
            }

            // «fino al 15 settembre»; прошло → следующий год
            val day = first.toIntOrNull()
            if (day != null && day in 1..31 && !busy(j + 1)) {
                val month = ItDateRules.months[tokens[j + 1].lower] ?: continue
                val date = ItDateRules.dateOrNull(today.year, month, day)?.takeIf { !it.isBefore(today) }
                    ?: ItDateRules.dateOrNull(today.year + 1, month, day) ?: continue
                return main.copy(untilDate = date, extraTokens = listOf(i..j + 1))
            }

            // «fino a settembre» — канун первого числа
            ItDateRules.months[first]?.let { month ->
                var firstOfMonth = LocalDate.of(today.year, month, 1)
                if (!firstOfMonth.isAfter(today)) firstOfMonth = firstOfMonth.plusYears(1)
                return main.copy(untilDate = firstOfMonth.minusDays(1), extraTokens = listOf(i..j))
            }
        }
        return main
    }

    private fun endOf(word: String, today: LocalDate): LocalDate? = when (word) {
        "settimana" -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        "mese" -> today.withDayOfMonth(today.lengthOfMonth())
        "anno" -> LocalDate.of(today.year, 12, 31)
        else -> ItDateRules.months[word]?.let { m ->
            val thisYear = LocalDate.of(today.year, m, 1).with(TemporalAdjusters.lastDayOfMonth())
            if (thisYear.isBefore(today)) thisYear.plusYears(1).with(TemporalAdjusters.lastDayOfMonth())
            else thisYear
        }
    }

    /** Хвост списка дней `[e] <giorno>`… начиная с start. */
    private fun consumeDays(
        tokens: List<Token>,
        start: Int,
        used: BooleanArray,
        days: MutableSet<DayOfWeek>,
    ): Int {
        var j = start
        while (true) {
            val k = if (tokens.getOrNull(j)?.lower == "e") j + 1 else j
            val word = tokens.getOrNull(k)?.lower ?: break
            if ((j..k).any { used[it] }) break
            days += ItDateRules.weekdays[word] ?: break
            j = k + 1
        }
        return j
    }
}
