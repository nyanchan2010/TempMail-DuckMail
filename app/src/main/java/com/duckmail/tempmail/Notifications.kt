package com.duckmail.tempmail

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {
    const val CHANNEL_MAIL = "new_mail"
    const val CHANNEL_WATCH = "watching"
    const val NOTIF_WATCH_ID = 1001

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val mail = NotificationChannel(CHANNEL_MAIL, "New mail", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Alerts when a new message arrives"
        }
        val watch = NotificationChannel(CHANNEL_WATCH, "Watching in background", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Ongoing notification while background watching is on"
            setShowBadge(false)
        }
        nm.createNotificationChannel(mail)
        nm.createNotificationChannel(watch)
    }

    fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun showNewMail(context: Context, mailbox: Mailbox, message: EmailMessage, hideOtp: Boolean) {
        createChannels(context)
        val otp = message.otp
        val title = if (otp != null && !hideOtp) "Verification code: $otp" else "New mail from ${message.fromName}"
        val text = if (otp != null && !hideOtp) {
            "${message.subject} — tap to open, or copy the code"
        } else if (otp != null) {
            "New verification code — open the app to view it"
        } else {
            message.subject
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_MAIL)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .setGroup("tempmail_${mailbox.id}")
        if (otp != null) {
            val copyIntent = Intent(context, CopyOtpReceiver::class.java).apply {
                putExtra("otp", otp)
            }
            val copyPending = PendingIntent.getBroadcast(
                context, otp.hashCode(), copyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(android.R.drawable.ic_menu_save, "Copy code", copyPending)
        }
        try {
            NotificationManagerCompat.from(context).notify(message.id.hashCode(), builder.build())
        } catch (_: SecurityException) {}
    }

    fun watchingNotification(context: Context, address: String?, unread: Int): android.app.Notification {
        createChannels(context)
        val stopIntent = Intent(context, MailWatchService::class.java).apply {
            action = MailWatchService.ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            context, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_WATCH)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("Watching ${address ?: "TempMail"}")
            .setContentText(if (unread > 0) "$unread unread" else "New mail will notify you here")
            .setContentIntent(openAppIntent(context))
            .setOngoing(true)
            .setShowWhen(false)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPending)
            .build()
    }
}

class CopyOtpReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val otp = intent.getStringExtra("otp") ?: return
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Verification code", otp))
            Toast.makeText(context, "Verification code copied", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }
}
