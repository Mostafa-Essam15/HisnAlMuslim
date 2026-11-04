package com.hisn.muslim

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("hisn", Context.MODE_PRIVATE)

    // الموقع (القاهرة افتراضيًا إلى أن يُحدَّد الموقع الفعلي)
    var lat: Double
        get() = sp.getString("lat", "30.0444")!!.toDouble()
        set(v) { sp.edit().putString("lat", v.toString()).apply() }
    var lng: Double
        get() = sp.getString("lng", "31.2357")!!.toDouble()
        set(v) { sp.edit().putString("lng", v.toString()).apply() }
    var hasLocation: Boolean
        get() = sp.getBoolean("hasLoc", false)
        set(v) { sp.edit().putBoolean("hasLoc", v).apply() }

    var method: String
        get() = sp.getString("method", "EGYPTIAN")!!
        set(v) { sp.edit().putString("method", v).apply() }

    // الأذان
    var adhanEnabled: Boolean
        get() = sp.getBoolean("adhanOn", true)
        set(v) { sp.edit().putBoolean("adhanOn", v).apply() }
    var fullAdhan: Boolean
        get() = sp.getBoolean("fullAdhan", true)
        set(v) { sp.edit().putBoolean("fullAdhan", v).apply() }
    var muezzin: String
        get() = sp.getString("muezzin", "refaat")!!
        set(v) { sp.edit().putString("muezzin", v).apply() }
    fun prayerEnabled(p: Prayer) = sp.getBoolean("en_${p.name}", true)
    fun setPrayerEnabled(p: Prayer, v: Boolean) { sp.edit().putBoolean("en_${p.name}", v).apply() }

    // الخلفية والتسبيح
    var bg: Int
        get() = sp.getInt("bg", 0)
        set(v) { sp.edit().putInt("bg", v).apply() }
    var tasbihCount: Int
        get() = sp.getInt("tasbihCount", 0)
        set(v) { sp.edit().putInt("tasbihCount", v).apply() }
    var tasbihIndex: Int
        get() = sp.getInt("tasbihIndex", 0)
        set(v) { sp.edit().putInt("tasbihIndex", v).apply() }

    // تتبع الصلوات
    fun dayKey(d: Date = Date()): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(d)

    fun done(day: String): Set<String> = sp.getStringSet("d_$day", emptySet())!!.toSet()

    fun toggle(day: String, key: String) {
        val s = done(day).toMutableSet()
        if (!s.add(key)) s.remove(key)
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
