package com.look.chat.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.look.chat.data.Settings

@Composable
fun SettingsDialog(
    currentUrl: String,
    currentWakeWord: String,
    currentEndWord: String,
    currentSkipChars: String,
    currentBeepSec: Int,
    onSave: (url: String, wakeWord: String, endWord: String, skipChars: String, beepSec: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var urlDraft by remember(currentUrl) { mutableStateOf(currentUrl) }
    var wordDraft by remember(currentWakeWord) { mutableStateOf(currentWakeWord) }
    var endDraft by remember(currentEndWord) { mutableStateOf(currentEndWord) }
    var skipDraft by remember(currentSkipChars) { mutableStateOf(currentSkipChars) }
    var beepDraft by remember(currentBeepSec) { mutableStateOf(currentBeepSec.toString()) }

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
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "На телефоне адрес этого компьютера уже вшит: ${Settings.PC_LAN_URL}\n" +
                        "Работает в той же Wi-Fi сети; если IP сменился — впишите новый.\n" +
                        "Для эмулятора по умолчанию: ${Settings.EMULATOR_URL}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(12.dp))
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
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    urlDraft.trim(),
                    wordDraft.trim(),
                    endDraft.trim(),
                    skipDraft,
                    beepDraft.trim().toIntOrNull() ?: currentBeepSec,
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
