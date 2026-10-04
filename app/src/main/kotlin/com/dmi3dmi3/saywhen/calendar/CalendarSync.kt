package com.dmi3dmi3.saywhen.calendar

import android.accounts.Account
import android.content.ContentResolver
import android.os.Bundle
import android.provider.CalendarContract

internal fun isSyncable(calendar: CalendarInfo): Boolean =
    calendar.accountName.isNotEmpty() &&
        calendar.accountType.isNotEmpty() &&
        calendar.accountType != CalendarContract.ACCOUNT_TYPE_LOCAL

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
