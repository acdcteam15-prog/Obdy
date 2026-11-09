package com.example.obdy

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Home-screen widget: the next service due, registration and insurance, and where the car is parked. Tap it to open Obdy. */
class ObdyWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) appWidgetManager.updateAppWidget(id, build(context))
    }

    companion object {
        fun refresh(ctx: Context) {
            try {
                val mgr = AppWidgetManager.getInstance(ctx)
                val ids = mgr.getAppWidgetIds(ComponentName(ctx, ObdyWidget::class.java))
                for (id in ids) mgr.updateAppWidget(id, build(ctx))
            } catch (_: Exception) { }
        }

        fun build(ctx: Context): RemoteViews {
            val s = Store(ctx)
            val car = s.loadCar()
            val now = System.currentTimeMillis()
            val rv = RemoteViews(ctx.packageName, R.layout.widget_obdy)
            rv.setTextViewText(R.id.w_title, car.name + if (car.odometerKm > 0) "  ·  ${car.odometerKm} km" else "")

            val dues = s.loadMaintenance().map { Maintenance.due(it, car.odometerKm, now) }.filter { it.recorded }
            val worst = dues.filter { it.overdue }.firstOrNull() ?: dues.filter { it.soon }.firstOrNull()
                ?: dues.filter { it.kmLeft != null || it.daysLeft != null }.minByOrNull { minOf(it.kmLeft?.toDouble() ?: 1e9, (it.daysLeft ?: 100000) * 40.0) }
            rv.setTextViewText(R.id.w_l1, if (worst == null) "Service: enter your last service dates in the app" else
                (if (worst.overdue) "OVERDUE: " else "Next: ") + worst.item.name + "  " + listOfNotNull(worst.kmLeft?.let { if (it < 0) "${-it} km over" else "$it km" }, worst.daysLeft?.let { if (it < 0) "${-it} days over" else "$it days" }).joinToString(" · "))

            val docs = s.loadDocs().filter { it.dueMs > 0 }.minByOrNull { it.dueMs }
            rv.setTextViewText(R.id.w_l2, if (docs == null) "Registration and insurance: set the dates in the app" else docs.name + ": " + (docs.daysLeft(now).let { if (it < 0) "expired" else "$it days left" }))

            val p = s.loadParking()
            rv.setTextViewText(R.id.w_l3, if (p == null) "" else "Parked " + Docs.formatDate(p.timeMs))

            val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            rv.setOnClickPendingIntent(R.id.w_root, open)
            return rv
        }
    }
}
