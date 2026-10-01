package com.look.chat.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.look.chat.data.model.ModelsState

@Composable
fun ModelsTabContent(
    models: ModelsState,
    customWords: Map<String, String>,
    onSaveWord: (word: String, model: String) -> Unit,
    onRemoveWord: (String) -> Unit,
    onPick: (String) -> Unit,
) {
    var wordDraft by remember { mutableStateOf("") }
    var modelDraft by remember { mutableStateOf("") }
    val sortedCustomWords = remember(customWords) { customWords.toSortedMap() }

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
        if (models.suggestions.isEmpty()) {
            Text(
                "Список моделей пуст — ни один провайдер не подключён.\n" +
                    "Впиши API-ключи в ai/Keys.kt и пересобери приложение.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        models.suggestions.forEach { model ->
            NavigationDrawerItem(
                label = { Text(model) },
                selected = false,
                onClick = { onPick(model) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
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
        sortedCustomWords.forEach { (word, model) ->
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
            val trimmedWord = wordDraft.trim()
            val trimmedModel = modelDraft.trim()
            if (trimmedWord.isNotBlank() && trimmedModel.isNotBlank()) {
                onSaveWord(trimmedWord, trimmedModel)
                wordDraft = ""
                modelDraft = ""
            }
        }) {
            Text("Добавить слово")
        }
        Spacer(Modifier.size(16.dp))
    }
}
