package com.dmi3dmi3.saywhen.parser.it

import com.dmi3dmi3.saywhen.parser.DurationCandidate
import com.dmi3dmi3.saywhen.parser.DurationRule
import com.dmi3dmi3.saywhen.parser.DurationScanner
import com.dmi3dmi3.saywhen.parser.GluedDurations
import com.dmi3dmi3.saywhen.parser.Token
import java.time.Duration

internal object ItDurationRules : DurationRule {

    private val hourWords = ItWords.hourWords

    private val scanner = DurationScanner(
        markers = setOf("per"),
        hourWords = hourWords,
        minuteWords = ItWords.minuteWords,
        cardinals = ItWords.cardinals,
        tailConjunctions = setOf("e"),
        glue = GluedDurations(ItWords.hourUnits, ItWords.minuteUnits, ItWords.minuteWords),
        idiomAt = ::idiomAt,
    )

    override fun find(tokens: List<Token>, used: BooleanArray): DurationCandidate? = scanner.find(tokens, used)

    private fun idiomAt(tokens: List<Token>, i: Int, used: BooleanArray): DurationCandidate? {
        val next = tokens[i + 1].lower
        if (next == "mezz" && used.getOrNull(i + 2) == false && tokens.getOrNull(i + 2)?.lower == "ora") {
            return DurationCandidate(Duration.ofMinutes(30), i..i + 2)
        }
        if (next == "un" && tokens.getOrNull(i + 2)?.lower == "quarto" &&
            tokens.getOrNull(i + 3)?.lower == "d" && tokens.getOrNull(i + 4)?.lower == "ora" &&
            (i + 2..i + 4).all { used.getOrNull(it) == false }
        ) {
            return DurationCandidate(Duration.ofMinutes(15), i..i + 4)
        }
        if ((next == "un" || next == "una") && used.getOrNull(i + 2) == false &&
            tokens.getOrNull(i + 2)?.lower in hourWords
        ) {
            if (used.getOrNull(i + 3) == false && used.getOrNull(i + 4) == false &&
                tokens.getOrNull(i + 3)?.lower == "e" &&
                tokens.getOrNull(i + 4)?.lower in setOf("mezza", "mezzo")
            ) {
                return DurationCandidate(Duration.ofMinutes(90), i..i + 4)
            }
            return DurationCandidate(Duration.ofHours(1), i..i + 2)
        }
        return null
    }
}
