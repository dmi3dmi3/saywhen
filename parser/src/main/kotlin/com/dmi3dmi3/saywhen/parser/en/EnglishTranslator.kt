package com.dmi3dmi3.saywhen.parser.en

import com.dmi3dmi3.saywhen.parser.Extraction
import com.dmi3dmi3.saywhen.parser.Pipeline
import com.dmi3dmi3.saywhen.parser.Token
import com.dmi3dmi3.saywhen.parser.Translator
import java.time.ZonedDateTime

internal object EnglishTranslator : Translator {

    override val orphanWords = setOf("at", "on", "in", "from", "until", "till")
    override val defaultTitle = "Event"

    private val pipeline =
        Pipeline(EnRecurrenceRules, EnReminderRules, EnDateRules, EnTimeRules, EnDurationRules, EnWords.hourUnits)

    override fun extract(tokens: List<Token>, now: ZonedDateTime, blocked: BooleanArray): Extraction =
        pipeline.extract(tokens, now, blocked)
}
