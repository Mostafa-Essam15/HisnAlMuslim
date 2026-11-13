package com.hisn.muslim

import android.content.Context
import android.graphics.Typeface as AndroidTypeface
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Typeface
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class MWord(val surah: Int, val ayah: Int, val glyph: String)
class MLine(val type: String, val text: String, val words: List<MWord>)

/**
 * مصحف المدينة بصفحاته الحقيقية (١٥ سطرًا لكل صفحة) بخطوط مجمع الملك فهد لطباعة المصحف الشريف (QCF).
 * التطبيق مبيحمّلش الخطوط ولا بيوزّعها؛ جهاز المستخدم بيحمّلها مرة واحدة من مستودعات GitHub العامة
 * وبتتخزن للقراءة بدون إنترنت:
 *  - توزيع الأسطر والكلمات: github.com/zonetecde/mushaf-layout (بيانات Quran.com، نسخة QPC V1)
 *  - خطوط الصفحات: github.com/mustafa0x/qpc-fonts (خطوط مجمع الملك فهد QCF_P001..P604)
 */
object MushafStore {
    private const val JSON_URL = "https://raw.githubusercontent.com/zonetecde/mushaf-layout/v2/mushaf/QPC1/page-%03d.json"
    private const val FONT_URL = "https://raw.githubusercontent.com/mustafa0x/qpc-fonts/master/mushaf/QCF_P%03d.TTF"

    var bulkProgress by mutableIntStateOf(0)      // عدد الصفحات المكتملة
    var bulkRunning by mutableStateOf(false)
    var bulkError by mutableStateOf<String?>(null)

    private fun dir(ctx: Context) = File(ctx.filesDir, "mushaf").apply { mkdirs() }
    private fun jsonFile(ctx: Context, p: Int) = File(dir(ctx), "page-%03d.json".format(p))
    private fun fontFile(ctx: Context, p: Int) = File(dir(ctx), "QCF_P%03d.ttf".format(p))

    fun hasPage(ctx: Context, p: Int) = validJson(jsonFile(ctx, p)) && validFont(fontFile(ctx, p))

    fun downloadedCount(ctx: Context): Int = (1..Quran.TOTAL_PAGES).count { hasPage(ctx, it) }

    private fun validJson(f: File) = f.exists() && f.length() > 200
    private fun validFont(f: File): Boolean {
        if (!f.exists() || f.length() < 5_000) return false
        // رأس ملف TrueType: 00 01 00 00 أو 'true' أو 'OTTO'
        return try {
            f.inputStream().use { s ->
                val h = ByteArray(4)
                if (s.read(h) < 4) false
                else (h[0].toInt() == 0 && h[1].toInt() == 1 && h[2].toInt() == 0 && h[3].toInt() == 0) ||
                    String(h) == "true" || String(h) == "OTTO"
            }
        } catch (e: Exception) { false }
    }

