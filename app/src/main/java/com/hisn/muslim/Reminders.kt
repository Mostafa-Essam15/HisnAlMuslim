package com.hisn.muslim

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color as AColor
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.util.Calendar

// ───────────────────────── الإشعارات ─────────────────────────

object Notif {
    const val CH_ADHAN = "adhan_channel"
    const val CH_CHECK = "check_channel"
    const val CH_REMIND = "remind_channel"

    const val ID_DUHA = 400
    const val ID_QIYAM = 401
    const val ID_FAST = 402
    const val ID_POPUP = 403
    const val ID_WIRD = 404
    const val ID_ADHKAR_AM = 405
    const val ID_ADHKAR_PM = 406
    const val ID_SUHOOR = 407
    const val ID_IFTAR = 408
    const val ID_PRE = 409
    const val ID_IQAMA = 410
    fun idCheck(p: Prayer) = 300 + p.ordinal
    fun idPeriod(p: Prayer) = 320 + p.ordinal

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val adhan = NotificationChannel(CH_ADHAN, "الأذان", NotificationManager.IMPORTANCE_HIGH)
        adhan.setSound(null, null) // الصوت بيشغله مشغّل الأذان بنفسه
        nm.createNotificationChannel(adhan)
        nm.createNotificationChannel(NotificationChannel(CH_CHECK, "تأكيد أداء الصلاة", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_REMIND, "التذكيرات (صيام، نوافل، أذكار، ورد)", NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun builder(ctx: Context, channel: String, title: String, text: String, big: String? = null) =
        NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat_mosque)
            .setColor(0xFF0F5C45.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(big ?: text))
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))

    fun notify(ctx: Context, id: Int, n: android.app.Notification) {
        ctx.getSystemService(NotificationManager::class.java).notify(id, n)
    }

    fun cancel(ctx: Context, id: Int) {
        ctx.getSystemService(NotificationManager::class.java).cancel(id)
    }
}

// ───────────────────────── جدولة التنبيهات ─────────────────────────

object Reminders {
    private const val RC_CHECK = 100 // + ordinal
    private const val RC_DUHA = 200
    private const val RC_QIYAM = 201
    private const val RC_FASTING = 202
    private const val RC_POPUP = 203
    private const val RC_WIRD = 204
    private const val RC_PERIOD = 210
    private const val RC_ADHKAR_AM = 240
    private const val RC_ADHKAR_PM = 241
    private const val RC_SUHOOR = 242
    private const val RC_IFTAR = 243
    private const val RC_PRE = 250
    private const val RC_IQAMA = 251
    private const val RC_FOCUS_ON = 230
    private const val RC_FOCUS_OFF = 231
    private const val RC_PERIOD_LATER = 220 // + ordinal

