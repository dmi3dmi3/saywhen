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
/**
 * Правило окна одной функцией: выбранный час 0..23 для голого часа [hour]
 * при окне активности [window]. Публична ради живых примеров в настройках
 * (задача 30a) — UI не держит копию правила.
 */
fun resolveTwelveHour(hour: Int, window: IntRange): Int {
    val second = if (hour == 12) 0 else hour + 12  // пара 12 — наступающая полночь
    return when {
        hour in window -> hour
        second in window -> second
        else -> hour
    }
}

internal object TwelveHourClock {

    /** Кандидат в окне; оба или ни один — буквальный (ранний). Окно —
     *  настройка (30a), дефолт 8:00–21:59: часы вне просят явности. */
    fun resolve(time: LocalTime, date: LocalDate, zone: ZoneId, window: IntRange): ZonedDateTime {
        val (first, second) = candidates(time, date, zone)
        return if (resolveTwelveHour(time.hour, window) == first.hour) first else second
    }

    private fun candidates(time: LocalTime, date: LocalDate, zone: ZoneId): Pair<ZonedDateTime, ZonedDateTime> {
        val first = date.atTime(time).atZone(zone)
        val second =
            if (time.hour == 12) date.plusDays(1).atTime(LocalTime.of(0, time.minute)).atZone(zone)
            else date.atTime(time.plusHours(12)).atZone(zone)
        return first to second
    }
}
