package com.dmi3dmi3.saywhen.parser

import java.time.Duration
import java.time.ZonedDateTime

/** Кусок исходного текста, распознанный как поле события (подсветка в UI). */
data class TokenMatch(val range: IntRange, val field: Field) {
    enum class Field { DATE, TIME, DURATION, RECURRENCE, REMINDER }
}

/**
 * Результат разбора. Всегда валиден: парсер не умеет «не справиться» —
 * в худшем случае это all-day событие на сегодня со всем текстом в заголовке.
 */
data class ParsedEvent(
    val title: String,
    val start: ZonedDateTime,   // для allDay — полночь даты в зоне запроса
    val allDay: Boolean,
    val duration: Duration?,    // null — не распознана; дефолт подставляет app-слой
    val rrule: String?,         // RFC 5545, например "FREQ=WEEKLY;BYDAY=TU"
    val reminderMinutes: Int? = null,  // «!10», «напомни за 10 минут»; null — не распознано
    val matches: List<TokenMatch>,
)

/** Сменный процессор: язык/алгоритм меняются заменой реализации. */
interface EventParser {
    /**
     * [blockedRanges] — стоп-лист: символьные диапазоны, исключённые пользователем
     * из распознания (тап по подсветке). Их токены не берёт ни одно правило,
     * но в заголовке они остаются.
     */
    fun parse(text: String, now: ZonedDateTime, blockedRanges: List<IntRange> = emptyList()): ParsedEvent
}
