package com.hisn.muslim

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Locale

@Composable
fun CheckRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onToggle() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked, onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(checkedColor = Gold, checkmarkColor = Color.Black, uncheckedColor = Muted)
        )
        Text(label, color = if (checked) Gold else Color.White)
    }
}

@Composable
fun GoldChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Gold, selectedLabelColor = Color.Black,
            containerColor = Color(0x33000000), labelColor = Color.White
        )
    )
}

// ───────────────────────── الرئيسية ─────────────────────────

@Composable
fun HomeScreen(prefs: Prefs) {
    val tick = refreshTick.intValue
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val day = prefs.dayKey()
    var done by remember(day) { mutableStateOf(prefs.done(day)) }
    val times = remember(tick, day) { Engine.times(prefs) }
    val next = remember(tick, now / 60000) { Engine.next(prefs) }
    val prev = remember(tick, now / 60000) { Engine.previous(prefs) }
    val fmt = remember { SimpleDateFormat("hh:mm a", Locale("ar")) }
    val tomorrowFast = remember(tick, day) { Fasting.reason(prefs, LocalDate.now().plusDays(1)) }
    val todayFast = remember(tick, day) { Fasting.reason(prefs, LocalDate.now()) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Glass {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocationOn, null, tint = Gold, modifier = Modifier.size(18.dp))
                    Text(
                        "  " + prefs.city.ifBlank { if (prefs.hasLocation) "موقعك الحالي" else "موقع افتراضي" },
                        color = Muted, fontSize = 14.sp
                    )
                }
                Text(
                    Fasting.hijriText(prefs) + " • " + java.text.SimpleDateFormat("EEEE d MMMM yyyy", java.util.Locale("ar")).format(java.util.Date()),
                    color = Muted, fontSize = 13.sp
                )
                Spacer(Modifier.height(10.dp))
                if (next != null) {
                    val diff = ((next.second.time - now) / 1000).coerceAtLeast(0)
                    Text("الصلاة القادمة", color = Muted, fontSize = 13.sp)
                    Text(next.first.ar, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Gold, fontFamily = Serif)
                    Text(
                        String.format(Locale.US, "%02d:%02d:%02d", diff / 3600, (diff % 3600) / 60, diff % 60),
                        fontSize = 44.sp, fontWeight = FontWeight.Light
                    )
                    Text("الساعة ${fmt.format(next.second)}", color = Muted)
                    if (prev != null) {
                        val span = (next.second.time - prev.second.time).coerceAtLeast(1)
                        val frac = ((now - prev.second.time).toFloat() / span).coerceIn(0f, 1f)
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { frac },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            color = Gold, trackColor = Color(0x33FFFFFF)
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(next.first.ar, fontSize = 12.sp, color = Muted)
                            Text("منذ ${prev.first.ar}", fontSize = 12.sp, color = Muted)
                        }
                    }
                }
                if (!prefs.hasLocation) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "المواقيت حاليًا على موقع افتراضي. اسمح بالوصول للموقع من الإعدادات لتحديد مواقيتك بدقة.",
                        fontSize = 12.sp, color = Color(0xFFFFB4A8)
                    )
                }
            }
        }
        item {
            val fard = done.count { it.endsWith("_FARD") }
            val qada = prefs.qadaCount(day)
            Glass {
                SectionTitle("ملخص عبادة اليوم", Icons.Rounded.EmojiEvents)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("الفروض: $fard/5", fontWeight = FontWeight.Bold)
                    Text("السنن: ${prefs.sunnahCount(day)}/${prefs.sunnahTotal()}", fontWeight = FontWeight.Bold)
                    if (qada > 0) Text("قضاء: $qada", color = Gold, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (fard / 5f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = Gold, trackColor = Color(0x33FFFFFF)
                )
            }
        }
        if (todayFast != null || tomorrowFast != null) {
            item {
                Glass {
                    SectionTitle("صيام السنن", Icons.Rounded.Spa)
                    todayFast?.let { Text("اليوم: ${it.first}", fontWeight = FontWeight.Bold) }
                    tomorrowFast?.let {
                        Text("غدًا: ${it.first}", fontWeight = FontWeight.Bold)
                        Text(it.second, fontSize = 12.sp, color = Muted)
                    }
                }
            }
        }
        item {
            val pr = next?.first ?: Prayer.FAJR
            val tip = remember(pr, day) { Content.tip(pr) }
            Glass {
                SectionTitle("${tip.kind} • ${pr.ar}", Icons.Rounded.AutoAwesome)
                Text(tip.text, fontSize = 18.sp, lineHeight = 30.sp, fontFamily = Serif)
                Spacer(Modifier.height(4.dp))
                Text(tip.source, fontSize = 12.sp, color = Muted)
            }
        }
        items(Prayer.values().toList()) { pr ->
            val isNext = next?.first == pr
            val isCurrent = prev?.first == pr && !isNext
            Glass(modifier = if (isNext) Modifier.border(1.5.dp, Gold, RoundedCornerShape(24.dp)) else Modifier) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AccessTime, null, tint = if (isNext) Gold else Muted, modifier = Modifier.size(20.dp))
                        Text("  " + pr.ar, fontSize = 21.sp, fontWeight = FontWeight.Bold, fontFamily = Serif)
                        if (isNext) Text("  القادمة", fontSize = 12.sp, color = Gold)
                        else if (isCurrent) Text("  الوقت الحالي", fontSize = 12.sp, color = Muted)
                    }
                    Text(fmt.format(Engine.timeOf(times, pr)), fontSize = 20.sp, color = Gold, fontWeight = FontWeight.SemiBold)
                }
                CheckRow("صليت الفرض", pr.fardKey in done) {
                    prefs.toggle(day, pr.fardKey); done = prefs.done(day)
                }
                pr.sunnah.forEach { (key, label) ->
                    CheckRow(label, key in done) {
                        prefs.toggle(day, key); done = prefs.done(day)
                    }
                }
            }
        }
        item {
            Text(
                "الشروق: ${fmt.format(times.sunrise)}",
                Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = Muted
            )
        }
    }
}

