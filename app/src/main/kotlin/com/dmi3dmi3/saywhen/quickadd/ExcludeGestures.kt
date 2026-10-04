package com.dmi3dmi3.saywhen.quickadd

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.dmi3dmi3.saywhen.parser.TokenMatch

internal fun applyEdit(
    old: TextFieldValue,
    new: TextFieldValue,
    blocked: List<IntRange>,
    matches: List<TokenMatch>,
): Pair<TextFieldValue, List<IntRange>> {
    if (new.text == old.text) {
        if (new.selection.collapsed && new.selection != old.selection) {
            val cursor = new.selection.start
            matches.firstOrNull { it.range.first < cursor && cursor <= it.range.last }
                ?.let { return new to blocked + listOf(it.range) }
        }
        return new to blocked
    }

    if (new.text.length == old.text.length - 1 && old.selection.collapsed) {
        val p = old.selection.start
        val hit = matches.firstOrNull { it.range.last == p - 1 }
        if (hit != null && new.selection == TextRange(p - 1) &&
            old.text.removeRange(p - 1, p) == new.text
        ) {
            return old.copy(selection = TextRange(p)) to blocked + listOf(hit.range)
        }
    }

    val pre = commonPrefix(old.text, new.text)
    val suf = commonSuffix(old.text, new.text, pre)
    val editStart = pre
    val editEndExcl = old.text.length - suf
    val delta = new.text.length - old.text.length
    val adjusted = blocked.mapNotNull { b ->
        when {
            editEndExcl <= b.first -> (b.first + delta)..(b.last + delta)
            editStart > b.last -> b
            else -> null
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
