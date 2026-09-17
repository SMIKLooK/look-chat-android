package com.look.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.look.chat.ui.ChatScreen
import com.look.chat.ui.theme.LookChatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AssistantEngine.ensureInit(this)
        enableEdgeToEdge()
        setContent {
            LookChatTheme {
                ChatScreen()
            }
        }
    }
}