// ───────────────────────── التحديات ─────────────────────────

@Composable
fun ChallengesScreen(prefs: Prefs) {
    val day = prefs.dayKey()
    val streak = prefs.streak()

    class Ch(val title: String, val desc: String, val cur: Int, val goal: Int)

    val list = listOf(
        Ch("فروض اليوم", "صلِّ الفروض الخمسة اليوم", prefs.fardCount(day), 5),
        Ch("سنن اليوم", "حافظ على السنن الرواتب والوتر اليوم", prefs.sunnahCount(day), prefs.sunnahTotal()),
        Ch("أسبوع من المحافظة", "٥ فروض يوميًا لمدة ٧ أيام متتالية", streak, 7),
        Ch("شهر من المحافظة", "٥ فروض يوميًا لمدة ٣٠ يومًا متتالية", streak, 30),
        Ch("فروض هذا الأسبوع", "٣٥ فرضًا خلال آخر ٧ أيام", prefs.weekFard(), 35),
        Ch("سنن هذا الأسبوع", "أكمل كل السنن في ٧ أيام من آخر ٧", prefs.sunnahFullDays(), 7)
    )
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Glass {
                SectionTitle("سلسلة المحافظة الحالية", Icons.Rounded.EmojiEvents)
                Text("$streak يوم", fontSize = 40.sp, fontWeight = FontWeight.Bold, fontFamily = Serif)
                Text("تُحسب الأيام التي سجّلت فيها الفروض الخمسة كاملة.", fontSize = 12.sp, color = Muted)
            }
        }
        items(list) { c ->
            Glass {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(c.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(if (c.cur >= c.goal) "✅ تم" else "${c.cur}/${c.goal}", color = Gold)
                }
                Text(c.desc, fontSize = 13.sp, color = Muted)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (c.cur.toFloat() / c.goal).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = Gold, trackColor = Color(0x33FFFFFF)
                )
            }
        }
    }
}

// ───────────────────────── الأذكار والتسبيح ─────────────────────────

@Composable
fun AzkarTasbihScreen(prefs: Prefs) {
    var mode by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GoldChip("الأذكار", mode == 0) { mode = 0 }
            GoldChip("السبحة والاستغفار", mode == 1) { mode = 1 }
            GoldChip("القبلة", mode == 2) { mode = 2 }
        }
        when (mode) { 0 -> AzkarScreen(prefs); 1 -> TasbihScreen(prefs); else -> QiblaScreen(prefs) }
    }
}

@Composable
fun AzkarScreen(prefs: Prefs) {
    var cat by remember { mutableStateOf("الكل") }
    var query by remember { mutableStateOf("") }
    var favTick by remember { mutableIntStateOf(0) }
    val counters = remember { mutableStateMapOf<Int, Int>() }
    val favs = remember(favTick) { prefs.favorites() }
    val day = prefs.dayKey()
    val q = query.trim()
    val shown = Content.azkar.withIndex().filter {
        (cat == "الكل" || (cat == "المفضلة" && it.index.toString() in favs) || cat in it.value.cats) &&
            (q.isEmpty() || it.value.text.contains(q) || it.value.source.contains(q))
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            (Content.azkarCats + "المفضلة").forEach { c -> GoldChip(c, cat == c) { cat = c } }
        }
        androidx.compose.material3.OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), label = { Text("بحث في الأذكار") }
        )
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (shown.isEmpty()) item { Text(if (cat == "المفضلة") "لا أذكار مفضلة بعد (اضغط ☆)." else "لا نتائج", color = Muted) }
            items(shown) { (idx, z) ->
                val c = counters[idx] ?: 0
                val fav = idx.toString() in favs
                Glass {
                    Text(z.text, fontSize = 19.sp, lineHeight = 32.sp, fontFamily = Serif)
                    Spacer(Modifier.height(6.dp))
                    Text(z.source, fontSize = 12.sp, color = Muted)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = {
                                if (c < z.count) {
                                    counters[idx] = c + 1
                                    // إتمام كل أذكار قسم (الصباح/المساء) في اليوم
                                    if (c + 1 >= z.count) {
                                        listOf("الصباح", "المساء").filter { it in z.cats }.forEach { k ->
                                            val ids = Content.azkar.withIndex().filter { k in it.value.cats }.map { it.index }
                                            val all = ids.all { i -> (if (i == idx) c + 1 else counters[i] ?: 0) >= Content.azkar[i].count }
                                            if (all) prefs.setAdhkarDone(day, k, true)
                                        }
                                    }
                                }
                            },
                            enabled = c < z.count,
                            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black)
                        ) { Text(if (c >= z.count) "تم ✅" else "$c / ${z.count}") }
                        if (c > 0) TextButton(onClick = { counters[idx] = 0 }) { Text("إعادة", color = Gold) }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { prefs.toggleFavorite(idx.toString()); favTick++ }) {
                            Text(if (fav) "★" else "☆", color = Gold, fontSize = 22.sp)
                        }
                    }
                }
            }
        }
    }
}

