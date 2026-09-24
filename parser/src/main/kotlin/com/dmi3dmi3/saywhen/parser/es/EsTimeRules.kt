package com.dmi3dmi3.saywhen.parser.es

import com.dmi3dmi3.saywhen.parser.ClockText
import com.dmi3dmi3.saywhen.parser.Confidence
import com.dmi3dmi3.saywhen.parser.TimeCandidate
import com.dmi3dmi3.saywhen.parser.Token
import com.dmi3dmi3.saywhen.parser.bareHourAfterClaim
import com.dmi3dmi3.saywhen.parser.free
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

internal object EsTimeRules {

    private val wordHours = mapOf(
        "una" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5, "seis" to 6,
        "siete" to 7, "ocho" to 8, "nueve" to 9, "diez" to 10, "once" to 11, "doce" to 12,
    )

    private val fixedTimes = mapOf(
        "mediodía" to LocalTime.NOON, "mediodia" to LocalTime.NOON,
        "medianoche" to LocalTime.MIDNIGHT,
    )

    // уточнение половины суток: [de|en|por] la + слово; 12 — как у соседей
    private val dayparts = mapOf<String, (Int) -> Int>(
        "mañana" to { h -> if (h == 12) 0 else h },
        "manana" to { h -> if (h == 12) 0 else h },
        "tarde" to { h -> if (h < 12) h + 12 else 12 },
        "noche" to { h -> if (h < 12) h + 12 else 0 },
    )
    private val daypartLinks = setOf("de", "en", "por")

    private val offsetHours = setOf("hora", "horas")
    private val offsetMinutes = setOf("minuto", "minutos")

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

        // интервал «de las 15 a las 17» — артикли обязательны («de» вездесущ)
        if (t == "de" && tokens.getOrNull(i + 1)?.lower == "las" &&
            free(used, i..i + 5, tokens.size)
        ) {
            val from = clockToken(tokens.getOrNull(i + 2)?.lower)
            val to = clockToken(tokens.getOrNull(i + 5)?.lower)
            if (from != null && to != null && from != to &&
                tokens.getOrNull(i + 3)?.lower == "a" &&
                tokens.getOrNull(i + 4)?.lower in setOf("las", "la")
            ) {
                var d = Duration.between(from, to)
                if (d < Duration.ZERO) d = d.plusHours(24)
                return TimeCandidate(from, d, i..i + 5)
            }
        }

        fixedTimes[t]?.let { return TimeCandidate(it, null, i..i, confidence = Confidence.STRONG) }

        // «medio día» раздельно
        if (t == "medio" && tokens.getOrNull(i + 1)?.lower in setOf("día", "dia") &&
            free(used, i..i + 1, tokens.size)
        ) {
            return TimeCandidate(LocalTime.NOON, null, i..i + 1, confidence = Confidence.STRONG)
        }

        // «a las 15 / a las 9:30 / a las 19.30 / a las 3 20 / a las 3 y 20 / a la una / a las 15 horas»
        if (t == "a" && tokens.getOrNull(i + 1)?.lower in setOf("las", "la") &&
            free(used, i..i + 2, tokens.size)
        ) {
            val raw = tokens.getOrNull(i + 2)?.lower
            val base = clockToken(raw)
            if (base != null && raw != null) {
                if (':' !in raw && '.' !in raw) {
                    // «a las 15 horas» — административный хвост
                    if (tokens.getOrNull(i + 3)?.lower == "horas" && free(used, i + 3..i + 3, tokens.size)) {
                        return refine(tokens, used, base, i..i + 3)
                    }
                    // пара «a las 3 20»
                    val mm = ClockText.pairMinutes(tokens.getOrNull(i + 3)?.lower)
                    if (mm != null && free(used, i + 3..i + 3, tokens.size)) {
                        return refine(tokens, used, base.withMinute(mm), i..i + 3)
                    }
                    postfix(tokens, used, base.hour, i + 3)?.let { (time, end) ->
                        return refine(tokens, used, time, i..end, inCircle = base.hour in 1..12, confidence = Confidence.STRONG)
                    }
                }
                return refine(tokens, used, base, i..i + 2)
            }
            wordHours[raw]?.let { h ->
                postfix(tokens, used, h, i + 3)?.let { (time, end) ->
                    return refine(tokens, used, time, i..end, inCircle = true, confidence = Confidence.STRONG)
                }
                return refine(tokens, used, LocalTime.of(h, 0), i..i + 2, inCircle = true, Confidence.STRONG)
            }
        }

