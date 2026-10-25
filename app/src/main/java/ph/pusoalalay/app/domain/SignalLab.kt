package ph.pusoalalay.app.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Research module, separate from the health journal. It follows the course paper
 * "An Exploratory Comparative Evaluation of Machine Learning and Rule-Based Screening of
 * Artificial Disturbances in Recorded Pulse Signals" (23 Oct 2026):
 *
 * - 256-sample windows advanced in 128-sample steps (Section 3.1)
 * - each original window gives one unchanged example and four altered copies:
 *   additive noise, isolated spikes, a 48-point flat line, a linear baseline drift (Section 4.1)
 * - six features: SD(x), SD(d), median|d|, median|a|, fraction(|d| > 5 MAD(x)), fraction(d = 0)
 * - standardised nearest-centroid classifier fitted on the training recordings only (Section 3.2)
 * - fixed flat-line rule: altered if fraction(d = 0) > 0.12 (Section 3.2)
 * - precision, recall, specificity, F1, balanced accuracy (Section 3.3)
 * - noise sensitivity of SD(d): 8 scales x 30 draws on a 1,024-sample excerpt (Section 4.3)
 *
 * The paper does not state the strength of each disturbance or its random generator, so the settings
 * below are documented assumptions and results recomputed here can differ from the archived run.
 * Data quality only: not a heart-rate measurement, artifact adjudication or diagnosis.
 */
object SignalLab {
    const val WINDOW = 256
    const val STEP = 128
    const val FLAT_LENGTH = 48
    const val FLAT_RULE_THRESHOLD = 0.12
    const val SPIKE_MAD_FACTOR = 5.0
    const val PAPER_SEED = 47L
    const val NOISE_SEED = 20261023L
    const val EXCERPT = 1024
    const val NOISE_DRAWS = 30
    const val MIN_SAMPLES = WINDOW
    const val MAX_SAMPLES = 50_000
    /** Samples per second of the built-in synthetic practice signal (not part of the paper). */
    const val SYNTHETIC_RATE = 50
    val NOISE_SCALES = listOf(0.0, 0.05, 0.10, 0.20, 0.35, 0.50, 0.75, 1.00)
    val FEATURE_NAMES = listOf("SD(x)", "SD(d)", "median|d|", "median|a|", "fraction |d| > 5 MAD(x)", "fraction d = 0")

    enum class Change { NONE, NOISE, SPIKES, FLAT_LINE, DRIFT }

    /** Strengths of the four disturbances, relative to the window's own SD (assumptions; see class comment). */
    data class Settings(
        val noiseScale: Double = 0.35,
        val spikeCount: Int = 3,
        val spikeSize: Double = 4.0,
        val driftSize: Double = 1.0,
        val seed: Long = PAPER_SEED
    )

    class Example(val recording: String, val window: Int, val change: Change, val values: DoubleArray, val original: DoubleArray) {
        val altered: Boolean get() = change != Change.NONE
    }

    data class Scores(val tp: Int, val fp: Int, val tn: Int, val fn: Int) {
        val total: Int get() = tp + fp + tn + fn
        val accuracy: Double get() = ratio(tp + tn, total)
        val precision: Double get() = ratio(tp, tp + fp)
        val recall: Double get() = ratio(tp, tp + fn)
        val specificity: Double get() = ratio(tn, tn + fp)
        val f1: Double get() = if (precision + recall == 0.0) 0.0 else 2 * precision * recall / (precision + recall)
        val balancedAccuracy: Double get() = (recall + specificity) / 2
    }

    /** The archived held-out results reported in Table 1 of the paper, for side-by-side display. */
    val ARCHIVED_MODEL = Scores(tp = 129, fp = 33, tn = 8, fn = 35)
    val ARCHIVED_RULE = Scores(tp = 41, fp = 2, tn = 39, fn = 123)
    /** Table 2 of the paper: mean first-difference SD per noise scale on the pulse1 excerpt. */
    val ARCHIVED_NOISE = listOf(8.149, 8.915, 10.910, 16.674, 26.548, 37.229, 54.969, 72.977)

    class Evaluation(
        val trainingExamples: Int,
        val test: List<Example>,
        val modelPredictions: List<Boolean>,
        val rulePredictions: List<Boolean>,
        val model: Scores,
        val rule: Scores
    )

    data class NoisePoint(val scale: Double, val meanSdDiff: Double, val sdAcrossDraws: Double)

    class Recording(val name: String, val values: DoubleArray, val synthetic: Boolean = false)