    private fun fetch(url: String, target: File): String? {
        val tmp = File(target.parentFile, target.name + ".part")
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000; readTimeout = 30_000
                setRequestProperty("User-Agent", "HisnAlMuslim/2.0")
            }
            if (conn.responseCode !in 200..299) return "كود ${conn.responseCode}"
            conn.inputStream.use { i -> FileOutputStream(tmp).use { o -> i.copyTo(o) } }
            target.delete()
            if (tmp.renameTo(target)) null else "تعذّر الحفظ"
        } catch (e: Exception) {
            tmp.delete()
            "تعذّر الاتصال"
        } finally {
            conn?.disconnect()
        }
    }

    /** تحميل صفحة واحدة (JSON + الخط). بيرجع null لو نجح أو رسالة الخطأ. من thread خلفي. */
    fun downloadPage(ctx: Context, p: Int): String? {
        if (!validJson(jsonFile(ctx, p))) fetch(JSON_URL.format(p), jsonFile(ctx, p))?.let { return it }
        if (!validJson(jsonFile(ctx, p))) return "ملف الصفحة غير صالح"
        // تأكد إن الـ JSON سليم
        try { parse(jsonFile(ctx, p).readText()) } catch (e: Exception) { jsonFile(ctx, p).delete(); return "ملف الصفحة تالف" }
        if (!validFont(fontFile(ctx, p))) {
            fontFile(ctx, p).delete()
            fetch(FONT_URL.format(p), fontFile(ctx, p))?.let { return it }
            if (!validFont(fontFile(ctx, p))) { fontFile(ctx, p).delete(); return "خط الصفحة غير صالح" }
        }
        return null
    }

    /** تحميل كل الصفحات (٤ خيوط)، ثم التحقق من سلامتها كلها. */
    fun downloadAll(ctx: Context) {
        if (bulkRunning) return
        bulkRunning = true; bulkError = null
        bulkProgress = downloadedCount(ctx)
        val pool = Executors.newFixedThreadPool(4)
        val failed = AtomicInteger(0)
        val done = AtomicInteger(bulkProgress)
        try {
            val futures = (1..Quran.TOTAL_PAGES).filter { !hasPage(ctx, it) }.map { p ->
                pool.submit {
                    var err: String? = downloadPage(ctx, p)
                    if (err != null) err = downloadPage(ctx, p) // محاولة ثانية
                    if (err != null) failed.incrementAndGet() else bulkProgress = done.incrementAndGet()
                }
            }
            futures.forEach { runCatching { it.get() } }
        } finally {
            pool.shutdown()
        }
        val ok = downloadedCount(ctx)
        bulkProgress = ok
        bulkError = if (ok < Quran.TOTAL_PAGES) "اكتمل تحميل $ok من ${Quran.TOTAL_PAGES} صفحة. أعد المحاولة لإكمال الباقي." else null
        bulkRunning = false
    }

    fun parse(json: String): List<MLine> {
        val o = JSONObject(json)
        val arr = o.getJSONArray("lines")
        return (0 until arr.length()).map { i ->
            val l = arr.getJSONObject(i)
            val type = l.optString("type")
            val ws = l.optJSONArray("words")
            val words = if (ws == null) emptyList() else (0 until ws.length()).map { j ->
                val w = ws.getJSONObject(j)
                val loc = w.optString("location").split(":")
                MWord(loc.getOrNull(0)?.toIntOrNull() ?: 0, loc.getOrNull(1)?.toIntOrNull() ?: 0, w.optString("qpcV1"))
            }.filter { it.glyph.isNotEmpty() }
            MLine(type, l.optString("text"), words)
        }
    }

    fun load(ctx: Context, p: Int): Pair<List<MLine>, FontFamily>? {
        if (!hasPage(ctx, p)) return null
        return try {
            val lines = parse(jsonFile(ctx, p).readText())
            val tf = AndroidTypeface.createFromFile(fontFile(ctx, p))
            lines to FontFamily(Typeface(tf))
        } catch (e: Exception) {
            null
        }
    }
}

