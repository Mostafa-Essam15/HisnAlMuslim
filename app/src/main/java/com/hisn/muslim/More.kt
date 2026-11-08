package com.hisn.muslim

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.util.Calendar
import java.util.Locale

private val HIJRI_MONTHS = listOf(
    "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
    "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"
)

/** مناسبات متفق عليها فقط (بدون موالد أو أيام مختلف في فضلها) */
object Occasions {
    fun of(month: Int, day: Int): String? = when {
        month == 1 && day == 1 -> "رأس السنة الهجرية"
        month == 1 && day == 9 -> "تاسوعاء (يُستحب صيامه)"
        month == 1 && day == 10 -> "يوم عاشوراء (يُستحب صيامه)"
        month == 9 && day == 1 -> "أول رمضان"
        month == 9 && day in listOf(21, 23, 25, 27, 29) -> "ليلة وتر من العشر الأواخر (تُلتمس فيها ليلة القدر)"
        month == 10 && day == 1 -> "عيد الفطر"
        month == 12 && day in 1..9 && day != 9 -> "من أيام العشر من ذي الحجة"
        month == 12 && day == 9 -> "يوم عرفة (يُستحب صيامه لغير الحاج)"
        month == 12 && day == 10 -> "عيد الأضحى"
        month == 12 && day in 11..13 -> "أيام التشريق"
        else -> null
    }
}

private fun hijriOf(p: Prefs, d: LocalDate): HijrahDate = HijrahDate.from(d.plusDays(p.hijriAdj.toLong()))

@Composable
fun MoreScreen(prefs: Prefs, onOpenQuran: (Int) -> Unit = {}) {
    var sec by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GoldChip("التقويم الهجري", sec == 0) { sec = 0 }
            GoldChip("رمضان", sec == 1) { sec = 1 }
            GoldChip("إحصاءات العبادة", sec == 2) { sec = 2 }
            GoldChip("نسخ احتياطي", sec == 3) { sec = 3 }
            GoldChip("بحث", sec == 4) { sec = 4 }
            GoldChip("الأحاديث (تجريبي)", sec == 5) { sec = 5 }
            GoldChip("الأدعية", sec == 6) { sec = 6 }
        }
        when (sec) {
            0 -> CalendarPane(prefs)
            1 -> RamadanPane(prefs)
            2 -> StatsPane(prefs)
            4 -> SearchPane(prefs, onOpenQuran)
            5 -> HadithPane(prefs)
            6 -> DuasPane(prefs)
            else -> BackupPane(prefs)
        }
    }
}

// ───────────── الأحاديث ─────────────

