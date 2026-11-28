package com.hisn.muslim

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
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
        sb.pushStringAnnotation("ayah", "${a.surah}:${a.num}")
        if (hi != null && hi.first == a.surah && hi.second == a.num) sb.pushStyle(SpanStyle(background = hiBg))
        else sb.pushStyle(SpanStyle())
        sb.append(a.text + " ")
        sb.pop()
        sb.pushStyle(SpanStyle(color = accent))
        sb.append("﴿${Quran.arabicDigits(a.num)}﴾ ")
        sb.pop()
        sb.pop()
        has = true
    }
    if (has) out += Block.Run(sb.toAnnotatedString())
    return out
}

/** وضع «الشاشة كاملة» للقراءة: يخفي الشريط السفلي وشريط النظام وأدوات القراءة */
var readerFull by androidx.compose.runtime.mutableStateOf(false)

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
        if (!(readerFull && section == 0)) {
            val moreSec = section == 3 || section == 4 || section == 5
            var moreMenu by remember { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("القراءة" to 0, "التفسير" to 6, "السور" to 1, "الختمة" to 2).forEach { (l, sec) ->
                    Box(Modifier.weight(1f)) { TabPill(l, section == sec) { section = sec } }
                }
                Box(Modifier.weight(1f)) {
                    TabPill(if (moreSec) when (section) { 3 -> "بحث"; 4 -> "العلامات"; else -> "الحفظ" } else "المزيد ▾", moreSec) { moreMenu = true }
                    androidx.compose.material3.DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                        listOf("بحث في المصحف" to 3, "العلامات المرجعية" to 4, "الحفظ والمراجعة" to 5).forEach { (l, sec) ->
                            androidx.compose.material3.DropdownMenuItem(text = { Text(l) }, onClick = { moreMenu = false; section = sec })
                        }
                    }
                }
            }
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
private fun TabPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier.fillMaxWidth().height(34.dp).clip(shape)
            .background(if (selected) Gold else if (Look.light) Color(0x14000000) else Color(0x33000000))
            .then(if (selected) Modifier else Modifier.border(1.dp, Gold.copy(alpha = 0.25f), shape))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 12.sp, maxLines = 1, softWrap = false, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) (if (Look.light) Color.White else Color.Black) else OnCard)
    }
}

