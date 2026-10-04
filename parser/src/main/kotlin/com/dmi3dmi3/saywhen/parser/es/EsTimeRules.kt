package com.dmi3dmi3.saywhen.parser.es

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

internal object EsTimeRules : TimeRule {

    private val oneOclock = setOf("una")

    private val fixedTimes = mapOf(
        "mediodía" to LocalTime.NOON, "mediodia" to LocalTime.NOON,
        "medianoche" to LocalTime.MIDNIGHT,
    )

    private val dayparts = mapOf(
        "mañana" to DayHalf.MORNING, "manana" to DayHalf.MORNING,
        "tarde" to DayHalf.AFTERNOON, "noche" to DayHalf.EVENING,
    )
    private val daypartLinks = setOf("de", "en", "por")

    private val halves = mapOf("am" to DayHalf.MORNING, "pm" to DayHalf.AFTERNOON)

    override fun find(tokens: List<Token>, used: BooleanArray): TimeCandidate? {
        for (i in tokens.indices) {
            if (used[i]) continue
            matchAt(tokens, i, used)?.let { return it }
        }
        return null
    }

    private fun matchAt(tokens: List<Token>, i: Int, used: BooleanArray): TimeCandidate? {
        val t = tokens[i].lower

        if (t == "de" && tokens.getOrNull(i + 1)?.lower == "las" &&
            free(used, i..i + 5, tokens.size)
        ) {
            val from = clockToken(tokens.getOrNull(i + 2)?.lower)
            val to = clockToken(tokens.getOrNull(i + 5)?.lower)
            if (from != null && to != null && from != to &&
                tokens.getOrNull(i + 3)?.lower == "a" &&
                tokens.getOrNull(i + 4)?.lower in setOf("las", "la")
            ) {
                return TimeCandidate(from, ClockText.span(from, to), i..i + 5)
            }
        }

        fixedTimes[t]?.let { return TimeCandidate(it, null, i..i, confidence = Confidence.STRONG) }

        if (t == "medio" && tokens.getOrNull(i + 1)?.lower in setOf("día", "dia") &&
            free(used, i..i + 1, tokens.size)
        ) {
            return TimeCandidate(LocalTime.NOON, null, i..i + 1, confidence = Confidence.STRONG)
        }

        if (t == "a" && tokens.getOrNull(i + 1)?.lower in setOf("las", "la") &&
            free(used, i..i + 2, tokens.size)
        ) {
            val raw = tokens.getOrNull(i + 2)?.lower
            val base = clockToken(raw)
            if (base != null && raw != null) {
                if (':' !in raw && '.' !in raw) {
                    if (tokens.getOrNull(i + 3)?.lower == "horas" && free(used, i + 3..i + 3, tokens.size)) {
                        return refine(tokens, used, base, i..i + 3)
                    }
                    val mm = ClockText.pairMinutes(tokens.getOrNull(i + 3)?.lower)
                    if (mm != null && free(used, i + 3..i + 3, tokens.size)) {
                        return refine(tokens, used, base.withMinute(mm), i..i + 3)
                    }
                    postfix(tokens, used, base.hour, i + 3)?.let { (time, end) ->
                        return refine(tokens, used, time, i..end, inCircle = base.hour in CIRCLE_HOUR, confidence = Confidence.STRONG)
                    }
                }
                return refine(tokens, used, base, i..i + 2)
            }
            circleHour(raw)?.let { h ->
                postfix(tokens, used, h, i + 3)?.let { (time, end) ->
                    return refine(tokens, used, time, i..end, inCircle = true, confidence = Confidence.STRONG)
                }
                return refine(tokens, used, LocalTime.of(h, 0), i..i + 2, inCircle = true, Confidence.STRONG)
            }
        }

        if (t == "las" && free(used, i..i + 1, tokens.size)) {
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

        run {
            val h = (t.toIntOrNull() ?: circleHour(t))?.takeIf { it in CIRCLE_HOUR } ?: return@run
            daypartAt(tokens, used, i + 1)?.let { (adjust, end) ->
                if (free(used, i..end, tokens.size)) {
                    return TimeCandidate(LocalTime.of(adjust(h), 0), null, i..end, confidence = Confidence.STRONG)
                }
            }
        }

        if (':' in t) {
            ClockText.clock(t)?.let { return refine(tokens, used, it, i..i) }
        }

        return null
    }

    override fun offsetTime(tokens: List<Token>, used: BooleanArray, now: ZonedDateTime): TimeCandidate? {
        for (i in tokens.indices) {
            if (used[i]) continue
            val t = tokens[i].lower
            if (t != "en" && t != "dentro") continue
            var j = i + 1
            if (t == "dentro") {
                if (tokens.getOrNull(j)?.lower != "de") continue
                j++
            }
            val next = tokens.getOrNull(j)?.lower ?: continue
            val base = now.toLocalTime().withSecond(0).withNano(0)
            if (next == "media" && tokens.getOrNull(j + 1)?.lower == "hora" &&
                free(used, i..j + 1, tokens.size)
            ) {
                return TimeCandidate(base.plusMinutes(30), null, i..j + 1)
            }
            if (next == "un" && tokens.getOrNull(j + 1)?.lower == "cuarto" &&
                tokens.getOrNull(j + 2)?.lower == "de" && tokens.getOrNull(j + 3)?.lower == "hora" &&
                free(used, i..j + 3, tokens.size)
            ) {
                return TimeCandidate(base.plusMinutes(15), null, i..j + 3)
            }
            val n = next.toLongOrNull() ?: EsWords.cardinals[next]?.toLong() ?: continue
            if (n !in AMOUNT) continue
            val time = when (tokens.getOrNull(j + 1)?.lower) {
                in EsWords.hourWords -> base.plusHours(n)
                in EsWords.minuteWords -> base.plusMinutes(n)
                else -> null
            } ?: continue
            if (free(used, i..j + 1, tokens.size)) return TimeCandidate(time, null, i..j + 1)
        }
        return null
    }

    private fun circleHour(word: String?): Int? =
        EsWords.cardinals[word]?.takeIf { it in CIRCLE_HOUR && (it != 1 || word in oneOclock) }

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
    ): TimeCandidate = refineCircle(time, range, inCircle, confidence) { k ->
        amPmAt(tokens, used, k) ?: daypartAt(tokens, used, k)
    }

