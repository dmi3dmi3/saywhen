package com.dmi3dmi3.saywhen.parser.ru

import com.dmi3dmi3.saywhen.parser.AMOUNT
import com.dmi3dmi3.saywhen.parser.ReminderCandidate
import com.dmi3dmi3.saywhen.parser.ReminderRule
import com.dmi3dmi3.saywhen.parser.Token

internal object ReminderRules : ReminderRule {

    private val triggers = setOf("напомни", "напомните")
    private val hourWords = Words.hourWords
    private val minuteWords = Words.minuteWords

    override fun find(tokens: List<Token>, used: BooleanArray): ReminderCandidate? {
        var found: ReminderCandidate? = null
        for (i in tokens.indices) {
            if (used[i] || tokens[i].lower !in triggers) continue
            if (used.getOrNull(i + 1) != false || tokens[i + 1].lower != "за") continue
            if (used.getOrNull(i + 2) != false) continue
            val amount = tokens[i + 2].lower

            if (amount in hourWords) {
                found = ReminderCandidate(60, i..i + 2)
                continue
            }
            val n = amount.toIntOrNull()?.takeIf { it == 0 || it in AMOUNT } ?: continue
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
