package com.look.chat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

/**
 * Foreground-сервис голосового ассистента: держит постоянное уведомление
 * с актуальным статусом и wakelock, чтобы микрофон слушал даже при
 * выключенном экране.
 */
class AssistantService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification(this, "Слушаю кодовое слово «${AssistantEngine.wakeWord.value}»")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        // Движок пушит статусы (слушаю / записываю / отвечаю) — уведомление
        // перерисовывается на каждое изменение состояния.
        AssistantEngine.assistantStatusListener = { text -> refreshNotification(text) }
        keepCpuAwake()
        AssistantEngine.onAssistantStarted(this)
        return START_STICKY
    }

    override fun onDestroy() {
        AssistantEngine.assistantStatusListener = null
        wakeLock?.release()
        wakeLock = null
        super.onDestroy()
    }

    /** Держит CPU включённым, пока ассистент слушает (иначе гашнущий экран
     *  усыпляет устройство и сессия распознавания умирает). */
    private fun keepCpuAwake() {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LookChat:assistant")
            .apply {
                setReferenceCounted(false)
                acquire(6 * 60 * 60 * 1000L) // страховка: не дольше 6 часов
            }
    }

    private fun refreshNotification(statusText: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(this, statusText))
    }

    private fun buildNotification(context: Context, statusText: String): Notification {
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle("Look Ассистент")
            .setContentText(statusText)
            .setOngoing(true)
            .setContentIntent(openApp)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "assistant"
        private const val NOTIFICATION_ID = 1

        /** Запускает сервис ассистента (нужны разрешения микрофона). */
        fun start(context: Context) {
            context.startForegroundService(Intent(context, AssistantService::class.java))
        }

        /** Останавливает ассистента. */
        fun stop(context: Context) {
            AssistantEngine.onAssistantStopped()
            context.stopService(Intent(context, AssistantService::class.java))
        }

        private fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Голосовой ассистент",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Постоянное уведомление, пока ассистент слушает микрофон"
                }
                context.getSystemService(NotificationManager::class.java)
                    .createNotificationChannel(channel)
            }
        }
    }
}
