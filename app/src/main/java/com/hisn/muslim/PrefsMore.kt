package com.hisn.muslim

import org.json.JSONObject

// ───────────── طريقة حساب المواقيت ─────────────

/** مذهب حساب العصر: SHAFI (الجمهور) أو HANAFI */
var Prefs.asrMethod: String
    get() = sp.getString("asrMethod", "SHAFI") ?: "SHAFI"
    set(v) { sp.edit().putString("asrMethod", v).apply() }

/** قاعدة خطوط العرض العالية: DEFAULT (حسب المكتبة) أو MIDDLE_OF_THE_NIGHT / SEVENTH_OF_THE_NIGHT / TWILIGHT_ANGLE */
var Prefs.highLat: String
    get() = sp.getString("highLat", "DEFAULT") ?: "DEFAULT"
    set(v) { sp.edit().putString("highLat", v).apply() }

/** تعديل يدوي لوقت كل صلاة بالدقائق (‎-30..+30) */
fun Prefs.adj(p: Prayer): Int = sp.getInt("adj_${p.name}", 0)
fun Prefs.setAdj(p: Prayer, v: Int) { sp.edit().putInt("adj_${p.name}", v.coerceIn(-30, 30)).apply() }

// ───────────── تتبع الصلاة (إتمام / قضاء) ─────────────

var Prefs.trackingEnabled: Boolean
    get() = sp.getBoolean("trackOn", true)
    set(v) { sp.edit().putBoolean("trackOn", v).apply() }

/** بوب اب «هل أتممت صلاة…؟» عند انتهاء وقت الصلاة */
var Prefs.periodPopupOn: Boolean
    get() = sp.getBoolean("periodPopup", true)
    set(v) { sp.edit().putBoolean("periodPopup", v).apply() }

val Prayer.qadaKey: String get() = "${name}_QADA"

fun Prefs.wasPrompted(day: String, p: Prayer): Boolean = sp.getBoolean("pq_${day}_${p.name}", false)
fun Prefs.markPrompted(day: String, p: Prayer) { sp.edit().putBoolean("pq_${day}_${p.name}", true).apply() }

fun Prefs.qadaCount(day: String): Int = Prayer.values().count { it.qadaKey in done(day) }

/** حالة الصلاة في يوم: 2 = أُديت، 1 = قضاء، 0 = بلا تسجيل */
fun Prefs.prayerStatus(day: String, p: Prayer): Int {
    val d = done(day)
    return when {
        p.fardKey in d -> 2
        p.qadaKey in d -> 1
        else -> 0
    }
}

/** تعيين حالة الصلاة بشكل متبادل: أُديت ⟷ قضاء ⟷ لا شيء */
fun Prefs.setPrayerStatus(day: String, p: Prayer, status: Int) {
    val s = done(day).toMutableSet()
    s.remove(p.fardKey)
    s.remove(p.qadaKey)
    if (status == 2) s.add(p.fardKey)
    if (status == 1) s.add(p.qadaKey)
    sp.edit().putStringSet("d_$day", s).apply()
}

// ───────────── القراءة في المصحف ─────────────

/** ثيم القراءة: 0 ورق كلاسيكي، 1 أبيض، 2 داكن مريح */
var Prefs.quranTheme: Int
    get() = sp.getInt("qTheme", 2)
    set(v) { sp.edit().putInt("qTheme", v).apply() }

var Prefs.reciter: String
    get() = sp.getString("reciter", "Alafasy_128kbps") ?: "Alafasy_128kbps"
    set(v) { sp.edit().putString("reciter", v).apply() }

/** 0 بدون تكرار، 1 تكرار الآية، 2 تكرار الصفحة */
var Prefs.repeatMode: Int
    get() = sp.getInt("repeatMode", 0)
    set(v) { sp.edit().putInt("repeatMode", v).apply() }

private fun Prefs.intSetOf(k: String): Set<Int> =
    (sp.getString(k, "") ?: "").split(",").mapNotNull { it.toIntOrNull() }.toSet()