@Composable
private fun HadithPane(prefs: Prefs) {
    val ctx = LocalContext.current
    var cats by remember { mutableStateOf<List<HCategory>?>(null) }
    var cat by remember { mutableStateOf<HCategory?>(null) }
    var items by remember { mutableStateOf<List<HItem>>(emptyList()) }
    var page by remember { mutableIntStateOf(1) }
    var open by remember { mutableStateOf<HDetail?>(null) }
    var err by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var favTick by remember { mutableIntStateOf(0) }
    var showFav by remember { mutableStateOf(false) }
    val favs = remember(favTick) { prefs.favoriteHadith() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    fun <T> load(block: () -> T, ok: (T) -> Unit) {
        busy = true; err = ""
        scope.launch {
            try { ok(kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { block() }) }
            catch (e: Exception) { err = "تعذّر التحميل: تأكد من الاتصال بالإنترنت (${e.message ?: ""})" }
            busy = false
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { load({ HadithApi.categories(ctx) }) { cats = it } }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                "الأحاديث تُعرض كما وردت من موسوعة الأحاديث النبوية (hadeethenc.com) بدرجتها وتخريجها، ولا يعدّل التطبيق أي نص. الميزة تجريبية وتحتاج إنترنت أول مرة.",
                fontSize = 11.sp, color = Muted
            )
            if (busy) Text("جارٍ التحميل…", color = Gold)
            if (err.isNotBlank()) Text(err, color = Color(0xFFFFB4A8), fontSize = 12.sp)
        }
        val d = open
        if (d != null) {
            item {
                Glass {
                    TextButton(onClick = { open = null }) { Text("‹ رجوع", color = Gold) }
                    if (d.title.isNotBlank()) Text(d.title, color = Gold, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(d.text, fontFamily = Serif, fontSize = 19.sp, lineHeight = 32.sp)
                    if (d.attribution.isNotBlank()) Text(d.attribution, fontSize = 13.sp, color = Muted)
                    if (d.grade.isNotBlank()) Text("الدرجة: ${d.grade}", fontSize = 13.sp, color = Gold)
                    if (d.reference.isNotBlank()) Text("التخريج: ${d.reference}", fontSize = 12.sp, color = Muted)
                    if (d.explanation.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("الشرح:", fontWeight = FontWeight.Bold)
                        Text(d.explanation, fontSize = 14.sp, lineHeight = 24.sp)
                    }
                    TextButton(onClick = { prefs.toggleFavoriteHadith(d.id); favTick++ }) {
                        Text(if (d.id in favs) "★ في المفضلة" else "☆ إضافة للمفضلة", color = Gold)
                    }
                }
            }
        } else if (showFav) {
            item { TextButton(onClick = { showFav = false }) { Text("‹ الأقسام", color = Gold) } }
            if (favs.isEmpty()) item { Text("لا أحاديث مفضلة بعد.", color = Muted) }
            items(favs.toList()) { id ->
                Glass(Modifier.clickable { load({ HadithApi.detail(ctx, id) }) { open = it } }) { Text("حديث رقم $id (اضغط للفتح)") }
            }
        } else if (cat == null) {
            item { TextButton(onClick = { showFav = true }) { Text("★ المفضلة (${favs.size})", color = Gold) } }
            items(cats ?: emptyList()) { c ->
                Glass(Modifier.clickable {
                    cat = c; page = 1
                    load({ HadithApi.list(ctx, c.id, 1) }) { items = it }
                }) {
                    Text(c.title, fontWeight = FontWeight.Bold)
                    if (c.count > 0) Text("${c.count} حديث", fontSize = 12.sp, color = Muted)
                }
            }
        } else {
            item { TextButton(onClick = { cat = null; items = emptyList() }) { Text("‹ الأقسام", color = Gold) } }
            items(items) { h ->
                Glass(Modifier.clickable { load({ HadithApi.detail(ctx, h.id) }) { open = it } }) {
                    Text(h.title, fontFamily = Serif, fontSize = 17.sp, lineHeight = 28.sp)
                }
            }
            if (items.size >= 20) item {
                TextButton(onClick = {
                    val c = cat ?: return@TextButton
                    page++
                    load({ HadithApi.list(ctx, c.id, page) }) { items = items + it }
                }) { Text("تحميل المزيد", color = Gold) }
            }
        }
    }
}

// ───────────── البحث العام ─────────────

@Composable
private fun SearchPane(prefs: Prefs, onOpenQuran: (Int) -> Unit) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    val query = q.trim()
    val azkar = remember(query) {
        if (query.length < 2) emptyList()
        else Content.azkar.filter { it.text.contains(query) || it.source.contains(query) }.take(30)
    }
    val tips = remember(query) {
        if (query.length < 2) emptyList()
        else Prayer.values().flatMap { pr -> (0 until 8).map { Content.tip(pr, it) } }
            .distinctBy { it.text }.filter { it.text.contains(query) || it.source.contains(query) }.take(20)
    }
    val hadiths = remember(query) { if (query.length < 2) emptyList() else HadithApi.searchCached(ctx, query) }
    var ayat by remember { mutableStateOf<List<Ayah>>(emptyList()) }
    var quranReady by remember { mutableStateOf(Quran.loaded) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!Quran.loaded) quranReady = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Quran.load(ctx) }
    }
    androidx.compose.runtime.LaunchedEffect(query, quranReady) {
        ayat = if (quranReady && query.length >= 2) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { Quran.search(query, 30) } else emptyList()
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            androidx.compose.material3.OutlinedTextField(
                value = q, onValueChange = { q = it }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), label = { Text("ابحث في المصحف والأذكار والأحاديث المحفوظة") }
            )
            if (!quranReady) Text("المصحف غير محمّل بعد؛ حمّله من تبويب المصحف ليظهر في البحث.", fontSize = 11.sp, color = Muted)
        }
        if (query.length >= 2 && azkar.isEmpty() && tips.isEmpty() && ayat.isEmpty() && hadiths.isEmpty()) {
            item { Text("لا نتائج", color = Muted) }
        }
        if (ayat.isNotEmpty()) {
            item { Text("المصحف (${ayat.size})", color = Gold, fontWeight = FontWeight.Bold) }
            items(ayat) { a ->
                Glass(Modifier.clickable { onOpenQuran(a.page) }) {
                    Text(a.text, fontFamily = Serif, fontSize = 18.sp, lineHeight = 30.sp)
                    Text("${Quran.surahName(a.surah)} • آية ${Quran.arabicDigits(a.num)} • صفحة ${Quran.arabicDigits(a.page)} (اضغط للفتح)", fontSize = 12.sp, color = Muted)
                }
            }
        }
        if (azkar.isNotEmpty()) {
            item { Text("الأذكار (${azkar.size})", color = Gold, fontWeight = FontWeight.Bold) }
            items(azkar) { z ->
                Glass {
                    Text(z.text, fontFamily = Serif, fontSize = 17.sp, lineHeight = 28.sp)
                    Text("${z.cats.joinToString("، ")} • ${z.source}", fontSize = 12.sp, color = Muted)
                }
            }
        }
        if (hadiths.isNotEmpty()) {
            item { Text("الأحاديث المحفوظة (${hadiths.size})", color = Gold, fontWeight = FontWeight.Bold) }
            items(hadiths) { d ->
                Glass {
                    Text(d.text, fontFamily = Serif, fontSize = 17.sp, lineHeight = 28.sp)
                    if (d.attribution.isNotBlank()) Text(d.attribution, fontSize = 12.sp, color = Muted)
                    if (d.grade.isNotBlank()) Text("الدرجة: ${d.grade}", fontSize = 12.sp, color = Gold)
                    if (d.reference.isNotBlank()) Text("التخريج: ${d.reference}", fontSize = 11.sp, color = Muted)
                }
            }
        }
        if (tips.isNotEmpty()) {
            item { Text("الفوائد والأحاديث (${tips.size})", color = Gold, fontWeight = FontWeight.Bold) }
            items(tips) { t ->
                Glass {
                    Text(t.text, fontFamily = Serif, fontSize = 17.sp, lineHeight = 28.sp)
                    Text("${t.kind} • ${t.source}", fontSize = 12.sp, color = Muted)
                }
            }
        }
    }
}

