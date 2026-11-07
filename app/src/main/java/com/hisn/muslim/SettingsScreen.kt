package com.hisn.muslim

import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Locale

fun fmtMinutes(m: Int): String {
    val h = m / 60
    val mm = m % 60
    val h12 = if (h % 12 == 0) 12 else h % 12
    return String.format(Locale.US, "%d:%02d %s", h12, mm, if (h < 12) "ص" else "م")
}

fun pickTime(ctx: Context, minutes: Int, onPick: (Int) -> Unit) {
    TimePickerDialog(ctx, { _, h, m -> onPick(h * 60 + m) }, minutes / 60, minutes % 60, false).show()
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Gold, checkedThumbColor = Color.Black,
                uncheckedTrackColor = Color(0x33FFFFFF), uncheckedThumbColor = Muted
            )
        )
    }
}

@Composable
fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }, verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = Gold))
        Text(label)
    }
}

@Composable
fun ChipRow(options: List<Pair<String, Int>>, current: Int, onPick: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (label, v) -> GoldChip(label, current == v) { onPick(v) } }
    }
}

@Composable
fun MuezzinRow(
    m: Muezzin, selected: Boolean, version: Int,
    onSelect: () -> Unit, onPickCustom: () -> Unit, onChanged: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloaded by remember(m.key, version) { mutableStateOf(Muezzins.isDownloaded(ctx, m)) }
    var progress by remember(m.key) { mutableIntStateOf(-1) } // -1 = مش بيحمّل
    var error by remember(m.key) { mutableStateOf<String?>(null) }
    var player by remember(m.key) { mutableStateOf<MediaPlayer?>(null) }
    val info by produceState("", downloaded, version) {
        value = if (downloaded) withContext(Dispatchers.IO) { Muezzins.info(ctx, m) } else ""
    }

    DisposableEffect(m.key) {
        onDispose { player?.let { runCatching { it.stop() }; it.release() } }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable { onSelect() }, verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onSelect, colors = RadioButtonDefaults.colors(selectedColor = Gold))
            Column(Modifier.weight(1f)) {
                Text(m.name)
                if (info.isNotBlank()) Text(info, fontSize = 11.sp, color = Muted)
            }
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
                    }) { Text(if (player != null) "⏹" else "▶ استماع", color = Gold) }
                    TextButton(onClick = {
                        player?.let { runCatching { it.stop() }; it.release() }
                        player = null
                        Muezzins.delete(ctx, m); downloaded = false; onChanged()
                    }) { Text("حذف", color = Color(0xFFFF8A80)) }
                }
                m.isCustom -> OutlinedButton(onClick = onPickCustom) { Text("اختيار ملف", color = Gold) }
                else -> OutlinedButton(onClick = {
                    error = null
                    progress = 0
                    scope.launch {
                        val res = withContext(Dispatchers.IO) { Muezzins.download(ctx, m) { pr -> progress = pr } }
                        progress = -1
                        if (res == null) { downloaded = true; onChanged() } else error = res
                    }
                }) { Text("⬇ تحميل", color = Gold) }
            }
        }
        if (progress >= 0) {
            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth(), color = Gold)
        }
        error?.let { Text(it, fontSize = 12.sp, color = Color(0xFFFF8A80)) }
    }
}

@Composable
fun StatusRow(ok: Boolean, text: String, actionLabel: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (ok) "✅" else "⚠️")
        Text("  $text", Modifier.weight(1f), fontSize = 13.sp)
        if (!ok) TextButton(onClick = onAction) { Text(actionLabel, color = Gold) }
    }
}

