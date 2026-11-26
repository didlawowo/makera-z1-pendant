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
        // Only the first line of a split report: no numbers, so not a reading (0.1.11 took the Z1's here).
        assertNull(SoftLimits.parse("Soft endstops are Enabled"))
    }

    /** What a real Carvera Air sent (2026-11-25): one line per piece, then ok. */
    private val airLines = listOf("Soft endstops are Enabled", ",X min = -302.000", ",X max = -1.000",
        ",Y min = -212.000", ",Y max = -1.000", ",Z min = -121.000", ",Z max = -1.000", "ok")

    private fun collect(lines: List<String>): Pair<SoftLimits.Report, Int> {
        val r = SoftLimits.Report()
        val done = lines.indexOfFirst { r.add(it) }
        return r to done
    }

    @Test fun theAirsSplitReport() {
        val (r, done) = collect(listOf("<Idle|MPos:-1,-1,-1>", "something else") + airLines)
        assertEquals("complete at the ok", 9, done)
        val s = r.result()!!
        assertTrue(s.enabled)
        assertEquals(Bed(-302.0, -1.0, -212.0, -1.0), s.bed)
        assertEquals(-121.0, s.min['Z']!!, 1e-9)
        assertEquals(-1.0, s.max['Z']!!, 1e-9)
        assertEquals("M211", s.source)
    }

    @Test fun theOneLineReport() {
        val (r, done) = collect(listOf(air, "ok"))
        assertEquals(1, done)
        assertEquals(Bed(-302.0, -1.0, -212.0, -1.0), r.result()!!.bed)
    }

    @Test fun aSplitReportWithNotHomedAndOffLimits() {
        val (r, _) = collect(listOf("Soft endstops are Disabled", ",X min is disabled", ",X max = -1.000",
            ",X axis is not homed", ",Y min = -212.000", ",Y max = -1.000", ",Z min = -121.000", ",Z max is disabled", "ok"))
        val s = r.result()!!
        assertFalse(s.enabled)
        assertNull(s.min['X']); assertNull(s.max['Z'])
        assertEquals(-212.0, s.min['Y']!!, 1e-9)
    }

    @Test fun cutOffOrMissingReports() {
        // No ok came (timeout): what did arrive still counts.
        val (r, done) = collect(airLines.dropLast(3))
        assertEquals(-1, done)
        assertEquals(-302.0, r.result()!!.min['X']!!, 1e-9)
        // Only the first line, then ok: nothing to trust, so the app reads the config instead.
        assertNull(collect(listOf("Soft endstops are Enabled", "ok")).first.result())
        // Firmware without the report: just ok.
        assertNull(collect(listOf("ok")).first.result())
    }

    @Test fun fromTheConfig() {
        val s = SoftLimits.fromConfig(-302.0, -212.0, null, null)
        assertEquals("config", s.source)
        assertEquals(-102.0, s.min['Z']!!, 1e-9)                    // missing: the Z1's
        assertEquals(-1.0, s.max['X']!!, 1e-9)
        assertEquals("built-in", SoftLimits.fromConfig(null, null, null, null).source)
    }
}
