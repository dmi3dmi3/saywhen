package com.dmi3dmi3.saywhen.parser

import com.dmi3dmi3.saywhen.parser.de.GermanTranslator
import com.dmi3dmi3.saywhen.parser.en.EnglishTranslator
import com.dmi3dmi3.saywhen.parser.es.SpanishTranslator
import com.dmi3dmi3.saywhen.parser.it.ItalianTranslator
import com.dmi3dmi3.saywhen.parser.ru.RussianTranslator
import java.time.ZonedDateTime

/**
 * Мультиязычный вход: включённые трансляторы разбирают текст, [Arbitration]
 * выбирает кандидатов, сборка одна — пользователь пишет на любом из
 * включённых языков (и смешивает их в одной фразе) без переключателя.
 * Порядок списка — тай-брейк арбитража; с задачи 30 он локале-зависим
 * (локальный язык первым — меньше кросс-языковых ложных срабатываний),
 * набор и порядок собирает app-слой из настроек. Пин задачи 16
 * «фиксированный порядок» пересмотрен осознанно.
 */
class MultilingualEventParser internal constructor(
    private val translators: List<Translator>,
    private val activityWindow: IntRange = DEFAULT_ACTIVITY_WINDOW,
) : EventParser {

    companion object {
        /** Канонический порядок кодов — он же дефолтный приоритет. */
        val LANGUAGES: List<String> = listOf("ru", "en", "it", "es", "de")

        /** Окно активности по умолчанию (30a): голый час тянется в 8:00–21:59. */
        val DEFAULT_ACTIVITY_WINDOW: IntRange = 8..21

        private val byCode = mapOf(
            "ru" to RussianTranslator, "en" to EnglishTranslator, "it" to ItalianTranslator,
            "es" to SpanishTranslator, "de" to GermanTranslator,
        )

        /**
         * По кодам [LANGUAGES] в порядке приоритета; неизвестные коды — игнор.
         * invoke, а не конструктор: JVM-сигнатура List<String> совпала бы
         * с внутренним List<Translator>.
         */
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
        // стоп-лист: заблокированный токен занят для правил, но остаётся в заголовке
        val blocked = BooleanArray(tokens.size) { i ->
            blockedRanges.any { it.first <= tokens[i].range.last && tokens[i].range.first <= it.last }
        }
        val extractions = translators.map { it.extract(tokens, now, blocked) }
        val merged = Arbitration.merge(extractions)
        // дефолтный заголовок — язык лидера по покрытию; пусто — первый в списке
        val leader = extractions.indices.maxByOrNull { Arbitration.coverage(extractions[it]) } ?: 0
        return EventAssembly.assemble(
            text, tokens, blocked, merged,
            translators.flatMapTo(mutableSetOf()) { it.orphanWords },
            translators[leader].defaultTitle, now, activityWindow,
        )
    }
}
