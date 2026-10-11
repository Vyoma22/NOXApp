package com.example.noxapp.auth

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class GradDateTest {

    private fun inToronto(gradMonth: Int, gradYear: Int): Calendar =
        Calendar.getInstance(TimeZone.getTimeZone("America/Toronto")).apply {
            time = GradDate.accessExpiresAt(gradMonth, gradYear)
        }

    @Test fun aprilGraduateLocksAtStartOfMay() {
        val c = inToronto(4, 2027)
        assertEquals(2027, c.get(Calendar.YEAR))
        assertEquals(Calendar.MAY, c.get(Calendar.MONTH))
        assertEquals(1, c.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, c.get(Calendar.HOUR_OF_DAY))
    }

    @Test fun decemberGraduateLocksInJanuaryOfNextYear() {
        val c = inToronto(12, 2027)
        assertEquals(2028, c.get(Calendar.YEAR))
        assertEquals(Calendar.JANUARY, c.get(Calendar.MONTH))
    }
}