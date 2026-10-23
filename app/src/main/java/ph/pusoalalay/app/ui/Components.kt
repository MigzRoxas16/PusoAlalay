package ph.pusoalalay.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ph.pusoalalay.app.R
import ph.pusoalalay.app.domain.*
import ph.pusoalalay.app.export.ReportExporter
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

@StringRes
fun RecordKind.label(): Int = when (this) {
    RecordKind.HEART_RATE -> R.string.heart_rate
    RecordKind.BLOOD_PRESSURE -> R.string.blood_pressure
    RecordKind.WEIGHT -> R.string.weight
    RecordKind.SYMPTOM -> R.string.symptoms
}
fun RecordKind.icon(): ImageVector = when (this) {
    RecordKind.HEART_RATE -> Icons.Outlined.FavoriteBorder
    RecordKind.BLOOD_PRESSURE -> Icons.Outlined.MonitorHeart
    RecordKind.WEIGHT -> Icons.Outlined.MonitorWeight
    RecordKind.SYMPTOM -> Icons.Outlined.EditNote
}
@StringRes
fun Symptom.label(): Int = ReportExporter.symptomLabel(this)

@StringRes
fun severityLabel(level: Int): Int = ReportExporter.severityLabel(level)

/** "Fatigue · Dizziness · Moderate" for symptom entries; empty when nothing was ticked. */
@Composable
fun symptomSummary(record: HealthRecord): String {
    val names = record.symptoms.sortedBy { it.ordinal }.map { stringResource(it.label()) }
    val severity = record.severity?.let { stringResource(severityLabel(it)) }
    return (names + listOfNotNull(severity)).joinToString(" · ")
}

fun formatTime(millis: Long, language: String, pattern: String = "dd MMM yyyy, h:mm a"): String =
    DateTimeFormatter.ofPattern(pattern, Locale.forLanguageTag(language)).withZone(MANILA).format(Instant.ofEpochMilli(millis))

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
}

@Composable
fun RecordRow(record: HealthRecord, language: String, unusual: Boolean = false, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(record.kind.icon(), contentDescription = null, tint = Mint, modifier = Modifier.padding(top = 4.dp).size(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(record.kind.label()), style = MaterialTheme.typography.titleMedium)
                if (record.kind != RecordKind.SYMPTOM)
                    Text(record.numberText() + " " + record.kind.unit(), style = MaterialTheme.typography.titleLarge)
                else {
                    val summary = symptomSummary(record)
                    if (summary.isNotEmpty()) Text(summary, style = MaterialTheme.typography.titleSmall)
                    if (record.note.isNotBlank())
                        Text(record.note, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                if (unusual) Text(stringResource(R.string.unusual_tag), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary)
                Text(formatTime(record.measuredAt, language), style = MaterialTheme.typography.bodySmall, color = Muted)
                Text(stringResource(if (record.source == MeasurementSource.SELF_REPORTED)
                    R.string.source_self else R.string.source_device), style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = Muted)
        }
    }
}

@Composable
fun EmptyJournal(onAdd: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(24.dp)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.AutoMirrored.Outlined.MenuBook, null, tint = Mint, modifier = Modifier.size(36.dp))
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.empty_body), color = Muted)
        Button(onClick = onAdd, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.add_record)) }
    }
}

@Composable
fun Trend(records: List<HealthRecord>, kind: RecordKind, language: String) {
    val data = records.filter { it.kind == kind && it.value != null }.sortedBy { it.measuredAt }.takeLast(20)
    if (data.size < 2 || kind == RecordKind.SYMPTOM) return
    val kindName = stringResource(kind.label())
    val description = stringResource(R.string.trend_accessibility, kindName, data.size,
        data.first().numberText(), data.last().numberText())
    val values = data.flatMap { listOfNotNull(it.value, it.secondary) }
    val low = values.min()
    val high = values.max()
    val spread = (high - low).coerceAtLeast(1.0)
    val firstTime = data.first().measuredAt
    val duration = (data.last().measuredAt - firstTime).coerceAtLeast(1)
    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(20.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.trend_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.trend_body), style = MaterialTheme.typography.bodySmall, color = Muted)
        Text("$high " + kind.unit(), style = MaterialTheme.typography.labelMedium, color = Muted)
        Canvas(Modifier.fillMaxWidth().height(150.dp).semantics { contentDescription = description }) {
            val left = 8.dp.toPx()
            val bottom = size.height - 8.dp.toPx()
            val top = 8.dp.toPx()
            val width = (size.width - 2 * left).coerceAtLeast(1f)
            fun point(record: HealthRecord, value: Double) = Offset(
                left + ((record.measuredAt - firstTime).toDouble() / duration * width).toFloat(),
                bottom - ((value - low) / spread * (bottom - top)).toFloat())
            for (i in 0..3) {
                val y = top + (bottom - top) * i / 3
                drawLine(Muted.copy(alpha = 0.18f), Offset(left, y), Offset(size.width - left, y), 1.dp.toPx())
            }
            // Connecting lines show only recorded values, with actual timestamp spacing.
            data.zipWithNext().forEach { (a, b) ->
                drawLine(Mint, point(a, a.value!!), point(b, b.value!!), 2.dp.toPx())
                if (a.secondary != null && b.secondary != null)
                    drawLine(androidx.compose.ui.graphics.Color(0xFFDBC79E), point(a, a.secondary), point(b, b.secondary), 2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx())))
            }
            data.forEach {
                drawCircle(Mint, 4.dp.toPx(), point(it, it.value!!))
                it.secondary?.let { value -> drawCircle(androidx.compose.ui.graphics.Color(0xFFDBC79E), 4.dp.toPx(), point(it, value)) }
            }
        }
        Text("$low " + kind.unit(), style = MaterialTheme.typography.labelMedium, color = Muted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(data.first().measuredAt, language, "dd MMM"), style = MaterialTheme.typography.bodySmall, color = Muted)
            Text(formatTime(data.last().measuredAt, language, "dd MMM"), style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        if (kind == RecordKind.BLOOD_PRESSURE) Text(stringResource(R.string.trend_legend), style = MaterialTheme.typography.bodySmall)
    }
}

/** Personal descriptive statistics for one measurement type. Not compared with any clinical range. */
@Composable
fun StatsCard(records: List<HealthRecord>, kind: RecordKind, unusualCount: Int) {
    val series = records.filter { it.kind == kind && it.value != null }.sortedBy { it.measuredAt }
    val summary = summarize(series) ?: return
    val p = summary.primary
    val s = summary.secondary
    val unit = kind.unit()
    val days = coverage(series.map { it.measuredAt }, System.currentTimeMillis())
    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(20.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.stats_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.stats_count, p.count))
        Text(stringResource(R.string.stats_average, formatPair(p.mean, s?.mean), unit))
        p.sd?.let { Text(stringResource(R.string.stats_variation, formatPair(it, s?.sd))) }
        Text(stringResource(R.string.stats_median, formatPair(p.median, s?.median)))
        Text(stringResource(R.string.stats_range, formatPair(p.min, s?.min), formatPair(p.max, s?.max), unit))
        days?.let { Text(stringResource(R.string.stats_coverage, it.daysWithData, it.totalDays)) }
        Text(stringResource(R.string.stats_unusual, unusualCount),
            color = if (unusualCount > 0) MaterialTheme.colorScheme.secondary else Cream)
        Text(stringResource(R.string.stats_explain), style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}
