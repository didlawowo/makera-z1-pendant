package com.z1pendant

import android.os.SystemClock
import com.z1pendant.link.Bed
import com.z1pendant.link.ConnState
import com.z1pendant.link.MachineTarget
import com.z1pendant.platform.Platform
import com.z1pendant.platform.Prefs
import com.z1pendant.platform.PrefsEditor
import com.z1pendant.platform.TextFile
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * A Carvera Air on the simulator (z1sim.py --model air): the soft limits are read on connect, and the
 * jog clamp, typed moves and Run's bed use the Air's travel instead of a Z1's. Once with M211, once
 * with firmware that doesn't report it (the config's mins instead).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SoftLimitsSimTest {
    private var sim: Process? = null

    private fun start(port: Int, vararg extra: String) {
        val script = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "python/z1sim.py") }.firstOrNull { it.exists() }
        assumeTrue("z1sim.py not found", script != null)
        sim = try {
            ProcessBuilder(listOf("python", script!!.path, "--port", "$port", "--no-broadcast", "--model", "air") + extra)
                .redirectErrorStream(true).redirectOutput(File(System.getProperty("java.io.tmpdir"), "z1sim_soft_$port.log")).start()
        } catch (_: Exception) { null }
        assumeTrue("python not runnable", sim != null)
        val up = (1..50).any { runCatching { Socket("127.0.0.1", port).close() }.isSuccess || run { Thread.sleep(100); false } }
        assumeTrue("simulator didn't start", up)
    }

    @Before fun setUp() {
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } answers { System.nanoTime() / 1_000_000 }
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After fun tearDown() {
        sim?.destroy(); sim?.waitFor(5, TimeUnit.SECONDS)
        Dispatchers.resetMain()
        unmockkStatic(SystemClock::class)
    }

    private fun airTravel(port: Int, source: String) = runBlocking {
        val vm = AppViewModel(SoftFakePlatform())
        val link = vm.link
        vm.connect(MachineTarget("sim", "127.0.0.1", port))
        withTimeout(5000) { while (link.conn.value != ConnState.Connected) delay(20) }
        try { withTimeout(20_000) { while (vm.softLimits.value.source != source) delay(50) } }
        catch (e: Exception) { System.err.println(vm.console.value.takeLast(30).joinToString("\n")); throw e }

        val sl = vm.softLimits.value
        assertEquals(Bed(-302.0, -1.0, -212.0, -1.0), sl.bed)
        assertEquals(-121.0, sl.min['Z']!!, 1e-9)
        assertEquals(-302.0, vm.jog.config.limits['X']!!.softMin!!, 1e-9)
        assertEquals(-212.0, vm.jog.config.limits['Y']!!.softMin!!, 1e-9)
        assertEquals(3000.0, vm.jog.config.limits['X']!!.maxRateMmMin, 1e-9)     // rates untouched
        assertEquals(Bed(-302.0, -1.0, -212.0, -1.0), vm.run.bed())

        // Typed moves: past a Z1's -207 is fine on the Air, past the Air's -302 isn't.
        assertNull(vm.planMove('X', -250.0, relative = false).error)
        assertNotNull(vm.planMove('X', -310.0, relative = false).error)
        link.disconnect()
    }

    /** The report split into one message per piece, as a real Air sends it (the simulator's default). */
    @Test fun carveraAirFromM211() { start(22265); airTravel(22265, "M211") }

    @Test fun carveraAirFromM211OnOneLine() { start(22267, "--m211-one-line"); airTravel(22267, "M211") }

    @Test fun carveraAirFromTheConfig() { start(22266, "--no-m211"); airTravel(22266, "config") }
}

private class SoftFakePlatform : Platform {
    private val map = HashMap<String, Any?>()
    override val prefs = object : Prefs {
        override fun getBoolean(key: String, default: Boolean) = map[key] as? Boolean ?: default
        override fun getInt(key: String, default: Int) = map[key] as? Int ?: default
        override fun getLong(key: String, default: Long) = map[key] as? Long ?: default
        override fun getFloat(key: String, default: Float) = map[key] as? Float ?: default
        override fun getString(key: String, default: String?) = map[key] as? String ?: default
        override fun edit(block: PrefsEditor.() -> Unit) {
            object : PrefsEditor {
                override fun putBoolean(key: String, value: Boolean) { map[key] = value }
                override fun putInt(key: String, value: Int) { map[key] = value }
                override fun putLong(key: String, value: Long) { map[key] = value }
                override fun putFloat(key: String, value: Float) { map[key] = value }
                override fun putString(key: String, value: String) { map[key] = value }
            }.block()
        }
    }
    override val hotkeyFile = object : TextFile {
        var text: String? = null
        override fun exists() = text != null
        override fun readText() = text ?: ""
        override fun writeText(text: String) { this.text = text }
    }
    override val haptics = object : Haptics {
        override var strength = 0f
        override val description = "none"
        override fun tick() {}
        override fun majorTick() {}
        override fun thud() {}
        override fun limit() {}
        override fun press() {}
    }
}
