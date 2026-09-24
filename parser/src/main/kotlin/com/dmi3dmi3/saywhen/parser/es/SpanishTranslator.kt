package com.dmi3dmi3.saywhen.parser.es

import com.dmi3dmi3.saywhen.parser.CompactReminder
import com.dmi3dmi3.saywhen.parser.Extraction
import com.dmi3dmi3.saywhen.parser.Token
import com.dmi3dmi3.saywhen.parser.Translator
import java.time.ZonedDateTime

/**
 * Испанский транслятор (задача 27): словари и правила по es-колонкам
 * матриц. Приоритет правил — как у соседей: повтор раньше дат, длительность
 * последней; локальная used-маска.
 */
internal object SpanishTranslator : Translator {

    override val orphanWords =
        setOf("a", "al", "el", "la", "las", "los", "de", "del", "en", "por", "para", "durante", "hasta", "dentro")
    override val defaultTitle = "Evento"

    override fun extract(tokens: List<Token>, now: ZonedDateTime, blocked: BooleanArray): Extraction {
        val used = blocked.copyOf()
        fun take(range: IntRange) {
            for (j in range) used[j] = true
        }

        val rec = EsRecurrenceRules.find(tokens, now.toLocalDate(), used)
        rec?.let { r ->
            take(r.tokens)
            r.extraTokens.forEach(::take)
        }

        // напоминание — раньше дат/времени (см. RussianTranslator)
        val reminder = listOfNotNull(
            CompactReminder.find(tokens, used, setOf("h")),
            EsReminderRules.find(tokens, used),
        ).maxByOrNull { it.tokens.last }
        reminder?.let { take(it.tokens) }

        val date = EsDateRules.findAll(tokens, now, used).firstOrNull()
        date?.let { take(it.tokens) }

        val time = EsTimeRules.find(tokens, used)
            ?: EsTimeRules.offsetTime(tokens, used, now)     // «en 2 horas»
            ?: EsTimeRules.bareHourAfterClaim(tokens, used)  // «mañana a 11»
        time?.let { take(it.tokens) }

        // интервал «de las … a las …» длительность уже принёс
        val duration =
            if (time != null && time.duration == null) EsDurationRules.find(tokens, used) else null

        return Extraction(recurrence = rec, date = date, time = time, duration = duration, reminder = reminder)
    }
}
