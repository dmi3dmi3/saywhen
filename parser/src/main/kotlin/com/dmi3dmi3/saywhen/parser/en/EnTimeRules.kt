package com.dmi3dmi3.saywhen.parser.en

import com.dmi3dmi3.saywhen.parser.ClockText
import com.dmi3dmi3.saywhen.parser.Confidence
import com.dmi3dmi3.saywhen.parser.TimeCandidate
import com.dmi3dmi3.saywhen.parser.Token
import java.time.Duration
import java.time.LocalTime

internal object EnTimeRules {

    private val wordHours = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6,
        "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12,
    )

    private val fixedTimes = mapOf("noon" to LocalTime.NOON, "midnight" to LocalTime.MIDNIGHT)

    // "5pm" / "5:30pm" — склеенный маркер половины суток
    private val gluedAmPm = Regex("""(\d{1,2})(?::(\d{2}))?(am|pm)""")

    /** Первый матч по свободным токенам; used не трогает. */
    fun find(tokens: List<Token>, used: BooleanArray): TimeCandidate? {
        for (i in tokens.indices) {
            if (used[i]) continue
            matchAt(tokens, i, used)?.let { return it }
        }
        return null
    }

    private fun matchAt(tokens: List<Token>, i: Int, used: BooleanArray): TimeCandidate? {
        val t = tokens[i].lower

        // "from 3 to 5[pm]"
        if (t == "from" && free(used, i..i + 3, tokens.size)) {
            val fromRaw = tokens.getOrNull(i + 1)?.lower
            val toRaw = tokens.getOrNull(i + 3)?.lower
            var from = timeToken(fromRaw)
            val to = timeToken(toRaw)
            // «from 3 to 5pm»: маркер конца распространяется на голое начало —
            // из пары {3, 15} берём половину суток с коротким интервалом
            if (from != null && to != null && from.hour in 1..12 &&
                amPm(fromRaw) == null && amPm(toRaw) != null
            ) {
                val alt = from.plusHours(12)
                if (spanBetween(alt, to) < spanBetween(from, to)) from = alt
            }
            if (from != null && to != null && from != to && tokens[i + 2].lower == "to") {
                return TimeCandidate(from, spanBetween(from, to), i..i + 3)
            }
        }

        // "noon" / "midnight" — предлог подберёт поглощение сирот
        fixedTimes[t]?.let { return TimeCandidate(it, null, i..i, confidence = Confidence.STRONG) }

        // "half past six" / "half past 6"
        if (t == "half" && tokens.getOrNull(i + 1)?.lower == "past" && free(used, i..i + 2, tokens.size)) {
            val word = tokens.getOrNull(i + 2)?.lower
            val h = wordHours[word] ?: word?.toIntOrNull()?.takeIf { it in 1..12 }
            if (h != null) {
                return refine(tokens, used, LocalTime.of(h, 30), i..i + 2, inCircle = true, Confidence.STRONG)
            }
        }

        // "at 5" / "at 5:30" / "at 5pm" / "at five"
        if (t == "at" && free(used, i..i + 1, tokens.size)) {
            val raw = tokens.getOrNull(i + 1)?.lower
            amPm(raw)?.let { return TimeCandidate(it, null, i..i + 1) }
            ClockText.clock(raw)?.let { time ->
                if (raw != null && ':' !in raw) {
                    // "at 11 30" — минуты отдельным токеном
                    val mm = ClockText.pairMinutes(tokens.getOrNull(i + 2)?.lower)
                    if (mm != null && free(used, i + 2..i + 2, tokens.size)) {
                        return refine(tokens, used, time.withMinute(mm), i..i + 2)
                    }
                }
                return refine(tokens, used, time, i..i + 1)
            }
            wordHours[raw]?.let { h ->
                return refine(tokens, used, LocalTime.of(h, 0), i..i + 1, confidence = Confidence.STRONG)
            }
        }

        // склеенные без "at": "5pm", "10-11:30", голое "9:30"
        amPm(t)?.let { return TimeCandidate(it, null, i..i) }
        ClockText.gluedInterval(t)?.let { (from, d) -> return TimeCandidate(from, d, i..i) }
        if (':' in t) {
            ClockText.clock(t)?.let { return refine(tokens, used, it, i..i) }
        }

        // голая пара "11 00" — двухцифровые минуты держат якорь не хуже двоеточия
        val mm = ClockText.pairMinutes(tokens.getOrNull(i + 1)?.lower)
        if (mm != null && free(used, i..i + 1, tokens.size)) {
            ClockText.clock(t)?.let { return refine(tokens, used, it.withMinute(mm), i..i + 1) }
        }

        return null
    }

    /** Голый час 0–23 сразу после распознанного куска — "tomorrow 11 standup". */
    fun bareHourAfterClaim(tokens: List<Token>, used: BooleanArray): TimeCandidate? {
        for (i in 1 until tokens.size) {
            if (used[i] || !used[i - 1]) continue
            val hour = tokens[i].lower.takeIf { it.length <= 2 }?.toIntOrNull() ?: continue
            if (hour in 0..23) return refine(tokens, used, LocalTime.of(hour, 0), i..i, confidence = Confidence.WEAK)
        }
        return null
    }

    /** Доводка часа круга: маркер am/pm следом снимает пару и приклеивается к матчу. */
    private fun refine(
        tokens: List<Token>,
        used: BooleanArray,
        time: LocalTime,
        range: IntRange,
        inCircle: Boolean = time.hour in 1..12,
        confidence: Confidence = Confidence.EXPLICIT,
    ): TimeCandidate {
        if (!inCircle) return TimeCandidate(time, null, range, confidence = confidence)
        val next = range.last + 1
        val marker = tokens.getOrNull(next)?.lower
        if ((marker == "am" || marker == "pm") && free(used, next..next, tokens.size)) {
            return TimeCandidate(
                time.withHour(halfOfDay(time.hour, marker)), null, range.first..next,
                confidence = confidence,
            )
        }
        return TimeCandidate(time, null, range, twelveHour = true, confidence = confidence)
    }

    // 12am = полночь, 12pm = полдень
    private fun halfOfDay(h: Int, marker: String): Int =
        if (marker == "am") (if (h == 12) 0 else h)
        else (if (h < 12) h + 12 else 12)

    /** "5pm" / "5:30pm" → явное время; иначе null. */
    private fun amPm(s: String?): LocalTime? {
        s ?: return null
        val m = gluedAmPm.matchEntire(s) ?: return null
        val h = m.groupValues[1].toInt().takeIf { it in 1..12 } ?: return null
        val minute = m.groupValues[2].ifEmpty { "0" }.toInt().takeIf { it in 0..59 } ?: return null
        return LocalTime.of(halfOfDay(h, m.groupValues[3]), minute)
    }

    private fun timeToken(s: String?): LocalTime? = amPm(s) ?: ClockText.clock(s)

    /** Интервал от from до to; отрицательный — через полночь. */
    private fun spanBetween(from: LocalTime, to: LocalTime): Duration {
        var d = Duration.between(from, to)
        if (d < Duration.ZERO) d = d.plusHours(24)
        return d
    }

    private fun free(used: BooleanArray, range: IntRange, size: Int): Boolean =
        range.last < size && range.all { !used[it] }
}
