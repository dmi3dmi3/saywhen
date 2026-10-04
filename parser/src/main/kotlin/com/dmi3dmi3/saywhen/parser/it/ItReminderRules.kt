package com.dmi3dmi3.saywhen.parser.it

import com.dmi3dmi3.saywhen.parser.ReminderCandidate
import com.dmi3dmi3.saywhen.parser.ReminderRule
import com.dmi3dmi3.saywhen.parser.Token
import com.dmi3dmi3.saywhen.parser.VerboseReminder

internal object ItReminderRules : ReminderRule {

    private val verbose = VerboseReminder(
        triggers = setOf("ricordamelo", "ricordami", "avvisami"),
        ones = setOf("un"),
        minuteWords = ItWords.minuteWords,
        hourWords = ItWords.hourWords,
        tails = setOf("prima"),
    )

    override fun find(tokens: List<Token>, used: BooleanArray): ReminderCandidate? = verbose.find(tokens, used)
}