    private fun pi(ctx: Context, rc: Int, type: String, prayer: Prayer? = null, count: Int = 0): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java)
            .putExtra("type", type)
            .putExtra("count", count)
        if (prayer != null) i.putExtra("prayer", prayer.name)
        return PendingIntent.getBroadcast(ctx, rc, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun setAt(ctx: Context, rc: Int, at: Long, type: String, prayer: Prayer? = null, count: Int = 0) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val p = pi(ctx, rc, type, prayer, count)
        if (Scheduler.canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
    }

    private fun cancelAlarm(ctx: Context, rc: Int, type: String) {
        ctx.getSystemService(AlarmManager::class.java).cancel(pi(ctx, rc, type))
    }

    fun scheduleAll(ctx: Context) {
        Scheduler.schedule(ctx)
        scheduleDuha(ctx)
        scheduleQiyam(ctx)
        scheduleFasting(ctx)
        schedulePopup(ctx)
        scheduleWird(ctx)
        schedulePeriodEnd(ctx)
        scheduleFocus(ctx)
        scheduleAdhkar(ctx)
        scheduleRamadan(ctx)
        schedulePreIqama(ctx)
        PrayerWidget.updateAll(ctx)
    }

    private fun nm(ctx: Context) = ctx.getSystemService(android.app.NotificationManager::class.java)

    /** بيجدول بداية/نهاية وضع التركيز (عدم الإزعاج حول الصلاة). بيشتغل بس لو المستخدم فعّله ومنح إذن الوصول لسياسة الإشعارات. */
    fun scheduleFocus(ctx: Context) {
        val p = Prefs(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.cancel(pi(ctx, RC_FOCUS_ON, "FOCUS_ON"))
        am.cancel(pi(ctx, RC_FOCUS_OFF, "FOCUS_OFF"))
        if (!p.focusOn || !DndGuard.hasAccess(ctx)) { focusRestore(ctx, p); return }
        val now = System.currentTimeMillis()
        val ws = Engine.focusWindows(p)
        val cur = ws.firstOrNull { now >= it.first && now < it.second && now >= p.focusOverrideUntil }
        if (cur != null) {
            focusApply(ctx, p)
            setAt(ctx, RC_FOCUS_OFF, cur.second, "FOCUS_OFF")
        } else {
            val next = ws.firstOrNull { it.first > now }
            if (next != null) setAt(ctx, RC_FOCUS_ON, next.first, "FOCUS_ON")
            focusRestore(ctx, p)
        }
    }

    fun focusApply(ctx: Context, p: Prefs) {
        val n = nm(ctx)
        val cur = n.currentInterruptionFilter
        // لو المستخدم أصلًا على وضع عدم إزعاج، ما نغيّرش حاجة
        if (p.focusPrev == 0 && cur == android.app.NotificationManager.INTERRUPTION_FILTER_ALL) {
            runCatching {
                n.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                p.focusPrev = cur
            }
        }
    }

    fun focusRestore(ctx: Context, p: Prefs) {
        val prev = p.focusPrev
        if (prev == 0) return
        p.focusPrev = 0
        if (DndGuard.hasAccess(ctx)) runCatching { nm(ctx).setInterruptionFilter(prev) }
    }

    /** تنبيه عند انتهاء وقت الصلاة الحالية لسؤالك: هل أتممتها؟ */
    fun schedulePeriodEnd(ctx: Context) {
        val p = Prefs(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java)
        if (!p.trackingEnabled || !p.periodPopupOn) { am.cancel(pi(ctx, RC_PERIOD, "PERIOD")); return }
        val n = Engine.nextPeriodEnd(p) ?: return
        setAt(ctx, RC_PERIOD, n.third.time, "PERIOD", n.first, 0)
    }

    fun schedulePeriodLater(ctx: Context, prayer: Prayer, afterMin: Int) {
        setAt(ctx, RC_PERIOD_LATER + prayer.ordinal, System.currentTimeMillis() + afterMin * 60_000L, "PERIODP", prayer, 0)
    }

    // تأكيد الصلاة بعد الأذان
    fun scheduleCheck(ctx: Context, prayer: Prayer, afterMin: Int, count: Int) {
        setAt(ctx, RC_CHECK + prayer.ordinal, System.currentTimeMillis() + afterMin * 60_000L, "CHECK", prayer, count)
    }

    fun cancelCheck(ctx: Context, prayer: Prayer) {
        ctx.getSystemService(AlarmManager::class.java).cancel(pi(ctx, RC_CHECK + prayer.ordinal, "CHECK", prayer))
    }

    private fun day(offset: Int): java.util.Date =
        Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offset) }.time

    fun scheduleDuha(ctx: Context) {
        val p = Prefs(ctx)
        if (!p.duhaOn) { cancelAlarm(ctx, RC_DUHA, "DUHA"); return }
        val now = System.currentTimeMillis()
        for (off in 0..1) {
            val t = Engine.times(p, day(off)).sunrise.time + 20 * 60_000L
            if (t > now + 30_000) { setAt(ctx, RC_DUHA, t, "DUHA"); return }
        }
    }

    /** بداية الثلث الأخير من الليل = الفجر - ثلث المدة من المغرب للفجر */
    fun nextQiyamTime(p: Prefs, now: Long): Long? {
        for (off in -1..1) {
            val maghrib = Engine.times(p, day(off)).maghrib.time
            val fajr = Engine.times(p, day(off + 1)).fajr.time
            val start = fajr - (fajr - maghrib) / 3
            if (start > now + 30_000) return start
        }
        return null
    }

    fun scheduleQiyam(ctx: Context) {
        val p = Prefs(ctx)
        if (!p.qiyamOn) { cancelAlarm(ctx, RC_QIYAM, "QIYAM"); return }
        nextQiyamTime(p, System.currentTimeMillis())?.let { setAt(ctx, RC_QIYAM, it, "QIYAM") }
    }

    fun nextDailyAt(minutes: Int, now: Long): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, minutes / 60)
        c.set(Calendar.MINUTE, minutes % 60)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        if (c.timeInMillis <= now + 30_000) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    fun scheduleFasting(ctx: Context) {
        val p = Prefs(ctx)
        val any = p.fastMonThu || p.fastWhite || p.fastArafah || p.fastAshura || p.fastShawwal || p.fastDhul
        if (!any) { cancelAlarm(ctx, RC_FASTING, "FASTING"); return }
        setAt(ctx, RC_FASTING, nextDailyAt(p.fastTime, System.currentTimeMillis()), "FASTING")
    }

    fun nextPopupTime(p: Prefs, now: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = now + p.popupIntervalMin * 60_000L
        val h = c.get(Calendar.HOUR_OF_DAY)
        if (h >= p.popupEndHour || h < p.popupStartHour) {
            c.set(Calendar.HOUR_OF_DAY, p.popupStartHour)
            c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            if (c.timeInMillis <= now) c.add(Calendar.DAY_OF_YEAR, 1)
        }
        return c.timeInMillis
    }

    fun schedulePopup(ctx: Context) {
        val p = Prefs(ctx)
        if (!p.popupOn) { cancelAlarm(ctx, RC_POPUP, "POPUP"); return }
        setAt(ctx, RC_POPUP, nextPopupTime(p, System.currentTimeMillis()), "POPUP")
    }

    private fun isRamadanDay(p: Prefs, d: LocalDate): Boolean =
        HijrahDate.from(d.plusDays(p.hijriAdj.toLong())).get(ChronoField.MONTH_OF_YEAR) == 9

    /** تذكير السحور والإفطار (في أيام رمضان فقط حسب التقويم؛ والرؤية الشرعية هي المرجع) */
    fun scheduleRamadan(ctx: Context) {
        val p = Prefs(ctx)
        cancelAlarm(ctx, RC_SUHOOR, "SUHOOR"); cancelAlarm(ctx, RC_IFTAR, "IFTAR")
        if (!p.ramadanReminders) return
        val now = System.currentTimeMillis()
        var suhoorAt = 0L
        var iftarAt = 0L
        for (off in 0..2) {
            val d = LocalDate.now().plusDays(off.toLong())
            if (!isRamadanDay(p, d)) continue
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, off) }
            val t = Engine.times(p, cal.time)
            val s = t.fajr.time - p.suhoorBeforeMin * 60_000L
            if (suhoorAt == 0L && s > now + 30_000) suhoorAt = s
            if (iftarAt == 0L && t.maghrib.time > now + 30_000) iftarAt = t.maghrib.time
        }
        if (suhoorAt > 0) setAt(ctx, RC_SUHOOR, suhoorAt, "SUHOOR")
        if (iftarAt > 0) setAt(ctx, RC_IFTAR, iftarAt, "IFTAR")
        // لو مفيش رمضان في الأيام الجاية، التحقق التالي بيحصل مع scheduleAll (فتح التطبيق/إعادة التشغيل/تعديل الإعدادات)
    }

    fun scheduleAdhkar(ctx: Context) {
        val p = Prefs(ctx)
        val now = System.currentTimeMillis()
        if (!p.adhkarReminders) {
            cancelAlarm(ctx, RC_ADHKAR_AM, "ADHKAR_AM"); cancelAlarm(ctx, RC_ADHKAR_PM, "ADHKAR_PM"); return
        }
        setAt(ctx, RC_ADHKAR_AM, nextDailyAt(p.morningMin, now), "ADHKAR_AM")
        setAt(ctx, RC_ADHKAR_PM, nextDailyAt(p.eveningMin, now), "ADHKAR_PM")
    }

    /** أقرب (صلاة مفعّلة، وقتها + إزاحة) بعد الآن، إزاحة بالدقائق (سالبة = قبل) */
    private fun nextShifted(p: Prefs, shiftMin: Int, now: Long): Pair<Prayer, Long>? {
        var best: Pair<Prayer, Long>? = null
        for (off in 0..1) {
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, off) }
            val t = Engine.times(p, cal.time)
            for (pr in Prayer.values()) {
                if (!p.prayerEnabled(pr)) continue
                val at = Engine.timeOf(t, pr).time + shiftMin * 60_000L
                if (at > now + 15_000 && (best == null || at < best.second)) best = pr to at
            }
        }
        return best
    }

    /** تذكير قبل الصلاة وتذكير الإقامة (إشعارات عادية، لا تشغّل صوت الأذان) */
    fun schedulePreIqama(ctx: Context) {
        val p = Prefs(ctx)
        val now = System.currentTimeMillis()
        if (p.preMin > 0) nextShifted(p, -p.preMin, now)?.let { setAt(ctx, RC_PRE, it.second, "PRE", it.first) } else cancelAlarm(ctx, RC_PRE, "PRE")
        if (p.iqamaMin > 0) nextShifted(p, p.iqamaMin, now)?.let { setAt(ctx, RC_IQAMA, it.second, "IQAMA", it.first) } else cancelAlarm(ctx, RC_IQAMA, "IQAMA")
    }

    fun scheduleWird(ctx: Context) {
        val p = Prefs(ctx)
        if (!p.khatmaActive || !p.wirdReminder) { cancelAlarm(ctx, RC_WIRD, "WIRD"); return }
        setAt(ctx, RC_WIRD, nextDailyAt(p.wirdTime, System.currentTimeMillis()), "WIRD")
    }
}