/** زرار دائري صغير لشريط التشغيل */
@Composable
private fun RoundBtn(label: String, active: Boolean = false, size: Int = 40, onClick: () -> Unit) {
    Box(
        Modifier.size(size.dp).clip(CircleShape)
            .background(if (active) Gold.copy(alpha = 0.22f) else Color.Transparent)
            .border(1.dp, Gold.copy(alpha = if (active) 0.9f else 0.4f), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Text(label, color = if (active) Gold else OnCard, fontSize = 16.sp) }
}

private var followNav = false
private var skipStop = false

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ReaderPane(prefs: Prefs, page: Int, onPage: (Int) -> Unit) {
    var fontF by remember { mutableStateOf(prefs.quranFont.toFloat()) }
    val font = fontF.roundToInt()
    var readTick by remember { mutableIntStateOf(0) }
    val ctx = LocalContext.current
    var theme by remember { mutableIntStateOf(prefs.quranTheme) }
    var bmTick by remember { mutableIntStateOf(0) }
    var mode by remember { mutableIntStateOf(prefs.mushafMode) }
    var ayahSheet by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var mscale by remember { mutableStateOf(prefs.mushafScale) }
    var repeat by remember { mutableIntStateOf(prefs.repeatMode) }
    var reciter by remember { mutableStateOf(prefs.reciter) }
    var showReciters by remember { mutableStateOf(false) }
    var showAudioSheet by remember { mutableStateOf(false) }
    var optMenu by remember { mutableStateOf(false) }
    var showRange by remember { mutableStateOf(false) }
    var rFrom by remember(page) { mutableIntStateOf(0) }
    var rTo by remember(page) { mutableIntStateOf(0) }
    var rTimes by remember { mutableIntStateOf(3) }
    val pageBg = when (theme) { 0 -> Color(0xFFF5ECD6); 1 -> Color(0xFFFFFFFF); else -> Color(0xCC0B1A14) }
    val ink = if (theme == 2) Color.White else Color(0xFF2B2118)
    val accent = if (theme == 2) Gold else Color(0xFF8A6A1F)
    val hiBg = if (theme == 2) Color(0x55E8C766) else Color(0x55C9A227)
    var showDl by remember { mutableStateOf(false) }
    if (showDl) AudioDownloadDialog(reciter, page) { showDl = false }
    val cur = QuranAudio.current
    val blocks = remember(page, theme, cur) { buildPageBlocks(page, accent, cur, hiBg) }
    val bookmarked = remember(page, bmTick) { page in prefs.bookmarks() }
    val pageAyahs = Quran.byPage[page].orEmpty()
    androidx.compose.runtime.DisposableEffect(page) { onDispose { if (skipStop) skipStop = false else { QuranAudio.stop(); followNav = false } } }
    // تشغيل السورة كاملة: الصفحة تتبع الآية الحالية
    androidx.compose.runtime.LaunchedEffect(cur) {
        val c = cur ?: return@LaunchedEffect
        if (followNav) Quran.ayahs.firstOrNull { it.surah == c.first && it.num == c.second }?.let { if (it.page != page) { skipStop = true; onPage(it.page) } }
    }
    val first = Quran.byPage[page]?.firstOrNull()
    val isRead = remember(page, readTick) { page in prefs.khatmaPagesRead() || page in prefs.pagesReadOn(prefs.dayKey()) }

    val activityWin = (ctx as? android.app.Activity)?.window
    val hostView = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(readerFull) {
        val c = activityWin?.let { androidx.core.view.WindowCompat.getInsetsController(it, hostView) }
        if (readerFull) {
            c?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        } else c?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        onDispose { c?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars()) }
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { readerFull = false } }
    androidx.activity.compose.BackHandler(enabled = readerFull) { readerFull = false }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().padding(horizontal = if (readerFull) 4.dp else 8.dp)) {
        if (!readerFull) Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("صفحة ${Quran.arabicDigits(page)}", color = Gold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (first != null) Text("  •  الجزء ${Quran.arabicDigits(first.juz)}  •  ${Quran.surahName(first.surah)}", color = Muted, fontSize = 12.sp, maxLines = 1)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(CircleShape).clickable {
                    if (mode == 1) { mscale = (mscale - 0.25f).coerceAtLeast(0.75f); prefs.mushafScale = mscale }
                    else { fontF = (font - 2f).coerceAtLeast(18f); prefs.quranFont = fontF.roundToInt() }
                }, contentAlignment = Alignment.Center) { Text("أ−", color = Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                Box(Modifier.size(34.dp).clip(CircleShape).clickable {
                    if (mode == 1) { mscale = (mscale + 0.25f).coerceAtMost(2.5f); prefs.mushafScale = mscale }
                    else { fontF = (font + 2f).coerceAtMost(44f); prefs.quranFont = fontF.roundToInt() }
                }, contentAlignment = Alignment.Center) { Text("أ+", color = Gold, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
                Box(Modifier.size(34.dp).clip(CircleShape).clickable { prefs.toggleBookmark(page); bmTick++ }, contentAlignment = Alignment.Center) {
                    Text(if (bookmarked) "★" else "☆", color = Gold, fontSize = 20.sp)
                }
                Box {
                    Box(Modifier.size(34.dp).clip(CircleShape).clickable { optMenu = true }, contentAlignment = Alignment.Center) { Text("⋮", color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                    androidx.compose.material3.DropdownMenu(expanded = optMenu, onDismissRequest = { optMenu = false }) {
                        androidx.compose.material3.DropdownMenuItem(text = { Text("⛶  شاشة كاملة") }, onClick = { optMenu = false; readerFull = true })
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(if (mode == 1) "التحويل إلى عرض النص" else "التحويل إلى عرض المصحف") },
                            onClick = { optMenu = false; mode = 1 - mode; prefs.mushafMode = mode }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("لون الصفحة: " + when (theme) { 0 -> "ورق"; 1 -> "أبيض"; else -> "داكن" } + " (اضغط للتبديل)") },
                            onClick = { theme = (theme + 1) % 3; prefs.quranTheme = theme }
                        )
                        androidx.compose.material3.DropdownMenuItem(text = { Text("تكبير الخط  أ+") }, onClick = {
                            if (mode == 1) { mscale = (mscale + 0.25f).coerceAtMost(2.5f); prefs.mushafScale = mscale }
                            else { fontF = (font + 2f).coerceAtMost(44f); prefs.quranFont = fontF.roundToInt() }
                        })
                        androidx.compose.material3.DropdownMenuItem(text = { Text("تصغير الخط  أ-") }, onClick = {
                            if (mode == 1) { mscale = (mscale - 0.25f).coerceAtLeast(0.75f); prefs.mushafScale = mscale }
                            else { fontF = (font - 2f).coerceAtLeast(18f); prefs.quranFont = fontF.roundToInt() }
                        })
                    }
                }
            }
        }
        if (!readerFull) Spacer(Modifier.height(2.dp))

        if (mode == 1) {
            key(page) {
                var drag by remember { mutableStateOf(0f) }
                Box(
                    Modifier.weight(1f).fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(pageBg)
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                        .pinchZoom(onZoom = { z -> mscale = (mscale * z).coerceIn(0.75f, 2.5f) }, onEnd = { prefs.mushafScale = mscale })
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
                    // صفحة المصحف بتتحسب بالبكسل على أساس خط ثابت: لو خط النظام كبير (وخصوصًا التكبير غير الخطي في أندرويد ١٤+) العرض المقاس مش بيطابق المرسوم فالكلمات بتتقطع
                    val dMush = androidx.compose.ui.platform.LocalDensity.current
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(dMush.density, 1f)
                    ) { MushafPageView(page, ink, accent, hiBg, ayahSheet ?: cur, Modifier.fillMaxSize(), mscale) { s, a -> ayahSheet = s to a } }
                }
            }
        } else {
        key(page) {
                val scroll = rememberScrollState()
                Column(
                    Modifier.weight(1f).fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(pageBg)
                        .pinchZoom(onZoom = { z -> fontF = (fontF * z).coerceIn(18f, 44f) }, onEnd = { prefs.quranFont = fontF.roundToInt() })
                        .pointerInput(page) {
                            var drag = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { drag = 0f },
                                onDragEnd = {
                                    if (drag > 120f && page < Quran.TOTAL_PAGES) onPage(page + 1)
                                    else if (drag < -120f && page > 1) onPage(page - 1)
                                },
                                onHorizontalDrag = { _, d -> drag += d }
                            )
                        }
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
                            is Block.Run -> {
                                var lay by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                                val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                                Text(
                                    b.text, color = ink, fontSize = font.sp,
                                    lineHeight = (font * 2.0f).sp, fontFamily = Serif, textAlign = TextAlign.Justify,
                                    onTextLayout = { lay = it },
                                    modifier = Modifier.pointerInput(b.text) {
                                        detectTapGestures(onLongPress = { pos ->
                                            val l = lay ?: return@detectTapGestures
                                            val o = l.getOffsetForPosition(pos)
                                            b.text.getStringAnnotations("ayah", o, o).firstOrNull()?.item?.split(":")?.let { p ->
                                                val s = p.getOrNull(0)?.toIntOrNull(); val n = p.getOrNull(1)?.toIntOrNull()
                                                if (s != null && n != null) {
                                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                    ayahSheet = s to n
                                                }
                                            }
                                        })
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!readerFull) {
        Spacer(Modifier.height(4.dp))
        // التلاوة: شريط واحد مدمج؛ القارئ والنطاق والتنزيل والسرعة والنوم في نافذة سفلية
        val barShape = RoundedCornerShape(30.dp)
        val reciterName = QuranAudio.reciters.firstOrNull { it.id == reciter }?.name ?: "القارئ"
        Row(
            Modifier.fillMaxWidth().clip(barShape).background(CardBg)
                .border(1.dp, Gold.copy(alpha = 0.30f), barShape).padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { showAudioSheet = true }.padding(horizontal = 4.dp, vertical = 2.dp)) {
                Text(reciterName, color = Gold, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(
                    if (QuranAudio.loading) "جارٍ التحميل…" else if (QuranAudio.playing) "جارٍ التشغيل" else "خيارات الصوت ▾",
                    color = Muted, fontSize = 11.sp, maxLines = 1
                )
            }
            RoundBtn(when (repeat) { 1 -> "🔂"; else -> "🔁" }, active = repeat != 0) {
                repeat = (repeat + 1) % 3; prefs.repeatMode = repeat
                android.widget.Toast.makeText(ctx, when (repeat) { 1 -> "تكرار الآية"; 2 -> "تكرار الصفحة"; else -> "بدون تكرار" }, android.widget.Toast.LENGTH_SHORT).show()
            }
            RoundBtn("⏹") { QuranAudio.stop() }
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(Gold).clickable {
                    if (QuranAudio.loading) QuranAudio.stop()
                    else if (QuranAudio.current != null) QuranAudio.pauseResume()
                    else if (pageAyahs.isEmpty()) android.widget.Toast.makeText(ctx, "لا توجد آيات محمّلة لهذه الصفحة؛ حمّل نص المصحف أولًا", android.widget.Toast.LENGTH_LONG).show()
                    else QuranAudio.play(ctx, reciter, pageAyahs, 0, { prefs.repeatMode }) { if (page < Quran.TOTAL_PAGES) onPage(page + 1) }
                },
                contentAlignment = Alignment.Center
            ) { Text(if (QuranAudio.loading) "…" else if (QuranAudio.playing) "⏸" else "▶", color = Color.Black, fontSize = 22.sp) }
        }
        QuranAudio.error?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 12.sp) }
        if (QuranAudio.reciters.firstOrNull { it.id == reciter }?.surahUrl != null) Text("هذا القارئ يشغّل السورة كاملة من ملف واحد (بالإنترنت): بدون تظليل الآية الجارية ولا تكرار نطاق.", color = Muted, fontSize = 11.sp)
        val big = bigFont()
        val prevB: @Composable (Modifier) -> Unit = { m ->
            TextButton(onClick = { if (page > 1) onPage(page - 1) }, modifier = m, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("السابقة", color = Gold, fontSize = 13.sp, textAlign = TextAlign.Center) }
        }
        val doneB: @Composable (Modifier) -> Unit = { m ->
            Button(
                onClick = {
                    prefs.recordPageRead(page)
                    readTick++
                    if (page < Quran.TOTAL_PAGES) onPage(page + 1)
                },
                enabled = !isRead,
                modifier = m, contentPadding = PaddingValues(horizontal = 6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Gold.copy(alpha = 0.22f), contentColor = Gold, disabledContainerColor = Color(0x22FFFFFF), disabledContentColor = Muted)
            ) { Text(if (isRead) "✓ مقروءة" else "أتممت الصفحة ✓", fontSize = 13.sp, textAlign = TextAlign.Center) }
        }
        val nextB: @Composable (Modifier) -> Unit = { m ->
            TextButton(onClick = { if (page < Quran.TOTAL_PAGES) onPage(page + 1) }, modifier = m, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("التالية", color = Gold, fontSize = 13.sp, textAlign = TextAlign.Center) }
        }
        if (big) {
            // خط كبير: الأزرار تتكدّس عموديًا بعرض كامل بدل ما تضيق وتتقطع
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                doneB(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    prevB(Modifier.weight(1f)); nextB(Modifier.weight(1f))
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                prevB(Modifier.weight(1f)); doneB(Modifier.weight(1.6f)); nextB(Modifier.weight(1f))
            }
        }
        }
    }
    if (showAudioSheet) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showAudioSheet = false }, containerColor = Ink, contentColor = OnCard,
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("خيارات الصوت", color = Gold, fontWeight = FontWeight.Bold, fontSize = 20.sp, fontFamily = Serif, modifier = Modifier.padding(horizontal = 4.dp))
                OptCard("القارئ") {
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 2
                    ) {
                        QuranAudio.reciters.forEach { r ->
                            val on = reciter == r.id
                            val sh = RoundedCornerShape(14.dp)
                            Box(
                                Modifier.weight(1f).clip(sh).background(if (on) Gold else Color.Transparent)
                                    .border(1.dp, Gold.copy(alpha = if (on) 0f else 0.3f), sh)
                                    .clickable { reciter = r.id; prefs.reciter = r.id; QuranAudio.stop() }
                                    .padding(vertical = 11.dp, horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(r.name, fontSize = 13.sp, maxLines = 1, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    color = if (on) (if (Look.light) Color.White else Color.Black) else OnCard)
                            }
                        }
                    }
                    TextButton(onClick = { showAudioSheet = false; showDl = true }, modifier = Modifier.fillMaxWidth()) { Text("⬇ تنزيل للاستماع بدون إنترنت", color = Gold) }
                }
                OptCard("التشغيل") {
                    Text("التكرار", color = Muted, fontSize = 12.sp)
                    SegRow(listOf("بدون", "آية", "صفحة"), repeat) { repeat = it; prefs.repeatMode = it }
                    Spacer(Modifier.height(4.dp))
                    Text("السرعة", color = Muted, fontSize = 12.sp)
                    val speeds = listOf(0.75f, 1f, 1.25f, 1.5f)
                    SegRow(speeds.map { "${it}x" }, speeds.indexOf(QuranAudio.speed)) { QuranAudio.changeSpeed(speeds[it]) }
                    Spacer(Modifier.height(4.dp))
                    Text("مؤقت النوم" + if (QuranAudio.sleepAt > 0L) " (مفعّل)" else "", color = Muted, fontSize = 12.sp)
                    val sleeps = listOf(0, 15, 30, 60)
                    SegRow(listOf("لا", "15 د", "30 د", "60 د"), if (QuranAudio.sleepAt == 0L) 0 else -1) { QuranAudio.setSleep(sleeps[it]) }
                }
                if (pageAyahs.isNotEmpty()) {
                    // تشغيل كامل من المكان اللي واقف عنده: الآية الجارية لو في الصفحة، وإلا آية «من:» المختارة
                    val here = cur?.let { c -> pageAyahs.firstOrNull { it.surah == c.first && it.num == c.second } } ?: pageAyahs[rFrom.coerceIn(0, pageAyahs.size - 1)]
                    OptCard("تشغيل من هنا") {
                        Text("يبدأ من: سورة ${Quran.surahName(here.surah)} آية ${Quran.arabicDigits(here.num)}", color = Muted, fontSize = 12.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val bc = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black)
                            Button(
                                onClick = { showAudioSheet = false; followNav = false; QuranAudio.play(ctx, reciter, pageAyahs.dropWhile { it !== here }, 0, { prefs.repeatMode }) {} },
                                colors = bc, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)
                            ) { Text("باقي الصفحة", fontSize = 12.sp, maxLines = 1) }
                            Button(
                                onClick = { showAudioSheet = false; followNav = true; QuranAudio.play(ctx, reciter, Quran.ayahs.filter { it.surah == here.surah && it.num >= here.num }, 0, { prefs.repeatMode }) {} },
                                colors = bc, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)
                            ) { Text("السورة كاملة", fontSize = 12.sp, maxLines = 1) }
                            Button(
                                onClick = {
                                    showAudioSheet = false
                                    followNav = true
                                    val i = Quran.ayahs.indexOfFirst { it.surah == here.surah && it.num == here.num }.coerceAtLeast(0)
                                    QuranAudio.play(ctx, reciter, Quran.ayahs.subList(i, Quran.ayahs.size), 0, { prefs.repeatMode }) {}
                                },
                                colors = bc, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)
                            ) { Text("المصحف كامل", fontSize = 12.sp, maxLines = 1) }
                        }
                    }
                    OptCard("تكرار نطاق من آيات الصفحة") {
                        val opts = pageAyahs.mapIndexed { i, a -> i.toString() to "آية ${Quran.arabicDigits(a.num)}" }
                        PickRow("من", opts.getOrNull(rFrom)?.second ?: "", opts) { rFrom = it.toInt(); if (rTo < rFrom) rTo = rFrom }
                        androidx.compose.material3.HorizontalDivider(color = Gold.copy(alpha = 0.12f))
                        PickRow("إلى", opts.getOrNull(rTo)?.second ?: "", opts.filter { it.first.toInt() >= rFrom }) { rTo = it.toInt() }
                        androidx.compose.material3.HorizontalDivider(color = Gold.copy(alpha = 0.12f))
                        Spacer(Modifier.height(6.dp))
                        Text("عدد المرات", color = Muted, fontSize = 12.sp)
                        val times = listOf(1, 3, 5, 10, 0)
                        SegRow(listOf("١", "٣", "٥", "١٠", "∞"), times.indexOf(rTimes)) { rTimes = times[it] }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                showAudioSheet = false
                                val to = rTo.coerceIn(rFrom, pageAyahs.size - 1)
                                QuranAudio.play(ctx, reciter, pageAyahs.subList(rFrom, to + 1), 0, { if (rTimes == 0) 2 else 0 }, rTimes) {}
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black)
                        ) { Text("▶ شغّل النطاق") }
                    }
                }
            }
        }
    }
    // أدوات الشاشة الكاملة مخفية افتراضيًا (عشان ما تغطيش كلام المصحف)؛ لمسة على الصفحة بتظهرها ٣ ثواني
    ayahSheet?.let { (s, a) ->
        AyahActionsSheet(
            s, a,
            onPlay = { list, follow -> QuranAudio.play(ctx, reciter, list, 0, { prefs.repeatMode }) {}; followNav = follow },
            onExitFull = if (readerFull) ({ readerFull = false }) else null,
            onBookmark = { bmTick++ },
            onDismiss = { ayahSheet = null }
        )
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
                        color = Gold, trackColor = Track
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
                                fontSize = 13.sp, color = if (started && n >= target) Gold else OnCard
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
                        color = Gold, trackColor = Track
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


/** تكبير/تصغير بإصبعين: بيشتغل بس لما يكون فيه إصبعين، والإصبع الواحد بيعدّي للتمرير/السحب عادي. */
private fun Modifier.pinchZoom(onZoom: (Float) -> Unit, onEnd: () -> Unit): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
        var prev = 0f
        var zoomed = false
        do {
            val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            val pts = ev.changes.filter { it.pressed }
            if (pts.size >= 2) {
                val d = (pts[0].position - pts[1].position).getDistance()
                if (prev > 0f && d > 0f) { onZoom(d / prev); zoomed = true }
                prev = d
                ev.changes.forEach { it.consume() }
            } else prev = 0f
        } while (ev.changes.any { it.pressed })
        if (zoomed) onEnd()
    }
}

/** كارت زجاجي بعنوان لتجميع خيارات الصوت */
@Composable
private fun OptCard(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().glassSurface(20.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = Gold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        content()
    }
}
