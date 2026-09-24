package com.dmi3dmi3.saywhen.parser.it

import com.dmi3dmi3.saywhen.parser.ReminderCandidate
import com.dmi3dmi3.saywhen.parser.Token
import com.dmi3dmi3.saywhen.parser.VerboseReminder

/** «ricordamelo/avvisami 15 minuti / un'ora prima» — общая логика хвостового типа в ядре. */
internal object ItReminderRules {

    private val verbose = VerboseReminder(
        triggers = setOf("ricordamelo", "ricordami", "avvisami"),
        ones = setOf("un"),  // «un'ora» — токенайзер режет апостроф: «un» + «ora»
        minuteWords = setOf("minuto", "minuti", "min"),
        hourWords = setOf("ora", "ore"),
        tails = setOf("prima"),
    )

    fun find(tokens: List<Token>, used: BooleanArray): ReminderCandidate? = verbose.find(tokens, used)
}
