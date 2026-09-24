package com.dmi3dmi3.saywhen.parser.it

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

internal object ItDateRules {

    private val relative = mapOf("oggi" to 0L, "domani" to 1L, "dopodomani" to 2L)

    // «domani sera» — половина суток приклеивается к дню хинтом для часа
    private val dayHalves = mapOf(
        "mattina" to DayHalf.MORNING, "mattino" to DayHalf.MORNING,
        "sera" to DayHalf.EVENING, "notte" to DayHalf.EVENING,
        "pomeriggio" to DayHalf.AFTERNOON,
    )

    val weekdays = mapOf(  // видима ItRecurrenceRules («ogni martedì»)
        "lunedì" to DayOfWeek.MONDAY, "lunedi" to DayOfWeek.MONDAY, "lun" to DayOfWeek.MONDAY,
        "martedì" to DayOfWeek.TUESDAY, "martedi" to DayOfWeek.TUESDAY, "mar" to DayOfWeek.TUESDAY,
        "mercoledì" to DayOfWeek.WEDNESDAY, "mercoledi" to DayOfWeek.WEDNESDAY,
        "mer" to DayOfWeek.WEDNESDAY,
        "giovedì" to DayOfWeek.THURSDAY, "giovedi" to DayOfWeek.THURSDAY, "gio" to DayOfWeek.THURSDAY,
        "venerdì" to DayOfWeek.FRIDAY, "venerdi" to DayOfWeek.FRIDAY, "ven" to DayOfWeek.FRIDAY,
        "sabato" to DayOfWeek.SATURDAY, "sab" to DayOfWeek.SATURDAY,
        "domenica" to DayOfWeek.SUNDAY, "dom" to DayOfWeek.SUNDAY,
    )

    val months = mapOf(  // видима ItRecurrenceRules («fino a settembre»)
        "gennaio" to 1, "febbraio" to 2, "marzo" to 3, "aprile" to 4,
        "maggio" to 5, "giugno" to 6, "luglio" to 7, "agosto" to 8,
        "settembre" to 9, "ottobre" to 10, "novembre" to 11, "dicembre" to 12,
        // сокращения; «mar» — сокращение вторника, конфликт решён в пользу дня
        "gen" to 1, "feb" to 2, "apr" to 4, "mag" to 5, "giu" to 6,
        "lug" to 7, "ago" to 8, "set" to 9, "sett" to 9, "ott" to 10,
        "nov" to 11, "dic" to 12,
    )

    private val offsetUnits = mapOf(
        "giorno" to ChronoUnit.DAYS, "giorni" to ChronoUnit.DAYS,
        "settimana" to ChronoUnit.WEEKS, "settimane" to ChronoUnit.WEEKS,
        "mese" to ChronoUnit.MONTHS, "mesi" to ChronoUnit.MONTHS,
        "anno" to ChronoUnit.YEARS, "anni" to ChronoUnit.YEARS,
    )

    private val wordNumbers = mapOf(
        "un" to 1L, "uno" to 1L, "una" to 1L, "due" to 2L, "tre" to 3L, "quattro" to 4L,
        "cinque" to 5L, "sei" to 6L, "sette" to 7L, "otto" to 8L, "nove" to 9L, "dieci" to 10L,
    )

    // «il 1º marzo» — маскулинный ординал держится в токене (º — буква)
    private val dayOrdinal = Regex("""(\d{1,2})º?""")

    // «prossimo lunedì» / «domenica prossima» — обе позиции и оба рода
    private val nextAdj = setOf("prossimo", "prossima")

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

        // «dopo domani» раздельно — раньше голого «domani»
        if (t == "dopo" && tokens.getOrNull(i + 1)?.lower == "domani") {
            return DateCandidate(today.plusDays(2), i..i + 1)
        }

        relative[t]?.let { shift ->
            dayHalves[tokens.getOrNull(i + 1)?.lower]?.let { half ->
                return DateCandidate(today.plusDays(shift), i..i + 1, dayHalf = half)
            }
            return DateCandidate(today.plusDays(shift), i..i)
        }

        // сутки + половина одним словом
        when (t) {
            "stasera", "stanotte" -> return DateCandidate(today, i..i, dayHalf = DayHalf.EVENING)
            "stamattina" -> return DateCandidate(today, i..i, dayHalf = DayHalf.MORNING)
            "domattina" -> return DateCandidate(today.plusDays(1), i..i, dayHalf = DayHalf.MORNING)
        }

        // «questa sera» / «sta sera» — то же раздельно
        if (t == "questa" || t == "sta") {
            dayHalves[tokens.getOrNull(i + 1)?.lower]?.let { half ->
                return DateCandidate(today, i..i + 1, dayHalf = half)
            }
        }

        matchWeekday(tokens, i, today)?.let { return it }

        calendarAt(tokens, i, today)?.let { return it }

