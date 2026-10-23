package ph.pusoalalay.app.domain

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

/** Descriptive statistics of the user's own recorded values. No clinical reference ranges are used. */
data class SeriesStats(val count: Int, val mean: Double, val sd: Double?, val median: Double, val min: Double, val max: Double)

/** Primary values (pulse, weight, systolic) and, for blood pressure, the diastolic values. */
data class KindSummary(val primary: SeriesStats, val secondary: SeriesStats?)

data class Coverage(val daysWithData: Int, val totalDays: Int)

/** Readings needed before a new reading is compared with the user's own history. */
const val MIN_HISTORY = 5
/** Number of previous readings used as the personal baseline. */
const val HISTORY_WINDOW = 30
/** Modified z-score cut-off recommended by Iglewicz and Hoaglin (1993). */
const val UNUSUAL_THRESHOLD = 3.5

fun median(values: List<Double>): Double {
    require(values.isNotEmpty())
    val sorted = values.sorted()
    val n = sorted.size
    return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
}

fun seriesStats(values: List<Double>): SeriesStats? {
    if (values.isEmpty()) return null
    val mean = values.average()
    val sd = if (values.size < 2) null else sqrt(values.sumOf { (it - mean) * (it - mean) } / (values.size - 1))
    return SeriesStats(values.size, mean, sd, median(values), values.min(), values.max())
}

fun summarize(series: List<HealthRecord>): KindSummary? {
    val primary = seriesStats(series.mapNotNull { it.value }) ?: return null
    return KindSummary(primary, seriesStats(series.mapNotNull { it.secondary }))
}

/** Distinct Manila calendar days with at least one reading, from the first reading's day through [endMillis]. */
fun coverage(times: List<Long>, endMillis: Long, zone: ZoneId = MANILA): Coverage? {
    if (times.isEmpty()) return null
    val days = times.map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }.toSet()
    val first = days.min()
    val last = maxOf(days.max(), Instant.ofEpochMilli(endMillis).atZone(zone).toLocalDate())
    return Coverage(days.size, ChronoUnit.DAYS.between(first, last).toInt() + 1)
}

/**
 * Modified z-score of [value] against [history]: (value - median) / (1.4826 * MAD).
 * Falls back to the mean absolute deviation when MAD is zero.
 */
fun robustDeviation(value: Double, history: List<Double>): Double? {
    if (history.isEmpty()) return null
    val m = median(history)
    val deviations = history.map { abs(it - m) }
    val mad = median(deviations)
    val scale = if (mad > 0) 1.4826 * mad else 1.2533 * deviations.average()
    if (scale == 0.0) return if (value == m) 0.0 else Double.POSITIVE_INFINITY
    return (value - m) / scale
}

/**
 * IDs of readings that are statistically far from the same person's previous readings of that type.
 * This is a review prompt, not a diagnosis or a clinical alert.
 */
fun unusualRecords(records: List<HealthRecord>): Set<String> {
    val flagged = mutableSetOf<String>()
    RecordKind.entries.filter { it != RecordKind.SYMPTOM }.forEach { kind ->
        val series = records.filter { it.kind == kind && it.value != null }.sortedBy { it.measuredAt }
        series.forEachIndexed { index, record ->
            if (index < MIN_HISTORY) return@forEachIndexed
            val prior = series.subList(maxOf(0, index - HISTORY_WINDOW), index)
            val primary = robustDeviation(record.value!!, prior.map { it.value!! })
            val priorSecondary = prior.mapNotNull { it.secondary }
            val secondary = record.secondary?.let { v ->
                if (priorSecondary.size >= MIN_HISTORY) robustDeviation(v, priorSecondary) else null
            }
            if ((primary != null && abs(primary) > UNUSUAL_THRESHOLD) ||
                (secondary != null && abs(secondary) > UNUSUAL_THRESHOLD)) flagged += record.id
        }
    }
    return flagged
}

fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.1f", value)

/** "120 / 80" for blood pressure, "72" otherwise. */
fun formatPair(primary: Double, secondary: Double?): String =
    if (secondary == null) formatNumber(primary) else formatNumber(primary) + " / " + formatNumber(secondary)