private fun Prefs.putIntSetOf(k: String, s: Set<Int>) {
    sp.edit().putString(k, s.sorted().joinToString(",")).apply()
}

fun Prefs.bookmarks(): Set<Int> = intSetOf("qBookmarks")
fun Prefs.toggleBookmark(page: Int) {
    val s = bookmarks().toMutableSet()
    if (!s.add(page)) s.remove(page)
    putIntSetOf("qBookmarks", s)
}

/** الصفحات اللي اتمّت قراءتها في يوم (مستقلة عن الختمة) */
fun Prefs.pagesReadOn(day: String): Set<Int> = intSetOf("qRead_$day")
fun Prefs.recordPageRead(page: Int) {
    val day = dayKey()
    putIntSetOf("qRead_$day", pagesReadOn(day) + page)
    markPageRead(page) // الختمة (لو شغّالة)
}

// ───────────── الختمة: السجل ─────────────

fun Prefs.khatmaReadOn(day: String): Int = intSetOf("khDay_$day").size

/** تعديل يدوي للورد اليومي (0 = يحسبه التطبيق تلقائيًا) */
var Prefs.khatmaGoalOverride: Int
    get() = sp.getInt("khGoal", 0)
    set(v) { sp.edit().putInt("khGoal", v).apply() }

// ───────────── السبحة ─────────────

var Prefs.vibrateOn: Boolean
    get() = sp.getBoolean("vibOn", true)
    set(v) { sp.edit().putBoolean("vibOn", v).apply() }

var Prefs.tasbihSound: Boolean
    get() = sp.getBoolean("tsSound", false)
    set(v) { sp.edit().putBoolean("tsSound", v).apply() }

/** أذكار السبحة المخصصة (مفصولة بسطر جديد) */
var Prefs.customDhikr: List<String>
    get() = (sp.getString("customDhikr", "") ?: "").split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    set(v) { sp.edit().putString("customDhikr", v.joinToString("\n")).apply() }

fun Prefs.tasbihTarget(name: String): Int = sp.getInt("tsTarget_$name", 33)
fun Prefs.setTasbihTarget(name: String, v: Int) { sp.edit().putInt("tsTarget_$name", v.coerceIn(1, 100000)).apply() }

/** عدّاد كل ذكر في يوم معيّن: "اسم=عدد" داخل JSON */
fun Prefs.tasbihDay(day: String): Map<String, Int> {
    val raw = sp.getString("tsDay_$day", null) ?: return emptyMap()
    return try {
        val o = JSONObject(raw)
        o.keys().asSequence().associateWith { o.optInt(it, 0) }
    } catch (e: Exception) {
        emptyMap()
    }
}

fun Prefs.tasbihAdd(day: String, name: String, n: Int = 1) {
    val m = tasbihDay(day).toMutableMap()
    m[name] = (m[name] ?: 0) + n
    val o = JSONObject()
    m.forEach { (k, v) -> o.put(k, v) }
    sp.edit().putString("tsDay_$day", o.toString()).apply()
}

fun Prefs.tasbihReset(day: String, name: String) {
    val m = tasbihDay(day).toMutableMap()
    m.remove(name)
    val o = JSONObject()
    m.forEach { (k, v) -> o.put(k, v) }
    sp.edit().putString("tsDay_$day", o.toString()).apply()
}

fun Prefs.tasbihTotal(day: String): Int = tasbihDay(day).values.sum()

// ───────────── الأذكار ─────────────

fun Prefs.favorites(): Set<String> = sp.getStringSet("favAzkar", emptySet())?.toSet() ?: emptySet()
fun Prefs.toggleFavorite(id: String) {
    val s = favorites().toMutableSet()
    if (!s.add(id)) s.remove(id)
    sp.edit().putStringSet("favAzkar", s).apply()
}

