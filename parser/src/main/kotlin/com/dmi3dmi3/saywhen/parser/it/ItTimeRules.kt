package com.dmi3dmi3.saywhen.parser.it

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

internal object ItTimeRules : TimeRule {

    private val atMarkers = setOf("alle", "all")

    private val oneOclock = setOf("una")

    private val fixedTimes = mapOf("mezzogiorno" to LocalTime.NOON, "mezzanotte" to LocalTime.MIDNIGHT)

    private val dayparts = mapOf(
        "mattina" to DayHalf.MORNING, "mattino" to DayHalf.MORNING,
        "pomeriggio" to DayHalf.AFTERNOON,
        "sera" to DayHalf.EVENING, "stasera" to DayHalf.EVENING,
        "notte" to DayHalf.MORNING,
    )
    private val daypartLinks = setOf("di", "del", "della")

    override fun find(tokens: List<Token>, used: BooleanArray): TimeCandidate? {
        for (i in tokens.indices) {
            if (used[i]) continue
            matchAt(tokens, i, used)?.let { return it }
        }
        return null
    }

    private fun matchAt(tokens: List<Token>, i: Int, used: BooleanArray): TimeCandidate? {
        val t = tokens[i].lower

        if (t == "dalle" && free(used, i..i + 3, tokens.size)) {
            val from = clockToken(tokens.getOrNull(i + 1)?.lower)
            val to = clockToken(tokens.getOrNull(i + 3)?.lower)
            if (from != null && to != null && from != to &&
                tokens.getOrNull(i + 2)?.lower in atMarkers
            ) {
                return TimeCandidate(from, ClockText.span(from, to), i..i + 3)
            }
        }

        fixedTimes[t]?.let { return TimeCandidate(it, null, i..i, confidence = Confidence.STRONG) }

        if (t == "ore" && free(used, i..i + 1, tokens.size)) {
            clockToken(tokens.getOrNull(i + 1)?.lower)?.let { time ->
                return refine(tokens, used, time, i..i + 1)
            }
        }

        if (t in atMarkers && free(used, i..i + 1, tokens.size)) {
            val raw = tokens.getOrNull(i + 1)?.lower
            val base = clockToken(raw)
            if (base != null && raw != null) {
                if (':' !in raw && '.' !in raw) {
                    val mm = ClockText.pairMinutes(tokens.getOrNull(i + 2)?.lower)
                    if (mm != null && free(used, i + 2..i + 2, tokens.size)) {
                        return refine(tokens, used, base.withMinute(mm), i..i + 2)
                    }
                    postfix(tokens, used, base.hour, i + 2)?.let { (time, end) ->
                        return refine(tokens, used, time, i..end, inCircle = base.hour in CIRCLE_HOUR, confidence = Confidence.STRONG)
                    }
                }
                return refine(tokens, used, base, i..i + 1)
            }
            circleHour(raw)?.let { h ->
                postfix(tokens, used, h, i + 2)?.let { (time, end) ->
                    return refine(tokens, used, time, i..end, inCircle = true, confidence = Confidence.STRONG)
                }
                return refine(tokens, used, LocalTime.of(h, 0), i..i + 1, inCircle = true, Confidence.STRONG)
            }
        }

        if (t == "le" && free(used, i..i + 1, tokens.size)) {
            val raw = tokens.getOrNull(i + 1)?.lower
            val h = circleHour(raw) ?: raw?.toIntOrNull()?.takeIf { it in CIRCLE_HOUR && raw.length <= 2 }
            if (h != null) {
                postfix(tokens, used, h, i + 2)?.let { (time, end) ->
                    return refine(tokens, used, time, i..end, inCircle = true, confidence = Confidence.STRONG)
                }
                daypartAt(tokens, used, i + 2)?.let { (adjust, end) ->
                    return TimeCandidate(LocalTime.of(adjust(h), 0), null, i..end, confidence = Confidence.STRONG)
                }
            }
        }

        if (':' in t) {
            ClockText.clock(t)?.let { return refine(tokens, used, it, i..i) }
        }

        run {
            val h = (t.toIntOrNull() ?: circleHour(t))?.takeIf { it in CIRCLE_HOUR } ?: return@run
            daypartAt(tokens, used, i + 1)?.let { (adjust, end) ->
                if (free(used, i..end, tokens.size)) {
                    return TimeCandidate(LocalTime.of(adjust(h), 0), null, i..end, confidence = Confidence.STRONG)
                }
            }
        }

        return null
    }

