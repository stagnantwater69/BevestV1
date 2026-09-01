package com.jtexpress.bevest.utils

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.jtexpress.bevest.domain.model.Incident
import com.jtexpress.bevest.domain.repository.MonthlyReport
import java.io.File
import java.io.FileOutputStream

/** Minimal text PDF generation with the platform PdfDocument — no third-party library. */
object PdfExporter {

    fun monthlyReport(
        context: Context,
        report: MonthlyReport,
        incidents: List<Incident>,
    ): File {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 @ 72dpi
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas
        val title = Paint().apply { textSize = 20f; isFakeBoldText = true }
        val body = Paint().apply { textSize = 12f }

        var y = 48f
        canvas.drawText("BeVest Monthly Safety Report", 40f, y, title)
        y += 28f
        canvas.drawText("Month: ${DateTimeUtils.monthLabel(report.monthKey)}", 40f, y, body); y += 18f
        canvas.drawText("Active workers: ${report.activeWorkers}", 40f, y, body); y += 18f
        canvas.drawText("Total incidents: ${report.totalIncidents}", 40f, y, body); y += 18f
        canvas.drawText("Safety percentage: ${report.safetyPercentage}%", 40f, y, body); y += 18f
        canvas.drawText(
            "Warnings: ${report.warningCount}   Danger: ${report.dangerCount}   Emergency: ${report.emergencyCount}",
            40f, y, body,
        )
        y += 28f
        canvas.drawText("Incidents", 40f, y, title); y += 20f

        incidents.take(30).forEach { incident ->
            val line = "${DateTimeUtils.formatDate(incident.createdAt)} · ${incident.workerId} · " +
                "${incident.type.name} · ${incident.severity.name} · ${incident.outcome ?: "open"}"
            canvas.drawText(line, 40f, y, body)
            y += 16f
        }

        doc.finishPage(page)
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "bevest-report-${report.monthKey}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
