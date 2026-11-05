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
        return PrayerTimes(Coordinates(p.lat, p.lng), DateComponents.from(date), params)
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
    private fun pending(ctx: Context, prayer: String): PendingIntent {
        val i = Intent(ctx, AdhanReceiver::class.java).putExtra("prayer", prayer)
        return PendingIntent.getBroadcast(
            ctx, 1, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun canExact(ctx: Context): Boolean {
        val am = ctx.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    /** يجدولو تنبيه واحد للأذان القادم، وبعد ما يشتغل بيجدول اللي بعده. */
    fun schedule(ctx: Context) {
        val p = Prefs(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java)
        if (!p.adhanEnabled) {
            am.cancel(pending(ctx, "FAJR"))
            return
        }
        // مهلة دقيقة عشان ما نعيد جدولة نفس الأذان لو التنبيه اشتغل قبله بملّي ثواني
        val (pr, date) = Engine.next(p, onlyEnabled = true, graceMs = 60_000) ?: return
        val pi = pending(ctx, pr.name)
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
        ContextCompat.startForegroundService(
            context,
            Intent(context, AdhanService::class.java).putExtra("prayer", prayerName)
        )
        Scheduler.schedule(context)

        // بعد الأذان بمدة: «هل صليت؟»
        val prefs = Prefs(context)
        val prayer = runCatching { Prayer.valueOf(prayerName) }.getOrNull()
        if (prayer != null && prefs.checkEnabled) {
            Reminders.scheduleCheck(context, prayer, prefs.checkAfterMin, 0)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.scheduleAll(context)
    }
}
