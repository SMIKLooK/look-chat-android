package com.look.chat.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.look.chat.data.model.ChatMessage
import com.look.chat.data.model.ModelsState
import com.look.chat.ui.theme.LookChatTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreenContent(
    state: ChatScreenUiState,
    onInputChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    onModelPicked: (String) -> Unit,
    onSaveSettings: (url: String, wakeWord: String, endWord: String, skipChars: String, beepSec: Int) -> Unit,
    onSaveCustomWord: (word: String, model: String) -> Unit,
    onRemoveCustomWord: (String) -> Unit,
    onStopSpeaking: () -> Unit,
    onStartAssistant: () -> Unit,
    onStopAssistant: () -> Unit,
    onMicPermissionDenied: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    var showSettings by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.RECORD_AUDIO] == true) {
            onStartAssistant()
        } else {
            onMicPermissionDenied()
        }
    }

    fun toggleAssistant() {
        if (state.assistantActive) {
            onStopAssistant()
            return
        }
        val needed = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isEmpty()) {
            onStartAssistant()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    if (showSettings) {
        SettingsDialog(
            currentUrl = state.serverUrl,
            currentWakeWord = state.wakeWord,
            currentEndWord = state.endWord,
            currentSkipChars = state.ttsSkipChars,
            currentBeepSec = state.beepSec,
            onSave = { url, word, end, skip, beep ->
                onSaveSettings(url, word, end, skip, beep)
                showSettings = false
            },
            onDismiss = { showSettings = false },
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(modifier = Modifier.imePadding()) {
                    ModelsTabContent(
                        models = state.models,
                        customWords = state.customWords,
                        onSaveWord = onSaveCustomWord,
                        onRemoveWord = onRemoveCustomWord,
                        onPick = { model ->
                            onModelPicked(model)
                            scope.launch { drawerState.close() }
                        },
                    )
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Список ИИ")
                        }
                    },
                    title = {
                        Column {
                            Text("Look Chat")
                            Text(
                                text = state.serverUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { toggleAssistant() }) {
                            Icon(
                                imageVector = Icons.Filled.Mic,
                                contentDescription = if (state.assistantActive) {
                                    "Выключить голосового ассистента"
                                } else {
                                    "Включить голосового ассистента"
                                },
                                tint = if (state.assistantActive) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Настройки")
                        }
                    },
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .imePadding(),
            ) {
                MessageList(
                    messages = state.messages,
                    loading = state.loading,
                    modifier = Modifier.weight(1f),
                )
                if (state.assistantActive) {
                    AssistantStatus(
                        loading = state.loading,
                        speaking = state.speaking,
                        dictating = state.dictating,
                        heard = state.heard,
                        wakeWord = state.wakeWord,
                        endWord = state.endWord,
                        onStopSpeaking = onStopSpeaking,
                    )
                }
                if (state.models.suggestions.isNotEmpty()) {
                    ModelSuggestions(
                        suggestions = state.models.suggestions,
                        onPick = onModelPicked,
                    )
                }
                InputRow(
                    input = state.input,
                    loading = state.loading,
                    onInputChange = onInputChange,
                    onSend = onSend,
                )
            }
        }
    }
}

@Composable
private fun MessageList(
    messages: List<ChatMessage>,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, loading) {
        val count = messages.size + if (loading) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(messages, key = { it.id }) { message ->
            MessageBubble(message)
        }
        if (loading) {
            item { TypingBubble() }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val clipboard = LocalClipboardManager.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(horizontalAlignment = if (message.fromUser) Alignment.End else Alignment.Start) {
            val container = when {
                message.isError -> MaterialTheme.colorScheme.errorContainer
                message.fromUser -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            val content = when {
                message.isError -> MaterialTheme.colorScheme.onErrorContainer
                message.fromUser -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Surface(
                color = container,
                contentColor = content,
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 1.dp,
            ) {
                Text(
                    text = if (message.voice) "🎙 ${message.text}" else message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { clipboard.setText(AnnotatedString(message.text)) },
                        ),
                )
            }
            if (message.model != null || message.elapsedMs != null) {
                val meta = buildString {
                    message.model?.let { append(it) }
                    if (message.provider != null && message.provider != message.model) {
                        append(" · ").append(message.provider)
                    }
                    message.elapsedMs?.let { append(" · ").append(it).append(" мс") }
                }
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 2.dp, start = 8.dp, end = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun TypingBubble() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 1.dp,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.size(8.dp))
                Text("Думаю…", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun AssistantStatus(
    loading: Boolean,
    speaking: Boolean,
    dictating: Boolean,
    heard: String,
    wakeWord: String,
    endWord: String,
    onStopSpeaking: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clickable(enabled = speaking) { onStopSpeaking() },
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = when {
                    speaking -> "🔊 Отвечаю голосом…"
                    loading -> "Думаю…"
                    dictating -> if (endWord.isEmpty()) {
                        "🎙 Диктую… пауза отправит запрос"
                    } else {
                        "🎙 Диктую… закончите словом «$endWord»"
                    }
                    else -> "🎙 Слушаю… Скажите: «$wakeWord <модель> <запрос>»"
                },
                style = MaterialTheme.typography.labelMedium,
            )
            if (speaking) {
                Text(
                    text = "Нажмите, чтобы остановить озвучку",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                )
            } else if (heard.isNotBlank() && !loading) {
                Text(
                    text = "«$heard»",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ModelSuggestions(
    suggestions: List<String>,
    onPick: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        items(suggestions) { model ->
            AssistChip(
                onClick = { onPick(model) },
                label = { Text(model) },
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InputRow(
    input: TextFieldValue,
    loading: Boolean,
    onInputChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Спросите что угодно…") },
            shape = RoundedCornerShape(24.dp),
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
                onSend = {
                    if (input.text.isNotBlank() && !loading) onSend()
                }
            ),
        )
        Spacer(Modifier.size(8.dp))
        FilledIconButton(
            onClick = onSend,
            enabled = input.text.isNotBlank() && !loading,
            modifier = Modifier.padding(bottom = 4.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить")
        }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun ChatScreenPreview() {
    LookChatTheme {
        ChatScreenContent(
            state = ChatScreenUiState(
                messages = listOf(
                    ChatMessage(
                        id = 0,
                        fromUser = false,
                        text = "Привет! Тут два режима. Руками: пишите просто вопрос — " +
                            "модель подставится сама. Голосом: нажмите 🎙 и скажите кодовое слово.",
                    ),
                    ChatMessage(id = 1, fromUser = true, text = "привет, кто ты?"),
                    ChatMessage(
                        id = 2,
                        fromUser = false,
                        text = "Я Look — голосовой ассистент. Спрашивай что угодно.",
                        model = "free-x",
                        provider = "openrouter",
                        elapsedMs = 350,
                    ),
                    ChatMessage(id = 3, fromUser = true, text = "а погода какая?", voice = true),
                ),
                models = ModelsState(
                    suggestions = listOf("deepseek", "gemini", "фри", "гигачат"),
                ),
                serverUrl = "http://192.168.0.16:8080",
                assistantActive = true,
                speaking = true,
                wakeWord = "старт",
                endWord = "стоп",
                heard = "старт расскажи анекдот",
            ),
            onInputChange = {},
            onSend = {},
            onModelPicked = {},
            onSaveSettings = { _, _, _, _, _ -> },
            onSaveCustomWord = { _, _ -> },
            onRemoveCustomWord = {},
            onStopSpeaking = {},
            onStartAssistant = {},
            onStopAssistant = {},
            onMicPermissionDenied = {},
        )
    }
}
