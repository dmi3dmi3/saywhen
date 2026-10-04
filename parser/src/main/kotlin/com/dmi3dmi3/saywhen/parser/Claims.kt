package com.dmi3dmi3.saywhen.parser

import java.time.DayOfWeek
import java.time.LocalTime

internal inline fun dateCandidates(
    tokens: List<Token>,
    used: BooleanArray,
    matchAt: (Int) -> DateCandidate?,
): List<DateCandidate> {
    val found = mutableListOf<DateCandidate>()
    var i = 0
    while (i < tokens.size) {
        val candidate = if (used[i]) null else matchAt(i)
        if (candidate != null && candidate.tokens.all { !used[it] }) {
            found += candidate
            i = candidate.tokens.last + 1
        } else {
            i++
        }
    }
    return found
}

internal fun consumeDays(
    tokens: List<Token>,
    start: Int,
    used: BooleanArray,
    days: MutableSet<DayOfWeek>,
    conjunction: String,
    lookup: (String) -> Set<DayOfWeek>?,
): Int {
    var j = start
    while (true) {
        val k = if (tokens.getOrNull(j)?.lower == conjunction) j + 1 else j
        val word = tokens.getOrNull(k)?.lower ?: break
        if ((j..k).any { used[it] }) break
        days += lookup(word) ?: break
        j = k + 1
    }
    return j
}

internal fun free(used: BooleanArray, range: IntRange, size: Int): Boolean =
    range.last < size && range.all { !used[it] }

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
