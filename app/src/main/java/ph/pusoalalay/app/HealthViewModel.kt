package ph.pusoalalay.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import ph.pusoalalay.app.domain.*
import ph.pusoalalay.app.export.ReportExporter
import ph.pusoalalay.app.reminders.Reminders

data class HealthState(val records: List<HealthRecord> = emptyList(), val loading: Boolean = true,
    val busy: Boolean = false, val storageError: Boolean = false)

sealed interface UiEvent {
    data object Saved : UiEvent
    data object Deleted : UiEvent
    data object Cleared : UiEvent
    data object Exported : UiEvent
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
    init { reload() }

    fun reload() {
        observation?.cancel()
        state.update { it.copy(loading = true, storageError = false) }
        observation = viewModelScope.launch {
            try {
                app.database.records().observe().map { rows -> rows.map(app.cipher::decode) }
                    .flowOn(Dispatchers.IO).collect { records ->
                        state.update { it.copy(records = records, loading = false, storageError = false) }
                    }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { state.update { it.copy(loading = false, storageError = true) } }
        }
    }

    fun save(record: HealthRecord, editing: Boolean) {
        if (state.value.busy || state.value.loading || state.value.storageError) return
        if (RecordValidator.validate(record) != null) {
            events.trySend(UiEvent.Error(R.string.save_error)); return
        }
        mutate(UiEvent.Saved, R.string.save_error) {
            val entity = app.cipher.encode(record)
            if (editing) check(app.database.records().update(entity) == 1) else app.database.records().insert(entity)
        }
    }

    fun delete(id: String) = mutate(UiEvent.Deleted) { app.database.records().delete(id) }
    fun deleteAll() = mutate(UiEvent.Cleared) {
        app.database.records().deleteAll()
        Reminders.disable(app)
    }

    fun prepareExport(records: List<HealthRecord>) { exportSnapshot = records.toList() }
    fun finishExport(uri: Uri?, pdf: Boolean) {
        val snapshot = exportSnapshot
        exportSnapshot = null
        if (uri == null) return
        if (snapshot.isNullOrEmpty()) { events.trySend(UiEvent.Error(R.string.report_lost)); return }
        mutate(UiEvent.Exported, R.string.export_error) { ReportExporter.write(app, uri, snapshot, pdf) }
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
