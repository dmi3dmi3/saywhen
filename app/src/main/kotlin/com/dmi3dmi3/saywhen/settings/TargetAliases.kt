package com.dmi3dmi3.saywhen.settings

import com.dmi3dmi3.saywhen.calendar.CalendarInfo
import com.dmi3dmi3.saywhen.target.TASKS_KEY
import com.dmi3dmi3.saywhen.target.TargetInfo
import com.dmi3dmi3.saywhen.target.toTargetInfo

fun calendarKey(id: Long): String = "calendar/$id"

fun normalizeAlias(raw: String): String =
    raw.trim().trimStart('#').trim().lowercase().replace(Regex("\\s+"), " ")

private fun beforeAt(s: String): String = s.substringBefore('@')

private const val RESERVED_TASKS_ALIAS = "tasks"

private fun reservedAlias(t: TargetInfo): String? = if (t.key == TASKS_KEY) RESERVED_TASKS_ALIAS else null

fun derivedAliases(targets: List<TargetInfo>): Map<String, String> {
    var aliases = targets.associate { it.key to (reservedAlias(it) ?: normalizeAlias(beforeAt(it.name))) }
    fun disambiguate(suffix: (TargetInfo) -> String) {
        val counts = aliases.values.groupingBy { it }.eachCount()
        aliases = targets.associate { t ->
            val alias = aliases.getValue(t.key)
            val yields = counts.getValue(alias) > 1 && reservedAlias(t) == null
            t.key to if (yields) normalizeAlias("$alias ${suffix(t)}") else alias
        }
    }
    disambiguate { beforeAt(it.account) }
    disambiguate { it.key.substringAfter('/') }
    return aliases
}

@JvmName("derivedCalendarAliases")
fun derivedAliases(calendars: List<CalendarInfo>): Map<String, String> =
    derivedAliases(calendars.map { it.toTargetInfo() })

fun effectiveAliases(targets: List<TargetInfo>, manual: Map<String, String>): Map<String, String> {
    val derived = derivedAliases(targets)
    val chosen = targets.mapNotNull { t ->
        manual[t.key]?.let(::normalizeAlias)?.takeIf { it.isNotEmpty() }?.let { t.key to it }
    }.toMap()
    var aliases = targets.associate { t -> t.key to (chosen[t.key] ?: derived.getValue(t.key)) }
    fun yieldDuplicates(suffix: (TargetInfo) -> String) {
        val counts = aliases.values.groupingBy { it }.eachCount()
        aliases = targets.associate { t ->
            val alias = aliases.getValue(t.key)
            val yields = t.key !in chosen && counts.getValue(alias) > 1
            t.key to if (yields) normalizeAlias("$alias ${suffix(t)}") else alias
        }
    }
    yieldDuplicates { beforeAt(it.account) }
    yieldDuplicates { it.key.substringAfter('/') }
    return aliases
}

@JvmName("effectiveCalendarAliases")
fun effectiveAliases(calendars: List<CalendarInfo>, manual: Map<String, String>): Map<String, String> =
    effectiveAliases(calendars.map { it.toTargetInfo() }, manual)

data class TargetSuggestions(val range: IntRange, val options: List<String>)

fun targetSuggestions(text: String, caret: Int, aliases: Collection<String>): TargetSuggestions? {
    val end = caret.coerceIn(0, text.length)
    if (end < text.length && !text[end].isWhitespace()) return null
    val hash = text.lastIndexOf('#', end - 1)
    if (hash < 0) return null
    val typed = text.substring(hash + 1, end).lowercase()
    if (aliases.any { it.equals(typed, ignoreCase = true) }) return null
    val options = aliases.filter { a -> a.startsWith(typed) || a.split(' ').any { it.startsWith(typed) } }
    if (options.isEmpty()) return null
    return TargetSuggestions(hash until end, options)
}
