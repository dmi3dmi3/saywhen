package com.dmi3dmi3.saywhen.parser.fr

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

internal object FrTimeRules : TimeRule {

    private val oneOclock = setOf("une")

    private val fixedTimes = mapOf("midi" to LocalTime.NOON, "minuit" to LocalTime.MIDNIGHT)

    private val dayparts = mapOf(
        "matin" to DayHalf.MORNING, "mat" to DayHalf.MORNING,
        "matinée" to DayHalf.MORNING, "matinee" to DayHalf.MORNING,
        "après-midi" to DayHalf.AFTERNOON, "apres-midi" to DayHalf.AFTERNOON, "aprem" to DayHalf.AFTERNOON,
        "soir" to DayHalf.EVENING, "soirée" to DayHalf.EVENING, "soiree" to DayHalf.EVENING,
        "nuit" to DayHalf.MORNING,
    )

    private val notTimeAfter = setOf("pendant", "durant", "dans")

    private val bareHourUnits = setOf("heure", "heures")

    private enum class Slot { PREPOSITION, ENDPOINT, BARE }

    private val gluedHour = Regex("""(\d{1,2})h(\d{2})?""")
    private val gluedMinutes = Regex("""(\d{1,3})(?:min|mn)""")

    private class Hour(val time: LocalTime, val end: Int, val circle: Boolean, val confidence: Confidence)

    override fun find(tokens: List<Token>, used: BooleanArray): TimeCandidate? {
        for (allowPour in listOf(false, true)) {
            for (i in tokens.indices) {
                if (used[i]) continue
                matchAt(tokens, i, used, allowPour)?.let { return it }
            }
        }
        return null
    }

    private fun matchAt(tokens: List<Token>, i: Int, used: BooleanArray, allowPour: Boolean): TimeCandidate? {
        val t = tokens[i].lower

        interval(tokens, i, used)?.let { return it }

        if (t == "à" && free(used, i..i + 1, tokens.size)) {
            hourAt(tokens, i + 1, used, Slot.PREPOSITION)?.let { return refine(tokens, used, it, i) }
        }

        val prev = tokens.getOrNull(i - 1)?.lower
        if (prev in notTimeAfter || (!allowPour && prev == "pour")) return null
        hourAt(tokens, i, used, Slot.BARE)?.let { return refine(tokens, used, it, i) }

        return null
    }

    private fun interval(tokens: List<Token>, i: Int, used: BooleanArray): TimeCandidate? {
        val t = tokens[i].lower
        if ('-' in t) {
            ClockText.gluedInterval(t)?.let { (from, d) -> return TimeCandidate(from, d, i..i) }
            val parts = t.split("-").map { it.trim() }
            if (parts.size == 2 && parts.any { 'h' in it }) {
                val from = gluedClock(parts[0]) ?: bareClock(parts[0])
                val to = gluedClock(parts[1]) ?: bareClock(parts[1])
                if (from != null && to != null && from != to) return TimeCandidate(from, ClockText.span(from, to), i..i)
            }
            return null
        }
        if (t != "de" && t != "entre") return null
        val from = hourAt(tokens, i + 1, used, Slot.ENDPOINT) ?: bareStart(tokens, i + 1, used) ?: return null
        var k = from.end + 1
        when (t) {
            "de" -> when (tokens.getOrNull(k)?.lower) {
                "à", "a" -> k++
                "jusqu" -> if (tokens.getOrNull(k + 1)?.lower == "à") k += 2 else return null
                else -> return null
            }
            else -> if (tokens.getOrNull(k)?.lower == "et") k++ else return null
        }
        if (!free(used, i..k - 1, tokens.size)) return null
        val to = hourAt(tokens, k, used, Slot.ENDPOINT) ?: return null
        if (to.time == from.time) return null
        return TimeCandidate(from.time, ClockText.span(from.time, to.time), i..to.end)
    }

