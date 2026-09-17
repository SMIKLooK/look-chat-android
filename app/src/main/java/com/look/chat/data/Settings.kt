package com.look.chat.data

import android.content.Context
import android.os.Build

/** Настройки приложения: сервер, кодовые слова ассистента, озвучка. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("look_chat", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString(KEY_URL, null)?.takeIf { it.isNotBlank() } ?: defaultUrl()
        set(value) = prefs.edit().putString(KEY_URL, value.trim().trimEnd('/')).apply()

    /** Слово, на которое просыпается голосовой ассистент. */
    var wakeWord: String
        get() = prefs.getString(KEY_WORD, null)?.takeIf { it.isNotBlank() }
            ?.lowercase()?.trim() ?: DEFAULT_WAKE_WORD
        set(value) = prefs.edit().putString(KEY_WORD, value.lowercase().trim()).apply()

    /**
     * Слово, которым завершается голосовой ввод (по умолчанию «стоп»).
     * Пустое значение = отправлять запрос после паузы, как раньше.
     */
    var endWord: String
        get() = prefs.getString(KEY_END_WORD, null)?.lowercase()?.trim() ?: DEFAULT_END_WORD
        set(value) = prefs.edit().putString(KEY_END_WORD, value.lowercase().trim()).apply()

    /** Символы, которые вырезаются из ответа перед озвучкой. */
    var ttsSkipChars: String
        get() = prefs.getString(KEY_TTS_SKIP, null) ?: DEFAULT_TTS_SKIP_CHARS
        set(value) = prefs.edit().putString(KEY_TTS_SKIP, value).apply()

    /**
     * Свои слова для моделей (настраиваются в боковом меню):
     * строка вида «слово=модель,слово2=модель2».
     */
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
        set(value) = prefs.edit()
            .putString(KEY_CUSTOM_WORDS, value.entries.joinToString(",") { "${it.key}=${it.value}" })
            .apply()

    /** Идентификатор сессии диалога на бекенде (история разговора). */
    var sessionId: String
        get() = prefs.getString(KEY_SESSION, null) ?: ""
        set(value) = prefs.edit().putString(KEY_SESSION, value.trim()).apply()

    companion object {
        // Адрес этого компьютера в Wi-Fi сети — вшит, чтобы на телефоне
        // работало сразу после установки. Если IP сменится (роутер раздаёт
        // адреса по DHCP), новый можно вписать в настройках приложения.
        const val PC_LAN_URL = "http://192.168.0.16:8080"

        // IP хост-машины с точки зрения Android-эмулятора.
        const val EMULATOR_URL = "http://10.0.2.2:8080"

        const val DEFAULT_WAKE_WORD = "лук"
        const val DEFAULT_END_WORD = "стоп"

        // Markdown-символы в ответах моделей звучат в TTS как мусор.
        const val DEFAULT_TTS_SKIP_CHARS = "*_#~`"

        fun defaultUrl(): String = if (isEmulator()) EMULATOR_URL else PC_LAN_URL

        private fun isEmulator(): Boolean =
            Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.contains("emulator") ||
                Build.MODEL.contains("Emulator") ||
                Build.PRODUCT.contains("sdk") ||
                Build.HARDWARE.contains("ranchu")

        private const val KEY_URL = "server_url"
        private const val KEY_WORD = "wake_word"
        private const val KEY_END_WORD = "end_word"
        private const val KEY_TTS_SKIP = "tts_skip_chars"
        private const val KEY_CUSTOM_WORDS = "custom_model_words"
        private const val KEY_SESSION = "session_id"
    }
}
