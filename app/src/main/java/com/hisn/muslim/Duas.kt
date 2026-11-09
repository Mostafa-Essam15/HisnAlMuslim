package com.hisn.muslim

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * دعاء: إما نص نبوي مكتوب مع مصدره، أو آية قرآنية بالمرجع (سورة/آية) والنص بيتقرأ من المصحف المحمّل
 * عشان ما يتكتبش نص قرآني باليد.
 */
class Dua(
    val cat: String,
    val title: String,
    val text: String?,
    val surah: Int = 0,
    val ayah: Int = 0,
    val source: String
)

object Duas {
    const val QURAN = "أدعية قرآنية"
    const val DISTRESS = "الكرب والهم"
    const val RIZQ = "الرزق"
    const val SHIFA = "الشفاء"
    const val TRAVEL = "السفر"
    const val ISTIKHARA = "الاستخارة"
    const val OCCASIONS = "المناسبات"
    const val NABAWI = "أدعية نبوية"

    val cats = listOf(QURAN, NABAWI, DISTRESS, RIZQ, SHIFA, TRAVEL, ISTIKHARA, OCCASIONS)

    private fun q(title: String, s: Int, a: Int, cat: String = QURAN) = Dua(cat, title, null, s, a, "")

    /** مصدر الدعاء؛ للآيات: اسم السورة ورقم الآية */
    fun sourceOf(d: Dua): String =
        if (d.surah > 0) {
            val n = Quran.surahName(d.surah)
            (if (n.isNotBlank()) n else "سورة رقم ${d.surah}") + " — آية ${d.ayah}"
        } else d.source

    val all: List<Dua> = listOf(
        q("خير الدنيا والآخرة", 2, 201),
        q("ختام سورة البقرة", 2, 286),
        q("الثبات على الهداية", 3, 8),
        q("المغفرة والثبات", 3, 147),
        q("التوبة (دعاء آدم)", 7, 23),
        q("إقامة الصلاة والذرية", 14, 40),
        q("الرحمة بالوالدين", 17, 24),
        q("شرح الصدر (دعاء موسى)", 20, 25),
        q("زيادة العلم", 20, 114),
        q("دعاء يونس عليه السلام في الكرب", 21, 87, DISTRESS),
        q("قرة الأعين من الأزواج والذرية", 25, 74),
        q("الافتقار إلى الله في الرزق (موسى)", 28, 24, RIZQ),
        q("الدعاء للإخوة السابقين", 59, 10),
        q("تسخير وسيلة السفر", 43, 13, TRAVEL),
        q("النزول في مكان مبارك (دعاء نوح)", 23, 29, TRAVEL),
        Dua(
            NABAWI, "الهمّ والحزن",
            "اللَّهُمَّ إِنِّي أَعُوذُ بِكَ مِنَ الْهَمِّ وَالْحَزَنِ، وَالْعَجْزِ وَالْكَسَلِ، وَالْبُخْلِ وَالْجُبْنِ، وَضَلَعِ الدَّيْنِ وَغَلَبَةِ الرِّجَالِ",
            source = "رواه البخاري"
        ),
        Dua(
            DISTRESS, "دعاء الكرب",
            "لَا إِلَهَ إِلَّا اللَّهُ الْعَظِيمُ الْحَلِيمُ، لَا إِلَهَ إِلَّا اللَّهُ رَبُّ الْعَرْشِ الْعَظِيمِ، لَا إِلَهَ إِلَّا اللَّهُ رَبُّ السَّمَاوَاتِ وَرَبُّ الْأَرْضِ وَرَبُّ الْعَرْشِ الْكَرِيمِ",
            source = "متفق عليه"
        ),
        Dua(
            DISTRESS, "الاستغاثة وإصلاح الشأن",
            "اللَّهُمَّ رَحْمَتَكَ أَرْجُو فَلَا تَكِلْنِي إِلَى نَفْسِي طَرْفَةَ عَيْنٍ، وَأَصْلِحْ لِي شَأْنِي كُلَّهُ، لَا إِلَهَ إِلَّا أَنْتَ",
            source = "رواه أبو داود"
        ),
        Dua(
            RIZQ, "الكفاية بالحلال",
            "اللَّهُمَّ اكْفِنِي بِحَلَالِكَ عَنْ حَرَامِكَ، وَأَغْنِنِي بِفَضْلِكَ عَمَّنْ سِوَاكَ",
            source = "رواه الترمذي"
        ),
        Dua(
            SHIFA, "الرقية للمريض",
            "أَذْهِبِ الْبَاسَ رَبَّ النَّاسِ، وَاشْفِ أَنْتَ الشَّافِي، لَا شِفَاءَ إِلَّا شِفَاؤُكَ، شِفَاءً لَا يُغَادِرُ سَقَمًا",
            source = "متفق عليه"
        ),
        Dua(
            SHIFA, "لمن يجد ألمًا في جسده",
            "يضع يده على موضع الألم من جسده ويقول: «بِسْمِ اللَّهِ» ثلاثًا، ثم يقول سبع مرات: «أَعُوذُ بِاللَّهِ وَقُدْرَتِهِ مِنْ شَرِّ مَا أَجِدُ وَأُحَاذِرُ»",
            source = "رواه مسلم (وفي لفظه: «بعزة الله وقدرته»)"
        ),
        Dua(
            ISTIKHARA, "دعاء الاستخارة",
            "اللَّهُمَّ إِنِّي أَسْتَخِيرُكَ بِعِلْمِكَ، وَأَسْتَقْدِرُكَ بِقُدْرَتِكَ، وَأَسْأَلُكَ مِنْ فَضْلِكَ الْعَظِيمِ، فَإِنَّكَ تَقْدِرُ وَلَا أَقْدِرُ، وَتَعْلَمُ وَلَا أَعْلَمُ، وَأَنْتَ عَلَّامُ الْغُيُوبِ، اللَّهُمَّ إِنْ كُنْتَ تَعْلَمُ أَنَّ هَذَا الْأَمْرَ (ويسمّي حاجته) خَيْرٌ لِي فِي دِينِي وَمَعَاشِي وَعَاقِبَةِ أَمْرِي، فَاقْدُرْهُ لِي وَيَسِّرْهُ لِي ثُمَّ بَارِكْ لِي فِيهِ، وَإِنْ كُنْتَ تَعْلَمُ أَنَّ هَذَا الْأَمْرَ شَرٌّ لِي فِي دِينِي وَمَعَاشِي وَعَاقِبَةِ أَمْرِي، فَاصْرِفْهُ عَنِّي وَاصْرِفْنِي عَنْهُ، وَاقْدُرْ لِيَ الْخَيْرَ حَيْثُ كَانَ ثُمَّ أَرْضِنِي بِهِ",
            source = "رواه البخاري (يُقال بعد ركعتين من غير الفريضة)"
        ),
        Dua(
            OCCASIONS, "ليلة القدر",
            "اللَّهُمَّ إِنَّكَ عَفُوٌّ تُحِبُّ الْعَفْوَ فَاعْفُ عَنِّي",
            source = "رواه الترمذي وابن ماجه"
        ),
        Dua(
            OCCASIONS, "عند الإفطار",
            "ذَهَبَ الظَّمَأُ وَابْتَلَّتِ الْعُرُوقُ وَثَبَتَ الْأَجْرُ إِنْ شَاءَ اللَّهُ",
            source = "رواه أبو داود"
        )
    )
}

