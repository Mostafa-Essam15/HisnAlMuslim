package com.hisn.muslim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {
    @Test fun qiblaFromCairoIsSouthEast() {
        val b = Qibla.bearing(30.0444, 31.2357)
        assertTrue("bearing=$b", b in 134.0..137.0)
    }

    @Test fun qiblaFromLondonIsSouthEast() {
        val b = Qibla.bearing(51.5074, -0.1278)
        assertTrue("bearing=$b", b in 115.0..125.0)
    }

    @Test fun khatmaPagesPerDayRoundsUp() {
        assertEquals(21, Khatma.pagesPerDay(30)) // 604/30 = 20.13 فتتقرّب لفوق
        assertEquals(604, Khatma.pagesPerDay(1))
        assertEquals(604, Khatma.pagesPerDay(0)) // حماية من القسمة على صفر
    }

    @Test fun occasionsOnlyAgreedOnes() {
        assertNotNull(Occasions.of(10, 1))
        assertNotNull(Occasions.of(12, 10))
        assertNull(Occasions.of(3, 12)) // لا مولد ولا احتفالات غير متفق عليها
    }
}
