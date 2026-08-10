package com.dmi3dmi3.saywhen.parser

import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.Period

/*
 * IR — смысловые кандидаты. Транслятор языка выставляет значения и флаги,
 * ничего не решая про итоговое событие; сборка ([EventAssembly]) интерпретирует
 * флаги, ничего не зная про формы языка.
 */

private val byDayCodes = mapOf(
    DayOfWeek.MONDAY to "MO", DayOfWeek.TUESDAY to "TU", DayOfWeek.WEDNESDAY to "WE",
    DayOfWeek.THURSDAY to "TH", DayOfWeek.FRIDAY to "FR", DayOfWeek.SATURDAY to "SA",
    DayOfWeek.SUNDAY to "SU",
)

/** Недельный повтор по набору дней — RRULE-знание, общее для трансляторов. */
internal fun weeklyRecurrence(days: Set<DayOfWeek>, range: IntRange) = RecurrenceCandidate(
    "FREQ=WEEKLY;BYDAY=" + days.sorted().joinToString(",") { byDayCodes.getValue(it) },
    range,
    period = Period.ofWeeks(1),
    anchorDays = days,
)

/** Кандидат в дату события; endDate != null у диапазона «с 23 по 28 августа». */
internal data class DateCandidate(
    val date: LocalDate,
    val tokens: IntRange,
    val endDate: LocalDate? = null,    // последний день диапазона, включительно
    val fromWeekday: Boolean = false,  // дата выведена из дня недели → сдвигаема на неделю
    val confidence: Confidence = Confidence.EXPLICIT,
)

/**
 * Диапазон дат — знание, общее для трансляторов. Год решает конец (прошёл →
 * следующий, как у одиночной даты; начало при этом может быть в прошлом —
 * событие уже идёт), начало — тот же год: диапазон через новый год не берём.
 * Конец не позже начала или дата невалидна — не диапазон.
 */
internal fun dateRangeCandidate(
    today: LocalDate,
    startDay: Int, startMonth: Int,
    endDay: Int, endMonth: Int,
    range: IntRange,
): DateCandidate? {
    val thisYear = localDateOrNull(today.year, endMonth, endDay)
    val end = if (thisYear != null && !thisYear.isBefore(today)) thisYear
              else localDateOrNull(today.year + 1, endMonth, endDay) ?: return null
    val start = localDateOrNull(end.year, startMonth, startDay) ?: return null
    if (!end.isAfter(start)) return null
    return DateCandidate(start, range, endDate = end)
}

// «23-28» — токенайзер держит пару дней с дефисом одним токеном
private val dayPairPattern = Regex("""(\d{1,2})-(\d{1,2})""")

/** Пара дней «23-28» из одного токена; вне 1..31 — не пара. */
internal fun dayPair(s: String?): Pair<Int, Int>? {
    val m = s?.let { dayPairPattern.matchEntire(it) } ?: return null
    val (d1, d2) = m.destructured
    return (d1.toInt() to d2.toInt()).takeIf { it.first in 1..31 && it.second in 1..31 }
}

private fun localDateOrNull(year: Int, month: Int, day: Int): LocalDate? =
    try {
        LocalDate.of(year, month, day)
    } catch (e: DateTimeException) {
        null
    }

/** Кандидат во время начала; duration != null у интервала «с X до Y». */
internal data class TimeCandidate(
    val time: LocalTime,
    val duration: Duration?,
    val tokens: IntRange,
    val twelveHour: Boolean = false,  // час круга 1..12 без уточнения → пара {h, h+12}
    val confidence: Confidence = Confidence.EXPLICIT,
)

/** Кандидат в длительность: «на час», «на 30 минут». */
internal data class DurationCandidate(
    val duration: Duration,
    val tokens: IntRange,
    val confidence: Confidence = Confidence.EXPLICIT,
)

/**
 * Кандидат в повтор: базовое RRULE + якорь для даты старта. Конец повтора
 * ([untilDate]/[count]) хранится данными, а не строкой: формат UNTIL зависит
 * от all-day/зоны — итоговое правило собирает сборка.
 */
internal data class RecurrenceCandidate(
    val rrule: String,
    val tokens: IntRange,
    val period: Period,                           // шаг повтора («каждые 2 недели» → 2 недели)
    val anchorDays: Set<DayOfWeek> = emptySet(),  // «каждый вторник», «по будням»
    val anchorMonthDay: Int? = null,              // «каждое 15 число»
    val untilDate: LocalDate? = null,             // «до конца августа», «до 15 сентября»
    val count: Int? = null,                       // «10 раз»
    val extraTokens: List<IntRange> = emptyList(),  // хвост конца — не смежен с основным матчем
    val confidence: Confidence = Confidence.EXPLICIT,
) {
    /** Первая дата ≥ base, попадающая в повтор. */
    fun resolveStartDate(base: LocalDate): LocalDate {
        var d = base
        when {
            anchorMonthDay != null -> while (d.dayOfMonth != anchorMonthDay) d = d.plusDays(1)
            anchorDays.isNotEmpty() -> while (d.dayOfWeek !in anchorDays) d = d.plusDays(1)
        }
        return d
    }

    /**
     * Следующее вхождение после from. У повтора без якоря это from + период —
     * не «+1 день»: иначе «каждую неделю в 9», сказанное во вторник после
     * девяти, навсегда стало бы «каждую среду».
     */
    fun nextOccurrence(from: LocalDate): LocalDate = when {
        anchorMonthDay != null || anchorDays.isNotEmpty() -> resolveStartDate(from.plusDays(1))
        else -> from.plus(period)
    }
}
