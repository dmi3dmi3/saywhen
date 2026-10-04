package com.dmi3dmi3.saywhen.parser.es

import java.time.DayOfWeek
import java.time.temporal.ChronoUnit

internal object EsWords {

    val cardinals: Map<String, Int> = mapOf(
        "un" to 1, "una" to 1, "uno" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4,
        "cinco" to 5, "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9, "diez" to 10,
        "once" to 11, "doce" to 12, "quince" to 15,
        "veinte" to 20, "treinta" to 30, "cuarenta" to 40, "cincuenta" to 50,
    )

    val tens: Set<Int> = emptySet()

    val ordinals: Map<String, Int> = mapOf(
        "primer" to 1, "primero" to 1, "primera" to 1,
        "segundo" to 2, "segunda" to 2, "tercer" to 3, "tercero" to 3, "tercera" to 3,
        "cuarto" to 4, "cuarta" to 4, "quinto" to 5, "quinta" to 5,
        "último" to -1, "ultimo" to -1, "última" to -1, "ultima" to -1,
    )

    val weekdays: Map<String, DayOfWeek> = mapOf(
        "lunes" to DayOfWeek.MONDAY, "lun" to DayOfWeek.MONDAY,
        "martes" to DayOfWeek.TUESDAY, "mar" to DayOfWeek.TUESDAY,
        "miércoles" to DayOfWeek.WEDNESDAY, "miercoles" to DayOfWeek.WEDNESDAY,
        "mié" to DayOfWeek.WEDNESDAY, "mie" to DayOfWeek.WEDNESDAY,
        "jueves" to DayOfWeek.THURSDAY, "jue" to DayOfWeek.THURSDAY,
        "viernes" to DayOfWeek.FRIDAY, "vie" to DayOfWeek.FRIDAY,
        "sábado" to DayOfWeek.SATURDAY, "sabado" to DayOfWeek.SATURDAY,
        "sáb" to DayOfWeek.SATURDAY, "sab" to DayOfWeek.SATURDAY,
        "domingo" to DayOfWeek.SUNDAY, "dom" to DayOfWeek.SUNDAY,
    )

    val months: Map<String, Int> = mapOf(
        "enero" to 1, "febrero" to 2, "marzo" to 3, "abril" to 4,
        "mayo" to 5, "junio" to 6, "julio" to 7, "agosto" to 8,
        "septiembre" to 9, "setiembre" to 9, "octubre" to 10,
        "noviembre" to 11, "diciembre" to 12,
        "ene" to 1, "feb" to 2, "abr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "ago" to 8, "sep" to 9, "sept" to 9, "oct" to 10,
        "nov" to 11, "dic" to 12,
    )

    val calendarUnits: Map<String, ChronoUnit> = mapOf(
        "día" to ChronoUnit.DAYS, "dia" to ChronoUnit.DAYS,
        "días" to ChronoUnit.DAYS, "dias" to ChronoUnit.DAYS,
        "semana" to ChronoUnit.WEEKS, "semanas" to ChronoUnit.WEEKS,
        "mes" to ChronoUnit.MONTHS, "meses" to ChronoUnit.MONTHS,
        "año" to ChronoUnit.YEARS, "ano" to ChronoUnit.YEARS,
        "años" to ChronoUnit.YEARS, "anos" to ChronoUnit.YEARS,
    )

    val hourWords = setOf("hora", "horas", "h")
    val minuteWords = setOf("minuto", "minutos", "min", "m")

    val hourUnits = setOf("h")
    val minuteUnits = setOf("m", "min")
}
