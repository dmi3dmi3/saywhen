package com.dmi3dmi3.saywhen.parser.en

import com.dmi3dmi3.saywhen.parser.DurationCandidate
import com.dmi3dmi3.saywhen.parser.DurationRule
import com.dmi3dmi3.saywhen.parser.DurationScanner
import com.dmi3dmi3.saywhen.parser.GluedDurations
import com.dmi3dmi3.saywhen.parser.Token
import java.time.Duration

internal object EnDurationRules : DurationRule {

    private val hourWords = EnWords.hourWords
    private val articles = setOf("an", "a")

    private val scanner = DurationScanner(
        markers = setOf("for"),
        hourWords = hourWords,
        minuteWords = EnWords.minuteWords,
        cardinals = EnWords.cardinals,
        tens = EnWords.tens,
        glue = GluedDurations(EnWords.hourUnits, EnWords.minuteUnits, EnWords.minuteWords),
        idiomAt = ::idiomAt,
    )

    override fun find(tokens: List<Token>, used: BooleanArray): DurationCandidate? = scanner.find(tokens, used)

    private fun idiomAt(tokens: List<Token>, i: Int, used: BooleanArray): DurationCandidate? {
        val next = tokens[i + 1].lower
        if (next in articles && used.getOrNull(i + 2) == false && tokens.getOrNull(i + 2)?.lower in hourWords) {
            if (used.getOrNull(i + 3) == false && used.getOrNull(i + 4) == false &&
                used.getOrNull(i + 5) == false &&
                tokens.getOrNull(i + 3)?.lower == "and" &&
                tokens.getOrNull(i + 4)?.lower in articles &&
                tokens.getOrNull(i + 5)?.lower == "half"
            ) {
                return DurationCandidate(Duration.ofMinutes(90), i..i + 5)
            }
            return DurationCandidate(Duration.ofHours(1), i..i + 2)
        }
        if (next == "half" && used.getOrNull(i + 2) == false && used.getOrNull(i + 3) == false &&
            tokens.getOrNull(i + 2)?.lower in articles &&
            tokens.getOrNull(i + 3)?.lower in hourWords
        ) {
            return DurationCandidate(Duration.ofMinutes(30), i..i + 3)
        }
        return null
    }
}
