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
val Prayer.missKey: String get() = "${name}_MISS"

fun Prefs.wasPrompted(day: String, p: Prayer): Boolean = sp.getBoolean("pq_${day}_${p.name}", false)
fun Prefs.markPrompted(day: String, p: Prayer) { sp.edit().putBoolean("pq_${day}_${p.name}", true).apply() }

fun Prefs.qadaCount(day: String): Int = Prayer.values().count { it.missKey in done(day) } // صلوات فاتت وتنتظر القضاء

/** حالة الصلاة في يوم: 2 = أُديت في وقتها، 1 = متأخرة/قضاء، 3 = لم أصلِّ، 0 = بلا تسجيل */
fun Prefs.prayerStatus(day: String, p: Prayer): Int {
    val d = done(day)
    return when {
        p.fardKey in d -> 2
        p.qadaKey in d -> 1
        p.missKey in d -> 3
        else -> 0
    }
}

/** تعيين حالة الصلاة بشكل متبادل: أُديت ⟷ قضاء ⟷ لا شيء */
fun Prefs.setPrayerStatus(day: String, p: Prayer, status: Int) {
    val s = done(day).toMutableSet()
    s.remove(p.fardKey)
    s.remove(p.qadaKey)
    s.remove(p.missKey)
    if (status == 3) s.add(p.missKey)
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

// ───────────── أذان الفجر ─────────────

/** استخدام ملف أذان الفجر الخاص (لو المستخدم أضافه) بدل أذان باقي الصلوات */
var Prefs.fajrCustomOn: Boolean
    get() = sp.getBoolean("fajrCustomOn", true)
    set(v) { sp.edit().putBoolean("fajrCustomOn", v).apply() }

// ───────────── تخصيص الشاشة الرئيسية ─────────────

val HOME_CARDS = listOf(
    "next" to "الصلاة القادمة والعدّاد",
    "summary" to "ملخص عبادة اليوم",
    "shortcuts" to "اختصارات (مصحف/أذكار/سبحة/قبلة)",
    "ayah" to "آية اليوم (مع تفسيرها)",
    "hadith" to "حديث اليوم",
    "fasting" to "صيام السنن",
    "tip" to "فائدة الصلاة",
    "prayers" to "قائمة الصلوات وتسجيلها"
)

/** ترتيب وظهور بطاقات الرئيسية: (المعرّف، ظاهرة؟). البطاقات الجديدة بتتضاف تلقائيًا في آخر القائمة. */
fun Prefs.homeLayout(): List<Pair<String, Boolean>> {
    val saved = (sp.getString("homeLayout", "") ?: "").split(",").mapNotNull {
        val p = it.split(":")
        if (p.size == 2 && HOME_CARDS.any { c -> c.first == p[0] }) p[0] to (p[1] == "1") else null
    }
    val known = saved.map { it.first }.toSet()
    return saved + HOME_CARDS.filter { it.first !in known }.map { it.first to true }
}

fun Prefs.setHomeLayout(l: List<Pair<String, Boolean>>) {
    sp.edit().putString("homeLayout", l.joinToString(",") { "${it.first}:${if (it.second) 1 else 0}" }).apply()
}

// ───────────── وضع قراءة المصحف ─────────────

/** 0 = النص العثماني (الافتراضي)، 1 = مصحف المدينة بصفحاته الحقيقية */
var Prefs.mushafMode: Int
    get() = sp.getInt("mushafMode", 0)
    set(v) { sp.edit().putInt("mushafMode", v).apply() }

/** تذكير قبل الصلاة بعدد دقائق (٠ = متوقف) */
var Prefs.preMin: Int
    get() = sp.getInt("preMin", 0)
    set(v) { sp.edit().putInt("preMin", v.coerceIn(0, 60)).apply() }

/** تذكير الإقامة بعد الأذان بعدد دقائق (٠ = متوقف) */
var Prefs.iqamaMin: Int
    get() = sp.getInt("iqamaMin", 0)
    set(v) { sp.edit().putInt("iqamaMin", v.coerceIn(0, 60)).apply() }

/** العد بأزرار الصوت داخل شاشة السبحة */
var Prefs.tasbihVolume: Boolean
    get() = sp.getBoolean("tsVol", false)
    set(v) { sp.edit().putBoolean("tsVol", v).apply() }

/** إجمالي التسبيح لآخر [days] يوم، الأقدم أولًا: (تاريخ، إجمالي) */
fun Prefs.tasbihHistory(days: Int): List<Pair<java.time.LocalDate, Int>> {
    val today = java.time.LocalDate.now()
    return (days - 1 downTo 0).map { off -> val d = today.minusDays(off.toLong()); d to tasbihTotal(d.toString()) }
}

/** أكثر الأذكار تسبيحًا في آخر [days] يوم */
fun Prefs.tasbihTop(days: Int): List<Pair<String, Int>> {
    val acc = HashMap<String, Int>()
    val today = java.time.LocalDate.now()
    for (off in 0 until days) tasbihDay(today.minusDays(off.toLong()).toString()).forEach { (k, v) -> acc[k] = (acc[k] ?: 0) + v }
    return acc.entries.sortedByDescending { it.value }.map { it.key to it.value }
}

/** قناة بسيطة بين MainActivity وشاشة السبحة لأزرار الصوت */
object TasbihBus { @Volatile var onVolume: (() -> Unit)? = null }

/** تكبير/تصغير صفحة المصحف (١ = ملء العرض) */
var Prefs.mushafScale: Float
    get() = sp.getFloat("mushafScale", 1f)
    set(v) { sp.edit().putFloat("mushafScale", v).apply() }

/** الثيم الفاتح (الافتراضي: الداكن) */
var Prefs.lightTheme: Boolean
    get() = sp.getBoolean("lightTheme", false)
    set(v) { sp.edit().putBoolean("lightTheme", v).apply() }

/** مؤذن مخصص لصلاة معيّنة ("" = المؤذن العام) */
fun Prefs.muezzinFor(p: Prayer): String = sp.getString("muez_${p.name}", "") ?: ""
fun Prefs.setMuezzinFor(p: Prayer, key: String) { sp.edit().putString("muez_${p.name}", key).apply() }
/** مفتاح المؤذن الفعلي للصلاة: الخاص بها لو محدد، وإلا العام */
fun Prefs.effectiveMuezzin(p: Prayer): String = muezzinFor(p).ifBlank { muezzin }
