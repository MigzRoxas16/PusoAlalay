package ph.pusoalalay.app

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ph.pusoalalay.app.domain.SignalLab

/** Research demo state. Imported recordings stay in memory only; nothing is written to the health journal. */
class ResearchLabViewModel(application: Application) : AndroidViewModel(application) {
    enum class Slot { TRAIN_1, TRAIN_2, TEST }

    data class LabState(
        val training1: SignalLab.Recording? = null,
        val training2: SignalLab.Recording? = null,
        val test: SignalLab.Recording? = null,
        val busy: Boolean = false,
        val error: Int? = null,
        val evaluation: SignalLab.Evaluation? = null,
        val noise: List<SignalLab.NoisePoint>? = null,
        val noiseSource: String? = null
    )

    private val state = MutableStateFlow(LabState())
    val uiState = state.asStateFlow()

    private fun displayName(uri: Uri): String {
        val name = try {
            getApplication<Application>().contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (_: Exception) { null }
        return name ?: "recording.csv"
    }

    fun importRecording(slot: Slot, uri: Uri?) {
        if (uri == null || state.value.busy) return
        state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val recording = try {
                withContext(Dispatchers.IO) {
                    val text = getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader().lineSequence().take(SignalLab.MAX_SAMPLES + 100).joinToString("\n")
                    }
                    text?.let(SignalLab::parseRecording)?.let { SignalLab.Recording(displayName(uri), it) }
                }
            } catch (error: CancellationException) { throw error } catch (_: Exception) { null }
            state.update {
                when {
                    recording == null -> it.copy(busy = false, error = R.string.lab_import_error)
                    slot == Slot.TRAIN_1 -> it.copy(busy = false, training1 = recording, evaluation = null, noise = null)
                    slot == Slot.TRAIN_2 -> it.copy(busy = false, training2 = recording, evaluation = null)
                    else -> it.copy(busy = false, test = recording, evaluation = null)
                }
            }
        }
    }

    fun useSynthetic() {
        if (state.value.busy) return
        state.update {
            it.copy(
                training1 = SignalLab.Recording("synthetic-A", SignalLab.syntheticPulse(seed = 1), synthetic = true),
                training2 = SignalLab.Recording("synthetic-B", SignalLab.syntheticPulse(seed = 2), synthetic = true),
                test = SignalLab.Recording("synthetic-C", SignalLab.syntheticPulse(seed = 3), synthetic = true),
                evaluation = null, noise = null, error = null
            )
        }
    }

    fun runClassifier() {
        val s = state.value
        val test = s.test ?: return
        val training = listOfNotNull(s.training1, s.training2)
        if (training.isEmpty() || s.busy) return
        state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = try { withContext(Dispatchers.Default) { SignalLab.evaluate(training, test) } }
            catch (error: CancellationException) { throw error } catch (_: Exception) { null }
            state.update { it.copy(busy = false, evaluation = result, error = if (result == null) R.string.lab_run_error else null) }
        }
    }

    /** Section 4.3 uses the first 1,024 values of the first training recording (pulse1.csv in the paper). */
    fun runNoise() {
        val source = state.value.training1 ?: return
        if (source.values.size < SignalLab.EXCERPT || state.value.busy) {
            state.update { it.copy(error = R.string.lab_noise_short) }
            return
        }
        state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val points = withContext(Dispatchers.Default) {
                SignalLab.noiseSensitivity(source.values.copyOfRange(0, SignalLab.EXCERPT))
            }
            state.update { it.copy(busy = false, noise = points, noiseSource = source.name) }
        }
    }
}
