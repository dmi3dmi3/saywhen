package com.dmi3dmi3.saywhen.calendar

import android.accounts.Account
import android.content.ContentResolver
import android.os.Bundle
import android.provider.CalendarContract

/** Есть ли у календаря серверная сторона: локальным и безаккаунтным синк не нужен. */
internal fun isSyncable(calendar: CalendarInfo): Boolean =
    calendar.accountName.isNotEmpty() &&
        calendar.accountType.isNotEmpty() &&
        calendar.accountType != CalendarContract.ACCOUNT_TYPE_LOCAL

/**
 * Пинок sync-адаптеру после вставки — иначе событие уезжает на сервер, только
 * когда адаптер сам проснётся (задача 20). Частые запросы троттлит система.
 */
fun requestCalendarSync(calendar: CalendarInfo) {
    if (!isSyncable(calendar)) return
    val extras = Bundle().apply {
        putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
        putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
    }
    ContentResolver.requestSync(
        Account(calendar.accountName, calendar.accountType),
        CalendarContract.AUTHORITY,
        extras,
    )
}
