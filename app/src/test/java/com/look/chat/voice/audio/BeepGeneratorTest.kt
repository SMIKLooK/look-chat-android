package com.look.chat.voice.audio

import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit

/**
 * Логика сигнала «я работаю» на подставленных времени и звуке: тик каждую
 * секунду, сигнал — по истечении интервала, если canBeep разрешает.
 */
@RunWith(RobolectricTestRunner::class)
class BeepGeneratorTest {

    private var canBeepFlag = true
    private var interval = 2
    private var fakeNow = 1000L
    private val tones = mutableListOf<Long>()

    private lateinit var generator: BeepGenerator

    @Before
    fun setUp() {
        canBeepFlag = true
        interval = 2
        fakeNow = 1000L
        tones.clear()
        generator = BeepGenerator(
            canBeep = { canBeepFlag },
            intervalSec = { interval },
            playTone = { tones.add(fakeNow) },
            now = { fakeNow },
        )
    }

    private fun tick() {
        // секунда планировщика = секунда фейкового времени
        fakeNow += 1000
        shadowOf(Looper.getMainLooper()).idleFor(1000, TimeUnit.MILLISECONDS)
    }

    @Test
    fun `first beep sounds after the interval`() {
        generator.start()

        tick() // 1 с из 2 — тишина
        assertTrue(tones.isEmpty())

        tick() // 2 с — сигнал
        assertEquals(listOf(3000L), tones)
    }

    @Test
    fun `beep resets the countdown`() {
        generator.start()
        tick()
        tick() // сигнал на 2-й секунде
        assertEquals(1, tones.size)

        tick() // 3 с — рано
        assertEquals(1, tones.size)

        tick() // 4 с — снова сигнал
        assertEquals(2, tones.size)
    }

    @Test
    fun `zero interval silences the signal`() {
        interval = 0
        generator.start()

        repeat(5) { tick() }

        assertTrue(tones.isEmpty())
    }

    @Test
    fun `canBeep false silences the signal`() {
        canBeepFlag = false
        generator.start()

        repeat(5) { tick() }

        assertTrue(tones.isEmpty())
    }

    @Test
    fun `interval change applies without restart`() {
        generator.start()
        tick()
        tick() // сигнал при интервале 2
        assertEquals(1, tones.size)

        interval = 10
        repeat(4) { tick() } // 4 с из 10 — тишина

        assertEquals(1, tones.size)
    }

    @Test
    fun `stop stops the ticking`() {
        generator.start()
        generator.stop()

        repeat(5) { tick() }

        assertTrue(tones.isEmpty())
    }
}
