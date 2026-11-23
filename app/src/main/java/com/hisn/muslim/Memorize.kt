@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.hisn.muslim

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape

// ───────────── الحفظ والمراجعة: تخزين التقدم ─────────────

/** الآيات المحفوظة بصيغة «سورة:آية»؛ المستخدم هو اللي بيسجّل، والتطبيق مبيحسبش حفظ تلقائي */
fun Prefs.memorized(): Set<String> = sp.getStringSet("memAyahs", emptySet())?.toSet() ?: emptySet()

fun Prefs.setMemorized(surah: Int, ayah: Int, on: Boolean) {
    val key = "$surah:$ayah"
    val s = memorized().toMutableSet()
    val had = key in s
    if (on) s.add(key) else s.remove(key)
    sp.edit().putStringSet("memAyahs", s).apply()
    if (on && !had) sp.edit().putInt("memDay_${dayKey()}", memToday() + 1).apply()
    if (!on && had) sp.edit().putInt("memDay_${dayKey()}", (memToday() - 1).coerceAtLeast(0)).apply()
}

fun Prefs.memToday(): Int = sp.getInt("memDay_${dayKey()}", 0)
fun Prefs.memWeek(): Int = lastDays(7).sumOf { sp.getInt("memDay_$it", 0) }

/** هدف الحفظ اليومي بالآيات */
var Prefs.memGoal: Int
    get() = sp.getInt("memGoal", 5)
    set(v) { sp.edit().putInt("memGoal", v.coerceIn(1, 100)).apply() }

// ───────────── المراجعة بالتكرار المتباعد (ليتنر) ─────────────

private val REV_GAPS = intArrayOf(1, 3, 7, 14, 30, 60)

/** key "سورة:آية" -> (المرحلة، تاريخ الاستحقاق yyyy-MM-dd) */
private fun Prefs.revMap(): MutableMap<String, Pair<Int, String>> {
    val m = mutableMapOf<String, Pair<Int, String>>()
    (sp.getString("memRev", "") ?: "").split(";").forEach { e ->
        val p = e.split("|")
        if (p.size == 3) p[1].toIntOrNull()?.let { m[p[0]] = it to p[2] }
    }
    return m
}

private fun Prefs.saveRev(m: Map<String, Pair<Int, String>>) {
    sp.edit().putString("memRev", m.entries.joinToString(";") { "${it.key}|${it.value.first}|${it.value.second}" }).apply()
}

/** المحفوظ اللي حان موعد مراجعته (اللي بلا سجل يُعتبر مستحقًا اليوم)، الأقدم استحقاقًا أولًا */
fun Prefs.reviewDue(limit: Int = 10): List<String> {
    val today = dayKey()
    val rev = revMap()
    return memorized().filter { (rev[it]?.second ?: today) <= today }
        .sortedWith(compareBy({ rev[it]?.second ?: today }, { it.substringBefore(":").toInt() }, { it.substringAfter(":").toInt() }))
        .take(limit)
}

fun Prefs.reviewCountDue(): Int {
    val today = dayKey()
    val rev = revMap()
    return memorized().count { (rev[it]?.second ?: today) <= today }
}

/** [ok] = راجعتها بنجاح: ننقلها لمرحلة أبعد؛ غير كده ترجع لمرحلة اليوم التالي */
fun Prefs.markReviewed(key: String, ok: Boolean) {
    val rev = revMap()
    val box = if (ok) ((rev[key]?.first ?: 0) + 1).coerceAtMost(REV_GAPS.size) else 1
    val gap = REV_GAPS[box - 1]
    val due = java.time.LocalDate.now().plusDays(gap.toLong()).toString()
    rev[key] = box to due
    saveRev(rev)
}

var Prefs.reviewReminder: Boolean
    get() = sp.getBoolean("revRemind", true)
    set(v) { sp.edit().putBoolean("revRemind", v).apply() }

