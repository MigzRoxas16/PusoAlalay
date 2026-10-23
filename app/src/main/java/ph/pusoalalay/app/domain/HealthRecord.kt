package ph.pusoalalay.app.domain

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

val MANILA: ZoneId = ZoneId.of("Asia/Manila")

enum class RecordKind { HEART_RATE, BLOOD_PRESSURE, WEIGHT, SYMPTOM }
enum class MeasurementSource { DEVICE_MANUAL, SELF_REPORTED }
enum class Symptom { FATIGUE, DIZZINESS, PALPITATIONS, SHORTNESS_OF_BREATH, SWELLING, CHEST_DISCOMFORT }

data class HealthRecord(
    val id: String = UUID.randomUUID().toString(),
    val kind: RecordKind,
    val measuredAt: Long,
    val value: Double? = null,
    val secondary: Double? = null,
    val note: String = "",
    val source: MeasurementSource = if (kind == RecordKind.SYMPTOM)
        MeasurementSource.SELF_REPORTED else MeasurementSource.DEVICE_MANUAL,
    val createdAt: Long = System.currentTimeMillis(),
    val symptoms: Set<Symptom> = emptySet(),
    /** 1 = mild, 2 = moderate, 3 = severe, as reported by the user. */
    val severity: Int? = null
)

enum class ValidationIssue { REQUIRED, INVALID_NUMBER, BP_ORDER, FUTURE_DATE, NOTE_TOO_LONG }

/** Input sanity bounds are storage/entry guards, never clinical normal ranges. */
object RecordValidator {
    fun validate(record: HealthRecord, now: Long = System.currentTimeMillis()): ValidationIssue? {
        if (record.note.length > 2000) return ValidationIssue.NOTE_TOO_LONG
        if (record.measuredAt <= 0 || record.measuredAt > now + 60_000L) return ValidationIssue.FUTURE_DATE
        if (record.kind == RecordKind.SYMPTOM) {
            if (record.severity != null && record.severity !in 1..3) return ValidationIssue.INVALID_NUMBER
            return if (record.note.isBlank() && record.symptoms.isEmpty()) ValidationIssue.REQUIRED else null
        }
        val value = record.value ?: return ValidationIssue.REQUIRED
        if (!value.isFinite() || value <= 0 || value > 999) return ValidationIssue.INVALID_NUMBER
        if (record.kind != RecordKind.WEIGHT && value % 1.0 != 0.0) return ValidationIssue.INVALID_NUMBER
        if (record.kind == RecordKind.BLOOD_PRESSURE) {
            val secondary = record.secondary ?: return ValidationIssue.REQUIRED
            if (!secondary.isFinite() || secondary <= 0 || secondary > 999 || secondary % 1.0 != 0.0)
                return ValidationIssue.INVALID_NUMBER
            if (secondary >= value) return ValidationIssue.BP_ORDER
        }
        return null
    }
}

fun nextReminder(now: Instant, hour: Int, minute: Int): ZonedDateTime {
    require(hour in 0..23 && minute in 0..59)
    val current = now.atZone(MANILA)
    val today = current.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
    return if (today.isAfter(current)) today else today.plusDays(1)
}

fun HealthRecord.numberText(): String = when (kind) {
    RecordKind.BLOOD_PRESSURE -> "${value?.toInt()} / ${secondary?.toInt()}"
    RecordKind.HEART_RATE -> value?.toInt()?.toString().orEmpty()
    RecordKind.WEIGHT -> value?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }.orEmpty()
    RecordKind.SYMPTOM -> ""
}

fun RecordKind.unit(): String = when (this) {
    RecordKind.HEART_RATE -> "bpm"
    RecordKind.BLOOD_PRESSURE -> "mmHg"
    RecordKind.WEIGHT -> "kg"
    RecordKind.SYMPTOM -> ""
}

/** Quote every cell, preserve multiline notes, prevent spreadsheet formula injection. */
fun csvCell(text: String): String {
    val guarded = if (text.trimStart().firstOrNull() in listOf('=', '+', '-', '@') ||
        text.startsWith('\t') || text.startsWith('\r')) "'$text" else text
    val escaped = guarded.replace("\"", "\"\"")
    return "\"" + escaped + "\""
}

fun csvReport(records: List<HealthRecord>): String {
    val header = listOf("record_id", "measured_at_UTC", "measured_at_Asia_Manila", "type", "value", "secondary_value", "unit", "source", "symptoms", "severity", "notes")
    val rows = records.sortedByDescending { it.measuredAt }.map { r ->
        listOf(r.id, Instant.ofEpochMilli(r.measuredAt).toString(),
            Instant.ofEpochMilli(r.measuredAt).atZone(MANILA).toOffsetDateTime().toString(), r.kind.name,
            r.value?.toString().orEmpty(), r.secondary?.toString().orEmpty(), r.kind.unit(), r.source.name,
            r.symptoms.sortedBy { it.ordinal }.joinToString(";") { it.name }, r.severity?.toString().orEmpty(), r.note)
    }
    return (listOf(header) + rows).joinToString("\r\n", postfix = "\r\n") { it.joinToString(",", transform = ::csvCell) }
}
