package com.dmi3dmi3.saywhen.quickadd

import com.dmi3dmi3.saywhen.parser.ParsedEvent
import com.dmi3dmi3.saywhen.parser.TokenMatch
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Строка-итог живого превью (задача 19b, вместо чипов): всегда описывает
 * полное будущее событие — распознанное обычным цветом, дефолты приглушённым
 * (флаг [SummarySegment.isDefault]). Чистая функция — покрыта JVM-тестами.
 */
internal data class EventSummary(val icon: SummaryIcon, val segments: List<SummarySegment>)

internal enum class SummaryIcon { CALENDAR, REPEAT }

internal data class SummarySegment(val text: String, val isDefault: Boolean)

/** Строки и форматы — из ресурсов; у каждой локали свои. */
internal data class SummaryLabels(
    val today: String,
    val tomorrow: String,
    val allDay: String,
    val locale: Locale,
    val datePattern: String,   // ru «EEE, d MMM» / en «EEE, MMM d»
    val timePattern: String,   // ru «H:mm» / en «h:mm a»
)

internal fun previewSummary(
    event: ParsedEvent,
    text: String,
    defaultDuration: Duration,
    labels: SummaryLabels,
    today: LocalDate,
): EventSummary {
    val dateFmt = DateTimeFormatter.ofPattern(labels.datePattern, labels.locale)
    val timeFmt = DateTimeFormatter.ofPattern(labels.timePattern, labels.locale)
    val hasDate = event.matches.any { it.field == TokenMatch.Field.DATE }
    val hasTime = event.matches.any { it.field == TokenMatch.Field.TIME }
    val recurrence = event.matches.filter { it.field == TokenMatch.Field.RECURRENCE }

    val segments = mutableListOf<SummarySegment>()

    // дата: диапазон целиком; иначе «сегодня»/«завтра» словами
    val startDate = event.start.toLocalDate()
    val allDayDays = if (event.allDay) event.duration?.toDays() ?: 1 else 1
    val dateText = when {
        allDayDays > 1 ->
            event.start.format(dateFmt) + " – " +
                event.start.plusDays(allDayDays - 1).format(dateFmt)
        startDate == today -> labels.today
        startDate == today.plusDays(1) -> labels.tomorrow
        else -> event.start.format(dateFmt)
    }
    segments += SummarySegment(dateText, isDefault = !hasDate && recurrence.isEmpty())

    // время: all-day — дефолт, пока время не названо явно
    if (event.allDay) {
        segments += SummarySegment(labels.allDay, isDefault = !hasTime)
    } else {
        val end = event.start.plus(event.duration ?: defaultDuration)
        segments += SummarySegment(
            "${event.start.format(timeFmt)}–${end.format(timeFmt)}",
            isDefault = false,
        )
    }

    // повтор — исходными словами пользователя (включая хвосты «до …»/«N раз»)
    if (event.rrule != null && recurrence.isNotEmpty()) {
        segments += SummarySegment(
            recurrence.joinToString(" ") { text.substring(it.range) },
            isDefault = false,
        )
    }

    return EventSummary(
        icon = if (event.rrule != null) SummaryIcon.REPEAT else SummaryIcon.CALENDAR,
        segments = segments,
    )
}
