package com.dmi3dmi3.saywhen.parser.de

import com.dmi3dmi3.saywhen.parser.DateCandidate
import com.dmi3dmi3.saywhen.parser.DayHalf
import com.dmi3dmi3.saywhen.parser.Token
import com.dmi3dmi3.saywhen.parser.dateRangeCandidate
import com.dmi3dmi3.saywhen.parser.dayPair
import com.dmi3dmi3.saywhen.parser.yearToken
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

internal object DeDateRules {

    // голое «morgen» — всегда дата; «утро» — только «am Morgen» / «morgens»
    private val relative = mapOf("heute" to 0L, "morgen" to 1L, "übermorgen" to 2L, "uebermorgen" to 2L)

    // «heute abend», «morgen früh» — половина суток приклеивается хинтом;
    // «heute morgen» — сегодня утром
    private val dayHalves = mapOf(
        "früh" to DayHalf.MORNING, "frueh" to DayHalf.MORNING, "morgen" to DayHalf.MORNING,
        "vormittag" to DayHalf.MORNING,
        "abend" to DayHalf.EVENING, "nachmittag" to DayHalf.AFTERNOON, "nacht" to DayHalf.EVENING,
    )

    val weekdays = mapOf(  // видима DeRecurrenceRules («jeden Dienstag»)
        "montag" to DayOfWeek.MONDAY,
        "dienstag" to DayOfWeek.TUESDAY,
        "mittwoch" to DayOfWeek.WEDNESDAY,
        "donnerstag" to DayOfWeek.THURSDAY,
        "freitag" to DayOfWeek.FRIDAY,
        "samstag" to DayOfWeek.SATURDAY, "sonnabend" to DayOfWeek.SATURDAY,
        "sonntag" to DayOfWeek.SUNDAY,
        // двухбуквенные mo/di/mi/do/fr/sa/so — не берём: «do», «so», «di» —
        // живые слова en/it, кросс-языковой шум в мердже
    )

    val months = mapOf(  // видима DeRecurrenceRules («bis September»)
        "januar" to 1, "februar" to 2, "märz" to 3, "maerz" to 3, "april" to 4,
        "mai" to 5, "juni" to 6, "juli" to 7, "august" to 8,
        "september" to 9, "oktober" to 10, "november" to 11, "dezember" to 12,
        "jan" to 1, "feb" to 2, "mär" to 3, "apr" to 4, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "sept" to 9, "okt" to 10,
        "nov" to 11, "dez" to 12,
    )

    private val offsetUnits = mapOf(
        "tag" to ChronoUnit.DAYS, "tage" to ChronoUnit.DAYS, "tagen" to ChronoUnit.DAYS,
        "woche" to ChronoUnit.WEEKS, "wochen" to ChronoUnit.WEEKS,
        "monat" to ChronoUnit.MONTHS, "monate" to ChronoUnit.MONTHS, "monaten" to ChronoUnit.MONTHS,
        "jahr" to ChronoUnit.YEARS, "jahre" to ChronoUnit.YEARS, "jahren" to ChronoUnit.YEARS,
    )

    private val wordNumbers = mapOf(
        "einem" to 1L, "einer" to 1L, "ein" to 1L, "eine" to 1L, "zwei" to 2L, "drei" to 3L,
        "vier" to 4L, "fünf" to 5L, "fuenf" to 5L, "sechs" to 6L, "sieben" to 7L,
        "acht" to 8L, "neun" to 9L, "zehn" to 10L,
    )

    private val nextAdj = setOf("nächsten", "nächste", "naechsten", "naechste", "kommenden", "kommende")
    private val thisAdj = setOf("diesen", "diese", "dieses")

    // «15te februar», «13ter» — цифро-суффиксы ординала
    private val daySuffix = Regex("""(\d{1,2})(?:te|ten|ter|tes)?""")

    // «erster märz», «am vierten dezember» — ординалы словами 1..20 и 30;
    // слитные 21–29 («einundzwanzigster») — не берём, в наборе не встречаются
    private val ordinalUnits: Map<String, Int> = buildMap {
        val stems = listOf(
            "erst" to 1, "zweit" to 2, "dritt" to 3, "viert" to 4, "fünft" to 5, "fuenft" to 5,
            "sechst" to 6, "siebt" to 7, "acht" to 8, "neunt" to 9, "zehnt" to 10,
            "elft" to 11, "zwölft" to 12, "zwoelft" to 12, "dreizehnt" to 13, "vierzehnt" to 14,
            "fünfzehnt" to 15, "fuenfzehnt" to 15, "sechzehnt" to 16, "siebzehnt" to 17,
            "achtzehnt" to 18, "neunzehnt" to 19, "zwanzigst" to 20,
            "dreißigst" to 30, "dreissigst" to 30,
        )
        for ((stem, n) in stems) for (suffix in listOf("e", "er", "en", "es")) put(stem + suffix, n)
    }

    /** Все кандидаты по свободным токенам в порядке появления. */
    fun findAll(tokens: List<Token>, now: ZonedDateTime, used: BooleanArray): List<DateCandidate> {
        val today = now.toLocalDate()
        val found = mutableListOf<DateCandidate>()
        var i = 0
        while (i < tokens.size) {
            val candidate = if (used[i]) null else matchAt(tokens, i, today)
            if (candidate != null && candidate.tokens.all { !used[it] }) {
                found += candidate
                i = candidate.tokens.last + 1
            } else {
                i++
            }
        }
        return found
    }

