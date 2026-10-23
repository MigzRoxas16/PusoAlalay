package ph.pusoalalay.app.export

import android.content.Context
import android.graphics.Color
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
    fun write(context: Context, uri: Uri, records: List<HealthRecord>, pdf: Boolean) {
        require(records.isNotEmpty())
        context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
            if (pdf) writePdf(localizedContext(context), stream, records)
            else stream.write(("\uFEFF" + csvReport(records)).toByteArray(Charsets.UTF_8))
        } ?: error("Cannot open destination")
    }

    private fun writePdf(context: Context, output: OutputStream, records: List<HealthRecord>) {
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
        fun line(text: String, bold: Boolean = false) {
            if (y > 720) {
                finishPage()
                pageNo++
                page = document.startPage(PdfDocument.PageInfo.Builder(612, 792, pageNo).create())
                y = 54f
            }
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
        try {
            paragraph("PusoAlalay", true)
            paragraph(context.getString(R.string.health_report), true)
            val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a", context.resources.configuration.locales[0]).withZone(MANILA)
            paragraph(context.getString(R.string.report_generated, formatter.format(Instant.now())))
            paragraph(context.getString(R.string.report_disclaimer))
            paragraph(context.getString(R.string.report_count, records.size))
            line("")
            records.sortedByDescending { it.measuredAt }.forEach { record ->
                val title = when (record.kind) {
                    RecordKind.HEART_RATE -> R.string.heart_rate
                    RecordKind.BLOOD_PRESSURE -> R.string.blood_pressure
                    RecordKind.WEIGHT -> R.string.weight
                    RecordKind.SYMPTOM -> R.string.symptoms
                }
                paragraph(context.getString(title) + " | " + formatter.format(Instant.ofEpochMilli(record.measuredAt)), true)
                if (record.kind != RecordKind.SYMPTOM) paragraph(record.numberText() + " " + record.kind.unit())
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
