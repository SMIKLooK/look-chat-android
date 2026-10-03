package com.look.chat.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.look.chat.data.Settings

@Composable
fun SettingsDialog(
    currentWakeWord: String,
    currentEndWord: String,
    currentSkipChars: String,
    currentBeepSec: Int,
    currentApiKeys: Map<String, String>,
    onSave: (
        wakeWord: String,
        endWord: String,
        skipChars: String,
        beepSec: Int,
        apiKeys: Map<String, String>,
    ) -> Unit,
    onDismiss: () -> Unit,
) {
    var wordDraft by remember(currentWakeWord) { mutableStateOf(currentWakeWord) }
    var endDraft by remember(currentEndWord) { mutableStateOf(currentEndWord) }
    var skipDraft by remember(currentSkipChars) { mutableStateOf(currentSkipChars) }
    var beepDraft by remember(currentBeepSec) { mutableStateOf(currentBeepSec.toString()) }
    var keyDrafts by remember(currentApiKeys) {
        mutableStateOf(Settings.API_KEY_PROVIDERS.keys.associateWith { currentApiKeys[it].orEmpty() })
    }
    var hiddenKeys by remember { mutableStateOf(Settings.API_KEY_PROVIDERS.keys) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройки") },
        text = {
            Column(
                modifier = Modifier
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = wordDraft,
                    onValueChange = { wordDraft = it },
                    singleLine = true,
                    label = { Text("Кодовое слово ассистента") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    "Слово, на которое просыпается микрофон. Нужно только для старта " +
                        "записи — в запрос не попадает.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(12.dp))
                OutlinedTextField(
                    value = endDraft,
                    onValueChange = { endDraft = it },
                    singleLine = true,
                    label = { Text("Слово окончания диктовки") },
                    modifier = Modifier.fillMaxWidth(),
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
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    "Эти символы вырезаются из ответа перед озвучкой. " +
                        "По умолчанию: *_#~` — можно дописать любые, например эмодзи.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(12.dp))
                OutlinedTextField(
                    value = beepDraft,
                    onValueChange = { beepDraft = it },
                    singleLine = true,
                    label = { Text("Сигнал «я работаю», сек") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    "Раз в сколько секунд подавать короткий сигнал, пока ассистент включён. " +
                        "0 — выключить. По умолчанию: ${Settings.DEFAULT_BEEP_INTERVAL_SEC}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.size(20.dp))
                Text(
                    "API-ключи провайдеров",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    "Свой ключ перекрывает встроенный. Очистите поле — вернётся встроенный. " +
                        "Ключ применяется сразу после «Сохранить».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(8.dp))
                Settings.API_KEY_PROVIDERS.entries.forEachIndexed { index, (id, label) ->
                    if (index > 0) Spacer(Modifier.size(8.dp))
                    ApiKeyField(
                        id = id,
                        label = label,
                        value = keyDrafts.getValue(id),
                        hidden = id in hiddenKeys,
                        onValueChange = { keyDrafts = keyDrafts + (id to it) },
                        onToggleHidden = {
                            hiddenKeys = if (id in hiddenKeys) hiddenKeys - id else hiddenKeys + id
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    wordDraft.trim(),
                    endDraft.trim(),
                    skipDraft,
                    beepDraft.trim().toIntOrNull() ?: currentBeepSec,
                    keyDrafts,
                )
            }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

@Composable
private fun ApiKeyField(
    id: String,
    label: String,
    value: String,
    hidden: Boolean,
    onValueChange: (String) -> Unit,
    onToggleHidden: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text("Ключ $label") },
        placeholder = { Text("встроенный ключ") },
        visualTransformation = if (hidden) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Ascii,
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
        ),
        trailingIcon = {
            IconButton(onClick = onToggleHidden) {
                Icon(
                    imageVector = if (hidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    contentDescription = if (hidden) {
                        "Показать ключ $label"
                    } else {
                        "Скрыть ключ $label"
                    },
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
