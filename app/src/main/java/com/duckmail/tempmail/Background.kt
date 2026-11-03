package com.duckmail.tempmail

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        try {
            val loaded = SecureStore(context).loadState()
            if (loaded.settings.backgroundWatching && loaded.mailboxes.isNotEmpty()) {
                MailWatchService.start(context)
                WatchWorker.schedule(context)
            }
        } catch (_: Exception) {}
    }
}

/**
 * Watchdog only. Android enforces a 15-minute minimum for periodic work, so this
 * worker can NEVER be the fast polling path — it does one refresh, notifies on
 * genuinely new mail, and tries to restart the foreground service if watching
 * was left on. The foreground service is the fast path.
 */
class WatchWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            val store = SecureStore(applicationContext)
            val loaded = store.loadState()
            if (loaded.mailboxes.isEmpty()) return Result.success()
            val api = DuckMailApi(loaded.settings.apiKey)
            val cache = loaded.messagesByMailbox.toMutableMap()
            for (mailbox in loaded.mailboxes) {
                try {
                    val oldIds = (cache[mailbox.id] ?: emptyList()).map { it.id }.toSet()
                    val fresh = api.fetchMessages(mailbox)
                    cache[mailbox.id] = fresh
                    for (m in fresh) {
                        if (m.id !in oldIds) {
                            NotificationHelper.showNewMail(applicationContext, mailbox, m, loaded.settings.hideOtpOnLockScreen)
                        }
                    }
                } catch (_: Exception) {}
            }
            store.saveState(loaded.mailboxes, cache, loaded.settings, loaded.activeId)
            if (loaded.settings.backgroundWatching) {
                try { MailWatchService.start(applicationContext) } catch (_: Exception) {}
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val NAME = "tempmail_watchdog"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<WatchWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME, ExistingPeriodicWorkPolicy.KEEP, req,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