// ───────────── التقويم ─────────────

@Composable
private fun CalendarPane(prefs: Prefs) {
    var offset by remember { mutableIntStateOf(0) }
    val today = remember { hijriOf(prefs, LocalDate.now()) }
    val first = remember(offset) { today.with(ChronoField.DAY_OF_MONTH, 1).plus(offset.toLong(), java.time.temporal.ChronoUnit.MONTHS) }
    val month = first.get(ChronoField.MONTH_OF_YEAR)
    val year = first.get(ChronoField.YEAR)
    val len = first.lengthOfMonth()
    val startIdx = (LocalDate.from(first).dayOfWeek.value + 1) % 7 // السبت = 0
    val dayNames = listOf("س", "ح", "ن", "ث", "ر", "خ", "ج")

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Glass {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { offset-- }) { Text("‹ السابق", color = Gold) }
                    Text("${HIJRI_MONTHS[month - 1]} ${Quran.arabicDigits(year)} هـ", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    TextButton(onClick = { offset++ }) { Text("التالي ›", color = Gold) }
                }
                Row(Modifier.fillMaxWidth()) {
                    dayNames.forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, color = Muted, fontSize = 12.sp) }
                }
                val cells = startIdx + len
                val rows = (cells + 6) / 7
                for (r in 0 until rows) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        for (c in 0 until 7) {
                            val d = r * 7 + c - startIdx + 1
                            val isToday = offset == 0 && d == today.get(ChronoField.DAY_OF_MONTH)
                            Box(
                                Modifier.weight(1f).height(44.dp).padding(2.dp)
                                    .background(
                                        when {
                                            isToday -> Gold
                                            d in 1..len && Occasions.of(month, d) != null -> Color(0x33E8C766)
                                            else -> Color.Transparent
                                        }, RoundedCornerShape(10.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (d in 1..len) {
                                    val g = LocalDate.from(first.plus((d - 1).toLong(), java.time.temporal.ChronoUnit.DAYS))
                                        .minusDays(prefs.hijriAdj.toLong())
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(Quran.arabicDigits(d), fontWeight = FontWeight.Bold, color = if (isToday) Color.Black else Color.White, fontSize = 15.sp)
                                        Text("${g.dayOfMonth}", fontSize = 9.sp, color = if (isToday) Color.Black else Muted)
                                    }
                                }
                            }
                        }
                    }
                }
                Text("الرقم الصغير = اليوم الميلادي. التاريخ بتقويم أم القرى مع تعديلك (${prefs.hijriAdj}) من الإعدادات؛ قد يختلف يومًا عن رؤية الهلال في بلدك.", fontSize = 11.sp, color = Muted)
            }
        }
        val occ = (1..len).mapNotNull { d -> Occasions.of(month, d)?.let { d to it } }
        if (occ.isNotEmpty()) item {
            Glass {
                SectionTitle("مناسبات هذا الشهر")
                occ.forEach { (d, t) -> Text("${Quran.arabicDigits(d)} ${HIJRI_MONTHS[month - 1]} — $t", fontSize = 14.sp) }
            }
        }
    }
}

