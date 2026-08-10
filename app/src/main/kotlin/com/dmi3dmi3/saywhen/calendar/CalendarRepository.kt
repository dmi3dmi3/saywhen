package com.dmi3dmi3.saywhen.calendar

import android.content.ContentResolver
import android.provider.CalendarContract.Calendars

data class CalendarInfo(
    val id: Long,
    val name: String,
    val accountName: String,
    val accountType: String,
    val ownerAccount: String,
    val isPrimary: Boolean,
    val color: Int = 0,  // ARGB из провайдера; 0 — не задан
) {
    val isGoogle: Boolean get() = accountType == "com.google"
}

/**
 * Каскад выбора: primary Google → любой primary → Google-календарь владельца →
 * любой календарь владельца → первый записываемый. IS_PRIMARY на части
 * устройств врёт или отсутствует — поэтому fallback'и (спека, «Запись в календарь»).
 */
internal fun pickDefaultCalendar(calendars: List<CalendarInfo>): CalendarInfo? =
    calendars.firstOrNull { it.isPrimary && it.isGoogle }
        ?: calendars.firstOrNull { it.isPrimary }
        ?: calendars.firstOrNull { it.ownerAccount == it.accountName && it.isGoogle }
        ?: calendars.firstOrNull { it.ownerAccount == it.accountName }
        ?: calendars.firstOrNull()

class CalendarRepository(private val resolver: ContentResolver) {

    /** Календари, в которые можно писать (уровень доступа от CONTRIBUTOR). */
    fun writableCalendars(): List<CalendarInfo> {
        val projection = arrayOf(
            Calendars._ID,
            Calendars.CALENDAR_DISPLAY_NAME,
            Calendars.ACCOUNT_NAME,
            Calendars.ACCOUNT_TYPE,
            Calendars.OWNER_ACCOUNT,
            Calendars.IS_PRIMARY,
            Calendars.CALENDAR_ACCESS_LEVEL,
            Calendars.CALENDAR_COLOR,
        )
        val result = mutableListOf<CalendarInfo>()
        resolver.query(Calendars.CONTENT_URI, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val access = c.getInt(6)
                if (access < Calendars.CAL_ACCESS_CONTRIBUTOR) continue
                result += CalendarInfo(
                    id = c.getLong(0),
                    name = c.getString(1) ?: "",
                    accountName = c.getString(2) ?: "",
                    accountType = c.getString(3) ?: "",
                    ownerAccount = c.getString(4) ?: "",
                    isPrimary = c.getInt(5) == 1,
                    color = if (c.isNull(7)) 0 else c.getInt(7),
                )
            }
        }
        return result
    }

    fun defaultCalendar(): CalendarInfo? = pickDefaultCalendar(writableCalendars())
}
