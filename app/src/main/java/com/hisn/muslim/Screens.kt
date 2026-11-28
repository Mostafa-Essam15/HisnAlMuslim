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
                    val frac = if (prev != null) {
                        val span = (next.second.time - prev.second.time).coerceAtLeast(1)
                        ((now - prev.second.time).toFloat() / span).coerceIn(0f, 1f)
                    } else 0f
                    NextPrayerRing(
                        next.first.label, diff, frac, fmt.format(next.second),
                        prev?.let { "من ${it.first.label} إلى ${next.first.label}" }
                    )
                }
                WeatherLine()
                Spacer(Modifier.height(16.dp))
                androidx.compose.material3.HorizontalDivider(color = Gold.copy(alpha = 0.18f))
                Spacer(Modifier.height(10.dp))
                PrayerStrip(prefs, times, next?.first, day, now)
                Spacer(Modifier.height(6.dp))
                Text("اضغط لعرض كل المواقيت", fontSize = 11.sp, color = Muted, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
                "wird" -> {
        item {
            val st = remember(tick, day) { Khatma.stats(prefs) }
            val rs = remember(tick, day) { prefs.readStreak() }
            val todayPages = remember(tick, day) { prefs.pagesReadOn(day).size }
            val resume = st?.nextPage ?: prefs.quranPage
            Glass(Modifier.clickable { homeQuranJump.intValue = resume; onNavigate(1) }) {
                SectionTitle("وردك اليوم", Icons.Rounded.AutoAwesome)
                if (st == null) {
                    Text("اقرأ كل يوم ولو صفحة. ابدأ ختمة بمدة تناسبك من المصحف ← ختمة / ورد، والتطبيق يحسب وردك اليومي.", fontSize = 13.sp, color = Muted)
                } else {
                    val goal = st.goalToday.coerceAtLeast(1)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("قرأت اليوم ${Quran.arabicDigits(st.readToday)} من ${Quran.arabicDigits(st.goalToday)} صفحة", fontWeight = FontWeight.Bold)
                        Text("الختمة ${Quran.arabicDigits(st.read * 100 / Khatma.TOTAL)}٪", color = Gold, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (st.readToday / goal.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = Gold, trackColor = Track
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { (st.read / Khatma.TOTAL.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape), color = Gold.copy(alpha = 0.55f), trackColor = Track
                    )
                    Text("باقي ${Quran.arabicDigits(st.daysLeft)} يوم على الموعد", fontSize = 12.sp, color = Muted)
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (rs > 0) "🔥 ${Quran.arabicDigits(rs)} يوم متتالي من القراءة" else if (todayPages == 0) "ابدأ سلسلة قراءتك اليوم" else " ",
                        fontSize = 13.sp, color = Gold
                    )
                    Text("تابع من صفحة ${Quran.arabicDigits(resume)} ◂", fontSize = 13.sp, color = Gold, fontWeight = FontWeight.Bold)
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
                SectionTitle("${tip.kind} • ${pr.label}", Icons.Rounded.AutoAwesome)
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

/** مبدّل أقسام زجاجي (بدل شرائح GoldChip) */
@Composable
fun SegTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).glassSurface(18.dp).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        labels.forEachIndexed { i, t ->
            val on = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (on) Gold else Color.Transparent)
                    .clickable { onSelect(i) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(t, fontSize = 13.sp, maxLines = 1, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) (if (Look.light) Color.White else Color.Black) else OnCard)
            }
        }
    }
}

/** مبدّل مدمج (أزرار متساوية العرض) لاختيار قيمة واحدة؛ selected = -1 لو مفيش اختيار */
@Composable
fun SegRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (Look.light) Color(0x14000000) else Color(0x33000000)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        labels.forEachIndexed { i, t ->
            val on = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(if (on) Gold else Color.Transparent)
                    .clickable { onSelect(i) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(t, fontSize = 13.sp, maxLines = 1, softWrap = false, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) (if (Look.light) Color.White else Color.Black) else OnCard)
            }
        }
    }
}

