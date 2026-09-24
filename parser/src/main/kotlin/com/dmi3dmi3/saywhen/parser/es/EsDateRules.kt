package com.dmi3dmi3.saywhen.parser.es

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

internal object EsDateRules {

    // голое «mañana» — всегда дата; «утро» — только в связке «por/de la mañana»
    private val relative = mapOf("hoy" to 0L, "mañana" to 1L, "manana" to 1L)

    // «esta noche», «mañana por la mañana» — половина суток хинтом для часа
    private val dayHalves = mapOf(
        "mañana" to DayHalf.MORNING, "manana" to DayHalf.MORNING,
        "tarde" to DayHalf.AFTERNOON, "noche" to DayHalf.EVENING,
    )

    val weekdays = mapOf(  // видима EsRecurrenceRules («cada martes»)
        "lunes" to DayOfWeek.MONDAY, "lun" to DayOfWeek.MONDAY,
        "martes" to DayOfWeek.TUESDAY, "mar" to DayOfWeek.TUESDAY,
        "miércoles" to DayOfWeek.WEDNESDAY, "miercoles" to DayOfWeek.WEDNESDAY,
        "mié" to DayOfWeek.WEDNESDAY, "mie" to DayOfWeek.WEDNESDAY,
        "jueves" to DayOfWeek.THURSDAY, "jue" to DayOfWeek.THURSDAY,
        "viernes" to DayOfWeek.FRIDAY, "vie" to DayOfWeek.FRIDAY,
        "sábado" to DayOfWeek.SATURDAY, "sabado" to DayOfWeek.SATURDAY,
        "sáb" to DayOfWeek.SATURDAY, "sab" to DayOfWeek.SATURDAY,
        "domingo" to DayOfWeek.SUNDAY, "dom" to DayOfWeek.SUNDAY,
        // «ma»/«mx»/«lu» — не берём: кросс-языковой шум («ma» — итальянский союз)
    )

    val months = mapOf(  // видима EsRecurrenceRules («hasta septiembre»)
        "enero" to 1, "febrero" to 2, "marzo" to 3, "abril" to 4,
        "mayo" to 5, "junio" to 6, "julio" to 7, "agosto" to 8,
        "septiembre" to 9, "setiembre" to 9, "octubre" to 10,
        "noviembre" to 11, "diciembre" to 12,
        // сокращения; «mar» — сокращение вторника, конфликт решён в пользу дня
        "ene" to 1, "feb" to 2, "abr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "ago" to 8, "sep" to 9, "sept" to 9, "oct" to 10,
        "nov" to 11, "dic" to 12,
    )

    private val offsetUnits = mapOf(
        "día" to ChronoUnit.DAYS, "dia" to ChronoUnit.DAYS,
        "días" to ChronoUnit.DAYS, "dias" to ChronoUnit.DAYS,
        "semana" to ChronoUnit.WEEKS, "semanas" to ChronoUnit.WEEKS,
        "mes" to ChronoUnit.MONTHS, "meses" to ChronoUnit.MONTHS,
        "año" to ChronoUnit.YEARS, "ano" to ChronoUnit.YEARS,
        "años" to ChronoUnit.YEARS, "anos" to ChronoUnit.YEARS,
    )

    private val wordNumbers = mapOf(
        "un" to 1L, "una" to 1L, "uno" to 1L, "dos" to 2L, "tres" to 3L, "cuatro" to 4L,
        "cinco" to 5L, "seis" to 6L, "siete" to 7L, "ocho" to 8L, "nueve" to 9L, "diez" to 10L,
    )

    // «el próximo viernes» / «proximo viernes»
    private val nextAdj = setOf("próximo", "proximo", "próxima", "proxima")

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

        // «pasado mañana» — раньше голого «mañana»
        if (t == "pasado" && tokens.getOrNull(i + 1)?.lower in setOf("mañana", "manana")) {
            return DateCandidate(today.plusDays(2), i..i + 1)
        }

        relative[t]?.let { shift ->
            // «la mañana» — утро, не «завтра»: артикль отдаёт слово правилам времени
            if (t != "hoy" && tokens.getOrNull(i - 1)?.lower == "la") return@let
            // «mañana por la mañana/tarde/noche»
            if (tokens.getOrNull(i + 1)?.lower == "por" && tokens.getOrNull(i + 2)?.lower == "la") {
                dayHalves[tokens.getOrNull(i + 3)?.lower]?.let { half ->
                    return DateCandidate(today.plusDays(shift), i..i + 3, dayHalf = half)
                }
            }
            return DateCandidate(today.plusDays(shift), i..i)
        }

        // «esta noche/tarde/mañana» — сегодня + половина суток
        if (t == "esta") {
            dayHalves[tokens.getOrNull(i + 1)?.lower]?.let { half ->
                return DateCandidate(today, i..i + 1, dayHalf = half)
            }
        }

        matchWeekday(tokens, i, today)?.let { return it }

        calendarAt(tokens, i, today)?.let { return it }

        // «en [N] días…» / «dentro de [N] semanas» («en» — только с числом и юнитом)
        if (t == "en" || t == "dentro") {
            var j = i + 1
            if (t == "dentro") {
                if (tokens.getOrNull(j)?.lower != "de") return null
                j++
            }
            val next = tokens.getOrNull(j)?.lower ?: return null
            val amount = next.toLongOrNull() ?: wordNumbers[next] ?: return null
            if (amount !in 1..3650) return null
            val unit = offsetUnits[tokens.getOrNull(j + 1)?.lower] ?: return null
            return DateCandidate(today.plus(amount, unit), i..j + 1)
        }

