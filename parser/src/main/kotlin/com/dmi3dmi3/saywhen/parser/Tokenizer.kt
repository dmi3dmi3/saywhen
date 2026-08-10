package com.dmi3dmi3.saywhen.parser

/** Слово исходной строки; range — позиции в ней (для matches/подсветки). */
data class Token(val text: String, val range: IntRange) {
    val lower: String get() = text.lowercase()
}

object Tokenizer {
    // буквы/цифры, внутри слова допустимы «:» (19:30) и «-» (что-нибудь)
    private val word = Regex("""[\p{L}\d]+(?:[:-][\p{L}\d]+)*""")

    fun tokenize(text: String): List<Token> =
        word.findAll(text).map { Token(it.value, it.range) }.toList()
}