    private fun ratio(a: Int, b: Int) = if (b == 0) 0.0 else a.toDouble() / b

    /** Population standard deviation, as in Eq. (2) of the paper. */
    fun sd(x: DoubleArray): Double {
        if (x.isEmpty()) return 0.0
        val mean = x.average()
        return sqrt(x.sumOf { (it - mean) * (it - mean) } / x.size)
    }

    fun median(x: DoubleArray): Double {
        require(x.isNotEmpty())
        val s = x.sorted()
        val n = s.size
        return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2.0
    }

    fun diff(x: DoubleArray): DoubleArray = DoubleArray(max(0, x.size - 1)) { x[it + 1] - x[it] }

    private fun gaussian(rng: Random): Double {
        val u1 = rng.nextDouble().coerceAtLeast(1e-12)
        return sqrt(-2.0 * ln(u1)) * cos(2 * PI * rng.nextDouble())
    }

    /** Windows of [WINDOW] samples starting every [STEP] samples; a final partial window is dropped. */
    fun windows(signal: DoubleArray): List<DoubleArray> {
        if (signal.size < WINDOW) return emptyList()
        return (0..(signal.size - WINDOW) / STEP).map { signal.copyOfRange(it * STEP, it * STEP + WINDOW) }
    }

    /** One altered copy of a window. Strengths scale with the window's own SD, so recording units do not matter. */
    fun perturb(window: DoubleArray, change: Change, rng: Random, settings: Settings): DoubleArray {
        val out = window.copyOf()
        val scale = sd(window).takeIf { it > 0 } ?: 1.0
        when (change) {
            Change.NONE -> Unit
            Change.NOISE -> for (i in out.indices) out[i] += gaussian(rng) * settings.noiseScale * scale
            Change.SPIKES -> repeat(settings.spikeCount) {
                val i = 1 + rng.nextInt(out.size - 2)
                out[i] += (if (rng.nextBoolean()) 1 else -1) * settings.spikeSize * scale
            }
            Change.FLAT_LINE -> {
                val start = rng.nextInt(out.size - FLAT_LENGTH + 1)
                val level = out[start]
                for (i in start until start + FLAT_LENGTH) out[i] = level
            }
            Change.DRIFT -> {
                val total = (if (rng.nextBoolean()) 1 else -1) * settings.driftSize * scale
                for (i in out.indices) out[i] += total * i / (out.size - 1)
            }
        }
        return out
    }

    /** Every window yields one unchanged example and four altered copies (4:1 altered prevalence). */
    fun examples(recording: Recording, rng: Random, settings: Settings): List<Example> =
        windows(recording.values).flatMapIndexed { index, window ->
            Change.entries.map { change -> Example(recording.name, index, change, perturb(window, change, rng, settings), window) }
        }

    /** The six features of Section 3.1. */
    fun features(x: DoubleArray): DoubleArray {
        val d = diff(x)
        val a = diff(d)
        val med = median(x)
        val mad = median(DoubleArray(x.size) { abs(x[it] - med) })
        val absD = DoubleArray(d.size) { abs(d[it]) }
        return doubleArrayOf(
            sd(x),
            sd(d),
            median(absD),
            median(DoubleArray(a.size) { abs(a[it]) }),
            absD.count { it > SPIKE_MAD_FACTOR * mad }.toDouble() / d.size,
            d.count { it == 0.0 }.toDouble() / d.size
        )
    }

    /** Fixed flat-line rule: altered when the fraction of exactly zero successive differences exceeds 0.12. */
    fun flatLineRule(features: DoubleArray): Boolean = features[5] > FLAT_RULE_THRESHOLD

    /** Standardised nearest-centroid classifier, Eq. (4). Means and SDs come from the training set only. */
    class NearestCentroid private constructor(
        private val mean: DoubleArray, private val scale: DoubleArray,
        private val unchanged: DoubleArray?, private val altered: DoubleArray?
    ) {
        private fun z(x: DoubleArray) = DoubleArray(x.size) { (x[it] - mean[it]) / scale[it] }
        private fun squared(a: DoubleArray, b: DoubleArray) = a.indices.sumOf { (a[it] - b[it]) * (a[it] - b[it]) }

        fun predict(x: DoubleArray): Boolean {
            if (unchanged == null || altered == null) return altered != null
            val v = z(x)
            return squared(v, altered) < squared(v, unchanged)
        }

        companion object {
            fun train(x: List<DoubleArray>, labels: List<Boolean>): NearestCentroid {
                require(x.isNotEmpty() && x.size == labels.size)
                val dims = x.first().size
                val mean = DoubleArray(dims) { k -> x.sumOf { it[k] } / x.size }
                val scale = DoubleArray(dims) { k -> sd(DoubleArray(x.size) { x[it][k] }).takeIf { it > 0 } ?: 1.0 }
                fun centroid(label: Boolean): DoubleArray? {
                    val rows = x.filterIndexed { i, _ -> labels[i] == label }
                    if (rows.isEmpty()) return null
                    return DoubleArray(dims) { k -> rows.sumOf { (it[k] - mean[k]) / scale[k] } / rows.size }
                }
                return NearestCentroid(mean, scale, centroid(false), centroid(true))
            }
        }
    }

