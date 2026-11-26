package com.z1pendant

import com.z1pendant.ui.sideBySideLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutBreakpointTest {
    @Test fun sideBySideNeedsWidthLandscapeAndHeight() = listOf(
        Triple(1366f, 1024f, true), Triple(1080f, 810f, true), Triple(840f, 500f, true),   // tablets sideways, exact thresholds
        Triple(1024f, 1366f, false), Triple(393f, 852f, false),                             // tablet / phone upright
        Triple(874f, 402f, false),                                                          // phone sideways: wide but too short (clipped readout)
        Triple(839f, 500f, false), Triple(1200f, 499f, false),                              // 1 dp under each threshold
    ).forEach { (w, h, want) -> assertEquals("$w x $h", want, sideBySideLayout(w, h)) }
}
