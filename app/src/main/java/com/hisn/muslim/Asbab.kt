package com.hisn.muslim

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AsbabSource(val id: String, val name: String, val credit: String, val latin: Boolean)
/** سبب (أو أكثر) لآية أو مجموعة آيات */
class AsbabItem(val ayahs: List<Int>, val texts: List<String>)

/**
 * أسباب النزول:
 *  - العربي: مضمّن في التطبيق (بيعمل بدون إنترنت) من «صحيح أسباب النزول — دراسة حديثية» لإبراهيم بن محمد العلي،
 *    عبر مجموعة بيانات mostafaahmed97/asbab-al-nuzul-dataset (ترخيص MIT، نصها في assets/licenses).
 *  (المصدر الإنجليزي للواحدي اتشال: ملفه في spa5k/tafsir_api مش بنفس الصيغة وما اتحمّلش على الجهاز.)
 * النص بيتعرض كما هو بدون أي تعديل. الآيات اللي ما لهاش سبب نزول منقول بتظهر فاضية.
 */
object Asbab {
    val sources = listOf(
        AsbabSource(
            "ar-sahih", "صحيح أسباب النزول (عربي)",
            "من كتاب «صحيح أسباب النزول — دراسة حديثية» لإبراهيم بن محمد العلي (الصحيح من أسباب النزول)، عبر مجموعة بيانات mostafaahmed97/asbab-al-nuzul-dataset (MIT).", false
        )
    )

    private var arCache: Map<Int, List<AsbabItem>>? = null

