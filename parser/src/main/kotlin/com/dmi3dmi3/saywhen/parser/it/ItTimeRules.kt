package com.dmi3dmi3.saywhen.parser.it

import com.dmi3dmi3.saywhen.parser.ClockText
import com.dmi3dmi3.saywhen.parser.Confidence
import com.dmi3dmi3.saywhen.parser.TimeCandidate
import com.dmi3dmi3.saywhen.parser.Token
import com.dmi3dmi3.saywhen.parser.bareHourAfterClaim
import com.dmi3dmi3.saywhen.parser.free
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

internal object ItTimeRules {

    // «alle 9» / «all» + «una» (апостроф режется токенайзером)
    private val atMarkers = setOf("alle", "all")

    private val wordHours = mapOf(
        "una" to 1, "due" to 2, "tre" to 3, "quattro" to 4, "cinque" to 5, "sei" to 6,
        "sette" to 7, "otto" to 8, "nove" to 9, "dieci" to 10, "undici" to 11, "dodici" to 12,
    )

    private val fixedTimes = mapOf("mezzogiorno" to LocalTime.NOON, "mezzanotte" to LocalTime.MIDNIGHT)

    // уточнение половины суток: [di|del|della] + слово; 12 — как у соседей
    private val dayparts = mapOf<String, (Int) -> Int>(
        "mattina" to { h -> if (h == 12) 0 else h },
        "mattino" to { h -> if (h == 12) 0 else h },
        "pomeriggio" to { h -> if (h < 12) h + 12 else 12 },
        "sera" to { h -> if (h < 12) h + 12 else 0 },
        "stasera" to { h -> if (h < 12) h + 12 else 0 },
        "notte" to { h -> if (h == 12) 0 else h },
    )
    private val daypartLinks = setOf("di", "del", "della")

    private val offsetHours = setOf("ora", "ore")
    private val offsetMinutes = setOf("minuti", "minuto")

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

        // «dalle 15 alle 17» / «dalle 9:30 alle 11:00»
        if (t == "dalle" && free(used, i..i + 3, tokens.size)) {
            val from = clockToken(tokens.getOrNull(i + 1)?.lower)
            val to = clockToken(tokens.getOrNull(i + 3)?.lower)
            if (from != null && to != null && from != to &&
                tokens.getOrNull(i + 2)?.lower in atMarkers
            ) {
                var d = Duration.between(from, to)
                if (d < Duration.ZERO) d = d.plusHours(24)
                return TimeCandidate(from, d, i..i + 3)
            }
        }

        fixedTimes[t]?.let { return TimeCandidate(it, null, i..i, confidence = Confidence.STRONG) }

        // «ore 15» / «alle ore 15:30» — административная форма; «alle» уходит сиротой
        if (t == "ore" && free(used, i..i + 1, tokens.size)) {
            clockToken(tokens.getOrNull(i + 1)?.lower)?.let { time ->
                return refine(tokens, used, time, i..i + 1)
            }
        }

        // «alle 15 / alle 9:30 / alle 19.30 / alle 3 20 / alle 3 e 20 / alle tre / all'una»
        if (t in atMarkers && free(used, i..i + 1, tokens.size)) {
            val raw = tokens.getOrNull(i + 1)?.lower
            val base = clockToken(raw)
            if (base != null && raw != null) {
                if (':' !in raw && '.' !in raw) {
                    // пара «alle 3 20»
                    val mm = ClockText.pairMinutes(tokens.getOrNull(i + 2)?.lower)
                    if (mm != null && free(used, i + 2..i + 2, tokens.size)) {
                        return refine(tokens, used, base.withMinute(mm), i..i + 2)
                    }
                    postfix(tokens, used, base.hour, i + 2)?.let { (time, end) ->
                        return refine(tokens, used, time, i..end, inCircle = base.hour in 1..12, confidence = Confidence.STRONG)
                    }
                }
                return refine(tokens, used, base, i..i + 1)
            }
            wordHours[raw]?.let { h ->
                postfix(tokens, used, h, i + 2)?.let { (time, end) ->
                    return refine(tokens, used, time, i..end, inCircle = true, confidence = Confidence.STRONG)
                }
                return refine(tokens, used, LocalTime.of(h, 0), i..i + 1, inCircle = true, Confidence.STRONG)
            }
        }

