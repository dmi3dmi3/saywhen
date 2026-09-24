package com.dmi3dmi3.saywhen.parser.ru

import com.dmi3dmi3.saywhen.parser.ClockText
import com.dmi3dmi3.saywhen.parser.Confidence
import com.dmi3dmi3.saywhen.parser.TimeCandidate
import com.dmi3dmi3.saywhen.parser.Token
import com.dmi3dmi3.saywhen.parser.bareHourAfterClaim
import com.dmi3dmi3.saywhen.parser.free
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

internal object TimeRules {

    private val hourWords = setOf("час", "часа", "часов")
    private val minuteWords = setOf("минуту", "минуты", "минут")

    // «в 15ч» — час, склеенный с единицей
    private val gluedHour = Regex("""(\d{1,2})ч""")

    private val wordHours = mapOf(
        "час" to 1, "два" to 2, "три" to 3, "четыре" to 4, "пять" to 5, "шесть" to 6,
        "семь" to 7, "восемь" to 8, "девять" to 9, "десять" to 10,
        "одиннадцать" to 11, "двенадцать" to 12,
    )

    private val fixedTimes = mapOf("полдень" to LocalTime.NOON, "полночь" to LocalTime.MIDNIGHT)

    // «пол седьмого» = 6:30: родительный порядковый → час − 1
    private val halfOrdinals = mapOf(
        "первого" to 0, "второго" to 1, "третьего" to 2, "четвёртого" to 3,
        "четвертого" to 3, "пятого" to 4, "шестого" to 5, "седьмого" to 6,
        "восьмого" to 7, "девятого" to 8, "десятого" to 9,
        "одиннадцатого" to 10, "двенадцатого" to 11,
    )

    // уточнение половины суток снимает пару; 12 — особый (12 дня = полдень, 12 ночи/вечера = полночь)
    private val dayparts = mapOf<String, (Int) -> Int>(
        "утра" to { h -> h },
        "дня" to { h -> if (h < 12) h + 12 else 12 },
        "вечера" to { h -> if (h < 12) h + 12 else 0 },
        "ночи" to { h -> if (h == 12) 0 else h },
    )

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

        // «с 15 до 17» / «с 9:30 до 11:00»
        if (t == "с" && free(used, i..i + 3, tokens.size)) {
            val from = clock(tokens.getOrNull(i + 1)?.lower)
            val to = clock(tokens.getOrNull(i + 3)?.lower)
            // «с 15 до 15» — не интервал; конец раньше начала — через полночь
            if (from != null && to != null && from != to && tokens[i + 2].lower == "до") {
                var d = Duration.between(from, to)
                if (d < Duration.ZERO) d = d.plusHours(24)  // «с 23 до 1»
                return TimeCandidate(from, d, i..i + 3)
            }
        }

        // «полдень» / «полночь» — фиксированные, предлог подберёт поглощение сирот
        fixedTimes[t]?.let { return TimeCandidate(it, null, i..i, confidence = Confidence.STRONG) }

        // «пол седьмого» / «полседьмого» / «пол-седьмого»
        if (t == "пол" && free(used, i..i + 1, tokens.size)) {
            halfOrdinals[tokens.getOrNull(i + 1)?.lower]?.let { h ->
                return refine(tokens, used, LocalTime.of(h, 30), i..i + 1, inCircle = true, Confidence.STRONG)
            }
        }
        val glued = when {
            t.startsWith("пол-") -> t.removePrefix("пол-")
            t.startsWith("пол") -> t.removePrefix("пол")
            else -> null
        }
        glued?.let { halfOrdinals[it] }?.let { h ->
            return refine(tokens, used, LocalTime.of(h, 30), i..i, inCircle = true, Confidence.STRONG)
        }

        // «8 часов вечера» — без предлога, но только с половиной суток следом
        run {
            val h = t.toIntOrNull() ?: return@run
            if (h !in 1..12) return@run
            if (tokens.getOrNull(i + 1)?.lower !in hourWords) return@run
            val adjust = dayparts[tokens.getOrNull(i + 2)?.lower] ?: return@run
            if (free(used, i..i + 2, tokens.size)) {
                return TimeCandidate(LocalTime.of(adjust(h), 0), null, i..i + 2, confidence = Confidence.STRONG)
            }
        }

