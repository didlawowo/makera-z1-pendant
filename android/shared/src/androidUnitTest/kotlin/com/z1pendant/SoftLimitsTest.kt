package com.z1pendant

import com.z1pendant.link.Bed
import com.z1pendant.link.SoftLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftLimitsTest {
    // The firmware's M211 report (community Robot.cpp; the same format strings are in the stock Z1 dump).
    private val z1 = "Soft endstops are Enabled,X min = -207.000,X max = -1.000,Y min = -206.000,Y max = -1.000," +
        "Z min = -102.000,Z max = -1.000"
    private val air = "Soft endstops are Enabled,X min = -302.000,X max = -1.000,Y min = -212.000,Y max = -1.000," +
        "Z min = -121.000,Z max = -1.000"

    @Test fun parsesTheZ1() {
        val s = SoftLimits.parse(z1)!!
        assertTrue(s.enabled)
        assertEquals(-207.0, s.min['X']!!, 1e-9); assertEquals(-206.0, s.min['Y']!!, 1e-9); assertEquals(-102.0, s.min['Z']!!, 1e-9)
        assertEquals(-1.0, s.max['Z']!!, 1e-9)
        assertEquals(Bed.Z1, s.bed)
        assertEquals("M211", s.source)
    }

    @Test fun parsesTheCarveraAir() {
        val s = SoftLimits.parse(air)!!
        assertEquals(Bed(-302.0, -1.0, -212.0, -1.0), s.bed)
        assertEquals(-121.0, s.min['Z']!!, 1e-9)
    }

    @Test fun disabledAndNotHomed() {
        val s = SoftLimits.parse("Soft endstops are Disabled,X min = -302.000,X max = -1.000,X axis is not homed," +
            "Y min = -212.000,Y max = -1.000,Y axis is not homed,Z min = -121.000,Z max = -1.000,Z axis is not homed")!!
        assertFalse(s.enabled)
        assertEquals(-302.0, s.min['X']!!, 1e-9)
    }

    @Test fun aLimitThatIsOff() {
        val s = SoftLimits.parse("Soft endstops are Enabled,X min is disabled,X max = -1.000,Y min = -212.000," +
            "Y max = -1.000,Z min = -121.000,Z max is disabled")!!
        assertNull(s.min['X']); assertNull(s.max['Z'])
        assertNull("no X min, so no box for the bed map", s.bed)
    }

    @Test fun otherLinesAreNotIt() {
        assertNull(SoftLimits.parse("ok"))
        assertNull(SoftLimits.parse("sd: soft_endstop.x_min is set to -302.0"))
    }

    @Test fun fromTheConfig() {
        val s = SoftLimits.fromConfig(-302.0, -212.0, null, null)
        assertEquals("config", s.source)
        assertEquals(-102.0, s.min['Z']!!, 1e-9)                    // missing: the Z1's
        assertEquals(-1.0, s.max['X']!!, 1e-9)
        assertEquals("built-in", SoftLimits.fromConfig(null, null, null, null).source)
    }
}
