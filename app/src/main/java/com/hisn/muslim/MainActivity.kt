package com.hisn.muslim

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import android.media.MediaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

/** بيتغير لما الموقع أو الإعدادات تتحدّث عشان الشاشات تتحسب من جديد */
val refreshTick = mutableIntStateOf(0)
val Gold = Color(0xFFD4AF37)

class Bg(val name: String, val brush: Brush)

val backgrounds = listOf(
    Bg("ليل المسجد", Brush.verticalGradient(listOf(Color(0xFF0B1D3A), Color(0xFF123B5E), Color(0xFF0B1D3A)))),
    Bg("فجر", Brush.verticalGradient(listOf(Color(0xFF1B1B3A), Color(0xFF693668), Color(0xFFF1A661)))),
    Bg("الواحة", Brush.verticalGradient(listOf(Color(0xFF053B2B), Color(0xFF0B6E4F), Color(0xFF053B2B)))),
    Bg("ذهب وأسود", Brush.verticalGradient(listOf(Color(0xFF000000), Color(0xFF2B2200), Color(0xFF000000)))),
    Bg("سماء الصحراء", Brush.verticalGradient(listOf(Color(0xFF3E2C1C), Color(0xFF8C5A2B), Color(0xFF3E2C1C))))
)

class MainActivity : ComponentActivity() {
    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { fetchLocation() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val req = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 33) req.add(Manifest.permission.POST_NOTIFICATIONS)
        permLauncher.launch(req.toTypedArray())
        Scheduler.schedule(this)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { App() }
        }
    }

    @SuppressLint("MissingPermission")
    fun fetchLocation() {
        val ok = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!ok) return
        LocationServices.getFusedLocationProviderClient(this)
            .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    val p = Prefs(this)
                    p.lat = loc.latitude
                    p.lng = loc.longitude
                    p.hasLocation = true
                    Scheduler.schedule(this)
                    refreshTick.intValue++
                }
            }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var tab by remember { mutableIntStateOf(0) }
    var bg by remember { mutableIntStateOf(prefs.bg) }
    val tabs = listOf("🕌" to "الصلاة", "🏆" to "التحديات", "📿" to "الأذكار", "🔢" to "التسبيح", "⚙️" to "الإعدادات")

    MaterialTheme(colorScheme = darkColorScheme(primary = Gold, onPrimary = Color.Black)) {
        Box(Modifier.fillMaxSize().background(backgrounds[bg].brush)) {
            Scaffold(
                containerColor = Color.Transparent,
                contentColor = Color.White,
                bottomBar = {
                    NavigationBar(containerColor = Color(0x99000000)) {
                        tabs.forEachIndexed { i, t ->
                            NavigationBarItem(
                                selected = tab == i,
                                onClick = { tab = i },
                                icon = { Text(t.first, fontSize = 20.sp) },
                                label = { Text(t.second, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            ) { pad ->
                Box(Modifier.padding(pad).fillMaxSize()) {
                    when (tab) {
                        0 -> HomeScreen(prefs)
                        1 -> ChallengesScreen(prefs)
                        2 -> AzkarScreen()
                        3 -> TasbihScreen(prefs)
                        else -> SettingsScreen(prefs, bg) { bg = it; prefs.bg = it }
                    }
                }
            }
        }
    }
}

@Composable
fun Glass(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0x66000000), contentColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun CheckRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onToggle() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Text(label)
    }
}

// ───────────────────────── الصلاة ─────────────────────────

@Composable
fun HomeScreen(prefs: Prefs) {
    val tick = refreshTick.intValue
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
    val fmt = remember { SimpleDateFormat("hh:mm a", Locale("ar")) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Glass {
                if (next != null) {
                    val diff = ((next.second.time - now) / 1000).coerceAtLeast(0)
                    Text("الصلاة القادمة: ${next.first.ar}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Gold)
                    Text(
                        String.format(Locale.US, "%02d:%02d:%02d", diff / 3600, (diff % 3600) / 60, diff % 60),
                        fontSize = 40.sp, fontWeight = FontWeight.Bold
                    )
                    Text("الساعة ${fmt.format(next.second)}")
                }
                if (!prefs.hasLocation) {
                    Spacer(Modifier.height(8.dp))
                    Text("المواقيت حاليًا على موقع افتراضي. اسمح بالوصول للموقع من الإعدادات لتحديد مواقيتك بدقة.", fontSize = 12.sp)
                }
            }
        }
        item {
            val pr = next?.first ?: Prayer.FAJR
            val tip = remember(pr, day) { Content.tip(pr) }
            Glass {
                Text("${tip.kind} • قبل ${pr.ar}", color = Gold, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(tip.text, fontSize = 17.sp, lineHeight = 28.sp)
                Spacer(Modifier.height(4.dp))
                Text(tip.source, fontSize = 12.sp, color = Color(0xFFCCCCCC))
            }
        }
        items(Prayer.values().toList()) { pr ->
            Glass {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(pr.ar, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(fmt.format(Engine.timeOf(times, pr)), fontSize = 20.sp, color = Gold)
                }
                CheckRow("صليت الفرض", pr.fardKey in done) {
                    prefs.toggle(day, pr.fardKey); done = prefs.done(day)
                }
                pr.sunnah.forEach { (key, label) ->
                    CheckRow(label, key in done) {
                        prefs.toggle(day, key); done = prefs.done(day)
                    }
                }
            }
        }
        item {
            Text(
                "الشروق: ${fmt.format(times.sunrise)}",
                Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = Color(0xFFDDDDDD)
            )
        }
    }
}

// ───────────────────────── التحديات ─────────────────────────

@Composable
fun ChallengesScreen(prefs: Prefs) {
    val day = prefs.dayKey()
    val streak = prefs.streak()
    data class Ch(val title: String, val desc: String, val cur: Int, val goal: Int)

    val list = listOf(
        Ch("فروض اليوم", "صلِّ الفروض الخمسة اليوم", prefs.fardCount(day), 5),
        Ch("سنن اليوم", "حافظ على السنن الرواتب والوتر اليوم", prefs.sunnahCount(day), prefs.sunnahTotal()),
        Ch("أسبوع من المحافظة", "٥ فروض يوميًا لمدة ٧ أيام متتالية", streak, 7),
        Ch("شهر من المحافظة", "٥ فروض يوميًا لمدة ٣٠ يومًا متتالية", streak, 30),
        Ch("فروض هذا الأسبوع", "٣٥ فرضًا خلال آخر ٧ أيام", prefs.weekFard(), 35),
        Ch("سنن هذا الأسبوع", "أكمل كل السنن في ٧ أيام من آخر ٧", prefs.sunnahFullDays(), 7)
    )
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Glass {
                Text("سلسلة المحافظة الحالية", color = Gold, fontWeight = FontWeight.Bold)
                Text("$streak يوم", fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Text("تُحسب الأيام التي سجّلت فيها الفروض الخمسة كاملة.", fontSize = 12.sp)
            }
        }
        items(list) { c ->
            Glass {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(c.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(if (c.cur >= c.goal) "✅ تم" else "${c.cur}/${c.goal}", color = Gold)
                }
                Text(c.desc, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (c.cur.toFloat() / c.goal).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = Gold
                )
            }
        }
    }
}

// ───────────────────────── الأذكار ─────────────────────────

@Composable
fun AzkarScreen() {
    var cat by remember { mutableStateOf("الكل") }
    val counters = remember { mutableStateMapOf<Int, Int>() }
    val shown = Content.azkar.withIndex().filter { cat == "الكل" || cat in it.value.cats }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Content.azkarCats.forEach { c ->
                FilterChip(selected = cat == c, onClick = { cat = c }, label = { Text(c) })
            }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(shown) { (idx, z) ->
                val c = counters[idx] ?: 0
                Glass {
                    Text(z.text, fontSize = 18.sp, lineHeight = 30.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(z.source, fontSize = 12.sp, color = Color(0xFFCCCCCC))
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { if (c < z.count) counters[idx] = c + 1 },
                            enabled = c < z.count
                        ) { Text(if (c >= z.count) "تم ✅" else "$c / ${z.count}") }
                        if (c > 0) TextButton(onClick = { counters[idx] = 0 }) { Text("إعادة") }
                    }
                }
            }
        }
    }
}

