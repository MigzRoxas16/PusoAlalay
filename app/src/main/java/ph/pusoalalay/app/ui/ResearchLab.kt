package ph.pusoalalay.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ph.pusoalalay.app.R
import ph.pusoalalay.app.ResearchLabViewModel
import ph.pusoalalay.app.ResearchLabViewModel.Slot
import ph.pusoalalay.app.domain.SignalLab
import java.util.Locale

private val Sand = Color(0xFFDBC79E)

private fun f4(value: Double) = String.format(Locale.US, "%.4f", value)
private fun f3(value: Double) = String.format(Locale.US, "%.3f", value)

/** Research demo following the course paper: data quality of recorded pulse windows, not a health check. */
@Composable
fun ResearchLabScreen(model: ResearchLabViewModel = viewModel()) {
    val state by model.uiState.collectAsStateWithLifecycle()
    val pick1 = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { model.importRecording(Slot.TRAIN_1, it) }
    val pick2 = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { model.importRecording(Slot.TRAIN_2, it) }
    val pick3 = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { model.importRecording(Slot.TEST, it) }
    val types = arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.lab_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.lab_scope), color = Muted)

        Text(stringResource(R.string.lab_step_data), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.lab_data_body), color = Muted)
        Text("raw.githubusercontent.com/nidhipathak/Reconfigure-Reuse-Dataset/main/pulse1.csv\n…/pulse3.csv\n…/pulse5.csv",
            style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = Mint)
        RecordingRow(stringResource(R.string.lab_train1), state.training1, state.busy) { pick1.launch(types) }
        RecordingRow(stringResource(R.string.lab_train2), state.training2, state.busy) { pick2.launch(types) }
        RecordingRow(stringResource(R.string.lab_test), state.test, state.busy) { pick3.launch(types) }
        OutlinedButton(onClick = model::useSynthetic, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.lab_use_synthetic))
        }
        Text(stringResource(R.string.lab_synthetic_note), style = MaterialTheme.typography.bodySmall, color = Muted)
        state.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }

        HorizontalDivider()
        Text(stringResource(R.string.lab_step_classifier), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.lab_classifier_body), color = Muted)
        Button(onClick = model::runClassifier, enabled = !state.busy && state.test != null && (state.training1 != null || state.training2 != null),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(if (state.busy) R.string.lab_running else R.string.lab_run_classifier))
        }
        state.evaluation?.let { ClassifierResults(it, state.test?.synthetic == true) }

        HorizontalDivider()
        Text(stringResource(R.string.lab_step_noise), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.lab_noise_body), color = Muted)
        Button(onClick = model::runNoise, enabled = !state.busy && state.training1 != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.lab_run_noise))
        }
        state.noise?.let { NoiseResults(it, state.noiseSource.orEmpty()) }

        HorizontalDivider()
        Text(stringResource(R.string.lab_limits_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.lab_limits), color = Muted)
        Text(stringResource(R.string.lab_citation), style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable
private fun RecordingRow(label: String, recording: SignalLab.Recording?, busy: Boolean, onImport: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                if (recording == null) Text(stringResource(R.string.lab_not_loaded), color = Muted, style = MaterialTheme.typography.bodySmall)
                else Text(stringResource(R.string.lab_loaded, recording.name, recording.values.size, SignalLab.windows(recording.values).size),
                    color = if (recording.synthetic) Sand else Cream, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = onImport, enabled = !busy) { Text(stringResource(R.string.lab_import)) }
        }
    }
}

