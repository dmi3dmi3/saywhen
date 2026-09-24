package com.dmi3dmi3.saywhen.parser.es

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

internal object EsRecurrenceRules {

    private val unitFreq = mapOf(
        "día" to "DAILY", "dia" to "DAILY", "días" to "DAILY", "dias" to "DAILY",
        "semana" to "WEEKLY", "semanas" to "WEEKLY",
        "mes" to "MONTHLY", "meses" to "MONTHLY",
        "año" to "YEARLY", "ano" to "YEARLY", "años" to "YEARLY", "anos" to "YEARLY",
    )

    // «el primer lunes del mes»; último — последний
    private val ordinals = mapOf(
        "primer" to 1, "primero" to 1, "primera" to 1,
        "segundo" to 2, "segunda" to 2, "tercer" to 3, "tercero" to 3, "tercera" to 3,
        "cuarto" to 4, "cuarta" to 4, "quinto" to 5, "quinta" to 5,
        "último" to -1, "ultimo" to -1, "última" to -1, "ultima" to -1,
    )

    // «cada mañana/tarde/noche» — ежедневно + половина суток
    private val dayHalves = mapOf(
        "mañana" to DayHalf.MORNING, "manana" to DayHalf.MORNING,
        "tarde" to DayHalf.AFTERNOON, "noche" to DayHalf.EVENING,
    )

