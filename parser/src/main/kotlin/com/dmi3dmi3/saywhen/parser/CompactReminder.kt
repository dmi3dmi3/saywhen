package com.dmi3dmi3.saywhen.parser

/**
 * Компактная форма напоминания «!N[час-юнит]» (задача 29) — универсальная,
 * реализация одна на все языки: «!10» — за 10 минут, «!0» — при событии,
 * «!1ч»/«!1h»/«!2std» — часы юнитом из словаря длительностей языка.
 * Несколько форм — последняя побеждает, ранние остаются текстом.
 */
internal object CompactReminder {

    private val pattern = Regex("""!(\d{1,3})(\p{L}*)""")

    fun find(tokens: List<Token>, used: BooleanArray, hourUnits: Set<String>): ReminderCandidate? {
        var found: ReminderCandidate? = null
        for (i in tokens.indices) {
            if (used[i]) continue
            val m = pattern.matchEntire(tokens[i].lower) ?: continue
            val (num, unit) = m.destructured
            val minutes = when {
                unit.isEmpty() -> num.toInt()
                unit in hourUnits -> num.toInt() * 60
                else -> continue  // неизвестный юнит («!10x») — не напоминание
            }
            found = ReminderCandidate(minutes, i..i)
        }
        return found
    }
}