        // «las tres …» — только с продолжением (постфикс или половина суток):
        // голое «las 3» рискует числом заголовка
        if (t == "las" && free(used, i..i + 1, tokens.size)) {
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

        // «6 de la mañana / nueve de la noche» — голый час, но только с половиной суток
        run {
            val h = (t.toIntOrNull() ?: wordHours[t])?.takeIf { it in 1..12 } ?: return@run
            daypartAt(tokens, used, i + 1)?.let { (adjust, end) ->
                if (free(used, i..end, tokens.size)) {
                    return TimeCandidate(LocalTime.of(adjust(h), 0), null, i..end, confidence = Confidence.STRONG)
                }
            }
        }

        // голое «9:30» — двоеточие держит якорь
        if (':' in t) {
            ClockText.clock(t)?.let { return refine(tokens, used, it, i..i) }
        }

        return null
    }

    /**
     * «en una hora / en 2 horas / en media hora / en un cuarto de hora» и
     * «dentro de …» — офсет от «сейчас»; перекат за полночь делает сборка.
     */
    fun offsetTime(tokens: List<Token>, used: BooleanArray, now: ZonedDateTime): TimeCandidate? {
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
            // «media hora»
            if (next == "media" && tokens.getOrNull(j + 1)?.lower == "hora" &&
                free(used, i..j + 1, tokens.size)
            ) {
                return TimeCandidate(base.plusMinutes(30), null, i..j + 1)
            }
            // «un cuarto de hora»
            if (next == "un" && tokens.getOrNull(j + 1)?.lower == "cuarto" &&
                tokens.getOrNull(j + 2)?.lower == "de" && tokens.getOrNull(j + 3)?.lower == "hora" &&
                free(used, i..j + 3, tokens.size)
            ) {
                return TimeCandidate(base.plusMinutes(15), null, i..j + 3)
            }
            val n = next.toLongOrNull() ?: wordNumbersTime[next] ?: continue
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

    private val wordNumbersTime = mapOf(
        "un" to 1L, "una" to 1L, "dos" to 2L, "tres" to 3L, "cuatro" to 4L, "cinco" to 5L,
        "seis" to 6L, "siete" to 7L, "ocho" to 8L, "nueve" to 9L, "diez" to 10L,
    )

    /** Голый час после распознанного куска — общий цикл в ядре, доводка наша. */
    fun bareHourAfterClaim(tokens: List<Token>, used: BooleanArray): TimeCandidate? =
        bareHourAfterClaim(tokens, used) { time, range ->
            refine(tokens, used, time, range, confidence = Confidence.WEAK)
        }

    /**
     * Доводка часа круга 1..12: половина суток или pm/am следом снимает пару;
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
        val next = range.last + 1
        val marker = tokens.getOrNull(next)?.lower
        if ((marker == "am" || marker == "pm") && free(used, next..next, tokens.size)) {
            val h = if (marker == "am") (if (time.hour == 12) 0 else time.hour)
                    else (if (time.hour < 12) time.hour + 12 else 12)
            return TimeCandidate(time.withHour(h), null, range.first..next, confidence = confidence)
        }
        daypartAt(tokens, used, next)?.let { (adjust, end) ->
            return TimeCandidate(time.withHour(adjust(time.hour)), null, range.first..end, confidence = confidence)
        }
        return TimeCandidate(time, null, range, twelveHour = true, confidence = confidence)
    }

    /** `[de|en|por] la <половина суток>` c позиции k → (доводка, конец матча). */
    private fun daypartAt(tokens: List<Token>, used: BooleanArray, k: Int): Pair<(Int) -> Int, Int>? {
        if (tokens.getOrNull(k)?.lower !in daypartLinks || !free(used, k..k, tokens.size)) return null
        if (tokens.getOrNull(k + 1)?.lower != "la" || !free(used, k + 1..k + 1, tokens.size)) return null
        val adjust = dayparts[tokens.getOrNull(k + 2)?.lower] ?: return null
        if (!free(used, k + 2..k + 2, tokens.size)) return null
        return adjust to k + 2
    }

    /**
     * Постфиксы минут: «y media» (+30), «y cuarto» (+15), «y 20» (цифрой;
     * словами — не берём), «menos cuarto» / «menos N» (час−1).
     */
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
            wordNumbersTime[next]?.takeIf { it in 1..30 }?.let {
                return LocalTime.of(prev, (60 - it).toInt()) to k + 1
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
