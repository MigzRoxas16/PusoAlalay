package ph.pusoalalay.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import ph.pusoalalay.app.data.AccountEntity
import ph.pusoalalay.app.data.PasswordHasher
import ph.pusoalalay.app.domain.*
import ph.pusoalalay.app.export.ReportExporter
import ph.pusoalalay.app.reminders.Reminders
import java.util.UUID

data class AccountSummary(val id: String, val username: String, val displayName: String)

data class HealthState(
    val records: List<HealthRecord> = emptyList(),
    val appointments: List<Appointment> = emptyList(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val storageError: Boolean = false,
    val sessionReady: Boolean = false,
    val user: AccountSummary? = null,
    val hasAccounts: Boolean = false,
    val authError: Int? = null
)

sealed interface UiEvent {
    data object Saved : UiEvent
    data object Deleted : UiEvent
    data object Cleared : UiEvent
    data object Exported : UiEvent
    data object AppointmentSaved : UiEvent
    data object AppointmentDeleted : UiEvent
    data object SignedIn : UiEvent
    data object SignedOut : UiEvent
    data object AccountDeleted : UiEvent
    data class Error(val message: Int) : UiEvent
}

class HealthViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PusoApplication
    private val state = MutableStateFlow(HealthState())
    val uiState = state.asStateFlow()
    private val events = Channel<UiEvent>(Channel.BUFFERED)
    val messages = events.receiveAsFlow()
    private var observation: Job? = null
    private var exportSnapshot: List<HealthRecord>? = null
    private var failedSignIns = 0
    private var lockedUntil = 0L

    init { restoreSession() }

    private fun AccountEntity.summary() = AccountSummary(id, username, displayName)

    private fun restoreSession() {
        viewModelScope.launch {
            try {
                val savedId = app.settings.currentUserId
                val (account, count) = withContext(Dispatchers.IO) {
                    // Re-arm appointment reminders in case the app was force-stopped.
                    app.database.appointments().pendingReminders(System.currentTimeMillis())
                        .forEach { Reminders.scheduleAppointment(app, it.id, it.remindAt) }
                    (if (savedId == null) null else app.database.accounts().byId(savedId)) to app.database.accounts().count()
                }
                if (account == null) app.settings.currentUserId = null
                state.update { it.copy(user = account?.summary(), hasAccounts = count > 0, sessionReady = true) }
                reload()
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { state.update { it.copy(sessionReady = true, loading = false, storageError = true) } }
        }
    }

    fun reload() {
        observation?.cancel()
        val owner = state.value.user?.id
        if (owner == null) {
            state.update { it.copy(records = emptyList(), appointments = emptyList(), loading = false, storageError = false) }
            return
        }
        state.update { it.copy(loading = true, storageError = false) }
        observation = viewModelScope.launch {
            launch {
                try {
                    app.database.records().observe(owner).map { rows -> rows.map(app.cipher::decode) }
                        .flowOn(Dispatchers.IO).collect { records ->
                            state.update { it.copy(records = records, loading = false, storageError = false) }
                        }
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { state.update { it.copy(loading = false, storageError = true) } }
            }
            launch {
                try {
                    app.database.appointments().observe(owner).map { rows -> rows.map(app.cipher::decodeAppointment) }
                        .flowOn(Dispatchers.IO).collect { list -> state.update { it.copy(appointments = list) } }
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { state.update { it.copy(storageError = true) } }
            }
        }
    }

    // ---- Accounts (stored only on this phone) ----

    fun clearAuthError() = state.update { it.copy(authError = null) }

    fun signUp(name: String, username: String, password: String, confirm: String) {
        if (state.value.busy) return
        val issue = AccountRules.validateSignUp(name, username, password, confirm)
        if (issue != null) {
            state.update { it.copy(authError = when (issue) {
                SignUpIssue.NAME -> R.string.auth_name_required
                SignUpIssue.USERNAME -> R.string.auth_username_rules
                SignUpIssue.PASSWORD -> R.string.auth_password_rules
                SignUpIssue.MISMATCH -> R.string.auth_password_mismatch
            }) }
            return
        }
        val normalized = AccountRules.normalizeUsername(username)
        state.update { it.copy(busy = true, authError = null) }
        viewModelScope.launch {
            val created: AccountEntity? = try {
                withContext(Dispatchers.IO) {
                    val accounts = app.database.accounts()
                    if (accounts.byUsername(normalized) != null) null
                    else {
                        val salt = PasswordHasher.newSalt()
                        val account = AccountEntity(UUID.randomUUID().toString(), normalized, name.trim(),
                            PasswordHasher.encode(PasswordHasher.hash(password.toCharArray(), salt)),
                            PasswordHasher.encode(salt), System.currentTimeMillis())
                        app.database.withTransaction {
                            val first = accounts.count() == 0
                            accounts.insert(account)
                            // Records saved before accounts existed belong to the phone's first account.
                            if (first) app.database.records().claimUnowned(account.id)
                        }
                        account
                    }
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                state.update { it.copy(busy = false, authError = R.string.operation_error) }
                return@launch
            }
            if (created == null) {
                state.update { it.copy(busy = false, authError = R.string.auth_username_taken) }
                return@launch
            }
            app.settings.currentUserId = created.id
            state.update { it.copy(busy = false, user = created.summary(), hasAccounts = true, authError = null) }
            reload()
            events.send(UiEvent.SignedIn)
        }
    }

    fun signIn(username: String, password: String) {
        if (state.value.busy) return
        if (System.currentTimeMillis() < lockedUntil) { state.update { it.copy(authError = R.string.auth_locked) }; return }
        val normalized = AccountRules.normalizeUsername(username)
        if (normalized.isEmpty() || password.isEmpty()) { state.update { it.copy(authError = R.string.auth_fill_in) }; return }
        state.update { it.copy(busy = true, authError = null) }
        viewModelScope.launch {
            val account: AccountEntity? = try {
                withContext(Dispatchers.IO) {
                    val found = app.database.accounts().byUsername(normalized)
                    if (found == null) {
                        // Hash anyway so the response time does not reveal which usernames exist.
                        PasswordHasher.hash(password.toCharArray(), ByteArray(16))
                        null
                    } else if (PasswordHasher.verify(password.toCharArray(), PasswordHasher.decode(found.salt),
                            PasswordHasher.decode(found.passwordHash))) found
                    else null
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { null }
            if (account == null) {
                failedSignIns++
                if (failedSignIns >= 5) { lockedUntil = System.currentTimeMillis() + 30_000L; failedSignIns = 0 }
                state.update { it.copy(busy = false, authError = R.string.auth_wrong) }
                return@launch
            }
            failedSignIns = 0
            app.settings.currentUserId = account.id
            state.update { it.copy(busy = false, user = account.summary(), authError = null) }
            reload()
            events.send(UiEvent.SignedIn)
        }
    }

    fun signOut() {
        if (state.value.busy) return
        app.settings.currentUserId = null
        exportSnapshot = null
        state.update { it.copy(user = null, authError = null) }
        reload()
        events.trySend(UiEvent.SignedOut)
    }

    fun deleteAccount(password: String) {
        val user = state.value.user ?: return
        if (state.value.busy) return
        state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val event: UiEvent = try {
                withContext(Dispatchers.IO) {
                    val account = app.database.accounts().byId(user.id) ?: error("Account missing")
                    if (!PasswordHasher.verify(password.toCharArray(), PasswordHasher.decode(account.salt),
                            PasswordHasher.decode(account.passwordHash))) UiEvent.Error(R.string.auth_wrong_password)
                    else {
                        app.database.appointments().allOwned(user.id).forEach { Reminders.cancelAppointment(app, it.id) }
                        app.database.withTransaction {
                            app.database.records().deleteAllOwned(user.id)
                            app.database.appointments().deleteAllOwned(user.id)
                            app.database.accounts().delete(user.id)
                        }
                        UiEvent.AccountDeleted
                    }
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { UiEvent.Error(R.string.operation_error) }
            if (event == UiEvent.AccountDeleted) {
                app.settings.currentUserId = null
                val remaining = try { withContext(Dispatchers.IO) { app.database.accounts().count() } }
                    catch (error: CancellationException) { throw error } catch (_: Exception) { 0 }
                state.update { it.copy(busy = false, user = null, hasAccounts = remaining > 0) }
                reload()
            } else state.update { it.copy(busy = false) }
            events.send(event)
        }
    }

    // ---- Health records ----

    fun save(record: HealthRecord, editing: Boolean) {
        val owner = state.value.user?.id ?: return
        if (state.value.busy || state.value.loading || state.value.storageError) return
        if (RecordValidator.validate(record) != null) {
            events.trySend(UiEvent.Error(R.string.save_error)); return
        }
        mutate(UiEvent.Saved, R.string.save_error) {
            val entity = app.cipher.encode(record, owner)
            if (editing) check(app.database.records().update(entity) == 1) else app.database.records().insert(entity)
        }
    }

    fun delete(id: String) {
        val owner = state.value.user?.id ?: return
        mutate(UiEvent.Deleted) { app.database.records().deleteOwned(id, owner) }
    }

    fun deleteAll() {
        val owner = state.value.user?.id ?: return
        mutate(UiEvent.Cleared) {
            app.database.records().deleteAllOwned(owner)
            Reminders.disable(app)
        }
    }

    // ---- Appointments ----

    fun saveAppointment(appointment: Appointment) {
        val owner = state.value.user?.id ?: return
        if (validateAppointment(appointment) != null) { events.trySend(UiEvent.Error(R.string.save_error)); return }
        mutate(UiEvent.AppointmentSaved, R.string.save_error) {
            app.database.appointments().insert(app.cipher.encodeAppointment(appointment, owner))
            Reminders.scheduleAppointment(app, appointment.id, appointment.remindAt)
        }
    }

    fun deleteAppointment(id: String) {
        val owner = state.value.user?.id ?: return
        mutate(UiEvent.AppointmentDeleted) {
            app.database.appointments().deleteOwned(id, owner)
            Reminders.cancelAppointment(app, id)
        }
    }

    // ---- Export ----

    fun prepareExport(records: List<HealthRecord>) { exportSnapshot = records.toList() }
    fun finishExport(uri: Uri?, pdf: Boolean) {
        val snapshot = exportSnapshot
        exportSnapshot = null
        if (uri == null) return
        if (snapshot.isNullOrEmpty()) { events.trySend(UiEvent.Error(R.string.report_lost)); return }
        val name = state.value.user?.displayName.orEmpty()
        mutate(UiEvent.Exported, R.string.export_error) { ReportExporter.write(app, uri, snapshot, pdf, name) }
    }

    private fun mutate(success: UiEvent, failure: Int = R.string.operation_error, operation: suspend () -> Unit) {
        if (state.value.busy) return
        state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val event = try { withContext(Dispatchers.IO) { operation() }; success }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { UiEvent.Error(failure) }
            state.update { it.copy(busy = false) }
            events.send(event)
        }
    }
}
