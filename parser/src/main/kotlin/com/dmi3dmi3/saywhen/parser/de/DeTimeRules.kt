package com.dmi3dmi3.saywhen.parser.de

import com.dmi3dmi3.saywhen.parser.ClockText
import com.dmi3dmi3.saywhen.parser.Confidence
import com.dmi3dmi3.saywhen.parser.TimeCandidate
import com.dmi3dmi3.saywhen.parser.Token
import com.dmi3dmi3.saywhen.parser.bareHourAfterClaim
import com.dmi3dmi3.saywhen.parser.decimalNumber
import com.dmi3dmi3.saywhen.parser.free
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.math.roundToLong

internal object DeTimeRules {

    private val wordHours = mapOf(
        "ein" to 1, "eins" to 1, "zwei" to 2, "drei" to 3, "vier" to 4,
        "fünf" to 5, "fuenf" to 5, "sechs" to 6, "sieben" to 7, "acht" to 8,
        "neun" to 9, "zehn" to 10, "elf" to 11, "zwölf" to 12, "zwoelf" to 12,
    )

    private val fixedTimes = mapOf(
        "mittag" to LocalTime.NOON, "mittags" to LocalTime.NOON,
        "mitternacht" to LocalTime.MIDNIGHT,
    )

    // уточнение половины суток: «am abend», наречия «abends», голое «früh»
    private val dayparts = mapOf<String, (Int) -> Int>(
        "früh" to { h -> if (h == 12) 0 else h },
        "frueh" to { h -> if (h == 12) 0 else h },
        "morgen" to { h -> if (h == 12) 0 else h },
        "morgens" to { h -> if (h == 12) 0 else h },
        "vormittag" to { h -> if (h == 12) 0 else h },
        "vormittags" to { h -> if (h == 12) 0 else h },
        "nachmittag" to { h -> if (h < 12) h + 12 else 12 },
        "nachmittags" to { h -> if (h < 12) h + 12 else 12 },
        "abend" to { h -> if (h < 12) h + 12 else 0 },
        "abends" to { h -> if (h < 12) h + 12 else 0 },
        "nacht" to { h -> if (h == 12) 0 else h },
        "nachts" to { h -> if (h == 12) 0 else h },
    )

    // наречия и голое «früh» работают без связки; существительные — после am / in der
    private val bareDayparts = setOf(
        "früh", "frueh", "morgens", "vormittags", "nachmittags", "abends", "nachts",
    )

    private val offsetHours = setOf("stunde", "stunden")
    private val offsetMinutes = setOf("minute", "minuten")

    // «18uhr» — склейка часа с Uhr
    private val gluedUhr = Regex("""(\d{1,2})uhr""")

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

        // «von 9:30 bis 11:00» / «von 9 bis 11» / «zwischen 9:30 und 11:00»
        if ((t == "von" || t == "zwischen") && free(used, i..i + 3, tokens.size)) {
            val mid = if (t == "von") "bis" else "und"
            val from = clockToken(tokens.getOrNull(i + 1)?.lower)
            val to = clockToken(tokens.getOrNull(i + 3)?.lower)
            if (from != null && to != null && from != to && tokens.getOrNull(i + 2)?.lower == mid) {
                var d = Duration.between(from, to)
                if (d < Duration.ZERO) d = d.plusHours(24)
                return TimeCandidate(from, d, i..i + 3)
            }
        }

        fixedTimes[t]?.let { return TimeCandidate(it, null, i..i, confidence = Confidence.STRONG) }

        // «halb 4» = 3:30 — час минус один (русская логика, не half past!)
        if (t == "halb" && free(used, i..i + 1, tokens.size)) {
            val raw = tokens.getOrNull(i + 1)?.lower
            val h = wordHours[raw] ?: raw?.toIntOrNull()?.takeIf { it in 1..12 }
            if (h != null) {
                var end = i + 1
                if (tokens.getOrNull(end + 1)?.lower == "uhr" && free(used, end + 1..end + 1, tokens.size)) end++
                return refine(tokens, used, LocalTime.of(h - 1, 30), i..end, inCircle = true, Confidence.STRONG)
            }
        }

        // «viertel nach 3» / «viertel vor 12» / «20 nach 3» / «15 [minuten] vor 12»
        nachVor(tokens, i, used)?.let { return it }

