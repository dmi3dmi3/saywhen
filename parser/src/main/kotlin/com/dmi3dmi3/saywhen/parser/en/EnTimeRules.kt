package com.dmi3dmi3.saywhen.parser.en

import com.dmi3dmi3.saywhen.parser.AMOUNT
import com.dmi3dmi3.saywhen.parser.CIRCLE_HOUR
import com.dmi3dmi3.saywhen.parser.ClockText
import com.dmi3dmi3.saywhen.parser.Confidence
import com.dmi3dmi3.saywhen.parser.DayHalf
import com.dmi3dmi3.saywhen.parser.TimeCandidate
import com.dmi3dmi3.saywhen.parser.TimeRule
import com.dmi3dmi3.saywhen.parser.Token
import com.dmi3dmi3.saywhen.parser.bareHourAfterClaim
import com.dmi3dmi3.saywhen.parser.free
import com.dmi3dmi3.saywhen.parser.refineCircle
import java.time.LocalTime
import java.time.ZonedDateTime

internal object EnTimeRules : TimeRule {

    private val fixedTimes = mapOf("noon" to LocalTime.NOON, "midnight" to LocalTime.MIDNIGHT)

    private val gluedAmPm = Regex("""(\d{1,2})(?::(\d{2}))?(am|pm)""")

    override fun find(tokens: List<Token>, used: BooleanArray): TimeCandidate? {
        for (i in tokens.indices) {
            if (used[i]) continue
            matchAt(tokens, i, used)?.let { return it }
        }
        return null
    }

    private fun matchAt(tokens: List<Token>, i: Int, used: BooleanArray): TimeCandidate? {
        val t = tokens[i].lower

        if (t == "from" && free(used, i..i + 3, tokens.size)) {
            val fromRaw = tokens.getOrNull(i + 1)?.lower
            val toRaw = tokens.getOrNull(i + 3)?.lower
            var from = timeToken(fromRaw)
            val to = timeToken(toRaw)
            if (from != null && to != null && from.hour in CIRCLE_HOUR &&
                amPm(fromRaw) == null && amPm(toRaw) != null
            ) {
                val alt = from.plusHours(12)
                if (ClockText.span(alt, to) < ClockText.span(from, to)) from = alt
            }
            if (from != null && to != null && from != to && tokens[i + 2].lower == "to") {
                return TimeCandidate(from, ClockText.span(from, to), i..i + 3)
            }
        }

        fixedTimes[t]?.let { return TimeCandidate(it, null, i..i, confidence = Confidence.STRONG) }

        if (t == "half" && tokens.getOrNull(i + 1)?.lower == "past" && free(used, i..i + 2, tokens.size)) {
            val h = circleHour(tokens.getOrNull(i + 2)?.lower)
            if (h != null) {
                return refine(tokens, used, LocalTime.of(h, 30), i..i + 2, inCircle = true, Confidence.STRONG)
            }
        }

        if (t == "quarter" && free(used, i..i + 2, tokens.size)) {
            val marker = tokens.getOrNull(i + 1)?.lower
            val h = circleHour(tokens.getOrNull(i + 2)?.lower)
            if (h != null) {
                val time = when (marker) {
                    "past" -> LocalTime.of(h, 15)
                    "to" -> LocalTime.of(if (h == 1) 12 else h - 1, 45)
                    else -> null
                }
                if (time != null) {
                    return refine(tokens, used, time, i..i + 2, inCircle = true, Confidence.STRONG)
                }
            }
        }

        if (t == "at" && free(used, i..i + 1, tokens.size)) {
            val raw = tokens.getOrNull(i + 1)?.lower
            amPm(raw)?.let { return TimeCandidate(it, null, i..i + 1) }
            (ClockText.clock(raw) ?: ClockText.dottedClock(raw))?.let { time ->
                if (raw != null && ':' !in raw) {
                    val mm = ClockText.pairMinutes(tokens.getOrNull(i + 2)?.lower)
                    if (mm != null && free(used, i + 2..i + 2, tokens.size)) {
                        return refine(tokens, used, time.withMinute(mm), i..i + 2)
                    }
                }
                return refine(tokens, used, time, i..i + 1)
            }
            EnWords.cardinals[raw]?.takeIf { it in CIRCLE_HOUR }?.let { h ->
                return refine(tokens, used, LocalTime.of(h, 0), i..i + 1, confidence = Confidence.STRONG)
            }
        }

        amPm(t)?.let { return TimeCandidate(it, null, i..i) }
        ClockText.gluedInterval(t)?.let { (from, d) -> return TimeCandidate(from, d, i..i) }
        if (':' in t) {
            ClockText.clock(t)?.let { return refine(tokens, used, it, i..i) }
        }

        val mm = ClockText.pairMinutes(tokens.getOrNull(i + 1)?.lower)
        if (mm != null && free(used, i..i + 1, tokens.size)) {
            ClockText.clock(t)?.let { return refine(tokens, used, it.withMinute(mm), i..i + 1) }
        }

        return null
    }

    override fun offsetTime(tokens: List<Token>, used: BooleanArray, now: ZonedDateTime): TimeCandidate? {
        for (i in tokens.indices) {
            if (used[i] || tokens[i].lower != "in") continue
            val next = tokens.getOrNull(i + 1)?.lower ?: continue
            val base = now.toLocalTime().withSecond(0).withNano(0)
            val n = next.toLongOrNull()?.takeIf { it in AMOUNT }
                ?: if (next == "a" || next == "an") 1L else continue
            val time = when (tokens.getOrNull(i + 2)?.lower) {
                in EnWords.hourWords -> base.plusHours(n)
                in EnWords.minuteWords -> base.plusMinutes(n)
                else -> null
            } ?: continue
            if (free(used, i..i + 2, tokens.size)) return TimeCandidate(time, null, i..i + 2)
        }
        return null
    }

    override fun bareHourAfterClaim(tokens: List<Token>, used: BooleanArray): TimeCandidate? =
        bareHourAfterClaim(tokens, used) { time, range ->
            refine(tokens, used, time, range, confidence = Confidence.WEAK)
        }

    private fun refine(
        tokens: List<Token>,
        used: BooleanArray,
        time: LocalTime,
        range: IntRange,
        inCircle: Boolean = time.hour in CIRCLE_HOUR,
        confidence: Confidence = Confidence.EXPLICIT,
    ): TimeCandidate = refineCircle(time, range, inCircle, confidence) { k -> amPmAt(tokens, used, k) }

    private val halves = mapOf("am" to DayHalf.MORNING, "pm" to DayHalf.AFTERNOON)

    private fun amPmAt(tokens: List<Token>, used: BooleanArray, k: Int): Pair<(Int) -> Int, Int>? =
        halves[tokens.getOrNull(k)?.lower]?.takeIf { free(used, k..k, tokens.size) }?.let { it::resolve to k }

    private fun circleHour(word: String?): Int? =
        (EnWords.cardinals[word] ?: word?.toIntOrNull())?.takeIf { it in CIRCLE_HOUR }

    private fun amPm(s: String?): LocalTime? {
        s ?: return null
        val m = gluedAmPm.matchEntire(s) ?: return null
        val h = m.groupValues[1].toInt().takeIf { it in CIRCLE_HOUR } ?: return null
        val minute = m.groupValues[2].ifEmpty { "0" }.toInt().takeIf { it in 0..59 } ?: return null
        return LocalTime.of(halves.getValue(m.groupValues[3]).resolve(h), minute)
    }

    private fun timeToken(s: String?): LocalTime? = amPm(s) ?: ClockText.clock(s)
}
