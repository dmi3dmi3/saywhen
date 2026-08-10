package com.dmi3dmi3.saywhen.parser

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class TitleTest {

    private val parser = RussianEventParser()

    // вторник, 14:00
    private val now = ZonedDateTime.of(2026, 7, 21, 14, 0, 0, 0, ZoneId.of("Europe/Moscow"))

    private fun assertTitle(text: String, expected: String) {
        assertEquals(text, expected, parser.parse(text, now).title)
    }

    @Test
    fun `распознанное выкусывается`() {
        assertTitle("стоматолог завтра в 15", "стоматолог")
        assertTitle("завтра в 15 стоматолог", "стоматолог")
        assertTitle("йога каждый вторник в 19", "йога")
        assertTitle("обед с 13 до 14 в кафе", "обед в кафе")
        assertTitle("созвон по будням в 9 30 на полчаса", "созвон")
    }

    @Test
    fun `нераспознанное остаётся как есть`() {
        assertTitle("купить корм коту", "купить корм коту")
        assertTitle("Сегодня делаем ужин на вторник", "делаем ужин на вторник")
    }

    @Test
    fun `осиротевший предлог уходит со своим куском`() {
        assertTitle("каждый вторник с 3 августа", "Событие")
        assertTitle("перенести встречу на завтра", "перенести встречу")
    }

    @Test
    fun `предлог перед обычным словом остаётся`() {
        assertTitle("обед с Машей завтра в 13", "обед с Машей")
        assertTitle("пицца в духовке завтра", "пицца в духовке")
    }

    @Test
    fun `подсветка совпадает с выкусыванием — предлог расширяет матч`() {
        val e = parser.parse("перенести встречу на завтра", now)
        assertEquals(1, e.matches.size)
        assertEquals(18..26, e.matches[0].range)  // «на завтра»
        assertEquals(TokenMatch.Field.DATE, e.matches[0].field)
    }

    @Test
    fun `пунктуация на краях подчищается, в середине остаётся`() {
        assertTitle("ужин, завтра", "ужин")
        assertTitle("завтра: купить хлеб, молоко", "купить хлеб, молоко")
        assertTitle("встреча, завтра, обед", "встреча, обед")  // без «, ,» после выреза
    }

    @Test
    fun `эмодзи и не-BMP символы не ломают вырезание`() {
        assertTitle("🎉 вечеринка завтра в 19", "🎉 вечеринка")
    }

    @Test
    fun `пустой остаток — Событие`() {
        assertTitle("завтра в 15", "Событие")
        assertTitle("каждый день", "Событие")
        assertTitle("", "Событие")
    }

    @Test
    fun `регистр пользователя сохраняется`() {
        assertTitle("День Рождения Маши 3 августа", "День Рождения Маши")
    }
}
