package com.look.chat.data

import android.content.Context
import android.os.Build
import androidx.core.content.edit

/** Настройки приложения: сервер, кодовые слова ассистента, озвучка. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("look_chat", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString(KEY_URL, null)?.takeIf { it.isNotBlank() } ?: defaultUrl()
        set(value) = prefs.edit { putString(KEY_URL, value.trim().trimEnd('/')) }

    var wakeWord: String
        get() = prefs.getString(KEY_WORD, null)?.takeIf { it.isNotBlank() }
            ?.lowercase()?.trim() ?: DEFAULT_WAKE_WORD
        set(value) = prefs.edit { putString(KEY_WORD, value.lowercase().trim()) }

    var endWord: String
        get() = prefs.getString(KEY_END_WORD, null)?.lowercase()?.trim() ?: DEFAULT_END_WORD
        set(value) = prefs.edit { putString(KEY_END_WORD, value.lowercase().trim()) }

    /** Символы, которые вырезаются из ответа перед озвучкой. */
    var ttsSkipChars: String
        get() = prefs.getString(KEY_TTS_SKIP, null) ?: DEFAULT_TTS_SKIP_CHARS
        set(value) = prefs.edit { putString(KEY_TTS_SKIP, value) }

    var beepIntervalSec: Int
        get() = prefs.getString(KEY_BEEP, null)?.toIntOrNull() ?: DEFAULT_BEEP_INTERVAL_SEC
        set(value) = prefs.edit { putString(KEY_BEEP, value.coerceAtLeast(0).toString()) }

    var customModelWords: Map<String, String>
        get() = prefs.getString(KEY_CUSTOM_WORDS, null)
            ?.split(',')
            ?.mapNotNull { entry ->
                val parts = entry.split('=', limit = 2)
                val word = parts.getOrNull(0)?.trim()?.lowercase() ?: ""
                val model = parts.getOrNull(1)?.trim() ?: ""
                if (word.isNotEmpty() && model.isNotEmpty()) word to model else null
            }
            ?.toMap()
            ?: emptyMap()
        set(value) = prefs.edit {
            putString(KEY_CUSTOM_WORDS, value.entries.joinToString(",") { "${it.key}=${it.value}" })
        }

    companion object {
        const val PC_LAN_URL = "http://192.168.0.16:8080"

        const val EMULATOR_URL = "http://10.0.2.2:8080"

        const val DEFAULT_WAKE_WORD = "старт"
        const val DEFAULT_END_WORD = "стоп"

        const val DEFAULT_BEEP_INTERVAL_SEC = 300

        // Markdown-символы в ответах моделей звучат в TTS как мусор.
        const val DEFAULT_TTS_SKIP_CHARS = "*_#~`"

        fun defaultUrl(): String = if (isEmulator()) EMULATOR_URL else PC_LAN_URL

        private const val KEY_URL = "server_url"
        private const val KEY_WORD = "wake_word"
        private const val KEY_END_WORD = "end_word"
        private const val KEY_TTS_SKIP = "tts_skip_chars"
        private const val KEY_CUSTOM_WORDS = "custom_model_words"
        private const val KEY_BEEP = "beep_interval_sec"
    }
}
private fun isEmulator(): Boolean =
    Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.contains("emulator") ||
            Build.MODEL.contains("Emulator") ||
            Build.PRODUCT.contains("sdk") ||
            Build.HARDWARE.contains("ranchu")
