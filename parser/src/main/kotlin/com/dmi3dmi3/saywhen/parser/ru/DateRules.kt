package com.dmi3dmi3.saywhen.parser.ru

import com.dmi3dmi3.saywhen.parser.Confidence
import com.dmi3dmi3.saywhen.parser.DateCandidate
import com.dmi3dmi3.saywhen.parser.DayHalf
import com.dmi3dmi3.saywhen.parser.Token
import com.dmi3dmi3.saywhen.parser.dateRangeCandidate
import com.dmi3dmi3.saywhen.parser.dayPair
import com.dmi3dmi3.saywhen.parser.yearToken
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

internal object DateRules {

    private val relative = mapOf(
        "сегодня" to 0L,
        "завтра" to 1L,
        "послезавтра" to 2L,
    )

    val weekdays = mapOf(  // видима RecurrenceRules («каждый вторник»)
        "понедельник" to DayOfWeek.MONDAY,
        "вторник" to DayOfWeek.TUESDAY,
        "среда" to DayOfWeek.WEDNESDAY, "среду" to DayOfWeek.WEDNESDAY,
        "четверг" to DayOfWeek.THURSDAY,
        "пятница" to DayOfWeek.FRIDAY, "пятницу" to DayOfWeek.FRIDAY,
        "суббота" to DayOfWeek.SATURDAY, "субботу" to DayOfWeek.SATURDAY,
        "воскресенье" to DayOfWeek.SUNDAY,
        // сокращения: «в пт», «каждый вт» — точку отбрасывает токенайзер
        "пн" to DayOfWeek.MONDAY, "пон" to DayOfWeek.MONDAY,
        "вт" to DayOfWeek.TUESDAY,
        "ср" to DayOfWeek.WEDNESDAY,
        "чт" to DayOfWeek.THURSDAY,
        "пт" to DayOfWeek.FRIDAY,
        "сб" to DayOfWeek.SATURDAY, "суб" to DayOfWeek.SATURDAY,
        "вс" to DayOfWeek.SUNDAY, "вск" to DayOfWeek.SUNDAY, "воскр" to DayOfWeek.SUNDAY,
    )

    val months = mapOf(  // видима RecurrenceRules («до конца августа»)
        "января" to 1, "февраля" to 2, "марта" to 3, "апреля" to 4,
        "мая" to 5, "июня" to 6, "июля" to 7, "августа" to 8,
        "сентября" to 9, "октября" to 10, "ноября" to 11, "декабря" to 12,
        // сокращения: «1 авг», «15 сент.» — точку отбрасывает токенайзер
        "янв" to 1, "фев" to 2, "февр" to 2, "мар" to 3, "апр" to 4,
        "май" to 5, "июн" to 6, "июл" to 7, "авг" to 8,
        "сен" to 9, "сент" to 9, "окт" to 10, "ноя" to 11, "нояб" to 11,
        "дек" to 12,
    )

    private val offsetUnits = mapOf(
        "день" to ChronoUnit.DAYS, "дня" to ChronoUnit.DAYS, "дней" to ChronoUnit.DAYS,
        "неделю" to ChronoUnit.WEEKS, "недели" to ChronoUnit.WEEKS, "недель" to ChronoUnit.WEEKS,
        "месяц" to ChronoUnit.MONTHS, "месяца" to ChronoUnit.MONTHS, "месяцев" to ChronoUnit.MONTHS,
        "год" to ChronoUnit.YEARS, "года" to ChronoUnit.YEARS, "лет" to ChronoUnit.YEARS,
    )

    // «завтра вечером» — половина суток приклеивается к дню хинтом для часа
    private val dayHalves = mapOf(
        "утром" to DayHalf.MORNING, "вечером" to DayHalf.EVENING,
        "ночью" to DayHalf.EVENING, "днём" to DayHalf.AFTERNOON, "днем" to DayHalf.AFTERNOON,
    )