@Composable
fun SettingsScreen(prefs: Prefs, bg: Int, bgVersion: Int, onBg: (Int) -> Unit, onBgPhoto: () -> Unit) {
    val ctx = LocalContext.current
    val tick = refreshTick.intValue
    var adhanOn by remember { mutableStateOf(prefs.adhanEnabled) }
    var full by remember { mutableStateOf(prefs.fullAdhan) }
    var playInSilent by remember { mutableStateOf(prefs.playInSilent) }
    var volume by remember { mutableIntStateOf(prefs.adhanVolume) }
    var muezzin by remember { mutableStateOf(prefs.muezzin) }
    var method by remember { mutableStateOf(prefs.method) }
    var mVersion by remember { mutableIntStateOf(0) }
    var checkOn by remember { mutableStateOf(prefs.checkEnabled) }
    var checkAfter by remember { mutableIntStateOf(prefs.checkAfterMin) }
    var laterMin by remember { mutableIntStateOf(prefs.laterMin) }
    var duha by remember { mutableStateOf(prefs.duhaOn) }
    var qiyam by remember { mutableStateOf(prefs.qiyamOn) }
    var fMonThu by remember { mutableStateOf(prefs.fastMonThu) }
    var fWhite by remember { mutableStateOf(prefs.fastWhite) }
    var fArafah by remember { mutableStateOf(prefs.fastArafah) }
    var fAshura by remember { mutableStateOf(prefs.fastAshura) }
    var fShawwal by remember { mutableStateOf(prefs.fastShawwal) }
    var fDhul by remember { mutableStateOf(prefs.fastDhul) }
    var fastTime by remember { mutableIntStateOf(prefs.fastTime) }
    var hijriAdj by remember { mutableIntStateOf(prefs.hijriAdj) }
    var popupOn by remember { mutableStateOf(prefs.popupOn) }
    var popupInt by remember { mutableIntStateOf(prefs.popupIntervalMin) }
    var popupStart by remember { mutableIntStateOf(prefs.popupStartHour) }
    var popupEnd by remember { mutableIntStateOf(prefs.popupEndHour) }
    val perPrayer = remember {
        mutableStateMapOf<Prayer, Boolean>().apply { Prayer.values().forEach { put(it, prefs.prayerEnabled(it)) } }
    }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val err = Muezzins.importCustom(ctx, uri)
            if (err == null) { muezzin = "custom"; prefs.muezzin = "custom" }
            mVersion++
        }
    }

    fun reschedule() = Reminders.scheduleAll(ctx)

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

        // ───── الأذان ─────
        item {
            Glass {
                SectionTitle("الأذان", Icons.Rounded.VolumeUp)
                SwitchRow("تشغيل الأذان في وقت الصلاة", adhanOn) { adhanOn = it; prefs.adhanEnabled = it; reschedule() }
                SwitchRow("أذان كامل (وإلا قصير: أول ${Muezzins.SHORT_SECONDS} ثانية)", full) { full = it; prefs.fullAdhan = it }
                Spacer(Modifier.height(6.dp))
                SwitchRow("تشغيل الأذان في وضع الصامت", playInSilent) {
                    playInSilent = it; prefs.playInSilent = it
                }
                val ringer = ctx.getSystemService(AudioManager::class.java).ringerMode
                val ringerLabel = when (ringer) {
                    AudioManager.RINGER_MODE_SILENT -> "صامت"
                    AudioManager.RINGER_MODE_VIBRATE -> "اهتزاز"
                    else -> "عادي"
                }
                val dndActive = DndGuard.isActive(ctx)
                val dndAccess = DndGuard.hasAccess(ctx)
                Text(
                    if (playInSilent)
                        "الأذان بيشتغل على قناة المنبّه، فبيُسمع حتى لو الموبايل على الصامت أو الاهتزاز."
                    else
                        "الأذان هيلتزم بوضع الصامت: لو الموبايل صامت هيظهر إشعار بدون صوت.",
                    fontSize = 12.sp, color = Muted
                )
                Text(
                    "وضع الصوت الحالي: $ringerLabel • عدم الإزعاج: ${if (dndActive) "مفعّل" else "غير مفعّل"}",
                    fontSize = 12.sp, color = Muted
                )
                if (playInSilent) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "«عدم الإزعاج» حاجة منفصلة عن الصامت. لو شغّال على «صمت تام» بيكتم المنبهات كمان، وعشان الأذان يشتغل وقتها " +
                            "لازم تدّي التطبيق إذن «الوصول لسياسة الإشعارات»: هيحوّل مؤقتًا لـ«المنبهات فقط» أثناء الأذان ويرجّعه بعد ما يخلص. " +
                            "التطبيق ما بيغيّر حاجة من غير الإذن ده، وبتقدر تسحبه من إعدادات الأندرويد في أي وقت.",
                        fontSize = 12.sp, color = Muted
                    )
                    StatusRow(dndAccess, "إذن الوصول لسياسة عدم الإزعاج", "فتح الإعدادات") {
                        ctx.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                    Text(
                        "بعض الأجهزة (شاومي، أوبو، فيفو، هواوي، سامسونج) فيها قيود إضافية على التطبيقات في الخلفية؛ " +
                            "لو الأذان ما اشتغلش على جهازك فعّل «التشغيل التلقائي» واستثنِ التطبيق من توفير البطارية.",
                        fontSize = 11.sp, color = Muted
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text("مستوى صوت الأذان: $volume%", fontSize = 14.sp)
                Slider(
                    value = volume.toFloat(), onValueChange = { volume = it.toInt() },
                    onValueChangeFinished = { prefs.adhanVolume = volume },
                    valueRange = 20f..100f,
                    colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Gold)
                )
                Prayer.values().forEach { p ->
                    SwitchRow("أذان ${p.ar}", perPrayer[p] ?: true) {
                        perPrayer[p] = it; prefs.setPrayerEnabled(p, it); reschedule()
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { AdhanService.start(ctx, Prayer.DHUHR, test = true) }) { Text("تجربة الأذان", color = Gold) }
                    OutlinedButton(onClick = {
                        ctx.sendBroadcast(
                            Intent(ctx, ReminderReceiver::class.java).putExtra("type", "CHECK").putExtra("prayer", "FAJR")
                        )
                    }) { Text("تجربة «هل صليت؟»", color = Gold) }
                }
            }
        }

        // ───── المؤذنون ─────
        item {
            Glass {
                SectionTitle("صوت المؤذن", Icons.Rounded.VolumeUp)
                Text(
                    "حمّل الأذان مرة واحدة وبعدها يشتغل بدون إنترنت. جودة الصوت بتتحدد بملف المصدر (بنعرضها تحت كل مؤذن بعد التحميل). " +
                        "لأعلى جودة اختار «ملف أذان من جهازك» (mp3 / m4a / flac / wav).",
                    fontSize = 12.sp, color = Muted
                )
            }
        }
        items(Muezzins.groups) { group ->
            Glass {
                Text(group, color = Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Muezzins.all.filter { it.group == group }.forEach { m ->
                    MuezzinRow(
                        m, selected = muezzin == m.key, version = mVersion,
                        onSelect = { muezzin = m.key; prefs.muezzin = m.key },
                        onPickCustom = { pickAudio.launch("audio/*") },
                        onChanged = { mVersion++ }
                    )
                }
            }
        }

        // ───── تأكيد الصلاة ─────
        item {
            Glass {
                SectionTitle("تأكيد أداء الصلاة", Icons.Rounded.CheckCircle)
                var trackOn by remember { mutableStateOf(prefs.trackingEnabled) }
                var periodOn by remember { mutableStateOf(prefs.periodPopupOn) }
                SwitchRow("تتبّع الصلوات (أديت / قضاء)", trackOn) { trackOn = it; prefs.trackingEnabled = it; reschedule() }
                SwitchRow("اسألني عند انتهاء وقت الصلاة: نعم / قضاء / لاحقاً", periodOn) { periodOn = it; prefs.periodPopupOn = it; reschedule() }
                SwitchRow("اسألني «هل صليت؟» بعد الأذان", checkOn) { checkOn = it; prefs.checkEnabled = it }
                if (checkOn) {
                    Spacer(Modifier.height(6.dp))
                    Text("اسألني بعد الأذان بـ:", fontSize = 13.sp, color = Muted)
                    ChipRow(listOf(10, 15, 20, 30, 45, 60).map { "$it د" to it }, checkAfter) { checkAfter = it; prefs.checkAfterMin = it }
                    Spacer(Modifier.height(8.dp))
                    Text("لو لسه ما صليت، ذكّرني كل:", fontSize = 13.sp, color = Muted)
                    ChipRow(listOf(5, 10, 15, 20, 30).map { "$it د" to it }, laterMin) { laterMin = it; prefs.laterMin = it }
                }
            }
        }

        // ───── وضع التركيز ─────
        item {
            Glass {
                SectionTitle("وضع التركيز حول الصلاة", Icons.Rounded.Notifications)
                var fOn by remember { mutableStateOf(prefs.focusOn) }
                var fb by remember { mutableIntStateOf(prefs.focusBeforeMin) }
                var fa by remember { mutableIntStateOf(prefs.focusAfterMin) }
                SwitchRow("تفعيل «عدم الإزعاج» تلقائيًا حول أوقات الصلاة", fOn) { fOn = it; prefs.focusOn = it; reschedule() }
                Text(
                    "بيحوّل الهاتف لوضع «عدم الإزعاج: الأولوية فقط» قبل الأذان ويرجّع وضعك السابق بعد المدة. " +
                        "لازم تمنح التطبيق إذن «الوصول لسياسة الإشعارات»، وما بيغيّر حاجة من غيره. الأذان نفسه بيفضل شغّال (قناة المنبّه).",
                    fontSize = 12.sp, color = Muted
                )
                StatusRow(DndGuard.hasAccess(ctx), "إذن الوصول لسياسة عدم الإزعاج", "فتح الإعدادات") {
                    ctx.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                if (fOn) {
                    Text("قبل الأذان بـ:", fontSize = 13.sp, color = Muted)
                    ChipRow(listOf(0, 5, 10, 15).map { "$it د" to it }, fb) { fb = it; prefs.focusBeforeMin = it; reschedule() }
                    Text("بعد الأذان بـ:", fontSize = 13.sp, color = Muted)
                    ChipRow(listOf(10, 20, 30, 45).map { "$it د" to it }, fa) { fa = it; prefs.focusAfterMin = it; reschedule() }
                    Prayer.values().forEach { pr ->
                        var on by remember { mutableStateOf(prefs.focusPrayer(pr)) }
                        SwitchRow(pr.ar, on) { on = it; prefs.setFocusPrayer(pr, it); reschedule() }
                    }
                }
            }
        }

        // ───── الصيام والنوافل ─────
        item {
            Glass {
                SectionTitle("صيام السنن والنوافل", Icons.Rounded.Spa)
                Text("التاريخ الهجري اليوم: ${Fasting.hijriText(prefs)}", fontSize = 13.sp, color = Muted)
                Spacer(Modifier.height(6.dp))
                SwitchRow("الإثنين والخميس", fMonThu) { fMonThu = it; prefs.fastMonThu = it; reschedule() }
                SwitchRow("الأيام البيض (١٣-١٤-١٥)", fWhite) { fWhite = it; prefs.fastWhite = it; reschedule() }
                SwitchRow("يوم عرفة", fArafah) { fArafah = it; prefs.fastArafah = it; reschedule() }
                SwitchRow("تاسوعاء وعاشوراء", fAshura) { fAshura = it; prefs.fastAshura = it; reschedule() }
                SwitchRow("ست من شوال", fShawwal) { fShawwal = it; prefs.fastShawwal = it; reschedule() }
                SwitchRow("العشر الأوائل من ذي الحجة", fDhul) { fDhul = it; prefs.fastDhul = it; reschedule() }
                TextButton(onClick = {
                    pickTime(ctx, fastTime) { m -> fastTime = m; prefs.fastTime = m; reschedule() }
                }) { Text("وقت التذكير (مساء اليوم السابق): ${fmtMinutes(fastTime)}", color = Gold) }
                Text("تعديل التاريخ الهجري (لو بلدك بتبدأ الشهر بيوم مختلف):", fontSize = 12.sp, color = Muted)
                ChipRow(listOf("−٢" to -2, "−١" to -1, "كما هو" to 0, "+١" to 1, "+٢" to 2), hijriAdj) {
                    hijriAdj = it; prefs.hijriAdj = it; refreshTick.intValue++
                }
                Spacer(Modifier.height(10.dp))
                SwitchRow("تذكير صلاة الضحى", duha) { duha = it; prefs.duhaOn = it; reschedule() }
                SwitchRow("تذكير قيام الليل (الثلث الأخير)", qiyam) { qiyam = it; prefs.qiyamOn = it; reschedule() }
            }
        }

        // ───── بوب اب الأذكار ─────
        item {
            Glass {
                SectionTitle("بوب اب الأذكار", Icons.Rounded.AutoAwesome)
                SwitchRow("اظهر ذكر على الشاشة بشكل دوري", popupOn) { popupOn = it; prefs.popupOn = it; reschedule() }
                if (popupOn) {
                    Spacer(Modifier.height(6.dp))
                    Text("كل:", fontSize = 13.sp, color = Muted)
                    ChipRow(
                        listOf("٣٠ د" to 30, "ساعة" to 60, "ساعتين" to 120, "٣ ساعات" to 180, "٤ ساعات" to 240),
                        popupInt
                    ) { popupInt = it; prefs.popupIntervalMin = it; reschedule() }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("من الساعة ", fontSize = 13.sp)
                        OutlinedButton(onClick = {
                            popupStart = (popupStart + 1).let { if (it >= popupEnd) 0 else it }
                            prefs.popupStartHour = popupStart; reschedule()
                        }) { Text("$popupStart:00", color = Gold) }
                        Text("  إلى  ", fontSize = 13.sp)
                        OutlinedButton(onClick = {
                            popupEnd = (popupEnd + 1).let { if (it > 23) popupStart + 1 else it }
                            prefs.popupEndHour = popupEnd; reschedule()
                        }) { Text("$popupEnd:00", color = Gold) }
                    }
                    Text("(اضغط على الساعة لتغييرها)", fontSize = 11.sp, color = Muted)
                    Spacer(Modifier.height(8.dp))
                    StatusRow(
                        AzkarPopup.canOverlay(ctx),
                        "السماح بالعرض فوق التطبيقات (عشان البوب اب يظهر على الشاشة؛ وإلا بيظهر كإشعار)",
                        "السماح"
                    ) {
                        ctx.startActivity(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                    OutlinedButton(onClick = {
                        val item = Content.randomPopupDhikr()
                        if (AzkarPopup.canOverlay(ctx)) AzkarPopup.show(ctx, item) else AzkarPopup.popupAsNotification(ctx, item)
                    }) { Text("تجربة البوب اب", color = Gold) }
                }
            }
        }

        // ───── أذونات ─────
        item {
            Glass {
                SectionTitle("الأذونات المهمة", Icons.Rounded.Notifications)
                StatusRow(
                    Scheduler.canExact(ctx), "التنبيهات الدقيقة (عشان الأذان يشتغل في ميعاده بالظبط)", "السماح"
                ) {
                    if (Build.VERSION.SDK_INT >= 31) ctx.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                StatusRow(
                    ForcedOverlay.can(ctx),
                    "العرض فوق التطبيقات (يُظهر الأذان وسؤال «هل صليت؟» إجباريًا على الشاشة)", "السماح"
                ) {
                    ctx.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                val fsOk = Build.VERSION.SDK_INT < 34 ||
                    ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
                StatusRow(fsOk, "شاشة الأذان الكاملة فوق شاشة القفل", "السماح") {
                    if (Build.VERSION.SDK_INT >= 34) ctx.startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${ctx.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                Text(
                    "نصيحة: استثنِ التطبيق من «توفير البطارية» عشان النظام ما يقتلوش في الخلفية.",
                    fontSize = 12.sp, color = Muted
                )
            }
        }

        // ───── الموقع ─────
        item {
            Glass {
                SectionTitle("الموقع", Icons.Rounded.LocationOn)
                if (prefs.hasLocation) {
                    if (prefs.city.isNotBlank()) Text(prefs.city, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("(%.3f, %.3f)".format(Locale.US, prefs.lat, prefs.lng), color = Muted, fontSize = 12.sp)
                } else Text("لم يتم تحديد الموقع بعد")
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = { (ctx as? MainActivity)?.fetchLocation() },
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black)
                ) { Text("تحديث الموقع") }
            }
        }

        // ───── طريقة الحساب ─────
        item {
            Glass {
                SectionTitle("طريقة حساب المواقيت", Icons.Rounded.AccessTime)
                var hanafi by remember { mutableStateOf(prefs.asrMethod == "HANAFI") }
                var adjTick by remember { mutableIntStateOf(0) }
                SwitchRow("العصر على المذهب الحنفي (ظل مثلين)", hanafi) {
                    hanafi = it; prefs.asrMethod = if (it) "HANAFI" else "SHAFI"; reschedule(); refreshTick.intValue++
                }
                Spacer(Modifier.height(6.dp))
                Text("تعديل يدوي للمواقيت بالدقائق (لمطابقة مسجد/تقويم محلي):", fontSize = 13.sp, color = Muted)
                Prayer.values().forEach { pr ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(pr.ar, Modifier.weight(1f))
                        GoldChip("−", false) { prefs.setAdj(pr, prefs.adj(pr) - 1); adjTick++; reschedule(); refreshTick.intValue++ }
                        Text(
                            "${if (prefs.adj(pr) > 0) "+" else ""}${prefs.adj(pr).also { adjTick }}",
                            Modifier.width(44.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        GoldChip("+", false) { prefs.setAdj(pr, prefs.adj(pr) + 1); adjTick++; reschedule(); refreshTick.intValue++ }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("خطوط العرض العالية (بلاد الليل/النهار الطويل):", fontSize = 13.sp, color = Muted)
                var hl by remember { mutableStateOf(prefs.highLat) }
                listOf(
                    "DEFAULT" to "افتراضي", "MIDDLE_OF_THE_NIGHT" to "منتصف الليل",
                    "SEVENTH_OF_THE_NIGHT" to "سُبع الليل", "TWILIGHT_ANGLE" to "زاوية الشفق"
                ).forEach { (id, name) ->
                    RadioRow(name, hl == id) { hl = id; prefs.highLat = id; reschedule(); refreshTick.intValue++ }
                }
                Content.methods.forEach { (id, name) ->
                    RadioRow(name, method == id) {
                        method = id; prefs.method = id; reschedule(); refreshTick.intValue++
                    }
                }
            }
        }

        // ───── الخلفية ─────
        item {
            Glass {
                SectionTitle("الخلفية", Icons.Rounded.Image)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BgTile(selected = bg == BG_AUTO_PHOTO, label = "صور تلقائي", onClick = { onBg(BG_AUTO_PHOTO) }) {
                        val b0 = remember { Photos.load(ctx, 4, 8) }
                        if (b0 != null) Image(b0, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    BgTile(selected = bg == BG_AUTO, label = "تلقائي (حسب الوقت)", onClick = { onBg(BG_AUTO) }) {
                        MosqueScene(sceneNow(prefs), Modifier.fillMaxSize())
                    }
                    sceneStyles.forEachIndexed { i, s ->
                        BgTile(selected = bg == i, label = s.name, onClick = { onBg(i) }) {
                            MosqueScene(i, Modifier.fillMaxSize())
                        }
                    }
                    if (hasCustomBg(ctx)) {
                        val bmp = remember(bgVersion) { loadCustomBg(ctx) }
                        BgTile(selected = bg == customBgIndex, label = "صورتك", onClick = { onBg(customBgIndex) }) {
                            if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("صور المساجد", fontSize = 13.sp, color = Muted)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (i in 1..Photos.COUNT) {
                        BgTile(selected = bg == PHOTO_BASE + i, label = "صورة $i", onClick = { onBg(PHOTO_BASE + i) }) {
                            val th = remember(i) { Photos.load(ctx, i, 8) }
                            if (th != null) Image(th, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onBgPhoto) { Text("اختيار صورة جامع من معرضك", color = Gold) }
                Text(
                    "الصور الجاهزة مرسومة داخل التطبيق. لو عايز صور جامع حقيقية نزّلها من مصدر مرخّص (زي Unsplash أو Pexels) واختارها من هنا.",
                    fontSize = 11.sp, color = Muted
                )
            }
        }
    }
}

@Composable
private fun BgTile(selected: Boolean, label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(width = 92.dp, height = 150.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(
                    if (selected) BorderStroke(3.dp, Gold) else BorderStroke(1.dp, Color(0x55FFFFFF)),
                    RoundedCornerShape(16.dp)
                )
                .clickable { onClick() }
        ) { content() }
        Text(label, fontSize = 12.sp, color = if (selected) Gold else Muted)
    }
}
