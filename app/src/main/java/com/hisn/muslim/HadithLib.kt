package com.hisn.muslim

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class HBook(val id: String, val name: String, val author: String, val graded: Boolean)
class HSec(val num: Int, val name: String, val first: Double, val last: Double)
class HRow(val num: String, val text: String, val grades: List<Pair<String, String>>)
class HData(val book: HBook, val sections: List<HSec>, val rows: List<HRow>) {
    val bySec: Map<Int, List<HRow>> by lazy {
        val m = HashMap<Int, MutableList<HRow>>()
        rows.forEach { r ->
            val n = r.num.toDoubleOrNull() ?: return@forEach
            val s = sections.firstOrNull { n >= it.first && n <= it.last } ?: return@forEach
            m.getOrPut(s.num) { mutableListOf() }.add(r)
        }
        m
    }
}

/**
 * المكتبة الحديثية: النصوص من مستودع fawazahmed0/hadith-api (ترخيص عام Unlicense)،
 * بتتحمّل كتاب كتاب على الجهاز وتتقرأ بدون إنترنت. التطبيق لا يعدّل أي نص ولا درجة؛
 * الدرجات بتظهر فقط لو موجودة في بيانات المصدر وبأسماء من حكموا بها.
 */
object HadithLib {
    private const val BASE = "https://raw.githubusercontent.com/fawazahmed0/hadith-api/1/editions"

    val books = listOf(
        HBook("ara-bukhari", "صحيح البخاري", "محمد بن إسماعيل البخاري", false),
        HBook("ara-muslim", "صحيح مسلم", "مسلم بن الحجاج", false),
        HBook("ara-abudawud", "سنن أبي داود", "أبو داود السجستاني", true),
        HBook("ara-tirmidhi", "جامع الترمذي", "محمد بن عيسى الترمذي", true),
        HBook("ara-nasai", "سنن النسائي", "أحمد بن شعيب النسائي", true),
        HBook("ara-ibnmajah", "سنن ابن ماجه", "محمد بن يزيد ابن ماجه", true),
        HBook("ara-malik", "موطأ مالك", "الإمام مالك بن أنس", true),
        HBook("ara-nawawi", "الأربعون النووية", "يحيى بن شرف النووي", false),
        HBook("ara-qudsi", "الأحاديث القدسية الأربعون", "", false)
    )

    private val gradeAr = mapOf(
        "Sahih" to "صحيح", "Hasan" to "حسن", "Daif" to "ضعيف", "Hasan Sahih" to "حسن صحيح",
        "Sahih Lighairihi" to "صحيح لغيره", "Hasan Lighairihi" to "حسن لغيره",
        "Isnaad Hasan" to "إسناده حسن", "Isnaad Sahih" to "إسناده صحيح", "Isnaad Daif" to "إسناده ضعيف",
        "Mawdu" to "موضوع", "Munkar" to "منكر", "Shadh" to "شاذ", "Sahih - Agreed Upon" to "صحيح (متفق عليه)",
        "Mauquf Sahih" to "موقوف صحيح", "Maqtu Sahih" to "مقطوع صحيح", "Mauquf Daif" to "موقوف ضعيف"
    )
    private val nameAr = mapOf(
        "Al-Albani" to "الألباني", "Zubair Ali Zai" to "زبير علي زئي", "Shuaib Al Arnaut" to "شعيب الأرنؤوط",
        "Muhammad Muhyi Al-Din Abdul Hamid" to "محمد محيي الدين عبد الحميد", "Ahmad Muhammad Shakir" to "أحمد شاكر",
        "Muhammad Fouad Abd al-Baqi" to "محمد فؤاد عبد الباقي", "Abu Ghuddah" to "أبو غدة", "Salim al-Hilali" to "سليم الهلالي"
    )

    fun gradeText(g: Pair<String, String>) = "${nameAr[g.first] ?: g.first}: ${gradeAr[g.second] ?: g.second}"

    private fun file(ctx: Context, b: HBook) = File(File(ctx.filesDir, "hlib").apply { mkdirs() }, "${b.id}.json")
    fun has(ctx: Context, b: HBook) = file(ctx, b).let { it.exists() && it.length() > 1000 }