    private fun arabic(ctx: Context): Map<Int, List<AsbabItem>> = arCache ?: run {
        val arr = org.json.JSONArray(ctx.assets.open("asbab_ar.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
        val m = HashMap<Int, MutableList<AsbabItem>>()
        for (k in 0 until arr.length()) {
            val o = arr.getJSONObject(k)
            val ay = o.getJSONArray("ayahs").let { x -> (0 until x.length()).map { x.getInt(it) } }
            val oc = o.getJSONArray("occasions").let { x -> (0 until x.length()).map { x.getString(it) } }
            m.getOrPut(o.getInt("surah")) { mutableListOf() }.add(AsbabItem(ay, oc))
        }
        m.also { arCache = it }
    }

    /** أسباب نزول سورة. من thread خلفي. بيرجع القائمة ورسالة خطأ (للإنجليزي بس لو التحميل فشل). */
    fun items(ctx: Context, src: AsbabSource, surah: Int): Pair<List<AsbabItem>?, String?> {
        if (!src.latin) return runCatching { (arabic(ctx)[surah] ?: emptyList()) to null }.getOrElse { null to "تعذّر قراءة بيانات أسباب النزول" }
        var err: String? = null
        if (!TafsirStore.hasSurah(ctx, src.id, surah)) {
            TafsirStore.downloadSurah(ctx, src.id, surah)?.let { err = "تعذّر التحميل ($it). تأكد من الاتصال بالإنترنت." }
        }
        val m = TafsirStore.load(ctx, src.id, surah) ?: return null to err
        return m.filter { it.value.isNotBlank() }.map { AsbabItem(listOf(it.key), listOf(it.value)) } to err
    }
}

@Composable
private fun AsbabTexts(item: AsbabItem, src: AsbabSource) {
    item.texts.forEachIndexed { i, t ->
        if (item.texts.size > 1) Text("الرواية ${Quran.arabicDigits(i + 1)}", color = Gold, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        androidx.compose.foundation.text.selection.SelectionContainer {
            Text(t, fontSize = 15.sp, lineHeight = 26.sp, textAlign = if (src.latin) TextAlign.Left else TextAlign.Start)
        }
        if (i < item.texts.size - 1) Spacer(Modifier.height(8.dp))
    }
}

private fun label(ayahs: List<Int>) =
    if (ayahs.size == 1) "آية ${Quran.arabicDigits(ayahs[0])}" else "الآيات ${Quran.arabicDigits(ayahs.first())}–${Quran.arabicDigits(ayahs.last())}"

/** أسباب النزول لآيات من سورة (للقائمة المطوّلة): من آية إلى آية */
@Composable
fun AsbabForRange(surah: Int, from: Int, to: Int) {
    val ctx = LocalContext.current
    var srcIdx by remember { mutableIntStateOf(0) }
    val src = Asbab.sources[srcIdx]
    var res by remember(surah, srcIdx) { mutableStateOf<Pair<List<AsbabItem>?, String?>?>(null) }
    LaunchedEffect(surah, srcIdx) { res = withContext(Dispatchers.IO) { Asbab.items(ctx, src, surah) } }
    if (Asbab.sources.size > 1) Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Asbab.sources.forEachIndexed { i, s -> GoldChip(if (s.latin) "English" else "عربي", srcIdx == i) { srcIdx = i } }
    }
    val r = res
    if (r == null) { Text("جارٍ التحميل…", color = Gold, fontSize = 13.sp); return }
    val list = r.first
    if (list == null) { Text(r.second ?: "تعذّر التحميل", color = Color(0xFFFF8A80), fontSize = 13.sp); return }
    val hit = list.filter { it.ayahs.any { a -> a in from..to } }
    if (hit.isEmpty()) Text("لا يوجد سبب نزول منقول لهذه الآية في هذا المصدر (ليس لكل آية سبب نزول).", color = Muted, fontSize = 13.sp)
    hit.forEach { item ->
        Text(label(item.ayahs), color = Gold, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        AsbabTexts(item, src)
    }
    Text("— ${src.name}", color = Muted, fontSize = 11.sp)
}

/** ترتيب أسباب النزول لآيات الصفحة الحالية */
@Composable
fun AsbabPane(page: Int) {
    val ctx = LocalContext.current
    var srcIdx by remember { mutableIntStateOf(0) }
    val src = Asbab.sources[srcIdx]
    val ayahs = Quran.byPage[page].orEmpty()
    var hits by remember(src.id, page) { mutableStateOf<List<Triple<Int, AsbabItem, Ayah?>>>(emptyList()) }
    var msg by remember(src.id, page) { mutableStateOf<String?>(null) }
    var loading by remember(src.id, page) { mutableStateOf(true) }
    LaunchedEffect(src.id, page) {
        loading = true; msg = null
        val out = mutableListOf<Triple<Int, AsbabItem, Ayah?>>()
        for (s in ayahs.map { it.surah }.distinct()) {
            val (list, e) = withContext(Dispatchers.IO) { Asbab.items(ctx, src, s) }
            if (e != null) msg = e
            val onPage = ayahs.filter { it.surah == s }.map { it.num }.toSet()
            list?.filter { it.ayahs.any { a -> a in onPage } }?.forEach { item ->
                out += Triple(s, item, ayahs.firstOrNull { it.surah == s && it.num in item.ayahs })
            }
        }
        hits = out; loading = false
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Glass {
                SectionTitle("أسباب النزول — صفحة ${Quran.arabicDigits(page)}")
                if (Asbab.sources.size > 1) Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Asbab.sources.forEachIndexed { i, s -> GoldChip(s.name, srcIdx == i) { srcIdx = i } }
                }
                Text(src.credit, color = Muted, fontSize = 11.sp)
                if (loading) Text("جارٍ التحميل…", color = Gold, fontSize = 12.sp)
                msg?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 12.sp) }
            }
        }
        if (!loading && msg == null && hits.isEmpty()) item {
            Glass { Text("لا يوجد سبب نزول منقول في هذا المصدر لآيات هذه الصفحة. (ليس لكل آية سبب نزول).", color = Muted, fontSize = 14.sp) }
        }
        items(hits) { (s, item, a) ->
            Glass {
                a?.let { Text(it.text, fontFamily = Serif, fontSize = 19.sp, lineHeight = 32.sp) }
                Text("${Quran.surahName(s)} — ${label(item.ayahs)}", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                AsbabTexts(item, src)
                Text("— ${src.name}", color = Muted, fontSize = 11.sp)
            }
        }
    }
}
