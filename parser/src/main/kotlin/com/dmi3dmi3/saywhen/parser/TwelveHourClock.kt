package com.dmi3dmi3.saywhen.parser

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Разрешение 12-часовой неоднозначности: час 1..12 без уточнения — пара
 * {h, h+12}; для 12 — {12:00, наступающая полночь}. Правило окна активности:
 * побеждает кандидат в дневном окне, оба или ни один — буквальный (ранний);
 * от момента набора выбор не зависит («не в прошлое» держит общий сдвиг в
 * сборке). Языконезависимо — ни одного слова конкретного языка: заготовка
 * общей сборки события (задача 16), транслятор лишь помечает кандидата
 * флагом `twelveHour`.
 */
internal object TwelveHourClock {

    // дневное окно; часы вне его (20:00–7:00) просят явности: «в 9 вечера», «22:30»
    private val window = 8..21

    /** Кандидат в окне; оба или ни один — буквальный (ранний). */
    fun resolve(time: LocalTime, date: LocalDate, zone: ZoneId): ZonedDateTime {
        val (first, second) = candidates(time, date, zone)
        return when {
            first.hour in window -> first
            second.hour in window -> second
            else -> first
        }
    }

    private fun candidates(time: LocalTime, date: LocalDate, zone: ZoneId): Pair<ZonedDateTime, ZonedDateTime> {
        val first = date.atTime(time).atZone(zone)
        val second =
            if (time.hour == 12) date.plusDays(1).atTime(LocalTime.of(0, time.minute)).atZone(zone)
            else date.atTime(time.plusHours(12)).atZone(zone)
        return first to second
    }
}
