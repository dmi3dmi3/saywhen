package com.dmi3dmi3.saywhen.parser.fr

import com.dmi3dmi3.saywhen.parser.ClockText
import com.dmi3dmi3.saywhen.parser.DurationCandidate
import com.dmi3dmi3.saywhen.parser.DurationRule
import com.dmi3dmi3.saywhen.parser.DurationScanner
import com.dmi3dmi3.saywhen.parser.GluedDurations
import com.dmi3dmi3.saywhen.parser.Token
import java.time.Duration

internal object FrDurationRules : DurationRule {

    private val hourWords = FrWords.hourWords
    private val halves = setOf("demie", "demi")

    private val hourMinutes = Regex("""(\d{1,2})h(\d{2})(?:min|mn)?""")

    private val glue = GluedDurations(FrWords.hourUnits, FrWords.minuteUnits, FrWords.minuteWords)

    private val scanner = DurationScanner(
        markers = setOf("pendant", "pour", "durant"),
        hourWords = hourWords,
        minuteWords = FrWords.minuteWords,
        cardinals = FrWords.cardinals,
        tailConjunctions = setOf("et"),
        glue = glue,
        idiomAt = ::idiomAt,
        bareGlue = false,
    )

    override fun find(tokens: List<Token>, used: BooleanArray): DurationCandidate? =
        scanner.find(tokens, used) ?: adjacentGlued(tokens, used)

    private fun idiomAt(tokens: List<Token>, i: Int, used: BooleanArray): DurationCandidate? {
        val next = tokens[i + 1].lower
        hourMinutes.matchEntire(next)?.let { m -> return durationOf(m)?.let { DurationCandidate(it, i..i + 1) } }
        if (next == "une" && tokens.getOrNull(i + 2)?.lower == "demi-heure" && used.getOrNull(i + 2) == false) {
            return DurationCandidate(Duration.ofMinutes(30), i..i + 2)
        }
        if ((next == "un" || next == "trois") &&
            tokens.getOrNull(i + 2)?.lower in setOf("quart", "quarts") &&
            tokens.getOrNull(i + 3)?.lower == "d" && tokens.getOrNull(i + 4)?.lower in hourWords &&
            (i + 2..i + 4).all { used.getOrNull(it) == false }
        ) {
            return DurationCandidate(Duration.ofMinutes(if (next == "un") 15 else 45), i..i + 4)
        }
        val n = next.toLongOrNull() ?: FrWords.cardinals[next]?.toLong() ?: return null
        if (n !in 1..23) return null
        if (tokens.getOrNull(i + 2)?.lower !in hourWords || used.getOrNull(i + 2) != false) return null
        if (tokens.getOrNull(i + 3)?.lower == "et" && tokens.getOrNull(i + 4)?.lower in halves &&
            (i + 3..i + 4).all { used.getOrNull(it) == false }
        ) {
            return DurationCandidate(Duration.ofHours(n).plusMinutes(30), i..i + 4)
        }
        val mm = ClockText.pairMinutes(tokens.getOrNull(i + 3)?.lower)?.takeIf { it > 0 } ?: return null
        if (used.getOrNull(i + 3) != false) return null
        var end = i + 3
        if (tokens.getOrNull(end + 1)?.lower in FrWords.minuteWords && used.getOrNull(end + 1) == false) end++
        return DurationCandidate(Duration.ofHours(n).plusMinutes(mm.toLong()), i..end)
    }

    private fun adjacentGlued(tokens: List<Token>, used: BooleanArray): DurationCandidate? {
        for (i in 1 until tokens.size) {
            if (used[i] || !used[i - 1]) continue
            val t = tokens[i].lower
            hourMinutes.matchEntire(t)?.let { m -> durationOf(m)?.let { return DurationCandidate(it, i..i) } }
            glue.gluedToken(t)?.let { (base, hours) ->
                var d = base
                var end = i
                if (hours) glue.minutesTail(tokens, used, i + 1)?.let { (mins, last) -> d = d.plusMinutes(mins); end = last }
                return DurationCandidate(d, i..end)
            }
        }
        return null
    }

    private fun durationOf(m: MatchResult): Duration? {
        val h = m.groupValues[1].toLong()
        val mm = m.groupValues[2].toLong()
        return if (h in 0..23 && mm in 0..59 && (h > 0 || mm > 0)) Duration.ofHours(h).plusMinutes(mm) else null
    }
}
