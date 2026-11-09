package com.hisn.muslim

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed class Block {
    class Head(val surah: Int) : Block()
    class Run(val text: AnnotatedString) : Block()
}

private fun buildPageBlocks(page: Int, accent: Color, hi: Pair<Int, Int>?, hiBg: Color): List<Block> {
    val list = Quran.byPage[page] ?: return emptyList()
    val out = mutableListOf<Block>()
    var sb = AnnotatedString.Builder()
    var has = false
    for (a in list) {
        if (a.num == 1) {
            if (has) {
                out += Block.Run(sb.toAnnotatedString())
                sb = AnnotatedString.Builder()
                has = false
            }
            out += Block.Head(a.surah)
        }
        if (hi != null && hi.first == a.surah && hi.second == a.num) sb.pushStyle(SpanStyle(background = hiBg))
        else sb.pushStyle(SpanStyle())
        sb.append(a.text + " ")
        sb.pop()
        sb.pushStyle(SpanStyle(color = accent))
        sb.append("﴿${Quran.arabicDigits(a.num)}﴾ ")
        sb.pop()
        has = true
    }
    if (has) out += Block.Run(sb.toAnnotatedString())
    return out
}

@Composable
fun QuranScreen(prefs: Prefs, jumpTo: Int, onJumpConsumed: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var ready by remember { mutableStateOf(Quran.loaded) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var section by remember { mutableIntStateOf(0) } // 0 قراءة، 1 السور، 2 الختمة
    var page by remember { mutableIntStateOf(prefs.quranPage.coerceIn(1, Quran.TOTAL_PAGES)) }

    LaunchedEffect(Unit) {
        if (!ready && Quran.isDownloaded(ctx)) {
            ready = withContext(Dispatchers.IO) { Quran.load(ctx) }
        }
    }
    LaunchedEffect(jumpTo) {
        if (jumpTo > 0) {
            page = jumpTo.coerceIn(1, Quran.TOTAL_PAGES)
            prefs.quranPage = page
            section = 0
            onJumpConsumed()
        }
    }

    if (!ready) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
            Glass {
                SectionTitle("المصحف الشريف")
                Text(
                    "لعرض المصحف داخل التطبيق لازم تحمّل نصه مرة واحدة (حوالي ٢–٣ ميجا)، وبعدها يشتغل بدون إنترنت.",
                    fontSize = 15.sp, lineHeight = 24.sp
                )
                Text("مصدر النص: Al-Quran Cloud (نص Tanzil، رواية حفص عن عاصم).", fontSize = 11.sp, color = Muted)
                Spacer(Modifier.height(14.dp))
                if (downloading) {
                    LinearProgressIndicator(
                        progress = { progress / 100f }, modifier = Modifier.fillMaxWidth(), color = Gold
                    )
                    Text("جاري التحميل… $progress%", color = Gold, fontSize = 13.sp)
                } else {
                    Button(
                        onClick = {
                            error = null
                            downloading = true
                            progress = 0
                            scope.launch {
                                val res = withContext(Dispatchers.IO) {
                                    Quran.download(ctx) { progress = it }
                                }
                                downloading = false
                                if (res == null) ready = true else error = res
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black)
                    ) { Text("⬇ تحميل المصحف") }
                }
                error?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 12.sp) }
            }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GoldChip("القراءة", section == 0) { section = 0 }
            GoldChip("التفسير", section == 6) { section = 6 }
            GoldChip("السور والأجزاء", section == 1) { section = 1 }
            GoldChip("ختمة / ورد", section == 2) { section = 2 }
            GoldChip("بحث", section == 3) { section = 3 }
            GoldChip("العلامات", section == 4) { section = 4 }
            GoldChip("الحفظ والمراجعة", section == 5) { section = 5 }
        }
        when (section) {
            0 -> ReaderPane(prefs, page) { page = it; prefs.quranPage = it }
            1 -> SurahList { p -> page = p; prefs.quranPage = p; section = 0 }
            2 -> KhatmaPane(prefs) { p -> page = p; prefs.quranPage = p; section = 0 }
            3 -> SearchPane { p -> page = p; prefs.quranPage = p; section = 0 }
            5 -> MemorizePane(prefs)
            6 -> TafsirPane(prefs, page)
            else -> BookmarksPane(prefs) { p -> page = p; prefs.quranPage = p; section = 0 }
        }
    }
}

