package com.dmi3dmi3.saywhen.parser

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

fun resolveTwelveHour(hour: Int, window: IntRange): Int {
    val second = if (hour == 12) 0 else hour + 12
    return when {
        hour in window -> hour
        second in window -> second
        else -> hour
    }
}

internal inline fun refineCircle(
    time: LocalTime,
    range: IntRange,
    inCircle: Boolean = time.hour in CIRCLE_HOUR,
    confidence: Confidence = Confidence.EXPLICIT,
    daypartAt: (Int) -> Pair<(Int) -> Int, Int>?,
): TimeCandidate {
    if (!inCircle) return TimeCandidate(time, null, range, confidence = confidence)
    daypartAt(range.last + 1)?.let { (adjust, end) ->
        return TimeCandidate(time.withHour(adjust(time.hour)), null, range.first..end, confidence = confidence)
    }
    return TimeCandidate(time, null, range, twelveHour = true, confidence = confidence)
}

internal object TwelveHourClock {

    fun resolve(time: LocalTime, date: LocalDate, zone: ZoneId, window: IntRange): ZonedDateTime {
        val (first, second) = candidates(time, date, zone)
        return if (resolveTwelveHour(time.hour, window) == first.hour) first else second
    }

    private fun candidates(time: LocalTime, date: LocalDate, zone: ZoneId): Pair<ZonedDateTime, ZonedDateTime> {
        val first = date.atTime(time).atZone(zone)
        val second =
            if (time.hour == 12) date.plusDays(1).atTime(LocalTime.of(0, time.minute)).atZone(zone)
            else date.atTime(time.plusHours(12)).atZone(zone)
        return first to second
    }
}
