package com.dmi3dmi3.saywhen.parser.de

import com.dmi3dmi3.saywhen.parser.DurationCandidate
import com.dmi3dmi3.saywhen.parser.DurationRule
import com.dmi3dmi3.saywhen.parser.DurationScanner
import com.dmi3dmi3.saywhen.parser.GluedDurations
import com.dmi3dmi3.saywhen.parser.Token
import java.time.Duration

internal object DeDurationRules : DurationRule {

    private val hourWords = DeWords.hourWords
    private val ones = setOf("eine", "einer")

    private val scanner = DurationScanner(
        markers = setOf("für", "fuer"),
        hourWords = hourWords,
        minuteWords = DeWords.minuteWords,
        cardinals = DeWords.cardinals,
        tailConjunctions = setOf("und"),
        glue = GluedDurations(DeWords.hourUnits, DeWords.minuteUnits, DeWords.minuteWords),
        idiomAt = ::idiomAt,
    )

    override fun find(tokens: List<Token>, used: BooleanArray): DurationCandidate? = scanner.find(tokens, used)

    private fun idiomAt(tokens: List<Token>, i: Int, used: BooleanArray): DurationCandidate? {
        val next = tokens[i + 1].lower
        if (next in ones && tokens.getOrNull(i + 2)?.lower == "halbe" &&
            tokens.getOrNull(i + 3)?.lower == "stunde" &&
            (i + 2..i + 3).all { used.getOrNull(it) == false }
        ) {
            return DurationCandidate(Duration.ofMinutes(30), i..i + 3)
        }
        if (next in setOf("anderthalb", "eineinhalb") && used.getOrNull(i + 2) == false &&
            tokens.getOrNull(i + 2)?.lower in hourWords
        ) {
            return DurationCandidate(Duration.ofMinutes(90), i..i + 2)
        }
        if (next in ones && used.getOrNull(i + 2) == false && tokens.getOrNull(i + 2)?.lower in hourWords) {
            return DurationCandidate(Duration.ofHours(1), i..i + 2)
        }
        return null
    }
}