// ───────────── رمضان ─────────────

@Composable
private fun RamadanPane(prefs: Prefs) {
    var tick by remember { mutableIntStateOf(0) }
    val todayG = LocalDate.now()
    val h = hijriOf(prefs, todayG)
    val isRam = h.get(ChronoField.MONTH_OF_YEAR) == 9
    val day = prefs.dayKey()
    val t = remember { Engine.times(prefs) }
    val fmt = SimpleDateFormat("h:mm a", Locale("ar"))
    val daysTo = remember {
        (1..400).firstOrNull { i ->
            val x = hijriOf(prefs, todayG.plusDays(i.toLong()))
            x.get(ChronoField.MONTH_OF_YEAR) == 9 && x.get(ChronoField.DAY_OF_MONTH) == 1
        }
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Glass {
                SectionTitle("رمضان")
                if (isRam) {
                    val d = h.get(ChronoField.DAY_OF_MONTH)
                    Text("اليوم ${Quran.arabicDigits(d)} من رمضان", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Gold)
                    if (d >= 21) Text("نحن في العشر الأواخر؛ تُلتمس ليلة القدر في الليالي الوتر.", fontSize = 13.sp, color = Muted)
                } else if (daysTo != null) {
                    Text("باقٍ تقريبًا $daysTo يوم على رمضان", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Gold)
                    Text("(حسب التقويم؛ والأمر يتوقف على ثبوت رؤية الهلال)", fontSize = 11.sp, color = Muted)
                }
            }
        }
        item {
            Glass {
                SectionTitle("اليوم")
                val suhoor = java.util.Date(t.fajr.time - prefs.suhoorBeforeMin * 60_000L)
                Text("السحور (قبل الفجر بـ ${prefs.suhoorBeforeMin} دقيقة): ${fmt.format(suhoor)}")
                Text("الإمساك/الفجر: ${fmt.format(t.fajr)}")
                Text("الإفطار/المغرب: ${fmt.format(t.maghrib)}")
                Text("مواقيت محسوبة لموقعك الحالي؛ احتط في الإمساك بحسب تقويم بلدك.", fontSize = 11.sp, color = Muted)
            }
        }
        item {
            Glass {
                SectionTitle("عبادات اليوم")
                val charity = remember(tick) { prefs.charityDone(day) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = charity, onCheckedChange = { prefs.setCharityDone(day, it); tick++ },
                        colors = CheckboxDefaults.colors(checkedColor = Gold, checkmarkColor = Color.Black)
                    )
                    Text("صدقة اليوم")
                }
                Text("قراءة القرآن اليوم: ${prefs.pagesReadOn(day).size} صفحة", color = Muted, fontSize = 13.sp)
                Text("التسبيح اليوم: ${prefs.tasbihTotal(day)}", color = Muted, fontSize = 13.sp)
            }
        }
        item {
            Glass {
                SectionTitle("دعاء الإفطار")
                Text(
                    "ذهب الظمأ وابتلّت العروق وثبت الأجر إن شاء الله",
                    fontFamily = Serif, fontSize = 18.sp
                )
                Text("رواه أبو داود (وحسّنه الألباني)", fontSize = 12.sp, color = Muted)
            }
        }
    }
}

// ───────────── الإحصاءات ─────────────

