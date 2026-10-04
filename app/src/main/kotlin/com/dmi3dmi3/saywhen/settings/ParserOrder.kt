package com.dmi3dmi3.saywhen.settings

import com.dmi3dmi3.saywhen.parser.MultilingualEventParser

fun parserOrder(
    uiLanguage: String?,
    parserOn: Set<String> = emptySet(),
    parserOff: Set<String> = emptySet(),
): List<String> {
    val known = MultilingualEventParser.LANGUAGES
    val auto = setOfNotNull(uiLanguage?.takeIf { it in known }, "en")
    val effective = (auto + parserOn - parserOff).filterTo(mutableSetOf()) { it in known }
        .takeIf { it.isNotEmpty() } ?: auto
    val priority = listOfNotNull(uiLanguage, "en").distinct()
    return priority.filter { it in effective } + known.filter { it in effective && it !in priority }
}

fun migrateParserLanguages(frozen: Set<String>, uiLanguage: String?): Pair<Set<String>, Set<String>> {
    val known = MultilingualEventParser.LANGUAGES
    val on = frozen.filterTo(mutableSetOf<String>()) { it in known }
    if (on.isEmpty()) return emptySet<String>() to emptySet()
    val auto = setOfNotNull(uiLanguage?.takeIf { it in known }, "en")
    return on to (auto - on)
}
