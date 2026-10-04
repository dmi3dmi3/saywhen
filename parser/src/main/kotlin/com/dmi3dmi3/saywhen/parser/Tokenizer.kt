package com.dmi3dmi3.saywhen.parser

data class Token(val text: String, val range: IntRange) {
    val lower: String get() = text.lowercase()
}

object Tokenizer {
    private val word =
        Regex("""!\d+\p{L}*|\d+[.,]\d+\p{L}*|\d(?:[\d:]*\d)?(?:[hH]\d*)?\.?\s*-\s*\d(?:[\d:]*\d)?(?:[hH]\d*)?\.?|[\p{L}\d]+(?:[:/-][\p{L}\d]+)*""")

    fun tokenize(text: String): List<Token> =
        word.findAll(text).map { Token(it.value, it.range) }.toList()
}
