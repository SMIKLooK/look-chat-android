package com.look.chat.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.look.chat.AssistantEngine
import com.look.chat.AssistantService
import com.look.chat.ChatMessage
import com.look.chat.ModelsState
import kotlinx.coroutines.launch

/** Главный экран: чат, подсказки моделей, поле ввода и голосовой ассистент. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen() {
    val context = LocalContext.current
    val messages by AssistantEngine.messages.collectAsStateWithLifecycle()
    val input by AssistantEngine.input.collectAsStateWithLifecycle()
    val loading by AssistantEngine.loading.collectAsStateWithLifecycle()
    val models by AssistantEngine.models.collectAsStateWithLifecycle()
    val serverUrl by AssistantEngine.serverUrl.collectAsStateWithLifecycle()
    val assistantActive by AssistantEngine.assistantActive.collectAsStateWithLifecycle()
    val speaking by AssistantEngine.speaking.collectAsStateWithLifecycle()
    val heard by AssistantEngine.heard.collectAsStateWithLifecycle()
    val wakeWord by AssistantEngine.wakeWord.collectAsStateWithLifecycle()
    val endWord by AssistantEngine.endWord.collectAsStateWithLifecycle()
    val dictating by AssistantEngine.dictating.collectAsStateWithLifecycle()
    val ttsSkipChars by AssistantEngine.ttsSkipChars.collectAsStateWithLifecycle()
    val customWords by AssistantEngine.customModelWords.collectAsStateWithLifecycle()

    var showSettings by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var drawerTab by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.RECORD_AUDIO] == true) {
            AssistantService.start(context)
        } else {
            AssistantEngine.onMicPermissionDenied()
        }
    }

    fun toggleAssistant() {
        if (assistantActive) {
            AssistantService.stop(context)
            return
        }
        val needed = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) {
            AssistantService.start(context)
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    if (showSettings) {
        SettingsDialog(
            currentUrl = serverUrl,
            currentWakeWord = wakeWord,
            currentEndWord = endWord,
            currentSkipChars = ttsSkipChars,
            onSave = { url, word, end, skip ->
                AssistantEngine.saveSettings(url, word, end, skip)
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
                    TabRow(selectedTabIndex = drawerTab) {
                        Tab(
                            selected = drawerTab == 0,
                            onClick = { drawerTab = 0 },
                            text = { Text("История") },
                        )
                        Tab(
                            selected = drawerTab == 1,
                            onClick = { drawerTab = 1 },
                            text = { Text("Модели") },
                        )
                    }
                    when (drawerTab) {
                        0 -> HistoryTabContent(
                            messages = messages,
                            onClear = AssistantEngine::clearHistory,
                        )
                        else -> ModelsTabContent(
                            models = models,
                            customWords = customWords,
                            onSaveWord = AssistantEngine::saveCustomWord,
                            onRemoveWord = AssistantEngine::removeCustomWord,
                            onPick = { model ->
                                AssistantEngine.onModelPicked(model)
                                scope.launch { drawerState.close() }
                            },
                        )
                    }
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
                            serverUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { toggleAssistant() }) {
                        Icon(
                            Icons.Filled.Mic,
                            contentDescription = if (assistantActive) {
                                "Выключить голосового ассистента"
                            } else {
                                "Включить голосового ассистента"
                            },
                            tint = if (assistantActive) {
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
                messages = messages,
                loading = loading,
                modifier = Modifier.weight(1f),
            )
            if (assistantActive) {
                AssistantStatus(
                    loading = loading,
                    speaking = speaking,
                    dictating = dictating,
                    heard = heard,
                    wakeWord = wakeWord,
                    endWord = endWord,
                    onStopSpeaking = AssistantEngine::stopSpeaking,
                )
            }
            if (models.suggestions.isNotEmpty()) {
                ModelSuggestions(
                    suggestions = models.suggestions,
                    onPick = AssistantEngine::onModelPicked,
                )
            }
            InputRow(
                input = input,
                loading = loading,
                onInputChange = AssistantEngine::onInputChange,
                onSend = AssistantEngine::send,
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

    // Сообщения пользователя — справа, ответы ассистента — слева.
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
                    // Голосовые сообщения помечаем микрофоном.
                    text = if (message.voice) "🎙 ${message.text}" else message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        // Долгое нажатие копирует текст сообщения в буфер обмена.
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

/** Строка статуса ассистента: слушает / диктует / думает / отвечает.
 *  Пока идёт озвучка, тап по строке останавливает голос. */
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

