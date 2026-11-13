@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

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
import androidx.compose.foundation.layout.width
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
import kotlinx.coroutines.withContext
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
        Text(label, color = if (checked) Gold else OnCard)
    }
}

@Composable
fun GoldChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(label, maxLines = 1, softWrap = false) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Gold, selectedLabelColor = if (Look.light) Color.White else Color.Black,
            containerColor = if (Look.light) Color(0x14000000) else Color(0x33000000), labelColor = OnCard
        )
    )
}

// ───────────────────────── الرئيسية ─────────────────────────

/** نقطة بداية شاشة الأذكار/السبحة/القبلة عند الفتح من اختصار: 0 أذكار، 1 سبحة، 2 قبلة */
val azkarStartMode = mutableIntStateOf(0)
/** صفحة المصحف المطلوب فتحها عند الضغط على آية اليوم من الرئيسية */
val homeQuranJump = mutableIntStateOf(0)

@Composable
fun HomeScreen(prefs: Prefs, onNavigate: (Int) -> Unit = {}) {
    val tick = refreshTick.intValue
    val order = remember(tick) { prefs.homeLayout().filter { it.second }.map { it.first } }
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
    LaunchedEffect(tick, prefs.lat, prefs.lng) { withContext(kotlinx.coroutines.Dispatchers.IO) { Weather.refresh(prefs) } }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // بعد التحديث الأندرويد/الشركة ممكن يسحب إذن «شاشة الأذان الكاملة»: ننبّه بكارت ثابت لحد ما يتفعّل تاني
        if (android.os.Build.VERSION.SDK_INT >= 34) item {
            val ctxF = androidx.compose.ui.platform.LocalContext.current
            val tickF = refreshTick.intValue
            val okFs = remember(tickF) { ctxF.getSystemService(android.app.NotificationManager::class.java).canUseFullScreenIntent() }
            if (!okFs) Glass(Modifier.border(1.5.dp, Color(0xFFFFB74D), RoundedCornerShape(24.dp))) {
                SectionTitle("فعّل شاشة الأذان الكاملة", Icons.Rounded.AutoAwesome)
                Text("إذن «الإشعارات بملء الشاشة» اتسحب (غالبًا بعد التحديث). من غيره الأذان مش هيظهر فوق شاشة القفل.", fontSize = 13.sp, color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.Button(
                        onClick = {
                            try {
                                ctxF.startActivity(
                                    android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, android.net.Uri.parse("package:${ctxF.packageName}"))
                                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } catch (_: Exception) {
                                ctxF.startActivity(
                                    android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${ctxF.packageName}"))
                                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black)
                    ) { Text("تفعيل الآن") }
                    if (android.os.Build.MANUFACTURER.lowercase().let { "xiaomi" in it || "redmi" in it || "poco" in it }) {
                        androidx.compose.material3.OutlinedButton(onClick = {
                            try {
                                ctxF.startActivity(
                                    android.content.Intent("miui.intent.action.APP_PERM_EDITOR")
                                        .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                                        .putExtra("extra_pkgname", ctxF.packageName).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } catch (_: Exception) {
                                ctxF.startActivity(
                                    android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${ctxF.packageName}"))
                                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }) { Text("أذونات شاومي", color = Gold) }
                    }
                }
            }
        }
        Updater.available?.let { up ->
            item {
                val ctxU = androidx.compose.ui.platform.LocalContext.current
                Glass(Modifier.border(1.5.dp, Gold, RoundedCornerShape(24.dp))) {
                    SectionTitle("تحديث جديد متاح", Icons.Rounded.AutoAwesome)
                    if (up.notes.isNotBlank()) Text(up.notes, fontSize = 14.sp)
                    if (up.size > 0) Text("الحجم: ${"%.1f".format(Locale.US, up.size / 1048576.0)} ميجا", fontSize = 12.sp, color = Muted)
                    if (Updater.progress >= 0) {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(progress = { Updater.progress / 100f }, modifier = Modifier.fillMaxWidth(), color = Gold, trackColor = Track)
                        Text("جارٍ التنزيل ${Updater.progress}%", fontSize = 12.sp, color = Gold)
                    } else {
                        TextButton(onClick = { Updater.downloadAndInstall(ctxU, up) }) { Text("⬇ تحميل وتثبيت الآن", color = Gold) }
                    }
                    if (Updater.message.isNotBlank()) Text(Updater.message, fontSize = 12.sp, color = Muted)
                }
            }
        }
        order.forEach { id ->
            when (id) {
                "next" -> {
        item {
            var sheet by remember { mutableStateOf(false) }
            if (sheet) PrayerSheet(prefs, times, next?.first, day, now, Fasting.hijriText(prefs), { onNavigate(5) }) { sheet = false }
            Glass(Modifier.clickable { sheet = true }) {
                Row(Modifier.clickable { onNavigate(5) }, verticalAlignment = Alignment.CenterVertically) {
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
                            color = Gold, trackColor = Track
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(next.first.ar, fontSize = 12.sp, color = Muted)
                            Text("منذ ${prev.first.ar}", fontSize = 12.sp, color = Muted)
                        }
                    }
                }
                WeatherLine()
                Spacer(Modifier.height(12.dp))
                PrayerStrip(prefs, times, next?.first, day, now)
                Text("اضغط لعرض كل المواقيت", fontSize = 11.sp, color = Muted)
                if (!prefs.hasLocation) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "المواقيت حاليًا على موقع افتراضي. اسمح بالوصول للموقع من الإعدادات لتحديد مواقيتك بدقة.",
                        fontSize = 12.sp, color = Color(0xFFFFB4A8)
                    )
                }
            }
        }
                }
                "summary" -> {
        item {
            val fard = done.count { it.endsWith("_FARD") }
            val qada = prefs.qadaCount(day)
            Glass(Modifier.clickable { onNavigate(3) }) {
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
                    color = Gold, trackColor = Track
                )
            }
        }
                }
                "shortcuts" -> {
        item {
            Glass {
                SectionTitle("اختصارات", Icons.Rounded.AutoAwesome)
                androidx.compose.foundation.layout.FlowRow(
                    Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = if (bigFont()) 2 else 4
                ) {
                    listOf("المصحف" to 1, "الأذكار" to 2, "السبحة" to 12, "القبلة" to 22).forEach { (label, code) ->
                        androidx.compose.material3.OutlinedButton(
                            onClick = {
                                azkarStartMode.intValue = code / 10 // 12 → السبحة، 22 → القبلة
                                onNavigate(code % 10)
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) { Text(label, fontSize = 13.sp, color = Gold, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                    }
                }
            }
        }
                }
                "ayah" -> {
        item {
            val ctxH = androidx.compose.ui.platform.LocalContext.current
            val ay by androidx.compose.runtime.produceState<Ayah?>(null, day) {
                value = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    if (!Quran.loaded) Quran.load(ctxH)
                    if (!Quran.loaded) null else {
                        val cal = java.util.Calendar.getInstance()
                        var i = Math.floorMod(cal.get(java.util.Calendar.YEAR) * 366 + cal.get(java.util.Calendar.DAY_OF_YEAR) * 53, Quran.ayahs.size)
                        var guard = 0
                        while (Quran.ayahs[i].text.length < 25 && guard++ < 50) i = (i + 1) % Quran.ayahs.size
                        Quran.ayahs[i]
                    }
                }
            }
            Glass(Modifier.clickable { ay?.let { homeQuranJump.intValue = it.page }; onNavigate(1) }) {
                SectionTitle("آية اليوم", Icons.Rounded.AutoAwesome)
                val a = ay
                if (a == null) {
                    Text("حمّل المصحف من تبويب المصحف لتظهر آية اليوم.", color = Muted, fontSize = 13.sp)
                } else {
                    Text(a.text, fontSize = 20.sp, lineHeight = 34.sp, fontFamily = Serif)
                    Text("${Quran.surahName(a.surah)} — آية ${Quran.arabicDigits(a.num)} — صفحة ${Quran.arabicDigits(a.page)}", fontSize = 12.sp, color = Muted)
                    val slug = remember(tick) { prefs.tafsirSlug }
                    val tf by androidx.compose.runtime.produceState<String?>(null, a.surah, a.num, slug) {
                        value = withContext(kotlinx.coroutines.Dispatchers.IO) { TafsirStore.load(ctxH, slug, a.surah)?.get(a.num) }
                    }
                    tf?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(6.dp))
                        Text("التفسير (${TafsirStore.editions.firstOrNull { e -> e.slug == slug }?.name ?: ""}):", fontSize = 12.sp, color = Gold)
                        Text(it, fontSize = 14.sp, lineHeight = 24.sp, maxLines = 5, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    Row {
                        TextButton(onClick = { shareText(ctxH, "${a.text}\n— ${Quran.surahName(a.surah)}: ${a.num}") }) { Text("مشاركة", color = Gold) }
                        TextButton(onClick = { ShareCard.share(ctxH, a.text, "سورة ${Quran.surahName(a.surah)} — آية ${a.num}") }) { Text("كصورة", color = Gold) }
                        TextButton(onClick = { homeQuranJump.intValue = a.page; onNavigate(1) }) { Text("فتح الصفحة", color = Gold) }
                    }
                }
            }
        }
                }
                "weather" -> {
        item {
            Glass { WeatherCardContent(prefs.city) }
        }
                }
                "hadith" -> {
        item {
            val ctxD = androidx.compose.ui.platform.LocalContext.current
            val hd by androidx.compose.runtime.produceState<Pair<HBook, HRow>?>(null, day, tick) {
                value = withContext(kotlinx.coroutines.Dispatchers.IO) { HadithLib.daily(ctxD) }
            }
            Glass(Modifier.clickable { moreStartSec.intValue = 5; onNavigate(4) }) {
                SectionTitle("حديث اليوم", Icons.Rounded.AutoAwesome)
                val h = hd
                if (h == null) {
                    Text("حمّل كتابًا من المزيد ← الأحاديث (الأربعون النووية مثلًا) ليظهر حديث اليوم.", color = Muted, fontSize = 13.sp)
                } else {
                    Text(h.second.text, fontSize = 18.sp, lineHeight = 31.sp, fontFamily = Serif, maxLines = 8, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text("${h.first.name} — حديث رقم ${h.second.num}", fontSize = 12.sp, color = Muted)
                    h.second.grades.firstOrNull()?.let { Text(HadithLib.gradeText(it), fontSize = 12.sp, color = Gold) }
                    Row {
                        TextButton(onClick = { shareText(ctxD, HadithLib.shareString(h.first, h.second)) }) { Text("مشاركة", color = Gold) }
                        if (h.second.text.length < 1500) TextButton(onClick = { ShareCard.share(ctxD, h.second.text, "${h.first.name} — حديث رقم ${h.second.num}") }) { Text("كصورة", color = Gold) }
                    }
                }
            }
        }
                }
                "fasting" -> {
        if (todayFast != null || tomorrowFast != null) {
            item {
                Glass(Modifier.clickable { moreStartSec.intValue = 0; onNavigate(4) }) {
                    SectionTitle("صيام السنن", Icons.Rounded.Spa)
                    todayFast?.let { Text("اليوم: ${it.first}", fontWeight = FontWeight.Bold) }
                    tomorrowFast?.let {
                        Text("غدًا: ${it.first}", fontWeight = FontWeight.Bold)
                        Text(it.second, fontSize = 12.sp, color = Muted)
                    }
                }
            }
        }
                }
                "tip" -> {
        item {
            val pr = next?.first ?: Prayer.FAJR
            val tip = remember(pr, day) { Content.tip(pr) }
            Glass(Modifier.clickable { azkarStartMode.intValue = 0; onNavigate(2) }) {
                SectionTitle("${tip.kind} • ${pr.ar}", Icons.Rounded.AutoAwesome)
                Text(tip.text, fontSize = 18.sp, lineHeight = 30.sp, fontFamily = Serif)
                Spacer(Modifier.height(4.dp))
                Text(tip.source, fontSize = 12.sp, color = Muted)
            }
        }
                }
                }
            }
        }
    }
}

// ───────────────────────── التحديات ─────────────────────────

@Composable
fun ChallengesScreen(prefs: Prefs) {
    var mode by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GoldChip("متابعة الصلوات", mode == 0) { mode = 0 }
            GoldChip("التحديات والقضاء", mode == 1) { mode = 1 }
        }
        if (mode == 0) PrayerTrackPane(prefs) else ChallengesPane(prefs)
    }
}