    // «cada dos semanas» — интервал числительным словом; quince — идиома
    // «cada quince días» (раз в две недели)
    private val intervalWords = mapOf(
        "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5,
        "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9, "diez" to 10,
        "quince" to 15,
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

    /** День недели с допуском множественного числа («sábados» → суббота). */
    private fun weekday(word: String?): DayOfWeek? =
        word?.let { EsDateRules.weekdays[it] ?: EsDateRules.weekdays[it.removeSuffix("s")] }

    private fun matchAt(tokens: List<Token>, i: Int, used: BooleanArray): RecurrenceCandidate? {
        val t = tokens[i].lower

        // «entre semana» — будни
        if (t == "entre" && tokens.getOrNull(i + 1)?.lower == "semana" &&
            free(used, i..i + 1, tokens.size)
        ) {
            return weeklyRecurrence(allWeekdays, i..i + 1)
        }

        // «[los] fines de semana» — выходные; «los» уходит сиротой
        if (t == "fines" && tokens.getOrNull(i + 1)?.lower == "de" &&
            tokens.getOrNull(i + 2)?.lower == "semana" && free(used, i..i + 2, tokens.size)
        ) {
            return weeklyRecurrence(weekend, i..i + 2)
        }

        // «los sábados / los lunes [y jueves]» — хабитуал с артиклем
        // множественного; «el sábado» остаётся датой
        if (t == "los") {
            weekday(tokens.getOrNull(i + 1)?.lower)?.let { first ->
                if (free(used, i..i + 1, tokens.size)) {
                    val days = mutableSetOf(first)
                    val end = consumeDays(tokens, i + 2, used, days)
                    return weeklyRecurrence(days, i until end)
                }
            }
            // «los findes» — разговорные выходные
            if (tokens.getOrNull(i + 1)?.lower in setOf("finde", "findes") &&
                free(used, i..i + 1, tokens.size)
            ) {
                return weeklyRecurrence(weekend, i..i + 1)
            }
        }

        // «una vez a la semana / al mes / al día / al año»
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
            unitFreq[tokens.getOrNull(j)?.lower]?.let { f ->
                if (free(used, i..j, tokens.size)) {
                    return RecurrenceCandidate("FREQ=$f", i..j, period = periodOf(f, 1))
                }
            }
        }

        // «el 15 de cada mes» — матч от числа, «el» уходит сиротой
        run {
            val n = t.toIntOrNull()?.takeIf { it in 1..31 } ?: return@run
            if (tokens.getOrNull(i + 1)?.lower == "de" && tokens.getOrNull(i + 2)?.lower == "cada" &&
                tokens.getOrNull(i + 3)?.lower == "mes" && free(used, i..i + 3, tokens.size)
            ) {
                return RecurrenceCandidate(
                    "FREQ=MONTHLY;BYMONTHDAY=$n", i..i + 3,
                    period = Period.ofMonths(1), anchorMonthDay = n,
                )
            }
        }

        // «[el] primer lunes del mes» без cada
        ordinals[t]?.let { ord ->
            weekday(tokens.getOrNull(i + 1)?.lower)?.let { dow ->
                if (tokens.getOrNull(i + 2)?.lower == "del" && tokens.getOrNull(i + 3)?.lower == "mes" &&
                    free(used, i..i + 3, tokens.size)
                ) {
                    return ordinalMonthlyRecurrence(ord, dow, i..i + 3)
                }
            }
        }

        // «todos los martes / todos los días / todas las semanas»
        if ((t == "todos" || t == "todas") &&
            tokens.getOrNull(i + 1)?.lower in setOf("los", "las")
        ) {
            val third = tokens.getOrNull(i + 2)?.lower
            weekday(third)?.let { first ->
                if (free(used, i..i + 2, tokens.size)) {
                    val days = mutableSetOf(first)
                    val end = consumeDays(tokens, i + 3, used, days)
                    return weeklyRecurrence(days, i until end)
                }
            }
            unitFreq[third]?.let { f ->
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

        // «cada primer lunes del mes»
        ordinals[next]?.let { ord ->
            weekday(tokens.getOrNull(i + 2)?.lower)?.let { dow ->
                if (tokens.getOrNull(i + 3)?.lower == "del" && tokens.getOrNull(i + 4)?.lower == "mes" &&
                    free(used, i..i + 4, tokens.size)
                ) {
                    return ordinalMonthlyRecurrence(ord, dow, i..i + 4)
                }
            }
        }

        // «cada martes [y jueves …]»
        weekday(next)?.let { first ->
            if (free(used, i..i + 1, tokens.size)) {
                val days = mutableSetOf(first)
                val end = consumeDays(tokens, i + 2, used, days)
                return weeklyRecurrence(days, i until end)
            }
        }

        // «cada mañana/tarde/noche»
        dayHalves[next]?.let { half ->
            if (free(used, i..i + 1, tokens.size)) {
                return RecurrenceCandidate("FREQ=DAILY", i..i + 1, period = Period.ofDays(1), dayHalf = half)
            }
        }

        // «cada fin de semana» / разговорное «cada finde»
        if (next == "fin" && tokens.getOrNull(i + 2)?.lower == "de" &&
            tokens.getOrNull(i + 3)?.lower == "semana" && free(used, i..i + 3, tokens.size)
        ) {
            return weeklyRecurrence(weekend, i..i + 3)
        }
        if (next == "finde" && free(used, i..i + 1, tokens.size)) {
            return weeklyRecurrence(weekend, i..i + 1)
        }

        // «cada día/semana/mes/año»
        unitFreq[next]?.let {
            if (free(used, i..i + 1, tokens.size)) {
                return RecurrenceCandidate("FREQ=$it", i..i + 1, period = periodOf(it, 1))
            }
        }

        // «cada quince días» — идиома «раз в две недели» (quincena): день
        // недели держится, не дрейфует; числовое «cada 15 días» — буквально
        if (next == "quince" && tokens.getOrNull(i + 2)?.lower in setOf("días", "dias") &&
            free(used, i..i + 2, tokens.size)
        ) {
            return RecurrenceCandidate("FREQ=WEEKLY;INTERVAL=2", i..i + 2, period = Period.ofWeeks(2))
        }

        val n = next.toIntOrNull() ?: intervalWords[next] ?: return null

        // «cada 15 del mes» — маркер числа обязателен
        if (tokens.getOrNull(i + 2)?.lower == "del" && tokens.getOrNull(i + 3)?.lower == "mes" &&
            n in 1..31 && free(used, i..i + 3, tokens.size)
        ) {
            return RecurrenceCandidate(
                "FREQ=MONTHLY;BYMONTHDAY=$n", i..i + 3,
                period = Period.ofMonths(1), anchorMonthDay = n,
            )
        }

        // «cada 2 semanas»; INTERVAL=1 не пишем — это дефолт RFC 5545
        val freq = unitFreq[tokens.getOrNull(i + 2)?.lower]
        if (freq != null && n in 1..99 && free(used, i..i + 2, tokens.size)) {
            val rrule = if (n > 1) "FREQ=$freq;INTERVAL=$n" else "FREQ=$freq"
            return RecurrenceCandidate(rrule, i..i + 2, period = periodOf(freq, n))
        }

        return null
    }

    /**
     * Конец повтора: «hasta el 15 de septiembre», «hasta fin(ales) de mes»,
     * «hasta septiembre», «10 veces». Только при уже найденном повторе.
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

            // «10 veces»
            val n = t.toIntOrNull()
            if (n != null && n in 1..999 && !busy(i + 1) && tokens[i + 1].lower == "veces") {
                return main.copy(count = n, extraTokens = listOf(i..i + 1))
            }

            if (t != "hasta") continue
            var j = i + 1
            while (!busy(j) && tokens.getOrNull(j)?.lower in setOf("el", "la", "los", "las")) j++
            if (busy(j)) continue
            val first = tokens[j].lower

            // «fin(ales) de mes/año/semana/agosto»
            if (first in setOf("fin", "finales") && !busy(j + 1) &&
                tokens.getOrNull(j + 1)?.lower == "de"
            ) {
                var k = j + 2
                if (tokens.getOrNull(k)?.lower in setOf("la", "el") && !busy(k)) k++
                if (busy(k)) continue
                val date = endOf(tokens[k].lower, today) ?: continue
                return main.copy(untilDate = date, extraTokens = listOf(i..k))
            }

            // «hasta el 15 de septiembre»; прошло → следующий год
            val day = first.toIntOrNull()
            if (day != null && day in 1..31 && !busy(j + 1)) {
                var m = j + 1
                if (tokens.getOrNull(m)?.lower == "de" && !busy(m)) m++
                val month = EsDateRules.months[tokens.getOrNull(m)?.lower ?: continue] ?: continue
                if (busy(m)) continue
                val date = EsDateRules.dateOrNull(today.year, month, day)?.takeIf { !it.isBefore(today) }
                    ?: EsDateRules.dateOrNull(today.year + 1, month, day) ?: continue
                return main.copy(untilDate = date, extraTokens = listOf(i..m))
            }

            // «hasta septiembre» — канун первого числа
            EsDateRules.months[first]?.let { month ->
                var firstOfMonth = LocalDate.of(today.year, month, 1)
                if (!firstOfMonth.isAfter(today)) firstOfMonth = firstOfMonth.plusYears(1)
                return main.copy(untilDate = firstOfMonth.minusDays(1), extraTokens = listOf(i..j))
            }
        }
        return main
    }

    private fun endOf(word: String, today: LocalDate): LocalDate? = when (word) {
        "semana" -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        "mes" -> today.withDayOfMonth(today.lengthOfMonth())
        "año", "ano" -> LocalDate.of(today.year, 12, 31)
        else -> EsDateRules.months[word]?.let { m ->
            val thisYear = LocalDate.of(today.year, m, 1).with(TemporalAdjusters.lastDayOfMonth())
            if (thisYear.isBefore(today)) thisYear.plusYears(1).with(TemporalAdjusters.lastDayOfMonth())
            else thisYear
        }
    }

    /** Хвост списка дней `[y] <día>`… начиная с start. */
    private fun consumeDays(
        tokens: List<Token>,
        start: Int,
        used: BooleanArray,
        days: MutableSet<DayOfWeek>,
    ): Int {
        var j = start
        while (true) {
            val k = if (tokens.getOrNull(j)?.lower == "y") j + 1 else j
            val word = tokens.getOrNull(k)?.lower ?: break
            if ((j..k).any { used[it] }) break
            days += weekday(word) ?: break
            j = k + 1
        }
        return j
    }
}
