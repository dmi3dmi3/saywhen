package com.dmi3dmi3.saywhen.parser.en

import com.dmi3dmi3.saywhen.parser.Extraction
import com.dmi3dmi3.saywhen.parser.Token
import com.dmi3dmi3.saywhen.parser.Translator
import java.time.ZonedDateTime

/**
 * Английский транслятор: словари и правила — зеркало русского там, где у
 * английского есть идиома (am/pm вместо «утра/вечера», half past вместо
 * «пол седьмого»). Приоритет правил тот же: повтор → дата → время →
 * длительность; семантика события — в общей сборке.
 */
internal object EnglishTranslator : Translator {

    override val orphanWords = setOf("at", "on", "in", "from", "until", "till")
    override val defaultTitle = "Event"

    override fun extract(tokens: List<Token>, now: ZonedDateTime, blocked: BooleanArray): Extraction {
        val used = blocked.copyOf()
        fun take(range: IntRange) {
            for (j in range) used[j] = true
        }

        val rec = EnRecurrenceRules.find(tokens, now.toLocalDate(), used)
        rec?.let { r ->
            take(r.tokens)
            r.extraTokens.forEach(::take)
        }

        val date = EnDateRules.findAll(tokens, now, used).firstOrNull()
        date?.let { take(it.tokens) }

        val time = EnTimeRules.find(tokens, used)
            ?: EnTimeRules.bareHourAfterClaim(tokens, used)  // "tomorrow 11 standup"
        time?.let { take(it.tokens) }

        // интервал "from … to …" длительность уже принёс
        val duration =
            if (time != null && time.duration == null) EnDurationRules.find(tokens, used) else null

        return Extraction(recurrence = rec, date = date, time = time, duration = duration)
    }
}
