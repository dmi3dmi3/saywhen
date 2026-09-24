package com.dmi3dmi3.saywhen.calendar

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import com.dmi3dmi3.saywhen.parser.ParsedEvent
import java.time.Duration
import java.time.ZoneOffset

/** ISO-8601 от java.time — валидный RFC 5545 dur-value («PT1H30M»). */
internal fun rfc5545Duration(d: Duration): String = d.toString()

class CalendarWriter(private val resolver: ContentResolver) {

    /**
     * Гочи провайдера (спека, «Запись в календарь»): повтор требует DURATION
     * и запрещает DTEND; all-day живёт в полуночи UTC; all-day+повтор — P1D.
     * [reminderMinutes] — уже действующее напоминание (текст > дефолт,
     * all-day-гейт — [effectiveReminder] на вызывающей стороне).
     * @return id события или null, если вставка не удалась.
     */
    fun insert(
        event: ParsedEvent,
        calendarId: Long,
        defaultDuration: Duration,
        reminderMinutes: Int? = null,
    ): Long? {
        val values = ContentValues().apply {
            put(Events.CALENDAR_ID, calendarId)
            put(Events.TITLE, event.title)
            event.rrule?.let { put(Events.RRULE, it) }

            if (event.allDay) {
                val utcStart = event.start.toLocalDate().atStartOfDay(ZoneOffset.UTC)
                // диапазон дат («с 23 по 28 августа») приносит длительность в днях
                val days = event.duration?.toDays()?.coerceAtLeast(1) ?: 1
                put(Events.ALL_DAY, 1)
                put(Events.EVENT_TIMEZONE, "UTC")
                put(Events.DTSTART, utcStart.toInstant().toEpochMilli())
                if (event.rrule != null) {
                    put(Events.DURATION, "P${days}D")
                } else {
                    put(Events.DTEND, utcStart.plusDays(days).toInstant().toEpochMilli())
                }
            } else {
                val duration = event.duration ?: defaultDuration
                put(Events.EVENT_TIMEZONE, event.start.zone.id)
                put(Events.DTSTART, event.start.toInstant().toEpochMilli())
                if (event.rrule != null) {
                    put(Events.DURATION, rfc5545Duration(duration))
                } else {
                    put(Events.DTEND, event.start.plus(duration).toInstant().toEpochMilli())
                }
            }
        }
        val uri = resolver.insert(Events.CONTENT_URI, values) ?: return null
        val id = runCatching { ContentUris.parseId(uri) }.getOrNull() ?: return null
        // напоминание — отдельной строкой; undo-delete события снесёт её каскадом
        reminderMinutes?.let { minutes ->
            resolver.insert(
                Reminders.CONTENT_URI,
                ContentValues().apply {
                    put(Reminders.EVENT_ID, id)
                    put(Reminders.MINUTES, minutes)
                    put(Reminders.METHOD, Reminders.METHOD_ALERT)
                },
            )
        }
        return id
    }

    /** Undo: удаление только что созданного события. */
    fun delete(eventId: Long): Boolean =
        resolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), null, null) > 0
}