// ───────────────────────── التسبيح والاستغفار ─────────────────────────

@Composable
fun TasbihScreen(prefs: Prefs) {
    var count by remember { mutableIntStateOf(prefs.tasbihCount) }
    var idx by remember { mutableIntStateOf(prefs.tasbihIndex) }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Content.tasbih.forEachIndexed { i, t ->
                FilterChip(
                    selected = idx == i,
                    onClick = { idx = i; count = 0; prefs.tasbihIndex = i; prefs.tasbihCount = 0 },
                    label = { Text(t) }
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(
            Content.tasbih[idx], fontSize = 26.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, color = Gold
        )
        Spacer(Modifier.height(32.dp))
        Box(
            Modifier.size(220.dp).clip(CircleShape).background(Gold)
                .clickable { count++; prefs.tasbihCount = count },
            contentAlignment = Alignment.Center
        ) {
            Text("$count", fontSize = 64.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = { count = 0; prefs.tasbihCount = 0 }) { Text("تصفير العدّاد") }
    }
}

// ───────────────────────── الإعدادات ─────────────────────────

@Composable
fun SettingsScreen(prefs: Prefs, bg: Int, onBg: (Int) -> Unit) {
    val ctx = LocalContext.current
    var adhanOn by remember { mutableStateOf(prefs.adhanEnabled) }
    var full by remember { mutableStateOf(prefs.fullAdhan) }
    var muezzin by remember { mutableStateOf(prefs.muezzin) }
    var method by remember { mutableStateOf(prefs.method) }
    val perPrayer = remember { mutableStateMapOf<Prayer, Boolean>().apply { Prayer.values().forEach { put(it, prefs.prayerEnabled(it)) } } }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Glass {
                Text("الأذان", color = Gold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                SwitchRow("تشغيل الأذان في وقت الصلاة", adhanOn) {
                    adhanOn = it; prefs.adhanEnabled = it; Scheduler.schedule(ctx)
                }
                SwitchRow("الأذان كامل (وإلا قصير: أول ${Muezzins.SHORT_SECONDS} ثانية)", full) { full = it; prefs.fullAdhan = it }
                Prayer.values().forEach { p ->
                    SwitchRow("أذان ${p.ar}", perPrayer[p] ?: true) {
                        perPrayer[p] = it; prefs.setPrayerEnabled(p, it); Scheduler.schedule(ctx)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { AdhanService.start(ctx, Prayer.DHUHR) }) { Text("تجربة الأذان الآن") }
                if (!Scheduler.canExact(ctx) && Build.VERSION.SDK_INT >= 31) {
                    Spacer(Modifier.height(8.dp))
                    Text("لازم تسمح بـ «التنبيهات والتذكيرات» عشان الأذان يشتغل في ميعاده بالظبط.", fontSize = 12.sp)
                    Button(onClick = {
                        ctx.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))
                        )
                    }) { Text("السماح بالتنبيهات الدقيقة") }
                }
            }
        }
        item {
            Glass {
                Text("صوت المؤذن", color = Gold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    "حمّل الأذان على الموبايل (مرة واحدة) وبعدها يشتغل بدون إنترنت. " +
                        "النوع (كامل / قصير) من الخيار الموجود في قسم الأذان أعلاه: القصير بيشتغل أول ${Muezzins.SHORT_SECONDS} ثانية.",
                    fontSize = 12.sp
                )
            }
        }
        items(listOf(Muezzins.EGY, Muezzins.HARAM, Muezzins.OTHER)) { group ->
            Glass {
                Text(group, color = Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Muezzins.all.filter { it.group == group }.forEach { m ->
                    MuezzinRow(m, selected = muezzin == m.key) { muezzin = m.key; prefs.muezzin = m.key }
                }
            }
        }
        item {
            Glass {
                Text("طريقة الحساب", color = Gold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Content.methods.forEach { (id, name) ->
                    RadioRow(name, method == id) {
                        method = id; prefs.method = id; Scheduler.schedule(ctx); refreshTick.intValue++
                    }
                }
            }
        }
        item {
            Glass {
                Text("الموقع", color = Gold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    if (prefs.hasLocation) "تم تحديد موقعك (%.3f, %.3f)".format(Locale.US, prefs.lat, prefs.lng)
                    else "لم يتم تحديد الموقع بعد"
                )
                Button(onClick = { (ctx as? MainActivity)?.fetchLocation() }) { Text("تحديث الموقع") }
            }
        }
        item {
            Glass {
                Text("الخلفية", color = Gold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                backgrounds.forEachIndexed { i, b -> RadioRow(b.name, bg == i) { onBg(i) } }
            }
        }
    }
}

@Composable
fun MuezzinRow(m: Muezzin, selected: Boolean, onSelect: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloaded by remember(m.key) { mutableStateOf(Muezzins.isDownloaded(ctx, m)) }
    var progress by remember(m.key) { mutableIntStateOf(-1) } // -1 = مش بيحمّل
    var error by remember(m.key) { mutableStateOf<String?>(null) }
    var player by remember(m.key) { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(m.key) {
        onDispose {
            player?.let { runCatching { it.stop() }; it.release() }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable { onSelect() }, verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onSelect)
            Text(m.name, Modifier.weight(1f))
            when {
                progress >= 0 -> Text("$progress%", color = Gold)
                downloaded -> {
                    TextButton(onClick = {
                        val p = player
                        if (p != null) {
                            runCatching { p.stop() }; p.release(); player = null
                        } else {
                            val np = Muezzins.previewPlayer(ctx, m)
                            if (np != null) {
                                np.setOnCompletionListener { it.release(); player = null }
                                np.start()
                                player = np
                            }
                        }
                    }) { Text(if (player != null) "⏹ إيقاف" else "▶ استماع") }
                    TextButton(onClick = {
                        Muezzins.delete(ctx, m); downloaded = false
                    }) { Text("حذف") }
                }
                else -> OutlinedButton(onClick = {
                    error = null
                    progress = 0
                    scope.launch {
                        val res = withContext(Dispatchers.IO) {
                            Muezzins.download(ctx, m) { pr -> progress = pr }
                        }
                        progress = -1
                        if (res == null) downloaded = true else error = res
                    }
                }) { Text("⬇ تحميل") }
            }
        }
        if (progress >= 0) {
            LinearProgressIndicator(
                progress = { progress / 100f }, modifier = Modifier.fillMaxWidth(), color = Gold
            )
        }
        error?.let { Text(it, fontSize = 12.sp, color = Color(0xFFFF8A80)) }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }, verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}