    private fun hourAt(tokens: List<Token>, k: Int, used: BooleanArray, slot: Slot): Hour? {
        val w = tokens.getOrNull(k)?.lower ?: return null
        if (!free(used, k..k, tokens.size)) return null

        fixedTimes[w]?.let { fixed ->
            val (time, end) = postfix(tokens, used, fixed, k) ?: (fixed to k)
            return Hour(time, end, circle = false, Confidence.STRONG)
        }

        gluedClock(w)?.let { glued ->
            if (w.endsWith("h")) {
                ClockText.pairMinutes(tokens.getOrNull(k + 1)?.lower)
                    ?.takeIf { free(used, k + 1..k + 1, tokens.size) }
                    ?.let { return Hour(glued.withMinute(it), k + 1, glued.hour in CIRCLE_HOUR, Confidence.EXPLICIT) }
            }
            val (time, end) = postfix(tokens, used, glued, k) ?: (glued to k)
            return Hour(time, end, time.hour in CIRCLE_HOUR, Confidence.EXPLICIT)
        }

        if (':' in w) return ClockText.clock(w)?.let { Hour(it, k, it.hour in CIRCLE_HOUR, Confidence.EXPLICIT) }
        if (slot == Slot.PREPOSITION) {
            ClockText.dottedClock(w)?.let { return Hour(it, k, it.hour in CIRCLE_HOUR, Confidence.EXPLICIT) }
        }

        val digit = w.toIntOrNull()?.takeIf { it in 0..23 && w.length <= 2 }
        val h = digit ?: wordHour(w) ?: return null
        val confidence = if (digit != null) Confidence.EXPLICIT else Confidence.STRONG
        var end = k
        val units = if (slot == Slot.BARE) bareHourUnits else FrWords.hourWords
        if (tokens.getOrNull(k + 1)?.lower in units && free(used, k + 1..k + 1, tokens.size)) {
            end = k + 1
        } else if (digit == null || slot != Slot.PREPOSITION) {
            return null
        }
        var time = LocalTime.of(h, 0)
        ClockText.pairMinutes(tokens.getOrNull(end + 1)?.lower)
            ?.takeIf { free(used, end + 1..end + 1, tokens.size) }
            ?.let { return Hour(time.withMinute(it), end + 1, h in CIRCLE_HOUR, confidence) }
        val tail = postfix(tokens, used, time, end)
        if (tail != null) {
            time = tail.first; end = tail.second
        } else if (digit == null && slot == Slot.BARE && daypartAt(tokens, used, end + 1) == null) {
            return null
        }
        return Hour(time, end, time.hour in CIRCLE_HOUR, confidence)
    }

    private fun postfix(tokens: List<Token>, used: BooleanArray, time: LocalTime, k: Int): Pair<LocalTime, Int>? {
        if (time.minute != 0) return null
        val first = tokens.getOrNull(k + 1)?.lower ?: return null
        if (first == "et" && free(used, k + 1..k + 2, tokens.size)) {
            when (tokens.getOrNull(k + 2)?.lower) {
                "demie", "demi" -> return time.plusMinutes(30) to k + 2
                "quart" -> return time.plusMinutes(15) to k + 2
            }
        }
        if (first == "moins" && tokens.getOrNull(k + 2)?.lower == "le" && tokens.getOrNull(k + 3)?.lower == "quart" &&
            free(used, k + 1..k + 3, tokens.size)
        ) {
            return time.minusMinutes(15) to k + 3
        }
        return null
    }

