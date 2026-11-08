package com.hisn.muslim

import android.content.Context
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class TafsirEdition(val slug: String, val name: String, val author: String)

/**
 * التفاسير: بتتحمّل سورة سورة من مستودع spa5k/tafsir_api (ترخيص MIT، والبيانات من Quran.com / Tarteel QUL)
 * وتتخزن على الجهاز للقراءة بدون إنترنت. النص بيتعرض كما هو من غير أي تعديل.
 */
object TafsirStore {
    private const val BASE = "https://raw.githubusercontent.com/spa5k/tafsir_api/main/tafsir"

    val editions = listOf(
        TafsirEdition("ar-tafsir-muyassar", "التفسير الميسر", "نخبة من العلماء (مجمع الملك فهد)"),
        TafsirEdition("ar-tafsir-as-saadi", "تفسير السعدي", "عبد الرحمن بن ناصر السعدي"),
        TafsirEdition("ar-tafsir-ibn-kathir", "تفسير ابن كثير", "إسماعيل بن عمر بن كثير"),
        TafsirEdition("ar-tafsir-al-jalalayn", "تفسير الجلالين", "المحلي والسيوطي"),
        TafsirEdition("ar-tafsir-al-baghawi", "تفسير البغوي", "الحسين بن مسعود البغوي"),
        TafsirEdition("ar-tafseer-al-qurtubi", "تفسير القرطبي", "محمد بن أحمد القرطبي"),
        TafsirEdition("ar-tafsir-al-tabari", "تفسير الطبري", "محمد بن جرير الطبري")
    )

    var bulkProgress by mutableIntStateOf(0)
    var bulkRunning by mutableStateOf(false)
    var bulkMsg by mutableStateOf<String?>(null)

    private fun file(ctx: Context, slug: String, surah: Int) = File(File(ctx.filesDir, "tafsir/$slug").apply { mkdirs() }, "$surah.json")

    fun hasSurah(ctx: Context, slug: String, surah: Int) = file(ctx, slug, surah).let { it.exists() && it.length() > 50 }

    fun downloadedSurahs(ctx: Context, slug: String) = (1..114).count { hasSurah(ctx, slug, it) }

    private fun parseOk(s: String): Boolean = try { JSONArray(s).length() > 0 } catch (e: Exception) { false }

    /** بيرجع null لو نجح أو رسالة خطأ. من thread خلفي. */
    fun downloadSurah(ctx: Context, slug: String, surah: Int): String? {
        if (hasSurah(ctx, slug, surah)) return null
        val target = file(ctx, slug, surah)
        val tmp = File(target.parentFile, "$surah.part")
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL("$BASE/$slug/$surah.json").openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000; readTimeout = 40_000
                setRequestProperty("User-Agent", "HisnAlMuslim/2.0")
            }
            if (conn.responseCode !in 200..299) return "كود ${conn.responseCode}"
            conn.inputStream.use { i -> FileOutputStream(tmp).use { o -> i.copyTo(o) } }
            if (!parseOk(tmp.readText())) { tmp.delete(); return "ملف تالف" }
            target.delete()
            if (tmp.renameTo(target)) null else "تعذّر الحفظ"
        } catch (e: Exception) {
            tmp.delete(); "تعذّر الاتصال"
        } finally {
            conn?.disconnect()
        }
    }

    fun downloadAll(ctx: Context, slug: String) {
        if (bulkRunning) return
        bulkRunning = true; bulkMsg = null
        val done = AtomicInteger(downloadedSurahs(ctx, slug))
        bulkProgress = done.get()
        val pool = Executors.newFixedThreadPool(3)
        try {
            (1..114).filter { !hasSurah(ctx, slug, it) }.map { s ->
                pool.submit {
                    var e = downloadSurah(ctx, slug, s)
                    if (e != null) e = downloadSurah(ctx, slug, s)
                    if (e == null) bulkProgress = done.incrementAndGet()
                }
            }.forEach { runCatching { it.get() } }
        } finally {
            pool.shutdown()
        }
        bulkProgress = downloadedSurahs(ctx, slug)
        bulkMsg = if (bulkProgress < 114) "اكتمل تحميل $bulkProgress من ١١٤ سورة؛ أعد المحاولة." else "تم تحميل التفسير كاملًا ✓"
        bulkRunning = false
    }

    /** تفسير آيات السورة: رقم الآية ← النص */
    fun load(ctx: Context, slug: String, surah: Int): Map<Int, String>? {
        val f = file(ctx, slug, surah)
        if (!f.exists()) return null
        return try {
            val arr = JSONArray(f.readText())
            val m = LinkedHashMap<Int, String>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val a = o.optString("ayah").toIntOrNull() ?: continue
                m[a] = o.optString("text")
            }
            m
        } catch (e: Exception) { null }
    }
}

