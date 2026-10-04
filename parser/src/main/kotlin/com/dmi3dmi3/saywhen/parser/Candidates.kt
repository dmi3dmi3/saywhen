package com.dmi3dmi3.saywhen.parser

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.Period
import java.time.temporal.ChronoUnit

private val byDayCodes = mapOf(
    DayOfWeek.MONDAY to "MO", DayOfWeek.TUESDAY to "TU", DayOfWeek.WEDNESDAY to "WE",
    DayOfWeek.THURSDAY to "TH", DayOfWeek.FRIDAY to "FR", DayOfWeek.SATURDAY to "SA",
    DayOfWeek.SUNDAY to "SU",
)

internal val workdays: Set<DayOfWeek> = setOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
)
internal val weekend: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

internal fun weeklyRecurrence(days: Set<DayOfWeek>, range: IntRange) = RecurrenceCandidate(
    "FREQ=WEEKLY;BYDAY=" + days.sorted().joinToString(",") { byDayCodes.getValue(it) },
    range,
    period = Period.ofWeeks(1),
    anchorDays = days,
)

internal fun ordinalMonthlyRecurrence(ord: Int, day: DayOfWeek, range: IntRange) = RecurrenceCandidate(
    "FREQ=MONTHLY;BYDAY=$ord${byDayCodes.getValue(day)}",
    range,
    period = Period.ofMonths(1),
    anchorOrdinal = ord,
    anchorDays = setOf(day),
)

internal fun periodOf(freq: String, n: Int): Period = when (freq) {
    "DAILY" -> Period.ofDays(n)
    "WEEKLY" -> Period.ofWeeks(n)
    "MONTHLY" -> Period.ofMonths(n)
    else -> Period.ofYears(n)
}

internal fun freqOf(unit: ChronoUnit): String = when (unit) {
    ChronoUnit.DAYS -> "DAILY"
    ChronoUnit.WEEKS -> "WEEKLY"
    ChronoUnit.MONTHS -> "MONTHLY"
    else -> "YEARLY"
}

internal fun isMonthOrdinal(ord: Int): Boolean = ord in 1..5 || ord == -1

internal enum class DayHalf {
    MORNING { override fun resolve(h: Int) = if (h == 12) 0 else h },
    AFTERNOON { override fun resolve(h: Int) = if (h < 12) h + 12 else 12 },
    EVENING { override fun resolve(h: Int) = if (h < 12) h + 12 else 0 };
    abstract fun resolve(h: Int): Int
}

internal data class DateCandidate(
    val date: LocalDate,
    val tokens: IntRange,
    val endDate: LocalDate? = null,
    val fromWeekday: Boolean = false,
    val dayHalf: DayHalf? = null,
    val confidence: Confidence = Confidence.EXPLICIT,
)

internal fun dateRangeCandidate(
    today: LocalDate,
    tokens: List<Token>,
    startDay: Int, startMonth: Int,
    endDay: Int, endMonth: Int,
    range: IntRange,
): DateCandidate? {
    val year = yearToken(tokens.getOrNull(range.last + 1)?.lower)
    val end = (if (year != null) dateOrNull(year, endMonth, endDay) else upcomingDate(today, endMonth, endDay))
        ?: return null
    val start = dateOrNull(end.year, startMonth, startDay) ?: return null
    if (!end.isAfter(start)) return null
    return DateCandidate(start, if (year != null) range.first..range.last + 1 else range, endDate = end)
}

internal fun yearToken(s: String?): Int? =
    s?.takeIf { it.length == 4 }?.toIntOrNull()?.takeIf { it in YEARS }

private val dayPairPattern = Regex("""(\d{1,2})\.?\s*-\s*(\d{1,2})\.?""")

internal fun dayPair(s: String?): Pair<Int, Int>? {
    val m = s?.let { dayPairPattern.matchEntire(it) } ?: return null
    val (d1, d2) = m.destructured
    return (d1.toInt() to d2.toInt()).takeIf { it.first in DAY_OF_MONTH && it.second in DAY_OF_MONTH }
}

internal data class TimeCandidate(
    val time: LocalTime,
    val duration: Duration?,
    val tokens: IntRange,
    val twelveHour: Boolean = false,
    val confidence: Confidence = Confidence.EXPLICIT,
)

internal data class ReminderCandidate(
    val minutes: Int,
    val tokens: IntRange,
    val confidence: Confidence = Confidence.EXPLICIT,
)

internal data class DurationCandidate(
    val duration: Duration,
    val tokens: IntRange,
    val confidence: Confidence = Confidence.EXPLICIT,
)

internal data class RecurrenceCandidate(
    val rrule: String,
    val tokens: IntRange,
    val period: Period,
    val anchorDays: Set<DayOfWeek> = emptySet(),
    val anchorMonthDay: Int? = null,
    val anchorOrdinal: Int? = null,
    val untilDate: LocalDate? = null,
    val count: Int? = null,
    val extraTokens: List<IntRange> = emptyList(),
    val dayHalf: DayHalf? = null,
    val confidence: Confidence = Confidence.EXPLICIT,
) {
    fun resolveStartDate(base: LocalDate): LocalDate {
        var d = base
        when {
            anchorOrdinal != null ->
                while (!isOrdinalDay(d)) d = d.plusDays(1)
            anchorMonthDay != null -> while (d.dayOfMonth != anchorMonthDay) d = d.plusDays(1)
            anchorDays.isNotEmpty() -> while (d.dayOfWeek !in anchorDays) d = d.plusDays(1)
        }
        return d
    }

    private fun isOrdinalDay(d: LocalDate): Boolean {
        if (d.dayOfWeek !in anchorDays) return false
        return if (anchorOrdinal!! > 0) (d.dayOfMonth - 1) / 7 + 1 == anchorOrdinal
        else d.month != d.plusWeeks(1).month
    }

    fun nextOccurrence(from: LocalDate): LocalDate = when {
        anchorMonthDay != null || anchorDays.isNotEmpty() -> resolveStartDate(from.plusDays(1))
        else -> from.plus(period)
    }
}
