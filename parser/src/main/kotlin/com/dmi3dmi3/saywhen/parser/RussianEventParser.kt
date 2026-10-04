package com.dmi3dmi3.saywhen.parser

import com.dmi3dmi3.saywhen.parser.ru.RussianTranslator

class RussianEventParser :
    EventParser by MultilingualEventParser(listOf(RussianTranslator))
