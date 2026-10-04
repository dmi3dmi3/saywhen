package com.dmi3dmi3.saywhen.parser.ru

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

internal object TimeRules : TimeRule {

    private val gluedHour = Regex("""(\d{1,2})ч""")

    private val fixedTimes = mapOf("полдень" to LocalTime.NOON, "полночь" to LocalTime.MIDNIGHT)

    private val dayparts = mapOf<String, (Int) -> Int>(
        "утра" to { h -> h },
        "дня" to DayHalf.AFTERNOON::resolve,
        "вечера" to DayHalf.EVENING::resolve,
        "ночи" to DayHalf.MORNING::resolve,
    )

    override fun find(tokens: List<Token>, used: BooleanArray): TimeCandidate? {
        for (i in tokens.indices) {
            if (used[i]) continue
            matchAt(tokens, i, used)?.let { return it }
        }
        return null
    }

    private fun matchAt(tokens: List<Token>, i: Int, used: BooleanArray): TimeCandidate? {
        val t = tokens[i].lower

        if (t == "с" && free(used, i..i + 3, tokens.size)) {
            val from = clock(tokens.getOrNull(i + 1)?.lower)
            val to = clock(tokens.getOrNull(i + 3)?.lower)
            if (from != null && to != null && from != to && tokens[i + 2].lower == "до") {
                return TimeCandidate(from, ClockText.span(from, to), i..i + 3)
            }
        }

        fixedTimes[t]?.let { return TimeCandidate(it, null, i..i, confidence = Confidence.STRONG) }

        if (t == "пол" && free(used, i..i + 1, tokens.size)) {
            halfOrdinal(tokens.getOrNull(i + 1)?.lower)?.let { h ->
                return refine(tokens, used, LocalTime.of(h, 30), i..i + 1, inCircle = true, Confidence.STRONG)
            }
        }
        val glued = when {
            t.startsWith("пол-") -> t.removePrefix("пол-")
            t.startsWith("пол") -> t.removePrefix("пол")
            else -> null
        }
        halfOrdinal(glued)?.let { h ->
            return refine(tokens, used, LocalTime.of(h, 30), i..i, inCircle = true, Confidence.STRONG)
        }

        run {
            val h = t.toIntOrNull() ?: return@run
            if (h !in CIRCLE_HOUR) return@run
            if (tokens.getOrNull(i + 1)?.lower !in Words.hourWords) return@run
            val adjust = dayparts[tokens.getOrNull(i + 2)?.lower] ?: return@run
            if (free(used, i..i + 2, tokens.size)) {
                return TimeCandidate(LocalTime.of(adjust(h), 0), null, i..i + 2, confidence = Confidence.STRONG)
            }
        }

        if (t == "в" && free(used, i..i + 1, tokens.size)) {
            val raw = tokens.getOrNull(i + 1)?.lower
            val time = clock(raw) ?: ClockText.dottedClock(raw) ?: gluedHourTime(raw)
            if (time != null && raw != null) {
                if (':' !in raw) {
                    val mm = ClockText.pairMinutes(tokens.getOrNull(i + 2)?.lower)
                    if (mm != null && free(used, i + 2..i + 2, tokens.size)) {
                        return refine(tokens, used, time.withMinute(mm), i..i + 2)
                    }
                    if (tokens.getOrNull(i + 2)?.lower in Words.hourWords && free(used, i + 2..i + 2, tokens.size)) {
                        return refine(tokens, used, time, i..i + 2)
                    }
                }
                return refine(tokens, used, time, i..i + 1)
            }
            hourWord(raw)?.let { h ->
                var end = i + 1
                if (tokens.getOrNull(end + 1)?.lower in Words.hourWords && free(used, end + 1..end + 1, tokens.size)) end++
                return refine(tokens, used, LocalTime.of(h, 0), i..end, confidence = Confidence.STRONG)
            }
        }

        if ('-' in t) {
            ClockText.gluedInterval(t)?.let { (from, d) -> return TimeCandidate(from, d, i..i) }
            return null
        }

        if (':' in t) {
            clock(t)?.let { return refine(tokens, used, it, i..i) }
        }

        val mm = ClockText.pairMinutes(tokens.getOrNull(i + 1)?.lower)
        if (mm != null && free(used, i..i + 1, tokens.size)) {
            clock(t)?.let { return refine(tokens, used, it.withMinute(mm), i..i + 1) }
        }

        return null
    }

    private fun refine(
        tokens: List<Token>,
        used: BooleanArray,
        time: LocalTime,
        range: IntRange,
        inCircle: Boolean = time.hour in CIRCLE_HOUR,
        confidence: Confidence = Confidence.EXPLICIT,
    ): TimeCandidate = refineCircle(time, range, inCircle, confidence) { k -> daypartAt(tokens, used, k) }

    private fun daypartAt(tokens: List<Token>, used: BooleanArray, k: Int): Pair<(Int) -> Int, Int>? =
        dayparts[tokens.getOrNull(k)?.lower]?.takeIf { free(used, k..k, tokens.size) }?.let { it to k }

    override fun offsetTime(tokens: List<Token>, used: BooleanArray, now: ZonedDateTime): TimeCandidate? {
        for (i in tokens.indices) {
            if (used[i] || tokens[i].lower != "через") continue
            val next = tokens.getOrNull(i + 1)?.lower ?: continue
            val base = now.toLocalTime().withSecond(0).withNano(0)
            if (next in Words.hourWords && free(used, i..i + 1, tokens.size)) {
                return TimeCandidate(base.plusHours(1), null, i..i + 1)
            }
            val n = next.toLongOrNull()?.takeIf { it in AMOUNT } ?: continue
            val time = when (tokens.getOrNull(i + 2)?.lower) {
                in Words.hourWords -> base.plusHours(n)
                in Words.minuteWords -> base.plusMinutes(n)
                else -> null
            } ?: continue
            if (free(used, i..i + 2, tokens.size)) return TimeCandidate(time, null, i..i + 2)
        }
        return null
    }

    private fun hourWord(s: String?): Int? =
        Words.cardinals[s]?.takeIf { it in CIRCLE_HOUR } ?: 1.takeIf { s == "час" }

    private fun halfOrdinal(s: String?): Int? =
        Words.ordinals[s]?.takeIf { it in CIRCLE_HOUR }?.let { it - 1 }

    private fun gluedHourTime(s: String?): LocalTime? =
        s?.let { gluedHour.matchEntire(it) }?.groupValues?.get(1)?.toIntOrNull()
            ?.takeIf { it in 0..23 }?.let { LocalTime.of(it, 0) }

    override fun bareHourAfterClaim(tokens: List<Token>, used: BooleanArray): TimeCandidate? =
        bareHourAfterClaim(tokens, used) { time, range ->
            refine(tokens, used, time, range, confidence = Confidence.WEAK)
        }

    private fun clock(s: String?) = ClockText.clock(s)
}
