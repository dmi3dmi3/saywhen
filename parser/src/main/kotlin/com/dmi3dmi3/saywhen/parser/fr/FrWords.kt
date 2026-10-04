package com.dmi3dmi3.saywhen.parser.fr

import com.dmi3dmi3.saywhen.parser.DigitOrdinal
import java.time.DayOfWeek
import java.time.temporal.ChronoUnit

internal object FrWords {

    val cardinals: Map<String, Int> = buildMap {
        putAll(
            mapOf(
                "un" to 1, "une" to 1, "deux" to 2, "trois" to 3, "quatre" to 4, "cinq" to 5,
                "six" to 6, "sept" to 7, "huit" to 8, "neuf" to 9, "dix" to 10,
                "onze" to 11, "douze" to 12, "treize" to 13, "quatorze" to 14, "quinze" to 15,
                "seize" to 16, "dix-sept" to 17, "dix-huit" to 18, "dix-neuf" to 19,
            ),
        )
        val units = listOf(
            "deux" to 2, "trois" to 3, "quatre" to 4, "cinq" to 5,
            "six" to 6, "sept" to 7, "huit" to 8, "neuf" to 9,
        )
        for ((tenWord, ten) in listOf("vingt" to 20, "trente" to 30, "quarante" to 40, "cinquante" to 50)) {
            put(tenWord, ten)
            put("$tenWord-et-un", ten + 1)
            put("$tenWord-et-une", ten + 1)
            for ((unitWord, unit) in units) put("$tenWord-$unitWord", ten + unit)
        }
    }

    val tens: Set<Int> = emptySet()

    val ordinals: Map<String, Int> = mapOf(
        "premier" to 1, "première" to 1, "premiere" to 1,
        "deuxième" to 2, "deuxieme" to 2, "second" to 2, "seconde" to 2,
        "troisième" to 3, "troisieme" to 3, "quatrième" to 4, "quatrieme" to 4,
        "cinquième" to 5, "cinquieme" to 5,
        "dernier" to -1, "dernière" to -1, "derniere" to -1,
    )

    val digitOrdinal = DigitOrdinal("er|ère|ere|ème|eme|e")

    val weekdays: Map<String, DayOfWeek> = mapOf(
        "lundi" to DayOfWeek.MONDAY,
        "mardi" to DayOfWeek.TUESDAY,
        "mercredi" to DayOfWeek.WEDNESDAY,
        "jeudi" to DayOfWeek.THURSDAY,
        "vendredi" to DayOfWeek.FRIDAY,
        "samedi" to DayOfWeek.SATURDAY,
        "dimanche" to DayOfWeek.SUNDAY,
    )

    val months: Map<String, Int> = mapOf(
        "janvier" to 1, "février" to 2, "fevrier" to 2, "mars" to 3, "avril" to 4,
        "mai" to 5, "juin" to 6, "juillet" to 7, "août" to 8, "aout" to 8,
        "septembre" to 9, "octobre" to 10, "novembre" to 11, "décembre" to 12, "decembre" to 12,
        "janv" to 1, "jan" to 1, "févr" to 2, "fév" to 2, "fevr" to 2, "fev" to 2,
        "avr" to 4, "juil" to 7, "sept" to 9, "oct" to 10, "nov" to 11, "déc" to 12, "dec" to 12,
    )

    val calendarUnits: Map<String, ChronoUnit> = mapOf(
        "jour" to ChronoUnit.DAYS, "jours" to ChronoUnit.DAYS,
        "semaine" to ChronoUnit.WEEKS, "semaines" to ChronoUnit.WEEKS,
        "mois" to ChronoUnit.MONTHS,
        "an" to ChronoUnit.YEARS, "ans" to ChronoUnit.YEARS,
        "année" to ChronoUnit.YEARS, "années" to ChronoUnit.YEARS,
        "annee" to ChronoUnit.YEARS, "annees" to ChronoUnit.YEARS,
    )

    val hourWords = setOf("heure", "heures", "h")
    val minuteWords = setOf("minute", "minutes", "min", "mn")

    val hourUnits = setOf("h")
    val minuteUnits = setOf("min", "mn")
}
