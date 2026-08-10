package com.dmi3dmi3.saywhen.parser.en

import com.dmi3dmi3.saywhen.parser.DateCandidate
import com.dmi3dmi3.saywhen.parser.Token
import com.dmi3dmi3.saywhen.parser.dateRangeCandidate
import com.dmi3dmi3.saywhen.parser.dayPair
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Число дня с необязательным порядковым суффиксом: "3", "3rd", "21st". */
internal fun dayNumber(s: String?): Int? {
    s ?: return null
    val m = Regex("""(\d{1,2})(?:st|nd|rd|th)?""").matchEntire(s) ?: return null
    return m.groupValues[1].toInt()
}

internal object EnDateRules {

    private val relative = mapOf("today" to 0L, "tomorrow" to 1L)

    val weekdays = mapOf(  // видима EnRecurrenceRules ("every tuesday")
        "monday" to DayOfWeek.MONDAY, "tuesday" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY, "thursday" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY, "saturday" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY,
        // сокращения: "on fri", "every thu"; омографы sat/sun/wed/mon —
        // трейдофф домена ввода событий, как у месяцев ("may 3")
        "mon" to DayOfWeek.MONDAY,
        "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY,
        "wed" to DayOfWeek.WEDNESDAY, "weds" to DayOfWeek.WEDNESDAY,
        "thu" to DayOfWeek.THURSDAY, "thur" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY,
        "fri" to DayOfWeek.FRIDAY,
        "sat" to DayOfWeek.SATURDAY,
        "sun" to DayOfWeek.SUNDAY,
    )

    val months = mapOf(
        "january" to 1, "february" to 2, "march" to 3, "april" to 4,
        "may" to 5, "june" to 6, "july" to 7, "august" to 8,
        "september" to 9, "october" to 10, "november" to 11, "december" to 12,
        // сокращения: "Aug 3", "Sep 15" — точку отбрасывает токенайзер
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4,
        "jun" to 6, "jul" to 7, "aug" to 8,
        "sep" to 9, "sept" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
    )

    private val offsetUnits = mapOf(
        "day" to ChronoUnit.DAYS, "days" to ChronoUnit.DAYS,
        "week" to ChronoUnit.WEEKS, "weeks" to ChronoUnit.WEEKS,
        "month" to ChronoUnit.MONTHS, "months" to ChronoUnit.MONTHS,
    )

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

        // диапазон — раньше одиночной даты: иначе "Aug 23 to …" съелось бы началом
        matchRange(tokens, i, today)?.let { return it }

        relative[t]?.let { return DateCandidate(today.plusDays(it), i..i) }

        matchWeekday(tokens, i, today)?.let { return it }

        // "August 3[rd]"
        months[t]?.let { month ->
            val day = dayNumber(tokens.getOrNull(i + 1)?.lower)
            if (day != null && day in 1..31) return calendarDate(today, month, day, i..i + 1)
        }

        // "3[rd] August"
        val day = dayNumber(t)
        if (day != null && day in 1..31) {
            months[tokens.getOrNull(i + 1)?.lower]?.let { month ->
                return calendarDate(today, month, day, i..i + 1)
            }
        }

        // "in 2 weeks" / "in a week"
        if (t == "in") {
            val next = tokens.getOrNull(i + 1)?.lower ?: return null
            val amount = next.toLongOrNull() ?: if (next == "a" || next == "an") 1L else return null
            if (amount !in 1..3650) return null
            val unit = offsetUnits[tokens.getOrNull(i + 2)?.lower] ?: return null
            return DateCandidate(today.plus(amount, unit), i..i + 2)
        }

        return null
    }

    /**
     * Диапазон дат: "Aug 23 to 28", "Aug 23-28", "Aug 30 to Sep 2",
     * "23-28 August", "23 to 28 August"; "from" подбирает поглощение сирот.
     */
    private fun matchRange(tokens: List<Token>, i: Int, today: LocalDate): DateCandidate? {
        val t = tokens[i].lower

        // месяц впереди: "Aug 23-28", "Aug 23 to 28 [Sep 2]"
        months[t]?.let { month ->
            val next = tokens.getOrNull(i + 1)?.lower
            dayPair(next)?.let { (d1, d2) ->
                return dateRangeCandidate(today, d1, month, d2, month, i..i + 1)
            }
            val d1 = dayNumber(next)?.takeIf { it in 1..31 } ?: return@let
            if (tokens.getOrNull(i + 2)?.lower != "to") return@let
            var j = i + 3
            val endMonth = months[tokens.getOrNull(j)?.lower]
            if (endMonth != null) j++
            val d2 = dayNumber(tokens.getOrNull(j)?.lower)?.takeIf { it in 1..31 } ?: return@let
            return dateRangeCandidate(today, d1, month, d2, endMonth ?: month, i..j)
        }

        // день впереди: "23-28 August", "23 to 28 August", "30 Aug to 2 Sep"
        dayPair(t)?.let { (d1, d2) ->
            val month = months[tokens.getOrNull(i + 1)?.lower] ?: return@let
            return dateRangeCandidate(today, d1, month, d2, month, i..i + 1)
        }
        val d1 = dayNumber(t)?.takeIf { it in 1..31 } ?: return null
        var j = i + 1
        val startMonth = months[tokens.getOrNull(j)?.lower]
        if (startMonth != null) j++
        if (tokens.getOrNull(j)?.lower != "to") return null
        val d2 = dayNumber(tokens.getOrNull(j + 1)?.lower)?.takeIf { it in 1..31 } ?: return null
        var end = j + 1
        val endMonth = months[tokens.getOrNull(end + 1)?.lower]
        if (endMonth != null) end++
        if (startMonth == null && endMonth == null) return null
        return dateRangeCandidate(today, d1, startMonth ?: endMonth!!, d2, endMonth ?: startMonth!!, i..end)
    }

    /** Дни недели: `[on] [next|this] <day>`; "next" — со следующего понедельника. */
    private fun matchWeekday(tokens: List<Token>, i: Int, today: LocalDate): DateCandidate? {
        var j = i
        if (tokens[j].lower == "on") j++
        val adj = tokens.getOrNull(j)?.lower
        val isNextWeek = adj == "next"
        if (isNextWeek || adj == "this") j++
        val dow = weekdays[tokens.getOrNull(j)?.lower] ?: return null

        val base = if (isNextWeek) today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)) else today
        return DateCandidate(base.with(TemporalAdjusters.nextOrSame(dow)), i..j, fromWeekday = true)
    }

    /** Календарная дата; прошла → следующий год, невалидная → не матч. */
    private fun calendarDate(today: LocalDate, month: Int, day: Int, range: IntRange): DateCandidate? {
        val thisYear = dateOrNull(today.year, month, day)
        val date = if (thisYear != null && !thisYear.isBefore(today)) thisYear
                   else dateOrNull(today.year + 1, month, day)
        return date?.let { DateCandidate(it, range) }
    }

    fun dateOrNull(year: Int, month: Int, day: Int): LocalDate? =
        try {
            LocalDate.of(year, month, day)
        } catch (e: DateTimeException) {
            null
        }
}
