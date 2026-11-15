package com.flarehop.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneLabelTest {
    @Test
    fun aPhoneIdIsFourHexDigits() {
        assertEquals("1A2B", phoneLabel(0x1A2B))
    }

    @Test
    fun aSmallIdKeepsItsLeadingZeros() {
        assertEquals("00FF", phoneLabel(0x00FF))
    }
}