        // «в 15» / «в 9:30» / «в 19.30» / «в 19 30» / «в 15 часов» / «в 15ч» / «в три [часа]» / «в 7 вечера»
        if (t == "в" && free(used, i..i + 1, tokens.size)) {
            val raw = tokens.getOrNull(i + 1)?.lower
            val time = clock(raw) ?: ClockText.dottedClock(raw) ?: gluedHourTime(raw)
            if (time != null && raw != null) {
                if (':' !in raw) {
                    // «в 19 30» — минуты отдельным токеном
                    val mm = ClockText.pairMinutes(tokens.getOrNull(i + 2)?.lower)
                    if (mm != null && free(used, i + 2..i + 2, tokens.size)) {
                        return refine(tokens, used, time.withMinute(mm), i..i + 2)
                    }
                    // «в 15 часов»
                    if (tokens.getOrNull(i + 2)?.lower in hourWords && free(used, i + 2..i + 2, tokens.size)) {
                        return refine(tokens, used, time, i..i + 2)
                    }
                }
                return refine(tokens, used, time, i..i + 1)
            }
            // «в три [часа] [дня]» — часы словами, только с предлогом
            wordHours[raw]?.let { h ->
                var end = i + 1
                if (tokens.getOrNull(end + 1)?.lower in hourWords && free(used, end + 1..end + 1, tokens.size)) end++
                return refine(tokens, used, LocalTime.of(h, 0), i..end, confidence = Confidence.STRONG)
            }
        }

        // склеенный интервал «23:40-23:00» / «10-11:30» — общий числовой слой
        if ('-' in t) {
            ClockText.gluedInterval(t)?.let { (from, d) -> return TimeCandidate(from, d, i..i) }
            return null
        }

        // голое «9:30» — двоеточие делает его временем (но не половиной суток)
        if (':' in t) {
            clock(t)?.let { return refine(tokens, used, it, i..i) }
        }

        // голая пара «11 00» — двухцифровые минуты держат якорь не хуже двоеточия
        val mm = ClockText.pairMinutes(tokens.getOrNull(i + 1)?.lower)
        if (mm != null && free(used, i..i + 1, tokens.size)) {
            clock(t)?.let { return refine(tokens, used, it.withMinute(mm), i..i + 1) }
        }

        return null
    }

    /**
     * Доводка одиночного времени с часом круга 1..12 (и 0:30 у «пол первого»):
     * уточнение «утра/дня/вечера/ночи» следом снимает пару и приклеивается к
     * матчу; без уточнения кандидат помечается флагом — выбор из пары делает
     * сборка ([TwelveHourClock]).
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
        val adjust = tokens.getOrNull(next)?.lower?.let { dayparts[it] }
        if (adjust != null && free(used, next..next, tokens.size)) {
            return TimeCandidate(time.withHour(adjust(time.hour)), null, range.first..next, confidence = confidence)
        }
        return TimeCandidate(time, null, range, twelveHour = true, confidence = confidence)
    }

    /**
     * «через час / через 2 часа / через 30 минут» — офсет от «сейчас».
     * Перекат за полночь делает сборка: прошедшее время уходит на завтра.
     */
    fun offsetTime(tokens: List<Token>, used: BooleanArray, now: ZonedDateTime): TimeCandidate? {
        for (i in tokens.indices) {
            if (used[i] || tokens[i].lower != "через") continue
            val next = tokens.getOrNull(i + 1)?.lower ?: continue
            val base = now.toLocalTime().withSecond(0).withNano(0)
            if (next in hourWords && free(used, i..i + 1, tokens.size)) {
                return TimeCandidate(base.plusHours(1), null, i..i + 1)
            }
            val n = next.toLongOrNull()?.takeIf { it in 1..999 } ?: continue
            val time = when (tokens.getOrNull(i + 2)?.lower) {
                in hourWords -> base.plusHours(n)
                in minuteWords -> base.plusMinutes(n)
                else -> null
            } ?: continue
            if (free(used, i..i + 2, tokens.size)) return TimeCandidate(time, null, i..i + 2)
        }
        return null
    }

    private fun gluedHourTime(s: String?): LocalTime? =
        s?.let { gluedHour.matchEntire(it) }?.groupValues?.get(1)?.toIntOrNull()
            ?.takeIf { it in 0..23 }?.let { LocalTime.of(it, 0) }

    /** Голый час после распознанного куска — общий цикл в ядре, доводка наша. */
    fun bareHourAfterClaim(tokens: List<Token>, used: BooleanArray): TimeCandidate? =
        bareHourAfterClaim(tokens, used) { time, range ->
            refine(tokens, used, time, range, confidence = Confidence.WEAK)
        }

    private fun clock(s: String?) = ClockText.clock(s)
}
