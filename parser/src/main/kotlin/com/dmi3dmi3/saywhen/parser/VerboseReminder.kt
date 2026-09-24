package com.dmi3dmi3.saywhen.parser

/**
 * Вербозное напоминание хвостового типа: «глагол [+слово] + N юнит + хвост» —
 * "remind me 30 minutes before", "avvisami un'ora prima", "erinnere mich
 * 10 Minuten vorher". Общая логика в ядре, словари — языков (ru — свой
 * префиксный шаблон «напомни за N», см. ru/ReminderRules). Хвост обязателен —
 * глагол без него остаётся заголовком; несколько форм — последняя побеждает.
 */
internal class VerboseReminder(
    private val triggers: Set<String>,
    private val follower: String? = null,       // "me"/"mich" после глагола
    private val followerRequired: Boolean = false,
    private val ones: Set<String>,              // артикль безчислового часа: an/a, un, una, eine
    private val minuteWords: Set<String>,
    private val hourWords: Set<String>,
    private val tails: Set<String>,             // before / prima / antes / vorher, davor
) {
    fun find(tokens: List<Token>, used: BooleanArray): ReminderCandidate? {
        var found: ReminderCandidate? = null
        for (i in tokens.indices) {
            if (used[i] || tokens[i].lower !in triggers) continue
            var j = i + 1
            if (follower != null && used.getOrNull(j) == false && tokens[j].lower == follower) j++
            else if (followerRequired) continue
            if (used.getOrNull(j) != false) continue

            // количество: «an hour» / «un'ora» / «eine stunde» — или N + юнит
            val minutes: Int
            if (tokens[j].lower in ones) {
                if (used.getOrNull(j + 1) != false || tokens[j + 1].lower !in hourWords) continue
                minutes = 60
            } else {
                val n = tokens[j].lower.toIntOrNull()?.takeIf { it in 0..999 } ?: continue
                if (used.getOrNull(j + 1) != false) continue
                minutes = when (tokens[j + 1].lower) {
                    in hourWords -> n * 60
                    in minuteWords -> n
                    else -> continue
                }
            }
            j += 2
            if (used.getOrNull(j) != false || tokens[j].lower !in tails) continue
            found = ReminderCandidate(minutes, i..j)
        }
        return found
    }
}
