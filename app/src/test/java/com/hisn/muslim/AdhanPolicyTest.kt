package com.hisn.muslim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdhanPolicyTest {
    private val play = AdhanPolicy.Decision.PLAY
    private val silent = AdhanPolicy.Decision.SKIP_SILENT
    private val late = AdhanPolicy.Decision.SKIP_LATE

    @Test
    fun normalRinger_plays() {
        assertEquals(play, AdhanPolicy.decide(false, ringerNormal = true, playInSilent = false, lateMs = 0))
        assertEquals(play, AdhanPolicy.decide(false, ringerNormal = true, playInSilent = true, lateMs = 0))
    }

    @Test
    fun silentRinger_withToggleOn_plays() {
        assertEquals(play, AdhanPolicy.decide(false, ringerNormal = false, playInSilent = true, lateMs = 0))
    }

    @Test
    fun silentRinger_withToggleOff_isSkipped() {
        assertEquals(silent, AdhanPolicy.decide(false, ringerNormal = false, playInSilent = false, lateMs = 0))
    }

    @Test
    fun lateDelivery_isSkipped_butExactBoundaryStillPlays() {
        val max = AdhanPolicy.MAX_LATE_MS
        assertEquals(play, AdhanPolicy.decide(false, true, true, max))
        assertEquals(late, AdhanPolicy.decide(false, true, true, max + 1))
    }

    @Test
    fun lateness_takesPriorityOverSilent() {
        assertEquals(late, AdhanPolicy.decide(false, ringerNormal = false, playInSilent = false, lateMs = AdhanPolicy.MAX_LATE_MS + 1))
    }

    @Test
    fun manualTest_ignoresLateness_butStillRespectsSilentChoice() {
        assertEquals(play, AdhanPolicy.decide(true, true, true, AdhanPolicy.MAX_LATE_MS * 10))
        assertEquals(silent, AdhanPolicy.decide(true, ringerNormal = false, playInSilent = false, lateMs = 0))
    }

    @Test
    fun duplicateDetection() {
        assertTrue(AdhanPolicy.isDuplicate("FAJR@1000", "FAJR@1000"))
        assertFalse(AdhanPolicy.isDuplicate("FAJR@1000", "DHUHR@2000"))
        assertFalse(AdhanPolicy.isDuplicate("FAJR@1000", "FAJR@1001"))
        assertFalse(AdhanPolicy.isDuplicate("", ""))
        assertFalse(AdhanPolicy.isDuplicate("FAJR@1000", ""))
    }
}
