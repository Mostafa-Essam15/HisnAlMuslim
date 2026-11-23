package com.hisn.muslim

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.util.Calendar

/** ويدجت «ذكر اليوم + وردك»: ذكر من أذكار التطبيق بمصدره (يتغيّر يوميًا) وتقدّم الورد. بالضغط يفتح التطبيق. */
class DailyWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { mgr.updateAppWidget(it, build(ctx)) }
    }

    companion object {
        fun build(ctx: Context): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_daily)
            try {
                val pool = Content.azkar.filter { it.text.length <= 170 && it.source.length <= 140 && !it.text.startsWith("قراءة سور") }
                if (pool.isNotEmpty()) {
                    val c = Calendar.getInstance()
                    val d = pool[Math.floorMod(c.get(Calendar.YEAR) * 366 + c.get(Calendar.DAY_OF_YEAR), pool.size)]
                    v.setTextViewText(R.id.dw_text, d.text)
                    v.setTextViewText(R.id.dw_src, d.source)
                }
                val p = Prefs(ctx)
                val st = Khatma.stats(p)
                v.setTextViewText(
                    R.id.dw_wird,
                    if (st != null) "📖 الورد: ${st.readToday} / ${st.goalToday} صفحة • تابع من ${st.nextPage}"
                    else "📖 اقرأ اليوم ولو صفحة"
                )
            } catch (e: Exception) {
            }
            val open = PendingIntent.getActivity(
                ctx, 9, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            v.setOnClickPendingIntent(R.id.widget_root, open)
            return v
        }

        fun updateAll(ctx: Context) {
            try {
                val mgr = AppWidgetManager.getInstance(ctx)
                val ids = mgr.getAppWidgetIds(ComponentName(ctx, DailyWidget::class.java))
                ids.forEach { mgr.updateAppWidget(it, build(ctx)) }
            } catch (e: Exception) {
            }
        }
    }
}
