package ph.pusoalalay.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ph.pusoalalay.app.*
import ph.pusoalalay.app.R
import ph.pusoalalay.app.data.Settings
import ph.pusoalalay.app.domain.*
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PusoApp(model: HealthViewModel, settings: Settings, onLanguageChanged: () -> Unit) {
    val state by model.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var onboarded by rememberSaveable { mutableStateOf(settings.onboardingDone) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var research by rememberSaveable { mutableStateOf(false) }
    var editor by rememberSaveable { mutableStateOf<String?>(null) }
    var initialKind by rememberSaveable { mutableStateOf(RecordKind.HEART_RATE.name) }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    var clearAll by rememberSaveable { mutableStateOf(false) }
    var exportSelection by remember { mutableStateOf<List<HealthRecord>?>(null) }
    var appointmentDialog by rememberSaveable { mutableStateOf(false) }
    var deletingAppointment by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingAccount by rememberSaveable { mutableStateOf(false) }
    val unusualIds = remember(state.records) { unusualRecords(state.records) }
    val language = settings.language

    fun message(resource: Int) { scope.launch { snackbar.showSnackbar(context.getString(resource)) } }
    fun changeLanguage(value: String) {
        if (value != language && !state.busy) { settings.language = value; onLanguageChanged() }
    }
    fun add(kind: RecordKind) {
        initialKind = kind.name; editor = "NEW"; detail = null
    }
    fun resetNavigation() {
        tab = 0; editor = null; detail = null; research = false; deleting = null; clearAll = false
        appointmentDialog = false; deletingAppointment = null; deletingAccount = false; exportSelection = null
    }
    LaunchedEffect(model, context) {
        model.messages.collect { event ->
            val text = when (event) {
                UiEvent.Saved -> { editor = null; detail = null; R.string.saved }
                UiEvent.Deleted -> { detail = null; deleting = null; R.string.deleted }
                UiEvent.Cleared -> { clearAll = false; detail = null; model.reload(); R.string.delete_all_success }
                UiEvent.Exported -> R.string.export_success
                UiEvent.AppointmentSaved -> { appointmentDialog = false; R.string.appointment_saved }
                UiEvent.AppointmentDeleted -> { deletingAppointment = null; R.string.appointment_deleted }
                UiEvent.SignedIn -> { resetNavigation(); R.string.auth_signed_in }
                UiEvent.SignedOut -> { resetNavigation(); R.string.auth_signed_out }
                UiEvent.AccountDeleted -> { resetNavigation(); R.string.account_deleted }
                is UiEvent.Error -> {
                    if (deleting != null) detail = null
                    deleting = null; clearAll = false; deletingAppointment = null
                    event.message
                }
            }
            snackbar.showSnackbar(context.getString(text))
        }
    }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> model.finishExport(uri, false) }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri -> model.finishExport(uri, true) }

    BackHandler(enabled = editor == null && (detail != null || research || tab != 0)) {
        when { detail != null -> detail = null; research -> research = false; else -> tab = 0 }
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { outerPadding ->
        Box(Modifier.fillMaxSize().padding(outerPadding)) {
            if (!onboarded) {
                Onboarding(language, ::changeLanguage) { settings.onboardingDone = true; onboarded = true }
            } else if (!state.sessionReady) {
                Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(); Text(stringResource(R.string.loading))
                }
            } else if (state.user == null) {
                AuthScreen(state.hasAccounts, state.busy, state.authError, model::signIn, model::signUp, model::clearAuthError)
            } else if (editor != null) {
                val existing = state.records.firstOrNull { it.id == editor }
                if (state.loading || state.storageError || (editor != "NEW" && existing == null)) {
                    Column(Modifier.safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(if (state.loading) R.string.loading else R.string.storage_error))
                        TextButton(onClick = { editor = null }) { Text(stringResource(R.string.back)) }
                    }
                } else key(editor) {
                    RecordForm(existing, RecordKind.valueOf(initialKind), language, state.busy,
                        onCancel = { editor = null }, onSave = { model.save(it, existing != null) })
                }
            } else {
                val labels = listOf(R.string.nav_health, R.string.nav_records, R.string.nav_reminders, R.string.nav_more)
                val icons = listOf(Icons.Outlined.FavoriteBorder, Icons.AutoMirrored.Outlined.MenuBook, Icons.Outlined.NotificationsNone, Icons.Outlined.Tune)
                Scaffold(
                    topBar = {
                        TopAppBar(title = {
                            Column {
                                Text("PusoAlalay", style = MaterialTheme.typography.titleLarge)
                                Text(if (research) stringResource(R.string.research)
                                    else state.user?.displayName ?: stringResource(R.string.offline_label),
                                    style = MaterialTheme.typography.labelSmall, color = Muted)
                            }
                        }, navigationIcon = {
                            if (research) IconButton(onClick = { research = false }) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
                            }
                        }, actions = {
                            Icon(Icons.Outlined.Lock, null, tint = Mint, modifier = Modifier.padding(end = 20.dp).size(18.dp))
                        })
                    },
                    bottomBar = {
                        NavigationBar {
                            labels.forEachIndexed { index, label ->
                                NavigationBarItem(selected = tab == index && !research, onClick = { tab = index; research = false },
                                    icon = { Icon(icons[index], null) }, label = { Text(stringResource(label)) })
                            }
                        }
                    },
                    floatingActionButton = {
                        if (tab == 1 && !research && !state.loading && !state.storageError)
                            FloatingActionButton(onClick = { if (!state.busy) add(RecordKind.HEART_RATE) }) {
                                Icon(Icons.Outlined.Add, stringResource(R.string.add_record))
                            }
                    }
                ) { innerPadding ->
                    Box(Modifier.fillMaxSize().padding(innerPadding)) {
                        when {
                            research -> ResearchScreen()
                            tab <= 1 && state.loading -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                CircularProgressIndicator(); Text(stringResource(R.string.loading))
                            }
                            tab <= 1 && state.storageError -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(stringResource(R.string.storage_error))
                                Button(onClick = model::reload) { Text(stringResource(R.string.retry)) }
                            }
                            tab == 0 -> HomeScreen(state.records, language, ::add, { detail = it.id }, { tab = 1 })
                            tab == 1 -> HistoryScreen(state.records, language, state.busy, { add(RecordKind.HEART_RATE) },
                                { detail = it.id }, { exportSelection = it })
                            tab == 2 -> ReminderScreen(settings, ::message, state.appointments, language, state.busy,
                                { appointmentDialog = true }, { deletingAppointment = it })
                            else -> state.user?.let { user ->
                                SettingsScreen(language, state.busy, user, ::changeLanguage, { research = true }, { clearAll = true },
                                    model::signOut, { deletingAccount = true })
                            }
                        }
                    }
                }
            }
        }
    }
    val selected = state.records.firstOrNull { it.id == detail }
    if (selected != null && editor == null) AlertDialog(
        onDismissRequest = { if (!state.busy) detail = null },
        title = { Text(stringResource(selected.kind.label())) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (selected.kind != RecordKind.SYMPTOM) Text(selected.numberText() + " " + selected.kind.unit(), style = MaterialTheme.typography.headlineMedium)
                Text(formatTime(selected.measuredAt, language))
                Text(stringResource(R.string.manila_time), color = Muted)
                val summary = symptomSummary(selected)
                if (summary.isNotEmpty()) Text(summary, style = MaterialTheme.typography.titleMedium)
                if (selected.id in unusualIds) Text(stringResource(R.string.unusual_tag), color = MaterialTheme.colorScheme.secondary)
                Text(stringResource(if (selected.source == MeasurementSource.SELF_REPORTED) R.string.source_self else R.string.source_device), color = Muted)
                if (selected.note.isNotBlank()) Text(selected.note)
                TextButton(onClick = { deleting = selected.id; detail = null }, enabled = !state.busy,
                    colors = ButtonDefaults.textButtonColors(contentColor = Rose)) { Text(stringResource(R.string.delete)) }
            }
        },
        confirmButton = { TextButton(onClick = { editor = selected.id; detail = null }, enabled = !state.busy) { Text(stringResource(R.string.edit)) } },
        dismissButton = { TextButton(onClick = { detail = null }, enabled = !state.busy) { Text(stringResource(R.string.close)) } }
    )
    if (deleting != null) AlertDialog(onDismissRequest = { if (!state.busy) deleting = null },
        title = { Text(stringResource(R.string.delete_title)) }, text = { Text(stringResource(R.string.delete_body)) },
        confirmButton = { TextButton(onClick = { deleting?.let(model::delete) }, enabled = !state.busy) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { deleting = null }, enabled = !state.busy) { Text(stringResource(R.string.cancel)) } })
    if (clearAll) AlertDialog(onDismissRequest = { if (!state.busy) clearAll = false },
        title = { Text(stringResource(R.string.delete_all_title)) }, text = { Text(stringResource(R.string.delete_all_body)) },
        confirmButton = { TextButton(onClick = model::deleteAll, enabled = !state.busy) { Text(stringResource(R.string.delete_all)) } },
        dismissButton = { TextButton(onClick = { clearAll = false }, enabled = !state.busy) { Text(stringResource(R.string.cancel)) } })
    if (appointmentDialog && state.user != null) AppointmentDialog(language, state.busy,
        onDismiss = { if (!state.busy) appointmentDialog = false }, onSave = model::saveAppointment)
    deletingAppointment?.let { id ->
        AlertDialog(onDismissRequest = { if (!state.busy) deletingAppointment = null },
            title = { Text(stringResource(R.string.appointment_delete_title)) },
            confirmButton = { TextButton(onClick = { model.deleteAppointment(id) }, enabled = !state.busy) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { deletingAppointment = null }, enabled = !state.busy) { Text(stringResource(R.string.cancel)) } })
    }
    if (deletingAccount && state.user != null) {
        var password by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { if (!state.busy) deletingAccount = false },
            title = { Text(stringResource(R.string.account_delete_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.account_delete_body))
                    OutlinedTextField(password, { password = it }, label = { Text(stringResource(R.string.auth_password)) },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), enabled = !state.busy)
                }
            },
            confirmButton = {
                TextButton(onClick = { model.deleteAccount(password) }, enabled = !state.busy && password.isNotEmpty(),
                    colors = ButtonDefaults.textButtonColors(contentColor = Rose)) { Text(stringResource(R.string.account_delete)) }
            },
            dismissButton = { TextButton(onClick = { deletingAccount = false }, enabled = !state.busy) { Text(stringResource(R.string.cancel)) } })
    }
    exportSelection?.let { snapshot ->
        AlertDialog(onDismissRequest = { exportSelection = null },
            title = { Text(stringResource(R.string.export)) }, text = { Text(stringResource(R.string.export_notice)) },
            confirmButton = {
                TextButton(onClick = {
                    model.prepareExport(snapshot); exportSelection = null
                    pdf.launch("PusoAlalay-" + LocalDate.now(MANILA) + ".pdf")
                }) { Text(stringResource(R.string.export_pdf)) }
            },
            dismissButton = {
                Column {
                    TextButton(onClick = { exportSelection = null }) { Text(stringResource(R.string.cancel)) }
                    TextButton(onClick = {
                        model.prepareExport(snapshot); exportSelection = null
                        csv.launch("PusoAlalay-" + LocalDate.now(MANILA) + ".csv")
                    }) { Text(stringResource(R.string.export_csv)) }
                }
            })
    }
}