    // порядковый день: «восемнадцатое/восемнадцатого [февраля]», составные
    // «двадцать четвёртое»; оба падежа
    private val ordinalUnits = mapOf(
        "первое" to 1, "первого" to 1, "второе" to 2, "второго" to 2,
        "третье" to 3, "третьего" to 3,
        "четвёртое" to 4, "четвертое" to 4, "четвёртого" to 4, "четвертого" to 4,
        "пятое" to 5, "пятого" to 5, "шестое" to 6, "шестого" to 6,
        "седьмое" to 7, "седьмого" to 7, "восьмое" to 8, "восьмого" to 8,
        "девятое" to 9, "девятого" to 9, "десятое" to 10, "десятого" to 10,
        "одиннадцатое" to 11, "одиннадцатого" to 11,
        "двенадцатое" to 12, "двенадцатого" to 12,
        "тринадцатое" to 13, "тринадцатого" to 13,
        "четырнадцатое" to 14, "четырнадцатого" to 14,
        "пятнадцатое" to 15, "пятнадцатого" to 15,
        "шестнадцатое" to 16, "шестнадцатого" to 16,
        "семнадцатое" to 17, "семнадцатого" to 17,
        "восемнадцатое" to 18, "восемнадцатого" to 18,
        "девятнадцатое" to 19, "девятнадцатого" to 19,
        "двадцатое" to 20, "двадцатого" to 20,
        "тридцатое" to 30, "тридцатого" to 30,
    )
    private val ordinalTens = mapOf("двадцать" to 20, "тридцать" to 30)

    // «18-е февраля», «18-го» — цифро-суффикс, токенайзер держит дефис
    private val daySuffix = Regex("""(\d{1,2})-(?:е|ое|го|ого)""")

    /** Все кандидаты по свободным токенам в порядке появления. */
    fun findAll(tokens: List<Token>, now: ZonedDateTime, used: BooleanArray): List<DateCandidate> {
        val today = now.toLocalDate()
        val found = mutableListOf<DateCandidate>()
        var i = 0
        while (i < tokens.size) {
            val candidate = if (used[i]) null else matchAt(tokens, i, today)
            if (candidate != null && candidate.tokens.all { !used[it] }) {
                found += candidate
                i = candidate.tokens.last + 1
            } else {
                i++
            }
        }
        return found
    }

    private fun matchAt(tokens: List<Token>, i: Int, today: LocalDate): DateCandidate? {
        val t = tokens[i].lower

        // диапазон — раньше одиночной даты: иначе «23 августа по …» съелось бы началом
        matchRange(tokens, i, today)?.let { return it }

        relative[t]?.let { shift ->
            dayHalves[tokens.getOrNull(i + 1)?.lower]?.let { half ->
                return DateCandidate(today.plusDays(shift), i..i + 1, dayHalf = half)
            }
            return DateCandidate(today.plusDays(shift), i..i)
        }

        matchWeekday(tokens, i, today)?.let { return it }

        // «3 августа» / «восемнадцатое февраля» — день + месяц в родительном
        calendarAt(tokens, i, today)?.let { return it }

        // «через [N] день/неделю/месяц»
        if (t == "через") {
            val next = tokens.getOrNull(i + 1)?.lower ?: return null
            val amount = next.toLongOrNull()
            if (amount != null) {
                // абсурдные смещения — не матч (и защита от переполнения LocalDate)
                if (amount !in 1..3650) return null
                val unit = offsetUnits[tokens.getOrNull(i + 2)?.lower] ?: return null
                return DateCandidate(today.plus(amount, unit), i..i + 2)
            }
            offsetUnits[next]?.let { return DateCandidate(today.plus(1, it), i..i + 1) }
        }

        return null
    }

    /**
     * Диапазон дат: «23 по 28 августа», «23 августа по 2 сентября»,
     * «23-28 августа»; предлог «с» подбирает поглощение сирот.
     */
    private fun matchRange(tokens: List<Token>, i: Int, today: LocalDate): DateCandidate? {
        val t = tokens[i].lower

        dayPair(t)?.let { (d1, d2) ->
            val month = months[tokens.getOrNull(i + 1)?.lower] ?: return@let
            return dateRangeCandidate(today, d1, month, d2, month, i..i + 1)
        }

        val startDay = t.toIntOrNull()?.takeIf { it in 1..31 } ?: return null
        var j = i + 1
        val startMonth = months[tokens.getOrNull(j)?.lower]
        if (startMonth != null) j++
        if (tokens.getOrNull(j)?.lower != "по") return null
        val endDay = tokens.getOrNull(j + 1)?.lower?.toIntOrNull()?.takeIf { it in 1..31 } ?: return null
        val endMonth = months[tokens.getOrNull(j + 2)?.lower] ?: return null
        return dateRangeCandidate(today, startDay, startMonth ?: endMonth, endDay, endMonth, i..j + 2)
    }

