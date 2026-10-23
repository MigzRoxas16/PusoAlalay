package ph.pusoalalay.app.export

import android.content.Context
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import ph.pusoalalay.app.R
import ph.pusoalalay.app.data.localizedContext
import ph.pusoalalay.app.domain.*
import java.io.OutputStream
import java.time.Instant
import java.time.format.DateTimeFormatter

object ReportExporter {
    fun write(context: Context, uri: Uri, records: List<HealthRecord>, pdf: Boolean, owner: String = "") {
        require(records.isNotEmpty())
        context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
            if (pdf) writePdf(localizedContext(context), stream, records, owner)
            else stream.write(("\uFEFF" + csvReport(records)).toByteArray(Charsets.UTF_8))
        } ?: error("Cannot open destination")
    }

    private fun kindLabel(kind: RecordKind) = when (kind) {
        RecordKind.HEART_RATE -> R.string.heart_rate
        RecordKind.BLOOD_PRESSURE -> R.string.blood_pressure
        RecordKind.WEIGHT -> R.string.weight
        RecordKind.SYMPTOM -> R.string.symptoms
    }

    fun symptomLabel(symptom: Symptom) = when (symptom) {
        Symptom.FATIGUE -> R.string.symptom_fatigue
        Symptom.DIZZINESS -> R.string.symptom_dizziness
        Symptom.PALPITATIONS -> R.string.symptom_palpitations
        Symptom.SHORTNESS_OF_BREATH -> R.string.symptom_breath
        Symptom.SWELLING -> R.string.symptom_swelling
        Symptom.CHEST_DISCOMFORT -> R.string.symptom_chest
    }

    fun severityLabel(level: Int) = when (level) {
        1 -> R.string.severity_mild
        2 -> R.string.severity_moderate
        else -> R.string.severity_severe
    }

    private fun writePdf(context: Context, output: OutputStream, records: List<HealthRecord>, owner: String) {
        val document = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(28, 42, 40); textSize = 11f; typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        var pageNo = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(612, 792, pageNo).create())
        var y = 54f
        fun finishPage() {
            paint.textSize = 9f
            page.canvas.drawText("PusoAlalay  |  $pageNo", 48f, 758f, paint)
            paint.textSize = 11f
            document.finishPage(page)
        }
        fun newPage() {
            finishPage()
            pageNo++
            page = document.startPage(PdfDocument.PageInfo.Builder(612, 792, pageNo).create())
            y = 54f
        }
        fun line(text: String, bold: Boolean = false) {
            if (y > 720) newPage()
            paint.typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            page.canvas.drawText(text, 48f, y, paint)
            y += 17f
        }
        fun paragraph(text: String, bold: Boolean = false) {
            // Break long unspaced notes too; never draw beyond the printable width.
            text.split('\n').forEach { logicalLine ->
                var remaining = logicalLine.replace('\r', ' ')
                if (remaining.isEmpty()) line("")
                while (remaining.isNotEmpty()) {
                    paint.typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
                    var n = paint.breakText(remaining, true, 516f, null).coerceAtLeast(1)
                    if (n < remaining.length) {
                        val boundary = remaining.lastIndexOf(' ', n - 1)
                        if (boundary > n / 2) n = boundary
                    }
                    line(remaining.take(n), bold)
                    remaining = remaining.drop(n).trimStart()
                }
            }
        }
        val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a", context.resources.configuration.locales[0]).withZone(MANILA)
        val shortDate = DateTimeFormatter.ofPattern("dd MMM", context.resources.configuration.locales[0]).withZone(MANILA)
        fun chart(series: List<HealthRecord>) {
            if (y + 150f > 720f) newPage()
            val canvas = page.canvas
            val left = 72f; val right = 560f; val top = y; val bottom = y + 110f
            val values = series.flatMap { listOfNotNull(it.value, it.secondary) }
            val low = values.min(); val high = values.max()
            val spread = (high - low).coerceAtLeast(1.0)
            val t0 = series.first().measuredAt
            val dt = (series.last().measuredAt - t0).coerceAtLeast(1L)
            fun px(r: HealthRecord) = left + ((r.measuredAt - t0).toDouble() / dt * (right - left)).toFloat()
            fun py(v: Double) = bottom - ((v - low) / spread * (bottom - top)).toFloat()
            val axis = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(160, 172, 168); strokeWidth = 0.7f }
            val primary = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(32, 112, 96); strokeWidth = 1.6f }
            val secondary = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(150, 110, 30); strokeWidth = 1.6f; pathEffect = DashPathEffect(floatArrayOf(5f, 3f), 0f)
            }
            canvas.drawLine(left, bottom, right, bottom, axis)
            canvas.drawLine(left, top, left, bottom, axis)
            series.zipWithNext().forEach { (a, b) ->
                canvas.drawLine(px(a), py(a.value!!), px(b), py(b.value!!), primary)
                val sa = a.secondary; val sb = b.secondary
                if (sa != null && sb != null) canvas.drawLine(px(a), py(sa), px(b), py(sb), secondary)
            }
            series.forEach { r ->
                canvas.drawCircle(px(r), py(r.value!!), 2.2f, primary)
                r.secondary?.let { canvas.drawCircle(px(r), py(it), 2.2f, secondary) }
            }
            paint.textSize = 8f
            paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            canvas.drawText(formatNumber(high), 40f, top + 8f, paint)
            canvas.drawText(formatNumber(low), 40f, bottom, paint)
            canvas.drawText(shortDate.format(Instant.ofEpochMilli(series.first().measuredAt)), left, bottom + 12f, paint)
            val last = shortDate.format(Instant.ofEpochMilli(series.last().measuredAt))
            canvas.drawText(last, right - paint.measureText(last), bottom + 12f, paint)
            paint.textSize = 11f
            y = bottom + 30f
        }
        try {
            val now = System.currentTimeMillis()
            val unusual = unusualRecords(records)
            paragraph("PusoAlalay", true)
            paragraph(context.getString(R.string.health_report), true)
            if (owner.isNotBlank()) paragraph(context.getString(R.string.report_prepared_for, owner))
            paragraph(context.getString(R.string.report_generated, formatter.format(Instant.ofEpochMilli(now))))
            paragraph(context.getString(R.string.report_disclaimer))
            paragraph(context.getString(R.string.report_count, records.size))
            line("")
            paragraph(context.getString(R.string.report_summary_title), true)
            paragraph(context.getString(R.string.report_missing_note))
            line("")
            listOf(RecordKind.HEART_RATE, RecordKind.BLOOD_PRESSURE, RecordKind.WEIGHT).forEach { kind ->
                val series = records.filter { it.kind == kind && it.value != null }.sortedBy { it.measuredAt }
                paragraph(context.getString(kindLabel(kind)), true)
                val summary = summarize(series)
                if (summary == null) {
                    paragraph(context.getString(R.string.report_no_data))
                    line("")
                    return@forEach
                }
                val p = summary.primary; val s = summary.secondary
                val unit = kind.unit()
                paragraph(context.getString(R.string.stats_count, p.count))
                paragraph(context.getString(R.string.stats_average, formatPair(p.mean, s?.mean), unit))
                p.sd?.let { paragraph(context.getString(R.string.stats_variation, formatPair(it, s?.sd))) }
                paragraph(context.getString(R.string.stats_median, formatPair(p.median, s?.median)))
                paragraph(context.getString(R.string.stats_range, formatPair(p.min, s?.min), formatPair(p.max, s?.max), unit))
                coverage(series.map { it.measuredAt }, now)?.let {
                    paragraph(context.getString(R.string.stats_coverage, it.daysWithData, it.totalDays))
                }
                paragraph(context.getString(R.string.stats_unusual, series.count { it.id in unusual }))
                if (series.size >= 2) chart(series) else line("")
            }
            paragraph(context.getString(R.string.symptoms), true)
            val symptomRecords = records.filter { it.kind == RecordKind.SYMPTOM }
            if (symptomRecords.isEmpty()) paragraph(context.getString(R.string.report_no_symptoms))
            else {
                paragraph(context.getString(R.string.stats_count, symptomRecords.size))
                Symptom.entries.forEach { symptom ->
                    val n = symptomRecords.count { symptom in it.symptoms }
                    if (n > 0) paragraph(context.getString(R.string.report_symptom_summary, context.getString(symptomLabel(symptom)), n))
                }
            }
            paragraph(context.getString(R.string.stats_explain))
            line("")
            paragraph(context.getString(R.string.report_entries_title), true)
            records.sortedByDescending { it.measuredAt }.forEach { record ->
                paragraph(context.getString(kindLabel(record.kind)) + " | " + formatter.format(Instant.ofEpochMilli(record.measuredAt)), true)
                if (record.kind != RecordKind.SYMPTOM) paragraph(record.numberText() + " " + record.kind.unit())
                if (record.symptoms.isNotEmpty() || record.severity != null) {
                    val parts = record.symptoms.sortedBy { it.ordinal }.map { context.getString(symptomLabel(it)) } +
                        listOfNotNull(record.severity?.let { context.getString(severityLabel(it)) })
                    paragraph(parts.joinToString(" · "))
                }
                if (record.id in unusual) paragraph(context.getString(R.string.unusual_tag))
                paragraph(context.getString(if (record.source == MeasurementSource.SELF_REPORTED)
                    R.string.source_self else R.string.source_device))
                if (record.note.isNotBlank()) paragraph(record.note)
                line("")
            }
            finishPage()
            document.writeTo(output)
        } finally { document.close() }
    }
}
