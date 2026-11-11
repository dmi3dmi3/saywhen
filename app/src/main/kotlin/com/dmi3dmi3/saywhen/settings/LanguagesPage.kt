package com.dmi3dmi3.saywhen.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dmi3dmi3.saywhen.R
import com.dmi3dmi3.saywhen.parser.MultilingualEventParser

@Composable
internal fun LanguagesPage(
    enabled: List<String>,
    uiLanguage: String,
    parserOn: Set<String>,
    parserOff: Set<String>,
    signals: LanguageSignals,
    onToggle: (String, Boolean) -> Unit,
) {
    GroupCard {
        for (code in MultilingualEventParser.LANGUAGES) {
            val on = code in enabled
            val reason = languageReason(code, uiLanguage, parserOn, parserOff, signals.system, signals.keyboard)
            SwitchRow(
                label = stringResource(languageLabels.getValue(code)),
                checked = on,
                hint = reasonLabel(reason)?.let { stringResource(it) },
                locked = reason == LanguageReason.ONLY,
                onCheckedChange = { onToggle(code, it) },
            )
        }
    }
}

private fun reasonLabel(reason: LanguageReason): Int? = when (reason) {
    LanguageReason.UI -> R.string.settings_ui_language
    LanguageReason.ALWAYS -> R.string.language_reason_always
    LanguageReason.SYSTEM -> R.string.language_reason_system
    LanguageReason.KEYBOARD -> R.string.language_reason_keyboard
    LanguageReason.MANUAL_ON -> R.string.language_reason_manual_on
    LanguageReason.MANUAL_OFF -> R.string.language_reason_manual_off
    LanguageReason.ONLY -> R.string.language_reason_only
    LanguageReason.NONE -> null
}
