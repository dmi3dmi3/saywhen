package com.dmi3dmi3.saywhen.parser.de

import com.dmi3dmi3.saywhen.parser.AMOUNT
import com.dmi3dmi3.saywhen.parser.CIRCLE_HOUR
import com.dmi3dmi3.saywhen.parser.ClockText
import com.dmi3dmi3.saywhen.parser.Confidence
import com.dmi3dmi3.saywhen.parser.DayHalf
import com.dmi3dmi3.saywhen.parser.TimeCandidate
import com.dmi3dmi3.saywhen.parser.TimeRule
import com.dmi3dmi3.saywhen.parser.Token
import com.dmi3dmi3.saywhen.parser.bareHourAfterClaim
import com.dmi3dmi3.saywhen.parser.decimalNumber
import com.dmi3dmi3.saywhen.parser.free
import com.dmi3dmi3.saywhen.parser.refineCircle
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.math.roundToLong

internal object DeTimeRules : TimeRule {

    private val oneOclock = setOf("ein", "eins")

    private val fixedTimes = mapOf(
        "mittag" to LocalTime.NOON, "mittags" to LocalTime.NOON,
        "mitternacht" to LocalTime.MIDNIGHT,
    )

    private val dayparts = mapOf(
        "früh" to DayHalf.MORNING, "frueh" to DayHalf.MORNING,
        "morgen" to DayHalf.MORNING, "morgens" to DayHalf.MORNING,
        "vormittag" to DayHalf.MORNING, "vormittags" to DayHalf.MORNING,
        "nachmittag" to DayHalf.AFTERNOON, "nachmittags" to DayHalf.AFTERNOON,
        "abend" to DayHalf.EVENING, "abends" to DayHalf.EVENING,
        "nacht" to DayHalf.MORNING, "nachts" to DayHalf.MORNING,
    )

    private val bareDayparts = setOf(
        "früh", "frueh", "morgens", "vormittags", "nachmittags", "abends", "nachts",
    )

    private val gluedUhr = Regex("""(\d{1,2})uhr""")

    override fun find(tokens: List<Token>, used: BooleanArray): TimeCandidate? {
        for (i in tokens.indices) {
            if (used[i]) continue
            matchAt(tokens, i, used)?.let { return it }
        }
        return null
    }

    private fun matchAt(tokens: List<Token>, i: Int, used: BooleanArray): TimeCandidate? {
        val t = tokens[i].lower

        if ((t == "von" || t == "zwischen") && free(used, i..i + 3, tokens.size)) {
            val mid = if (t == "von") "bis" else "und"
            val from = clockToken(tokens.getOrNull(i + 1)?.lower)
            val to = clockToken(tokens.getOrNull(i + 3)?.lower)
            if (from != null && to != null && from != to && tokens.getOrNull(i + 2)?.lower == mid) {
                return TimeCandidate(from, ClockText.span(from, to), i..i + 3)
            }
        }

        fixedTimes[t]?.let { return TimeCandidate(it, null, i..i, confidence = Confidence.STRONG) }

        if (t == "halb" && free(used, i..i + 1, tokens.size)) {
            val raw = tokens.getOrNull(i + 1)?.lower
            val h = circleHour(raw) ?: raw?.toIntOrNull()?.takeIf { it in CIRCLE_HOUR }
            if (h != null) {
                var end = i + 1
                if (tokens.getOrNull(end + 1)?.lower == "uhr" && free(used, end + 1..end + 1, tokens.size)) end++
                return refine(tokens, used, LocalTime.of(h - 1, 30), i..end, inCircle = true, Confidence.STRONG)
            }
        }

        nachVor(tokens, i, used)?.let { return it }

        if (t == "um" && free(used, i..i + 1, tokens.size)) {
            val raw = tokens.getOrNull(i + 1)?.lower
            val base = clockToken(raw)
            if (base != null && raw != null) {
                var end = i + 1
                if (tokens.getOrNull(end + 1)?.lower == "uhr" && free(used, end + 1..end + 1, tokens.size)) {
                    end++
                    val mm = ClockText.pairMinutes(tokens.getOrNull(end + 1)?.lower)
                    if (mm != null && free(used, end + 1..end + 1, tokens.size)) {
                        return refine(tokens, used, base.withMinute(mm), i..end + 1)
                    }
                    return refine(tokens, used, base, i..end)
                }
                if (raw.let { ':' !in it && '.' !in it }) {
                    val mm = ClockText.pairMinutes(tokens.getOrNull(i + 2)?.lower)
                    if (mm != null && free(used, i + 2..i + 2, tokens.size)) {
                        return refine(tokens, used, base.withMinute(mm), i..i + 2)
                    }
                }
                return refine(tokens, used, base, i..end)
            }
            circleHour(raw)?.let { h ->
                var end = i + 1
                if (tokens.getOrNull(end + 1)?.lower == "uhr" && free(used, end + 1..end + 1, tokens.size)) end++
                return refine(tokens, used, LocalTime.of(h, 0), i..end, inCircle = true, Confidence.STRONG)
            }
        }

        run {
            val h = t.toIntOrNull()?.takeIf { it in 0..23 && t.length <= 2 } ?: return@run
            if (tokens.getOrNull(i + 1)?.lower != "uhr" || !free(used, i..i + 1, tokens.size)) return@run
            var time = LocalTime.of(h, 0)
            var end = i + 1
            val mm = ClockText.pairMinutes(tokens.getOrNull(i + 2)?.lower)
            if (mm != null && free(used, i + 2..i + 2, tokens.size)) {
                time = time.withMinute(mm); end = i + 2
            }
            return refine(tokens, used, time, i..end)
        }

        gluedUhr.matchEntire(t)?.let { m ->
            m.groupValues[1].toIntOrNull()?.takeIf { it in 0..23 }?.let { h ->
                return refine(tokens, used, LocalTime.of(h, 0), i..i)
            }
        }

        if (':' in t) {
            ClockText.clock(t)?.let { return refine(tokens, used, it, i..i) }
        }

        return null
    }

