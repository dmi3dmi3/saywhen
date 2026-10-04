package com.dmi3dmi3.saywhen.parser

import java.time.Duration
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

internal object EventAssembly {

    private class Claim(var tokens: IntRange, val field: TokenMatch.Field)

    fun assemble(
        text: String,
        tokens: List<Token>,
        blocked: BooleanArray,
        extraction: Extraction,
        orphanWords: Set<String>,
        defaultTitle: String,
        now: ZonedDateTime,
        activityWindow: IntRange,
    ): ParsedEvent {
        val used = BooleanArray(tokens.size) { blocked[it] }
        val claims = mutableListOf<Claim>()

        fun take(range: IntRange, field: TokenMatch.Field) {
            for (j in range) used[j] = true
            claims += Claim(range, field)
        }

        val rec = extraction.recurrence
        val date = extraction.date
        val time = extraction.time
        rec?.let { r ->
            take(r.tokens, TokenMatch.Field.RECURRENCE)
            r.extraTokens.forEach { take(it, TokenMatch.Field.RECURRENCE) }
        }
        date?.let { take(it.tokens, TokenMatch.Field.DATE) }
        time?.let { take(it.tokens, TokenMatch.Field.TIME) }
        extraction.duration?.let { take(it.tokens, TokenMatch.Field.DURATION) }
        extraction.reminder?.let { take(it.tokens, TokenMatch.Field.REMINDER) }

        for (i in tokens.indices.reversed()) {
            if (!used[i] && tokens[i].lower in orphanWords &&
                used.getOrNull(i + 1) == true && !blocked[i + 1]
            ) {
                used[i] = true
                claims.find { it.tokens.first == i + 1 }?.let { it.tokens = i..it.tokens.last }
            }
        }

        val baseDate = date?.date ?: now.toLocalDate()
        val startDate = rec?.resolveStartDate(baseDate) ?: baseDate
        val allDay = time == null
        val dayHalf = rec?.dayHalf ?: date?.dayHalf
        var start = when {
            allDay -> startDate.atStartOfDay(now.zone)
            time!!.twelveHour && dayHalf != null -> {
                val hour = dayHalf.resolve(time.time.hour)
                val date = if (dayHalf == DayHalf.EVENING && time.time.hour == 12) {
                    startDate.plusDays(1)
                } else {
                    startDate
                }
                date.atTime(time.time.withHour(hour)).atZone(now.zone)
            }
            time.twelveHour -> TwelveHourClock.resolve(time.time, startDate, now.zone, activityWindow)
            else -> startDate.atTime(time.time).atZone(now.zone)
        }

        if (!allDay && !start.isAfter(now)) {
            start = when {
                rec != null -> {
                    val next = rec.nextOccurrence(startDate)
                    if (rec.untilDate != null && next.isAfter(rec.untilDate)) start
                    else next.atTime(start.toLocalTime()).atZone(now.zone)
                }
                date == null -> start.plusDays(1)
                date.fromWeekday -> start.plusWeeks(1)
                else -> start
            }
        }

        val rangeDuration = date?.endDate
            ?.takeIf { allDay }
            ?.let { Duration.ofDays(ChronoUnit.DAYS.between(date.date, it) + 1) }

        return ParsedEvent(
            title = buildTitle(text, tokens, used, blocked, defaultTitle),
            start = start,
            allDay = allDay,
            duration = time?.duration ?: extraction.duration?.duration ?: rangeDuration,
            rrule = rec?.let { finalRrule(it, allDay, now) },
            reminderMinutes = extraction.reminder?.minutes,
            matches = claims
                .map { TokenMatch(charSpan(tokens, it.tokens), it.field) }
                .sortedBy { it.range.first },
        )
    }

    private fun finalRrule(rec: RecurrenceCandidate, allDay: Boolean, now: ZonedDateTime): String {
        val until = rec.untilDate?.let { d ->
            if (allDay) DateTimeFormatter.BASIC_ISO_DATE.format(d)
            else d.atTime(23, 59, 59).atZone(now.zone)
                .withZoneSameInstant(ZoneOffset.UTC)
                .format(UNTIL_UTC)
        }
        return buildString {
            append(rec.rrule)
            until?.let { append(";UNTIL=").append(it) }
            rec.count?.let { append(";COUNT=").append(it) }
        }
    }

    private val UNTIL_UTC: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

    private fun buildTitle(
        text: String,
        tokens: List<Token>,
        used: BooleanArray,
        blocked: BooleanArray,
        defaultTitle: String,
    ): String {
        val sb = StringBuilder(text)
        for (i in tokens.indices.reversed()) {
            if (used[i] && !blocked[i]) sb.delete(tokens[i].range.first, tokens[i].range.last + 1)
        }
        return sb.toString()
            .replace(Regex("""(?:^|(?<=\s))['’]+(?=\s|$)"""), "")
            .replace(Regex("\\s+"), " ")
            .replace(Regex(""" ([,.;:?]|!(?!\d))"""), "$1")
            .replace(Regex(",{2,}"), ",")
            .trim { it.isWhitespace() || it in ",.;:—-" }
            .let { if (it.any(Char::isLetterOrDigit)) it else defaultTitle }
    }

    private fun charSpan(tokens: List<Token>, range: IntRange): IntRange =
        tokens[range.first].range.first..tokens[range.last].range.last
}