        // «um 15 [Uhr]» / «um 9:30» / «um 19.30» / «um halb 4» / «um drei» / «um 3 15»
        if (t == "um" && free(used, i..i + 1, tokens.size)) {
            val raw = tokens.getOrNull(i + 1)?.lower
            val base = clockToken(raw)
            if (base != null && raw != null) {
                var end = i + 1
                if (tokens.getOrNull(end + 1)?.lower == "uhr" && free(used, end + 1..end + 1, tokens.size)) {
                    end++
                    // «um 3 uhr 15» — пара после Uhr
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
            wordHours[raw]?.let { h ->
                var end = i + 1
                if (tokens.getOrNull(end + 1)?.lower == "uhr" && free(used, end + 1..end + 1, tokens.size)) end++
                return refine(tokens, used, LocalTime.of(h, 0), i..end, inCircle = true, Confidence.STRONG)
            }
        }

        // «3 uhr [15]» / «15 uhr [30]» без um; «8 uhr am abend»
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

        // склейка «18uhr»
        gluedUhr.matchEntire(t)?.let { m ->
            m.groupValues[1].toIntOrNull()?.takeIf { it in 0..23 }?.let { h ->
                return refine(tokens, used, LocalTime.of(h, 0), i..i)
            }
        }

        // голое «9:30» / «3:18 früh» — двоеточие держит якорь
        if (':' in t) {
            ClockText.clock(t)?.let { return refine(tokens, used, it, i..i) }
        }

        return null
    }

    /** «viertel nach 3», «viertel vor 12», «20 nach 3», «15 [minuten] vor 12». */
    private fun nachVor(tokens: List<Token>, i: Int, used: BooleanArray): TimeCandidate? {
        val t = tokens[i].lower
        val minutes = when {
            t == "viertel" -> 15
            else -> t.toIntOrNull()?.takeIf { it in 1..30 && t.length <= 2 } ?: return null
        }
        var j = i + 1
        if (t != "viertel" && tokens.getOrNull(j)?.lower in setOf("minuten", "min")) j++
        val dir = tokens.getOrNull(j)?.lower ?: return null
        if (dir != "nach" && dir != "vor") return null
        val raw = tokens.getOrNull(j + 1)?.lower
        val h = wordHours[raw] ?: raw?.toIntOrNull()?.takeIf { it in 1..12 } ?: return null
        if (!free(used, i..j + 1, tokens.size)) return null
        val time = if (dir == "nach") LocalTime.of(h, minutes)
                   else LocalTime.of(if (h == 1) 12 else h - 1, 60 - minutes)
        var end = j + 1
        if (tokens.getOrNull(end + 1)?.lower == "uhr" && free(used, end + 1..end + 1, tokens.size)) end++
        return refine(tokens, used, time, i..end, inCircle = true, Confidence.STRONG)
    }

    /**
     * «in einer Stunde / in 2 Stunden / in 30 Minuten / in einer halben
     * Stunde / in 2,5 Stunden» — офсет от «сейчас»; перекат — сборкой.
     */
    fun offsetTime(tokens: List<Token>, used: BooleanArray, now: ZonedDateTime): TimeCandidate? {
        for (i in tokens.indices) {
            if (used[i] || tokens[i].lower != "in") continue
            var j = i + 1
            val next = tokens.getOrNull(j)?.lower ?: continue
            val base = now.toLocalTime().withSecond(0).withNano(0)
            // «in einer halben stunde» / «in einer viertelstunde / dreiviertelstunde»
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
            // «in 2,5 stunden» — десятичный офсет
            decimalNumber(next)?.let { v ->
                if (tokens.getOrNull(j + 1)?.lower in offsetHours && free(used, i..j + 1, tokens.size)) {
                    return TimeCandidate(base.plusMinutes((v * 60).roundToLong()), null, i..j + 1)
                }
            }
            val n = next.toLongOrNull()
                ?: mapOf(
                    "einer" to 1L, "einem" to 1L, "zwei" to 2L, "drei" to 3L, "vier" to 4L,
                    "fünf" to 5L, "fuenf" to 5L, "sechs" to 6L, "sieben" to 7L, "acht" to 8L,
                    "neun" to 9L, "zehn" to 10L,
                )[next] ?: continue
            if (n !in 1..999) continue
            val time = when (tokens.getOrNull(j + 1)?.lower) {
                in offsetHours -> base.plusHours(n)
                in offsetMinutes -> base.plusMinutes(n)
                else -> null
            } ?: continue
            if (free(used, i..j + 1, tokens.size)) return TimeCandidate(time, null, i..j + 1)
        }
        return null
    }

    /** Голый час после распознанного куска — общий цикл в ядре, доводка наша. */
    fun bareHourAfterClaim(tokens: List<Token>, used: BooleanArray): TimeCandidate? =
        bareHourAfterClaim(tokens, used) { time, range ->
            refine(tokens, used, time, range, confidence = Confidence.WEAK)
        }

    /**
     * Доводка часа круга 1..12: половина суток следом снимает пару;
     * без уточнения — флаг, выбор делает сборка (окно активности).
     */
    private fun refine(
        tokens: List<Token>,
        used: BooleanArray,
        time: LocalTime,
        range: IntRange,
        inCircle: Boolean = time.hour in 1..12,
        confidence: Confidence = Confidence.EXPLICIT,
    ): TimeCandidate {
        if (!inCircle) return TimeCandidate(time, null, range, confidence = confidence)
        daypartAt(tokens, used, range.last + 1)?.let { (adjust, end) ->
            return TimeCandidate(time.withHour(adjust(time.hour)), null, range.first..end, confidence = confidence)
        }
        return TimeCandidate(time, null, range, twelveHour = true, confidence = confidence)
    }

    /** «am abend» / «in der früh» / наречие «abends» с позиции k → (доводка, конец). */
    private fun daypartAt(tokens: List<Token>, used: BooleanArray, k: Int): Pair<(Int) -> Int, Int>? {
        val first = tokens.getOrNull(k)?.lower ?: return null
        if (first in bareDayparts && free(used, k..k, tokens.size)) {
            return dayparts.getValue(first) to k
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
        val adjust = dayparts[tokens.getOrNull(j)?.lower] ?: return null
        if (!free(used, k..j, tokens.size)) return null
        return adjust to j
    }

    private fun clockToken(s: String?): LocalTime? = ClockText.clock(s) ?: ClockText.dottedClock(s)
}
