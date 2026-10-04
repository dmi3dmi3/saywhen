package com.dmi3dmi3.saywhen.parser.de

import com.dmi3dmi3.saywhen.parser.DigitOrdinal
import java.time.DayOfWeek
import java.time.temporal.ChronoUnit

internal object DeWords {

    val cardinals: Map<String, Int> = mapOf(
        "ein" to 1, "eins" to 1, "eine" to 1, "einer" to 1, "einem" to 1,
        "zwei" to 2, "drei" to 3, "vier" to 4, "fünf" to 5, "fuenf" to 5,
        "sechs" to 6, "sieben" to 7, "acht" to 8, "neun" to 9, "zehn" to 10,
        "elf" to 11, "zwölf" to 12, "zwoelf" to 12, "fünfzehn" to 15, "fuenfzehn" to 15,
        "zwanzig" to 20, "dreißig" to 30, "dreissig" to 30, "vierzig" to 40,
        "fünfzig" to 50, "fuenfzig" to 50,
    )

    val tens: Set<Int> = emptySet()

    val ordinals: Map<String, Int> = buildMap {
        val stems = listOf(
            "erst" to 1, "zweit" to 2, "dritt" to 3, "viert" to 4, "fünft" to 5, "fuenft" to 5,
            "sechst" to 6, "siebt" to 7, "acht" to 8, "neunt" to 9, "zehnt" to 10,
            "elft" to 11, "zwölft" to 12, "zwoelft" to 12, "dreizehnt" to 13, "vierzehnt" to 14,
            "fünfzehnt" to 15, "fuenfzehnt" to 15, "sechzehnt" to 16, "siebzehnt" to 17,
            "achtzehnt" to 18, "neunzehnt" to 19, "zwanzigst" to 20,
            "dreißigst" to 30, "dreissigst" to 30,
            "letzt" to -1,
        )
        for ((stem, n) in stems) for (suffix in listOf("e", "en", "er", "es")) put(stem + suffix, n)
    }

    val digitOrdinal = DigitOrdinal("te|ten|ter|tes")

    val weekdays: Map<String, DayOfWeek> = mapOf(
        "montag" to DayOfWeek.MONDAY,
        "dienstag" to DayOfWeek.TUESDAY,
        "mittwoch" to DayOfWeek.WEDNESDAY,
        "donnerstag" to DayOfWeek.THURSDAY,
        "freitag" to DayOfWeek.FRIDAY,
        "samstag" to DayOfWeek.SATURDAY, "sonnabend" to DayOfWeek.SATURDAY,
        "sonntag" to DayOfWeek.SUNDAY,
    )

    val months: Map<String, Int> = mapOf(
        "januar" to 1, "februar" to 2, "märz" to 3, "maerz" to 3, "april" to 4,
        "mai" to 5, "juni" to 6, "juli" to 7, "august" to 8,
        "september" to 9, "oktober" to 10, "november" to 11, "dezember" to 12,
        "jan" to 1, "feb" to 2, "mär" to 3, "apr" to 4, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "sept" to 9, "okt" to 10,
        "nov" to 11, "dez" to 12,
    )

    val calendarUnits: Map<String, ChronoUnit> = mapOf(
        "tag" to ChronoUnit.DAYS, "tage" to ChronoUnit.DAYS, "tagen" to ChronoUnit.DAYS,
        "woche" to ChronoUnit.WEEKS, "wochen" to ChronoUnit.WEEKS,
        "monat" to ChronoUnit.MONTHS, "monate" to ChronoUnit.MONTHS, "monaten" to ChronoUnit.MONTHS,
        "jahr" to ChronoUnit.YEARS, "jahre" to ChronoUnit.YEARS, "jahren" to ChronoUnit.YEARS,
    )

    val hourWords = setOf("stunde", "stunden", "std", "h")
    val minuteWords = setOf("minute", "minuten", "min", "m")

    val hourUnits = setOf("h", "std")
    val minuteUnits = setOf("m", "min")
}
