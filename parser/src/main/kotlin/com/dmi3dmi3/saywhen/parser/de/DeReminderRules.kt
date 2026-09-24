package com.dmi3dmi3.saywhen.parser.de

import com.dmi3dmi3.saywhen.parser.ReminderCandidate
import com.dmi3dmi3.saywhen.parser.Token
import com.dmi3dmi3.saywhen.parser.VerboseReminder

/** «erinnere mich 10 Minuten / eine Stunde vorher|davor» — общая логика хвостового типа в ядре. */
internal object DeReminderRules {

    private val verbose = VerboseReminder(
        triggers = setOf("erinnere"),
        follower = "mich",
        followerRequired = true,
        ones = setOf("eine"),
        minuteWords = setOf("minute", "minuten", "min"),
        hourWords = setOf("stunde", "stunden", "std"),
        tails = setOf("vorher", "davor"),
    )

    fun find(tokens: List<Token>, used: BooleanArray): ReminderCandidate? = verbose.find(tokens, used)
}
