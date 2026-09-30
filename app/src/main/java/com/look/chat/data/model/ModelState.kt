package com.look.chat.data.model

data class ModelsState(
    val keywords: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val serverModels: List<String> = emptyList(),
)
