package ph.pusoalalay.app.domain

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class SignalLabTest {
    private fun synthetic(seed: Long) = SignalLab.Recording("synthetic-$seed", SignalLab.syntheticPulse(seed = seed), synthetic = true)

    @Test fun windowsFollowThePaper() {
        // 5,424 values (the size of pulse5.csv) -> 41 windows of 256 advanced by 128.
        assertEquals(41, SignalLab.windows(DoubleArray(5424) { it.toDouble() }).size)
        assertEquals(0, SignalLab.windows(DoubleArray(255)).size)
        val w = SignalLab.windows(DoubleArray(512) { it.toDouble() })
        assertEquals(3, w.size)
        assertEquals(128.0, w[1][0], 0.0)
    }
    @Test fun eachWindowGivesOneUnchangedAndFourAlteredExamples() {
        val ex = SignalLab.examples(synthetic(1), Random(47), SignalLab.Settings())
        assertEquals(0, ex.size % 5)
        assertEquals(ex.size / 5, ex.count { !it.altered })
        ex.filter { it.change == SignalLab.Change.NONE }.forEach { assertArrayEquals(it.original, it.values, 0.0) }
        ex.filter { it.altered }.forEach { assertFalse(it.original.contentEquals(it.values)) }
    }
    @Test fun sixFeaturesMatchTheirDefinitions() {
        val x = doubleArrayOf(1.0, 1.0, 1.0, 4.0, 2.0)
        val f = SignalLab.features(x)
        assertEquals(6, f.size)
        assertEquals(SignalLab.sd(x), f[0], 1e-12)
        assertEquals(SignalLab.sd(doubleArrayOf(0.0, 0.0, 3.0, -2.0)), f[1], 1e-12)
        assertEquals(1.0, f[2], 1e-12)            // median of |d| = median(0, 0, 2, 3) = 1
        assertEquals(3.0, f[3], 1e-12)            // second differences 0, 3, -5 -> median of |a| = 3
        assertEquals(0.5, f[4], 1e-12)            // MAD(x) = 0, so |d| > 0 counts: 2 of 4 differences
        assertEquals(0.5, f[5], 1e-12)            // 2 of 4 differences are exactly zero
    }
    @Test fun flatLineCopiesTriggerTheRule() {
        val window = DoubleArray(256) { 500.0 + 40 * kotlin.math.sin(it / 6.0) + it % 3 }
        val flat = SignalLab.perturb(window, SignalLab.Change.FLAT_LINE, Random(1), SignalLab.Settings())
        assertTrue(SignalLab.flatLineRule(SignalLab.features(flat)))
        assertFalse(SignalLab.flatLineRule(SignalLab.features(window)))
    }
    @Test fun populationSdAsInEquationTwo() {
        assertEquals(2.0, SignalLab.sd(doubleArrayOf(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0)), 1e-12)
    }
    @Test fun archivedTableOneMetricsAreConsistent() {
        val m = SignalLab.ARCHIVED_MODEL
        assertEquals(0.7963, m.precision, 5e-5)
        assertEquals(0.7866, m.recall, 5e-5)
        assertEquals(0.1951, m.specificity, 5e-5)
        assertEquals(0.7914, m.f1, 5e-5)
        assertEquals(0.4909, m.balancedAccuracy, 5e-5)
        val r = SignalLab.ARCHIVED_RULE
        assertEquals(0.9535, r.precision, 5e-5)
        assertEquals(0.2500, r.recall, 5e-5)
        assertEquals(0.9512, r.specificity, 5e-5)
        assertEquals(0.3961, r.f1, 5e-5)
        assertEquals(0.6006, r.balancedAccuracy, 5e-5)
        assertEquals(205, m.total)
    }
    @Test fun heldOutEvaluationKeepsTheFourToOneDesign() {
        val e = SignalLab.evaluate(listOf(synthetic(1), synthetic(2)), synthetic(3))
        assertEquals(e.test.size, e.model.total)
        assertEquals(e.test.size / 5, e.model.tn + e.model.fp)
        assertEquals(e.test.size * 4 / 5, e.model.tp + e.model.fn)
        assertEquals(e.test.size, e.rulePredictions.size)
        val again = SignalLab.evaluate(listOf(synthetic(1), synthetic(2)), synthetic(3))
        assertEquals(e.model, again.model)
    }
    @Test fun nearestCentroidUsesStandardisedDistances() {
        val x = listOf(doubleArrayOf(0.0, 0.0), doubleArrayOf(0.2, 0.1), doubleArrayOf(10.0, 10.0), doubleArrayOf(9.8, 10.1))
        val model = SignalLab.NearestCentroid.train(x, listOf(false, false, true, true))
        assertTrue(model.predict(doubleArrayOf(8.0, 9.0)))
        assertFalse(model.predict(doubleArrayOf(1.0, 0.5)))
    }
    @Test fun noiseSensitivityStartsAtTheUnalteredRoughnessAndRises() {
        val excerpt = SignalLab.syntheticPulse().copyOfRange(0, SignalLab.EXCERPT)
        val points = SignalLab.noiseSensitivity(excerpt)
        assertEquals(8, points.size)
        assertEquals(SignalLab.sd(SignalLab.diff(excerpt)), points.first().meanSdDiff, 1e-9)
        assertTrue(points.zipWithNext().all { (a, b) -> b.meanSdDiff > a.meanSdDiff })
    }
    @Test fun parsesOneValuePerLineOrTimeValuePairs() {
        val plain = (0 until 300).joinToString("\r\n") { (500 + it % 9).toString() }
        assertEquals(300, SignalLab.parseRecording(plain)!!.size)
        val pairs = "time,ppg\n" + (0 until 300).joinToString("\n") { "$it,${it % 7}.5" }
        assertEquals(0.5, SignalLab.parseRecording(pairs)!![0], 0.0)
        assertNull(SignalLab.parseRecording("1\n2\n3"))
    }
}
