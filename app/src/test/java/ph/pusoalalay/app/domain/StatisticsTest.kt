package ph.pusoalalay.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class StatisticsTest {
    private val start = Instant.parse("2026-10-01T01:00:00Z").toEpochMilli()
    private val day = 24 * 60 * 60_000L
    private fun pulse(i: Int, value: Double) = HealthRecord(id = "p$i", kind = RecordKind.HEART_RATE,
        measuredAt = start + i * day, value = value)

    @Test fun descriptiveStatistics() {
        val s = seriesStats(listOf(70.0, 72.0, 74.0, 76.0))!!
        assertEquals(4, s.count)
        assertEquals(73.0, s.mean, 1e-9)
        assertEquals(2.5819888974716, s.sd!!, 1e-9)
        assertEquals(73.0, s.median, 1e-9)
        assertEquals(70.0, s.min, 0.0)
        assertEquals(76.0, s.max, 0.0)
        assertNull(seriesStats(listOf(80.0))!!.sd)
        assertNull(seriesStats(emptyList()))
    }
    @Test fun medianOddAndEven() {
        assertEquals(3.0, median(listOf(5.0, 1.0, 3.0)), 0.0)
        assertEquals(2.5, median(listOf(4.0, 1.0, 2.0, 3.0)), 0.0)
    }
    @Test fun coverageCountsDistinctManilaDays() {
        // 1 Oct 09:00 and 1 Oct 20:00 Manila are the same day; 3 Oct is another. End 5 Oct -> 5 days.
        val times = listOf(start, start + 11 * 60 * 60_000L, start + 2 * day)
        assertEquals(Coverage(2, 5), coverage(times, start + 4 * day))
        assertNull(coverage(emptyList(), start))
    }
    @Test fun flagsOnlyReadingsFarFromPersonalBaseline() {
        val values = listOf(70.0, 72.0, 71.0, 69.0, 73.0, 72.0, 118.0, 71.0)
        val records = values.mapIndexed { i, v -> pulse(i, v) }
        assertEquals(setOf("p6"), unusualRecords(records))
    }
    @Test fun needsEnoughHistoryBeforeFlagging() {
        val records = listOf(70.0, 71.0, 140.0).mapIndexed { i, v -> pulse(i, v) }
        assertTrue(unusualRecords(records).isEmpty())
    }
    @Test fun identicalHistoryFlagsAnyChangeButNotRepeats() {
        val records = listOf(60.0, 60.0, 60.0, 60.0, 60.0, 60.0, 61.0).mapIndexed { i, v -> pulse(i, v) }
        assertEquals(setOf("p6"), unusualRecords(records))
        assertEquals(0.0, robustDeviation(60.0, listOf(60.0, 60.0))!!, 0.0)
    }
    @Test fun bloodPressureChecksDiastolicToo() {
        val bp = listOf(120.0 to 80.0, 118.0 to 79.0, 121.0 to 81.0, 119.0 to 80.0, 122.0 to 78.0, 120.0 to 105.0)
            .mapIndexed { i, (s, d) -> HealthRecord(id = "b$i", kind = RecordKind.BLOOD_PRESSURE,
                measuredAt = start + i * day, value = s, secondary = d) }
        assertEquals(setOf("b5"), unusualRecords(bp))
    }
    @Test fun formatting() {
        assertEquals("72", formatNumber(72.0))
        assertEquals("63.4", formatNumber(63.44))
        assertEquals("120 / 80", formatPair(120.0, 80.0))
    }
}
