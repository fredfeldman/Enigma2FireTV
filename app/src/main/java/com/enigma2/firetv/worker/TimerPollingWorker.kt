package com.enigma2.firetv.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.enigma2.firetv.R
import com.enigma2.firetv.data.api.ApiClient
import com.enigma2.firetv.data.model.Timer
import com.enigma2.firetv.data.prefs.ReceiverPreferences

class TimerPollingWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val prefs = ReceiverPreferences(applicationContext)
        if (!prefs.isConfigured) return Result.success()
        ApiClient.initialize(prefs.host, prefs.port, prefs.useHttps, prefs.username, prefs.password)
        return try {
            val timers = ApiClient.service.getTimerList().timers ?: emptyList()
            val nowRecording = timers.filter { it.state == 2 && it.justPlay == 0 }
            val sp = applicationContext.getSharedPreferences(POLL_PREFS, Context.MODE_PRIVATE)
            val prevKeys = sp.getStringSet(KEY_PREV, emptySet()) ?: emptySet()
            val currentKeys = nowRecording.map { timerKey(it) }.toSet()
            nowRecording.filter { timerKey(it) !in prevKeys }.forEach { postNotification(it) }
            sp.edit().putStringSet(KEY_PREV, currentKeys).apply()
            Result.success()
        } catch (e: Exception) {
            Result.success()
        }
    }

    private fun timerKey(t: Timer) = "${t.serviceRef}|${t.beginTimestamp}"

    private fun postNotification(timer: Timer) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    applicationContext.getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = applicationContext.getString(R.string.notif_channel_desc) }
            )
        }
        val suffix = timer.serviceName?.let { " · $it" } ?: ""
        nm.notify(
            timer.beginTimestamp.toInt(),
            NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(applicationContext.getString(R.string.notif_recording_started))
                .setContentText("${timer.name}$suffix")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
        )
    }

    companion object {
        const val CHANNEL_ID = "recording_alerts"
        const val WORK_NAME = "timer_polling"
        private const val POLL_PREFS = "timer_poll_state"
        private const val KEY_PREV = "prev_recording_keys"
    }
}
