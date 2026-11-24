package com.z1pendant.link

/**
 * The machine's soft limits (machine coordinates), read from the firmware instead of assuming a Z1,
 * so a Carvera Air (X -302, Y -212, Z -121) gets its whole travel.
 *
 * `M211` with no S makes the firmware (stock Z1 and the community firmware, Robot.cpp) print one line:
 * `Soft endstops are Enabled,X min = -207.000,X max = -1.000,Y min = ...,Z max = -1.000` and, for an
 * axis that isn't homed, `,X axis is not homed`; a limit that's off reads `,X min is disabled`. The
 * mins are `soft_endstop.x_min`... from the config, the maxes are fixed at -1 in the firmware.
 * Firmware without that report: the mins from `config-get sd soft_endstop.*_min`, max -1.
 */
data class SoftLimits(
    /** False: the firmware has soft limits off (`soft_endstop.enable false` or `M211 S0`). */
    val enabled: Boolean,
    val min: Map<Char, Double?>,
    val max: Map<Char, Double?>,
    /** Where these came from, for the log: "M211", "config" or "built-in". */
    val source: String,
) {
    /** The X/Y box in machine coordinates, or null if either axis has no limit both ways. */
    val bed: Bed?
        get() {
            val x0 = min['X'] ?: return null; val x1 = max['X'] ?: return null
            val y0 = min['Y'] ?: return null; val y1 = max['Y'] ?: return null
            return if (x1 > x0 && y1 > y0) Bed(x0, x1, y0, y1) else null
        }

    fun describe(): String = (if (enabled) "" else "off in the firmware; ") +
        "XYZ".toList().joinToString(", ") { a -> "$a ${n(min[a])} to ${n(max[a])}" } + " ($source)"

    private fun n(d: Double?) = d?.let { com.z1pendant.util.Fmt.fixed(it, 1) } ?: "none"

    companion object {
        const val COMMAND = "M211"

        /** The Z1's own (its config.txt, and the -1 max the firmware uses). The fallback. */
        val Z1 = SoftLimits(
            enabled = true,
            min = mapOf('X' to -207.0, 'Y' to -206.0, 'Z' to -102.0),
            max = mapOf('X' to -1.0, 'Y' to -1.0, 'Z' to -1.0),
            source = "built-in",
        )

        /** Max on every axis when only the mins could be read: the firmware's fixed -1. */
        const val FIRMWARE_MAX = -1.0

        private val head = Regex("""Soft endstops are (Enabled|Disabled)""", RegexOption.IGNORE_CASE)
        private val num = """([-+]?\d+(?:\.\d*)?)"""

        /** The limits from an M211 reply line, or null for any other line. */
        fun parse(line: String): SoftLimits? {
            val h = head.find(line) ?: return null
            val min = HashMap<Char, Double?>(); val max = HashMap<Char, Double?>()
            for (a in "XYZ") {
                min[a] = Regex("""$a min = $num""").find(line)?.groupValues?.get(1)?.toDoubleOrNull()
                max[a] = Regex("""$a max = $num""").find(line)?.groupValues?.get(1)?.toDoubleOrNull()
            }
            // An axis it says nothing about (cut-off line): keep the Z1's rather than "no limit".
            for (a in "XYZ") {
                if (min[a] == null && !line.contains("$a min is disabled")) min[a] = Z1.min[a]
                if (max[a] == null && !line.contains("$a max is disabled")) max[a] = Z1.max[a]
            }
            return SoftLimits(h.groupValues[1].equals("Enabled", true), min, max, "M211")
        }

        /** From the config's mins and enable flag (any missing: the Z1's); max -1. */
        fun fromConfig(xMin: Double?, yMin: Double?, zMin: Double?, enabled: Boolean?): SoftLimits =
            SoftLimits(
                enabled = enabled ?: true,
                min = mapOf('X' to (xMin ?: Z1.min['X']), 'Y' to (yMin ?: Z1.min['Y']), 'Z' to (zMin ?: Z1.min['Z'])),
                max = mapOf('X' to FIRMWARE_MAX, 'Y' to FIRMWARE_MAX, 'Z' to FIRMWARE_MAX),
                source = if (xMin == null && yMin == null && zMin == null) "built-in" else "config",
            )
    }
}

/** The machine's X/Y travel (its soft limits), machine coordinates: Run's bed map and off-bed check. */
data class Bed(val x0: Double, val x1: Double, val y0: Double, val y1: Double) {
    val width get() = x1 - x0
    val height get() = y1 - y0

    companion object {
        val Z1 = Bed(-207.0, -1.0, -206.0, -1.0)
    }
}