@Composable
private fun ChallengesPane(prefs: Prefs) {
    val day = prefs.dayKey()
    val streak = prefs.streak()
    var qTick by remember { mutableIntStateOf(0) }
    val pendingQada = remember(qTick, refreshTick.intValue) {
        prefs.lastDays(30).flatMap { d -> Prayer.values().filter { prefs.prayerStatus(d, it) == 3 }.map { d to it } }
    }

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
        item {
            Glass {
                SectionTitle("سجل القضاء (آخر ٣٠ يومًا)", Icons.Rounded.AccessTime)
                if (pendingQada.isEmpty()) {
                    Text("لا صلوات مسجّلة للقضاء.", color = Muted, fontSize = 13.sp)
                } else {
                    Text("عليك قضاء ${pendingQada.size} صلاة. اضغط «تم القضاء» بعد أدائها.", color = Muted, fontSize = 13.sp)
                    pendingQada.forEach { (d, p) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("${p.ar} — $d", fontSize = 15.sp)
                            TextButton(onClick = { prefs.setPrayerStatus(d, p, 1); qTick++ }) { Text("تم القضاء", color = Gold) }
                        }
                    }
                }
            }
        }
        item { CustomChallengesCard(prefs) }
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
                    color = Gold, trackColor = Track
                )
            }
        }
    }
}

