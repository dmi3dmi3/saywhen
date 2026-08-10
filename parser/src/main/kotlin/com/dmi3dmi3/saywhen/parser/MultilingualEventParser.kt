package com.dmi3dmi3.saywhen.parser

import com.dmi3dmi3.saywhen.parser.en.EnglishTranslator
import com.dmi3dmi3.saywhen.parser.ru.RussianTranslator
import java.time.ZonedDateTime

/**
 * Мультиязычный вход: все трансляторы разбирают текст, [Arbitration] выбирает
 * кандидатов, сборка одна — пользователь пишет на любом языке (и смешивает
 * языки в одной фразе) без переключателя. Порядок списка — тай-брейк
 * арбитража; фиксированный, не локале-зависимый: иначе одна фраза вела бы
 * себя по-разному на разных устройствах.
 */
class MultilingualEventParser internal constructor(
    private val translators: List<Translator>,
) : EventParser {

    constructor() : this(listOf(RussianTranslator, EnglishTranslator))

    init {
        require(translators.isNotEmpty())
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
            translators[leader].defaultTitle, now,
        )
    }
}
