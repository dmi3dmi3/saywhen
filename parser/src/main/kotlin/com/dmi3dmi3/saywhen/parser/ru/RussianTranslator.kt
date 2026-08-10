package com.dmi3dmi3.saywhen.parser.ru

import com.dmi3dmi3.saywhen.parser.Extraction
import com.dmi3dmi3.saywhen.parser.Token
import com.dmi3dmi3.saywhen.parser.Translator
import java.time.ZonedDateTime

/**
 * Русский транслятор: словари и правила. Приоритет правил — повтор раньше
 * дат («каждый ВТОРНИК» содержит день недели, который иначе украли бы датовые
 * правила), длительность последней (осмысленна только при найденном времени);
 * приоритет держит локальная used-маска. Семантика события живёт в сборке.
 */
internal object RussianTranslator : Translator {

    override val orphanWords = setOf("в", "во", "с", "на", "до", "по")
    override val defaultTitle = "Событие"

    override fun extract(tokens: List<Token>, now: ZonedDateTime, blocked: BooleanArray): Extraction {
        val used = blocked.copyOf()
        fun take(range: IntRange) {
            for (j in range) used[j] = true
        }

        val rec = RecurrenceRules.find(tokens, now.toLocalDate(), used)
        rec?.let { r ->
            take(r.tokens)
            r.extraTokens.forEach(::take)
        }

        // дата: первый кандидат побеждает, остальные остаются текстом
        val date = DateRules.findAll(tokens, now, used).firstOrNull()
        date?.let { take(it.tokens) }

        val time = TimeRules.find(tokens, used)
            ?: TimeRules.bareHourAfterClaim(tokens, used)  // «завтра 11 планёрка»
        time?.let { take(it.tokens) }

        // интервал «с … до …» длительность уже принёс
        val duration =
            if (time != null && time.duration == null) DurationRules.find(tokens, used) else null

        return Extraction(recurrence = rec, date = date, time = time, duration = duration)
    }
}
