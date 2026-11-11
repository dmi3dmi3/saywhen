package com.dmi3dmi3.saywhen.settings

import com.dmi3dmi3.saywhen.parser.MultilingualEventParser

fun parserOrder(
    uiLanguage: String?,
    parserOn: Set<String> = emptySet(),
    parserOff: Set<String> = emptySet(),
    systemLanguages: List<String> = emptyList(),
    keyboardLanguages: List<String> = emptyList(),
): List<String> {
    val known = MultilingualEventParser.LANGUAGES
    val system = languageCodes(systemLanguages).filter { it in known }
    val auto = autoLanguages(uiLanguage, systemLanguages, keyboardLanguages)
    val effective = (auto + parserOn - parserOff).filterTo(mutableSetOf()) { it in known }
        .takeIf { it.isNotEmpty() } ?: auto
    val priority = (listOfNotNull(uiLanguage, "en") + system).distinct()
    return priority.filter { it in effective } + known.filter { it in effective && it !in priority }
}

fun autoLanguages(
    uiLanguage: String?,
    systemLanguages: List<String> = emptyList(),
    keyboardLanguages: List<String> = emptyList(),
): Set<String> {
    val known = MultilingualEventParser.LANGUAGES
    return setOfNotNull(uiLanguage?.takeIf { it in known }, "en") +
        languageCodes(systemLanguages).filter { it in known } +
        languageCodes(keyboardLanguages).filter { it in known }
}

enum class LanguageReason { UI, ALWAYS, SYSTEM, KEYBOARD, MANUAL_ON, MANUAL_OFF, ONLY, NONE }

fun languageReason(
    code: String,
    uiLanguage: String?,
    parserOn: Set<String>,
    parserOff: Set<String>,
    systemLanguages: List<String> = emptyList(),
    keyboardLanguages: List<String> = emptyList(),
): LanguageReason {
    val order = parserOrder(uiLanguage, parserOn, parserOff, systemLanguages, keyboardLanguages)
    val auto = autoLanguages(uiLanguage, systemLanguages, keyboardLanguages)
    if (code !in order) return if (code in parserOff && code in auto) LanguageReason.MANUAL_OFF else LanguageReason.NONE
    return when {
        order.size == 1 -> LanguageReason.ONLY
        code !in auto -> LanguageReason.MANUAL_ON
        code == uiLanguage -> LanguageReason.UI
        code == "en" -> LanguageReason.ALWAYS
        code in languageCodes(systemLanguages) -> LanguageReason.SYSTEM
        else -> LanguageReason.KEYBOARD
    }
}

fun toggleParserLanguage(
    code: String,
    on: Boolean,
    auto: Set<String>,
    parserOn: Set<String>,
    parserOff: Set<String>,
): Pair<Set<String>, Set<String>> = when {
    on == (code in auto) -> (parserOn - code) to (parserOff - code)
    on -> (parserOn + code) to (parserOff - code)
    else -> (parserOn - code) to (parserOff + code)
}

private fun languageCodes(tags: List<String>): List<String> =
    tags.map { it.substringBefore('-').substringBefore('_').trim().lowercase() }
        .filter { it.isNotEmpty() }
        .distinct()

fun migrateParserLanguages(frozen: Set<String>, uiLanguage: String?): Pair<Set<String>, Set<String>> {
    val known = MultilingualEventParser.LANGUAGES
    val on = frozen.filterTo(mutableSetOf<String>()) { it in known }
    if (on.isEmpty()) return emptySet<String>() to emptySet()
    val auto = setOfNotNull(uiLanguage?.takeIf { it in known }, "en")
    return on to (auto - on)
}