        // «tra/fra [N] giorni…»; «in» — только с числом и юнитом («in settimana» — не сдвиг)
        if (t == "tra" || t == "fra" || t == "in") {
            val next = tokens.getOrNull(i + 1)?.lower ?: return null
            val amount = next.toLongOrNull() ?: wordNumbers[next]
            if (amount != null) {
                if (amount !in 1..3650) return null
                val unit = offsetUnits[tokens.getOrNull(i + 2)?.lower] ?: return null
                return DateCandidate(today.plus(amount, unit), i..i + 2)
            }
            if (t != "in") {
                offsetUnits[next]?.let { return DateCandidate(today.plus(1, it), i..i + 1) }
            }
        }

        return null
    }

    /**
     * Диапазон дат: «dal 23 [agosto] al 28 agosto», «23-28 agosto»,
     * «13 - 15 luglio» (спейс-дефис держит токенайзер).
     */
    private fun matchRange(tokens: List<Token>, i: Int, today: LocalDate): DateCandidate? {
        val t = tokens[i].lower

        dayPair(t)?.let { (d1, d2) ->
            val month = months[tokens.getOrNull(i + 1)?.lower] ?: return@let
            return dateRangeCandidate(today, d1, month, d2, month, i..i + 1)
        }

        if (t != "dal") return null
        val d1 = tokens.getOrNull(i + 1)?.lower?.toIntOrNull()?.takeIf { it in 1..31 } ?: return null
        var j = i + 2
        val startMonth = months[tokens.getOrNull(j)?.lower]
        if (startMonth != null) j++
        if (tokens.getOrNull(j)?.lower != "al") return null
        val d2 = tokens.getOrNull(j + 1)?.lower?.toIntOrNull()?.takeIf { it in 1..31 } ?: return null
        var end = j + 1
        val endMonth = months[tokens.getOrNull(end + 1)?.lower]
        if (endMonth != null) end++
        if (startMonth == null && endMonth == null) return null
        return dateRangeCandidate(today, d1, startMonth ?: endMonth!!, d2, endMonth ?: startMonth!!, i..end)
    }

    /**
     * Дни недели: `[il|l] [prossimo] <giorno> [prossimo]`; prossimo — со
     * следующего понедельника. Календарная дата следом главнее дня недели.
     */
    private fun matchWeekday(tokens: List<Token>, i: Int, today: LocalDate): DateCandidate? {
        var j = i
        if (tokens[j].lower == "il" || tokens[j].lower == "l") j++
        var isNext = false
        if (tokens.getOrNull(j)?.lower in nextAdj) {
            isNext = true; j++
        }
        val dow = weekdays[tokens.getOrNull(j)?.lower] ?: return null
        var end = j
        if (!isNext && tokens.getOrNull(j + 1)?.lower in nextAdj) {
            isNext = true; end = j + 1
        }

        // «lunedì 18 febbraio» — дата главнее дня недели
        calendarAt(tokens, end + 1, today)?.let { d -> return d.copy(tokens = i..d.tokens.last) }

        // «mercoledì della prossima settimana» / «di questa settimana»
        if (tokens.getOrNull(end + 1)?.lower in setOf("di", "della")) {
            val q = end + 2
            val adj = tokens.getOrNull(q)?.lower
            if (adj in setOf("prossima", "questa") && tokens.getOrNull(q + 1)?.lower == "settimana") {
                if (adj == "prossima") isNext = true
                end = q + 1
            }
        }

        val base = if (isNext) today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)) else today
        return DateCandidate(base.with(TemporalAdjusters.nextOrSame(dow)), i..end, fromWeekday = true)
    }

    /** «[il] 3 giugno [2027]», «primo [di] marzo», «il 1º marzo»; явный год — буквально. */
    private fun calendarAt(tokens: List<Token>, k: Int, today: LocalDate): DateCandidate? {
        var p = k
        if (tokens.getOrNull(p)?.lower == "il") p++
        val t = tokens.getOrNull(p)?.lower ?: return null
        val day = if (t == "primo") 1
                  else dayOrdinal.matchEntire(t)?.groupValues?.get(1)?.toIntOrNull()
                      ?.takeIf { it in 1..31 } ?: return null
        var m = p + 1
        if (t == "primo" && tokens.getOrNull(m)?.lower == "di") m++
        val month = months[tokens.getOrNull(m)?.lower] ?: return null
        yearToken(tokens.getOrNull(m + 1)?.lower)?.let { y ->
            return dateOrNull(y, month, day)?.let { DateCandidate(it, k..m + 1) }
        }
        val thisYear = dateOrNull(today.year, month, day)
        val date = if (thisYear != null && !thisYear.isBefore(today)) thisYear
                   else dateOrNull(today.year + 1, month, day)
        return date?.let { DateCandidate(it, k..m) }
    }

    fun dateOrNull(year: Int, month: Int, day: Int): LocalDate? =
        try {
            LocalDate.of(year, month, day)
        } catch (e: DateTimeException) {
            null
        }
}
