package com.dmi3dmi3.saywhen.parser

import java.time.LocalDate
import java.time.ZonedDateTime

internal interface Translator {
    val orphanWords: Set<String>

    val defaultTitle: String

    fun extract(tokens: List<Token>, now: ZonedDateTime, blocked: BooleanArray): Extraction
}

internal interface RecurrenceRule {
    fun find(tokens: List<Token>, today: LocalDate, used: BooleanArray): RecurrenceCandidate?
}

internal interface ReminderRule {
    fun find(tokens: List<Token>, used: BooleanArray): ReminderCandidate?
}

internal interface DateRule {
    fun findAll(tokens: List<Token>, now: ZonedDateTime, used: BooleanArray): List<DateCandidate>
}

internal interface TimeRule {
    fun find(tokens: List<Token>, used: BooleanArray): TimeCandidate?

    fun offsetTime(tokens: List<Token>, used: BooleanArray, now: ZonedDateTime): TimeCandidate?

    fun bareHourAfterClaim(tokens: List<Token>, used: BooleanArray): TimeCandidate?
}

internal interface DurationRule {
    fun find(tokens: List<Token>, used: BooleanArray): DurationCandidate?
}

internal data class Extraction(
    val recurrence: RecurrenceCandidate? = null,
    val date: DateCandidate? = null,
    val time: TimeCandidate? = null,
    val duration: DurationCandidate? = null,
    val reminder: ReminderCandidate? = null,
)
