package ph.pusoalalay.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ph.pusoalalay.app.R
import ph.pusoalalay.app.domain.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordForm(initial: HealthRecord?, initialKind: RecordKind, language: String, busy: Boolean,
    onCancel: () -> Unit, onSave: (HealthRecord) -> Unit) {
    val context = LocalContext.current
    var kindName by rememberSaveable { mutableStateOf((initial?.kind ?: initialKind).name) }
    val kind = RecordKind.valueOf(kindName)
    val id = rememberSaveable { initial?.id ?: UUID.randomUUID().toString() }
    val createdAt = rememberSaveable { initial?.createdAt ?: System.currentTimeMillis() }
    var measuredAt by rememberSaveable { mutableLongStateOf(initial?.measuredAt ?: System.currentTimeMillis()) }
    val originalValue = initial?.value?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }.orEmpty()
    var heartValue by rememberSaveable { mutableStateOf(if (initial?.kind == RecordKind.HEART_RATE) originalValue else "") }
    var pressureValue by rememberSaveable { mutableStateOf(if (initial?.kind == RecordKind.BLOOD_PRESSURE) originalValue else "") }
    var weightValue by rememberSaveable { mutableStateOf(if (initial?.kind == RecordKind.WEIGHT) originalValue else "") }
    val value = when (kind) {
        RecordKind.HEART_RATE -> heartValue
        RecordKind.BLOOD_PRESSURE -> pressureValue
        RecordKind.WEIGHT -> weightValue
        RecordKind.SYMPTOM -> ""
    }
    fun setValue(text: String) {
        when (kind) {
            RecordKind.HEART_RATE -> heartValue = text
            RecordKind.BLOOD_PRESSURE -> pressureValue = text
            RecordKind.WEIGHT -> weightValue = text
            RecordKind.SYMPTOM -> Unit
        }
    }
    var secondary by rememberSaveable { mutableStateOf(initial?.secondary?.toInt()?.toString().orEmpty()) }
    var note by rememberSaveable { mutableStateOf(initial?.note.orEmpty()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var dirty by rememberSaveable { mutableStateOf(false) }
    var discard by rememberSaveable { mutableStateOf(false) }
    fun close() { if (dirty) discard = true else onCancel() }
    BackHandler { if (!busy) close() }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(if (initial == null) R.string.add_record else R.string.edit_record)) },
            navigationIcon = { IconButton(onClick = { close() }, enabled = !busy) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
            } })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.record_type), style = MaterialTheme.typography.titleMedium)
            if (initial == null) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecordKind.entries.forEach { candidate ->
                    FilterChip(selected = kind == candidate, onClick = {
                        kindName = candidate.name; error = null; dirty = true
                    }, label = { Text(stringResource(candidate.label())) }, enabled = !busy)
                }
            } else Text(stringResource(kind.label()), style = MaterialTheme.typography.titleLarge)
            if (kind != RecordKind.SYMPTOM) {
                Text(stringResource(R.string.entry_help), color = Muted)
                OutlinedTextField(value, { setValue(it); dirty = true; error = null }, modifier = Modifier.fillMaxWidth().testTag("record_value"),
                    label = { Text(stringResource(if (kind == RecordKind.BLOOD_PRESSURE) R.string.systolic else kind.label())) },
                    suffix = { Text(kind.unit()) }, singleLine = true, enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = if (kind == RecordKind.WEIGHT) KeyboardType.Decimal else KeyboardType.Number))
                if (kind == RecordKind.BLOOD_PRESSURE)
                    OutlinedTextField(secondary, { secondary = it; dirty = true; error = null }, modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.diastolic)) }, suffix = { Text("mmHg") },
                        singleLine = true, enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            Text(stringResource(R.string.measured_at), style = MaterialTheme.typography.titleMedium)
            val selected = Instant.ofEpochMilli(measuredAt).atZone(MANILA)
            OutlinedButton(onClick = {
                DatePickerDialog(context, { _, year, month, day ->
                    measuredAt = LocalDate.of(year, month + 1, day).atTime(selected.toLocalTime()).atZone(MANILA).toInstant().toEpochMilli()
                    dirty = true; error = null
                }, selected.year, selected.monthValue - 1, selected.dayOfMonth).apply {
                    // Future dates are rejected on save; stop them from being picked in the first place.
                    datePicker.maxDate = System.currentTimeMillis()
                }.show()
            }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.date) + ": " + formatTime(measuredAt, language, "dd MMM yyyy"))
            }
            OutlinedButton(onClick = {
                TimePickerDialog(context, { _, hour, minute ->
                    measuredAt = selected.withHour(hour).withMinute(minute).withSecond(0).withNano(0).toInstant().toEpochMilli()
                    dirty = true; error = null
                }, selected.hour, selected.minute, false).show()
            }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.time) + ": " + formatTime(measuredAt, language, "h:mm a"))
            }
            Text(stringResource(R.string.manila_time), style = MaterialTheme.typography.bodySmall, color = Muted)
            OutlinedTextField(note, { note = it; dirty = true; error = null }, modifier = Modifier.fillMaxWidth().testTag("record_note"),
                label = { Text(stringResource(if (kind == RecordKind.SYMPTOM) R.string.notes else R.string.notes_optional)) },
                placeholder = { Text(stringResource(R.string.notes_hint)) }, minLines = 3, maxLines = 8, enabled = !busy,
                supportingText = { Text(stringResource(R.string.note_counter, note.length)) }, isError = note.length > 2000)
            error?.let {
                val resource = when (ValidationIssue.valueOf(it)) {
                    ValidationIssue.REQUIRED -> R.string.validation_required
                    ValidationIssue.INVALID_NUMBER -> R.string.validation_number
                    ValidationIssue.BP_ORDER -> R.string.validation_bp
                    ValidationIssue.FUTURE_DATE -> R.string.validation_future
                    ValidationIssue.NOTE_TOO_LONG -> R.string.validation_note
                }
                Text(stringResource(resource), color = MaterialTheme.colorScheme.error)
            }
            Text(stringResource(if (kind == RecordKind.SYMPTOM) R.string.source_self else R.string.source_device),
                style = MaterialTheme.typography.bodySmall, color = Muted)
            Button(onClick = {
                val first = value.trim().replace(',', '.').toDoubleOrNull()
                val second = secondary.trim().toDoubleOrNull()
                if (kind != RecordKind.SYMPTOM && ((value.isNotBlank() && first == null) ||
                    (kind == RecordKind.BLOOD_PRESSURE && secondary.isNotBlank() && second == null))) {
                    error = ValidationIssue.INVALID_NUMBER.name
                } else {
                    val record = HealthRecord(id, kind, measuredAt,
                        if (kind == RecordKind.SYMPTOM) null else first,
                        if (kind == RecordKind.BLOOD_PRESSURE) second else null, note.trim(),
                        if (kind == RecordKind.SYMPTOM) MeasurementSource.SELF_REPORTED else MeasurementSource.DEVICE_MANUAL, createdAt)
                    val issue = RecordValidator.validate(record)
                    if (issue != null) error = issue.name else onSave(record)
                }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("record_save")) {
                Text(stringResource(if (busy) R.string.saving else R.string.save_record))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false },
        title = { Text(stringResource(R.string.discard_title)) }, text = { Text(stringResource(R.string.discard_body)) },
        confirmButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.discard)) } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text(stringResource(R.string.keep_editing)) } })
}