    @Volatile private var cached: HData? = null

    /** بيرجع null لو نجح أو رسالة الخطأ. من thread خلفي. */
    fun download(ctx: Context, b: HBook): String? {
        val target = file(ctx, b)
        val tmp = File(target.parentFile, "${b.id}.part")
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL("$BASE/${b.id}.min.json").openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000; readTimeout = 60_000
                setRequestProperty("User-Agent", "HisnAlMuslim/2.0")
            }
            if (conn.responseCode !in 200..299) return "كود ${conn.responseCode}"
            conn.inputStream.use { i -> FileOutputStream(tmp).use { o -> i.copyTo(o) } }
            val ok = parse(tmp, b)
            if (ok == null || ok.rows.isEmpty()) { tmp.delete(); return "ملف تالف" }
            target.delete()
            if (tmp.renameTo(target)) { cached = ok; null } else "تعذّر الحفظ"
        } catch (e: Exception) {
            tmp.delete(); "تعذّر الاتصال"
        } catch (e: OutOfMemoryError) {
            tmp.delete(); "الذاكرة لا تكفي"
        } finally {
            conn?.disconnect()
        }
    }

    fun delete(ctx: Context, b: HBook) { file(ctx, b).delete(); if (cached?.book === b) cached = null }

    fun load(ctx: Context, b: HBook): HData? {
        cached?.let { if (it.book === b) return it }
        val f = file(ctx, b)
        if (!f.exists()) return null
        val d = try { parse(f, b) } catch (e: Exception) { null } catch (e: OutOfMemoryError) { null }
        if (d != null) cached = d
        return d
    }

    private fun parse(f: File, b: HBook): HData? {
        val secs = ArrayList<HSec>()
        val rows = ArrayList<HRow>()
        JsonReader(InputStreamReader(f.inputStream().buffered(), Charsets.UTF_8)).use { r ->
            val names = HashMap<Int, String>()
            val ranges = HashMap<Int, Pair<Double, Double>>()
            r.beginObject()
            while (r.hasNext()) {
                when (r.nextName()) {
                    "metadata" -> {
                        r.beginObject()
                        while (r.hasNext()) {
                            when (r.nextName()) {
                                "sections" -> {
                                    r.beginObject()
                                    while (r.hasNext()) {
                                        val k = r.nextName().toIntOrNull()
                                        val v = if (r.peek() == JsonToken.STRING) r.nextString() else { r.skipValue(); "" }
                                        if (k != null) names[k] = v
                                    }
                                    r.endObject()
                                }
                                "section_details" -> {
                                    r.beginObject()
                                    while (r.hasNext()) {
                                        val k = r.nextName().toIntOrNull()
                                        var a = 0.0; var z = 0.0
                                        r.beginObject()
                                        while (r.hasNext()) {
                                            when (r.nextName()) {
                                                "hadithnumber_first" -> a = numOf(r)
                                                "hadithnumber_last" -> z = numOf(r)
                                                else -> r.skipValue()
                                            }
                                        }
                                        r.endObject()
                                        if (k != null) ranges[k] = a to z
                                    }
                                    r.endObject()
                                }
                                else -> r.skipValue()
                            }
                        }
                        r.endObject()
                        names.keys.sorted().filter { it > 0 && !names[it].isNullOrBlank() }.forEach { k ->
                            val rg = ranges[k] ?: return@forEach
                            if (rg.second > 0.0) secs.add(HSec(k, names[k] ?: "", rg.first, rg.second))
                        }
                    }
                    "hadiths" -> {
                        r.beginArray()
                        while (r.hasNext()) {
                            var num = ""; var text = ""
                            val grades = ArrayList<Pair<String, String>>()
                            r.beginObject()
                            while (r.hasNext()) {
                                when (r.nextName()) {
                                    "hadithnumber" -> num = r.nextString().removeSuffix(".0")
                                    "text" -> text = r.nextString()
                                    "grades" -> {
                                        r.beginArray()
                                        while (r.hasNext()) {
                                            var n = ""; var g = ""
                                            r.beginObject()
                                            while (r.hasNext()) {
                                                when (r.nextName()) {
                                                    "name" -> n = r.nextString()
                                                    "grade" -> g = r.nextString()
                                                    else -> r.skipValue()
                                                }
                                            }
                                            r.endObject()
                                            if (g.isNotBlank()) grades.add(n to g)
                                        }
                                        r.endArray()
                                    }
                                    else -> r.skipValue()
                                }
                            }
                            r.endObject()
                            if (text.isNotBlank() && num.isNotBlank()) rows.add(HRow(num, text.trim(), grades))
                        }
                        r.endArray()
                    }
                    else -> r.skipValue()
                }
            }
            r.endObject()
        }
        return HData(b, secs, rows)
    }

    private fun numOf(r: JsonReader): Double = if (r.peek() == JsonToken.NUMBER) r.nextDouble() else (r.nextString().toDoubleOrNull() ?: 0.0)

    fun key(b: HBook, r: HRow) = "${b.id}:${r.num}"

    fun shareString(b: HBook, r: HRow): String {
        val g = if (r.grades.isEmpty()) "" else "\n" + r.grades.joinToString("\n") { gradeText(it) }
        return "${r.text}\n\n— ${b.name}، حديث رقم ${r.num}$g"
    }
}

