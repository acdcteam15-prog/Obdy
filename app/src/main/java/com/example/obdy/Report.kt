package com.example.obdy

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportLine(val kind: Int, val text: String) {
    companion object { const val TITLE = 0; const val HEADING = 1; const val BODY = 2; const val SMALL = 3; const val WARN = 4 }
}

/** Builds the PDF you can show a mechanic. Plain pages of text: reliable and small. */
object Report {
    private const val W = 595
    private const val H = 842
    private const val M = 40

    fun lines(
        car: Car, faults: List<Fault>, monitors: MonitorStatus?, freeze: FreezeFrame?, live: Map<String, String>,
        battery: BatteryRecord?, symptoms: String, ai: String?, due: List<String>, voltage: Double?, repairs: List<RepairRecord> = emptyList(),
    ): List<ReportLine> {
        val l = ArrayList<ReportLine>()
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        l.add(ReportLine(ReportLine.TITLE, "Vehicle scan report"))
        l.add(ReportLine(ReportLine.SMALL, "Made by Obdy on ${df.format(Date())}"))
        l.add(ReportLine(ReportLine.HEADING, "Vehicle"))
        l.add(ReportLine(ReportLine.BODY, "${car.name} (${car.year})" + if (car.plate.isNotBlank()) ", plate ${car.plate}" else ""))
        if (car.vin.isNotBlank()) l.add(ReportLine(ReportLine.BODY, "VIN: ${car.vin}"))
        if (car.engine.isNotBlank()) l.add(ReportLine(ReportLine.BODY, "Engine: ${car.engine}"))
        if (car.odometerKm > 0) l.add(ReportLine(ReportLine.BODY, "Mileage: ${car.odometerKm} km"))
        if (voltage != null) l.add(ReportLine(ReportLine.BODY, "Battery voltage at scan: ${Calc.fmt(voltage, 2)} V"))
        if (symptoms.isNotBlank()) {
            l.add(ReportLine(ReportLine.HEADING, "Symptoms described by the owner"))
            l.add(ReportLine(ReportLine.BODY, symptoms.trim()))
        }
        l.add(ReportLine(ReportLine.HEADING, "Fault codes (${faults.size})"))
        if (faults.isEmpty()) l.add(ReportLine(ReportLine.BODY, "No fault codes were stored in the modules that answered."))
        for (f in faults) {
            l.add(ReportLine(if (f.severity() >= 2) ReportLine.WARN else ReportLine.BODY, "${f.code}  [${f.kind}]  ${f.module}"))
            l.add(ReportLine(ReportLine.SMALL, "    ${f.description}" + if (f.status.isNotBlank()) "  (${f.status})" else ""))
            val causes = DtcDb.causes(f.code)
            if (causes.isNotEmpty()) l.add(ReportLine(ReportLine.SMALL, "    Possible causes: " + causes.joinToString("; ")))
        }
        if (monitors != null) {
            l.add(ReportLine(ReportLine.HEADING, "Warning light and readiness"))
            l.add(ReportLine(ReportLine.BODY, "Warning light: ${if (monitors.milOn) "ON" else "off"}. Codes counted by the engine computer: ${monitors.dtcCount}."))
            for (m in monitors.monitors.filter { it.supported }) l.add(ReportLine(ReportLine.SMALL, "    ${m.name}: ${if (m.complete) "complete" else "NOT complete"}"))
        }
        if (freeze != null && freeze.code != null) {
            l.add(ReportLine(ReportLine.HEADING, "Freeze frame for ${freeze.code}"))
            for ((p, v) in freeze.values) l.add(ReportLine(ReportLine.SMALL, "    ${p.name}: ${Calc.fmt(v, 1)} ${p.unit}"))
        }
        if (live.isNotEmpty()) {
            l.add(ReportLine(ReportLine.HEADING, "Live values at scan time"))
            for ((k, v) in live) l.add(ReportLine(ReportLine.SMALL, "    $k: $v"))
        }
        if (battery != null) {
            l.add(ReportLine(ReportLine.HEADING, "Battery and charging test"))
            val v = BatteryTest.evaluate(battery.rest, battery.crank, battery.idle, battery.rev)
            for (x in v) l.add(ReportLine(if (x.level == Level.Bad) ReportLine.WARN else ReportLine.SMALL, "    ${x.title}: ${x.value}. ${x.text}"))
        }
        if (due.isNotEmpty()) {
            l.add(ReportLine(ReportLine.HEADING, "Service due or overdue"))
            for (d in due) l.add(ReportLine(ReportLine.BODY, "- $d"))
        }
        if (repairs.isNotEmpty()) {
            l.add(ReportLine(ReportLine.HEADING, "Repair and service history"))
            for (x in repairs.take(25)) l.add(ReportLine(ReportLine.SMALL, "    ${Docs.formatDate(x.timeMs)}  ${x.title} (${x.category})" + (if (x.km > 0) "  ${x.km} km" else "") + (if (x.workshop.isNotBlank()) "  at ${x.workshop}" else "") + (if (x.cost > 0) "  ${Calc.fmt(x.cost, 2)}" else "")))
        }
        if (!ai.isNullOrBlank()) {
            l.add(ReportLine(ReportLine.HEADING, "AI analysis (a guide, not a verdict)"))
            l.add(ReportLine(ReportLine.BODY, ai.replace("**", "").replace("##", "").replace("* ", "- ")))
        }
        l.add(ReportLine(ReportLine.SMALL, "Codes read with a generic OBD-II adapter. A workshop should confirm the diagnosis before parts are replaced."))
        return l
    }

    private fun Fault.severity() = DtcDb.severity(code)

    fun build(ctx: Context, lines: List<ReportLine>): File {
        val doc = PdfDocument()
        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(20, 20, 30) }
        val head = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 14f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(180, 90, 0) }
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = Color.rgb(25, 25, 30) }
        val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9.5f; color = Color.rgb(70, 70, 80) }
        val warn = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(190, 30, 30) }

        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
        var canvas = page.canvas
        var y = M.toFloat()
        val width = W - 2 * M

        for (ln in lines) {
            val paint = when (ln.kind) { ReportLine.TITLE -> title; ReportLine.HEADING -> head; ReportLine.SMALL -> small; ReportLine.WARN -> warn; else -> body }
            val gap = when (ln.kind) { ReportLine.HEADING -> 10f; ReportLine.TITLE -> 4f; else -> 2f }
            y += if (ln.kind == ReportLine.HEADING) 8f else 0f
            val layout = StaticLayout.Builder.obtain(ln.text, 0, ln.text.length, paint, width).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
            if (y + layout.height > H - M && y > M + 1) {
                doc.finishPage(page)
                pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
                canvas = page.canvas
                y = M.toFloat()
            }
            canvas.save(); canvas.translate(M.toFloat(), y); layout.draw(canvas); canvas.restore()
            y += layout.height + gap
        }
        doc.finishPage(page)
        val dir = File(ctx.filesDir, "reports").also { it.mkdirs() }
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(8)?.forEach { it.delete() }
        val out = File(dir, "Obdy-report-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".pdf")
        out.outputStream().use { doc.writeTo(it) }
        doc.close()
        return out
    }
}
