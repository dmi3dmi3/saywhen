package com.dmi3dmi3.saywhen.parser

internal val DAY_OF_MONTH = 1..31
internal val CIRCLE_HOUR = 1..12
internal val AMOUNT = 1..999
internal val INTERVAL = 1..99
internal val OFFSET_DAYS = 1L..3650L
internal val YEARS = 1970..2100

internal class DigitOrdinal(suffix: String) {
    private val optional = Regex("""(\d{1,2})(?:$suffix)?""")
    private val required = Regex("""(\d{1,2})(?:$suffix)""")

    fun parse(s: String?, requireSuffix: Boolean = false): Int? =
        s?.let { (if (requireSuffix) required else optional).matchEntire(it) }
            ?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in DAY_OF_MONTH }
}

internal fun decimalNumber(s: String): Double? =
    s.takeIf { decimalPair.matches(it) }?.replace(',', '.')?.toDoubleOrNull()

private val decimalPair = Regex("""\d{1,3}[.,]\d{1,2}""")
