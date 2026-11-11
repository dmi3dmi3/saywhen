package com.dmi3dmi3.saywhen.settings

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.core.app.LocaleManagerCompat

data class LanguageSignals(val system: List<String>, val keyboard: List<String>) {
    companion object {
        val NONE = LanguageSignals(emptyList(), emptyList())
    }
}

fun readLanguageSignals(context: Context): LanguageSignals =
    LanguageSignals(systemLanguages(context), keyboardLanguages(context))

private fun systemLanguages(context: Context): List<String> = runCatching {
    LocaleManagerCompat.getSystemLocales(context).toLanguageTags().split(',')
}.getOrDefault(emptyList())

@Suppress("DEPRECATION")
private fun keyboardLanguages(context: Context): List<String> = runCatching {
    val imm = context.getSystemService(InputMethodManager::class.java) ?: return emptyList()
    imm.enabledInputMethodList.flatMap { imi ->
        imm.getEnabledInputMethodSubtypeList(imi, true)
            .filter { (it.mode.isEmpty() || it.mode == "keyboard") && !it.isAuxiliary }
            .map { it.languageTag.ifEmpty { it.locale } }
    }
}.getOrDefault(emptyList())
