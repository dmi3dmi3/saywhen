package com.dmi3dmi3.saywhen.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.Period

/**
 * Арбитраж кандидатов нескольких трансляторов (задача 16). Языки не нужны:
 * Extraction собираются руками — арбитраж видит только скоры, покрытие и
 * токены, в этом и смысл инкапсуляции.
 */
class ArbitrationTest {

    private fun time(range: IntRange, hour: Int, conf: Confidence) =
        TimeCandidate(LocalTime.of(hour, 0), null, range, confidence = conf)

    private fun date(range: IntRange, day: Int, conf: Confidence = Confidence.EXPLICIT) =
        DateCandidate(LocalDate.of(2026, 7, day), range, confidence = conf)

    private fun rec(range: IntRange) =
        RecurrenceCandidate("FREQ=DAILY", range, period = Period.ofDays(1))

    private fun merge(vararg e: Extraction) = Arbitration.merge(e.toList())

    @Test
    fun `поле есть только у одного — берётся оно`() {
        val merged = merge(
            Extraction(time = time(0..0, 10, Confidence.EXPLICIT)),
            Extraction(date = date(1..1, 25)),
        )
        assertEquals(10, merged.time!!.time.hour)
        assertEquals(25, merged.date!!.date.dayOfMonth)
    }

    @Test
    fun `выше скор побеждает даже при меньшем покрытии`() {
        val merged = merge(
            // покрытие 1 токен, но EXPLICIT
            Extraction(time = time(0..0, 10, Confidence.EXPLICIT)),
            // покрытие 3 токена, но STRONG
            Extraction(time = time(1..3, 11, Confidence.STRONG)),
        )
        assertEquals(10, merged.time!!.time.hour)
    }

    @Test
    fun `равные скоры — большее покрытие`() {
        val merged = merge(
            Extraction(time = time(0..0, 10, Confidence.EXPLICIT)),
            Extraction(time = time(1..2, 11, Confidence.EXPLICIT)),
        )
        assertEquals(11, merged.time!!.time.hour)
    }

    @Test
    fun `равные скоры и покрытие — порядок в списке`() {
        val merged = merge(
            Extraction(time = time(0..0, 10, Confidence.EXPLICIT)),
            Extraction(time = time(1..1, 11, Confidence.EXPLICIT)),
        )
        assertEquals(10, merged.time!!.time.hour)
    }

    @Test
    fun `пересечение токенов с принятым полем — кандидат отбрасывается`() {
        val merged = merge(
            // повтор занимает 0..1; время того же транслятора — на 2
            Extraction(recurrence = rec(0..1), time = time(2..2, 10, Confidence.STRONG)),
            // чужое время EXPLICIT, но лезет в токен 1, занятый повтором
            Extraction(time = time(1..1, 11, Confidence.EXPLICIT)),
        )
        assertEquals("FREQ=DAILY", merged.recurrence!!.rrule)
        assertEquals(10, merged.time!!.time.hour)  // EXPLICIT-кандидат выбит коллизией
    }

    @Test
    fun `гард — duration без времени сбрасывается`() {
        val merged = merge(
            Extraction(duration = DurationCandidate(java.time.Duration.ofHours(1), 0..0)),
        )
        assertNull(merged.duration)
    }

    @Test
    fun `гард — duration при интервальном времени сбрасывается`() {
        val interval = TimeCandidate(
            LocalTime.of(15, 0), java.time.Duration.ofHours(2), 1..2,
            confidence = Confidence.EXPLICIT,
        )
        val merged = merge(
            Extraction(time = interval),
            Extraction(duration = DurationCandidate(java.time.Duration.ofHours(1), 3..3)),
        )
        assertEquals(2, merged.time!!.duration!!.toHours())
        assertNull(merged.duration)  // «с 15 до 17» длительность уже принёс
    }

    @Test
    fun `пусто у всех — пусто в мердже`() {
        val merged = merge(Extraction(), Extraction())
        assertNull(merged.recurrence)
        assertNull(merged.date)
        assertNull(merged.time)
        assertNull(merged.duration)
    }
}
