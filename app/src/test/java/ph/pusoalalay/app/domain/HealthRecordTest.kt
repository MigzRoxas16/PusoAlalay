package ph.pusoalalay.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class HealthRecordTest {
    private val now = Instant.parse("2026-10-23T10:00:00Z").toEpochMilli()
    private fun pulse(value: Double? = 72.0) = HealthRecord(kind = RecordKind.HEART_RATE,
        measuredAt = now, value = value)

    @Test fun acceptsMeasurementWithoutAssigningMedicalCategory() {
        assertNull(RecordValidator.validate(pulse(250.0), now))
    }
    @Test fun rejectsNonfiniteNumbers() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach {
            assertEquals(ValidationIssue.INVALID_NUMBER, RecordValidator.validate(pulse(it), now))
        }
    }
    @Test fun requiresMeasurement() {
        assertEquals(ValidationIssue.REQUIRED, RecordValidator.validate(pulse(null), now))
    }
    @Test fun wholePulseAndPositiveValuesOnly() {
        listOf(-5.0, 0.0, 72.5, 1000.0).forEach {
            assertEquals(ValidationIssue.INVALID_NUMBER, RecordValidator.validate(pulse(it), now))
        }
    }
    @Test fun acceptsDecimalWeight() {
        assertNull(RecordValidator.validate(HealthRecord(kind = RecordKind.WEIGHT, measuredAt = now, value = 63.4), now))
    }
    @Test fun rejectsReversedBloodPressure() {
        val record = HealthRecord(kind = RecordKind.BLOOD_PRESSURE, measuredAt = now, value = 80.0, secondary = 120.0)
        assertEquals(ValidationIssue.BP_ORDER, RecordValidator.validate(record, now))
    }
    @Test fun bloodPressureRequiresBothValues() {
        assertEquals(ValidationIssue.REQUIRED, RecordValidator.validate(HealthRecord(kind = RecordKind.BLOOD_PRESSURE,
            measuredAt = now, value = 120.0), now))
    }
    @Test fun symptomRequiresMeaningfulNote() {
        assertEquals(ValidationIssue.REQUIRED, RecordValidator.validate(HealthRecord(kind = RecordKind.SYMPTOM,
            measuredAt = now, note = "   "), now))
        assertNull(RecordValidator.validate(HealthRecord(kind = RecordKind.SYMPTOM, measuredAt = now, note = "Pagod pagkatapos maglakad."), now))
    }
    @Test fun rejectsFutureAndExcessiveNotes() {
        assertEquals(ValidationIssue.FUTURE_DATE, RecordValidator.validate(pulse().copy(measuredAt = now + 120_000), now))
        assertEquals(ValidationIssue.NOTE_TOO_LONG, RecordValidator.validate(pulse().copy(note = "x".repeat(2001)), now))
    }
    @Test fun nextReminderUsesManilaEvenWhenDeviceHasAnotherTimezone() {
        assertEquals(Instant.parse("2026-10-23T11:00:00Z"), nextReminder(Instant.ofEpochMilli(now), 19, 0).toInstant())
    }
    @Test fun reminderAtOrAfterTargetMovesToNextDay() {
        assertEquals(Instant.parse("2026-10-24T11:00:00Z"),
            nextReminder(Instant.parse("2026-10-23T11:00:00Z"), 19, 0).toInstant())
        assertEquals(Instant.parse("2026-10-24T11:00:00Z"),
            nextReminder(Instant.parse("2026-10-23T12:00:00Z"), 19, 0).toInstant())
    }
    @Test fun csvEscapesMultilineQuotesAndFormulaCells() {
        assertEquals("\"one, \"\"two\"\"\nthree\"", csvCell("one, \"two\"\nthree"))
        listOf("=1+1", "+SUM(A1)", "-2+3", "@NOW()", "  =1", "\t=1").forEach {
            assertTrue(csvCell(it).startsWith("\"'"))
        }
    }
    @Test fun csvPreservesOriginAndBothTimezones() {
        val text = csvReport(listOf(pulse().copy(note = "Pagod, pero nakapagpahinga.")))
        assertTrue(text.contains("2026-10-23T10:00:00Z"))
        assertTrue(text.contains("2026-10-23T18:00+08:00"))
        assertTrue(text.contains("DEVICE_MANUAL"))
        assertTrue(text.contains("\"Pagod, pero nakapagpahinga.\""))
    }
}
