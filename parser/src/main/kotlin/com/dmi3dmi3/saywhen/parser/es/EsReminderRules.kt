package com.dmi3dmi3.saywhen.parser.es

import com.dmi3dmi3.saywhen.parser.ReminderCandidate
import com.dmi3dmi3.saywhen.parser.ReminderRule
import com.dmi3dmi3.saywhen.parser.Token
import com.dmi3dmi3.saywhen.parser.VerboseReminder

internal object EsReminderRules : ReminderRule {

    private val verbose = VerboseReminder(
        triggers = setOf("recuérdame", "recuerdame", "avísame", "avisame"),
        ones = setOf("una"),
        minuteWords = EsWords.minuteWords,
        hourWords = EsWords.hourWords,
        tails = setOf("antes"),
    )

    override fun find(tokens: List<Token>, used: BooleanArray): ReminderCandidate? = verbose.find(tokens, used)
}
