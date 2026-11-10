package com.hisn.muslim

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** ويدجت المسبحة: المس الرقم للعدّ، وأسهم لتغيير الذكر، و«إعادة» لتصفير عدّاد الذكر اليوم. */
class TasbihWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { mgr.updateAppWidget(it, build(ctx)) }
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        val p = Prefs(ctx)
        val all = Content.tasbih + p.customDhikr
        val idx = p.tasbihIndex.coerceIn(0, all.size - 1)
        val day = p.dayKey()
        when (intent.action) {
            ACT_COUNT -> {
                p.tasbihAdd(day, all[idx]); p.tasbihCount = (p.tasbihDay(day)[all[idx]] ?: 0)
                val c = p.tasbihDay(day)[all[idx]] ?: 0
                if (p.vibrateOn) vibrate(ctx, if (c % p.tasbihTarget(all[idx]) == 0) 200 else 20)
            }
            ACT_RESET -> p.tasbihReset(day, all[idx])
            ACT_NEXT -> p.tasbihIndex = (idx + 1) % all.size
            ACT_PREV -> p.tasbihIndex = (idx - 1 + all.size) % all.size
            else -> return
        }
        updateAll(ctx)
    }

    companion object {
        private const val ACT_COUNT = "com.hisn.muslim.TASBIH_COUNT"
        private const val ACT_RESET = "com.hisn.muslim.TASBIH_RESET"
        private const val ACT_NEXT = "com.hisn.muslim.TASBIH_NEXT"
        private const val ACT_PREV = "com.hisn.muslim.TASBIH_PREV"

        private fun act(ctx: Context, action: String, rc: Int): PendingIntent = PendingIntent.getBroadcast(
            ctx, rc, Intent(ctx, TasbihWidget::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        fun build(ctx: Context): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_tasbih)
            val p = Prefs(ctx)
            val all = Content.tasbih + p.customDhikr
            val idx = p.tasbihIndex.coerceIn(0, all.size - 1)
            val name = all[idx]
            val target = p.tasbihTarget(name).coerceAtLeast(1)
            val count = p.tasbihDay(p.dayKey())[name] ?: 0
            v.setTextViewText(R.id.tb_name, name)
            v.setTextViewText(R.id.tb_count, (count % target).toString())
            v.setTextViewText(R.id.tb_target, "من $target • اليوم: $count")
            v.setOnClickPendingIntent(R.id.tb_count, act(ctx, ACT_COUNT, 30))
            v.setOnClickPendingIntent(R.id.tb_reset, act(ctx, ACT_RESET, 31))
            v.setOnClickPendingIntent(R.id.tb_next, act(ctx, ACT_NEXT, 32))
            v.setOnClickPendingIntent(R.id.tb_prev, act(ctx, ACT_PREV, 33))
            return v
        }

        fun updateAll(ctx: Context) {
            try {
                val mgr = AppWidgetManager.getInstance(ctx)
                val ids = mgr.getAppWidgetIds(ComponentName(ctx, TasbihWidget::class.java))
                ids.forEach { mgr.updateAppWidget(it, build(ctx)) }
            } catch (e: Exception) {
            }
        }
    }
}
