package com.dmi3dmi3.saywhen.parser

import java.time.Duration
import java.time.ZonedDateTime

data class TokenMatch(val range: IntRange, val field: Field) {
    enum class Field { DATE, TIME, DURATION, RECURRENCE, REMINDER }
}

data class ParsedEvent(
    val title: String,
    val start: ZonedDateTime,
    val allDay: Boolean,
    val duration: Duration?,
    val rrule: String?,
    val reminderMinutes: Int? = null,
    val matches: List<TokenMatch>,
)

interface EventParser {

    fun parse(text: String, now: ZonedDateTime, blockedRanges: List<IntRange> = emptyList()): ParsedEvent
}
