package com.dmi3dmi3.saywhen.parser

import java.time.Duration
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Языконезависимая сборка события из IR-кандидатов: клеймы и поглощение
 * предлогов-сирот, базовая дата и якоря повторов, 12-часовой круг
 * ([TwelveHourClock]), сдвиги «время в будущем», итоговое RRULE, вырезание
 * заголовка. Ни одного слова конкретного языка — язык кончается на границе
 * [Translator].
 */
internal object EventAssembly {

    private class Claim(var tokens: IntRange, val field: TokenMatch.Field)

    fun assemble(
        text: String,
        tokens: List<Token>,
        blocked: BooleanArray,
        extraction: Extraction,
        orphanWords: Set<String>,
        defaultTitle: String,
        now: ZonedDateTime,
    ): ParsedEvent {
        val used = BooleanArray(tokens.size) { blocked[it] }
        val claims = mutableListOf<Claim>()

        fun take(range: IntRange, field: TokenMatch.Field) {
            for (j in range) used[j] = true
            claims += Claim(range, field)
        }

        val rec = extraction.recurrence
        val date = extraction.date
        val time = extraction.time
        rec?.let { r ->
            take(r.tokens, TokenMatch.Field.RECURRENCE)
            r.extraTokens.forEach { take(it, TokenMatch.Field.RECURRENCE) }  // «до конца августа», «10 раз»
        }
        date?.let { take(it.tokens, TokenMatch.Field.DATE) }
        time?.let { take(it.tokens, TokenMatch.Field.TIME) }
        extraction.duration?.let { take(it.tokens, TokenMatch.Field.DURATION) }

        // поглощение осиротевших предлогов (справа налево — цепочки каскадом);
        // к заблокированному соседу не липнем — он останется в заголовке
        for (i in tokens.indices.reversed()) {
            if (!used[i] && tokens[i].lower in orphanWords &&
                used.getOrNull(i + 1) == true && !blocked[i + 1]
            ) {
                used[i] = true
                claims.find { it.tokens.first == i + 1 }?.let { it.tokens = i..it.tokens.last }
            }
        }

        // база — явная дата или сегодня; повтор подтягивает её к своему якорю
        val baseDate = date?.date ?: now.toLocalDate()
        val startDate = rec?.resolveStartDate(baseDate) ?: baseDate
        val allDay = time == null
        // половина суток от контекста («tonight», «every morning») снимает пару круга
        val dayHalf = rec?.dayHalf ?: date?.dayHalf
        var start = when {
            allDay -> startDate.atStartOfDay(now.zone)
            time!!.twelveHour && dayHalf != null ->
                startDate.atTime(time.time.withHour(dayHalf.resolve(time.time.hour))).atZone(now.zone)
            // 12-часовой круг: окно активности решает пару, «сейчас» не участвует —
            // прошедшее без явной даты уезжает вперёд общим сдвигом ниже
            time.twelveHour -> TwelveHourClock.resolve(time.time, startDate, now.zone)
            else -> startDate.atTime(time.time).atZone(now.zone)
        }

        // «время в будущем»: сдвигаем только когда дата не была сказана явно
        if (!allDay && !start.isAfter(now)) {
            start = when {
                rec != null -> {
                    val next = rec.nextOccurrence(startDate)
                    // серия уже закончилась (UNTIL раньше следующего вхождения) —
                    // старт за UNTIL не гоним, иначе создалась бы пустая серия
                    if (rec.untilDate != null && next.isAfter(rec.untilDate)) start
                    else next.atTime(start.toLocalTime()).atZone(now.zone)
                }
                date == null -> start.plusDays(1)           // «в 14» ровно сейчас → завтра
                date.fromWeekday -> start.plusWeeks(1)      // «во вторник в 9» → след. вторник
                else -> start                               // «сегодня в 9» — уважаем
            }
        }

        // диапазон дат «с 23 по 28 августа» — длительность в днях, конец включительно;
        // при явном времени диапазон не действует — timed-событие на дату старта
        val rangeDuration = date?.endDate
            ?.takeIf { allDay }
            ?.let { Duration.ofDays(ChronoUnit.DAYS.between(date.date, it) + 1) }

        return ParsedEvent(
            title = buildTitle(text, tokens, used, blocked, defaultTitle),
            start = start,
            allDay = allDay,
            duration = time?.duration ?: extraction.duration?.duration ?: rangeDuration,
            rrule = rec?.let { finalRrule(it, allDay, now) },
            matches = claims
                .map { TokenMatch(charSpan(tokens, it.tokens), it.field) }
                .sortedBy { it.range.first },
        )
    }

    /**
     * Итоговое RRULE: база + UNTIL/COUNT. UNTIL для all-day — дата, для timed —
     * конец дня в зоне пользователя, переведённый в UTC (гоча провайдера:
     * timed-UNTIL обязан быть с 'Z').
     */
    private fun finalRrule(rec: RecurrenceCandidate, allDay: Boolean, now: ZonedDateTime): String {
        val until = rec.untilDate?.let { d ->
            if (allDay) DateTimeFormatter.BASIC_ISO_DATE.format(d)
            else d.atTime(23, 59, 59).atZone(now.zone)
                .withZoneSameInstant(ZoneOffset.UTC)
                .format(UNTIL_UTC)
        }
        return buildString {
            append(rec.rrule)
            until?.let { append(";UNTIL=").append(it) }
            rec.count?.let { append(";COUNT=").append(it) }
        }
    }

    private val UNTIL_UTC: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

    /** Текст минус занятые токены (кроме заблокированных); края — без висячей пунктуации. */
    private fun buildTitle(
        text: String,
        tokens: List<Token>,
        used: BooleanArray,
        blocked: BooleanArray,
        defaultTitle: String,
    ): String {
        val sb = StringBuilder(text)
        for (i in tokens.indices.reversed()) {
            if (used[i] && !blocked[i]) sb.delete(tokens[i].range.first, tokens[i].range.last + 1)
        }
        return sb.toString()
            .replace(Regex("\\s+"), " ")
            .replace(Regex(" ([,.;:!?])"), "$1")  // «встреча , обед» → «встреча, обед»
            .replace(Regex(",{2,}"), ",")         // следы двух вырезов подряд
            .trim { it.isWhitespace() || it in ",.;:—-" }
            .ifEmpty { defaultTitle }
    }

    /** Символьный диапазон от первого до последнего токена. */
    private fun charSpan(tokens: List<Token>, range: IntRange): IntRange =
        tokens[range.first].range.first..tokens[range.last].range.last
}
