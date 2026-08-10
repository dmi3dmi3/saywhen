package com.dmi3dmi3.saywhen.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.dmi3dmi3.saywhen.settings.SettingsRepository
import com.dmi3dmi3.saywhen.settings.ThemeMode

/**
 * Носитель ролей дизайн-системы (design.md, «Цвет: темы через роли»).
 * Роль → слот M3: фон карточки → surface · поверхность поля →
 * surfaceContainerHighest (дефолт поля) · акцент распознавания →
 * primaryContainer · чипы → secondaryContainer · действие → primary ·
 * ошибка → error. Экраны берут цвета только из MaterialTheme.colorScheme.
 *
 * Тема — яркость поверх палитры: «как в системе» следует тёмной теме ОС,
 * светлая/тёмная — принудительные. Палитра всюду динамическая (Material You,
 * API 31+; ниже — статические схемы M3): дефолт «выгляди частью системы»,
 * акцентные наборы палитр — следующей итерацией (design.md, оси).
 */
@Composable
fun SayWhenTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    // до первого значения DataStore не рисуем; кэш переживает пересоздание
    // активити (смена языка/конфигурации) — без пустого кадра и мигания
    val settings by repo.settings.collectAsState(initial = null)
    var cachedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val theme = settings?.theme ?: cachedKey?.let(ThemeMode::fromKey) ?: return
    LaunchedEffect(theme) { cachedKey = theme.key }

    val dark = when (theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
