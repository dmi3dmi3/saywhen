package com.dmi3dmi3.saywhen.parser.it

import com.dmi3dmi3.saywhen.parser.DigitOrdinal
import java.time.DayOfWeek
import java.time.temporal.ChronoUnit

internal object ItWords {

    val cardinals: Map<String, Int> = mapOf(
        "un" to 1, "uno" to 1, "una" to 1, "due" to 2, "tre" to 3, "quattro" to 4,
        "cinque" to 5, "sei" to 6, "sette" to 7, "otto" to 8, "nove" to 9, "dieci" to 10,
        "undici" to 11, "dodici" to 12, "quindici" to 15,
        "venti" to 20, "trenta" to 30, "quaranta" to 40, "cinquanta" to 50,
    )

    val tens: Set<Int> = emptySet()

    val ordinals: Map<String, Int> = mapOf(
        "primo" to 1, "prima" to 1, "secondo" to 2, "seconda" to 2,
        "terzo" to 3, "terza" to 3, "quarto" to 4, "quarta" to 4,
        "quinto" to 5, "quinta" to 5, "ultimo" to -1, "ultima" to -1,
    )

    val digitOrdinal = DigitOrdinal("º")

    val weekdays: Map<String, DayOfWeek> = mapOf(
        "lunedì" to DayOfWeek.MONDAY, "lunedi" to DayOfWeek.MONDAY, "lun" to DayOfWeek.MONDAY,
        "martedì" to DayOfWeek.TUESDAY, "martedi" to DayOfWeek.TUESDAY, "mar" to DayOfWeek.TUESDAY,
        "mercoledì" to DayOfWeek.WEDNESDAY, "mercoledi" to DayOfWeek.WEDNESDAY,
        "mer" to DayOfWeek.WEDNESDAY,
        "giovedì" to DayOfWeek.THURSDAY, "giovedi" to DayOfWeek.THURSDAY, "gio" to DayOfWeek.THURSDAY,
        "venerdì" to DayOfWeek.FRIDAY, "venerdi" to DayOfWeek.FRIDAY, "ven" to DayOfWeek.FRIDAY,
        "sabato" to DayOfWeek.SATURDAY, "sab" to DayOfWeek.SATURDAY,
        "domenica" to DayOfWeek.SUNDAY, "dom" to DayOfWeek.SUNDAY,
    )

    val months: Map<String, Int> = mapOf(
        "gennaio" to 1, "febbraio" to 2, "marzo" to 3, "aprile" to 4,
        "maggio" to 5, "giugno" to 6, "luglio" to 7, "agosto" to 8,
        "settembre" to 9, "ottobre" to 10, "novembre" to 11, "dicembre" to 12,
        "gen" to 1, "feb" to 2, "apr" to 4, "mag" to 5, "giu" to 6,
        "lug" to 7, "ago" to 8, "set" to 9, "sett" to 9, "ott" to 10,
        "nov" to 11, "dic" to 12,
    )

    val calendarUnits: Map<String, ChronoUnit> = mapOf(
        "giorno" to ChronoUnit.DAYS, "giorni" to ChronoUnit.DAYS,
        "settimana" to ChronoUnit.WEEKS, "settimane" to ChronoUnit.WEEKS,
        "mese" to ChronoUnit.MONTHS, "mesi" to ChronoUnit.MONTHS,
        "anno" to ChronoUnit.YEARS, "anni" to ChronoUnit.YEARS,
    )

    val hourWords = setOf("ora", "ore", "h")
    val minuteWords = setOf("minuto", "minuti", "min", "m")

    val hourUnits = setOf("h")
    val minuteUnits = setOf("m", "min")
}
