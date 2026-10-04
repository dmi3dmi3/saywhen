package com.dmi3dmi3.saywhen.parser.ru

import com.dmi3dmi3.saywhen.parser.DigitOrdinal
import java.time.DayOfWeek
import java.time.temporal.ChronoUnit

internal object Words {

    val cardinals: Map<String, Int> = mapOf(
        "два" to 2, "две" to 2, "три" to 3, "четыре" to 4, "пять" to 5,
        "шесть" to 6, "семь" to 7, "восемь" to 8, "девять" to 9, "десять" to 10,
        "одиннадцать" to 11, "двенадцать" to 12, "пятнадцать" to 15,
        "двадцать" to 20, "тридцать" to 30, "сорок" to 40, "пятьдесят" to 50,
    )

    val tens: Set<Int> = setOf(20, 30, 40, 50)

    val ordinals: Map<String, Int> = mapOf(
        "первый" to 1, "первую" to 1, "первое" to 1, "первого" to 1,
        "второй" to 2, "вторую" to 2, "второе" to 2, "второго" to 2,
        "третий" to 3, "третью" to 3, "третье" to 3, "третьего" to 3,
        "четвертый" to 4, "четвёртый" to 4, "четвертую" to 4, "четвёртую" to 4,
        "четвертое" to 4, "четвёртое" to 4, "четвертого" to 4, "четвёртого" to 4,
        "пятый" to 5, "пятую" to 5, "пятое" to 5, "пятого" to 5,
        "шестое" to 6, "шестого" to 6, "седьмое" to 7, "седьмого" to 7,
        "восьмое" to 8, "восьмого" to 8, "девятое" to 9, "девятого" to 9,
        "десятое" to 10, "десятого" to 10,
        "одиннадцатое" to 11, "одиннадцатого" to 11,
        "двенадцатое" to 12, "двенадцатого" to 12,
        "тринадцатое" to 13, "тринадцатого" to 13,
        "четырнадцатое" to 14, "четырнадцатого" to 14,
        "пятнадцатое" to 15, "пятнадцатого" to 15,
        "шестнадцатое" to 16, "шестнадцатого" to 16,
        "семнадцатое" to 17, "семнадцатого" to 17,
        "восемнадцатое" to 18, "восемнадцатого" to 18,
        "девятнадцатое" to 19, "девятнадцатого" to 19,
        "двадцатое" to 20, "двадцатого" to 20,
        "тридцатое" to 30, "тридцатого" to 30,
        "последний" to -1, "последнюю" to -1, "последнее" to -1,
    )

    val digitOrdinal = DigitOrdinal("""-(?:е|ое|го|ого)""")

    val weekdays: Map<String, DayOfWeek> = mapOf(
        "понедельник" to DayOfWeek.MONDAY,
        "вторник" to DayOfWeek.TUESDAY,
        "среда" to DayOfWeek.WEDNESDAY, "среду" to DayOfWeek.WEDNESDAY,
        "четверг" to DayOfWeek.THURSDAY,
        "пятница" to DayOfWeek.FRIDAY, "пятницу" to DayOfWeek.FRIDAY,
        "суббота" to DayOfWeek.SATURDAY, "субботу" to DayOfWeek.SATURDAY,
        "воскресенье" to DayOfWeek.SUNDAY,
        "пн" to DayOfWeek.MONDAY, "пон" to DayOfWeek.MONDAY,
        "вт" to DayOfWeek.TUESDAY,
        "ср" to DayOfWeek.WEDNESDAY,
        "чт" to DayOfWeek.THURSDAY,
        "пт" to DayOfWeek.FRIDAY,
        "сб" to DayOfWeek.SATURDAY, "суб" to DayOfWeek.SATURDAY,
        "вс" to DayOfWeek.SUNDAY, "вск" to DayOfWeek.SUNDAY, "воскр" to DayOfWeek.SUNDAY,
    )

    val months: Map<String, Int> = mapOf(
        "января" to 1, "февраля" to 2, "марта" to 3, "апреля" to 4,
        "мая" to 5, "июня" to 6, "июля" to 7, "августа" to 8,
        "сентября" to 9, "октября" to 10, "ноября" to 11, "декабря" to 12,
        "янв" to 1, "фев" to 2, "февр" to 2, "мар" to 3, "апр" to 4,
        "май" to 5, "июн" to 6, "июл" to 7, "авг" to 8,
        "сен" to 9, "сент" to 9, "окт" to 10, "ноя" to 11, "нояб" to 11,
        "дек" to 12,
    )

    val calendarUnits: Map<String, ChronoUnit> = mapOf(
        "день" to ChronoUnit.DAYS, "дня" to ChronoUnit.DAYS, "дней" to ChronoUnit.DAYS,
        "неделю" to ChronoUnit.WEEKS, "недели" to ChronoUnit.WEEKS, "недель" to ChronoUnit.WEEKS,
        "месяц" to ChronoUnit.MONTHS, "месяца" to ChronoUnit.MONTHS, "месяцев" to ChronoUnit.MONTHS,
        "год" to ChronoUnit.YEARS, "года" to ChronoUnit.YEARS, "лет" to ChronoUnit.YEARS,
    )

    val hourWords = setOf("час", "часа", "часов", "ч")
    val minuteWords = setOf("минуту", "минуты", "минут", "мин", "м")

    val hourUnits = setOf("ч")
    val minuteUnits = setOf("м", "мин")
}
