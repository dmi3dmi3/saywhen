package com.dmi3dmi3.saywhen.quickadd

import com.dmi3dmi3.saywhen.parser.MultilingualEventParser
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class PreviewSummaryTest {

    private val parser = MultilingualEventParser()

    // вторник, 14:00
    private val now = ZonedDateTime.of(2026, 7, 21, 14, 0, 0, 0, ZoneId.of("Europe/Moscow"))
    private val hour = Duration.ofHours(1)
    private val ru = SummaryLabels(
        today = "сегодня", tomorrow = "завтра", allDay = "весь день",
        locale = Locale("ru"), datePattern = "EEE, d MMM", timePattern = "H:mm",
        hourUnit = "ч", minuteUnit = "мин",
        reminderAtEvent = "🔔 в начале", reminderBefore = "🔔 за %s",
    )
    private val en = SummaryLabels(
        today = "today", tomorrow = "tomorrow", allDay = "all day",
        locale = Locale.ENGLISH, datePattern = "EEE, MMM d", timePattern = "h:mm a",
        hourUnit = "h", minuteUnit = "min",
        reminderAtEvent = "🔔 at start", reminderBefore = "🔔 %s before",
    )

    private fun summary(
        text: String,
        labels: SummaryLabels = ru,
        defaultReminder: Int? = null,
    ): EventSummary =
        previewSummary(parser.parse(text, now), text, hour, defaultReminder, labels, now.toLocalDate())

    private fun seg(text: String, isDefault: Boolean = false) = SummarySegment(text, isDefault)

    @Test
    fun `пустой ввод — все сегменты дефолтные`() {
        assertEquals(
            EventSummary(SummaryIcon.CALENDAR, listOf(seg("сегодня", true), seg("весь день", true))),
            summary(""),
        )
    }

    @Test
    fun `без даты и времени — дефолты при заголовке`() {
        assertEquals(
            EventSummary(SummaryIcon.CALENDAR, listOf(seg("сегодня", true), seg("весь день", true))),
            summary("купить корм коту"),
        )
    }

    @Test
    fun `завтра словом, время распознано`() {
        assertEquals(
            EventSummary(SummaryIcon.CALENDAR, listOf(seg("завтра"), seg("15:00–16:00"))),
            summary("стоматолог завтра в 15"),
        )
    }

    @Test
    fun `интервал двигает конец`() {
        assertEquals(
            EventSummary(SummaryIcon.CALENDAR, listOf(seg("завтра"), seg("15:00–17:00"))),
            summary("завтра с 15 до 17"),
        )
    }

    @Test
    fun `явная дата — форматом, all-day остаётся дефолтом времени`() {
        assertEquals(
            EventSummary(SummaryIcon.CALENDAR, listOf(seg("пн, 3 авг."), seg("весь день", true))),
            summary("день рождения 3 августа"),
        )
    }

    @Test
    fun `повтор — словами пользователя, иконка повтора`() {
        assertEquals(
            EventSummary(
                SummaryIcon.REPEAT,
                listOf(seg("сегодня"), seg("19:00–20:00"), seg("каждый вторник")),
            ),
            summary("йога каждый вторник в 19"),
        )
    }

    @Test
    fun `фраза-монстр — хвост повтора склеен с головой`() {
        assertEquals(
            EventSummary(
                SummaryIcon.REPEAT,
                listOf(
                    seg("сегодня"),
                    seg("15:00–17:00"),
                    seg("каждый вторник и четверг до конца сентября"),
                ),
            ),
            summary("встреча каждый вторник и четверг с 15 до 17 до конца сентября"),
        )
    }

    @Test
    fun `диапазон дат — оба конца`() {
        assertEquals(
            EventSummary(
                SummaryIcon.CALENDAR,
                listOf(seg("вс, 23 авг. – пт, 28 авг."), seg("весь день", true)),
            ),
            summary("с 23 по 28 августа"),
        )
    }

    // --- напоминания (задача 29): текст > дефолт, all-day — без сегмента ---

    @Test
    fun `напоминание из текста — обычным цветом`() {
        assertEquals(
            EventSummary(
                SummaryIcon.CALENDAR,
                listOf(seg("завтра"), seg("15:00–16:00"), seg("🔔 за 10 мин")),
            ),
            summary("стоматолог завтра в 15 !10"),
        )
    }

    @Test
    fun `напоминание из дефолта — приглушённым`() {
        assertEquals(
            EventSummary(
                SummaryIcon.CALENDAR,
                listOf(seg("завтра"), seg("15:00–16:00"), seg("🔔 за 30 мин", true)),
            ),
            summary("стоматолог завтра в 15", defaultReminder = 30),
        )
    }

    @Test
    fun `текст побеждает дефолт`() {
        assertEquals(
            seg("🔔 за 10 мин"),
            summary("стоматолог завтра в 15 !10", defaultReminder = 30).segments.last(),
        )
    }

    @Test
    fun `all-day — без сегмента напоминания при любом входе`() {
        assertEquals(
            listOf(seg("сегодня", true), seg("весь день", true)),
            summary("купить корм", defaultReminder = 30).segments,
        )
        assertEquals(
            listOf(seg("сегодня", true), seg("весь день", true)),
            summary("купить корм !10").segments,
        )
    }

    @Test
    fun `ноль минут — в начале, полтора часа — обе единицы`() {
        assertEquals(seg("🔔 в начале"), summary("звонок завтра в 15 !0").segments.last())
        assertEquals(seg("🔔 за 1 ч 30 мин"), summary("звонок завтра в 15 !90").segments.last())
    }

    @Test
    fun `английский — свои форматы`() {
        assertEquals(
            EventSummary(SummaryIcon.CALENDAR, listOf(seg("tomorrow"), seg("11:00 AM–12:30 PM"))),
            summary("movie tomorrow at 11 for an hour and a half", en),
        )
    }

    @Test
    fun `напоминание рядом с повтором — перед повтором, иконка повтора`() {
        // 🔔 раньше повтора: длинный повтор гибнет под многоточием, не колокольчик
        assertEquals(
            EventSummary(
                SummaryIcon.REPEAT,
                listOf(
                    seg("сегодня"), seg("19:00–20:00"),
                    seg("🔔 за 10 мин"), seg("каждый вторник"),
                ),
            ),
            summary("йога каждый вторник в 19 !10"),
        )
    }
}
