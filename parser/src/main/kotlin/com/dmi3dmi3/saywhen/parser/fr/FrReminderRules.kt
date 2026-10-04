package com.dmi3dmi3.saywhen.parser.fr

import com.dmi3dmi3.saywhen.parser.GluedDurations
import com.dmi3dmi3.saywhen.parser.ReminderCandidate
import com.dmi3dmi3.saywhen.parser.ReminderRule
import com.dmi3dmi3.saywhen.parser.Token
import com.dmi3dmi3.saywhen.parser.VerboseReminder

internal object FrReminderRules : ReminderRule {

    private val verbose = VerboseReminder(
        triggers = setOf(
            "rappel", "rappelle", "rappelle-moi", "rappeler",
            "préviens", "previens", "préviens-moi", "previens-moi", "prévenir", "prevenir",
        ),
        follower = "moi",
        ones = setOf("une"),
        minuteWords = FrWords.minuteWords,
        hourWords = FrWords.hourWords,
        tails = setOf("avant"),
        glue = GluedDurations(FrWords.hourUnits, FrWords.minuteUnits, FrWords.minuteWords),
    )

    override fun find(tokens: List<Token>, used: BooleanArray): ReminderCandidate? {
        val found = verbose.find(tokens, used) ?: return null
        val first = found.tokens.first
        val me = first > 0 && !used[first - 1] && tokens[first - 1].lower == "me"
        return if (me) found.copy(tokens = first - 1..found.tokens.last) else found
    }
}