        return null
    }

    /**
     * Диапазон дат: «del 23 al 28 de agosto», «13 a 15 de julio»,
     * «23-28 de agosto».
     */
    private fun matchRange(tokens: List<Token>, i: Int, today: LocalDate): DateCandidate? {
        val t = tokens[i].lower

        dayPair(t)?.let { (d1, d2) ->
            var m = i + 1
            if (tokens.getOrNull(m)?.lower == "de") m++
            val month = months[tokens.getOrNull(m)?.lower] ?: return@let
            return dateRangeCandidate(today, d1, month, d2, month, i..m)
        }

        // «del 23 al 28 [de agosto]» / «13 a 15 de julio»
        val marked = t == "del"
        val d1Tok = if (marked) tokens.getOrNull(i + 1)?.lower else t
        val d1 = d1Tok?.toIntOrNull()?.takeIf { it in 1..31 } ?: return null
        var j = if (marked) i + 2 else i + 1
        if (tokens.getOrNull(j)?.lower !in setOf("al", "a")) return null
        val d2 = tokens.getOrNull(j + 1)?.lower?.toIntOrNull()?.takeIf { it in 1..31 } ?: return null
        var end = j + 1
        var m = end + 1
        if (tokens.getOrNull(m)?.lower == "de") m++
        val month = months[tokens.getOrNull(m)?.lower]
        if (month != null) end = m
        // без маркера «del» месяц обязателен: голое «13 a 15» — не диапазон
        if (month == null && !marked) return null
        return month?.let { dateRangeCandidate(today, d1, it, d2, it, i..end) }
    }

    /**
     * Дни недели: `[el|los] [próximo|este] <día> [que viene]`;
     * próximo / que viene — со следующего понедельника.
     */
    private fun matchWeekday(tokens: List<Token>, i: Int, today: LocalDate): DateCandidate? {
        var j = i
        if (tokens[j].lower == "el") j++  // «los lunes» — хабитуал, дело повторов
        var isNext = false
        when (tokens.getOrNull(j)?.lower) {
            in nextAdj -> { isNext = true; j++ }
            "este" -> j++
            else -> {}
        }
        val dow = weekdays[tokens.getOrNull(j)?.lower] ?: return null
        var end = j
        if (!isNext && tokens.getOrNull(j + 1)?.lower == "que" &&
            tokens.getOrNull(j + 2)?.lower == "viene"
        ) {
            isNext = true; end = j + 2
        }

        // «lunes, 18 de febrero» — дата главнее дня недели
        calendarAt(tokens, end + 1, today)?.let { d -> return d.copy(tokens = i..d.tokens.last) }

        // «miércoles de la próxima semana / de la semana que viene / de esta semana»
        if (tokens.getOrNull(end + 1)?.lower == "de") {
            var q = end + 2
            if (tokens.getOrNull(q)?.lower == "la") q++
            val adj = tokens.getOrNull(q)?.lower
            when {
                adj in nextAdj && tokens.getOrNull(q + 1)?.lower == "semana" -> {
                    isNext = true; end = q + 1
                }
                adj == "esta" && tokens.getOrNull(q + 1)?.lower == "semana" -> end = q + 1
                adj == "semana" && tokens.getOrNull(q + 1)?.lower == "que" &&
                    tokens.getOrNull(q + 2)?.lower == "viene" -> {
                    isNext = true; end = q + 2
                }
            }
        }

        val base = if (isNext) today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)) else today
        return DateCandidate(base.with(TemporalAdjusters.nextOrSame(dow)), i..end, fromWeekday = true)
    }

    /**
     * «[el] 3 [de] agosto [de 2027]», «el primero/uno de marzo», «marzo 3»;
     * явный год — буквально, даже прошедший.
     */
    private fun calendarAt(tokens: List<Token>, k: Int, today: LocalDate): DateCandidate? {
        var p = k
        if (tokens.getOrNull(p)?.lower == "el") p++
        val t = tokens.getOrNull(p)?.lower ?: return null

        // «marzo 3» — месяц впереди
        months[t]?.let { month ->
            val day = tokens.getOrNull(p + 1)?.lower?.toIntOrNull()?.takeIf { it in 1..31 }
                ?: return@let
            return withYear(tokens, k, p + 1, month, day, today)
        }

        val day = when (t) {
            "primero", "uno" -> 1
            else -> t.toIntOrNull()?.takeIf { it in 1..31 } ?: return null
        }
        var m = p + 1
        if (tokens.getOrNull(m)?.lower == "de") m++
        val month = months[tokens.getOrNull(m)?.lower] ?: return null
        return withYear(tokens, k, m, month, day, today)
    }

    /** Хвост года «[de] 2027» после месяца на позиции mEnd; без него — ближайшее будущее. */
    private fun withYear(
        tokens: List<Token>,
        from: Int,
        mEnd: Int,
        month: Int,
        day: Int,
        today: LocalDate,
    ): DateCandidate? {
        var y = mEnd + 1
        if (tokens.getOrNull(y)?.lower == "de") y++
        yearToken(tokens.getOrNull(y)?.lower)?.let { year ->
            return dateOrNull(year, month, day)?.let { DateCandidate(it, from..y) }
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
