package com.dmi3dmi3.saywhen.parser.ru

import com.dmi3dmi3.saywhen.parser.Confidence
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

internal object RecurrenceRules {

    private val every = setOf("каждый", "каждая", "каждую", "каждое", "каждые")

    // «ежедневно» — частота одним словом, как en daily/weekly
    private val adverbFreq = mapOf(
        "ежедневно" to "DAILY", "еженедельно" to "WEEKLY",
        "ежемесячно" to "MONTHLY", "ежегодно" to "YEARLY",
    )

    // «каждое утро/вечер/ночь», «по утрам/вечерам/ночам» — ежедневный повтор
    // + половина суток для часа круга
    private val dayHalves = mapOf(
        "утро" to DayHalf.MORNING, "вечер" to DayHalf.EVENING, "ночь" to DayHalf.EVENING,
    )
    private val poHalves = mapOf(
        "утрам" to DayHalf.MORNING, "вечерам" to DayHalf.EVENING, "ночам" to DayHalf.EVENING,
    )

    // «каждый первый/второй… <день недели> месяца»; -1 — последний
    private val ordinals = mapOf(
        "первый" to 1, "первую" to 1, "первое" to 1,
        "второй" to 2, "вторую" to 2, "второе" to 2,
        "третий" to 3, "третью" to 3, "третье" to 3,
        "четвертый" to 4, "четвёртый" to 4, "четвертую" to 4, "четвёртую" to 4,
        "четвертое" to 4, "четвёртое" to 4,
        "пятый" to 5, "пятую" to 5, "пятое" to 5,
        "последний" to -1, "последнюю" to -1, "последнее" to -1,
    )

    private val unitFreq = mapOf(
        "день" to "DAILY", "дня" to "DAILY", "дней" to "DAILY",
        "неделю" to "WEEKLY", "недели" to "WEEKLY", "недель" to "WEEKLY",
        "месяц" to "MONTHLY", "месяца" to "MONTHLY", "месяцев" to "MONTHLY",
        "год" to "YEARLY", "года" to "YEARLY", "лет" to "YEARLY",
    )

    // «каждые две недели» — интервал числительным словом
    private val intervalWords = mapOf(
        "два" to 2, "две" to 2, "три" to 3, "четыре" to 4, "пять" to 5,
        "шесть" to 6, "семь" to 7, "восемь" to 8, "девять" to 9, "десять" to 10,
    )

