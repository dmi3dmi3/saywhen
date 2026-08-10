package com.dmi3dmi3.saywhen.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsTest {

    @Test
    fun `дефолты — авто-календарь, час, системная тема, отклики включены`() {
        val s = Settings()
        assertNull(s.calendarId)
        assertEquals(60, s.defaultDurationMinutes)
        assertEquals(ThemeMode.SYSTEM, s.theme)
        assertEquals(true, s.nodEnabled)
        assertEquals(true, s.hapticEnabled)
    }

    @Test
    fun `тема — round-trip ключа и фоллбэк на системную`() {
        for (mode in ThemeMode.entries) {
            assertEquals(mode, ThemeMode.fromKey(mode.key))
        }
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey("liquid-glass"))  // из будущей версии
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey(null))
    }

    @Test
    fun `подписи длительности`() {
        assertEquals("30 мин", durationLabel(30, "ч", "мин"))
        assertEquals("1 ч", durationLabel(60, "ч", "мин"))
        assertEquals("1 ч 30 мин", durationLabel(90, "ч", "мин"))
        assertEquals("2 ч", durationLabel(120, "ч", "мин"))
    }
}
