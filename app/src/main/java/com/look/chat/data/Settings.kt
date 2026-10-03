package com.look.chat.data

import android.content.Context
import androidx.core.content.edit

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("look_chat", Context.MODE_PRIVATE)

    var wakeWord: String
        get() = prefs.getString(KEY_WORD, null)?.takeIf { it.isNotBlank() }
            ?.lowercase()?.trim() ?: DEFAULT_WAKE_WORD
        set(value) = prefs.edit { putString(KEY_WORD, value.lowercase().trim()) }

    var endWord: String
        get() = prefs.getString(KEY_END_WORD, null)?.lowercase()?.trim() ?: DEFAULT_END_WORD
        set(value) = prefs.edit { putString(KEY_END_WORD, value.lowercase().trim()) }

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

    var apiKeys: Map<String, String>
        get() = API_KEY_PROVIDERS.keys.mapNotNull { id ->
            prefs.getString(KEY_API_PREFIX + id, null)?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { id to it }
        }.toMap()
        set(value) = prefs.edit {
            API_KEY_PROVIDERS.keys.forEach { id ->
                val key = value[id]?.trim().orEmpty()
                if (key.isEmpty()) remove(KEY_API_PREFIX + id) else putString(KEY_API_PREFIX + id, key)
            }
        }

    companion object {
        const val DEFAULT_WAKE_WORD = "старт"
        const val DEFAULT_END_WORD = "стоп"

        const val DEFAULT_BEEP_INTERVAL_SEC = 300

        const val DEFAULT_TTS_SKIP_CHARS = "*_#~`"

        val API_KEY_PROVIDERS = mapOf(
            "gemini" to "Gemini",
            "openrouter" to "OpenRouter",
            "gptunnel" to "Gptunnel",
        )

        private const val KEY_WORD = "wake_word"
        private const val KEY_END_WORD = "end_word"
        private const val KEY_TTS_SKIP = "tts_skip_chars"
        private const val KEY_CUSTOM_WORDS = "custom_model_words"
        private const val KEY_BEEP = "beep_interval_sec"
        private const val KEY_API_PREFIX = "api_key_"
    }
}
