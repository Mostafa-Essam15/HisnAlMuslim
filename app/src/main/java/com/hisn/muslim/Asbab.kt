package com.hisn.muslim

import android.content.Context
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

class AsbabSource(val slug: String, val name: String, val credit: String, val latin: Boolean)

/**
 * أسباب النزول: بتتحمّل من نفس مستودع التفاسير (spa5k/tafsir_api) وتتخزن للقراءة بدون إنترنت.
 * النص بيتعرض كما هو من غير أي تعديل أو كتابة من عندنا. الآيات اللي ما لهاش سبب نزول في الكتاب بتظهر فاضية.
 * المصادر العربية بتتضاف هنا لما نتأكد من مصدر موثوق وصيغته.
 */
object Asbab {
    val sources = listOf(
        AsbabSource(
            "en-asbab-al-nuzul-by-al-wahidi", "أسباب النزول — الواحدي (ترجمة إنجليزية)",
            "كتاب «أسباب النزول» لأبي الحسن الواحدي، بالترجمة الإنجليزية. المصدر: altafsir.com عبر مستودع spa5k/tafsir_api.", true
        )
    )

    /** رقم الآية ← النص. بيحمّل السورة لو مش موجودة. من thread خلفي. بيرجع الخريطة ورسالة الخطأ. */
    fun load(ctx: Context, slug: String, surah: Int): Pair<Map<Int, String>?, String?> {
        var err: String? = null
        if (!TafsirStore.hasSurah(ctx, slug, surah)) {
            TafsirStore.downloadSurah(ctx, slug, surah)?.let { err = "تعذّر التحميل ($it). تأكد من الاتصال بالإنترنت." }
        }
        return TafsirStore.load(ctx, slug, surah) to err
    }
}

/** ترتيب أسباب النزول لآيات الصفحة الحالية */
@Composable
fun AsbabPane(page: Int) {
    val ctx = LocalContext.current
    var srcIdx by remember { mutableIntStateOf(0) }
    val src = Asbab.sources[srcIdx]
    val ayahs = Quran.byPage[page].orEmpty()
    val data = remember(src.slug, page) { mutableStateMapOf<Int, Map<Int, String>>() }
    var msg by remember(src.slug, page) { mutableStateOf<String?>(null) }
    var loading by remember(src.slug, page) { mutableStateOf(true) }
    LaunchedEffect(src.slug, page) {
        loading = true; msg = null
        for (s in ayahs.map { it.surah }.distinct()) {
            val (m, e) = withContext(Dispatchers.IO) { Asbab.load(ctx, src.slug, s) }
            if (e != null) msg = e
            if (m != null) data[s] = m
        }
        loading = false
    }
    val withText = ayahs.filter { !data[it.surah]?.get(it.num).isNullOrBlank() }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Glass {
                SectionTitle("أسباب النزول — صفحة ${Quran.arabicDigits(page)}")
                Text(src.name, color = Gold, fontSize = 13.sp)
                Text(src.credit, color = Muted, fontSize = 11.sp)
                if (loading) Text("جارٍ التحميل…", color = Gold, fontSize = 12.sp)
                msg?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 12.sp) }
            }
        }
        if (!loading && msg == null && withText.isEmpty()) item {
            Glass { Text("لا يوجد سبب نزول منقول في هذا المصدر لآيات هذه الصفحة.", color = Muted, fontSize = 14.sp) }
        }
        items(withText) { a ->
            Glass {
                Text(a.text, fontFamily = Serif, fontSize = 19.sp, lineHeight = 32.sp)
                Text("${Quran.surahName(a.surah)} — آية ${Quran.arabicDigits(a.num)}", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Text(data[a.surah]?.get(a.num).orEmpty(), fontSize = 15.sp, lineHeight = 24.sp, textAlign = if (src.latin) TextAlign.Left else TextAlign.Start)
                Text("— ${src.name}", color = Muted, fontSize = 11.sp)
            }
        }
    }
}
