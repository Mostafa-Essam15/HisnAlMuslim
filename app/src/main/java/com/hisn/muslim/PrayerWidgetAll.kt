package com.hisn.muslim

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Locale

/** ويدجت بمواعيد الصلوات الخمس لليوم؛ القادمة بلون ذهبي. */
class PrayerWidgetAll : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { mgr.updateAppWidget(it, build(ctx)) }
    }

    companion object {
        private val rows = intArrayOf(R.id.r0, R.id.r1, R.id.r2, R.id.r3, R.id.r4)
        private val names = intArrayOf(R.id.n0, R.id.n1, R.id.n2, R.id.n3, R.id.n4)
        private val times = intArrayOf(R.id.t0, R.id.t1, R.id.t2, R.id.t3, R.id.t4)

        fun build(ctx: Context): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_prayers_all)
            val prefs = Prefs(ctx)
            try {
                val t = Engine.times(prefs)
                val next = Engine.next(prefs)?.first
                val fmt = SimpleDateFormat("hh:mm a", Locale("ar"))
                Prayer.values().forEachIndexed { i, pr ->
                    val color = if (pr == next) 0xFFE8C766.toInt() else 0xFFFFFFFF.toInt()
                    v.setTextViewText(names[i], pr.ar)
                    v.setTextViewText(times[i], fmt.format(Engine.timeOf(t, pr)))
                    v.setTextColor(names[i], color)
                    v.setTextColor(times[i], color)
                    v.setInt(rows[i], "setBackgroundResource", if (pr == next) R.drawable.widget_row_next else 0)
                }
                v.setTextViewText(R.id.a_title, "🕌 مواقيت الصلاة" + (prefs.city.takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""))
                v.setTextViewText(R.id.a_date, Fasting.hijriText(prefs))
            } catch (e: Exception) {
            }
            val open = PendingIntent.getActivity(
                ctx, 8, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            v.setOnClickPendingIntent(R.id.widget_root, open)
            return v
        }

        fun updateAll(ctx: Context) {
            try {
                val mgr = AppWidgetManager.getInstance(ctx)
                val ids = mgr.getAppWidgetIds(ComponentName(ctx, PrayerWidgetAll::class.java))
                ids.forEach { mgr.updateAppWidget(it, build(ctx)) }
            } catch (e: Exception) {
            }
        }
    }
}
