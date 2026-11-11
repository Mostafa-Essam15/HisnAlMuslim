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

    @Test fun arabicFoldIgnoresDiacriticsAndAlefForms() {
        assertEquals(Quran.fold("الْحَمْدُ لِلَّهِ"), Quran.fold("الحمد لله"))
        assertEquals(Quran.fold("إِيمَان"), Quran.fold("ايمان"))
        assertEquals(Quran.fold("رحمة"), Quran.fold("رحمه"))
    }

    @Test fun arabicDigitsConvert() {
        assertEquals("٦٠٤", Quran.arabicDigits(604))
    }

    @Test fun hadithGradeIsTranslatedAndUnknownKept() {
        assertEquals("الألباني: صحيح", HadithLib.gradeText("Al-Albani" to "Sahih"))
        assertEquals("Someone New: حسن", HadithLib.gradeText("Someone New" to "Hasan"))
        assertEquals("الألباني: Unlisted", HadithLib.gradeText("Al-Albani" to "Unlisted"))
    }

    @Test fun hadithKeyAndShareString() {
        val b = HBook("ara-abudawud", "سنن أبي داود", "أبو داود", true)
        val r = HRow("12", "نص", listOf("Al-Albani" to "Daif"))
        assertEquals("ara-abudawud:12", HadithLib.key(b, r))
        val s = HadithLib.shareString(b, r)
        assertTrue(s.startsWith("نص"))
        assertTrue(s.contains("سنن أبي داود، حديث رقم 12"))
        assertTrue(s.contains("الألباني: ضعيف"))
        assertEquals("نص\n\n— سنن أبي داود، حديث رقم 5", HadithLib.shareString(b, HRow("5", "نص", emptyList())))
    }

    @Test fun hadithRowsGroupedBySectionRange() {
        val b = HBook("x", "x", "x", false)
        val d = HData(b, listOf(HSec(1, "a", 1.0, 3.0), HSec(2, "b", 4.0, 6.0)),
            listOf(HRow("2", "t", emptyList()), HRow("5", "t", emptyList()), HRow("99", "t", emptyList())))
        assertEquals(1, d.bySec[1]?.size)
        assertEquals(1, d.bySec[2]?.size)
        assertNull(d.bySec[3])
    }

    @Test fun qiblaDistanceFromCairoAboutOnePointTwoThousandKm() {
        val d = Qibla.distanceKm(30.0444, 31.2357)
        assertTrue("distance=$d", d in 1200.0..1300.0)
        assertTrue(Qibla.distanceKm(21.4225, 39.8262) < 1.0) // عند الكعبة
    }

    @Test fun arabicDigitsKeepsMinusSign() {
        assertEquals("-٥", Quran.arabicDigits(-5))
        assertEquals("٠", Quran.arabicDigits(0))
    }

    @Test fun weatherCodesMapToArabicLabelsAndIcons() {
        assertEquals("صافٍ", Weather.label(0))
        assertEquals("مطر", Weather.label(63))
        assertEquals("عاصفة رعدية", Weather.label(95))
        assertEquals("—", Weather.label(1234))
        assertEquals("🌙", Weather.icon(0, day = false))
        assertEquals("☀️", Weather.icon(0, day = true))
    }

    @Test fun weatherDayNameFromIsoDate() {
        assertEquals("الأربعاء", Weather.dayName("2026-11-11"))
        assertEquals("bad", Weather.dayName("bad"))
    }
}
