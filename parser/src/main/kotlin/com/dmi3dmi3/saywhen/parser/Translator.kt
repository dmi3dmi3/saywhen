package com.dmi3dmi3.saywhen.parser

import java.time.ZonedDateTime

/**
 * Транслятор языка: словари и правила распознавания. Язык не выходит за его
 * пределы — на выходе только смысловые кандидаты (IR из [Candidates.kt]).
 * Внутренний приоритет правил (повтор → дата → время → длительность,
 * локальная used-маска) — тоже дело транслятора: это приоритет форм языка.
 */
internal interface Translator {
    /** Предлоги-сироты: незанятый предлог прямо перед клеймом уходит с ним. */
    val orphanWords: Set<String>

    /** Заголовок пустого остатка («Событие» / "Event"). */
    val defaultTitle: String

    /** [blocked] — стоп-лист пользователя; заблокированные токены не распознавать. */
    fun extract(tokens: List<Token>, now: ZonedDateTime, blocked: BooleanArray): Extraction
}

/** IR-выход транслятора: по одному победившему кандидату на поле. */
internal data class Extraction(
    val recurrence: RecurrenceCandidate? = null,
    val date: DateCandidate? = null,
    val time: TimeCandidate? = null,
    val duration: DurationCandidate? = null,
    val reminder: ReminderCandidate? = null,
)