    // «по …» — дательный множественный
    private val poDays: Map<String, Set<DayOfWeek>> = mapOf(
        "будням" to setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        ),
        "выходным" to setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
        "понедельникам" to setOf(DayOfWeek.MONDAY),
        "вторникам" to setOf(DayOfWeek.TUESDAY),
        "средам" to setOf(DayOfWeek.WEDNESDAY),
        "четвергам" to setOf(DayOfWeek.THURSDAY),
        "пятницам" to setOf(DayOfWeek.FRIDAY),
        "субботам" to setOf(DayOfWeek.SATURDAY),
        "воскресеньям" to setOf(DayOfWeek.SUNDAY),
    )

    /** Первый матч по свободным токенам + хвост конца повтора; used не трогает. */
    fun find(tokens: List<Token>, today: LocalDate, used: BooleanArray): RecurrenceCandidate? {
        for (i in tokens.indices) {
            if (used[i]) continue
            matchAt(tokens, i, used)?.let { return withEnd(tokens, today, used, it) }
        }
        return null
    }

    /**
     * Конец повтора среди свободных токенов: «до конца недели/месяца/года»,
     * «до конца августа», «до 15 сентября», «10 раз». Ищется только при уже
     * найденном повторе — без него эти слова остаются обычным текстом.
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

            // «10 раз»
            val n = t.toIntOrNull()
            if (n != null && n in 1..999 && !busy(i + 1) &&
                tokens[i + 1].lower in setOf("раз", "раза")
            ) {
                return main.copy(count = n, extraTokens = listOf(i..i + 1))
            }

            if (t != "до" || busy(i + 1)) continue
            val second = tokens[i + 1].lower

            // «до конца недели/месяца/года/<месяца-род>»
            if (second == "конца" && !busy(i + 2)) {
                val date = endOf(tokens[i + 2].lower, today) ?: continue
                return main.copy(untilDate = date, extraTokens = listOf(i..i + 2))
            }

            // «до 15 сентября»; прошло → следующий год
            val day = second.toIntOrNull()
            if (day != null && day in 1..31 && !busy(i + 2)) {
                val month = DateRules.months[tokens[i + 2].lower] ?: continue
                val date = DateRules.dateOrNull(today.year, month, day)?.takeIf { !it.isBefore(today) }
                    ?: DateRules.dateOrNull(today.year + 1, month, day) ?: continue
                return main.copy(untilDate = date, extraTokens = listOf(i..i + 2))
            }

            // «до сентября» — канун первого числа: серия до наступления месяца
            DateRules.months[second]?.let { month ->
                var firstOfMonth = LocalDate.of(today.year, month, 1)
                if (!firstOfMonth.isAfter(today)) firstOfMonth = firstOfMonth.plusYears(1)
                return main.copy(untilDate = firstOfMonth.minusDays(1), extraTokens = listOf(i..i + 1))
            }
        }
        return main
    }

    private fun endOf(word: String, today: LocalDate): LocalDate? = when (word) {
        "недели" -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        "месяца" -> today.withDayOfMonth(today.lengthOfMonth())
        "года" -> LocalDate.of(today.year, 12, 31)
        else -> DateRules.months[word]?.let { m ->
            val thisYear = LocalDate.of(today.year, m, 1).with(TemporalAdjusters.lastDayOfMonth())
            if (thisYear.isBefore(today)) thisYear.plusYears(1).with(TemporalAdjusters.lastDayOfMonth())
            else thisYear
        }
    }

    private fun matchAt(tokens: List<Token>, i: Int, used: BooleanArray): RecurrenceCandidate? {
        val t = tokens[i].lower

        // «по будням» / «по выходным» / «по вторникам [и четвергам …]» /
        // «по утрам»
        if (t == "по" && free(used, i..i + 1, tokens.size)) {
            poDays[tokens.getOrNull(i + 1)?.lower]?.let { first ->
                val days = first.toMutableSet()
                val end = consumeDays(tokens, i + 2, used, days) { poDays[it] }
                return weeklyRecurrence(days, i until end)
            }
            poHalves[tokens.getOrNull(i + 1)?.lower]?.let { half ->
                return RecurrenceCandidate("FREQ=DAILY", i..i + 1, period = Period.ofDays(1), dayHalf = half)
            }
        }

        // «ежедневно» / «еженедельно» / «ежемесячно» / «ежегодно»
        adverbFreq[t]?.let {
            return RecurrenceCandidate("FREQ=$it", i..i, period = periodOf(it, 1))
        }

        // «раз в неделю» / «раз в 2 недели» — частота без «каждый»; с числом
        // перед «раз» («5 раз в день») повтором не берём: дни/часы неизвестны,
        // RRULE это не выражает
        if (t == "раз" && tokens.getOrNull(i + 1)?.lower == "в" &&
            (i == 0 || tokens[i - 1].lower.toIntOrNull() == null)
        ) {
            val third = tokens.getOrNull(i + 2)?.lower
            unitFreq[third]?.let { f ->
                if (free(used, i..i + 2, tokens.size)) {
                    return RecurrenceCandidate("FREQ=$f", i..i + 2, period = periodOf(f, 1))
                }
            }
            val n = third?.toIntOrNull()
            val f = unitFreq[tokens.getOrNull(i + 3)?.lower]
            if (n != null && n in 1..99 && f != null && free(used, i..i + 3, tokens.size)) {
                val rrule = if (n > 1) "FREQ=$f;INTERVAL=$n" else "FREQ=$f"
                return RecurrenceCandidate(rrule, i..i + 3, period = periodOf(f, n))
            }
        }

        if (t !in every) return null
        val next = tokens.getOrNull(i + 1)?.lower ?: return null

        // «каждое второе воскресенье месяца» — порядковый день недели; слово
        // «месяца» обязательно: голое «каждый второй вторник» в живой речи
        // значит «раз в две недели», угадывать не берёмся
        ordinals[next]?.let { ord ->
            DateRules.weekdays[tokens.getOrNull(i + 2)?.lower]?.let { dow ->
                if (tokens.getOrNull(i + 3)?.lower == "месяца" && free(used, i..i + 3, tokens.size)) {
                    return ordinalMonthlyRecurrence(ord, dow, i..i + 3)
                }
            }
        }

        // «каждый вторник [среду и пятницу …]»
        DateRules.weekdays[next]?.let { first ->
            if (free(used, i..i + 1, tokens.size)) {
                val days = mutableSetOf(first)
                val end = consumeDays(tokens, i + 2, used, days) { w ->
                    DateRules.weekdays[w]?.let(::setOf)
                }
                return weeklyRecurrence(days, i until end)
            }
        }

        // «каждое утро/вечер/ночь» — ежедневно + половина суток для часа
        dayHalves[next]?.let { half ->
            if (free(used, i..i + 1, tokens.size)) {
                return RecurrenceCandidate("FREQ=DAILY", i..i + 1, period = Period.ofDays(1), dayHalf = half)
            }
        }

        // «каждые выходные» — как «по выходным»
        if (next == "выходные" && free(used, i..i + 1, tokens.size)) {
            return weeklyRecurrence(poDays.getValue("выходным"), i..i + 1)
        }

        // «каждый день/неделю/месяц/год»
        unitFreq[next]?.let {
            if (free(used, i..i + 1, tokens.size)) {
                return RecurrenceCandidate("FREQ=$it", i..i + 1, period = periodOf(it, 1))
            }
        }

        val n = next.toIntOrNull() ?: intervalWords[next] ?: return null
        val third = tokens.getOrNull(i + 2)?.lower

        // «каждое 15 число»
        if (third in setOf("число", "числа") && n in 1..31 && free(used, i..i + 2, tokens.size)) {
            return RecurrenceCandidate(
                "FREQ=MONTHLY;BYMONTHDAY=$n", i..i + 2,
                period = Period.ofMonths(1), anchorMonthDay = n,
            )
        }

        // «каждые 2 недели»; INTERVAL=1 не пишем — это дефолт RFC 5545
        val freq = unitFreq[third]
        if (freq != null && n in 1..99 && free(used, i..i + 2, tokens.size)) {
            val rrule = if (n > 1) "FREQ=$freq;INTERVAL=$n" else "FREQ=$freq"
            return RecurrenceCandidate(rrule, i..i + 2, period = periodOf(freq, n))
        }

        // «каждое 26» — эллипсис «каждого 26-го числа»; строго средний род:
        // «каждые 3 занятия» месячным повтором стать не должно
        if (t == "каждое" && n in 1..31 && free(used, i..i + 1, tokens.size)) {
            return RecurrenceCandidate(
                "FREQ=MONTHLY;BYMONTHDAY=$n", i..i + 1,
                period = Period.ofMonths(1), anchorMonthDay = n,
            )
        }

        return null
    }

    /** Хвост списка дней `[и] <день>`… начиная с start; возвращает индекс за последним съеденным. */
    private fun consumeDays(
        tokens: List<Token>,
        start: Int,
        used: BooleanArray,
        days: MutableSet<DayOfWeek>,
        lookup: (String) -> Set<DayOfWeek>?,
    ): Int {
        var j = start
        while (true) {
            val k = if (tokens.getOrNull(j)?.lower == "и") j + 1 else j
            val word = tokens.getOrNull(k)?.lower ?: break
            if ((j..k).any { used[it] }) break
            days += lookup(word) ?: break
            j = k + 1
        }
        return j
    }

}
