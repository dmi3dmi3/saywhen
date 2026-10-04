package com.dmi3dmi3.saywhen.parser

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
                else -> continue
            }
            found = ReminderCandidate(minutes, i..i)
        }
        return found
    }
}
