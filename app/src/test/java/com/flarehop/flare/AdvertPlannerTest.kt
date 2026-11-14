package com.flarehop.flare

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvertPlannerTest {
    private val a = ByteArray(24) { 1 }
    private val b = ByteArray(24) { 2 }

    @Test
    fun stoppedWithNothingToSendWaits() {
        assertEquals(AdvertAction.Wait, AdvertPlanner.decide(AdvertState.STOPPED, null, null))
    }

    @Test
    fun stoppedWithAPacketStarts() {
        val action = AdvertPlanner.decide(AdvertState.STOPPED, null, a)
        assertTrue(action is AdvertAction.Start)
        assertArrayEquals(a, (action as AdvertAction.Start).bytes)
    }

    @Test
    fun startingWaitsForTheCallback() {
        assertEquals(AdvertAction.Wait, AdvertPlanner.decide(AdvertState.STARTING, a, b))
    }

    @Test
    fun onWithNewBytesUpdates() {
        val action = AdvertPlanner.decide(AdvertState.ON, a, b)
        assertTrue(action is AdvertAction.Update)
        assertArrayEquals(b, (action as AdvertAction.Update).bytes)
    }

    @Test
    fun onWithTheSameBytesSkipsTheUpdate() {
        assertEquals(AdvertAction.Wait, AdvertPlanner.decide(AdvertState.ON, a, a.copyOf()))
    }

    @Test
    fun onWithNothingToSendPauses() {
        assertEquals(AdvertAction.Pause, AdvertPlanner.decide(AdvertState.ON, a, null))
    }

    @Test
    fun pausedWithAPacketResumes() {
        val action = AdvertPlanner.decide(AdvertState.PAUSED, null, b)
        assertTrue(action is AdvertAction.Resume)
        assertArrayEquals(b, (action as AdvertAction.Resume).bytes)
    }

    @Test
    fun pausedWithNothingToSendStaysPaused() {
        assertEquals(AdvertAction.Wait, AdvertPlanner.decide(AdvertState.PAUSED, null, null))
    }
}
