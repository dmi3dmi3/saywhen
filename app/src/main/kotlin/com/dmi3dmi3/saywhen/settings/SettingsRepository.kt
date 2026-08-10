package com.dmi3dmi3.saywhen.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Settings(
    val calendarId: Long? = null,          // null — автоматически (каскад primary)
    val defaultDurationMinutes: Int = 60,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val nodEnabled: Boolean = true,        // кивок ярлычка на «создано» (19b)
    val hapticEnabled: Boolean = true,     // вибрация при создании (задача 20)
)

/** Тема: яркость поверх палитры (dynamic на 31+, см. ui/Theme.kt). */
enum class ThemeMode(val key: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark");

    companion object {
        /** Неизвестный ключ (настройка из будущей версии) — системная. */
        fun fromKey(key: String?): ThemeMode = entries.find { it.key == key } ?: SYSTEM
    }
}

/** «1 ч 30 мин» из минут; единицы — из ресурсов, функция чистая — покрыта JVM-тестом. */
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
        val THEME = stringPreferencesKey("theme")
        val NOD = booleanPreferencesKey("nod_enabled")
        val HAPTIC = booleanPreferencesKey("haptic_enabled")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            calendarId = p[Keys.CALENDAR_ID],
            defaultDurationMinutes = p[Keys.DURATION_MIN] ?: 60,
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
