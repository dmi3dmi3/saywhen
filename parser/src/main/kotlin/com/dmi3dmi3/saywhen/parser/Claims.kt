package com.dmi3dmi3.saywhen.parser

import java.time.LocalTime

/*
 * Операции над маской занятости токенов — общие для правил всех языков.
 */

/** Диапазон существует и целиком свободен. */
internal fun free(used: BooleanArray, range: IntRange, size: Int): Boolean =
    range.last < size && range.all { !used[it] }

/**
 * Фоллбэк, когда обычные правила времени ничего не нашли: голое число 0–23
 * сразу после распознанного куска (даты/повтора) читается как час —
 * «завтра 11 планёрка», "tomorrow 11 standup". Смежность обязательна:
 * «купить 15 яиц» временем не становится. Языковая доводка половины суток —
 * в refine транслятора.
 */
internal inline fun bareHourAfterClaim(
    tokens: List<Token>,
    used: BooleanArray,
    refine: (LocalTime, IntRange) -> TimeCandidate,
): TimeCandidate? {
    for (i in 1 until tokens.size) {
        if (used[i] || !used[i - 1]) continue
        val hour = tokens[i].lower.takeIf { it.length <= 2 }?.toIntOrNull() ?: continue
        if (hour in 0..23) return refine(LocalTime.of(hour, 0), i..i)
    }
    return null
}
