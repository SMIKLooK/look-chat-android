package com.look.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.input.TextFieldValue
import com.look.chat.AssistantEngine
import com.look.chat.data.model.ChatMessage
import com.look.chat.data.model.ModelsState
import com.look.chat.service.AssistantService

data class ChatScreenUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: TextFieldValue = TextFieldValue(""),
    val loading: Boolean = false,
    val models: ModelsState = ModelsState(),
    val assistantActive: Boolean = false,
    val speaking: Boolean = false,
    val heard: String = "",
    val wakeWord: String = "",
    val endWord: String = "",
    val dictating: Boolean = false,
    val ttsSkipChars: String = "",
    val beepSec: Int = 0,
    val customWords: Map<String, String> = emptyMap(),
    val apiKeys: Map<String, String> = emptyMap(),
)

@Composable
fun ChatScreen() {
    val context = LocalContext.current

    val state = ChatScreenUiState(
        messages = AssistantEngine.messages.collectAsStateWithLifecycle().value,
        input = AssistantEngine.input.collectAsStateWithLifecycle().value,
        loading = AssistantEngine.loading.collectAsStateWithLifecycle().value,
        models = AssistantEngine.models.collectAsStateWithLifecycle().value,
        assistantActive = AssistantEngine.assistantActive.collectAsStateWithLifecycle().value,
        speaking = AssistantEngine.speaking.collectAsStateWithLifecycle().value,
        heard = AssistantEngine.heard.collectAsStateWithLifecycle().value,
        wakeWord = AssistantEngine.wakeWord.collectAsStateWithLifecycle().value,
        endWord = AssistantEngine.endWord.collectAsStateWithLifecycle().value,
        dictating = AssistantEngine.dictating.collectAsStateWithLifecycle().value,
        ttsSkipChars = AssistantEngine.ttsSkipChars.collectAsStateWithLifecycle().value,
        beepSec = AssistantEngine.beepIntervalSec.collectAsStateWithLifecycle().value,
        customWords = AssistantEngine.customModelWords.collectAsStateWithLifecycle().value,
        apiKeys = AssistantEngine.apiKeys.collectAsStateWithLifecycle().value,
    )

    ChatScreenContent(
        state = state,
        onInputChange = AssistantEngine::onInputChange,
        onSend = AssistantEngine::send,
        onCancelRequest = AssistantEngine::cancelRequest,
        onModelPicked = AssistantEngine::onModelPicked,
        onSaveSettings = AssistantEngine::saveSettings,
        onSaveApiKeys = AssistantEngine::saveApiKeys,
        onSaveCustomWord = AssistantEngine::saveCustomWord,
        onRemoveCustomWord = AssistantEngine::removeCustomWord,
        onStopSpeaking = AssistantEngine::stopSpeaking,
        onStartAssistant = { AssistantService.start(context) },
        onStopAssistant = { AssistantService.stop(context) },
        onMicPermissionDenied = AssistantEngine::onMicPermissionDenied,
    )
}