/** عرض صفحة مصحف المدينة. لو الصفحة مش محمّلة بيحاول يحمّلها، ولو فشل بيعرض زر التحميل. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MushafPageView(page: Int, ink: Color, accent: Color, hi: Color, current: Pair<Int, Int>?, modifier: Modifier = Modifier, scale: Float = 1f, onAyah: ((Int, Int) -> Unit)? = null) {
    val ctx = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var attempt by remember(page) { mutableIntStateOf(0) }
    var err by remember(page) { mutableStateOf<String?>(null) }
    val data by produceState<Pair<List<MLine>, FontFamily>?>(null, page, attempt, MushafStore.bulkProgress / 50) {
        value = withContext(Dispatchers.IO) {
            MushafStore.load(ctx, page) ?: run {
                err = MushafStore.downloadPage(ctx, page)
                MushafStore.load(ctx, page)
            }
        }
    }
    val d = data
    if (d == null) {
        Column(modifier.padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("صفحة المصحف غير محمّلة", color = ink, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            err?.let { Text("(${it})", color = Color(0xFFB3261E), fontSize = 12.sp) }
            Spacer(Modifier.height(8.dp))
            Text(
                "حمّل صفحات المصحف مرة واحدة (حوالي ٦٠ ميجا) لتعمل بدون إنترنت. الخطوط من مجمع الملك فهد لطباعة المصحف الشريف.",
                color = ink, fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            if (MushafStore.bulkRunning) {
                LinearProgressIndicator(progress = { MushafStore.bulkProgress / Quran.TOTAL_PAGES.toFloat() }, modifier = Modifier.fillMaxWidth(), color = Gold)
                Text("${MushafStore.bulkProgress} / ${Quran.TOTAL_PAGES}", color = ink, fontSize = 12.sp)
            } else {
                Button(
                    onClick = { scope.launch(Dispatchers.IO) { MushafStore.downloadAll(ctx); attempt++ } },
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black)
                ) { Text("⬇ تحميل كل الصفحات") }
                Button(
                    onClick = { attempt++ },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x33888888), contentColor = ink)
                ) { Text("إعادة محاولة هذه الصفحة") }
                MushafStore.bulkError?.let { Text(it, color = Color(0xFFB3261E), fontSize = 12.sp) }
            }
        }
        return
    }
    val (lines, family) = d
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val boxW = maxWidth
        val boxH = maxHeight
        val availW = with(density) { (maxWidth * scale).toPx() }
        val availHdp = maxHeight.value * scale
        val textLines = lines.filter { it.type == "text" }
        val ref = 24.sp
        val gap0 = 24f * 0.22f
        // عرض كل سطر بالحجم المرجعي، وأعرض سطر هو اللي بيحدد حجم الخط
        fun natural(l: MLine, m: TextMeasurer): Float =
            l.words.sumOf { m.measure(it.glyph, TextStyle(fontFamily = family, fontSize = ref)).size.width.toDouble() }.toFloat() +
                (l.words.size - 1).coerceAtLeast(0) * with(density) { gap0.sp.toPx() }
        val widths = textLines.map { natural(it, measurer) }
        val maxW = widths.maxOrNull()?.takeIf { it > 0 } ?: 1f
        val fsByWidth = 24f * availW / maxW * 0.97f // هامش أمان ٣٪ لامتداد حبر الخط خارج مقاس الكلمة
        val rowH = availHdp / 15f
        val fs = minOf(fsByWidth, rowH * 0.78f).coerceAtLeast(10f)
        val gapPx = with(density) { (fs * 0.22f).sp.toPx() }
        Box(Modifier.fillMaxSize().then(if (scale > 1f) Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()).verticalScroll(androidx.compose.foundation.rememberScrollState()) else Modifier), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.width(boxW * scale).height(boxH * scale)) {
            var ti = 0
            lines.forEach { l ->
                Box(Modifier.fillMaxWidth().height(rowH.dp), contentAlignment = Alignment.Center) {
                    when (l.type) {
                        "surah-header" -> Box(
                            Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 3.dp)
                                .background(accent.copy(alpha = 0.18f), androidx.compose.foundation.shape.RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            // خط أصغر + ارتفاع غير مقيّد عشان أعلى/أسفل الحروف ما يتقصّش
                            Text(
                                l.text, color = accent, fontFamily = Serif, fontSize = (rowH * 0.34f).sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Visible,
                                modifier = Modifier.wrapContentHeight(align = Alignment.CenterVertically, unbounded = true)
                            )
                        }
                        "basmala" -> Text(
                            "بِسْمِ ٱللَّهِ ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ",
                            color = ink, fontFamily = Serif, fontSize = (rowH * 0.4f).sp,
                            maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Visible,
                            modifier = Modifier.wrapContentHeight(align = Alignment.CenterVertically, unbounded = true)
                        )
                        "text" -> {
                            val w = widths[ti++]
                            val full = w / maxW >= 0.93f
                            Row(
                                // ارتفاع غير مقيّد: خطوط QCF أطول من الصف فكان أسفل الحروف بيتقصّ
                                Modifier.fillMaxWidth().wrapContentHeight(align = Alignment.CenterVertically, unbounded = true),
                                horizontalArrangement = if (full && l.words.size > 1) Arrangement.SpaceBetween else Arrangement.spacedBy(with(density) { gapPx.toDp() }, Alignment.CenterHorizontally)
                            ) {
                                l.words.forEach { word ->
                                    val on = current != null && current.first == word.surah && current.second == word.ayah
                                    Text(
                                        word.glyph, color = ink, fontFamily = family, fontSize = fs.sp,
                                        softWrap = false, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Visible,
                                        modifier = (if (on) Modifier.background(hi) else Modifier).then(
                                            if (onAyah != null && word.surah > 0) Modifier.combinedClickable(
                                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null,
                                                onClick = {},
                                                onLongClick = {
                                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                    onAyah(word.surah, word.ayah)
                                                }
                                            ) else Modifier
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }
}
