package com.hisn.muslim

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.HighLatitudeRule
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import java.util.Calendar
import java.util.Date

object Engine {
    fun times(p: Prefs, date: Date = Date()): PrayerTimes {
        val params = try {
            CalculationMethod.valueOf(p.method).parameters
        } catch (e: Exception) {
            CalculationMethod.EGYPTIAN.parameters
        }
        if (p.asrMethod == "HANAFI") params.madhab = Madhab.HANAFI
        if (p.highLat != "DEFAULT") {
            runCatching { HighLatitudeRule.valueOf(p.highLat) }.getOrNull()?.let { params.highLatitudeRule = it }
        }
        params.adjustments.fajr = p.adj(Prayer.FAJR)
        params.adjustments.dhuhr = p.adj(Prayer.DHUHR)
        params.adjustments.asr = p.adj(Prayer.ASR)
        params.adjustments.maghrib = p.adj(Prayer.MAGHRIB)
        params.adjustments.isha = p.adj(Prayer.ISHA)
        return PrayerTimes(Coordinates(p.lat, p.lng), DateComponents.from(date), params)
    }

    /** نهاية وقت الصلاة: الفجر→الشروق، الظهر→العصر، العصر→المغرب، المغرب→العشاء، العشاء→فجر اليوم التالي */
    fun endOf(p: Prefs, date: Date, pr: Prayer): Date {
        val t = times(p, date)
        return when (pr) {
            Prayer.FAJR -> t.sunrise
            Prayer.DHUHR -> t.asr
            Prayer.ASR -> t.maghrib
            Prayer.MAGHRIB -> t.isha
            Prayer.ISHA -> {
                val c = Calendar.getInstance().apply { time = date; add(Calendar.DAY_OF_YEAR, 1) }
                times(p, c.time).fajr
            }
        }
    }

    /** تاريخ اليوم الذي تنتمي إليه آخر فترة منتهية لهذه الصلاة (العشاء بعد منتصف الليل تتبع الأمس) */
    fun dayOfPeriod(p: Prefs, pr: Prayer, now: Long = System.currentTimeMillis()): Date {
        for (off in 0 downTo -1) {
            val d = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, off) }.time
            if (endOf(p, d, pr).time <= now + 60_000L) return d
        }
        return Date()
    }

    /** أقرب نهاية وقت صلاة قادمة (صلاة، يوم الصلاة، وقت النهاية) */
    fun nextPeriodEnd(p: Prefs, now: Long = System.currentTimeMillis()): Triple<Prayer, Date, Date>? {
        for (off in -1..1) {
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, off) }
            for (pr in Prayer.values()) {
                val e = endOf(p, cal.time, pr)
                if (e.time > now) return Triple(pr, cal.time, e)
            }
        }
        return null
    }

    fun timeOf(t: PrayerTimes, pr: Prayer): Date = when (pr) {
        Prayer.FAJR -> t.fajr
        Prayer.DHUHR -> t.dhuhr
        Prayer.ASR -> t.asr
        Prayer.MAGHRIB -> t.maghrib
        Prayer.ISHA -> t.isha
    }

    /** أقرب صلاة قادمة (اليوم أو غدًا) */
    fun next(p: Prefs, onlyEnabled: Boolean = false, graceMs: Long = 0): Pair<Prayer, Date>? {
        val now = System.currentTimeMillis() + graceMs
        for (off in 0..1) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, off)
            val t = times(p, cal.time)
            for (pr in Prayer.values()) {
                val d = timeOf(t, pr)
                if (d.time > now && (!onlyEnabled || p.prayerEnabled(pr))) return pr to d
            }
        }
        return null
    }
}

object Scheduler {
    /** كود الطلب ثابت (١) عشان يكون فيه تنبيه أذان واحد بس: أي جدولة جديدة بتستبدل القديم ومفيش تكرار. */
    private fun pending(ctx: Context, prayer: String, at: Long = 0L): PendingIntent {
        val i = Intent(ctx, AdhanReceiver::class.java).putExtra("prayer", prayer).putExtra("at", at)
        return PendingIntent.getBroadcast(
            ctx, 1, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun canExact(ctx: Context): Boolean {
        val am = ctx.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    /**
     * يجدولو تنبيه واحد للأذان القادم، وبعد ما يشتغل بيجدول اللي بعده.
     * @param afterFire بنمرّرها true من مستقبِل الأذان بعد ما التنبيه يشتغل: مهلة دقيقة عشان ما نعيد
     * جدولة نفس الأذان بملّي ثواني. في أي جدولة تانية (فتح التطبيق، تغيير التوقيت) المهلة صفر،
     * عشان أذان بعد أقل من دقيقة ما يتلغيش بالغلط.
     */
    fun schedule(ctx: Context, afterFire: Boolean = false) {
        val p = Prefs(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java)
        if (!p.adhanEnabled) {
            am.cancel(pending(ctx, "FAJR"))
            return
        }
        val (pr, date) = Engine.next(p, onlyEnabled = true, graceMs = if (afterFire) 60_000 else 0) ?: return
        val pi = pending(ctx, pr.name, date.time)
        if (canExact(ctx)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, date.time, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, date.time, pi)
        }
    }
}

class AdhanReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prayerName = intent.getStringExtra("prayer") ?: return
        val at = intent.getLongExtra("at", 0L)
        val prefs = Prefs(context)
        val prayer = runCatching { Prayer.valueOf(prayerName) }.getOrNull() ?: return

        // منع التكرار: نفس الصلاة بنفس وقت الجدولة اتعالجت قبل كده
        val key = if (at > 0) "$prayerName@$at" else ""
        if (AdhanPolicy.isDuplicate(prefs.lastFireKey, key)) {
            Scheduler.schedule(context, afterFire = true)
            return
        }
        if (key.isNotEmpty()) prefs.lastFireKey = key

        if (prefs.adhanEnabled && prefs.prayerEnabled(prayer)) {
            AdhanService.start(context, prayer, scheduledAt = at)
        }
        Scheduler.schedule(context, afterFire = true)

        // بعد الأذان بمدة: «هل صليت؟»
        if (prefs.checkEnabled) {
            Reminders.scheduleCheck(context, prayer, prefs.checkAfterMin, 0)
        }
    }
}

/**
 * بيعيد الجدولة بعد: إعادة تشغيل الجهاز، تحديث التطبيق، تغيير المنطقة الزمنية، تغيير التاريخ أو الساعة،
 * وتغيّر إذن «المنبهات الدقيقة». ولو التطبيق كان غيّر «عدم الإزعاج» مؤقتًا وفضل كده، بيرجّعه.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = Prefs(context)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            DndGuard.restore(context, prefs)
        }
        Reminders.scheduleAll(context)
    }
}