@Composable
private fun ReaderPane(prefs: Prefs, page: Int, onPage: (Int) -> Unit) {
    var font by remember { mutableIntStateOf(prefs.quranFont) }
    var readTick by remember { mutableIntStateOf(0) }
    val ctx = LocalContext.current
    var theme by remember { mutableIntStateOf(prefs.quranTheme) }
    var bmTick by remember { mutableIntStateOf(0) }
    var mode by remember { mutableIntStateOf(prefs.mushafMode) }
    var mscale by remember { mutableStateOf(prefs.mushafScale) }
    var repeat by remember { mutableIntStateOf(prefs.repeatMode) }
    var reciter by remember { mutableStateOf(prefs.reciter) }
    var showReciters by remember { mutableStateOf(false) }
    val pageBg = when (theme) { 0 -> Color(0xFFF5ECD6); 1 -> Color(0xFFFFFFFF); else -> Color(0xCC0B1A14) }
    val ink = if (theme == 2) Color.White else Color(0xFF2B2118)
    val accent = if (theme == 2) Gold else Color(0xFF8A6A1F)
    val hiBg = if (theme == 2) Color(0x55E8C766) else Color(0x55C9A227)
    val cur = QuranAudio.current
    val blocks = remember(page, theme, cur) { buildPageBlocks(page, accent, cur, hiBg) }
    val bookmarked = remember(page, bmTick) { page in prefs.bookmarks() }
    val pageAyahs = Quran.byPage[page].orEmpty()
    androidx.compose.runtime.DisposableEffect(page) { onDispose { QuranAudio.stop() } }
    val first = Quran.byPage[page]?.firstOrNull()
    val isRead = remember(page, readTick) { page in prefs.khatmaPagesRead() || page in prefs.pagesReadOn(prefs.dayKey()) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("صفحة ${Quran.arabicDigits(page)} من ${Quran.arabicDigits(Quran.TOTAL_PAGES)}", color = Gold, fontWeight = FontWeight.Bold)
                if (first != null) Text("الجزء ${Quran.arabicDigits(first.juz)} • ${Quran.surahName(first.surah)}", color = Muted, fontSize = 12.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { prefs.toggleBookmark(page); bmTick++ }) {
                    Text(if (bookmarked) "★" else "☆", color = Gold, fontSize = 22.sp)
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            GoldChip(if (mode == 1) "عرض: المصحف" else "عرض: النص", false) { mode = 1 - mode; prefs.mushafMode = mode }
            GoldChip(when (theme) { 0 -> "ورق"; 1 -> "أبيض"; else -> "داكن" }, false) { theme = (theme + 1) % 3; prefs.quranTheme = theme }
            GoldChip("تصغير الخط  أ-", false) {
                if (mode == 1) { mscale = (mscale - 0.25f).coerceAtLeast(0.75f); prefs.mushafScale = mscale }
                else { font = (font - 2).coerceAtLeast(18); prefs.quranFont = font }
            }
            GoldChip("تكبير الخط  أ+", false) {
                if (mode == 1) { mscale = (mscale + 0.25f).coerceAtMost(2.5f); prefs.mushafScale = mscale }
                else { font = (font + 2).coerceAtMost(44); prefs.quranFont = font }
            }
        }
        Spacer(Modifier.height(6.dp))

        if (mode == 1) {
            key(page) {
                var drag by remember { mutableStateOf(0f) }
                Box(
                    Modifier.weight(1f).fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(pageBg)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .pointerInput(page) {
                            detectHorizontalDragGestures(
                                onDragStart = { drag = 0f },
                                onDragEnd = {
                                    // السحب من اليسار لليمين = الصفحة التالية (اتجاه المصحف)
                                    if (drag > 120f && page < Quran.TOTAL_PAGES) onPage(page + 1)
                                    else if (drag < -120f && page > 1) onPage(page - 1)
                                },
                                onHorizontalDrag = { _, d -> drag += d }
                            )
                        }
                ) {
                    MushafPageView(page, ink, accent, hiBg, cur, Modifier.fillMaxSize(), mscale)
                }
            }
        } else {
        key(page) {
                val scroll = rememberScrollState()
                Column(
                    Modifier.weight(1f).fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(pageBg)
                        .verticalScroll(scroll)
                        .padding(18.dp)
                ) {
                    blocks.forEach { b ->
                        when (b) {
                            is Block.Head -> {
                                Column(
                                    Modifier.fillMaxWidth().padding(vertical = 10.dp)
                                        .background(accent.copy(alpha = 0.18f), RoundedCornerShape(14.dp)).padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(Quran.surahName(b.surah), color = accent, fontSize = (font - 2).sp, fontWeight = FontWeight.Bold, fontFamily = Serif)
                                    if (b.surah != 1 && b.surah != 9) {
                                        Text("بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ", color = ink, fontSize = (font - 2).sp, fontFamily = Serif)
                                    }
                                }
                            }
                            is Block.Run -> Text(
                                b.text, color = ink, fontSize = font.sp,
                                lineHeight = (font * 2.0f).sp, fontFamily = Serif, textAlign = TextAlign.Justify
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        // التلاوة
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = {
                    if (QuranAudio.loading) QuranAudio.stop()
                    else if (QuranAudio.current != null) QuranAudio.pauseResume()
                    else if (pageAyahs.isEmpty()) android.widget.Toast.makeText(ctx, "لا توجد آيات محمّلة لهذه الصفحة؛ حمّل نص المصحف أولًا", android.widget.Toast.LENGTH_LONG).show()
                    else QuranAudio.play(ctx, reciter, pageAyahs, 0, { prefs.repeatMode }) { if (page < Quran.TOTAL_PAGES) onPage(page + 1) }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black)
            ) { Text(if (QuranAudio.loading) "…" else if (QuranAudio.playing) "⏸ إيقاف مؤقت" else "▶ استمع") }
            OutlinedButton(onClick = { QuranAudio.stop() }) { Text("⏹", color = Gold) }
            OutlinedButton(onClick = { repeat = (repeat + 1) % 3; prefs.repeatMode = repeat }) {
                Text(when (repeat) { 1 -> "🔂 آية"; 2 -> "🔁 صفحة"; else -> "تكرار: لا" }, color = Gold, fontSize = 12.sp)
            }
            OutlinedButton(onClick = { showReciters = !showReciters }) {
                Text(QuranAudio.reciters.firstOrNull { it.id == reciter }?.name ?: "القارئ", color = Gold, fontSize = 12.sp, maxLines = 1)
            }
        }
        if (showReciters) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuranAudio.reciters.forEach { r ->
                    GoldChip(r.name, reciter == r.id) { reciter = r.id; prefs.reciter = r.id; QuranAudio.stop() }
                }
            }
        }
        if (QuranAudio.current != null || QuranAudio.sleepAt > 0L) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(0.75f, 1f, 1.25f, 1.5f).forEach { v -> GoldChip("${v}x", QuranAudio.speed == v) { QuranAudio.changeSpeed(v) } }
                Text("نوم:", color = Muted, fontSize = 12.sp)
                listOf(0 to "لا", 15 to "15 د", 30 to "30 د", 60 to "60 د").forEach { (m, l) ->
                    GoldChip(l, (m == 0 && QuranAudio.sleepAt == 0L)) { QuranAudio.setSleep(m) }
                }
                if (QuranAudio.sleepAt > 0L) Text("مفعّل", color = Gold, fontSize = 12.sp)
            }
        }
        QuranAudio.error?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 12.sp) }
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = { if (page > 1) onPage(page - 1) }) { Text("السابقة", color = Gold) }
            Button(
                onClick = {
                    prefs.recordPageRead(page)
                    readTick++
                    if (page < Quran.TOTAL_PAGES) onPage(page + 1)
                },
                enabled = !isRead,
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black)
            ) { Text(if (isRead) "✓ مقروءة" else "أتممت الصفحة ✓") }
            OutlinedButton(onClick = { if (page < Quran.TOTAL_PAGES) onPage(page + 1) }) { Text("التالية", color = Gold) }
        }
    }
}

