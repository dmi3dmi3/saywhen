package com.dmi3dmi3.saywhen.parser.de

import com.dmi3dmi3.saywhen.parser.Extraction
import com.dmi3dmi3.saywhen.parser.Pipeline
import com.dmi3dmi3.saywhen.parser.Token
import com.dmi3dmi3.saywhen.parser.Translator
import java.time.ZonedDateTime

internal object GermanTranslator : Translator {

    override val orphanWords = setOf(
        "um", "am", "im", "an", "von", "vom", "bis", "für", "fuer", "in", "zu", "zum", "zur",
        "der", "die", "das", "den", "des", "gegen", "circa", "zirka", "ca", "ungefähr", "etwa", "so",
    )
    override val defaultTitle = "Termin"

    private val pipeline =
        Pipeline(DeRecurrenceRules, DeReminderRules, DeDateRules, DeTimeRules, DeDurationRules, DeWords.hourUnits)

    override fun extract(tokens: List<Token>, now: ZonedDateTime, blocked: BooleanArray): Extraction =
        pipeline.extract(tokens, now, blocked)
}