var Prefs.tafsirSlug: String
    get() = sp.getString("tafsirSlug", "ar-tafsir-muyassar") ?: "ar-tafsir-muyassar"
    set(v) { sp.edit().putString("tafsirSlug", v).apply() }

@Composable
fun TafsirPane(prefs: Prefs, page: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var slug by remember { mutableStateOf(prefs.tafsirSlug) }
    val ed = TafsirStore.editions.firstOrNull { it.slug == slug } ?: TafsirStore.editions.first()
    val ayahs = Quran.byPage[page].orEmpty()
    val data = remember(slug, page) { mutableStateMapOf<Int, Map<Int, String>>() }
    var msg by remember(slug, page) { mutableStateOf<String?>(null) }
    var loading by remember(slug, page) { mutableStateOf(true) }
    val open = remember(slug, page) { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(slug, page) {
        loading = true; msg = null
        for (s in ayahs.map { it.surah }.distinct()) {
            val m = withContext(Dispatchers.IO) {
                if (!TafsirStore.hasSurah(ctx, slug, s)) {
                    val e = TafsirStore.downloadSurah(ctx, slug, s)
                    if (e != null) msg = "تعذّر تحميل التفسير ($e). تأكد من الاتصال بالإنترنت."
                }
                TafsirStore.load(ctx, slug, s)
            }
            if (m != null) data[s] = m
        }
        loading = false
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Glass {
                SectionTitle("التفسير — صفحة ${Quran.arabicDigits(page)}")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TafsirStore.editions.forEach { e -> GoldChip(e.name, slug == e.slug) { slug = e.slug; prefs.tafsirSlug = e.slug } }
                }
                Text("المفسّر: ${ed.author}", color = Muted, fontSize = 12.sp)
                Text("المصدر: مستودع spa5k/tafsir_api (بيانات Quran.com / Tarteel). النص كما ورد دون تعديل.", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                val have = TafsirStore.downloadedSurahs(ctx, slug)
                if (TafsirStore.bulkRunning) {
                    LinearProgressIndicator(progress = { TafsirStore.bulkProgress / 114f }, modifier = Modifier.fillMaxWidth(), color = Gold)
                    Text("${TafsirStore.bulkProgress} / ١١٤ سورة", color = Gold, fontSize = 12.sp)
                } else {
                    OutlinedButton(onClick = { scope.launch(Dispatchers.IO) { TafsirStore.downloadAll(ctx, slug) } }) {
                        Text(if (have >= 114) "✓ التفسير محمّل كاملًا (يعمل بدون إنترنت)" else "⬇ تحميل هذا التفسير كاملًا ($have/١١٤)", color = Gold, fontSize = 12.sp)
                    }
                }
                TafsirStore.bulkMsg?.let { Text(it, color = Gold, fontSize = 12.sp) }
                if (loading) Text("جارٍ التحميل…", color = Gold, fontSize = 12.sp)
                msg?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 12.sp) }
            }
        }
        items(ayahs) { a ->
            val t = data[a.surah]?.get(a.num)
            val key = "${a.surah}:${a.num}"
            Glass {
                Text(a.text, fontFamily = Serif, fontSize = 20.sp, lineHeight = 34.sp)
                Text("${Quran.surahName(a.surah)} — آية ${Quran.arabicDigits(a.num)}", color = Muted, fontSize = 12.sp)
                val shown = open[key] == true
                TextButton(onClick = { open[key] = !shown }) { Text(if (shown) "إخفاء التفسير" else "عرض التفسير", color = Gold) }
                if (shown) {
                    if (t == null) Text(if (loading) "جارٍ التحميل…" else "التفسير غير متاح حاليًا", color = Muted, fontSize = 13.sp)
                    else {
                        val prev = data[a.surah]?.get(a.num - 1)
                        if (prev != null && prev == t) Text("هذه الآية داخلة في شرح الآية السابقة (نفس الفقرة).", color = Muted, fontSize = 13.sp)
                        Text(t, fontSize = 16.sp, lineHeight = 28.sp)
                        Text("— ${ed.name}: ${ed.author}", color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