// ───────────────────────── الأذكار والتسبيح ─────────────────────────

@Composable
fun AzkarTasbihScreen(prefs: Prefs) {
    var mode by remember { mutableIntStateOf(azkarStartMode.intValue).also { azkarStartMode.intValue = 0 } }
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
                    val ctxA = androidx.compose.ui.platform.LocalContext.current
                    val tap = {
                        if (c < z.count) {
                            counters[idx] = c + 1
                            if (prefs.vibrateOn) vibrate(ctxA, if (c + 1 >= z.count) 150 else 20)
                            // إتمام كل أذكار قسم (الصباح/المساء) في اليوم
                            if (c + 1 >= z.count) {
                                listOf("الصباح", "المساء").filter { it in z.cats }.forEach { k ->
                                    val ids = Content.azkar.withIndex().filter { k in it.value.cats }.map { it.index }
                                    val all = ids.all { i -> (if (i == idx) c + 1 else counters[i] ?: 0) >= Content.azkar[i].count }
                                    if (all) prefs.setAdhkarDone(day, k, true)
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        // زرار العدّ: دايرة كبيرة بالرقم الحالي، اضغط عليها لكل مرة
                        Box(
                            Modifier.size(64.dp).clip(CircleShape)
                                .background(if (c >= z.count) Color(0x66E8C766) else Gold)
                                .clickable(enabled = c < z.count) { tap() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (c >= z.count) "✅" else Quran.arabicDigits(c),
                                color = Color.Black, fontSize = 24.sp, fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("العدد المطلوب: ${Quran.arabicDigits(z.count)} ${if (z.count == 1) "مرة" else "مرات"}", fontWeight = FontWeight.Bold)
                            Text(if (c >= z.count) "تم ✅" else "اضغط الدائرة عند كل مرة (المتبقي ${Quran.arabicDigits(z.count - c)})", fontSize = 12.sp, color = Muted)
                        }
                        Spacer(Modifier.width(8.dp))
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

