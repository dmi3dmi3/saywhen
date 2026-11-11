package com.dmi3dmi3.saywhen.settings

private const val SEPARATOR = " · "

fun summaryOf(vararg parts: String): String = parts.joinToString(SEPARATOR)

fun languagesSummary(names: List<String>): String =
    if (names.size <= 2) names.joinToString(", ")
    else names.take(2).joinToString(", ") + " +" + (names.size - 2)

fun timeSummary(startHour: Int, endHour: Int, dotTime: Boolean): String =
    summaryOf("$startHour–$endHour", "16.00 ${if (dotTime) "=" else "≠"} 16:00")

fun listSummary(parts: List<String>, empty: String): String =
    parts.joinToString(", ").replaceFirstChar { it.uppercase() }.ifEmpty { empty }

fun shortAlias(alias: String, max: Int = 14): String =
    if (alias.length <= max) alias else alias.take(max - 1) + "…"
