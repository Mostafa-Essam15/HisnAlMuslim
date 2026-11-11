@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.hisn.muslim

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

class CustomChallenge(val id: String, val title: String, val goal: Int)

/** تحديات يومية يكتبها المستخدم (اسم + عدد يومي). كل التقدّم بيتسجّل بإدخاله هو، ومفيش احتساب تلقائي. */
fun Prefs.customChallenges(): List<CustomChallenge> = try {
    val a = JSONArray(sp.getString("customChal", "[]"))
    (0 until a.length()).map { val o = a.getJSONObject(it); CustomChallenge(o.getString("id"), o.getString("t"), o.getInt("g")) }
} catch (e: Exception) { emptyList() }

private fun Prefs.saveCustom(l: List<CustomChallenge>) {
    val a = JSONArray(); l.forEach { a.put(JSONObject().put("id", it.id).put("t", it.title).put("g", it.goal)) }
    sp.edit().putString("customChal", a.toString()).apply()
}

fun Prefs.addCustomChallenge(title: String, goal: Int) {
    saveCustom(customChallenges() + CustomChallenge(System.currentTimeMillis().toString(36), title.trim(), goal.coerceIn(1, 100000)))
}

fun Prefs.removeCustomChallenge(id: String) {
    saveCustom(customChallenges().filter { it.id != id })
    val ed = sp.edit(); sp.all.keys.filter { it.startsWith("cc_${id}_") }.forEach { ed.remove(it) }; ed.apply()
}

fun Prefs.customProgress(id: String, day: String): Int = sp.getInt("cc_${id}_$day", 0)

fun Prefs.addCustomProgress(id: String, day: String, delta: Int) {
    sp.edit().putInt("cc_${id}_$day", (customProgress(id, day) + delta).coerceAtLeast(0)).apply()
}

/** أيام متتالية حقّق فيها الهدف، لحد النهارده (لو النهارده لسه ما اكتملش بنبدأ العدّ من امبارح) */
fun Prefs.customStreak(c: CustomChallenge): Int {
    var d = LocalDate.now()
    if (customProgress(c.id, d.toString()) < c.goal) d = d.minusDays(1)
    var n = 0
    while (n < 3650 && customProgress(c.id, d.toString()) >= c.goal) { n++; d = d.minusDays(1) }
    return n
}

@Composable
fun CustomChallengesCard(prefs: Prefs) {
    var tick by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf(false) }
    val day = prefs.dayKey()
    val list = remember(tick) { prefs.customChallenges() }
    Glass {
        SectionTitle("تحدياتي المخصصة", Icons.Rounded.EmojiEvents)
        if (list.isEmpty()) Text("اعمل تحدي بنفسك: مثلًا «الاستغفار ١٠٠ مرة» أو «ورد القرآن ١٠ صفحات». أنت اللي بتسجّل تقدّمك.", fontSize = 13.sp, color = Muted)
        list.forEach { c ->
            val cur = remember(tick, day, c.id) { prefs.customProgress(c.id, day) }
            val streak = remember(tick, day, c.id) { prefs.customStreak(c) }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(c.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text(if (cur >= c.goal) "✅ ${Quran.arabicDigits(cur)}/${Quran.arabicDigits(c.goal)}" else "${Quran.arabicDigits(cur)}/${Quran.arabicDigits(c.goal)}", color = Gold)
            }
            LinearProgressIndicator(
                progress = { (cur.toFloat() / c.goal).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(8.dp).clip(CircleShape),
                color = Gold, trackColor = Track
            )
            if (streak > 0) Text("🔥 ${Quran.arabicDigits(streak)} يوم متتالي", fontSize = 12.sp, color = Muted)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { prefs.addCustomProgress(c.id, day, -1); tick++ }) { Text("−١", color = Gold) }
                OutlinedButton(onClick = { prefs.addCustomProgress(c.id, day, 1); tick++ }) { Text("+١", color = Gold) }
                OutlinedButton(onClick = { prefs.addCustomProgress(c.id, day, 10); tick++ }) { Text("+١٠", color = Gold) }
                TextButton(onClick = { prefs.removeCustomChallenge(c.id); tick++ }) { Text("حذف", color = Color(0xFFFF8A80)) }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { dialog = true }) { Text("＋ تحدّي جديد", color = Gold) }
    }
    if (dialog) {
        var name by remember { mutableStateOf("") }
        var goal by remember { mutableStateOf("100") }
        AlertDialog(
            onDismissRequest = { dialog = false },
            title = { Text("تحدّي جديد") },
            text = {
                androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("اسم التحدي") }, singleLine = true)
                    OutlinedTextField(
                        goal, { goal = it.filter { c -> c.isDigit() }.take(6) }, label = { Text("الهدف اليومي (عدد)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val g = goal.toIntOrNull() ?: 0
                    if (name.isNotBlank() && g > 0) { prefs.addCustomChallenge(name, g); tick++; dialog = false }
                }) { Text("إضافة", color = Gold) }
            },
            dismissButton = { TextButton(onClick = { dialog = false }) { Text("إلغاء") } }
        )
    }
}

/** أيام تذكير الأذكار: بت لكل يوم حسب java.time.DayOfWeek (١ الاثنين … ٧ الأحد)، الافتراضي كل الأيام */
var Prefs.adhkarDays: Int
    get() = sp.getInt("adhkarDays", 127)
    set(v) { sp.edit().putInt("adhkarDays", v and 127).apply() }

fun Prefs.adhkarDayOn(dow: java.time.DayOfWeek) = adhkarDays and (1 shl (dow.value - 1)) != 0
