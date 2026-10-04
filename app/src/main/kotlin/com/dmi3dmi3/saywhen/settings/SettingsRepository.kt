package com.dmi3dmi3.saywhen.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Settings(
    val calendarId: Long? = null,
    val defaultDurationMinutes: Int = 60,
    val defaultReminderMinutes: Int? = null,
    val parserLanguages: Set<String>? = null,
    val parserOn: Set<String> = emptySet(),
    val parserOff: Set<String> = emptySet(),
    val activityStartHour: Int = 8,
    val activityEndHour: Int = 22,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val nodEnabled: Boolean = true,
    val hapticEnabled: Boolean = true,
)

enum class ThemeMode(val key: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark");

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.find { it.key == key } ?: SYSTEM
    }
}

internal fun durationLabel(minutes: Int, hourUnit: String, minuteUnit: String): String {
    val h = minutes / 60
    val m = minutes % 60
    return listOfNotNull(
        if (h > 0) "$h $hourUnit" else null,
        if (m > 0) "$m $minuteUnit" else null,
    ).joinToString(" ")
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val CALENDAR_ID = longPreferencesKey("calendar_id")
        val DURATION_MIN = intPreferencesKey("default_duration_minutes")
        val REMINDER_MIN = intPreferencesKey("default_reminder_minutes")
        val PARSER_LANGS = stringSetPreferencesKey("parser_languages")
        val PARSER_ON = stringSetPreferencesKey("parser_on")
        val PARSER_OFF = stringSetPreferencesKey("parser_off")
        val ACTIVITY_START = intPreferencesKey("activity_start_hour")
        val ACTIVITY_END = intPreferencesKey("activity_end_hour")
        val THEME = stringPreferencesKey("theme")
        val NOD = booleanPreferencesKey("nod_enabled")
        val HAPTIC = booleanPreferencesKey("haptic_enabled")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            calendarId = p[Keys.CALENDAR_ID],
            defaultDurationMinutes = p[Keys.DURATION_MIN] ?: 60,
            defaultReminderMinutes = p[Keys.REMINDER_MIN],
            parserLanguages = p[Keys.PARSER_LANGS],
            parserOn = p[Keys.PARSER_ON].orEmpty(),
            parserOff = p[Keys.PARSER_OFF].orEmpty(),
            activityStartHour = p[Keys.ACTIVITY_START] ?: 8,
            activityEndHour = p[Keys.ACTIVITY_END] ?: 22,
            theme = ThemeMode.fromKey(p[Keys.THEME]),
            nodEnabled = p[Keys.NOD] ?: true,
            hapticEnabled = p[Keys.HAPTIC] ?: true,
        )
    }

    suspend fun setCalendarId(id: Long?) {
        context.dataStore.edit { p ->
            if (id == null) p.remove(Keys.CALENDAR_ID) else p[Keys.CALENDAR_ID] = id
        }
    }

    suspend fun setDefaultDuration(minutes: Int) {
        context.dataStore.edit { p -> p[Keys.DURATION_MIN] = minutes }
    }

    suspend fun setParserLanguage(code: String, on: Boolean) {
        context.dataStore.edit { p ->
            val wasOn = p[Keys.PARSER_ON].orEmpty()
            val wasOff = p[Keys.PARSER_OFF].orEmpty()
            p[Keys.PARSER_ON] = if (on) wasOn + code else wasOn - code
            p[Keys.PARSER_OFF] = if (on) wasOff - code else wasOff + code
        }
    }

    suspend fun migrateParserLanguages(uiLanguage: String?) {
        context.dataStore.edit { p ->
            val frozen = p[Keys.PARSER_LANGS] ?: return@edit
            val (on, off) = migrateParserLanguages(frozen, uiLanguage)
            p[Keys.PARSER_ON] = on
            p[Keys.PARSER_OFF] = off
            p.remove(Keys.PARSER_LANGS)
        }
    }

    suspend fun setActivityWindow(startHour: Int, endHour: Int) {
        context.dataStore.edit { p ->
            p[Keys.ACTIVITY_START] = startHour
            p[Keys.ACTIVITY_END] = endHour
        }
    }

    suspend fun setDefaultReminder(minutes: Int?) {
        context.dataStore.edit { p ->
            if (minutes == null) p.remove(Keys.REMINDER_MIN) else p[Keys.REMINDER_MIN] = minutes
        }
    }

    suspend fun setTheme(mode: ThemeMode) {
        context.dataStore.edit { p -> p[Keys.THEME] = mode.key }
    }

    suspend fun setNodEnabled(enabled: Boolean) {
        context.dataStore.edit { p -> p[Keys.NOD] = enabled }
    }

    suspend fun setHapticEnabled(enabled: Boolean) {
        context.dataStore.edit { p -> p[Keys.HAPTIC] = enabled }
    }
}