// ───────────────────────── صيام السنن ─────────────────────────

object Fasting {
    /** سبب استحباب الصيام في هذا اليوم (عنوان + دليل) أو null */
    fun reason(p: Prefs, d: LocalDate): Pair<String, String>? {
        val h = HijrahDate.from(d.plusDays(p.hijriAdj.toLong()))
        val m = h.get(ChronoField.MONTH_OF_YEAR)
        val day = h.get(ChronoField.DAY_OF_MONTH)

        if (m == 9) return null // رمضان فرض
        if (m == 10 && day == 1) return null // عيد الفطر
        if (m == 12 && day in 10..13) return null // عيد الأضحى وأيام التشريق

        if (p.fastArafah && m == 12 && day == 9)
            return "يوم عرفة" to "صيام يوم عرفة يكفّر السنة التي قبله والتي بعده — رواه مسلم (لغير الحاج)"
        if (p.fastAshura && m == 1 && day == 10)
            return "يوم عاشوراء" to "صيام يوم عاشوراء يكفّر السنة الماضية — رواه مسلم"
        if (p.fastAshura && m == 1 && day == 9)
            return "يوم تاسوعاء" to "يُستحب صيام التاسع مع العاشر مخالفةً لأهل الكتاب — رواه مسلم"
        if (p.fastDhul && m == 12 && day in 1..8)
            return "من أيام العشر من ذي الحجة" to "العمل الصالح في هذه الأيام أحب إلى الله من غيرها — رواه البخاري"
        if (p.fastShawwal && m == 10 && day in 2..7)
            return "ست من شوال" to "من صام رمضان ثم أتبعه ستًّا من شوال كان كصيام الدهر — رواه مسلم"
        if (p.fastWhite && day in 13..15 && m != 12)
            return "من الأيام البيض" to "صيام ثلاثة أيام من كل شهر (13 و14 و15) — متفق عليه"
        if (p.fastMonThu && (d.dayOfWeek == DayOfWeek.MONDAY || d.dayOfWeek == DayOfWeek.THURSDAY)) {
            val name = if (d.dayOfWeek == DayOfWeek.MONDAY) "الإثنين" else "الخميس"
            return "يوم $name" to "تُعرض الأعمال يومي الإثنين والخميس وأحب أن يُعرض عملي وأنا صائم — رواه الترمذي"
        }
        return null
    }

