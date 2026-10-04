package com.dmi3dmi3.saywhen.parser.en

import com.dmi3dmi3.saywhen.parser.DigitOrdinal
import java.time.DayOfWeek
import java.time.temporal.ChronoUnit

internal object EnWords {

    val cardinals: Map<String, Int> = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "eleven" to 11, "twelve" to 12, "fifteen" to 15,
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
    )

    val tens: Set<Int> = setOf(20, 30, 40, 50)

    val ordinals: Map<String, Int> = mapOf(
        "first" to 1, "second" to 2, "third" to 3, "fourth" to 4, "fifth" to 5, "last" to -1,
    )

    val digitOrdinal = DigitOrdinal("st|nd|rd|th")

    val weekdays: Map<String, DayOfWeek> = mapOf(
        "monday" to DayOfWeek.MONDAY, "tuesday" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY, "thursday" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY, "saturday" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY,
        "mon" to DayOfWeek.MONDAY,
        "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY,
        "wed" to DayOfWeek.WEDNESDAY, "weds" to DayOfWeek.WEDNESDAY,
        "thu" to DayOfWeek.THURSDAY, "thur" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY,
        "fri" to DayOfWeek.FRIDAY,
        "sat" to DayOfWeek.SATURDAY,
        "sun" to DayOfWeek.SUNDAY,
    )

    val months: Map<String, Int> = mapOf(
        "january" to 1, "february" to 2, "march" to 3, "april" to 4,
        "may" to 5, "june" to 6, "july" to 7, "august" to 8,
        "september" to 9, "october" to 10, "november" to 11, "december" to 12,
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4,
        "jun" to 6, "jul" to 7, "aug" to 8,
        "sep" to 9, "sept" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
    )

    val calendarUnits: Map<String, ChronoUnit> = mapOf(
        "day" to ChronoUnit.DAYS, "days" to ChronoUnit.DAYS,
        "week" to ChronoUnit.WEEKS, "weeks" to ChronoUnit.WEEKS,
        "month" to ChronoUnit.MONTHS, "months" to ChronoUnit.MONTHS,
        "year" to ChronoUnit.YEARS, "years" to ChronoUnit.YEARS,
    )

    val hourWords = setOf("hour", "hours", "hr", "hrs", "h")
    val minuteWords = setOf("minute", "minutes", "min", "mins", "m")

    val hourUnits = setOf("h", "hr", "hrs")
    val minuteUnits = setOf("m", "min", "mins")
}
