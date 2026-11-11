package com.dmi3dmi3.saywhen.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import com.dmi3dmi3.saywhen.R

@Composable
internal fun LooksPage(
    theme: ThemeMode,
    nodEnabled: Boolean,
    hapticEnabled: Boolean,
    onSetTheme: (ThemeMode) -> Unit,
    onSetNod: (Boolean) -> Unit,
    onSetHaptic: (Boolean) -> Unit,
) {
    Section(R.string.settings_theme) {
        ThemeTiles(selected = theme, onSelect = onSetTheme)
    }
    Section(R.string.settings_ui_language) {
        OptionChips(
            options = listOf("" to stringResource(R.string.language_system)) +
                languageLabels.map { (code, label) -> code to stringResource(label) },
            selected = currentUiLanguage(),
            onSelect = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(it)) },
        )
    }
    Section(R.string.settings_behavior) {
        SwitchRow(R.string.setting_nod, nodEnabled, onCheckedChange = onSetNod)
        SwitchRow(R.string.setting_haptic, hapticEnabled, onCheckedChange = onSetHaptic)
    }
}
