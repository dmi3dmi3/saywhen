package com.dmi3dmi3.saywhen.parser.de

import com.dmi3dmi3.saywhen.parser.CompactReminder
import com.dmi3dmi3.saywhen.parser.Extraction
import com.dmi3dmi3.saywhen.parser.Token
import com.dmi3dmi3.saywhen.parser.Translator
import java.time.ZonedDateTime

/**
 * Немецкий транслятор (задача 27a): словари и правила по de-колонкам
 * матриц. Приоритет правил — как у соседей: повтор раньше дат, длительность
 * последней; локальная used-маска. Приблизительность (gegen/circa/…) не
 * храним — слова уходят сиротами.
 */
internal object GermanTranslator : Translator {

    override val orphanWords = setOf(
        "um", "am", "im", "an", "von", "vom", "bis", "für", "fuer", "in", "zu", "zum", "zur",
        "der", "die", "das", "den", "des", "gegen", "circa", "zirka", "ca", "ungefähr", "etwa", "so",
    )
    override val defaultTitle = "Termin"

    override fun extract(tokens: List<Token>, now: ZonedDateTime, blocked: BooleanArray): Extraction {
        val used = blocked.copyOf()
        fun take(range: IntRange) {
            for (j in range) used[j] = true
        }

        val rec = DeRecurrenceRules.find(tokens, now.toLocalDate(), used)
        rec?.let { r ->
            take(r.tokens)
            r.extraTokens.forEach(::take)
        }

        // напоминание — раньше дат/времени (см. RussianTranslator)
        val reminder = listOfNotNull(
            CompactReminder.find(tokens, used, setOf("h", "std")),
            DeReminderRules.find(tokens, used),
        ).maxByOrNull { it.tokens.last }
        reminder?.let { take(it.tokens) }

        val date = DeDateRules.findAll(tokens, now, used).firstOrNull()
        date?.let { take(it.tokens) }

        val time = DeTimeRules.find(tokens, used)
            ?: DeTimeRules.offsetTime(tokens, used, now)     // «in 2 stunden»
            ?: DeTimeRules.bareHourAfterClaim(tokens, used)  // «morgen 11 …»
        time?.let { take(it.tokens) }

        // интервал «von … bis …» длительность уже принёс
        val duration =
            if (time != null && time.duration == null) DeDurationRules.find(tokens, used) else null

        return Extraction(recurrence = rec, date = date, time = time, duration = duration, reminder = reminder)
    }
}
