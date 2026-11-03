package com.duckmail.tempmail

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service for background watching (user opt-in only).
 * Polls every 15s normally, 30s in power-save. Swiping the app away does not stop it.
 * WorkManager alone cannot do 15s polling — its minimum is 15 minutes — so this
 * service is the real background path, and the worker is only a watchdog/restart.
 */
class MailWatchService : Service() {

    companion object {
        const val ACTION_START = "com.duckmail.tempmail.START"
        const val ACTION_STOP = "com.duckmail.tempmail.STOP"

        fun start(context: Context) {
            val intent = Intent(context, MailWatchService::class.java).apply { action = ACTION_START }
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {}
        }

        fun stop(context: Context) {
            val intent = Intent(context, MailWatchService::class.java).apply { action = ACTION_STOP }
            try { context.startService(intent) } catch (_: Exception) {}
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watchJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val store = SecureStore(applicationContext)
        val loaded = store.loadState()
        val first = loaded.mailboxes.firstOrNull()
        val notif = NotificationHelper.watchingNotification(applicationContext, first?.address, 0)
        startForeground(NotificationHelper.NOTIF_WATCH_ID, notif)
        startWatching()
        return START_STICKY
    }

    private fun startWatching() {
        if (watchJob?.isActive == true) return
        watchJob = scope.launch {
            val store = SecureStore(applicationContext)
            val seen = mutableSetOf<String>()
            // Seed with what is already cached, so we never re-notify old mail.
            try {
                val initial = store.loadState()
                initial.messagesByMailbox.values.flatten().forEach { seen.add(it.id) }
            } catch (_: Exception) {}
            while (isActive) {
                try {
                    val loaded = store.loadState()
                    if (!loaded.settings.backgroundWatching) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                        break
                    }
                    val pm = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
                    val intervalMs = if (pm.isPowerSaveMode) 30_000L else 15_000L
                    val api = DuckMailApi(loaded.settings.apiKey)
                    val cache = loaded.messagesByMailbox.toMutableMap()
                    var totalUnread = 0
                    for (mailbox in loaded.mailboxes) {
                        try {
                            val fresh = api.fetchMessages(mailbox)
                            cache[mailbox.id] = fresh
                            totalUnread += fresh.count { !it.isRead }
                            for (m in fresh) {
                                if (m.id !in seen) {
                                    seen.add(m.id)
                                    NotificationHelper.showNewMail(applicationContext, mailbox, m, loaded.settings.hideOtpOnLockScreen)
                                }
                            }
                        } catch (_: Exception) {
                            // Keep watching other mailboxes; transient failure is not fatal.
                        }
                    }
                    store.saveState(loaded.mailboxes, cache, loaded.settings, loaded.activeId)
                    val first = loaded.mailboxes.firstOrNull()
                    val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    try {
                        nm.notify(
                            NotificationHelper.NOTIF_WATCH_ID,
                            NotificationHelper.watchingNotification(applicationContext, first?.address, totalUnread),
                        )
                    } catch (_: Exception) {}
                    delay(intervalMs)
                } catch (_: Exception) {
                    delay(15_000L)
                }
            }
        }
    }

    override fun onDestroy() {
        watchJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