@Composable
fun MemorizePane(prefs: Prefs) {
    val ctx = LocalContext.current
    var surah by remember { mutableIntStateOf(1) }
    var from by remember { mutableIntStateOf(1) }
    var to by remember { mutableIntStateOf(7) }
    var hidden by remember { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }
    val revealed = remember(surah, from, to) { mutableStateMapOf<Int, Boolean>() }
    var goal by remember { mutableIntStateOf(prefs.memGoal) }
    val mem = remember(tick) { prefs.memorized() }
    val info = Quran.surahs.firstOrNull { it.number == surah }
    val list = remember(surah, from, to) {
        Quran.ayahs.filter { it.surah == surah && it.num in from..to }
    }
    val doneToday = remember(tick) { prefs.memToday() }
    val week = remember(tick) { prefs.memWeek() }
    val reciter = prefs.reciter

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Glass {
                SectionTitle("تقدّم الحفظ")
                Text("المحفوظ: ${Quran.arabicDigits(mem.size)} آية من ${Quran.arabicDigits(Quran.ayahs.size)}", fontWeight = FontWeight.Bold)
                LinearProgressIndicator(
                    progress = { (mem.size / Quran.ayahs.size.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = Gold, trackColor = Track
                )
                Spacer(Modifier.height(6.dp))
                Text("اليوم: $doneToday / $goal آية • هذا الأسبوع: $week آية", color = Muted, fontSize = 13.sp)
                ChipRow(listOf("٣" to 3, "٥" to 5, "١٠" to 10, "٢٠" to 20), goal) { goal = it; prefs.memGoal = it }
                Text("الهدف اليومي بالآيات (تسجّل الآية بنفسك بعد ما تسمّعها).", fontSize = 11.sp, color = Muted)
            }
        }
        item {
            val due = remember(tick) { prefs.reviewDue() }
            val totalDue = remember(tick) { prefs.reviewCountDue() }
            Glass {
                SectionTitle("مراجعة اليوم")
                var rem by remember { mutableStateOf(prefs.reviewReminder) }
                CheckRow("تذكير يوميًا الساعة ٧ مساءً (لو فيه آيات مستحقة)", rem) {
                    rem = !rem; prefs.reviewReminder = rem; Reminders.scheduleReview(ctx)
                }
                if (mem.isEmpty()) {
                    Text("سجّل الآيات اللي حفظتها (زر «حفظتها ✓») وهتظهر هنا للمراجعة على فترات متباعدة: بعد يوم ثم ٣ ثم ٧ ثم ١٤ ثم ٣٠ ثم ٦٠ يومًا.", color = Muted, fontSize = 13.sp)
                } else if (due.isEmpty()) {
                    Text("خلّصت مراجعة اليوم. ما شاء الله 🤍", color = Gold, fontWeight = FontWeight.Bold)
                } else {
                    Text("مستحق للمراجعة: ${Quran.arabicDigits(totalDue)} آية (بنعرض ${Quran.arabicDigits(due.size)} كل مرة). سمّع من ذاكرتك ثم اضغط.", color = Muted, fontSize = 12.sp)
                    due.forEach { key ->
                        val sn = key.substringBefore(":").toInt()
                        val an = key.substringAfter(":").toInt()
                        val ay = Quran.ayahs.firstOrNull { it.surah == sn && it.num == an }
                        var shown by remember(key) { mutableStateOf(false) }
                        Spacer(Modifier.height(8.dp))
                        Text("${Quran.surahName(sn)} — آية ${Quran.arabicDigits(an)}", color = Gold, fontWeight = FontWeight.Bold)
                        if (ay != null) Text(
                            if (shown) ay.text else ay.text.split(" ").first() + " …",
                            fontFamily = Serif, fontSize = 20.sp, lineHeight = 32.sp, color = if (shown) androidx.compose.ui.graphics.Color.Unspecified else Muted
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { shown = !shown }) { Text(if (shown) "إخفاء" else "إظهار", color = Gold) }
                            if (ay != null) TextButton(onClick = { QuranAudio.play(ctx, reciter, listOf(ay), 0, { 0 }) {} }) { Text("▶", color = Gold) }
                            TextButton(onClick = { prefs.markReviewed(key, true); tick++ }) { Text("راجعتها ✓", color = Gold) }
                            TextButton(onClick = { prefs.markReviewed(key, false); tick++ }) { Text("نسيتها", color = Gold) }
                        }
                    }
                }
            }
        }
        item {
            Glass {
                SectionTitle("اختر ما تحفظه")
                androidx.compose.foundation.layout.FlowRow(
                    Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = if (bigFont()) 1 else 3
                ) {
                    OutlinedTextField(
                        value = surah.toString(), onValueChange = { v ->
                            v.toIntOrNull()?.let { n ->
                                if (n in 1..114) {
                                    surah = n; from = 1
                                    to = minOf(Quran.surahs.firstOrNull { it.number == n }?.ayahCount ?: 1, 10)
                                }
                            }
                        },
                        label = { Text("رقم السورة") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = from.toString(), onValueChange = { v ->
                            v.toIntOrNull()?.let { if (it in 1..(info?.ayahCount ?: 1)) { from = it; if (to < it) to = it } }
                        },
                        label = { Text("من آية") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = to.toString(), onValueChange = { v ->
                            v.toIntOrNull()?.let { if (it in from..(info?.ayahCount ?: 1)) to = it }
                        },
                        label = { Text("إلى آية") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Text("${info?.name ?: ""} — عدد آياتها ${Quran.arabicDigits(info?.ayahCount ?: 0)}", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoldChip(if (hidden) "النص مخفي (سمّع من ذاكرتك)" else "النص ظاهر", hidden) { hidden = !hidden; revealed.clear() }
                    OutlinedButton(onClick = {
                        QuranAudio.play(ctx, reciter, list, 0, { 0 }) {}
                    }) { Text("▶ استمع للمقطع", color = Gold, fontSize = 12.sp) }
                    OutlinedButton(onClick = { QuranAudio.stop() }) { Text("⏹", color = Gold) }
                }
            }
        }
        items(list) { a ->
            val key = "${a.surah}:${a.num}"
            val isMem = key in mem
            val show = !hidden || revealed[a.num] == true
            Glass {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("آية ${Quran.arabicDigits(a.num)}", color = Gold, fontWeight = FontWeight.Bold)
                    if (isMem) Text("✅ محفوظة", color = Gold, fontSize = 12.sp)
                }
                if (show) {
                    Text(a.text, fontFamily = Serif, fontSize = 22.sp, lineHeight = 36.sp)
                } else {
                    // إشارة أول كلمة فقط كتلميح
                    Text(a.text.split(" ").first() + " …", fontFamily = Serif, fontSize = 22.sp, color = Muted)
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (hidden) TextButton(onClick = { revealed[a.num] = !(revealed[a.num] ?: false) }) {
                        Text(if (revealed[a.num] == true) "إخفاء" else "إظهار للمراجعة", color = Gold)
                    }
                    TextButton(onClick = { QuranAudio.play(ctx, reciter, listOf(a), 0, { 1 }) {} }) { Text("🔂 كرّر الآية", color = Gold) }
                    TextButton(onClick = { prefs.setMemorized(a.surah, a.num, !isMem); tick++ }) {
                        Text(if (isMem) "إلغاء الحفظ" else "حفظتها ✓", color = Gold)
                    }
                }
            }
        }
    }
}
