package com.dmi3dmi3.saywhen.quickadd

import com.dmi3dmi3.saywhen.parser.ParsedEvent
import com.dmi3dmi3.saywhen.parser.TokenMatch
import com.dmi3dmi3.saywhen.settings.durationLabel
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class EventSummary(val icon: SummaryIcon, val segments: List<SummarySegment>)

internal enum class SummaryIcon { CALENDAR, REPEAT }

internal data class SummarySegment(val text: String, val isDefault: Boolean)

internal data class SummaryLabels(
    val today: String,
    val tomorrow: String,
    val allDay: String,
    val locale: Locale,
    val datePattern: String,
    val timePattern: String,
    val hourUnit: String,
    val minuteUnit: String,
    val reminderAtEvent: String,
    val reminderBefore: String,
)

internal fun effectiveReminder(event: ParsedEvent, defaultMinutes: Int?): Int? =
    if (event.allDay) null else event.reminderMinutes ?: defaultMinutes

internal fun previewSummary(
    event: ParsedEvent,
    text: String,
    defaultDuration: Duration,
    defaultReminderMinutes: Int?,
    labels: SummaryLabels,
    today: LocalDate,
): EventSummary {
    val dateFmt = DateTimeFormatter.ofPattern(labels.datePattern, labels.locale)
    val timeFmt = DateTimeFormatter.ofPattern(labels.timePattern, labels.locale)
    val hasDate = event.matches.any { it.field == TokenMatch.Field.DATE }
    val hasTime = event.matches.any { it.field == TokenMatch.Field.TIME }
    val recurrence = event.matches.filter { it.field == TokenMatch.Field.RECURRENCE }

    val segments = mutableListOf<SummarySegment>()

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

    if (event.allDay) {
        segments += SummarySegment(labels.allDay, isDefault = !hasTime)
    } else {
        val end = event.start.plus(event.duration ?: defaultDuration)
        segments += SummarySegment(
            "${event.start.format(timeFmt)}–${end.format(timeFmt)}",
            isDefault = false,
        )
    }

    effectiveReminder(event, defaultReminderMinutes)?.let { minutes ->
        segments += SummarySegment(
            if (minutes == 0) labels.reminderAtEvent
            else labels.reminderBefore.format(
                durationLabel(minutes, labels.hourUnit, labels.minuteUnit),
            ),
            isDefault = event.reminderMinutes == null,
        )
    }

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
