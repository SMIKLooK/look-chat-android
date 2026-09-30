package com.look.chat.service

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
import com.look.chat.AssistantEngine
import com.look.chat.ui.MainActivity
import com.look.chat.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class AssistantService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var statusJob: Job? = null
    private var wakeLockJob: Job? = null

    private val notificationManager by lazy {
        getSystemService(NotificationManager::class.java)
    }

    private val contentPendingIntent by lazy {
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification(
            "Слушаю кодовое слово «${AssistantEngine.wakeWord.value}»",
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        AssistantEngine.onAssistantStarted(applicationContext)

        statusJob?.cancel()
        statusJob = serviceScope.launch {
            AssistantEngine.assistantStatus.collect { text ->
                if (text.isNotBlank()) refreshNotification(text)
            }
        }
        keepCpuAwake()
        return START_STICKY
    }

    override fun onDestroy() {
        if (AssistantEngine.assistantActive.value) {
            AssistantEngine.onAssistantStopped()
        }

        serviceScope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun keepCpuAwake() {
        acquireWakeLock()
        wakeLockJob?.cancel()
        wakeLockJob = serviceScope.launch {
            while (true) {
                delay(WAKELOCK_RENEW_MS)
                acquireWakeLock()
            }
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        releaseWakeLock()
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LookChat:assistant")
            .apply {
                setReferenceCounted(false)
                acquire(WAKELOCK_TIMEOUT_MS)
            }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        wakeLock = null
    }

    private fun refreshNotification(statusText: String) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    private fun buildNotification(statusText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle("Look Ассистент")
            .setContentText(statusText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(contentPendingIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "assistant"
        private const val NOTIFICATION_ID = 1

        private const val WAKELOCK_RENEW_MS = 60 * 60 * 1000L
        private const val WAKELOCK_TIMEOUT_MS = 2 * 60 * 60 * 1000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, AssistantService::class.java))
        }

        fun stop(context: Context) {
            AssistantEngine.onAssistantStopped()
            context.stopService(Intent(context, AssistantService::class.java))
        }

        private fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Голосовой ассистент",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Постоянное уведомление, пока ассистент слушает микрофон"
            }
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }
}
