package ph.pusoalalay.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ph.pusoalalay.app.R
import ph.pusoalalay.app.domain.*
import java.time.ZonedDateTime

@Composable
fun LanguagePicker(language: String, onChange: (String) -> Unit) {
    Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FilterChip(language == "fil", { onChange("fil") }, label = { Text(stringResource(R.string.filipino)) })
        FilterChip(language == "en", { onChange("en") }, label = { Text(stringResource(R.string.english)) })
    }
}

@Composable
fun Onboarding(language: String, onLanguage: (String) -> Unit, onStart: () -> Unit) {
    var acknowledged by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Icon(Icons.Outlined.FavoriteBorder, null, tint = Mint, modifier = Modifier.padding(top = 16.dp).size(44.dp))
        Text("PusoAlalay", style = MaterialTheme.typography.titleLarge, color = Mint)
        Text(stringResource(R.string.welcome), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.onboarding_body), color = Muted)
        LanguagePicker(language, onLanguage)
        listOf(R.string.onboarding_local to R.string.onboarding_local_body,
            R.string.onboarding_scope to R.string.onboarding_scope_body,
            R.string.onboarding_backup to R.string.onboarding_backup_body).forEach { (title, body) ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(body), color = Muted)
            }
        }
        Row(Modifier.fillMaxWidth().toggleable(acknowledged, role = Role.Checkbox,
            onValueChange = { acknowledged = it }).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(acknowledged, onCheckedChange = null)
            Text(stringResource(R.string.acknowledge), modifier = Modifier.weight(1f).padding(start = 12.dp))
        }
        Button(onClick = onStart, enabled = acknowledged, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.get_started))
        }
        Text(stringResource(R.string.version_label), style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable
fun HomeScreen(records: List<HealthRecord>, language: String, onAdd: (RecordKind) -> Unit,
    onRecord: (HealthRecord) -> Unit, onHistory: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.tagline), color = Mint, style = MaterialTheme.typography.labelLarge)
                Text(stringResource(R.string.today_heading), style = MaterialTheme.typography.headlineLarge)
                Text(stringResource(R.string.today_body), color = Muted)
                FilledTonalButton(onClick = { onAdd(RecordKind.HEART_RATE) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.add_record))
                }
            }
        }
        if (records.isEmpty()) item { EmptyJournal { onAdd(RecordKind.HEART_RATE) } }
        else {
            item { SectionTitle(stringResource(R.string.latest_measurements)) }
            items(listOf(RecordKind.HEART_RATE, RecordKind.BLOOD_PRESSURE, RecordKind.WEIGHT)) { kind ->
                val latest = records.firstOrNull { it.kind == kind }
                if (latest == null) OutlinedCard(onClick = { onAdd(kind) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(kind.icon(), null, tint = Mint)
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(kind.label()), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.no_measurement), color = Muted)
                        }
                        Icon(Icons.Outlined.Add, null)
                    }
                } else RecordRow(latest, language) { onRecord(latest) }
            }
            item {
                TextButton(onClick = { onAdd(RecordKind.SYMPTOM) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.EditNote, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.symptoms))
                }
            }
            item { SectionTitle(stringResource(R.string.recent_entries)) }
            items(records.take(3), key = { "recent-" + it.id }) { record -> RecordRow(record, language) { onRecord(record) } }
            item { TextButton(onClick = onHistory) { Text(stringResource(R.string.view_all)) } }
        }
        item { Text(stringResource(R.string.not_diagnostic), color = Muted, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
fun HistoryScreen(records: List<HealthRecord>, language: String, busy: Boolean,
    onAdd: () -> Unit, onRecord: (HealthRecord) -> Unit, onExport: (List<HealthRecord>) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("ALL") }
    var days by rememberSaveable { mutableIntStateOf(0) }
    var search by rememberSaveable { mutableStateOf("") }
    val cutoff = if (days == 0) 0L else ZonedDateTime.now(MANILA).toLocalDate().minusDays((days - 1).toLong())
        .atStartOfDay(MANILA).toInstant().toEpochMilli()
    val selected = records.filter { (filter == "ALL" || it.kind.name == filter) &&
        it.measuredAt >= cutoff && it.note.contains(search, ignoreCase = true) }
    // Each reading is compared with the same person's earlier readings, using the whole history.
    val unusual = remember(records) { unusualRecords(records) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(stringResource(R.string.records), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.record_count, records.size), color = Muted, modifier = Modifier.padding(top = 6.dp))
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(filter == "ALL", { filter = "ALL" }, label = { Text(stringResource(R.string.all_types)) })
                RecordKind.entries.forEach { kind ->
                    FilterChip(filter == kind.name, { filter = kind.name }, label = { Text(stringResource(kind.label())) })
                }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to R.string.all_time, 7 to R.string.last_7, 30 to R.string.last_30).forEach { (n, label) ->
                    FilterChip(days == n, { days = n }, label = { Text(stringResource(label)) })
                }
            }
        }
        item {
            OutlinedTextField(search, { search = it }, label = { Text(stringResource(R.string.search_notes)) },
                singleLine = true, leadingIcon = { Icon(Icons.Outlined.Search, null) }, modifier = Modifier.fillMaxWidth())
        }
        if (filter != "ALL" && filter != RecordKind.SYMPTOM.name) {
            item { StatsCard(selected, RecordKind.valueOf(filter), selected.count { it.id in unusual }) }
            item { Trend(selected, RecordKind.valueOf(filter), language) }
        }
        item {
            OutlinedButton(onClick = { onExport(selected) }, enabled = selected.isNotEmpty() && !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(Icons.Outlined.FileDownload, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.export_selection, selected.size))
            }
        }
        if (records.isEmpty()) item { EmptyJournal(onAdd) }
        else if (selected.isEmpty()) item {
            Text(stringResource(R.string.no_results), modifier = Modifier.padding(vertical = 16.dp))
            TextButton(onClick = { filter = "ALL"; days = 0; search = "" }) { Text(stringResource(R.string.clear_filters)) }
        }
        items(selected, key = { it.id }) { record -> RecordRow(record, language, record.id in unusual) { onRecord(record) } }
        item { Spacer(Modifier.height(76.dp)) }
    }
}
