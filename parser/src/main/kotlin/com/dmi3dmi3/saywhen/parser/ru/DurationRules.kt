package com.dmi3dmi3.saywhen.parser.ru

import com.dmi3dmi3.saywhen.parser.DurationCandidate
import com.dmi3dmi3.saywhen.parser.DurationRule
import com.dmi3dmi3.saywhen.parser.DurationScanner
import com.dmi3dmi3.saywhen.parser.GluedDurations
import com.dmi3dmi3.saywhen.parser.Token
import java.time.Duration

internal object DurationRules : DurationRule {

    private val hourWords = Words.hourWords

    private val scanner = DurationScanner(
        markers = setOf("на"),
        hourWords = hourWords,
        minuteWords = Words.minuteWords,
        cardinals = Words.cardinals,
        tens = Words.tens,
        glue = GluedDurations(Words.hourUnits, Words.minuteUnits, Words.minuteWords),
        idiomAt = ::idiomAt,
    )

    override fun find(tokens: List<Token>, used: BooleanArray): DurationCandidate? = scanner.find(tokens, used)

    private fun idiomAt(tokens: List<Token>, i: Int, used: BooleanArray): DurationCandidate? {
        val next = tokens[i + 1].lower
        if (next in hourWords) return DurationCandidate(Duration.ofHours(1), i..i + 1)
        if (next == "полчаса") return DurationCandidate(Duration.ofMinutes(30), i..i + 1)
        if (next == "полтора" && used.getOrNull(i + 2) == false &&
            tokens.getOrNull(i + 2)?.lower in hourWords
        ) {
            return DurationCandidate(Duration.ofMinutes(90), i..i + 2)
        }
        return null
    }
}
