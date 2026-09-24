package com.dmi3dmi3.saywhen.parser.it

import com.dmi3dmi3.saywhen.parser.CompactReminder
import com.dmi3dmi3.saywhen.parser.Extraction
import com.dmi3dmi3.saywhen.parser.Token
import com.dmi3dmi3.saywhen.parser.Translator
import java.time.ZonedDateTime

/**
 * Итальянский транслятор (задача 26): словари и правила по it-колонкам
 * матриц. Приоритет правил — как у соседей: повтор раньше дат, длительность
 * последней; локальная used-маска.
 */
internal object ItalianTranslator : Translator {

    override val orphanWords =
        setOf("a", "al", "alle", "il", "l", "le", "di", "per", "dal", "dalle", "tra", "fra", "fino", "entro", "nei")
    override val defaultTitle = "Evento"

    override fun extract(tokens: List<Token>, now: ZonedDateTime, blocked: BooleanArray): Extraction {
        val used = blocked.copyOf()
        fun take(range: IntRange) {
            for (j in range) used[j] = true
        }

        val rec = ItRecurrenceRules.find(tokens, now.toLocalDate(), used)
        rec?.let { r ->
            take(r.tokens)
            r.extraTokens.forEach(::take)
        }

        // напоминание — раньше дат/времени (см. RussianTranslator)
        val reminder = listOfNotNull(
            CompactReminder.find(tokens, used, setOf("h")),
            ItReminderRules.find(tokens, used),
        ).maxByOrNull { it.tokens.last }
        reminder?.let { take(it.tokens) }

        val date = ItDateRules.findAll(tokens, now, used).firstOrNull()
        date?.let { take(it.tokens) }

        val time = ItTimeRules.find(tokens, used)
            ?: ItTimeRules.offsetTime(tokens, used, now)     // «tra 2 ore»
            ?: ItTimeRules.bareHourAfterClaim(tokens, used)  // «domani 11 …»
        time?.let { take(it.tokens) }

        // интервал «dalle … alle …» длительность уже принёс
        val duration =
            if (time != null && time.duration == null) ItDurationRules.find(tokens, used) else null

        return Extraction(recurrence = rec, date = date, time = time, duration = duration, reminder = reminder)
    }
}
