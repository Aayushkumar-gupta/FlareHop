package com.flarehop.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AnglesTest {
    @Test
    fun anglesWrapIntoOneTurn() {
        assertEquals(350f, Angles.normalize(-10f), 0.001f)
        assertEquals(10f, Angles.normalize(370f), 0.001f)
    }

    @Test
    fun theShortestTurnCrossesNorth() {
        assertEquals(20f, Angles.shortestTurn(350f, 10f), 0.001f)
        assertEquals(-20f, Angles.shortestTurn(10f, 350f), 0.001f)
    }

    @Test
    fun smoothingMovesPartOfTheWayAcrossNorth() {
        assertEquals(353f, Angles.smooth(350f, 10f, weight = 0.15f), 0.001f)
        assertEquals(90f, Angles.smooth(null, 90f), 0.001f)
    }

    @Test
    fun theArrowTargetStaysNextToWhereItIs() {
        assertEquals(370f, Angles.unwrapNear(350f, 10f), 0.001f)
        assertEquals(-10f, Angles.unwrapNear(10f, 350f), 0.001f)
    }
}