@Composable
private fun StatsPane(prefs: Prefs) {
    val days = remember { prefs.lastDays(14).reversed() }
    val d30 = remember { prefs.lastDays(30) }
    val fard30 = d30.sumOf { prefs.fardCount(it) }
    val qada30 = d30.sumOf { prefs.qadaCount(it) }
    val quran7 = prefs.lastDays(7).sumOf { prefs.pagesReadOn(it).size }
    val tasbih7 = prefs.lastDays(7).sumOf { prefs.tasbihTotal(it) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Glass {
                SectionTitle("الفروض في آخر ١٤ يومًا")
                Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                    days.forEach { dk ->
                        val n = prefs.fardCount(dk)
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.fillMaxWidth().height((8 + n * 14).dp)
                                    .background(if (n == 5) Gold else Color(0x88E8C766), RoundedCornerShape(5.dp))
                            )
                            Text("$n", fontSize = 10.sp, color = Muted)
                        }
                    }
                }
                Text("الذهبي = ٥ فروض كاملة", fontSize = 11.sp, color = Muted)
            }
        }
        item {
            Glass {
                SectionTitle("ملخص")
                Text("فروض مؤداة (٣٠ يومًا): $fard30 من ١٥٠")
                Text("صلوات مسجّلة قضاءً (٣٠ يومًا): $qada30")
                Text("السلسلة الحالية: ${prefs.streak()} يوم")
                Text("صفحات القرآن (٧ أيام): $quran7")
                Text("التسبيح (٧ أيام): $tasbih7")
                Text("الإحصاءات من تسجيلك أنت فقط ولا تُقيّم أحدًا؛ المقصود المتابعة والتشجيع.", fontSize = 11.sp, color = Muted)
            }
        }
    }
}

// ───────────── النسخ الاحتياطي ─────────────

object Backup {
    fun export(ctx: Context): String {
        val all = Prefs(ctx).sp.all
        val o = JSONObject()
        for ((k, v) in all) {
            val e = JSONObject()
            when (v) {
                is String -> e.put("t", "s").put("v", v)
                is Int -> e.put("t", "i").put("v", v)
                is Long -> e.put("t", "l").put("v", v)
                is Boolean -> e.put("t", "b").put("v", v)
                is Float -> e.put("t", "f").put("v", v.toDouble())
                is Set<*> -> e.put("t", "set").put("v", JSONArray(v.map { it.toString() }))
                else -> continue
            }
            o.put(k, e)
        }
        return JSONObject().put("app", "hisn").put("version", 1).put("data", o).toString()
    }

    /** يرجّع عدد المفاتيح المستعادة أو -1 لو الملف غير صالح */
    fun import(ctx: Context, json: String): Int {
        return try {
            val root = JSONObject(json)
            if (root.optString("app") != "hisn") return -1
            val data = root.getJSONObject("data")
            val ed = Prefs(ctx).sp.edit()
            var n = 0
            for (k in data.keys()) {
                val e = data.getJSONObject(k)
                when (e.getString("t")) {
                    "s" -> ed.putString(k, e.getString("v"))
                    "i" -> ed.putInt(k, e.getInt("v"))
                    "l" -> ed.putLong(k, e.getLong("v"))
                    "b" -> ed.putBoolean(k, e.getBoolean("v"))
                    "f" -> ed.putFloat(k, e.getDouble("v").toFloat())
                    "set" -> {
                        val a = e.getJSONArray("v")
                        ed.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet())
                    }
                    else -> continue
                }
                n++
            }
            ed.apply()
            n
        } catch (e: Exception) {
            -1
        }
    }
}

@Composable
private fun BackupPane(prefs: Prefs) {
    val ctx = LocalContext.current
    var msg by remember { mutableStateOf("") }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) {
            msg = try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(Backup.export(ctx).toByteArray()) }
                "تم حفظ النسخة الاحتياطية."
            } catch (e: Exception) { "تعذّر حفظ الملف." }
        }
    }
    val loader = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val text = try { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } } catch (e: Exception) { null }
            val n = if (text == null) -1 else Backup.import(ctx, text)
            msg = if (n < 0) "الملف غير صالح." else {
                Reminders.scheduleAll(ctx); refreshTick.intValue++
                "تمت استعادة $n إعدادًا. أعد فتح التطبيق لتحديث كل الشاشات."
            }
        }
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Glass {
                SectionTitle("نسخ احتياطي واستعادة")
                Text(
                    "يحفظ كل بياناتك المحلية (تسجيل الصلوات، الختمة، العلامات، التسبيح، الإعدادات والموقع) في ملف تحتفظ به أنت. لا يُرسل شيء لأي خادم. ملفات الأذان والمصحف المحمّلة لا تدخل في النسخة وتُحمَّل من جديد.",
                    fontSize = 13.sp, color = Muted
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { saver.launch("hisn-backup-${SimpleDateFormat("yyyyMMdd", Locale.US).format(Calendar.getInstance().time)}.json") },
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black)
                    ) { Text("تصدير") }
                    OutlinedButton(onClick = { loader.launch(arrayOf("application/json", "*/*")) }) { Text("استعادة من ملف", color = Gold) }
                }
                if (msg.isNotEmpty()) Text(msg, color = Gold, fontSize = 13.sp)
            }
        }
    }
}