        // «le tre …» — только с продолжением (постфикс или половина суток):
        // голое «le 3» рискует числом заголовка («le 3 mele»)
        if (t == "le" && free(used, i..i + 1, tokens.size)) {
            val raw = tokens.getOrNull(i + 1)?.lower
            val h = wordHours[raw] ?: raw?.toIntOrNull()?.takeIf { it in 1..12 && raw.length <= 2 }
            if (h != null) {
                postfix(tokens, used, h, i + 2)?.let { (time, end) ->
                    return refine(tokens, used, time, i..end, inCircle = true, confidence = Confidence.STRONG)
                }
                daypartAt(tokens, used, i + 2)?.let { (adjust, end) ->
                    return TimeCandidate(LocalTime.of(adjust(h), 0), null, i..end, confidence = Confidence.STRONG)
                }
            }
        }

        // голое «9:30» / «3:15 del pomeriggio» — двоеточие держит якорь
        if (':' in t) {
            ClockText.clock(t)?.let { return refine(tokens, used, it, i..i) }
        }

        // «8 di sera / della sera» — голый час, но только с половиной суток
        run {
            val h = (t.toIntOrNull() ?: wordHours[t])?.takeIf { it in 1..12 } ?: return@run
            daypartAt(tokens, used, i + 1)?.let { (adjust, end) ->
                if (free(used, i..end, tokens.size)) {
                    return TimeCandidate(LocalTime.of(adjust(h), 0), null, i..end, confidence = Confidence.STRONG)
                }
            }
        }

        return null
    }

    /**
     * «tra/fra un'ora / 2 ore / 30 minuti / mezz'ora» — офсет от «сейчас».
     * Перекат за полночь делает сборка: прошедшее время уходит на завтра.
     */
    fun offsetTime(tokens: List<Token>, used: BooleanArray, now: ZonedDateTime): TimeCandidate? {
        for (i in tokens.indices) {
            if (used[i]) continue
            val t = tokens[i].lower
            if (t != "tra" && t != "fra") continue
            val next = tokens.getOrNull(i + 1)?.lower ?: continue
            val base = now.toLocalTime().withSecond(0).withNano(0)
            // «un'ora» / «mezz'ora» — апостроф разрезан токенайзером
            if ((next == "un" || next == "mezz") && tokens.getOrNull(i + 2)?.lower == "ora" &&
                free(used, i..i + 2, tokens.size)
            ) {
                val time = if (next == "un") base.plusHours(1) else base.plusMinutes(30)
                return TimeCandidate(time, null, i..i + 2)
            }
            // «un quarto d'ora» / «tre quarti d'ora» → d, ora
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
            val n = next.toLongOrNull()?.takeIf { it in 1..999 } ?: continue
            val time = when (tokens.getOrNull(i + 2)?.lower) {
                in offsetHours -> base.plusHours(n)
                in offsetMinutes -> base.plusMinutes(n)
                else -> null
            } ?: continue
            if (free(used, i..i + 2, tokens.size)) return TimeCandidate(time, null, i..i + 2)
        }
        return null
    }

    /** Голый час после распознанного куска — общий цикл в ядре, доводка наша. */
    fun bareHourAfterClaim(tokens: List<Token>, used: BooleanArray): TimeCandidate? =
        bareHourAfterClaim(tokens, used) { time, range ->
            refine(tokens, used, time, range, confidence = Confidence.WEAK)
        }

    /**
     * Доводка часа круга 1..12: уточнение половины суток следом снимает пару;
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

    /** `[di|del|della] <половина суток>` c позиции k → (доводка, конец матча). */
    private fun daypartAt(tokens: List<Token>, used: BooleanArray, k: Int): Pair<(Int) -> Int, Int>? {
        if (tokens.getOrNull(k)?.lower !in daypartLinks || !free(used, k..k, tokens.size)) return null
        val adjust = dayparts[tokens.getOrNull(k + 1)?.lower] ?: return null
        if (!free(used, k + 1..k + 1, tokens.size)) return null
        return adjust to k + 1
    }

    /**
     * Постфиксы минут: «e mezza/mezzo» (+30), «e un quarto» (+15), «e 20»
     * (цифрой; словами — не берём), «meno un quarto» (час−1:45).
     */
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

    private fun clockToken(s: String?): LocalTime? = ClockText.clock(s) ?: ClockText.dottedClock(s)
}
