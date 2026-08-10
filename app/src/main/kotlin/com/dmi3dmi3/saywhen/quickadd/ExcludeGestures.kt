package com.dmi3dmi3.saywhen.quickadd

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.dmi3dmi3.saywhen.parser.TokenMatch

/**
 * Решение по одному изменению поля ввода: что показать и какой стоп-лист дальше.
 * [matches] — от предыдущего парса, т.е. подсветка, которую пользователь видит
 * в момент жеста.
 *
 * Жесты: тап по подсветке — матч в стоп-лист (курсор ставится как обычно);
 * backspace сразу за подсветкой — то же, символ не удаляется («подсветка как
 * невидимый символ»); правка, задевшая символы внутри исключённого диапазона, —
 * единственный путь обратно в распознание. Всё непохожее на жест проходит
 * как обычная правка — деградация в безопасную сторону.
 */
internal fun applyEdit(
    old: TextFieldValue,
    new: TextFieldValue,
    blocked: List<IntRange>,
    matches: List<TokenMatch>,
): Pair<TextFieldValue, List<IntRange>> {
    if (new.text == old.text) {
        // тап: selection схлопнут и переехал строго внутрь матча;
        // границы (first и last+1) — обычная постановка курсора, не жест
        if (new.selection.collapsed && new.selection != old.selection) {
            val cursor = new.selection.start
            matches.firstOrNull { it.range.first < cursor && cursor <= it.range.last }
                ?.let { return new to blocked + listOf(it.range) }
        }
        return new to blocked
    }

    // backspace-за-подсветкой: удалён ровно последний символ матча,
    // курсор стоял сразу за ним → текст возвращаем, матч в стоп-лист
    if (new.text.length == old.text.length - 1 && old.selection.collapsed) {
        val p = old.selection.start
        val hit = matches.firstOrNull { it.range.last == p - 1 }
        if (hit != null && new.selection == TextRange(p - 1) &&
            old.text.removeRange(p - 1, p) == new.text
        ) {
            return old.copy(selection = TextRange(p)) to blocked + listOf(hit.range)
        }
    }

    // обычная правка: дифф общий-префикс/суффикс → заменённый интервал старого текста
    val pre = commonPrefix(old.text, new.text)
    val suf = commonSuffix(old.text, new.text, pre)
    val editStart = pre
    val editEndExcl = old.text.length - suf
    val delta = new.text.length - old.text.length
    val adjusted = blocked.mapNotNull { b ->
        when {
            editEndExcl <= b.first -> (b.first + delta)..(b.last + delta)  // целиком левее — сдвиг
            editStart > b.last -> b            // целиком правее (вкл. дописывание вплотную)
            else -> null                       // задела символы слова — исключение снимается
        }
    }
    return new to adjusted
}

private fun commonPrefix(a: String, b: String): Int {
    val n = minOf(a.length, b.length)
    var i = 0
    while (i < n && a[i] == b[i]) i++
    return i
}

private fun commonSuffix(a: String, b: String, pre: Int): Int {
    val n = minOf(a.length, b.length) - pre
    var i = 0
    while (i < n && a[a.length - 1 - i] == b[b.length - 1 - i]) i++
    return i
}
