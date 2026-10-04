package com.dmi3dmi3.saywhen.parser.ru

import com.dmi3dmi3.saywhen.parser.Extraction
import com.dmi3dmi3.saywhen.parser.Pipeline
import com.dmi3dmi3.saywhen.parser.Token
import com.dmi3dmi3.saywhen.parser.Translator
import java.time.ZonedDateTime

internal object RussianTranslator : Translator {

    override val orphanWords = setOf("в", "во", "с", "на", "до", "по")
    override val defaultTitle = "Событие"

    private val pipeline = Pipeline(RecurrenceRules, ReminderRules, DateRules, TimeRules, DurationRules, Words.hourUnits)

    override fun extract(tokens: List<Token>, now: ZonedDateTime, blocked: BooleanArray): Extraction =
        pipeline.extract(tokens, now, blocked)
}
