package com.dmi3dmi3.saywhen.parser

internal class VerboseReminder(
    private val triggers: Set<String>,
    private val follower: String? = null,
    private val followerRequired: Boolean = false,
    private val ones: Set<String>,
    private val minuteWords: Set<String>,
    private val hourWords: Set<String>,
    private val tails: Set<String>,
    private val glue: GluedDurations? = null,
) {
    fun find(tokens: List<Token>, used: BooleanArray): ReminderCandidate? {
        var found: ReminderCandidate? = null
        for (i in tokens.indices) {
            if (used[i] || tokens[i].lower !in triggers) continue
            var j = i + 1
            if (follower != null && used.getOrNull(j) == false && tokens[j].lower == follower) j++
            else if (followerRequired) continue
            if (used.getOrNull(j) != false) continue

            val minutes: Int
            val glued = glue?.gluedToken(tokens[j].lower)?.first
            if (glued != null) {
                minutes = glued.toMinutes().toInt()
                j += 1
            } else if (tokens[j].lower in ones) {
                if (used.getOrNull(j + 1) != false || tokens[j + 1].lower !in hourWords) continue
                minutes = 60
                j += 2
            } else {
                val n = tokens[j].lower.toIntOrNull()?.takeIf { it in 0..999 } ?: continue
                if (used.getOrNull(j + 1) != false) continue
                minutes = when (tokens[j + 1].lower) {
                    in hourWords -> n * 60
                    in minuteWords -> n
                    else -> continue
                }
                j += 2
            }
            if (used.getOrNull(j) != false || tokens[j].lower !in tails) continue
            found = ReminderCandidate(minutes, i..j)
        }
        return found
    }
}