    override fun offsetTime(tokens: List<Token>, used: BooleanArray, now: ZonedDateTime): TimeCandidate? {
        for (i in tokens.indices) {
            if (used[i]) continue
            val t = tokens[i].lower
            if (t != "tra" && t != "fra") continue
            val next = tokens.getOrNull(i + 1)?.lower ?: continue
            val base = now.toLocalTime().withSecond(0).withNano(0)
            if ((next == "un" || next == "mezz") && tokens.getOrNull(i + 2)?.lower == "ora" &&
                free(used, i..i + 2, tokens.size)
            ) {
                val time = if (next == "un") base.plusHours(1) else base.plusMinutes(30)
                return TimeCandidate(time, null, i..i + 2)
            }
            if (next == "un" && tokens.getOrNull(i + 2)?.lower == "quarto" &&
                tokens.getOrNull(i + 3)?.lower == "d" && tokens.getOrNull(i + 4)?.lower == "ora" &&
                free(used, i..i + 4, tokens.size)
            ) {
                return TimeCandidate(base.plusMinutes(15), null, i..i + 4)
            }
            if (next == "tre" && tokens.getOrNull(i + 2)?.lower == "quarti" &&
                tokens.getOrNull(i + 3)?.lower == "d" && tokens.getOrNull(i + 4)?.lower == "ora" &&
                free(used, i..i + 4, tokens.size)
            ) {
                return TimeCandidate(base.plusMinutes(45), null, i..i + 4)
            }
            val n = next.toLongOrNull()?.takeIf { it in AMOUNT } ?: continue
            val time = when (tokens.getOrNull(i + 2)?.lower) {
                in ItWords.hourWords -> base.plusHours(n)
                in ItWords.minuteWords -> base.plusMinutes(n)
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
    ): TimeCandidate = refineCircle(time, range, inCircle, confidence) { k -> daypartAt(tokens, used, k) }

    private fun daypartAt(tokens: List<Token>, used: BooleanArray, k: Int): Pair<(Int) -> Int, Int>? {
        if (tokens.getOrNull(k)?.lower !in daypartLinks || !free(used, k..k, tokens.size)) return null
        val half = dayparts[tokens.getOrNull(k + 1)?.lower] ?: return null
        if (!free(used, k + 1..k + 1, tokens.size)) return null
        return half::resolve to k + 1
    }

    private fun postfix(tokens: List<Token>, used: BooleanArray, hour: Int, k: Int): Pair<LocalTime, Int>? {
        val first = tokens.getOrNull(k)?.lower ?: return null
        if (first == "e" && free(used, k..k + 1, tokens.size)) {
            val next = tokens.getOrNull(k + 1)?.lower ?: return null
            if (next == "mezza" || next == "mezzo") return LocalTime.of(hour, 30) to k + 1
            if (next == "un" && tokens.getOrNull(k + 2)?.lower == "quarto" &&
                free(used, k + 2..k + 2, tokens.size)
            ) {
                return LocalTime.of(hour, 15) to k + 2
            }
            if (next.length <= 2) {
                next.toIntOrNull()?.takeIf { it in 0..59 }?.let { return LocalTime.of(hour, it) to k + 1 }
            }
        }
        if (first == "meno" && free(used, k..k + 2, tokens.size) &&
            tokens.getOrNull(k + 1)?.lower == "un" && tokens.getOrNull(k + 2)?.lower == "quarto"
        ) {
            return LocalTime.of(if (hour == 1) 12 else hour - 1, 45) to k + 2
        }
        return null
    }

    private fun circleHour(word: String?): Int? =
        ItWords.cardinals[word]?.takeIf { it in CIRCLE_HOUR && (it != 1 || word in oneOclock) }

    private fun clockToken(s: String?): LocalTime? = ClockText.clock(s) ?: ClockText.dottedClock(s)
}