    fun score(actual: List<Boolean>, predicted: List<Boolean>): Scores {
        var tp = 0; var fp = 0; var tn = 0; var fn = 0
        actual.zip(predicted).forEach { (a, p) ->
            when {
                a && p -> tp++
                !a && p -> fp++
                !a && !p -> tn++
                else -> fn++
            }
        }
        return Scores(tp, fp, tn, fn)
    }

    /** Train on [training] recordings, test on the held-out [test] recording (paper: pulse1 + pulse3 -> pulse5). */
    fun evaluate(training: List<Recording>, test: Recording, settings: Settings = Settings()): Evaluation {
        require(training.isNotEmpty()) { "No training recording" }
        val rng = Random(settings.seed)
        val trainExamples = training.flatMap { examples(it, rng, settings) }
        val testExamples = examples(test, rng, settings)
        require(trainExamples.isNotEmpty() && testExamples.isNotEmpty()) { "Recording shorter than one window" }
        val model = NearestCentroid.train(trainExamples.map { features(it.values) }, trainExamples.map { it.altered })
        val testFeatures = testExamples.map { features(it.values) }
        val modelPredictions = testFeatures.map { model.predict(it) }
        val rulePredictions = testFeatures.map { flatLineRule(it) }
        val actual = testExamples.map { it.altered }
        return Evaluation(trainExamples.size, testExamples, modelPredictions, rulePredictions,
            score(actual, modelPredictions), score(actual, rulePredictions))
    }

    /** Section 4.3: Gaussian noise at each scale (x the excerpt's SD), [draws] draws each; SD of first differences. */
    fun noiseSensitivity(excerpt: DoubleArray, scales: List<Double> = NOISE_SCALES, draws: Int = NOISE_DRAWS,
        seed: Long = NOISE_SEED): List<NoisePoint> {
        val rng = Random(seed)
        val base = sd(excerpt)
        return scales.map { scale ->
            val values = DoubleArray(draws) {
                val noisy = DoubleArray(excerpt.size) { i -> excerpt[i] + gaussian(rng) * scale * base }
                sd(diff(noisy))
            }
            NoisePoint(scale, values.average(), sd(values))
        }
    }

    /** One value per line; uses the last number on each line, so "time,value" files also work. */
    fun parseRecording(text: String): DoubleArray? {
        val values = text.lineSequence().mapNotNull { line ->
            line.split(',', ';', '\t', ' ').mapNotNull { it.trim().toDoubleOrNull() }.lastOrNull()
        }.filter { it.isFinite() }.take(MAX_SAMPLES).toList()
        return if (values.size >= MIN_SAMPLES) values.toDoubleArray() else null
    }

    /** Practice signal only (not part of the paper): pulse-like beats rounded to whole units like a sensor. */
    fun syntheticPulse(seconds: Int = 110, bpm: Double = 72.0, seed: Long = 7L): DoubleArray {
        val rng = Random(seed)
        val n = seconds * SYNTHETIC_RATE
        val out = DoubleArray(n)
        var beatStart = 0.0
        while (beatStart < n) {
            val period = SYNTHETIC_RATE * 60.0 / (bpm + rng.nextDouble(-3.0, 3.0))
            val amp = 1.0 + rng.nextDouble(-0.05, 0.05)
            for (i in max(0, beatStart.toInt()) until min(n, (beatStart + period).toInt() + 1)) {
                val t = (i - beatStart) / period
                val main = (t - 0.15) / 0.06
                val second = (t - 0.42) / 0.08
                out[i] += amp * (exp(-main * main) + 0.4 * exp(-second * second))
            }
            beatStart += period
        }
        return DoubleArray(n) { Math.round(520 + 120 * out[it] + 8 * sin(2 * PI * 0.25 * it / SYNTHETIC_RATE)).toDouble() }
    }
}
