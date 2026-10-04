package com.dmi3dmi3.saywhen.parser

import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

internal fun dateOrNull(year: Int, month: Int, day: Int): LocalDate? =
    try {
        LocalDate.of(year, month, day)
    } catch (e: DateTimeException) {
        null
    }

internal fun upcomingDate(today: LocalDate, month: Int, day: Int): LocalDate? =
    dateOrNull(today.year, month, day)?.takeIf { !it.isBefore(today) }
        ?: dateOrNull(today.year + 1, month, day)

internal fun weekdayDate(today: LocalDate, dow: DayOfWeek, nextWeek: Boolean): LocalDate {
    val base = if (nextWeek) today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)) else today
    return base.with(TemporalAdjusters.nextOrSame(dow))
}

internal fun eveOfMonth(today: LocalDate, month: Int): LocalDate {
    var first = LocalDate.of(today.year, month, 1)
    if (!first.isAfter(today)) first = first.plusYears(1)
    return first.minusDays(1)
}

internal class PeriodEnds(
    private val weekWords: Set<String>,
    private val monthWords: Set<String>,
    private val yearWords: Set<String>,
    private val monthNames: Map<String, Int>,
) {
    fun endOf(word: String, today: LocalDate): LocalDate? = when (word) {
        in weekWords -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        in monthWords -> today.withDayOfMonth(today.lengthOfMonth())
        in yearWords -> LocalDate.of(today.year, 12, 31)
        else -> monthNames[word]?.let { m ->
            val thisYear = LocalDate.of(today.year, m, 1).with(TemporalAdjusters.lastDayOfMonth())
            if (thisYear.isBefore(today)) thisYear.plusYears(1).with(TemporalAdjusters.lastDayOfMonth())
            else thisYear
        }
    }
}
