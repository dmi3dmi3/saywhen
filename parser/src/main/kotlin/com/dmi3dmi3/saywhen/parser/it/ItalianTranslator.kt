package com.dmi3dmi3.saywhen.parser.it

import com.dmi3dmi3.saywhen.parser.Extraction
import com.dmi3dmi3.saywhen.parser.Pipeline
import com.dmi3dmi3.saywhen.parser.Token
import com.dmi3dmi3.saywhen.parser.Translator
import java.time.ZonedDateTime

internal object ItalianTranslator : Translator {

    override val orphanWords =
        setOf("a", "al", "alle", "il", "l", "le", "di", "per", "dal", "dalle", "tra", "fra", "fino", "entro", "nei")
    override val defaultTitle = "Evento"

    private val pipeline =
        Pipeline(ItRecurrenceRules, ItReminderRules, ItDateRules, ItTimeRules, ItDurationRules, ItWords.hourUnits)

    override fun extract(tokens: List<Token>, now: ZonedDateTime, blocked: BooleanArray): Extraction =
        pipeline.extract(tokens, now, blocked)
}