    private fun matchAt(tokens: List<Token>, i: Int, today: LocalDate): DateCandidate? {
        val t = tokens[i].lower

        matchRange(tokens, i, today)?.let { return it }

        relative[t]?.let { shift ->
            // «am morgen» — утро, не «завтра»: артикль отдаёт слово правилам времени
            if (t == "morgen" && tokens.getOrNull(i - 1)?.lower == "am") return@let
            // «heute abend», «morgen früh», «heute morgen»
            dayHalves[tokens.getOrNull(i + 1)?.lower]?.let { half ->
                return DateCandidate(today.plusDays(shift), i..i + 1, dayHalf = half)
            }
            return DateCandidate(today.plusDays(shift), i..i)
        }

        matchWeekday(tokens, i, today)?.let { return it }

        calendarAt(tokens, i, today)?.let { return it }

        // «in [N] Tagen/Wochen/Monaten/Jahren» — только с числом и юнитом
        if (t == "in") {
            val next = tokens.getOrNull(i + 1)?.lower ?: return null
            val amount = next.toLongOrNull() ?: wordNumbers[next] ?: return null
            if (amount !in 1..3650) return null
            val unit = offsetUnits[tokens.getOrNull(i + 2)?.lower] ?: return null
            return DateCandidate(today.plus(amount, unit), i..i + 2)
        }

        return null
    }

    /** Диапазон дат: «[vom] 13 bis [zum] 15 Juli», «13-15 Juli», «13. - 15. Juli». */
    private fun matchRange(tokens: List<Token>, i: Int, today: LocalDate): DateCandidate? {
        val t = tokens[i].lower

        dayPair(t)?.let { (d1, d2) ->
            val month = months[tokens.getOrNull(i + 1)?.lower] ?: return@let
            return dateRangeCandidate(today, d1, month, d2, month, i..i + 1)
        }

        val d1 = dayAt(t) ?: return null
        var j = i + 1
        val startMonth = months[tokens.getOrNull(j)?.lower]
        if (startMonth != null) j++
        if (tokens.getOrNull(j)?.lower != "bis") return null
        var k = j + 1
        if (tokens.getOrNull(k)?.lower == "zum") k++
        val d2 = tokens.getOrNull(k)?.lower?.let(::dayAt) ?: return null
        var end = k
        val endMonth = months[tokens.getOrNull(end + 1)?.lower]
        if (endMonth != null) end++
        if (startMonth == null && endMonth == null) return null
        return dateRangeCandidate(today, d1, startMonth ?: endMonth!!, d2, endMonth ?: startMonth!!, i..end)
    }

    /** День цифрой с необязательным суффиксом «15te/13ter»; иначе null. */
    private fun dayAt(word: String): Int? =
        daySuffix.matchEntire(word)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..31 }

    /** Дни недели: `[nächsten|kommenden|diesen] <Tag> [[der] nächsten/dieser Woche]`. */
    private fun matchWeekday(tokens: List<Token>, i: Int, today: LocalDate): DateCandidate? {
        var j = i
        val adj = tokens.getOrNull(j)?.lower
        var isNext = adj in nextAdj
        if (isNext || adj in thisAdj) j++
        val dow = weekdays[tokens.getOrNull(j)?.lower] ?: return null

        // «Montag, 18. Februar» — дата главнее дня недели
        calendarAt(tokens, j + 1, today)?.let { d -> return d.copy(tokens = i..d.tokens.last) }

        var end = j
        // «mittwoch [der] nächste(n) woche» / «dienstag dieser woche»
        run {
            var q = j + 1
            if (tokens.getOrNull(q)?.lower == "der") q++
            val qualifier = tokens.getOrNull(q)?.lower ?: return@run
            val next = qualifier in nextAdj
            if (!next && qualifier !in setOf("dieser", "diese")) return@run
            if (tokens.getOrNull(q + 1)?.lower != "woche") return@run
            if (next) isNext = true
            end = q + 1
        }

        val base = if (isNext) today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)) else today
        return DateCandidate(base.with(TemporalAdjusters.nextOrSame(dow)), i..end, fromWeekday = true)
    }

    /**
     * «[am] 15[.|te] Februar [2027]», «Februar 15», «erster März», «am vierten
     * Dezember»; явный год — буквально, даже прошедший.
     */
    private fun calendarAt(tokens: List<Token>, k: Int, today: LocalDate): DateCandidate? {
        val t = tokens.getOrNull(k)?.lower ?: return null

        // «Februar 15» — месяц впереди
        months[t]?.let { month ->
            val day = tokens.getOrNull(k + 1)?.lower?.let(::dayAt) ?: return@let
            return withYear(tokens, k, k + 1, month, day, today)
        }

        val day = dayAt(t) ?: ordinalUnits[t] ?: return null
        val month = months[tokens.getOrNull(k + 1)?.lower] ?: return null
        return withYear(tokens, k, k + 1, month, day, today)
    }

    /** Хвост года «2027» после месяца на позиции mEnd; без него — ближайшее будущее. */
    private fun withYear(
        tokens: List<Token>,
        from: Int,
        mEnd: Int,
        month: Int,
        day: Int,
        today: LocalDate,
    ): DateCandidate? {
        yearToken(tokens.getOrNull(mEnd + 1)?.lower)?.let { year ->
            return dateOrNull(year, month, day)?.let { DateCandidate(it, from..mEnd + 1) }
        }
        val thisYear = dateOrNull(today.year, month, day)
        val date = if (thisYear != null && !thisYear.isBefore(today)) thisYear
                   else dateOrNull(today.year + 1, month, day)
        return date?.let { DateCandidate(it, from..mEnd) }
    }

    fun dateOrNull(year: Int, month: Int, day: Int): LocalDate? =
        try {
            LocalDate.of(year, month, day)
        } catch (e: DateTimeException) {
            null
        }
}