    /** وصف التاريخ الهجري لعرضه في الرئيسية */
    fun hijriText(p: Prefs, d: LocalDate = LocalDate.now()): String {
        val h = HijrahDate.from(d.plusDays(p.hijriAdj.toLong()))
        val months = listOf(
            "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
            "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"
        )
        val m = h.get(ChronoField.MONTH_OF_YEAR)
        return "${h.get(ChronoField.DAY_OF_MONTH)} ${months[m - 1]} ${h.get(ChronoField.YEAR)} هـ"
    }
}

// ───────────────────────── بوب اب الأذكار ─────────────────────────

object AzkarPopup {
    private var view: View? = null
    private val handler = Handler(Looper.getMainLooper())

    fun canOverlay(ctx: Context) = Settings.canDrawOverlays(ctx)

    fun dismiss(ctx: Context) {
        val v = view ?: return
        runCatching { ctx.applicationContext.getSystemService(WindowManager::class.java).removeView(v) }
        view = null
    }

    fun show(ctx: Context, item: Pair<String, String>) {
        val app = ctx.applicationContext
        dismiss(app)
        val dp = app.resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()

        val bg = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFF0F5C45.toInt(), 0xFF06261D.toInt())
        ).apply {
            cornerRadius = 28 * dp
            setStroke(px(2), 0xFFE8C766.toInt())
        }
        val root = LinearLayout(app).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = bg
            setPadding(px(22), px(18), px(22), px(16))
            elevation = 24 * dp
        }
        fun tv(text: String, size: Float, color: Int, bold: Boolean = false, serif: Boolean = false) = TextView(app).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            if (bold) setTypeface(typeface, Typeface.BOLD)
            if (serif) typeface = Typeface.create(Typeface.SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        root.addView(tv("✨ ذكر وتذكير", 14f, 0xFFE8C766.toInt(), bold = true))
        root.addView(tv(item.first, 19f, AColor.WHITE, serif = true).apply {
            setLineSpacing(0f, 1.35f)
            setPadding(0, px(12), 0, px(8))
        })
        if (item.second.isNotBlank()) root.addView(tv(item.second, 12f, 0xFFC9D4CF.toInt()))

        val row = LinearLayout(app).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, px(14), 0, 0)
        }
        fun btn(label: String, filled: Boolean, onClick: () -> Unit) = TextView(app).apply {
            text = label
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(if (filled) AColor.BLACK else 0xFFE8C766.toInt())
            setPadding(px(22), px(10), px(22), px(10))
            background = GradientDrawable().apply {
                cornerRadius = 40 * dp
                if (filled) setColor(0xFFE8C766.toInt()) else setStroke(px(1), 0xFFE8C766.toInt())
            }
            setOnClickListener { onClick() }
        }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { setMargins(px(6), 0, px(6), 0) }
        row.addView(btn("تم ✅", true) { dismiss(app) }, lp)
        row.addView(btn("ذكر آخر", false) { show(app, Content.randomPopupDhikr()) }, lp)
        root.addView(row)

        val params = WindowManager.LayoutParams(
            (app.resources.displayMetrics.widthPixels * 0.9).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
        try {
            app.getSystemService(WindowManager::class.java).addView(root, params)
            view = root
            handler.postDelayed({ if (view === root) dismiss(app) }, 120_000)
        } catch (e: Exception) {
            // مفيش صلاحية العرض فوق التطبيقات → إشعار عادي
            popupAsNotification(app, item)
        }
    }

    fun popupAsNotification(ctx: Context, item: Pair<String, String>) {
        Notif.ensureChannels(ctx)
        val n = Notif.builder(ctx, Notif.CH_REMIND, "✨ ذكر وتذكير", item.first, item.first + "\n" + item.second).build()
        Notif.notify(ctx, Notif.ID_POPUP, n)
    }
}

