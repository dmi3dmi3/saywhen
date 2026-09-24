package com.dmi3dmi3.saywhen.parser.en

import com.dmi3dmi3.saywhen.parser.ReminderCandidate
import com.dmi3dmi3.saywhen.parser.Token
import com.dmi3dmi3.saywhen.parser.VerboseReminder

/** "remind [me] 30 minutes/an hour before" — общая логика хвостового типа в ядре. */
internal object EnReminderRules {

    private val verbose = VerboseReminder(
        triggers = setOf("remind"),
        follower = "me",
        ones = setOf("an", "a"),
        minuteWords = setOf("minute", "minutes", "min", "mins"),
        hourWords = setOf("hour", "hours", "hr", "hrs"),
        tails = setOf("before"),
    )

    fun find(tokens: List<Token>, used: BooleanArray): ReminderCandidate? = verbose.find(tokens, used)
}
