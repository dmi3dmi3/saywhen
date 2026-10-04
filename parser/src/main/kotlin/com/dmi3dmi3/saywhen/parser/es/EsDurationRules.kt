package com.dmi3dmi3.saywhen.parser.es

import com.dmi3dmi3.saywhen.parser.DurationCandidate
import com.dmi3dmi3.saywhen.parser.DurationRule
import com.dmi3dmi3.saywhen.parser.DurationScanner
import com.dmi3dmi3.saywhen.parser.GluedDurations
import com.dmi3dmi3.saywhen.parser.Token
import java.time.Duration

internal object EsDurationRules : DurationRule {

    private val hourWords = EsWords.hourWords

    private val scanner = DurationScanner(
        markers = setOf("por", "durante"),
        hourWords = hourWords,
        minuteWords = EsWords.minuteWords,
        cardinals = EsWords.cardinals,
        tailConjunctions = setOf("y"),
        glue = GluedDurations(EsWords.hourUnits, EsWords.minuteUnits, EsWords.minuteWords),
        idiomAt = ::idiomAt,
    )

    override fun find(tokens: List<Token>, used: BooleanArray): DurationCandidate? = scanner.find(tokens, used)

    private fun idiomAt(tokens: List<Token>, i: Int, used: BooleanArray): DurationCandidate? {
        val next = tokens[i + 1].lower
        if (next == "media" && used.getOrNull(i + 2) == false && tokens.getOrNull(i + 2)?.lower == "hora") {
            return DurationCandidate(Duration.ofMinutes(30), i..i + 2)
        }
        if (next == "un" && tokens.getOrNull(i + 2)?.lower == "cuarto" &&
            tokens.getOrNull(i + 3)?.lower == "de" && tokens.getOrNull(i + 4)?.lower == "hora" &&
            (i + 2..i + 4).all { used.getOrNull(it) == false }
        ) {
            return DurationCandidate(Duration.ofMinutes(15), i..i + 4)
        }
        val hourAt = when {
            next in hourWords -> i + 1
            (next == "una" || next == "un") && tokens.getOrNull(i + 2)?.lower in hourWords -> i + 2
            else -> return null
        }
        if ((i + 1..hourAt).any { used.getOrNull(it) != false }) return null
        if (tokens.getOrNull(hourAt + 1)?.lower == "y" &&
            tokens.getOrNull(hourAt + 2)?.lower == "media" &&
            (hourAt + 1..hourAt + 2).all { used.getOrNull(it) == false }
        ) {
            return DurationCandidate(Duration.ofMinutes(90), i..hourAt + 2)
        }
        return DurationCandidate(Duration.ofHours(1), i..hourAt)
    }
}
