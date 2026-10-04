package com.dmi3dmi3.saywhen.parser

import java.time.ZonedDateTime

internal class Pipeline(
    private val recurrence: RecurrenceRule,
    private val reminder: ReminderRule,
    private val date: DateRule,
    private val time: TimeRule,
    private val duration: DurationRule,
    private val compactHourUnits: Set<String>,
) {
    fun extract(tokens: List<Token>, now: ZonedDateTime, blocked: BooleanArray): Extraction {
        val used = blocked.copyOf()
        fun take(range: IntRange) {
            for (j in range) used[j] = true
        }

        val rec = recurrence.find(tokens, now.toLocalDate(), used)
        rec?.let { r ->
            take(r.tokens)
            r.extraTokens.forEach(::take)
        }

        val rem = listOfNotNull(
            CompactReminder.find(tokens, used, compactHourUnits),
            reminder.find(tokens, used),
        ).maxByOrNull { it.tokens.last }
        rem?.let { take(it.tokens) }

        val d = date.findAll(tokens, now, used).firstOrNull()
        d?.let { take(it.tokens) }

        val t = time.find(tokens, used)
            ?: time.offsetTime(tokens, used, now)
            ?: time.bareHourAfterClaim(tokens, used)
        t?.let { take(it.tokens) }

        val dur = if (t != null && t.duration == null) duration.find(tokens, used) else null

        return Extraction(recurrence = rec, date = d, time = t, duration = dur, reminder = rem)
    }
}
