package com.dmi3dmi3.saywhen.quickadd

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.dmi3dmi3.saywhen.parser.TokenMatch
import org.junit.Assert.assertEquals
import org.junit.Test

/** Жесты исключения (задача 12): тап по подсветке, backspace-за-подсветкой, жизнь диапазонов. */
class ApplyEditTest {

    private val text = "ужин в 19"

    private fun String.spanOf(word: String): IntRange {
        val i = indexOf(word)
        require(i >= 0) { "«$word» нет в «$this»" }
        return i until i + word.length
    }

    private val timeMatch = TokenMatch(text.spanOf("в 19"), TokenMatch.Field.TIME)  // 5..8

    private fun tfv(t: String, cursor: Int) = TextFieldValue(t, TextRange(cursor))

    // --- тап ---

    @Test
    fun `тап внутрь матча исключает его`() {
        val new = tfv(text, 7)
        val (value, blocked) = applyEdit(tfv(text, 0), new, emptyList(), listOf(timeMatch))
        assertEquals(new, value)
        assertEquals(listOf(5..8), blocked)
    }

    @Test
    fun `тап на границах и вне матча — не исключение`() {
        for (cursor in listOf(5, 9, 2)) {  // range.first, range.last+1, вне
            val (value, blocked) = applyEdit(tfv(text, 0), tfv(text, cursor), emptyList(), listOf(timeMatch))
            assertEquals(tfv(text, cursor), value)
            assertEquals(emptyList<IntRange>(), blocked)
        }
    }

    @Test
    fun `выделение текста — не тап`() {
        val new = TextFieldValue(text, TextRange(5, 9))
        val (_, blocked) = applyEdit(tfv(text, 0), new, emptyList(), listOf(timeMatch))
        assertEquals(emptyList<IntRange>(), blocked)
    }

    // --- backspace как невидимый символ ---

    @Test
    fun `backspace сразу за матчем — символ цел, матч исключён`() {
        val (value, blocked) = applyEdit(tfv(text, 9), tfv("ужин в 1", 8), emptyList(), listOf(timeMatch))
        assertEquals(tfv(text, 9), value)  // текст восстановлен, курсор на месте
        assertEquals(listOf(5..8), blocked)
    }

    @Test
    fun `backspace не на границе матча — обычное удаление`() {
        val (value, blocked) = applyEdit(tfv(text, 4), tfv("ужи в 19", 3), emptyList(), listOf(timeMatch))
        assertEquals(tfv("ужи в 19", 3), value)
        assertEquals(emptyList<IntRange>(), blocked)
    }

    @Test
    fun `удаление нескольких символов через матч — обычная правка`() {
        // выделили « в 19» и стёрли — жест не срабатывает, текст меняется
        val (value, blocked) = applyEdit(tfv(text, 9), tfv("ужин", 4), emptyList(), listOf(timeMatch))
        assertEquals(tfv("ужин", 4), value)
        assertEquals(emptyList<IntRange>(), blocked)
    }

    // --- жизнь исключённых диапазонов при правках ---

    @Test
    fun `дописывание после исключённого переживает`() {
        // вплотную к слову и дальше; исключённое не подсвечено → matches пуст
        val (value, blocked) =
            applyEdit(tfv(text, 9), tfv("$text с друзьями", 20), listOf(5..8), emptyList())
        assertEquals(tfv("$text с друзьями", 20), value)
        assertEquals(listOf(5..8), blocked)
    }

    @Test
    fun `вставка левее сдвигает диапазон`() {
        val (_, blocked) =
            applyEdit(tfv(text, 0), tfv("наш $text", 4), listOf(5..8), emptyList())
        assertEquals(listOf(9..12), blocked)
    }

    @Test
    fun `удаление левее сдвигает диапазон`() {
        val (_, blocked) =
            applyEdit(tfv(text, 4), tfv("ужи в 19", 3), listOf(5..8), emptyList())
        assertEquals(listOf(4..7), blocked)
    }

    @Test
    fun `правка внутри исключённого снимает исключение`() {
        val (value, blocked) =
            applyEdit(tfv(text, 9), tfv("ужин в 1", 8), listOf(5..8), emptyList())
        assertEquals(tfv("ужин в 1", 8), value)  // символ удалился: подсветки не было, жест не при чём
        assertEquals(emptyList<IntRange>(), blocked)
    }

    @Test
    fun `вставка внутри исключённого снимает исключение`() {
        val (_, blocked) =
            applyEdit(tfv(text, 7), tfv("ужин в 219", 8), listOf(5..8), emptyList())
        assertEquals(emptyList<IntRange>(), blocked)
    }

    @Test
    fun `вставка символа-дубля границы — дифф видит правку внутри, исключение снято`() {
        // документация неоднозначности диффа (ревью 12–15): ввод «в» перед «в 19»
        // неотличим от вставки внутри слова; деградация в безопасную сторону
        val (_, blocked) =
            applyEdit(tfv(text, 5), tfv("ужин вв 19", 6), listOf(5..8), emptyList())
        assertEquals(emptyList<IntRange>(), blocked)
    }

    @Test
    fun `паста поверх всего снимает пересёкшиеся`() {
        val (_, blocked) =
            applyEdit(tfv(text, 9), tfv("совсем другое", 13), listOf(5..8), emptyList())
        assertEquals(emptyList<IntRange>(), blocked)
    }

    @Test
    fun `обычный набор текста — не тап`() {
        // текст и курсор меняются вместе; матч в хвосте не задет
        val m = TokenMatch("в 19 ужин".spanOf("в 19"), TokenMatch.Field.TIME)  // 0..3
        val (value, blocked) =
            applyEdit(tfv("в 19 ужин", 9), tfv("в 19 ужина", 10), emptyList(), listOf(m))
        assertEquals(tfv("в 19 ужина", 10), value)
        assertEquals(emptyList<IntRange>(), blocked)
    }
}
