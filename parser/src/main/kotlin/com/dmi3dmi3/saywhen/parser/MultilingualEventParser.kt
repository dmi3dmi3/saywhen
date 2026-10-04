package com.dmi3dmi3.saywhen.parser

import com.dmi3dmi3.saywhen.parser.de.GermanTranslator
import com.dmi3dmi3.saywhen.parser.en.EnglishTranslator
import com.dmi3dmi3.saywhen.parser.es.SpanishTranslator
import com.dmi3dmi3.saywhen.parser.fr.FrenchTranslator
import com.dmi3dmi3.saywhen.parser.it.ItalianTranslator
import com.dmi3dmi3.saywhen.parser.ru.RussianTranslator
import java.time.ZonedDateTime

class MultilingualEventParser internal constructor(
    private val translators: List<Translator>,
    private val activityWindow: IntRange = DEFAULT_ACTIVITY_WINDOW,
) : EventParser {

    companion object {
        val LANGUAGES: List<String> = listOf("ru", "en", "it", "es", "de", "fr")

        val DEFAULT_ACTIVITY_WINDOW: IntRange = 8..21

        private val byCode = mapOf(
            "ru" to RussianTranslator, "en" to EnglishTranslator, "it" to ItalianTranslator,
            "es" to SpanishTranslator, "de" to GermanTranslator, "fr" to FrenchTranslator,
        )

        operator fun invoke(
            languages: List<String>,
            activityWindow: IntRange = DEFAULT_ACTIVITY_WINDOW,
        ): MultilingualEventParser =
            MultilingualEventParser(languages.distinct().mapNotNull { byCode[it] }, activityWindow)
    }

    constructor() : this(LANGUAGES.map { byCode.getValue(it) })

    init {
        require(translators.isNotEmpty()) { "нужен хотя бы один язык из $LANGUAGES" }
    }

    override fun parse(text: String, now: ZonedDateTime, blockedRanges: List<IntRange>): ParsedEvent {
        val tokens = Tokenizer.tokenize(text)
        val blocked = BooleanArray(tokens.size) { i ->
            blockedRanges.any { it.first <= tokens[i].range.last && tokens[i].range.first <= it.last }
        }
        val extractions = translators.map { it.extract(tokens, now, blocked) }
        val merged = Arbitration.merge(extractions)
        val leader = extractions.indices.maxByOrNull { Arbitration.coverage(extractions[it]) } ?: 0
        return EventAssembly.assemble(
            text, tokens, blocked, merged,
            translators.flatMapTo(mutableSetOf()) { it.orphanWords },
            translators[leader].defaultTitle, now, activityWindow,
        )
    }
}
