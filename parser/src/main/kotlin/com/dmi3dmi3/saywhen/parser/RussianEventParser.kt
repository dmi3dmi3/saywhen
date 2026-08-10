package com.dmi3dmi3.saywhen.parser

import com.dmi3dmi3.saywhen.parser.ru.RussianTranslator

/**
 * Только русский язык — [MultilingualEventParser] с одним транслятором.
 * Пайплайн и архитектура описаны там; здесь — имя для обратной совместимости
 * и языковых тестов.
 */
class RussianEventParser :
    EventParser by MultilingualEventParser(listOf(RussianTranslator))