/** Горизонтальная лента подсказок: модели и псевдонимы с бекенда. */
@Composable
private fun ModelSuggestions(
    suggestions: List<String>,
    onPick: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
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
            onValueChange = AssistantEngine::onInputChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Спросите что угодно…") },
            shape = RoundedCornerShape(24.dp),
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
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

/** Вкладка «История»: переписка текущей сессии и очистка истории. */
@Composable
private fun HistoryTabContent(
    messages: List<ChatMessage>,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        TextButton(onClick = onClear, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Text("Очистить историю (и на сервере)")
        }
        HorizontalDivider()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(messages) { message ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = when {
                            message.fromUser -> "Вы"
                            message.isError -> "Ошибка"
                            else -> "ИИ"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

/** Вкладка «Модели»: провайдеры, псевдонимы и свои слова для моделей. */
@Composable
private fun ModelsTabContent(
    models: ModelsState,
    customWords: Map<String, String>,
    onSaveWord: (word: String, model: String) -> Unit,
    onRemoveWord: (String) -> Unit,
    onPick: (String) -> Unit,
) {
    var wordDraft by remember { mutableStateOf("") }
    var modelDraft by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .imePadding(),
    ) {
        Text(
            "Доступные ИИ",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp),
        )
        if (models.providers.isEmpty()) {
            Text(
                "Сервер не ответил — список моделей пуст.\nПроверьте адрес сервера (⚙).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        models.providers.forEach { provider ->
            Text(
                text = provider.name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
            )
            provider.models.forEach { model ->
                NavigationDrawerItem(
                    label = { Text(model) },
                    selected = false,
                    onClick = { onPick(model) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }
        }
        if (models.aliases.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                "Псевдонимы (короткие имена)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
            )
            models.aliases.toSortedMap().forEach { (alias, target) ->
                NavigationDrawerItem(
                    label = {
                        Column {
                            Text(alias)
                            Text(
                                target,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    selected = false,
                    onClick = { onPick(alias) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text(
            "Свои слова для моделей",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
        )
        Text(
            "Слово, которое надо сказать голосом (или написать), " +
                "чтобы запрос ушёл к этой модели.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
        )
        customWords.toSortedMap().forEach { (word, model) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(word, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "→ $model",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onRemoveWord(word) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Удалить слово $word")
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        ) {
            OutlinedTextField(
                value = wordDraft,
                onValueChange = { wordDraft = it },
                singleLine = true,
                label = { Text("Слово") },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(8.dp))
            OutlinedTextField(
                value = modelDraft,
                onValueChange = { modelDraft = it },
                singleLine = true,
                label = { Text("Модель") },
                modifier = Modifier.weight(1f),
            )
        }
        TextButton(onClick = {
            if (wordDraft.isNotBlank() && modelDraft.isNotBlank()) {
                onSaveWord(wordDraft, modelDraft)
                wordDraft = ""
                modelDraft = ""
            }
        }) {
            Text("Добавить слово")
        }
        Spacer(Modifier.size(16.dp))
    }
}

@Composable
private fun SettingsDialog(
    currentUrl: String,
    currentWakeWord: String,
    currentEndWord: String,
    currentSkipChars: String,
    onSave: (url: String, wakeWord: String, endWord: String, skipChars: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var urlDraft by remember(currentUrl) { mutableStateOf(currentUrl) }
    var wordDraft by remember(currentWakeWord) { mutableStateOf(currentWakeWord) }
    var endDraft by remember(currentEndWord) { mutableStateOf(currentEndWord) }
    var skipDraft by remember(currentSkipChars) { mutableStateOf(currentSkipChars) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройки") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = urlDraft,
                    onValueChange = { urlDraft = it },
                    singleLine = true,
                    label = { Text("Адрес сервера") },
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "На телефоне адрес этого компьютера уже вшит: http://192.168.0.16:8080\n" +
                        "Работает в той же Wi-Fi сети (WiFI-k3022898); если IP сменился — впишите новый.\n" +
                        "Для эмулятора по умолчанию: http://10.0.2.2:8080",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(12.dp))
                OutlinedTextField(
                    value = wordDraft,
                    onValueChange = { wordDraft = it },
                    singleLine = true,
                    label = { Text("Кодовое слово ассистента") },
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    "Слово, на которое просыпается микрофон. Нужно только для старта " +
                        "записи — на бекенд не отправляется.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(12.dp))
                OutlinedTextField(
                    value = endDraft,
                    onValueChange = { endDraft = it },
                    singleLine = true,
                    label = { Text("Слово окончания диктовки") },
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    "Запрос голосом отправится, когда вы скажете это слово. " +
                        "По умолчанию: стоп. Пусто — отправлять после паузы.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(12.dp))
                OutlinedTextField(
                    value = skipDraft,
                    onValueChange = { skipDraft = it },
                    singleLine = true,
                    label = { Text("Символы, которые не озвучивать") },
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    "Эти символы вырезаются из ответа перед озвучкой. " +
                        "По умолчанию: *_#~` — можно дописать любые, например эмодзи.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(urlDraft, wordDraft, endDraft, skipDraft) }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}