@Composable
private fun ScoresTable(title: String, model: SignalLab.Scores, rule: SignalLab.Scores) {
    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        @Composable fun row(name: String, a: String, b: String, bold: Boolean = false) {
            Row(Modifier.fillMaxWidth()) {
                Text(name, Modifier.weight(1.3f), color = if (bold) Cream else Muted, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
                    style = MaterialTheme.typography.bodySmall)
                Text(a, Modifier.weight(1f), fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, style = MaterialTheme.typography.bodySmall)
                Text(b, Modifier.weight(1f), fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, style = MaterialTheme.typography.bodySmall)
            }
        }
        row(stringResource(R.string.lab_measure), stringResource(R.string.lab_ml), stringResource(R.string.lab_rule), bold = true)
        row("TP / FP / TN / FN", listOf(model.tp, model.fp, model.tn, model.fn).joinToString(" / "),
            listOf(rule.tp, rule.fp, rule.tn, rule.fn).joinToString(" / "))
        row(stringResource(R.string.lab_precision), f4(model.precision), f4(rule.precision))
        row(stringResource(R.string.lab_recall), f4(model.recall), f4(rule.recall))
        row(stringResource(R.string.lab_specificity), f4(model.specificity), f4(rule.specificity))
        row("F1", f4(model.f1), f4(rule.f1))
        row(stringResource(R.string.lab_balanced), f4(model.balancedAccuracy), f4(rule.balancedAccuracy))
        row(stringResource(R.string.lab_examples), model.total.toString(), rule.total.toString())
    }
}