// ───────────────────────── المستقبِل العام للتذكيرات ─────────────────────────

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val type = intent.getStringExtra("type") ?: return
        val prayer = intent.getStringExtra("prayer")?.let { runCatching { Prayer.valueOf(it) }.getOrNull() }
        val count = intent.getIntExtra("count", 0)
        val p = Prefs(ctx)
        Notif.ensureChannels(ctx)

        when (type) {
            "CHECK" -> if (prayer != null) onCheck(ctx, p, prayer, count)
            "DONE" -> if (prayer != null) {
                p.setDone(p.dayKey(), prayer.fardKey)
                Reminders.cancelCheck(ctx, prayer)
                Notif.cancel(ctx, Notif.idCheck(prayer))
                ForcedOverlay.dismiss(ctx, "check_${prayer.name}")
            }
            "LATER" -> if (prayer != null) {
                // بس نخفي الإشعار، والتذكير التالي متجدول بالفعل
                Notif.cancel(ctx, Notif.idCheck(prayer))
            }
            "PERIOD", "PERIODP" -> {
                if (prayer != null) onPeriodEnd(ctx, p, prayer, type == "PERIODP")
                if (type == "PERIOD") Reminders.schedulePeriodEnd(ctx)
            }
            "P_YES", "P_QADA", "P_LATER" -> if (prayer != null) {
                val day = p.dayKey(Engine.dayOfPeriod(p, prayer))
                when (type) {
                    "P_YES" -> p.setPrayerStatus(day, prayer, 2)
                    "P_QADA" -> p.setPrayerStatus(day, prayer, 3)
                    else -> Reminders.schedulePeriodLater(ctx, prayer, p.laterMin.coerceAtLeast(5))
                }
                Reminders.cancelCheck(ctx, prayer)
                Notif.cancel(ctx, Notif.idPeriod(prayer))
                Notif.cancel(ctx, Notif.idCheck(prayer))
                refreshTick.intValue++
            }
            "FOCUS_ON", "FOCUS_OFF" -> Reminders.scheduleFocus(ctx)
            "DUHA" -> {
                val n = Notif.builder(
                    ctx, Notif.CH_REMIND, "☀️ وقت صلاة الضحى",
                    "ركعتان أو أكثر تجزئ عن صدقات المفاصل — رواه مسلم"
                ).build()
                Notif.notify(ctx, Notif.ID_DUHA, n)
                Reminders.scheduleDuha(ctx)
            }
            "QIYAM" -> {
                val n = Notif.builder(
                    ctx, Notif.CH_REMIND, "🌙 الثلث الأخير من الليل",
                    "وقت القيام والدعاء والاستغفار",
                    "ينزل ربنا كل ليلة إلى السماء الدنيا حين يبقى ثلث الليل الآخر فيقول: من يدعوني فأستجيب له، من يسألني فأعطيه، من يستغفرني فأغفر له — متفق عليه"
                ).build()
                Notif.notify(ctx, Notif.ID_QIYAM, n)
                Reminders.scheduleQiyam(ctx)
            }
            "FASTING" -> {
                Fasting.reason(p, LocalDate.now().plusDays(1))?.let { (title, detail) ->
                    val n = Notif.builder(ctx, Notif.CH_REMIND, "🌿 غدًا: $title", "هل تنوي الصيام غدًا؟ $detail").build()
                    Notif.notify(ctx, Notif.ID_FAST, n)
                }
                Reminders.scheduleFasting(ctx)
            }
            "POPUP" -> {
                val item = Content.randomPopupDhikr()
                if (AzkarPopup.canOverlay(ctx)) AzkarPopup.show(ctx, item) else AzkarPopup.popupAsNotification(ctx, item)
                Reminders.schedulePopup(ctx)
            }
            "ADHKAR_AM", "ADHKAR_PM" -> {
                val morning = type == "ADHKAR_AM"
                val cat = if (morning) "الصباح" else "المساء"
                // لو أتممت الأذكار النهارده مفيش داعي للتذكير
                if (cat !in p.adhkarDone(p.dayKey())) {
                    val n = Notif.builder(
                        ctx, Notif.CH_REMIND, if (morning) "🌅 أذكار الصباح" else "🌇 أذكار المساء",
                        "حان وقت أذكار ${if (morning) "الصباح" else "المساء"}. افتح التطبيق وأتمّها."
                    ).build()
                    Notif.notify(ctx, if (morning) Notif.ID_ADHKAR_AM else Notif.ID_ADHKAR_PM, n)
                }
                Reminders.scheduleAdhkar(ctx)
            }
            "SUHOOR" -> {
                val n = Notif.builder(
                    ctx, Notif.CH_REMIND, "🌙 وقت السحور",
                    "قرب الفجر. تسحّروا فإن في السحور بركة — متفق عليه"
                ).build()
                Notif.notify(ctx, Notif.ID_SUHOOR, n)
                Reminders.scheduleRamadan(ctx)
            }
            "IFTAR" -> {
                val n = Notif.builder(
                    ctx, Notif.CH_REMIND, "🌇 حان وقت الإفطار",
                    "ذهب الظمأ وابتلّت العروق وثبت الأجر إن شاء الله",
                    "ذهب الظمأ وابتلّت العروق وثبت الأجر إن شاء الله — رواه أبو داود"
                ).build()
                Notif.notify(ctx, Notif.ID_IFTAR, n)
                Reminders.scheduleRamadan(ctx)
            }
            "PRE" -> {
                val pr = intent.getStringExtra("prayer")?.let { runCatching { Prayer.valueOf(it) }.getOrNull() }
                if (pr != null && p.preMin > 0) {
                    Notif.notify(ctx, Notif.ID_PRE, Notif.builder(
                        ctx, Notif.CH_REMIND, "⏳ اقترب وقت ${pr.ar}", "بقي نحو ${p.preMin} دقيقة على أذان ${pr.ar}. استعد للصلاة."
                    ).build())
                }
                Reminders.schedulePreIqama(ctx)
            }
            "IQAMA" -> {
                val pr = intent.getStringExtra("prayer")?.let { runCatching { Prayer.valueOf(it) }.getOrNull() }
                if (pr != null && p.iqamaMin > 0) {
                    Notif.notify(ctx, Notif.ID_IQAMA, Notif.builder(
                        ctx, Notif.CH_REMIND, "🕌 موعد إقامة ${pr.ar}", "مرّت ${p.iqamaMin} دقيقة على الأذان. قم إلى الصلاة."
                    ).build())
                }
                Reminders.schedulePreIqama(ctx)
            }
            "WIRD" -> {
                val st = Khatma.stats(p)
                if (st != null && st.readToday < st.goalToday) {
                    val n = Notif.builder(
                        ctx, Notif.CH_REMIND, "📖 وردك اليوم من المصحف",
                        "المطلوب اليوم ${st.goalToday} صفحة، قرأت ${st.readToday}. كمّل من صفحة ${st.nextPage}."
                    ).build()
                    Notif.notify(ctx, Notif.ID_WIRD, n)
                }
                Reminders.scheduleWird(ctx)
            }
        }
    }

    private fun onPeriodEnd(ctx: Context, p: Prefs, prayer: Prayer, isLater: Boolean) {
        if (!p.trackingEnabled || !p.periodPopupOn) return
        val day = p.dayKey(Engine.dayOfPeriod(p, prayer))
        if (p.prayerStatus(day, prayer) != 0) return
        if (!isLater) { if (p.wasPrompted(day, prayer)) return; p.markPrompted(day, prayer) }

        fun action(label: String, type: String, rc: Int) = NotificationCompat.Action(
            0, label,
            PendingIntent.getBroadcast(
                ctx, rc,
                Intent(ctx, ReminderReceiver::class.java).putExtra("type", type).putExtra("prayer", prayer.name),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        val n = Notif.builder(
            ctx, Notif.CH_CHECK, "انتهى وقت ${prayer.ar}",
            "هل أتممت صلاة ${prayer.ar}؟ يمكنك تسجيلها «قضاء» إن فاتتك."
        )
            .addAction(action("✅ نعم", "P_YES", 600 + prayer.ordinal * 3))
            .addAction(action("↩ قضاء", "P_QADA", 601 + prayer.ordinal * 3))
            .addAction(action("⏳ لاحقاً", "P_LATER", 602 + prayer.ordinal * 3))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        Notif.notify(ctx, Notif.idPeriod(prayer), n)
        val k = "period_${prayer.name}"
        ForcedOverlay.show(
            ctx, k, "⏱ انتهى وقت الصلاة", "هل أتممت صلاة ${prayer.ar}؟", "يمكنك تسجيلها «قضاء» إن فاتتك.",
            listOf(
                ForcedOverlay.reminderButton(ctx, k, "✅ نعم، أتممتها", true, "P_YES", prayer),
                ForcedOverlay.reminderButton(ctx, k, "↩ قضاء", false, "P_QADA", prayer),
                ForcedOverlay.reminderButton(ctx, k, "⏳ لاحقاً", false, "P_LATER", prayer)
            )
        )
        Reminders.cancelCheck(ctx, prayer)
        Notif.cancel(ctx, Notif.idCheck(prayer))
    }

    private fun onCheck(ctx: Context, p: Prefs, prayer: Prayer, count: Int) {
        if (!p.checkEnabled) return
        if (prayer.fardKey in p.done(p.dayKey())) return

        fun action(label: String, type: String, rc: Int) = NotificationCompat.Action(
            0, label,
            PendingIntent.getBroadcast(
                ctx, rc,
                Intent(ctx, ReminderReceiver::class.java).putExtra("type", type).putExtra("prayer", prayer.name),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        val n = Notif.builder(
            ctx, Notif.CH_CHECK, "هل صليت ${prayer.ar}؟",
            "اضغط «صليت» لتسجيلها، أو «لسه» وهنفكرك بعد ${p.laterMin} دقيقة."
        )
            .addAction(action("✅ صليت", "DONE", 500 + prayer.ordinal * 2))
            .addAction(action("⏳ لسه", "LATER", 501 + prayer.ordinal * 2))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(false)
            .build()
        Notif.notify(ctx, Notif.idCheck(prayer), n)
        val k = "check_${prayer.name}"
        ForcedOverlay.show(
            ctx, k, "🤲 تذكير", "هل صليت ${prayer.ar}؟", "اضغط «صليت» لتسجيلها، أو «لسه» وهنفكرك بعد ${p.laterMin} دقيقة.",
            listOf(
                ForcedOverlay.reminderButton(ctx, k, "✅ صليت", true, "DONE", prayer),
                ForcedOverlay.reminderButton(ctx, k, "⏳ لسه", false, "LATER", prayer)
            )
        )

        // تذكير تاني بعد المدة اللي اخترتها لحد ما تؤكد (بحد أقصى ٦ مرات وقبل الصلاة اللي بعدها)
        val nextPrayer = Engine.next(p)?.second?.time ?: Long.MAX_VALUE
        val at = System.currentTimeMillis() + p.laterMin * 60_000L
        if (count < 5 && at < nextPrayer) Reminders.scheduleCheck(ctx, prayer, p.laterMin, count + 1)
    }
}