fun Prefs.favoriteHadith(): Set<String> = sp.getStringSet("favHadith", emptySet())?.toSet() ?: emptySet()
fun Prefs.toggleFavoriteHadith(id: String) {
    val s = favoriteHadith().toMutableSet()
    if (!s.add(id)) s.remove(id)
    sp.edit().putStringSet("favHadith", s).apply()
}

/** أقسام الأذكار اللي اتمّت في يوم (مثلًا «الصباح») */
fun Prefs.adhkarDone(day: String): Set<String> = sp.getStringSet("adhkarDone_$day", emptySet())?.toSet() ?: emptySet()
fun Prefs.setAdhkarDone(day: String, cat: String, done: Boolean) {
    val s = adhkarDone(day).toMutableSet()
    if (done) s.add(cat) else s.remove(cat)
    sp.edit().putStringSet("adhkarDone_$day", s).apply()
}

// ───────────── وضع التركيز (عدم الإزعاج حول الصلاة) ─────────────

var Prefs.focusOn: Boolean
    get() = sp.getBoolean("focusOn", false)
    set(v) { sp.edit().putBoolean("focusOn", v).apply() }

/** يبدأ قبل الأذان بكام دقيقة */
var Prefs.focusBeforeMin: Int
    get() = sp.getInt("focusBefore", 5)
    set(v) { sp.edit().putInt("focusBefore", v).apply() }

/** يستمر بعد الأذان بكام دقيقة */
var Prefs.focusAfterMin: Int
    get() = sp.getInt("focusAfter", 20)
    set(v) { sp.edit().putInt("focusAfter", v).apply() }

fun Prefs.focusPrayer(p: Prayer): Boolean = sp.getBoolean("focus_${p.name}", true)
fun Prefs.setFocusPrayer(p: Prayer, v: Boolean) { sp.edit().putBoolean("focus_${p.name}", v).apply() }

/** حالة «عدم الإزعاج» قبل ما يشغّل التطبيق وضع التركيز (0 = ما غيّرناش) */
var Prefs.focusPrev: Int
    get() = sp.getInt("focusPrev", 0)
    set(v) { sp.edit().putInt("focusPrev", v).apply() }

/** لو المستخدم أوقف التركيز يدويًا، بنتجاهل النافذة الحالية لحد وقت (ملّي ثانية) */
var Prefs.focusOverrideUntil: Long
    get() = sp.getLong("focusOverride", 0L)
    set(v) { sp.edit().putLong("focusOverride", v).apply() }

// ───────────── رمضان ─────────────

var Prefs.ramadanReminders: Boolean
    get() = sp.getBoolean("ramRem", true)
    set(v) { sp.edit().putBoolean("ramRem", v).apply() }

/** تذكير السحور قبل الفجر بكام دقيقة */
var Prefs.suhoorBeforeMin: Int
    get() = sp.getInt("suhoorMin", 45)
    set(v) { sp.edit().putInt("suhoorMin", v).apply() }

fun Prefs.charityDone(day: String): Boolean = sp.getBoolean("charity_$day", false)
fun Prefs.setCharityDone(day: String, v: Boolean) { sp.edit().putBoolean("charity_$day", v).apply() }

// ───────────── عام ─────────────

/** إظهار التحديات والسلاسل (يمكن إيقافه) */
var Prefs.gamify: Boolean
    get() = sp.getBoolean("gamify", true)
    set(v) { sp.edit().putBoolean("gamify", v).apply() }

/** تذكير يومي بالأذكار (الصباح/المساء) كإشعار */
var Prefs.adhkarReminders: Boolean
    get() = sp.getBoolean("adhkarRem", false)
    set(v) { sp.edit().putBoolean("adhkarRem", v).apply() }

/** تلقائي: صباح 6:30 ومساء 17:30 */
var Prefs.morningMin: Int
    get() = sp.getInt("morningMin", 6 * 60 + 30)
    set(v) { sp.edit().putInt("morningMin", v).apply() }

var Prefs.eveningMin: Int
    get() = sp.getInt("eveningMin", 17 * 60 + 30)
    set(v) { sp.edit().putInt("eveningMin", v).apply() }
