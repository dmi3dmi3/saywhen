package com.dmi3dmi3.saywhen.parser

/** Слово исходной строки; range — позиции в ней (для matches/подсветки). */
data class Token(val text: String, val range: IntRange) {
    val lower: String get() = text.lowercase()
}

object Tokenizer {
    // буквы/цифры, внутри слова допустимы «:» (19:30), «-» (что-нибудь) и «/»
    // («2/15» — целиком, чтобы числа не рассыпались в ложную пару часов);
    // десятичная пара «1,5»/«19.30» — один токен, с хвостом единицы («1.5h»);
    // числовой дефис с пробелами «13 - 15» — один токен, как склейка «13-15»,
    // ординальные точки допустимы («13. - 15.» — немецкая нотация дней);
    // бэнг вплотную к числу — токен напоминания «!10»/«!1ч» (задача 29)
    private val word =
        Regex("""!\d+\p{L}*|\d+[.,]\d+\p{L}*|\d[\d:]*\.?\s*-\s*[\d:]*\d\.?|[\p{L}\d]+(?:[:/-][\p{L}\d]+)*""")

    fun tokenize(text: String): List<Token> =
        word.findAll(text).map { Token(it.value, it.range) }.toList()
}
