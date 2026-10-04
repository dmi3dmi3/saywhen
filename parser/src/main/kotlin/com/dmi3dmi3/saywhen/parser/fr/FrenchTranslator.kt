package com.dmi3dmi3.saywhen.parser.fr

import com.dmi3dmi3.saywhen.parser.Extraction
import com.dmi3dmi3.saywhen.parser.Pipeline
import com.dmi3dmi3.saywhen.parser.Token
import com.dmi3dmi3.saywhen.parser.Translator
import java.time.ZonedDateTime

internal object FrenchTranslator : Translator {

    override val orphanWords = setOf(
        "à", "au", "aux", "le", "la", "les", "l", "de", "du", "d", "en", "dans", "pour",
        "entre", "jusqu", "vers", "environ", "autour", "alentours", "plutôt", "plutot",
    )
    override val defaultTitle = "Évènement"

    private val pipeline =
        Pipeline(FrRecurrenceRules, FrReminderRules, FrDateRules, FrTimeRules, FrDurationRules, FrWords.hourUnits)

    override fun extract(tokens: List<Token>, now: ZonedDateTime, blocked: BooleanArray): Extraction =
        pipeline.extract(tokens, now, blocked)
}