    override fun offsetTime(tokens: List<Token>, used: BooleanArray, now: ZonedDateTime): TimeCandidate? {
        val base = now.toLocalTime().withSecond(0).withNano(0)
        for (i in tokens.indices) {
            if (used[i] || tokens[i].lower != "dans") continue
            val j = i + 1
            val next = tokens.getOrNull(j)?.lower ?: continue
            if (used[j]) continue
            if (next == "une" && tokens.getOrNull(j + 1)?.lower == "demi-heure" && free(used, i..j + 1, tokens.size)) {
                return TimeCandidate(base.plusMinutes(30), null, i..j + 1)
            }
            if ((next == "un" || next == "trois") &&
                tokens.getOrNull(j + 1)?.lower in setOf("quart", "quarts") &&
                tokens.getOrNull(j + 2)?.lower == "d" &&
                tokens.getOrNull(j + 3)?.lower in FrWords.hourWords && free(used, i..j + 3, tokens.size)
            ) {
                return TimeCandidate(base.plusMinutes(if (next == "un") 15 else 45), null, i..j + 3)
            }
            gluedHour.matchEntire(next)?.let { m ->
                val h = m.groupValues[1].toLong()
                val mm = m.groupValues[2].takeIf { it.isNotEmpty() }?.toLong() ?: 0L
                if ((h > 0 || mm > 0) && mm in 0..59) return TimeCandidate(base.plusHours(h).plusMinutes(mm), null, i..j)
            }
            gluedMinutes.matchEntire(next)?.let { m ->
                m.groupValues[1].toLong().takeIf { it in AMOUNT }?.let { return TimeCandidate(base.plusMinutes(it), null, i..j) }
            }
            val n = next.toLongOrNull() ?: FrWords.cardinals[next]?.toLong() ?: continue
            if (n !in AMOUNT) continue
            val time = when (tokens.getOrNull(j + 1)?.lower) {
                in FrWords.hourWords -> base.plusHours(n)
                in FrWords.minuteWords -> base.plusMinutes(n)
                else -> null
            } ?: continue
            if (free(used, i..j + 1, tokens.size)) return TimeCandidate(time, null, i..j + 1)
        }
        return null
    }

    override fun bareHourAfterClaim(tokens: List<Token>, used: BooleanArray): TimeCandidate? =
        bareHourAfterClaim(tokens, used) { time, range ->
            refineCircle(time, range, confidence = Confidence.WEAK) { k -> daypartAt(tokens, used, k) }
        }

    private fun refine(tokens: List<Token>, used: BooleanArray, hour: Hour, from: Int): TimeCandidate =
        refineCircle(hour.time, from..hour.end, hour.circle, hour.confidence) { k -> daypartAt(tokens, used, k) }

    private fun daypartAt(tokens: List<Token>, used: BooleanArray, k: Int): Pair<(Int) -> Int, Int>? {
        val j = when (tokens.getOrNull(k)?.lower) {
            "du", "le" -> k + 1
            "de" -> if (tokens.getOrNull(k + 1)?.lower in setOf("la", "l")) k + 2 else return null
            else -> return null
        }
        val half = dayparts[tokens.getOrNull(j)?.lower] ?: return null
        if (!free(used, k..j, tokens.size)) return null
        return half::resolve to j
    }

    private fun wordHour(word: String): Int? =
        FrWords.cardinals[word]?.takeIf { it in 0..23 && (it != 1 || word in oneOclock) }

    private fun bareStart(tokens: List<Token>, k: Int, used: BooleanArray): Hour? {
        val w = tokens.getOrNull(k)?.lower ?: return null
        if (!free(used, k..k, tokens.size)) return null
        return bareClock(w)?.let { Hour(it, k, circle = false, Confidence.EXPLICIT) }
    }

    private fun bareClock(s: String): LocalTime? =
        s.toIntOrNull()?.takeIf { it in 0..23 && s.length <= 2 }?.let { LocalTime.of(it, 0) }

    private fun gluedClock(s: String): LocalTime? {
        val m = gluedHour.matchEntire(s) ?: return null
        val h = m.groupValues[1].toInt().takeIf { it in 0..23 } ?: return null
        val mm = m.groupValues[2].takeIf { it.isNotEmpty() }?.toInt()?.takeIf { it in 0..59 } ?: 0
        return LocalTime.of(h, mm)
    }
}