@Composable
private fun Confusion(title: String, s: SignalLab.Scores, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        val cells = listOf(
            stringResource(R.string.lab_cell_tn) to s.tn, stringResource(R.string.lab_cell_fp) to s.fp,
            stringResource(R.string.lab_cell_fn) to s.fn, stringResource(R.string.lab_cell_tp) to s.tp
        )
        val most = maxOf(1, s.tn, s.fp, s.fn, s.tp)
        cells.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                pair.forEach { (label, n) ->
                    Column(Modifier.weight(1f).background(Mint.copy(alpha = 0.12f + 0.55f * n / most), RoundedCornerShape(8.dp)).padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(n.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(label, style = MaterialTheme.typography.labelSmall, color = Cream)
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassifierResults(e: SignalLab.Evaluation, synthetic: Boolean) {
    Text(stringResource(R.string.lab_counts, e.trainingExamples, e.test.size))
    if (synthetic) Text(stringResource(R.string.lab_synthetic_warning), color = Sand, style = MaterialTheme.typography.bodySmall)
    ScoresTable(stringResource(R.string.lab_this_run), e.model, e.rule)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Confusion(stringResource(R.string.lab_ml), e.model, Modifier.weight(1f))
        Confusion(stringResource(R.string.lab_rule), e.rule, Modifier.weight(1f))
    }
    ScoresTable(stringResource(R.string.lab_paper_table), SignalLab.ARCHIVED_MODEL, SignalLab.ARCHIVED_RULE)
    Text(stringResource(R.string.lab_compare_note), style = MaterialTheme.typography.bodySmall, color = Muted)
    WindowViewer(e)
}

@Composable
private fun WindowViewer(e: SignalLab.Evaluation) {
    val windows = e.test.size / 5
    var window by rememberSaveable { mutableIntStateOf(0) }
    var change by rememberSaveable { mutableIntStateOf(1) }
    val index = (window.coerceIn(0, windows - 1)) * 5 + change
    val example = e.test[index]
    val labels = listOf(R.string.lab_change_none, R.string.lab_change_noise, R.string.lab_change_spikes, R.string.lab_change_flat, R.string.lab_change_drift)
    Text(stringResource(R.string.lab_viewer_title), style = MaterialTheme.typography.titleMedium)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i, label -> FilterChip(change == i, { change = i }, label = { Text(stringResource(label)) }) }
    }
    Text(stringResource(R.string.lab_window_n, window + 1, windows), style = MaterialTheme.typography.bodySmall, color = Muted)
    Slider(value = window.toFloat(), onValueChange = { window = it.toInt() }, valueRange = 0f..(windows - 1).coerceAtLeast(1).toFloat(),
        steps = (windows - 2).coerceAtLeast(0))
    val description = stringResource(R.string.lab_viewer_a11y, window + 1, stringResource(labels[change]))
    Canvas(Modifier.fillMaxWidth().height(170.dp).background(Panel, RoundedCornerShape(16.dp)).semantics { contentDescription = description }) {
        val pad = 10.dp.toPx()
        val all = example.original + example.values
        val low = all.min(); val high = all.max(); val spread = (high - low).coerceAtLeast(1e-9)
        fun x(i: Int) = pad + i * (size.width - 2 * pad) / (example.values.size - 1)
        fun y(v: Double) = (size.height - pad - (v - low) / spread * (size.height - 2 * pad)).toFloat()
        for (i in 1 until example.original.size) drawLine(Muted.copy(alpha = 0.55f), Offset(x(i - 1), y(example.original[i - 1])), Offset(x(i), y(example.original[i])), 1.5.dp.toPx())
        if (example.altered) for (i in 1 until example.values.size) drawLine(Mint, Offset(x(i - 1), y(example.values[i - 1])), Offset(x(i), y(example.values[i])), 1.5.dp.toPx())
    }
    Text(stringResource(R.string.lab_viewer_legend), style = MaterialTheme.typography.bodySmall, color = Muted)
    val truth = stringResource(if (example.altered) R.string.lab_truth_altered else R.string.lab_truth_unchanged)
    val ml = stringResource(if (e.modelPredictions[index]) R.string.lab_says_altered else R.string.lab_says_unchanged)
    val rule = stringResource(if (e.rulePredictions[index]) R.string.lab_says_altered else R.string.lab_says_unchanged)
    Text(stringResource(R.string.lab_verdicts, truth, ml, rule))
    val f = SignalLab.features(example.values)
    Text(SignalLab.FEATURE_NAMES.indices.joinToString("\n") { SignalLab.FEATURE_NAMES[it] + " = " + f4(f[it]) },
        style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = Muted)
}

@Composable
private fun NoiseResults(points: List<SignalLab.NoisePoint>, source: String) {
    Text(stringResource(R.string.lab_noise_source, source), style = MaterialTheme.typography.bodySmall, color = Muted)
    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text(stringResource(R.string.lab_noise_scale), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.lab_noise_this_run), Modifier.weight(1.4f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.lab_noise_paper), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
        points.forEachIndexed { i, p ->
            Row {
                Text(String.format(Locale.US, "%.2f", p.scale), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Text(f3(p.meanSdDiff) + " ± " + f3(p.sdAcrossDraws), Modifier.weight(1.4f), style = MaterialTheme.typography.bodySmall)
                Text(SignalLab.ARCHIVED_NOISE.getOrNull(i)?.let(::f3) ?: "", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
    }
    val description = stringResource(R.string.lab_noise_a11y, f3(points.first().meanSdDiff), f3(points.last().meanSdDiff))
    Canvas(Modifier.fillMaxWidth().height(160.dp).background(Panel, RoundedCornerShape(16.dp)).semantics { contentDescription = description }) {
        val pad = 14.dp.toPx()
        val high = maxOf(points.maxOf { it.meanSdDiff + it.sdAcrossDraws }, SignalLab.ARCHIVED_NOISE.max())
        fun x(s: Double) = (pad + s * (size.width - 2 * pad)).toFloat()
        fun y(v: Double) = (size.height - pad - v / high * (size.height - 2 * pad)).toFloat()
        drawLine(Muted.copy(alpha = 0.4f), Offset(pad, size.height - pad), Offset(size.width - pad, size.height - pad), 1.dp.toPx())
        points.zipWithNext().forEach { (a, b) -> drawLine(Mint, Offset(x(a.scale), y(a.meanSdDiff)), Offset(x(b.scale), y(b.meanSdDiff)), 2.dp.toPx()) }
        points.forEachIndexed { i, p ->
            drawCircle(Mint, 3.5.dp.toPx(), Offset(x(p.scale), y(p.meanSdDiff)))
            SignalLab.ARCHIVED_NOISE.getOrNull(i)?.let { drawCircle(Sand, 3.dp.toPx(), Offset(x(p.scale), y(it)), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx())) }
        }
    }
    Text(stringResource(R.string.lab_noise_legend), style = MaterialTheme.typography.bodySmall, color = Muted)
}