    private fun nachVor(tokens: List<Token>, i: Int, used: BooleanArray): TimeCandidate? {
        val t = tokens[i].lower
        val minutes = when {
            t == "viertel" -> 15
            else -> t.toIntOrNull()?.takeIf { it in 1..30 && t.length <= 2 } ?: return null
        }
        var j = i + 1
        if (t != "viertel" && tokens.getOrNull(j)?.lower in DeWords.minuteWords) j++
        val dir = tokens.getOrNull(j)?.lower ?: return null
        if (dir != "nach" && dir != "vor") return null
        val raw = tokens.getOrNull(j + 1)?.lower
        val h = circleHour(raw) ?: raw?.toIntOrNull()?.takeIf { it in CIRCLE_HOUR } ?: return null
        if (!free(used, i..j + 1, tokens.size)) return null
        val time = if (dir == "nach") LocalTime.of(h, minutes)
                   else LocalTime.of(if (h == 1) 12 else h - 1, 60 - minutes)
        var end = j + 1
        if (tokens.getOrNull(end + 1)?.lower == "uhr" && free(used, end + 1..end + 1, tokens.size)) end++
        return refine(tokens, used, time, i..end, inCircle = true, Confidence.STRONG)
    }

    override fun offsetTime(tokens: List<Token>, used: BooleanArray, now: ZonedDateTime): TimeCandidate? {
        for (i in tokens.indices) {
            if (used[i] || tokens[i].lower != "in") continue
            var j = i + 1
            val next = tokens.getOrNull(j)?.lower ?: continue
            val base = now.toLocalTime().withSecond(0).withNano(0)
            if (next in setOf("einer", "eine")) {
                if (tokens.getOrNull(j + 1)?.lower == "halben" &&
                    tokens.getOrNull(j + 2)?.lower == "stunde" && free(used, i..j + 2, tokens.size)
                ) {
                    return TimeCandidate(base.plusMinutes(30), null, i..j + 2)
                }
                when (tokens.getOrNull(j + 1)?.lower) {
                    "viertelstunde" -> if (free(used, i..j + 1, tokens.size)) {
                        return TimeCandidate(base.plusMinutes(15), null, i..j + 1)
                    }
                    "dreiviertelstunde" -> if (free(used, i..j + 1, tokens.size)) {
                        return TimeCandidate(base.plusMinutes(45), null, i..j + 1)
                    }
                }
            }
            decimalNumber(next)?.let { v ->
                if (tokens.getOrNull(j + 1)?.lower in DeWords.hourWords && free(used, i..j + 1, tokens.size)) {
                    return TimeCandidate(base.plusMinutes((v * 60).roundToLong()), null, i..j + 1)
                }
            }
            val n = next.toLongOrNull() ?: DeWords.cardinals[next]?.toLong() ?: continue
            if (n !in AMOUNT) continue
            val time = when (tokens.getOrNull(j + 1)?.lower) {
                in DeWords.hourWords -> base.plusHours(n)
                in DeWords.minuteWords -> base.plusMinutes(n)
                else -> null
            } ?: continue
            if (free(used, i..j + 1, tokens.size)) return TimeCandidate(time, null, i..j + 1)
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
    ): TimeCandidate = refineCircle(time, range, inCircle, confidence) { k -> daypartAt(tokens, used, k) }

    private fun daypartAt(tokens: List<Token>, used: BooleanArray, k: Int): Pair<(Int) -> Int, Int>? {
        val first = tokens.getOrNull(k)?.lower ?: return null
        if (first in bareDayparts && free(used, k..k, tokens.size)) {
            return dayparts.getValue(first)::resolve to k
        }
        var j = k
        when (first) {
            "am" -> j = k + 1
            "in" -> {
                if (tokens.getOrNull(k + 1)?.lower != "der") return null
                j = k + 2
            }
            else -> return null
        }
        val half = dayparts[tokens.getOrNull(j)?.lower] ?: return null
        if (!free(used, k..j, tokens.size)) return null
        return half::resolve to j
    }

    private fun circleHour(word: String?): Int? =
        DeWords.cardinals[word]?.takeIf { it in CIRCLE_HOUR && (it != 1 || word in oneOclock) }

    private fun clockToken(s: String?): LocalTime? = ClockText.clock(s) ?: ClockText.dottedClock(s)
}
