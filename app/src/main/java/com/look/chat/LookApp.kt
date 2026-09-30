package com.look.chat

import android.app.Application

class LookApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AssistantEngine.ensureInit(this)
    }
}