@Composable
private fun SurahList(onOpen: (Int) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("الأجزاء", color = Gold, fontWeight = FontWeight.Bold)
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (j in 1..30) GoldChip("${Quran.arabicDigits(j)}", false) { Quran.juzFirstPage[j]?.let(onOpen) }
            }
            Spacer(Modifier.height(6.dp))
            Text("السور", color = Gold, fontWeight = FontWeight.Bold)
        }
        items(Quran.surahs) { s ->
            Glass(Modifier.clickable { onOpen(s.firstPage) }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).background(Color(0x33E8C766)),
                        contentAlignment = Alignment.Center
                    ) { Text(Quran.arabicDigits(s.number), color = Gold, fontSize = 14.sp) }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(s.name, fontSize = 19.sp, fontFamily = Serif, fontWeight = FontWeight.Bold)
                        Text(
                            "${if (s.revelation.equals("Meccan", true)) "مكية" else "مدنية"} • ${Quran.arabicDigits(s.ayahCount)} آية",
                            color = Muted, fontSize = 12.sp
                        )
                    }
                    Text("ص ${Quran.arabicDigits(s.firstPage)}", color = Muted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun KhatmaPane(prefs: Prefs, onOpenPage: (Int) -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val st = remember(tick) { Khatma.stats(prefs) }
    var days by remember { mutableIntStateOf(prefs.khatmaDays) }
    var remind by remember { mutableStateOf(prefs.wirdReminder) }
    var wirdTime by remember { mutableIntStateOf(prefs.wirdTime) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (st == null) {
            item {
                Glass {
                    SectionTitle("ابدأ ختمة جديدة")
                    Text("اختار المدة اللي عايز تختم فيها القرآن، والتطبيق يحسب وردك اليومي.", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(7, 10, 15, 20, 30, 60).forEach { d -> GoldChip("$d يوم", days == d) { days = d } }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { days = (days - 1).coerceAtLeast(1) }) { Text("−", color = Gold) }
                        Text("  $days يوم  ", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        OutlinedButton(onClick = { days = (days + 1).coerceAtMost(365) }) { Text("+", color = Gold) }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "وردك اليومي ≈ ${Khatma.pagesPerDay(days)} صفحة (${"%.1f".format(java.util.Locale.US, Khatma.pagesPerDay(days) / 20f)} جزء تقريبًا)",
                        color = Gold, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            prefs.startKhatma(days)
                            Reminders.scheduleWird(ctx)
                            tick++
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black)
                    ) { Text("ابدأ الختمة") }
                }
            }
        } else {
            item {
                Glass {
                    SectionTitle("ختمة القرآن")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${st.read} / ${Khatma.TOTAL} صفحة", fontWeight = FontWeight.Bold)
                        Text("${(st.read * 100 / Khatma.TOTAL)}%", color = Gold, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { st.read / Khatma.TOTAL.toFloat() },
                        modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
                        color = Gold, trackColor = Color(0x33FFFFFF)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("المدة: ${st.daysTotal} يوم • المتبقي: ${st.daysLeft} يوم", color = Muted, fontSize = 13.sp)
                    Text("موعد الانتهاء المتوقع: ${st.finishDate}", color = Muted, fontSize = 13.sp)
                }
            }
            item {
                val hist = remember(tick, st.read) { prefs.lastDays(14) }
                val target = Khatma.pagesPerDay(st.daysTotal)
                var goal by remember(tick) { mutableIntStateOf(prefs.khatmaGoalOverride) }
                Glass {
                    SectionTitle("سجل القراءة (آخر ١٤ يومًا)")
                    var missed = 0
                    hist.reversed().forEach { d ->
                        val n = prefs.khatmaReadOn(d)
                        val started = d >= prefs.khatmaStart
                        if (started && d != prefs.dayKey() && n < target) missed++
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(d, fontSize = 13.sp, color = Muted)
                            Text(
                                if (!started) "—" else "$n صفحة" + if (n >= target) " ✅" else "",
                                fontSize = 13.sp, color = if (started && n >= target) Gold else Color.White
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("أيام لم يُبلغ فيها الورد الأصلي ($target صفحة): $missed", fontSize = 12.sp, color = Muted)
                    Spacer(Modifier.height(8.dp))
                    Text("تعديل الورد اليومي يدويًا:", fontSize = 12.sp, color = Muted)
                    ChipRow(
                        listOf("تلقائي" to 0, "٥" to 5, "١٠" to 10, "٢٠" to 20, "٤٠" to 40), goal
                    ) { goal = it; prefs.khatmaGoalOverride = it; tick++ }
                }
            }
            item {
                Glass {
                    SectionTitle("ورد اليوم")
                    Text(
                        "المطلوب اليوم: ${st.goalToday} صفحة • قرأت: ${st.readToday}",
                        fontSize = 17.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { if (st.goalToday == 0) 1f else (st.readToday / st.goalToday.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                        color = Gold, trackColor = Color(0x33FFFFFF)
                    )
                    Spacer(Modifier.height(12.dp))
                    if (st.remaining == 0) {
                        Text("🎉 ما شاء الله، أتممت الختمة. تقبّل الله منك.", color = Gold, fontWeight = FontWeight.Bold)
                    } else {
                        Button(
                            onClick = { onOpenPage(st.nextPage) },
                            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black)
                        ) { Text("كمّل القراءة من صفحة ${st.nextPage}") }
                    }
                }
            }
            item {
                Glass {
                    SectionTitle("تذكير الورد")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("ذكّرني بالورد يوميًا")
                        Switch(
                            checked = remind,
                            onCheckedChange = { remind = it; prefs.wirdReminder = it; Reminders.scheduleWird(ctx) },
                            colors = SwitchDefaults.colors(checkedTrackColor = Gold, checkedThumbColor = Color.Black)
                        )
                    }
                    if (remind) {
                        TextButton(onClick = {
                            pickTime(ctx, wirdTime) { m -> wirdTime = m; prefs.wirdTime = m; Reminders.scheduleWird(ctx) }
                        }) { Text("الوقت: ${fmtMinutes(wirdTime)}  (اضغط للتغيير)", color = Gold) }
                    }
                }
            }
            item {
                TextButton(onClick = { prefs.stopKhatma(); Reminders.scheduleWird(ctx); tick++ }) {
                    Text("إنهاء الختمة الحالية", color = Color(0xFFFF8A80))
                }
            }
        }
    }
}

@Composable
private fun SearchPane(onOpen: (Int) -> Unit) {
    var q by remember { mutableStateOf("") }
    val results = remember(q) { Quran.search(q) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        androidx.compose.material3.OutlinedTextField(
            value = q, onValueChange = { q = it }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), label = { Text("ابحث في القرآن (بدون تشكيل)") }
        )
        Spacer(Modifier.height(8.dp))
        if (q.trim().length >= 2 && results.isEmpty()) Text("لا نتائج", color = Muted)
        if (results.size >= 100) Text("أول ١٠٠ نتيجة فقط — دقّق البحث", color = Muted, fontSize = 12.sp)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results) { a ->
                Glass(Modifier.clickable { onOpen(a.page) }) {
                    Text(a.text, fontFamily = Serif, fontSize = 18.sp, lineHeight = 30.sp)
                    Text("${Quran.surahName(a.surah)} • آية ${Quran.arabicDigits(a.num)} • ص ${Quran.arabicDigits(a.page)}", color = Gold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun BookmarksPane(prefs: Prefs, onOpen: (Int) -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    val list = remember(tick) { prefs.bookmarks().sorted() }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Glass(Modifier.clickable { onOpen(prefs.quranPage) }) {
                Text("آخر موضع قراءة: صفحة ${Quran.arabicDigits(prefs.quranPage)}", color = Gold, fontWeight = FontWeight.Bold)
                Text("اضغط للمتابعة", color = Muted, fontSize = 12.sp)
            }
        }
        if (list.isEmpty()) item { Text("لا علامات بعد. اضغط ☆ أثناء القراءة لحفظ الصفحة.", color = Muted) }
        items(list) { pg ->
            val f = Quran.byPage[pg]?.firstOrNull()
            Glass(Modifier.clickable { onOpen(pg) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("صفحة ${Quran.arabicDigits(pg)}", fontWeight = FontWeight.Bold)
                        if (f != null) Text("${Quran.surahName(f.surah)} • الجزء ${Quran.arabicDigits(f.juz)}", color = Muted, fontSize = 12.sp)
                    }
                    TextButton(onClick = { prefs.toggleBookmark(pg); tick++ }) { Text("حذف", color = Color(0xFFFF8A80)) }
                }
            }
        }
    }
}
