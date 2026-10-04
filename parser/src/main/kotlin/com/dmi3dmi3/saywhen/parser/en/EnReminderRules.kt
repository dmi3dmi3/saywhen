package com.dmi3dmi3.saywhen.parser.en

import com.dmi3dmi3.saywhen.parser.ReminderCandidate
import com.dmi3dmi3.saywhen.parser.ReminderRule
import com.dmi3dmi3.saywhen.parser.Token
import com.dmi3dmi3.saywhen.parser.VerboseReminder

internal object EnReminderRules : ReminderRule {

    private val verbose = VerboseReminder(
        triggers = setOf("remind"),
        follower = "me",
        ones = setOf("an", "a"),
        minuteWords = EnWords.minuteWords,
        hourWords = EnWords.hourWords,
        tails = setOf("before"),
    )

    override fun find(tokens: List<Token>, used: BooleanArray): ReminderCandidate? = verbose.find(tokens, used)
}