    /** День с позиции k — цифра, «18-е/18-го» или словами; (день, последний токен). */
    private fun dayAt(tokens: List<Token>, k: Int): Pair<Int, Int>? {
        val t = tokens.getOrNull(k)?.lower ?: return null
        t.toIntOrNull()?.let { return (it to k).takeIf { p -> p.first in 1..31 } }
        daySuffix.matchEntire(t)?.let { m ->
            return (m.groupValues[1].toInt() to k).takeIf { p -> p.first in 1..31 }
        }
        ordinalTens[t]?.let { tens ->
            ordinalUnits[tokens.getOrNull(k + 1)?.lower]?.takeIf { it in 1..9 }
                ?.let { return (tens + it) to (k + 1) }
        }
        ordinalUnits[t]?.let { return it to k }
        return null
    }

    /** «3 августа [2027 [года]]» с позиции k; явный год — буквально, даже прошедший. */
    private fun calendarAt(tokens: List<Token>, k: Int, today: LocalDate): DateCandidate? {
        val (day, dEnd) = dayAt(tokens, k) ?: return null
        val month = months[tokens.getOrNull(dEnd + 1)?.lower] ?: return null
        yearToken(tokens.getOrNull(dEnd + 2)?.lower)?.let { y ->
            var end = dEnd + 2
            if (tokens.getOrNull(end + 1)?.lower in setOf("года", "г")) end++
            return dateOrNull(y, month, day)?.let { DateCandidate(it, k..end) }
        }
        val thisYear = dateOrNull(today.year, month, day)
        val date = if (thisYear != null && !thisYear.isBefore(today)) thisYear
                   else dateOrNull(today.year + 1, month, day)
        return date?.let { DateCandidate(it, k..dEnd + 1) }
    }

    private val nextAdj = setOf("следующий", "следующая", "следующую", "следующее")
    private val thisAdj = setOf("этот", "эта", "эту", "это")

    /**
     * Дни недели: `[в|во] [следующий|этот] <день>`.
     * «Следующий» — день следующей календарной недели (с ближайшего понедельника);
     * «этот» и голая форма — ближайший будущий, сегодня подходит.
     */
    private fun matchWeekday(tokens: List<Token>, i: Int, today: LocalDate): DateCandidate? {
        var j = i
        if (tokens[j].lower == "в" || tokens[j].lower == "во") j++
        val adj = tokens.getOrNull(j)?.lower
        var isNextWeek = adj in nextAdj
        if (isNextWeek || adj in thisAdj) j++
        val dow = weekdays[tokens.getOrNull(j)?.lower] ?: return null

        // «Понедельник, 18 февраля» — календарная дата главнее дня недели:
        // день поглощается её матчем, согласованность не проверяем
        calendarAt(tokens, j + 1, today)?.let { d -> return d.copy(tokens = i..d.tokens.last) }

        var end = j
        // «вторник на следующей неделе» / «понедельник этой недели»
        weekQualifier(tokens, j + 1)?.let { (next, qEnd) ->
            if (next) isNextWeek = true
            end = qEnd
        }

        val base = if (isNextWeek) today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)) else today
        return DateCandidate(base.with(TemporalAdjusters.nextOrSame(dow)), i..end, fromWeekday = true)
    }

    /** «[на] следующей/этой неделе» за днём → (следующая ли неделя, конец матча). */
    private fun weekQualifier(tokens: List<Token>, k: Int): Pair<Boolean, Int>? {
        var j = k
        if (tokens.getOrNull(j)?.lower == "на") j++
        val adj = tokens.getOrNull(j)?.lower ?: return null
        val next = adj in setOf("следующей", "будущей")
        if (!next && adj !in setOf("этой", "текущей")) return null
        if (tokens.getOrNull(j + 1)?.lower !in setOf("неделе", "недели")) return null
        return next to j + 1
    }

    fun dateOrNull(year: Int, month: Int, day: Int): LocalDate? =
        try {
            LocalDate.of(year, month, day)
        } catch (e: DateTimeException) {
            null
        }
}
