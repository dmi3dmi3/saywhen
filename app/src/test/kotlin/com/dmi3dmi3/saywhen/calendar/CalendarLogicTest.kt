package com.dmi3dmi3.saywhen.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration

class CalendarLogicTest {

    private fun cal(
        id: Long,
        primary: Boolean = false,
        google: Boolean = true,
        ownerMatches: Boolean = true,
    ) = CalendarInfo(
        id = id,
        name = "cal$id",
        accountName = "a@gmail.com",
        accountType = if (google) "com.google" else "LOCAL",
        ownerAccount = if (ownerMatches) "a@gmail.com" else "someone@else.org",
        isPrimary = primary,
    )

    @Test
    fun `primary google побеждает`() {
        val picked = pickDefaultCalendar(listOf(cal(1), cal(2, primary = true), cal(3)))
        assertEquals(2L, picked?.id)
    }

    @Test
    fun `google primary важнее локального primary`() {
        val picked = pickDefaultCalendar(
            listOf(cal(1, primary = true, google = false), cal(2, primary = true)),
        )
        assertEquals(2L, picked?.id)
    }

    @Test
    fun `IS_PRIMARY врёт — fallback на ownerAccount == accountName`() {
        val picked = pickDefaultCalendar(
            listOf(cal(1, ownerMatches = false), cal(2, ownerMatches = true)),
        )
        assertEquals(2L, picked?.id)
    }

    @Test
    fun `совсем ничего подходящего — первый из списка`() {
        val picked = pickDefaultCalendar(
            listOf(cal(7, ownerMatches = false), cal(8, ownerMatches = false)),
        )
        assertEquals(7L, picked?.id)
    }

    @Test
    fun `пустой список — null`() {
        assertNull(pickDefaultCalendar(emptyList()))
    }

    @Test
    fun `синкабельность — google да, локальный и безаккаунтный нет`() {
        assertEquals(true, isSyncable(cal(1)))
        assertEquals(false, isSyncable(cal(2, google = false)))  // ACCOUNT_TYPE_LOCAL
        assertEquals(false, isSyncable(cal(3).copy(accountName = "")))
    }

    @Test
    fun `rfc5545 длительности`() {
        assertEquals("PT1H", rfc5545Duration(Duration.ofHours(1)))
        assertEquals("PT30M", rfc5545Duration(Duration.ofMinutes(30)))
        assertEquals("PT1H30M", rfc5545Duration(Duration.ofMinutes(90)))
    }
}
