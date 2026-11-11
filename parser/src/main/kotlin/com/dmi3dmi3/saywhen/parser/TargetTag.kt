package com.dmi3dmi3.saywhen.parser

internal data class TargetMatch(val range: IntRange, val alias: String)

internal object TargetTag {

    fun find(text: String, aliases: Set<String>, blockedRanges: List<IntRange>): TargetMatch? {
        if (aliases.isEmpty()) return null
        val sorted = aliases.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            .sortedByDescending { it.length }
        var found: TargetMatch? = null
        var i = text.indexOf('#')
        while (i >= 0) {
            val alias = sorted.firstOrNull { a ->
                val end = i + 1 + a.length
                end <= text.length &&
                    text.regionMatches(i + 1, a, 0, a.length, ignoreCase = true) &&
                    (end == text.length || !text[end].isLetterOrDigit())
            }
            if (alias != null) {
                val range = i until i + 1 + alias.length
                val blocked = blockedRanges.any { it.first <= range.last && range.first <= it.last }
                if (!blocked) found = TargetMatch(range, alias)
            }
            i = text.indexOf('#', i + 1)
        }
        return found
    }
}
