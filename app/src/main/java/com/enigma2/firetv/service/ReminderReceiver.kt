package com.enigma2.firetv.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.enigma2.firetv.R
import com.enigma2.firetv.data.prefs.EpgReminder
import com.enigma2.firetv.data.prefs.ReceiverPreferences
import com.enigma2.firetv.data.prefs.RemindersStore
import com.enigma2.firetv.ui.player.PlayerActivity

/**
 * Fires when an EPG reminder's start time is reached. Posts a local
 * notification with **Watch now** and **Snooze 5 min** action buttons.
 *
 * Uses `setAndAllowWhileIdle` (inexact) so no `SCHEDULE_EXACT_ALARM`
 * permission is required on Android 12+ — tens-of-seconds slop is
 * acceptable for a "remind me at start" notification.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, 0)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val channel = intent.getStringExtra(EXTRA_CHANNEL).orEmpty()
        val sref = intent.getStringExtra(EXTRA_SREF).orEmpty()
        val notifId = NOTIF_BASE + (id and 0x7FFFFFFF) % 1000

        when (intent.action) {
            ACTION_SNOOZE -> {
                val newStart = (System.currentTimeMillis() / 1000) + SNOOZE_SECONDS
                val resched = EpgReminder(id, title, channel, sref, newStart)
                schedule(context, resched)
                runCatching { RemindersStore(context).add(resched) }
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .cancel(notifId)
                return
            }
            ACTION_WATCH -> {
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .cancel(notifId)
                launchPlayer(context, channel, sref)
                runCatching { RemindersStore(context).remove(id) }
                return
            }
        }

        ensureChannel(context)

        val watchIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_WATCH
            putExtra(EXTRA_ID, id); putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_CHANNEL, channel); putExtra(EXTRA_SREF, sref)
        }
        val snoozeIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_SNOOZE
            putExtra(EXTRA_ID, id); putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_CHANNEL, channel); putExtra(EXTRA_SREF, sref)
        }
        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val watchPi = PendingIntent.getBroadcast(context, id * 10 + 1, watchIntent, piFlags)
        val snoozePi = PendingIntent.getBroadcast(context, id * 10 + 2, snoozeIntent, piFlags)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_channel_placeholder)
            .setContentTitle(context.getString(R.string.reminder_notification_title, title))
            .setContentText(context.getString(R.string.reminder_notification_text, channel))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, context.getString(R.string.reminder_action_watch), watchPi)
            .addAction(0, context.getString(R.string.reminder_action_snooze), snoozePi)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notifId, notification)

        runCatching { RemindersStore(context).remove(id) }
    }

    private fun launchPlayer(context: Context, channelName: String, sref: String) {
        if (sref.isBlank()) return
        val prefs = ReceiverPreferences(context)
        val streamUrl = prefs.streamUrl(sref)
        val intent = Intent(context, PlayerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(PlayerActivity.EXTRA_STREAM_URL, streamUrl)
            putExtra(PlayerActivity.EXTRA_CHANNEL_NAME, channelName)
            putExtra(PlayerActivity.EXTRA_SERVICE_REF, sref)
        }
        try { context.startActivity(intent) } catch (_: Exception) {}
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.reminder_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }
    }

    companion object {
        const val CHANNEL_ID = "epg_reminder_channel"
        private const val NOTIF_BASE = 5000

        const val EXTRA_ID = "id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_CHANNEL = "channel"
        const val EXTRA_SREF = "sref"

        const val ACTION_SNOOZE = "com.enigma2.firetv.ACTION_REMINDER_SNOOZE"
        const val ACTION_WATCH = "com.enigma2.firetv.ACTION_REMINDER_WATCH"
        private const val SNOOZE_SECONDS = 5 * 60L

        fun schedule(context: Context, reminder: EpgReminder) {
            val triggerMs = reminder.startTimestampSec * 1000L
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra(EXTRA_ID, reminder.id)
                putExtra(EXTRA_TITLE, reminder.title)
                putExtra(EXTRA_CHANNEL, reminder.channelName)
                putExtra(EXTRA_SREF, reminder.sref)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pi = PendingIntent.getBroadcast(context, reminder.id, intent, flags)
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
            } else {
                @Suppress("DEPRECATION")
                am.set(AlarmManager.RTC_WAKEUP, triggerMs, pi)
            }
        }

        fun cancel(context: Context, id: Int) {
            val intent = Intent(context, ReminderReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pi = PendingIntent.getBroadcast(context, id, intent, flags)
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(pi)
        }
    }
}
