package com.dmi3dmi3.saywhen.settings

import com.dmi3dmi3.saywhen.parser.MultilingualEventParser

/**
 * Порядок парсеров из настроек (задача 30): локальный язык первым, en вторым,
 * остальные включённые — каноническим порядком [MultilingualEventParser.LANGUAGES].
 * [enabled] == null — авто-режим «следовать языку интерфейса»: {язык UI, en};
 * пустой/мусорный ручной набор ведёт себя как авто (защита от битого стора).
 * Чистая функция — покрыта JVM-тестом.
 */
fun parserOrder(uiLanguage: String?, enabled: Set<String>?): List<String> {
    val known = MultilingualEventParser.LANGUAGES
    val set = enabled?.filterTo(mutableSetOf()) { it in known }?.takeIf { it.isNotEmpty() }
        ?: setOfNotNull(uiLanguage?.takeIf { it in known }, "en")
    val priority = listOfNotNull(uiLanguage, "en").distinct()
    return priority.filter { it in set } + known.filter { it in set && it !in priority }
}
