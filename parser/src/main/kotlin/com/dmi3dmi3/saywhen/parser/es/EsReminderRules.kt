package com.dmi3dmi3.saywhen.parser.es

import com.dmi3dmi3.saywhen.parser.ReminderCandidate
import com.dmi3dmi3.saywhen.parser.Token
import com.dmi3dmi3.saywhen.parser.VerboseReminder

/** «recuérdame/avísame 10 minutos / una hora antes» — общая логика хвостового типа в ядре. */
internal object EsReminderRules {

    private val verbose = VerboseReminder(
        triggers = setOf("recuérdame", "recuerdame", "avísame", "avisame"),
        ones = setOf("una"),
        minuteWords = setOf("minuto", "minutos", "min"),
        hourWords = setOf("hora", "horas"),
        tails = setOf("antes"),
    )

    fun find(tokens: List<Token>, used: BooleanArray): ReminderCandidate? = verbose.find(tokens, used)
}
