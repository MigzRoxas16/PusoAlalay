package ph.pusoalalay.app.reminders

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ph.pusoalalay.app.MainActivity
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

    fun notify(context: Context) {
        channel(context)
        if (!allowed(context)) return
        val local = localizedContext(context)
        val contentIntent = PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(local.getString(R.string.app_name))
            .setContentText(local.getString(R.string.reminder_body))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(contentIntent).setAutoCancel(true).build()
        try { NotificationManagerCompat.from(context).notify(REQUEST, notification) }
        catch (_: SecurityException) { /* Permission may be revoked after the check. */ }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Settings(context).reminderEnabled) return
        Reminders.notify(context)
        Reminders.schedule(context)
    }
}

class RestoreReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) {
            Reminders.channel(context)
            Reminders.schedule(context)
        }
    }
}