/** حلقة تقدّم دائرية بمحتوى في الوسط */
@Composable
fun RingProgress(frac: Float, size: androidx.compose.ui.unit.Dp, stroke: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    val track = Track
    val gold = Gold
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val st = androidx.compose.ui.graphics.drawscope.Stroke(stroke.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            val inset = stroke.toPx() / 2
            val sz = androidx.compose.ui.geometry.Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val tl = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(track, 0f, 360f, false, tl, sz, style = st)
            if (frac > 0f) drawArc(gold, -90f, 360f * frac, false, tl, sz, style = st)
        }
        content()
    }
}

// ───────────────────────── التحديات ─────────────────────────

@Composable
fun ChallengesScreen(prefs: Prefs) {
    var mode by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        SegTabs(listOf("متابعة الصلوات", "التحديات والقضاء"), mode) { mode = it }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RingProgress((streak / 7f).coerceIn(0f, 1f), 96.dp, 8.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$streak", fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = Serif, color = Gold)
                            Text("يوم", fontSize = 11.sp, color = Muted)
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("سلسلة المحافظة", fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = Serif, color = Gold)
                        Text(if (streak >= 7) "ما شاء الله، أكملت أسبوعًا فأكثر" else "${7 - streak} أيام ويكتمل الأسبوع", fontSize = 13.sp)
                        Text("تُحسب الأيام التي سجّلت فيها الفروض الخمسة كاملة.", fontSize = 12.sp, color = Muted)
                    }
                }
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
                            Text("${p.label} — $d", fontSize = 15.sp)
                            TextButton(onClick = { prefs.setPrayerStatus(d, p, 1); qTick++ }) { Text("تم القضاء", color = Gold) }
                        }
                    }
                }
            }
        }
        item { CustomChallengesCard(prefs) }
        items(list) { c ->
            val done = c.cur >= c.goal
            Row(
                Modifier.fillMaxWidth().glassSurface(22.dp).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RingProgress((c.cur.toFloat() / c.goal).coerceIn(0f, 1f), 54.dp, 5.dp) {
                    if (done) Icon(Icons.Rounded.EmojiEvents, null, tint = Gold, modifier = Modifier.size(22.dp))
                    else Text("${c.cur}/${c.goal}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Gold)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(c.desc, fontSize = 12.sp, color = Muted)
                }
                if (done) Text("تم", color = Gold, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ───────────────────────── الأذكار والتسبيح ─────────────────────────

@Composable
fun AzkarTasbihScreen(prefs: Prefs) {
    var mode by remember { mutableIntStateOf(azkarStartMode.intValue).also { azkarStartMode.intValue = 0 } }
    Column(Modifier.fillMaxSize()) {
        val segShape = RoundedCornerShape(22.dp)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(segShape)
                .background(if (Look.light) Color(0x14000000) else Color(0x40000000))
                .border(1.dp, Gold.copy(alpha = 0.25f), segShape).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("الأذكار", "السبحة", "القبلة").forEachIndexed { i, l ->
                val sel = mode == i
                Box(
                    Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(18.dp))
                        .background(if (sel) Gold else Color.Transparent).clickable { mode = i },
                    contentAlignment = Alignment.Center
                ) {
                    Text(l, fontSize = 14.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                        color = if (sel) (if (Look.light) Color.White else Color.Black) else OnCard)
                }
            }
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

    var showSearch by remember { mutableStateOf(false) }
    // تقدّم أذكار الصباح/المساء: كام ذكر خلص من الإجمالي
    val sectionIds = remember(cat) { if (cat == "الصباح" || cat == "المساء") Content.azkar.withIndex().filter { cat in it.value.cats }.map { it.index } else emptyList() }
    val sectionDone = sectionIds.count { (counters[it] ?: 0) >= Content.azkar[it].count }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape)
                    .background(if (showSearch || q.isNotEmpty()) Gold.copy(alpha = 0.22f) else Color.Transparent)
                    .border(1.dp, Gold.copy(alpha = 0.45f), CircleShape).clickable { showSearch = !showSearch },
                contentAlignment = Alignment.Center
            ) { Text("🔍", fontSize = 15.sp) }
            (Content.azkarCats + "المفضلة").forEach { c -> GoldChip(c, cat == c) { cat = c } }
        }
        if (showSearch || q.isNotEmpty()) {
            androidx.compose.material3.OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("ابحث في الأذكار…", color = Muted) },
                shape = RoundedCornerShape(18.dp)
            )
        }
        if (sectionIds.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("أذكار ${cat}", fontSize = 12.sp, color = Muted)
                    Text("${Quran.arabicDigits(sectionDone)} من ${Quran.arabicDigits(sectionIds.size)}", fontSize = 12.sp, color = Gold, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { sectionDone / sectionIds.size.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape), color = Gold, trackColor = Track
                )
            }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (shown.isEmpty()) item { Text(if (cat == "المفضلة") "لا أذكار مفضلة بعد (اضغط ☆)." else "لا نتائج", color = Muted) }
            items(shown) { (idx, z) ->
                val c = counters[idx] ?: 0
                val fav = idx.toString() in favs
                var more by remember(idx) { mutableStateOf(false) }
                Glass {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (z.count > 1) Box(
                            Modifier.clip(RoundedCornerShape(10.dp)).background(Gold.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 3.dp)
                        ) { Text("×${Quran.arabicDigits(z.count)}", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.weight(1f))
                        Text(if (fav) "★" else "☆", color = Gold, fontSize = 22.sp,
                            modifier = Modifier.clip(CircleShape).clickable { prefs.toggleFavorite(idx.toString()); favTick++ }.padding(4.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(z.text, fontSize = 20.sp, lineHeight = 36.sp, fontFamily = Serif, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    if (z.fadl.isNotBlank() || z.source.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (more) "إخفاء الفضل والمصدر ▴" else "الفضل والمصدر ▾", color = Gold, fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { more = !more }.padding(vertical = 6.dp),
                            textAlign = TextAlign.Center
                        )
                        if (more) {
                            if (z.fadl.isNotBlank()) Text("الفضل: ${z.fadl}", fontSize = 13.sp, lineHeight = 22.sp, color = Gold)
                            if (z.source.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(z.source, fontSize = 12.sp, lineHeight = 19.sp, color = Muted)
                            }
                        }
                    }
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
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        // حلقة العدّ: بتتملي مع كل مرة، وتتحول لـ✓ ذهبي لما الذكر يخلص
                        val done = c >= z.count
                        val gold = Gold
                        val trk = Track
                        Box(
                            Modifier.size(68.dp).clip(CircleShape).clickable(enabled = !done) { tap() },
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                                val sw = 6.dp.toPx()
                                val tl = androidx.compose.ui.geometry.Offset(sw / 2, sw / 2)
                                val sz = androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw)
                                if (done) drawCircle(gold) else {
                                    drawArc(trk, 0f, 360f, false, tl, sz, style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
                                    drawArc(gold, -90f, 360f * (c / z.count.toFloat()), false, tl, sz,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                                }
                            }
                            Text(if (done) "✓" else Quran.arabicDigits(c), color = if (done) Color.Black else OnCard, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(if (done) "تم، تقبّل الله" else "المتبقي ${Quran.arabicDigits(z.count - c)}", fontWeight = FontWeight.Bold, color = if (done) Gold else OnCard)
                            if (!done) Text("اضغط الدائرة عند كل مرة", fontSize = 11.sp, color = Muted)
                            if (c > 0) Text("إعادة", color = Gold, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { counters[idx] = 0 }.padding(vertical = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

