package com.hisn.muslim

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("hisn", Context.MODE_PRIVATE)

    private fun str(k: String, d: String) = sp.getString(k, d) ?: d
    private fun put(k: String, v: String) = sp.edit().putString(k, v).apply()
    private fun put(k: String, v: Int) = sp.edit().putInt(k, v).apply()
    private fun put(k: String, v: Boolean) = sp.edit().putBoolean(k, v).apply()

    // ───── الموقع ─────
    var lat: Double
        get() = str("lat", "30.0444").toDouble()
        set(v) = put("lat", v.toString())
    var lng: Double
        get() = str("lng", "31.2357").toDouble()
        set(v) = put("lng", v.toString())
    var hasLocation: Boolean
        get() = sp.getBoolean("hasLoc", false)
        set(v) = put("hasLoc", v)
    var city: String
        get() = str("city", "")
        set(v) = put("city", v)

    var method: String
        get() = str("method", "EGYPTIAN")
        set(v) = put("method", v)

    // ───── الأذان ─────
    var adhanEnabled: Boolean
        get() = sp.getBoolean("adhanOn", true)
        set(v) = put("adhanOn", v)
    var fullAdhan: Boolean
        get() = sp.getBoolean("fullAdhan", true)
        set(v) = put("fullAdhan", v)
    var muezzin: String
        get() = str("muezzin", "refaat")
        set(v) = put("muezzin", v)
    var adhanVolume: Int
        get() = sp.getInt("adhanVol", 80)
        set(v) = put("adhanVol", v)
    /** تشغيل الأذان في وضع الصامت/الاهتزاز (الأذان بيشتغل على قناة المنبه) */
    var playInSilent: Boolean
        get() = sp.getBoolean("playSilent", true)
        set(v) = put("playSilent", v)
    /** آخر أذان اشتغل (صلاة@وقت الجدولة) عشان ما يتكررش لو التنبيه اتسلّم مرتين */
    var lastFireKey: String
        get() = str("lastFire", "")
        set(v) = put("lastFire", v)
    /** حالة «عدم الإزعاج» قبل ما التطبيق يغيّرها مؤقتًا أثناء الأذان (0 = ما غيّرناش حاجة) */
    var prevDnd: Int
        get() = sp.getInt("prevDnd", 0)
        set(v) = put("prevDnd", v)
    fun prayerEnabled(p: Prayer) = sp.getBoolean("en_${p.name}", true)
    fun setPrayerEnabled(p: Prayer, v: Boolean) = put("en_${p.name}", v)

    // ───── المظهر ─────
    var bg: Int
        get() = sp.getInt("bg", 0)
        set(v) = put("bg", v)
    var tasbihCount: Int
        get() = sp.getInt("tasbihCount", 0)
        set(v) = put("tasbihCount", v)
    var tasbihIndex: Int
        get() = sp.getInt("tasbihIndex", 0)
        set(v) = put("tasbihIndex", v)

    // ───── تأكيد الصلاة بعد الأذان ─────
    var checkEnabled: Boolean
        get() = sp.getBoolean("checkOn", true)
        set(v) = put("checkOn", v)
    /** بعد كام دقيقة من الأذان يسألك: صليت؟ */
    var checkAfterMin: Int
        get() = sp.getInt("checkAfter", 20)
        set(v) = put("checkAfter", v)
    /** لو ضغطت "لسه": يفكّرك تاني بعد كام دقيقة */
    var laterMin: Int
        get() = sp.getInt("laterMin", 10)
        set(v) = put("laterMin", v)

    // ───── الصيام والنوافل ─────
    var fastMonThu: Boolean
        get() = sp.getBoolean("fMonThu", true)
        set(v) = put("fMonThu", v)
    var fastWhite: Boolean
        get() = sp.getBoolean("fWhite", true)
        set(v) = put("fWhite", v)
    var fastArafah: Boolean
        get() = sp.getBoolean("fArafah", true)
        set(v) = put("fArafah", v)
    var fastAshura: Boolean
        get() = sp.getBoolean("fAshura", true)
        set(v) = put("fAshura", v)
    var fastShawwal: Boolean
        get() = sp.getBoolean("fShawwal", true)
        set(v) = put("fShawwal", v)
    var fastDhul: Boolean
        get() = sp.getBoolean("fDhul", true)
        set(v) = put("fDhul", v)
    /** وقت تذكير الصيام (دقائق من منتصف الليل) - الافتراضي ٨:٣٠ مساءً */
    var fastTime: Int
        get() = sp.getInt("fastTime", 20 * 60 + 30)
        set(v) = put("fastTime", v)
    /** تعديل التاريخ الهجري بالأيام (-٢..+٢) لمطابقة رؤية الهلال في بلدك */
    var hijriAdj: Int
        get() = sp.getInt("hijriAdj", 0)
        set(v) = put("hijriAdj", v)
    var duhaOn: Boolean
        get() = sp.getBoolean("duhaOn", true)
        set(v) = put("duhaOn", v)
    var qiyamOn: Boolean
        get() = sp.getBoolean("qiyamOn", false)
        set(v) = put("qiyamOn", v)

    // ───── بوب اب الأذكار ─────
    var popupOn: Boolean
        get() = sp.getBoolean("popupOn", true)
        set(v) = put("popupOn", v)
    var popupIntervalMin: Int
        get() = sp.getInt("popupInt", 120)
        set(v) = put("popupInt", v)
    var popupStartHour: Int
        get() = sp.getInt("popupStart", 7)
        set(v) = put("popupStart", v)
    var popupEndHour: Int
        get() = sp.getInt("popupEnd", 22)
        set(v) = put("popupEnd", v)

    // ───── المصحف والختمة ─────
    var quranPage: Int
        get() = sp.getInt("qPage", 1)
        set(v) = put("qPage", v)
    var quranFont: Int
        get() = sp.getInt("qFont", 26)
        set(v) = put("qFont", v)
    var khatmaActive: Boolean
        get() = sp.getBoolean("khActive", false)
        set(v) = put("khActive", v)
    var khatmaStart: String
        get() = str("khStart", "")
        set(v) = put("khStart", v)
    var khatmaDays: Int
        get() = sp.getInt("khDays", 30)
        set(v) = put("khDays", v)
    var wirdTime: Int
        get() = sp.getInt("wirdTime", 21 * 60)
        set(v) = put("wirdTime", v)
    var wirdReminder: Boolean
        get() = sp.getBoolean("wirdOn", true)
        set(v) = put("wirdOn", v)

    private fun intSet(k: String): Set<Int> =
        str(k, "").split(",").mapNotNull { it.toIntOrNull() }.toSet()

    private fun putIntSet(k: String, s: Set<Int>) = put(k, s.joinToString(","))

    fun khatmaPagesRead(): Set<Int> = intSet("khPages")

    fun khatmaReadToday(): Set<Int> = intSet("khDay_${dayKey()}")

    /** بيسجّل الصفحة كمقروءة في الختمة (مرة واحدة فقط لكل صفحة) */
    fun markPageRead(page: Int) {
        if (!khatmaActive) return
        val all = khatmaPagesRead()
        if (page in all) return
        putIntSet("khPages", all + page)
        putIntSet("khDay_${dayKey()}", khatmaReadToday() + page)
    }

    fun startKhatma(days: Int) {
        khatmaActive = true
        khatmaDays = days
        khatmaStart = dayKey()
        put("khPages", "")
    }

    fun stopKhatma() {
        khatmaActive = false
    }

    // ───── تتبع الصلوات ─────
    fun dayKey(d: Date = Date()): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(d)

    fun done(day: String): Set<String> = sp.getStringSet("d_$day", emptySet())!!.toSet()

    fun toggle(day: String, key: String) {
        val s = done(day).toMutableSet()
        if (!s.add(key)) s.remove(key)
        sp.edit().putStringSet("d_$day", s).apply()
    }

    fun setDone(day: String, key: String) {
        val s = done(day).toMutableSet()
        s.add(key)
        sp.edit().putStringSet("d_$day", s).apply()
    }

    fun fardCount(day: String): Int = Prayer.values().count { "${it.name}_FARD" in done(day) }

    private val allSunnahKeys = Prayer.values().flatMap { p -> p.sunnah.map { it.first } }

    fun sunnahCount(day: String): Int = done(day).count { it in allSunnahKeys }
    fun sunnahTotal(): Int = allSunnahKeys.size

    fun streak(): Int {
        val cal = Calendar.getInstance()
        if (fardCount(dayKey(cal.time)) < 5) cal.add(Calendar.DAY_OF_YEAR, -1)
        var n = 0
        while (fardCount(dayKey(cal.time)) == 5) {
            n++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return n
    }

    fun lastDays(n: Int): List<String> {
        val cal = Calendar.getInstance()
        return (0 until n).map { val k = dayKey(cal.time); cal.add(Calendar.DAY_OF_YEAR, -1); k }
    }

    fun weekFard(): Int = lastDays(7).sumOf { fardCount(it) }
    fun sunnahFullDays(): Int = lastDays(7).count { sunnahCount(it) == sunnahTotal() }
}
