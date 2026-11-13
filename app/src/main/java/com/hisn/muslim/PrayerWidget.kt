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

/** ويدجت الشاشة الرئيسية: الصلاة القادمة ووقتها. بيتحدّث مع كل أذان ومع إعادة الجدولة وكل ٣٠ دقيقة. */
class PrayerWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { mgr.updateAppWidget(it, build(ctx)) }
    }

    companion object {
        fun build(ctx: Context): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_prayer)
            val prefs = Prefs(ctx)
            val next = try { Engine.next(prefs) } catch (e: Exception) { null }
            if (next != null) {
                v.setTextViewText(R.id.w_name, next.first.label)
                v.setTextViewText(R.id.w_time, SimpleDateFormat("hh:mm a", Locale("ar")).format(next.second))
            }
            if (next != null) {
                // عدّاد تنازلي حي بيشتغل بدون تحديثات (النظام بيعدّه)، والويدجت بيتحدّث مع كل أذان
                val remain = (next.second.time - System.currentTimeMillis()).coerceAtLeast(0L)
                v.setBoolean(R.id.w_count, "setCountDown", true)
                v.setChronometer(R.id.w_count, android.os.SystemClock.elapsedRealtime() + remain, "المتبقي %s", true)
                v.setViewVisibility(R.id.w_count, android.view.View.VISIBLE)
            } else v.setViewVisibility(R.id.w_count, android.view.View.GONE)
            v.setTextViewText(R.id.w_city, listOf(prefs.city, try { Fasting.hijriText(prefs) } catch (e: Exception) { "" }).filter { it.isNotBlank() }.joinToString(" • "))
            val open = PendingIntent.getActivity(
                ctx, 7, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            v.setOnClickPendingIntent(R.id.widget_root, open)
            return v
        }

        fun updateAll(ctx: Context) {
            PrayerWidgetAll.updateAll(ctx)
            try {
                val mgr = AppWidgetManager.getInstance(ctx)
                val ids = mgr.getAppWidgetIds(ComponentName(ctx, PrayerWidget::class.java))
                if (ids.isNotEmpty()) ids.forEach { mgr.updateAppWidget(it, build(ctx)) }
            } catch (e: Exception) {
                // الويدجت اختياري؛ أي خطأ فيه ما يأثرش على باقي التذكيرات
            }
        }
    }
}