private fun copyText(ctx: Context, s: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("dua", s))
}

fun shareText(ctx: Context, s: String) {
    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, s)
    ctx.startActivity(Intent.createChooser(i, "مشاركة").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun Prefs.favDuas(): Set<String> = sp.getStringSet("favDuas", emptySet())?.toSet() ?: emptySet()
private fun Prefs.toggleFavDua(id: String) {
    val s = favDuas().toMutableSet()
    if (!s.add(id)) s.remove(id)
    sp.edit().putStringSet("favDuas", s).apply()
}

@Composable
fun DuasPane(prefs: Prefs) {
    val ctx = LocalContext.current
    var cat by remember { mutableStateOf(Duas.cats.first()) }
    var q by remember { mutableStateOf("") }
    var favTick by remember { mutableIntStateOf(0) }
    var ready by remember { mutableStateOf(Quran.loaded) }
    LaunchedEffect(Unit) {
        if (!Quran.loaded) ready = withContext(Dispatchers.IO) { Quran.load(ctx) }
    }
    val favs = remember(favTick) { prefs.favDuas() }
    val query = q.trim()

    fun textOf(d: Dua): String? =
        d.text ?: if (ready) Quran.ayahs.firstOrNull { it.surah == d.surah && it.num == d.ayah }?.text else null

    val shown = Duas.all.withIndex().filter { (i, d) ->
        (cat == "المفضلة" && i.toString() in favs || cat == d.cat) &&
            (query.length < 2 || d.title.contains(query) || (d.text ?: "").contains(query) || Duas.sourceOf(d).contains(query))
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            (Duas.cats + "المفضلة").forEach { c -> GoldChip(c, cat == c) { cat = c } }
        }
        OutlinedTextField(
            value = q, onValueChange = { q = it }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), label = { Text("بحث في الأدعية") }
        )
        if (!ready) Text(
            "الأدعية القرآنية تُعرض من نص المصحف؛ حمّله من تبويب المصحف لتظهر نصوصها.",
            Modifier.padding(horizontal = 16.dp), fontSize = 11.sp, color = Muted
        )
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (shown.isEmpty()) item { Text(if (cat == "المفضلة") "لا أدعية مفضلة بعد." else "لا نتائج", color = Muted) }
            items(shown) { (idx, d) ->
                val body = textOf(d)
                Glass {
                    Text(d.title, color = Gold, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    if (body != null) Text(body, fontFamily = Serif, fontSize = 19.sp, lineHeight = 32.sp)
                    else Text("(نص الآية يظهر بعد تحميل المصحف)", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(Duas.sourceOf(d), fontSize = 12.sp, color = Muted)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { prefs.toggleFavDua(idx.toString()); favTick++ }) {
                            Text(if (idx.toString() in favs) "★" else "☆", color = Gold, fontSize = 20.sp)
                        }
                        if (body != null) {
                            TextButton(onClick = { copyText(ctx, "$body\n— ${Duas.sourceOf(d)}") }) { Text("نسخ", color = Gold) }
                            TextButton(onClick = { shareText(ctx, "$body\n— ${Duas.sourceOf(d)}") }) { Text("مشاركة", color = Gold) }
                            TextButton(onClick = { ShareCard.share(ctx, body, Duas.sourceOf(d)) }) { Text("كصورة", color = Gold) }
                        }
                    }
                }
            }
        }
    }
}
