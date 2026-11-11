package com.dmi3dmi3.saywhen.parser

internal object DotTime {
    private val pair = Regex("""(?<![\d.])(\d{1,2})\.(\d{2})(?![\d\p{L}]|\.\d)""")

    fun normalize(text: String): String = pair.replace(text) { m ->
        val (h, mm) = m.destructured
        if (h.toInt() in 0..23 && mm.toInt() in 0..59) "$h:$mm" else m.value
    }
}
