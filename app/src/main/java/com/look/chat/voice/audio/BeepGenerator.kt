package com.look.chat.voice.audio

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.util.Log

class BeepGenerator(
    private val canBeep: () -> Boolean,
    private val intervalSec: () -> Int,
    private val playTone: (() -> Unit)? = null,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val emitSound: () -> Unit = playTone ?: { beep() }

    private var tone: ToneGenerator? = null
    private var lastBeepAt = 0L

    @Volatile
    private var started = false

    // Проверка раз в секунду: intervalSec может меняться в настройках на ходу.
    private val tick = object : Runnable {
        override fun run() {
            if (!started) return
            maybeBeep()
            handler.postDelayed(this, 1000)
        }
    }

    // Запускает отсчёт; первый сигнал прозвучит через интервал.
    fun start() {
        started = true
        lastBeepAt = now()
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, 1000)
    }

    fun stop() {
        started = false
        handler.removeCallbacks(tick)
        try {
            tone?.release()
        } catch (e: Exception) {
            Log.w(TAG, "tone release", e)
        }
        tone = null
    }

    private fun maybeBeep() {
        val interval = intervalSec()
        if (interval <= 0 || !canBeep()) return
        if (now() - lastBeepAt >= interval * 1000L) {
            lastBeepAt = now()
            emitSound()
        }
    }

    private fun beep() {
        try {
            val generator = tone
                ?: ToneGenerator(AudioManager.STREAM_MUSIC, 100).also { tone = it }
            generator.startTone(ToneGenerator.TONE_PROP_BEEP2, 150)
        } catch (e: Exception) {
            Log.w(TAG, "beep", e)
        }
    }

    private companion object {
        const val TAG = "BeepGenerator"
    }
}
