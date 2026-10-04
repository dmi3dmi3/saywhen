package com.dmi3dmi3.saywhen.parser.es

import com.dmi3dmi3.saywhen.parser.Extraction
import com.dmi3dmi3.saywhen.parser.Pipeline
import com.dmi3dmi3.saywhen.parser.Token
import com.dmi3dmi3.saywhen.parser.Translator
import java.time.ZonedDateTime

internal object SpanishTranslator : Translator {

    override val orphanWords =
        setOf("a", "al", "el", "la", "las", "los", "de", "del", "en", "por", "para", "durante", "hasta", "dentro")
    override val defaultTitle = "Evento"

    private val pipeline =
        Pipeline(EsRecurrenceRules, EsReminderRules, EsDateRules, EsTimeRules, EsDurationRules, EsWords.hourUnits)

    override fun extract(tokens: List<Token>, now: ZonedDateTime, blocked: BooleanArray): Extraction =
        pipeline.extract(tokens, now, blocked)
}