    private fun amPmAt(tokens: List<Token>, used: BooleanArray, k: Int): Pair<(Int) -> Int, Int>? =
        halves[tokens.getOrNull(k)?.lower]?.takeIf { free(used, k..k, tokens.size) }?.let { it::resolve to k }

    private fun daypartAt(tokens: List<Token>, used: BooleanArray, k: Int): Pair<(Int) -> Int, Int>? {
        if (tokens.getOrNull(k)?.lower !in daypartLinks || !free(used, k..k, tokens.size)) return null
        if (tokens.getOrNull(k + 1)?.lower != "la" || !free(used, k + 1..k + 1, tokens.size)) return null
        val half = dayparts[tokens.getOrNull(k + 2)?.lower] ?: return null
        if (!free(used, k + 2..k + 2, tokens.size)) return null
        return half::resolve to k + 2
    }

    private fun postfix(tokens: List<Token>, used: BooleanArray, hour: Int, k: Int): Pair<LocalTime, Int>? {
        val first = tokens.getOrNull(k)?.lower ?: return null
        if (first == "y" && free(used, k..k + 1, tokens.size)) {
            val next = tokens.getOrNull(k + 1)?.lower ?: return null
            if (next == "media") return LocalTime.of(hour, 30) to k + 1
            if (next == "cuarto") return LocalTime.of(hour, 15) to k + 1
            if (next.length <= 2) {
                next.toIntOrNull()?.takeIf { it in 0..59 }?.let { return LocalTime.of(hour, it) to k + 1 }
            }
        }
        if (first == "menos" && free(used, k..k + 1, tokens.size)) {
            val prev = if (hour == 1) 12 else hour - 1
            val next = tokens.getOrNull(k + 1)?.lower ?: return null
            if (next == "cuarto") return LocalTime.of(prev, 45) to k + 1
            EsWords.cardinals[next]?.takeIf { it in 1..30 }?.let {
                return LocalTime.of(prev, 60 - it) to k + 1
            }
            if (next.length <= 2) {
                next.toIntOrNull()?.takeIf { it in 1..30 }?.let {
                    return LocalTime.of(prev, 60 - it) to k + 1
                }
            }
        }
        return null
    }

    private fun clockToken(s: String?): LocalTime? = ClockText.clock(s) ?: ClockText.dottedClock(s)
}
