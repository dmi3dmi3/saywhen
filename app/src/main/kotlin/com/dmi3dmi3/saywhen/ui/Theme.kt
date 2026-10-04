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

@Composable
fun SayWhenTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
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
