package com.dmi3dmi3.saywhen.parser.ru

import com.dmi3dmi3.saywhen.parser.ReminderCandidate
import com.dmi3dmi3.saywhen.parser.Token

internal object ReminderRules {

    private val triggers = setOf("напомни", "напомните")
    // юниты — как у DurationRules, включая короткие «ч»/«м» (фикс ревью волны 3)
    private val hourWords = setOf("час", "часа", "часов", "ч")
    private val minuteWords = setOf("минуту", "минуты", "минут", "мин", "м")

    /**
     * «напомни(те) за N [минут/часов]», «напомни за час»; юнит опционален —
     * голое число это минуты. Глагол без числа-хвоста не срабатывает —
     * «напомни маме» остаётся заголовком (guard-пин в ReminderTest).
     */
    fun find(tokens: List<Token>, used: BooleanArray): ReminderCandidate? {
        var found: ReminderCandidate? = null
        for (i in tokens.indices) {
            if (used[i] || tokens[i].lower !in triggers) continue
            if (used.getOrNull(i + 1) != false || tokens[i + 1].lower != "за") continue
            if (used.getOrNull(i + 2) != false) continue
            val amount = tokens[i + 2].lower

            // «напомни за час» — безчисловая идиома
            if (amount in hourWords) {
                found = ReminderCandidate(60, i..i + 2)
                continue
            }
            val n = amount.toIntOrNull()?.takeIf { it in 0..999 } ?: continue
            var minutes = n
            var end = i + 2
            if (used.getOrNull(i + 3) == false) when (tokens[i + 3].lower) {
                in hourWords -> { minutes = n * 60; end = i + 3 }
                in minuteWords -> end = i + 3
                else -> {}
            }
            found = ReminderCandidate(minutes, i..end)
        }
        return found
    }
}
