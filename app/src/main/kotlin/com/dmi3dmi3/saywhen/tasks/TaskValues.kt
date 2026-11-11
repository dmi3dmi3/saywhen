package com.dmi3dmi3.saywhen.tasks

import com.dmi3dmi3.saywhen.parser.ParsedEvent
import com.dmi3dmi3.saywhen.parser.TokenMatch

fun taskValues(event: ParsedEvent, listId: Long?, noDueDate: Boolean): Map<String, Any> {
    val values = linkedMapOf<String, Any>("title" to event.title)
    if (listId != null) values["list_id"] = listId
    val dated = !event.allDay || event.matches.any {
        it.field == TokenMatch.Field.DATE || it.field == TokenMatch.Field.RECURRENCE
    }
    if (dated || !noDueDate) {
        values["due_date"] = if (event.allDay) {
            event.start.toLocalDate().atStartOfDay(event.start.zone).toInstant().toEpochMilli()
        } else {
            event.start.toInstant().toEpochMilli()
        }
        values["due_all_day"] = if (event.allDay) 1 else 0
    }
    event.rrule?.let {
        values["recurrence"] = it
        values["repeat_from"] = "due_date"
    }
    return values
}

fun reminderOffsetMs(minutesBefore: Int): Long = -minutesBefore * 60_000L