@Composable
fun HadithLibPane(prefs: Prefs) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var book by remember { mutableStateOf<HBook?>(null) }
    var data by remember { mutableStateOf<HData?>(null) }
    var sec by remember { mutableStateOf<HSec?>(null) }
    var q by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf<String?>(null) }
    var err by remember { mutableStateOf("") }
    var tick by remember { mutableIntStateOf(0) }
    var showFav by remember { mutableStateOf(false) }
    var single by remember { mutableStateOf<HRow?>(null) }
    val favs = remember(tick) { prefs.favoriteHadith() }

    fun openBook(b: HBook, jump: String? = null) {
        busy = "جارٍ فتح ${b.name}…"; err = ""
        scope.launch {
            val d = withContext(Dispatchers.IO) { HadithLib.load(ctx, b) }
            busy = null
            if (d == null) err = "تعذّر قراءة الملف؛ أعد التحميل." else {
                book = b; data = d; sec = null; q = ""; showFav = false
                single = if (jump != null) d.rows.firstOrNull { it.num == jump } else null
            }
        }
    }
    fun download(b: HBook) {
        busy = "جارٍ تحميل ${b.name}… (قد يستغرق دقيقة)"; err = ""
        scope.launch {
            val e = withContext(Dispatchers.IO) { HadithLib.download(ctx, b) }
            busy = null; tick++
            if (e != null) err = "تعذّر التحميل: $e"
        }
    }

    val cur = book
    val d = data
    val results = remember(q, data) {
        val query = q.trim()
        val dd = data
        if (dd == null || query.isEmpty()) emptyList()
        else if (query.toIntOrNull() != null) dd.rows.filter { it.num == query }
        else if (query.length < 2) emptyList()
        else { val f = Quran.fold(query); dd.rows.asSequence().filter { Quran.fold(it.text).contains(f) }.take(40).toList() }
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                "النصوص من مستودع hadith-api (ترخيص عام) وتُعرض كما وردت دون أي تعديل. الدرجات تظهر فقط حيث وُجدت في المصدر وبأسماء المحققين، وقد تختلف أحكامهم؛ ارجع لأهل العلم عند الحاجة. أسماء الأبواب كما وردت في المصدر.",
                fontSize = 11.sp, color = Muted
            )
            busy?.let { Text(it, color = Gold) }
            if (err.isNotBlank()) Text(err, color = Color(0xFFFFB4A8), fontSize = 12.sp)
        }
        if (cur == null || d == null) {
            if (showFav) {
                item { TextButton(onClick = { showFav = false }) { Text("‹ الكتب", color = Gold) } }
                if (favs.isEmpty()) item { Text("لا أحاديث مفضلة بعد.", color = Muted) }
                items(favs.toList().sorted()) { k ->
                    val b = HadithLib.books.firstOrNull { k.startsWith(it.id + ":") }
                    if (b != null) Glass(Modifier.clickable { if (HadithLib.has(ctx, b)) openBook(b, k.substringAfter(":")) else err = "حمّل ${b.name} أولًا" }) {
                        Text("${b.name} — حديث رقم ${k.substringAfter(":")}")
                    }
                }
            } else {
                item { TextButton(onClick = { showFav = true }) { Text("★ المفضلة (${favs.count { k -> HadithLib.books.any { k.startsWith(it.id + ":") } }})", color = Gold) } }
                items(HadithLib.books) { b ->
                    val have = remember(tick) { HadithLib.has(ctx, b) }
                    Glass(Modifier.clickable { if (have) openBook(b) else download(b) }) {
                        Text(b.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        if (b.author.isNotBlank()) Text(b.author, fontSize = 12.sp, color = Muted)
                        Text(if (have) "محمّل ✓ — اضغط للفتح" else "اضغط للتحميل", fontSize = 12.sp, color = if (have) Gold else Muted)
                        if (b.graded) Text("مع درجات المحققين", fontSize = 11.sp, color = Muted)
                    }
                }
            }
        } else {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        when {
                            single != null -> single = null
                            sec != null -> sec = null
                            else -> { book = null; data = null }
                        }
                    }) { Text("‹ رجوع", color = Gold) }
                    Text(cur.name, fontWeight = FontWeight.Bold, color = Gold)
                }
            }
            val one = single
            if (one != null) {
                item { HadithCard(prefs, cur, one, favs) { tick++ } }
            } else {
                item {
                    OutlinedTextField(
                        value = q, onValueChange = { q = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        label = { Text("ابحث في ${cur.name} أو اكتب رقم الحديث") }
                    )
                }
                val query = q.trim()
                if (query.isNotEmpty()) {
                    val res = results
                    if (res.isEmpty()) item { Text(if (query.length < 2) "اكتب حرفين على الأقل" else "لا نتائج", color = Muted) }
                    items(res) { r -> HadithCard(prefs, cur, r, favs) { tick++ } }
                } else if (sec == null) {
                    items(d.sections) { s ->
                        val n = d.bySec[s.num]?.size ?: 0
                        Glass(Modifier.clickable { sec = s }) {
                            Text("${s.num}. ${s.name}", fontWeight = FontWeight.Bold)
                            Text("$n حديث", fontSize = 12.sp, color = Muted)
                        }
                    }
                    if (d.sections.isEmpty()) items(d.rows) { r -> HadithCard(prefs, cur, r, favs) { tick++ } }
                } else {
                    val s = sec!!
                    item { Text("${s.num}. ${s.name}", color = Gold, fontWeight = FontWeight.Bold) }
                    items(d.bySec[s.num] ?: emptyList()) { r -> HadithCard(prefs, cur, r, favs) { tick++ } }
                }
            }
        }
    }
}

@Composable
private fun HadithCard(prefs: Prefs, b: HBook, r: HRow, favs: Set<String>, changed: () -> Unit) {
    val ctx = LocalContext.current
    Glass {
        Text(r.text, fontFamily = Serif, fontSize = 19.sp, lineHeight = 32.sp)
        Spacer(Modifier.height(6.dp))
        Text("${b.name} — حديث رقم ${r.num}", fontSize = 12.sp, color = Muted)
        r.grades.forEach { Text(HadithLib.gradeText(it), fontSize = 12.sp, color = Gold) }
        Row {
            val k = HadithLib.key(b, r)
            TextButton(onClick = { prefs.toggleFavoriteHadith(k); changed() }) {
                Text(if (k in favs) "★ في المفضلة" else "☆ مفضلة", color = Gold)
            }
            TextButton(onClick = { shareText(ctx, HadithLib.shareString(b, r)) }) { Text("مشاركة", color = Gold) }
            if (r.text.length < 1500) TextButton(onClick = {
                ShareCard.share(ctx, r.text, "${b.name} — حديث رقم ${r.num}" + (r.grades.firstOrNull()?.let { "\n" + HadithLib.gradeText(it) } ?: ""))
            }) { Text("كصورة", color = Gold) }
        }
    }
}
