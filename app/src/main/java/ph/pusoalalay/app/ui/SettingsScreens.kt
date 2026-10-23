package ph.pusoalalay.app.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ph.pusoalalay.app.R
import ph.pusoalalay.app.data.Settings
import ph.pusoalalay.app.reminders.Reminders
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ReminderScreen(settings: Settings, onMessage: (Int) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var enabled by remember { mutableStateOf(settings.reminderEnabled) }
    var hour by remember { mutableIntStateOf(settings.reminderHour) }
    var minute by remember { mutableIntStateOf(settings.reminderMinute) }
    var allowed by remember { mutableStateOf(Reminders.allowed(context)) }
    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                allowed = Reminders.allowed(context)
                enabled = settings.reminderEnabled
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    fun enable() {
        settings.reminderEnabled = true
        enabled = true
        Reminders.schedule(context)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = Reminders.allowed(context)
        if (granted && allowed) enable() else onMessage(R.string.permission_denied)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Icon(Icons.Outlined.NotificationsNone, null, tint = Mint, modifier = Modifier.size(40.dp))
        Text(stringResource(R.string.reminder_heading), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.reminder_intro), color = Muted)
        OutlinedCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.daily_reminder), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(enabled, onCheckedChange = { turnOn ->
                    if (!turnOn) { Reminders.disable(context); enabled = false }
                    else if (allowed) enable()
                    else if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else onMessage(R.string.notifications_off)
                })
            }
        }
        val time = LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern("h:mm a", Locale.forLanguageTag(settings.language)))
        OutlinedButton(onClick = {
            TimePickerDialog(context, { _, h, m ->
                hour = h; minute = m; settings.reminderHour = h; settings.reminderMinute = m
                Reminders.schedule(context)
            }, hour, minute, false).show()
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Icon(Icons.Outlined.Schedule, null); Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.reminder_time, time))
        }
        if (!allowed) {
            Text(stringResource(R.string.notifications_off), color = MaterialTheme.colorScheme.secondary)
            TextButton(onClick = {
                context.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName))
            }) { Text(stringResource(R.string.notification_settings)) }
        }
        HorizontalDivider()
        Text(stringResource(R.string.reminder_delay), color = Muted)
    }
}

@Composable
fun SettingsScreen(language: String, busy: Boolean, onLanguage: (String) -> Unit, onResearch: () -> Unit, onDeleteAll: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(stringResource(R.string.privacy_title), style = MaterialTheme.typography.headlineLarge)
        LanguagePicker(language, onLanguage)
        HorizontalDivider()
        Icon(Icons.Outlined.Lock, null, tint = Mint)
        Text(stringResource(R.string.privacy_body), color = Muted)
        Text(stringResource(R.string.privacy_backup), color = Muted)
        Text(stringResource(R.string.screenshots_note), style = MaterialTheme.typography.bodySmall, color = Muted)
        OutlinedButton(onClick = onResearch, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Icon(Icons.Outlined.Science, null); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.research))
        }
        TextButton(onClick = onDeleteAll, enabled = !busy,
            colors = ButtonDefaults.textButtonColors(contentColor = Rose), modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(Icons.Outlined.DeleteOutline, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.delete_all))
        }
        Text(stringResource(R.string.version_label), style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable
fun ResearchScreen() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(stringResource(R.string.research_title), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.research_body), color = Muted)
        HorizontalDivider()
        Text(stringResource(R.string.research_metrics))
        Text(stringResource(R.string.research_limits), color = Muted)
        Text(stringResource(R.string.version_label), style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}
