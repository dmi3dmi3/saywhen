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
    val defaultTarget: String? = null,
    val tasksEnabled: Boolean = false,
    val tasksNoDueDate: Boolean = false,
    val defaultDurationMinutes: Int = 60,
    val defaultReminderMinutes: Int? = null,
    val parserLanguages: Set<String>? = null,
    val parserOn: Set<String> = emptySet(),
    val parserOff: Set<String> = emptySet(),
    val targetAliases: Map<String, String> = emptyMap(),
    val dotTime: Boolean = false,
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
        val DEFAULT_TARGET = stringPreferencesKey("default_target")
        val TASKS_ENABLED = booleanPreferencesKey("tasks_enabled")
        val TASKS_NO_DUE_DATE = booleanPreferencesKey("tasks_no_due_date")
        val DURATION_MIN = intPreferencesKey("default_duration_minutes")
        val REMINDER_MIN = intPreferencesKey("default_reminder_minutes")
        val PARSER_LANGS = stringSetPreferencesKey("parser_languages")
        val PARSER_ON = stringSetPreferencesKey("parser_on")
        val PARSER_OFF = stringSetPreferencesKey("parser_off")
        val TARGET_ALIASES = stringSetPreferencesKey("target_aliases")
        val DOT_TIME = booleanPreferencesKey("dot_time")
        val ACTIVITY_START = intPreferencesKey("activity_start_hour")
        val ACTIVITY_END = intPreferencesKey("activity_end_hour")
        val THEME = stringPreferencesKey("theme")
        val NOD = booleanPreferencesKey("nod_enabled")
        val HAPTIC = booleanPreferencesKey("haptic_enabled")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            defaultTarget = p[Keys.DEFAULT_TARGET] ?: p[Keys.CALENDAR_ID]?.let { calendarKey(it) },
            tasksEnabled = p[Keys.TASKS_ENABLED] ?: false,
            tasksNoDueDate = p[Keys.TASKS_NO_DUE_DATE] ?: false,
            defaultDurationMinutes = p[Keys.DURATION_MIN] ?: 60,
            defaultReminderMinutes = p[Keys.REMINDER_MIN],
            parserLanguages = p[Keys.PARSER_LANGS],
            parserOn = p[Keys.PARSER_ON].orEmpty(),
            parserOff = p[Keys.PARSER_OFF].orEmpty(),
            targetAliases = p[Keys.TARGET_ALIASES].orEmpty().mapNotNull { entry ->
                entry.split('=', limit = 2).takeIf { it.size == 2 }?.let { (k, v) -> k to v }
            }.toMap(),
            dotTime = p[Keys.DOT_TIME] ?: false,
            activityStartHour = p[Keys.ACTIVITY_START] ?: 8,
            activityEndHour = p[Keys.ACTIVITY_END] ?: 22,
            theme = ThemeMode.fromKey(p[Keys.THEME]),
            nodEnabled = p[Keys.NOD] ?: true,
            hapticEnabled = p[Keys.HAPTIC] ?: true,
        )
    }

    suspend fun setDefaultTarget(key: String?) {
        context.dataStore.edit { p ->
            p.remove(Keys.CALENDAR_ID)
            if (key == null) p.remove(Keys.DEFAULT_TARGET) else p[Keys.DEFAULT_TARGET] = key
        }
    }

    suspend fun setTasksEnabled(enabled: Boolean) {
        context.dataStore.edit { p -> p[Keys.TASKS_ENABLED] = enabled }
    }

    suspend fun setTasksNoDueDate(enabled: Boolean) {
        context.dataStore.edit { p -> p[Keys.TASKS_NO_DUE_DATE] = enabled }
    }

    suspend fun setDefaultDuration(minutes: Int) {
        context.dataStore.edit { p -> p[Keys.DURATION_MIN] = minutes }
    }

    suspend fun setParserLanguage(code: String, on: Boolean, auto: Set<String>) {
        context.dataStore.edit { p ->
            val (nowOn, nowOff) = toggleParserLanguage(code, on, auto, p[Keys.PARSER_ON].orEmpty(), p[Keys.PARSER_OFF].orEmpty())
            p[Keys.PARSER_ON] = nowOn
            p[Keys.PARSER_OFF] = nowOff
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

    suspend fun setTargetAlias(key: String, alias: String?) {
        context.dataStore.edit { p ->
            val rest = p[Keys.TARGET_ALIASES].orEmpty().filterNot { it.startsWith("$key=") }.toSet()
            p[Keys.TARGET_ALIASES] = if (alias.isNullOrEmpty()) rest else rest + "$key=$alias"
        }
    }

    suspend fun setDotTime(enabled: Boolean) {
        context.dataStore.edit { p -> p[Keys.DOT_TIME] = enabled }
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
