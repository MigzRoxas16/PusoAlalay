package ph.pusoalalay.app.reminders

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ph.pusoalalay.app.MainActivity
import ph.pusoalalay.app.PusoApplication
import ph.pusoalalay.app.R
import ph.pusoalalay.app.data.Settings
import ph.pusoalalay.app.data.localizedContext
import ph.pusoalalay.app.domain.nextReminder
import java.time.Instant

object Reminders {
    private const val CHANNEL = "daily_records"
    private const val REQUEST = 401

    private fun pending(context: Context) = PendingIntent.getBroadcast(context, REQUEST,
        Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun appointmentPending(context: Context, id: String) = PendingIntent.getBroadcast(context, id.hashCode(),
        Intent(context, AppointmentReceiver::class.java).setData(Uri.parse("pusoalalay://appointment/$id")),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun channel(context: Context) {
        val local = localizedContext(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, local.getString(R.string.reminders),
            NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = local.getString(R.string.reminder_channel_description)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        })
    }

    fun allowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        return NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun schedule(context: Context) {
        val settings = Settings(context)
        val alarm = context.getSystemService(AlarmManager::class.java)
        alarm.cancel(pending(context))
        if (!settings.reminderEnabled) return
        val trigger = nextReminder(Instant.now(), settings.reminderHour, settings.reminderMinute)
        // A logging reminder is not an exact/medical alarm. Android may delay this window.
        alarm.setWindow(AlarmManager.RTC_WAKEUP, trigger.toInstant().toEpochMilli(),
            15 * 60_000L, pending(context))
    }

    fun disable(context: Context) {
        Settings(context).reminderEnabled = false
        context.getSystemService(AlarmManager::class.java).cancel(pending(context))
        NotificationManagerCompat.from(context).cancel(REQUEST)
    }

    /** Appointment reminders are approximate (10-minute window) and never change any treatment schedule. */
    fun scheduleAppointment(context: Context, id: String, remindAt: Long) {
        if (remindAt <= System.currentTimeMillis()) return
        context.getSystemService(AlarmManager::class.java)
            .setWindow(AlarmManager.RTC_WAKEUP, remindAt, 10 * 60_000L, appointmentPending(context, id))
    }

    fun cancelAppointment(context: Context, id: String) {
        context.getSystemService(AlarmManager::class.java).cancel(appointmentPending(context, id))
        NotificationManagerCompat.from(context).cancel(id.hashCode())
    }

    private fun post(context: Context, notificationId: Int, title: Int, body: Int) {
        channel(context)
        if (!allowed(context)) return
        val local = localizedContext(context)
        val contentIntent = PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(local.getString(title))
            .setContentText(local.getString(body))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(contentIntent).setAutoCancel(true).build()
        try { NotificationManagerCompat.from(context).notify(notificationId, notification) }
        catch (_: SecurityException) { /* Permission may be revoked after the check. */ }
    }

    fun notify(context: Context) = post(context, REQUEST, R.string.app_name, R.string.reminder_body)

    /** The notification never shows the appointment title, so nothing private appears on the screen. */
    fun notifyAppointment(context: Context, id: String) =
        post(context, id.hashCode(), R.string.appointment_notification_title, R.string.appointment_notification_body)
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Settings(context).reminderEnabled) return
        Reminders.notify(context)
        Reminders.schedule(context)
    }
}

class AppointmentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.data?.lastPathSegment ?: return
        Reminders.notifyAppointment(context, id)
    }
}

class RestoreReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) {
            Reminders.channel(context)
            Reminders.schedule(context)
            val app = context.applicationContext as PusoApplication
            val result = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    app.database.appointments().pendingReminders(System.currentTimeMillis())
                        .forEach { Reminders.scheduleAppointment(app, it.id, it.remindAt) }
                } catch (_: Exception) {
                    // Reminders are rescheduled again the next time the app opens.
                } finally {
                    result.finish()
                }
            }
        }
    }
}
