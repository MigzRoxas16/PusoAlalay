package ph.pusoalalay.app.ui

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ph.pusoalalay.app.AccountSummary
import ph.pusoalalay.app.R
import ph.pusoalalay.app.data.Settings
import ph.pusoalalay.app.domain.*
import ph.pusoalalay.app.reminders.Reminders
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ReminderScreen(settings: Settings, onMessage: (Int) -> Unit, appointments: List<Appointment>, language: String,
    busy: Boolean, onAddAppointment: () -> Unit, onDeleteAppointment: (String) -> Unit) {
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
    // Separate request for appointments so allowing notifications does not switch on the daily reminder.
    val appointmentPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = Reminders.allowed(context)
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
        Text(stringResource(R.string.appointments_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.appointments_body), color = Muted)
        Button(onClick = {
            if (!allowed && Build.VERSION.SDK_INT >= 33) appointmentPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            onAddAppointment()
        }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Icon(Icons.Outlined.EventAvailable, null); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.appointment_add))
        }
        if (appointments.isEmpty()) Text(stringResource(R.string.appointment_none), color = Muted)
        val now = System.currentTimeMillis()
        appointments.forEach { appointment ->
            val past = appointment.startsAt < now
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(appointment.title, style = MaterialTheme.typography.titleMedium, color = if (past) Muted else Cream)
                    Text(formatTime(appointment.startsAt, language), color = if (past) Muted else Cream)
                    Text(if (past) stringResource(R.string.appointment_past)
                        else stringResource(R.string.appointment_reminder_at, formatTime(appointment.remindAt, language)),
                        style = MaterialTheme.typography.bodySmall, color = Muted)
                    if (appointment.note.isNotBlank()) Text(appointment.note, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { onDeleteAppointment(appointment.id) }, enabled = !busy,
                        colors = ButtonDefaults.textButtonColors(contentColor = Rose)) { Text(stringResource(R.string.delete)) }
                }
            }
        }
        HorizontalDivider()
        Text(stringResource(R.string.reminder_delay), color = Muted)
    }
}

@Composable
fun AppointmentDialog(language: String, busy: Boolean, onDismiss: () -> Unit, onSave: (Appointment) -> Unit) {
    val context = LocalContext.current
    var title by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var startsAt by rememberSaveable {
        mutableLongStateOf(LocalDate.now(MANILA).plusDays(1).atTime(9, 0).atZone(MANILA).toInstant().toEpochMilli())
    }
    var lead by rememberSaveable { mutableIntStateOf(2) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = Instant.ofEpochMilli(startsAt).atZone(MANILA)
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.appointment_add)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it; error = null }, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.appointment_title)) }, singleLine = true, enabled = !busy)
                Text(stringResource(R.string.appointment_when), style = MaterialTheme.typography.titleSmall)
                OutlinedButton(onClick = {
                    DatePickerDialog(context, { _, year, month, day ->
                        startsAt = LocalDate.of(year, month + 1, day).atTime(selected.toLocalTime()).atZone(MANILA).toInstant().toEpochMilli()
                        error = null
                    }, selected.year, selected.monthValue - 1, selected.dayOfMonth).apply {
                        datePicker.minDate = System.currentTimeMillis()
                    }.show()
                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.date) + ": " + formatTime(startsAt, language, "dd MMM yyyy"))
                }
                OutlinedButton(onClick = {
                    TimePickerDialog(context, { _, hour, minute ->
                        startsAt = selected.withHour(hour).withMinute(minute).withSecond(0).withNano(0).toInstant().toEpochMilli()
                        error = null
                    }, selected.hour, selected.minute, false).show()
                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.time) + ": " + formatTime(startsAt, language, "h:mm a"))
                }
                Text(stringResource(R.string.appointment_remind), style = MaterialTheme.typography.titleSmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(R.string.lead_1h, R.string.lead_2h, R.string.lead_1d).forEachIndexed { index, label ->
                        FilterChip(lead == index, { lead = index }, label = { Text(stringResource(label)) }, enabled = !busy)
                    }
                }
                OutlinedTextField(note, { note = it; error = null }, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.notes_optional)) }, maxLines = 4, enabled = !busy)
                error?.let {
                    Text(stringResource(when (AppointmentIssue.valueOf(it)) {
                        AppointmentIssue.TITLE_REQUIRED -> R.string.appointment_error_title
                        AppointmentIssue.PAST_TIME -> R.string.appointment_error_past
                        AppointmentIssue.TOO_LONG -> R.string.appointment_error_long
                    }), color = MaterialTheme.colorScheme.error)
                }
                Text(stringResource(R.string.appointment_privacy), style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val appointment = Appointment(startsAt = startsAt, remindAt = startsAt - REMINDER_LEADS[lead],
                    title = title.trim(), note = note.trim())
                val issue = validateAppointment(appointment)
                if (issue != null) error = issue.name else onSave(appointment)
            }, enabled = !busy) { Text(stringResource(R.string.save_record)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) } })
}

@Composable
fun SettingsScreen(language: String, busy: Boolean, user: AccountSummary, onLanguage: (String) -> Unit, onResearch: () -> Unit,
    onDeleteAll: () -> Unit, onSignOut: () -> Unit, onDeleteAccount: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.account_signed_in_as), style = MaterialTheme.typography.labelMedium, color = Muted)
                Text(user.displayName, style = MaterialTheme.typography.titleLarge)
                Text("@" + user.username, color = Muted)
                OutlinedButton(onClick = onSignOut, enabled = !busy, modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.sign_out))
                }
            }
        }
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
        TextButton(onClick = onDeleteAccount, enabled = !busy,
            colors = ButtonDefaults.textButtonColors(contentColor = Rose), modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(Icons.Outlined.PersonRemove, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.account_delete))
        }
        Text(stringResource(R.string.account_privacy), style = MaterialTheme.typography.bodySmall, color = Muted)
        Text(stringResource(R.string.version_label), style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable
fun ResearchScreen(onOpenLab: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(stringResource(R.string.research_title), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.research_body), color = Muted)
        HorizontalDivider()
        Text(stringResource(R.string.research_metrics))
        Text(stringResource(R.string.research_limits), color = Muted)
        Button(onClick = onOpenLab, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.lab_open))
        }
        Text(stringResource(R.string.version_label), style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}
