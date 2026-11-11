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

internal data class SummarySegment(
    val text: String,
    val isAssumed: Boolean,
    val dotColor: Int? = null,
)

internal data class SummaryTarget(
    val alias: String,
    val color: Int?,
    val isTaskList: Boolean = false,
)

internal fun chosenTargetKey(tag: String?, aliases: Map<String, String>, defaultTarget: String?): String? =
    tag?.let { alias -> aliases.entries.firstOrNull { it.value == alias }?.key }
        ?: defaultTarget?.takeIf { it in aliases }

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
    val noDueDate: String = "",
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
    target: SummaryTarget? = null,
    tasksNoDueDate: Boolean = false,
): EventSummary {
    val dateFmt = DateTimeFormatter.ofPattern(labels.datePattern, labels.locale)
    val timeFmt = DateTimeFormatter.ofPattern(labels.timePattern, labels.locale)
    val hasDate = event.matches.any { it.field == TokenMatch.Field.DATE }
    val hasTime = event.matches.any { it.field == TokenMatch.Field.TIME }
    val recurrence = event.matches.filter { it.field == TokenMatch.Field.RECURRENCE }

    val segments = mutableListOf<SummarySegment>()

    val taskList = target?.isTaskList == true
    if (taskList && tasksNoDueDate && event.allDay && !hasDate && recurrence.isEmpty()) {
        segments += SummarySegment(labels.noDueDate, isAssumed = true)
        target.let { segments += SummarySegment("#${it.alias}", isAssumed = false, dotColor = it.color) }
        return EventSummary(SummaryIcon.CALENDAR, segments)
    }

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
    segments += SummarySegment(dateText, isAssumed = !hasDate && recurrence.isEmpty())

    if (event.allDay) {
        segments += SummarySegment(labels.allDay, isAssumed = !hasTime)
    } else if (taskList) {
        segments += SummarySegment(event.start.format(timeFmt), isAssumed = false)
    } else {
        val end = event.start.plus(event.duration ?: defaultDuration)
        segments += SummarySegment(
            "${event.start.format(timeFmt)}–${end.format(timeFmt)}",
            isAssumed = false,
        )
    }

    effectiveReminder(event, defaultReminderMinutes)?.let { minutes ->
        segments += SummarySegment(
            if (minutes == 0) labels.reminderAtEvent
            else labels.reminderBefore.format(
                durationLabel(minutes, labels.hourUnit, labels.minuteUnit),
            ),
            isAssumed = false,
        )
    }

    if (event.rrule != null && recurrence.isNotEmpty()) {
        segments += SummarySegment(
            recurrence.joinToString(" ") { text.substring(it.range) },
            isAssumed = false,
        )
    }

    target?.let {
        segments += SummarySegment("#${it.alias}", isAssumed = false, dotColor = it.color)
    }

    return EventSummary(
        icon = if (event.rrule != null) SummaryIcon.REPEAT else SummaryIcon.CALENDAR,
        segments = segments,
    )
}
