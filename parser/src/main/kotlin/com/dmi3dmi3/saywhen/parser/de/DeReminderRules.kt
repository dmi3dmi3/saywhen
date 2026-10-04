package com.dmi3dmi3.saywhen.parser.de

import com.dmi3dmi3.saywhen.parser.ReminderCandidate
import com.dmi3dmi3.saywhen.parser.ReminderRule
import com.dmi3dmi3.saywhen.parser.Token
import com.dmi3dmi3.saywhen.parser.VerboseReminder

internal object DeReminderRules : ReminderRule {

    private val verbose = VerboseReminder(
        triggers = setOf("erinnere"),
        follower = "mich",
        followerRequired = true,
        ones = setOf("eine"),
        minuteWords = DeWords.minuteWords,
        hourWords = DeWords.hourWords,
        tails = setOf("vorher", "davor"),
    )

    override fun find(tokens: List<Token>, used: BooleanArray): ReminderCandidate? = verbose.find(tokens, used)
}
