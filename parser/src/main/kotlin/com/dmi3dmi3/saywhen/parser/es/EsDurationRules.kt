package com.dmi3dmi3.saywhen.parser.es

import com.dmi3dmi3.saywhen.parser.DurationCandidate
import com.dmi3dmi3.saywhen.parser.GluedDurations
import com.dmi3dmi3.saywhen.parser.Token
import com.dmi3dmi3.saywhen.parser.decimalNumber
import java.time.Duration
import kotlin.math.roundToLong

internal object EsDurationRules {

    private val markers = setOf("por", "durante")
    private val hourWords = setOf("hora", "horas", "h")
    private val minuteWords = setOf("minuto", "minutos", "m", "min")

    // склейки "2h" / "45m" / "2h45m" — общая логика в ядре, латинские единицы
    private val glue = GluedDurations(setOf("h"), setOf("m", "min"), minuteWords)

    private val wordNumbers = mapOf(
        "dos" to 2L, "tres" to 3L, "cuatro" to 4L, "cinco" to 5L, "seis" to 6L,
        "siete" to 7L, "ocho" to 8L, "nueve" to 9L, "diez" to 10L,
        "quince" to 15L, "veinte" to 20L, "treinta" to 30L, "cuarenta" to 40L,
        "cincuenta" to 50L,
    )

    fun find(tokens: List<Token>, used: BooleanArray): DurationCandidate? {
        for (i in tokens.indices) {
            if (used[i] || tokens[i].lower !in markers) continue
            if (used.getOrNull(i + 1) == true) continue
            val next = tokens.getOrNull(i + 1)?.lower ?: continue

            // «por media hora»
            if (next == "media" && used.getOrNull(i + 2) == false &&
                tokens.getOrNull(i + 2)?.lower == "hora"
            ) {
                return DurationCandidate(Duration.ofMinutes(30), i..i + 2)
            }

            // «por un cuarto de hora»
            if (next == "un" && tokens.getOrNull(i + 2)?.lower == "cuarto" &&
                tokens.getOrNull(i + 3)?.lower == "de" && tokens.getOrNull(i + 4)?.lower == "hora" &&
                (i + 2..i + 4).all { used.getOrNull(it) == false }
            ) {
                return DurationCandidate(Duration.ofMinutes(15), i..i + 4)
            }

            // «por hora y media» / «por una hora [y media]»
            run {
                val hourAt = when {
                    next in hourWords -> i + 1
                    (next == "una" || next == "un") && tokens.getOrNull(i + 2)?.lower in hourWords -> i + 2
                    else -> return@run
                }
                if ((i + 1..hourAt).any { used.getOrNull(it) != false }) return@run
                if (tokens.getOrNull(hourAt + 1)?.lower == "y" &&
                    tokens.getOrNull(hourAt + 2)?.lower == "media" &&
                    (hourAt + 1..hourAt + 2).all { used.getOrNull(it) == false }
                ) {
                    return DurationCandidate(Duration.ofMinutes(90), i..hourAt + 2)
                }
                return DurationCandidate(Duration.ofHours(1), i..hourAt)
            }

            // «por 2h» / «por 45m» / «por 2h45m»
            glue.gluedToken(next)?.let { (base, hours) ->
                var d = base
                var end = i + 1
                if (hours) glue.minutesTail(tokens, used, end + 1)?.let { (mins, last) ->
                    d = d.plusMinutes(mins); end = last
                }
                return DurationCandidate(d, i..end)
            }

            // «por 1,5 horas» — десятичные только у часов
            decimalNumber(next)?.let { v ->
                if (used.getOrNull(i + 2) == false && tokens.getOrNull(i + 2)?.lower in hourWords) {
                    return DurationCandidate(Duration.ofMinutes((v * 60).roundToLong()), i..i + 2)
                }
            }

            // «por N horas/minutos», число цифрами или словом
            val amount = next.toLongOrNull() ?: wordNumbers[next] ?: continue
            var j = i + 2
            if (amount !in 1..999) continue
            if (used.getOrNull(j) == true) continue
            var duration = when (tokens.getOrNull(j)?.lower) {
                in hourWords -> Duration.ofHours(amount)
                in minuteWords -> Duration.ofMinutes(amount)
                else -> continue
            }
            // хвост минут: «por 2 horas y 30 [minutos]» — «y» сам маркер хвоста
            if (tokens[j].lower in hourWords) {
                var k = j + 1
                var viaY = false
                if (tokens.getOrNull(k)?.lower == "y" && used.getOrNull(k) == false) {
                    k++; viaY = true
                }
                val tail = glue.minutesTail(tokens, used, k)
                if (tail != null) {
                    duration = duration.plusMinutes(tail.first); j = tail.second
                } else if (viaY && used.getOrNull(k) == false) {
                    tokens.getOrNull(k)?.lower?.toLongOrNull()?.takeIf { it in 1..59 }?.let {
                        duration = duration.plusMinutes(it); j = k
                    }
                }
            }
            return DurationCandidate(duration, i..j)
        }

        // голая склейка без маркера — «reunión a las 11 2h45m»
        for (i in tokens.indices) {
            if (used[i]) continue
            glue.gluedToken(tokens[i].lower)?.let { (base, hours) ->
                var d = base
                var end = i
                if (hours) glue.minutesTail(tokens, used, i + 1)?.let { (mins, last) ->
                    d = d.plusMinutes(mins); end = last
                }
                return DurationCandidate(d, i..end)
            }
        }
        return null
    }
}
