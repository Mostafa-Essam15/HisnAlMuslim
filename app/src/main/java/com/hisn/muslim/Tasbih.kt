package com.hisn.muslim

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

fun vibrate(ctx: Context, ms: Long) {
    try {
        @Suppress("DEPRECATION")
        val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    } catch (_: Exception) {}
}

@Composable
fun TasbihScreen(prefs: Prefs) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var custom by remember { mutableStateOf(prefs.customDhikr) }
    val all = Content.tasbih + custom
    var idx by remember { mutableIntStateOf(prefs.tasbihIndex.coerceIn(0, all.size - 1)) }
    var vib by remember { mutableStateOf(prefs.vibrateOn) }
    var confirmReset by remember { mutableStateOf(false) }
    var addDialog by remember { mutableStateOf(false) }
    var newText by remember { mutableStateOf("") }
    val day = prefs.dayKey()
    val name = all[idx]
    val count = remember(tick, idx) { prefs.tasbihDay(day)[name] ?: 0 }
    val target = remember(tick, idx) { prefs.tasbihTarget(name) }
    val total = remember(tick) { prefs.tasbihTotal(day) }
    val rounds = count / target
    val inRound = count % target
    var full by remember { mutableStateOf(false) }
    var volOn by remember { mutableStateOf(prefs.tasbihVolume) }
    val bump = {
        prefs.tasbihAdd(day, name)
        prefs.tasbihCount = count + 1
        tick++
        if (vib) vibrate(ctx, if ((count + 1) % target == 0) 200 else 25)
    }
    val bumpNow = rememberUpdatedState(bump)
    DisposableEffect(volOn) {
        TasbihBus.onVolume = if (volOn) ({ bumpNow.value() }) else null
        onDispose { TasbihBus.onVolume = null }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                all.forEachIndexed { i, t -> GoldChip(t, idx == i) { idx = i; prefs.tasbihIndex = i } }
                GoldChip("＋ ذكر جديد", false) { addDialog = true }
            }
        }
        item { Text(name, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = Gold, fontFamily = Serif) }
        item {
            Box(
                Modifier.size(230.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Gold, GoldDeep)))
                    .clickable { bump() },
                contentAlignment = Alignment.Center
            ) {
                Text("$inRound", fontSize = 68.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }
        item {
            LinearProgressIndicator(
                progress = { inRound / target.toFloat() },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = Gold, trackColor = Track
            )
            Text("الهدف: $target • دورات مكتملة: $rounds • إجمالي اليوم لهذا الذكر: $count", color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
            Text("إجمالي التسبيح اليوم (كل الأذكار): $total", color = Gold, fontSize = 13.sp)
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(33, 34, 99, 100, 1000).forEach { t -> GoldChip("$t", target == t) { prefs.setTasbihTarget(name, t); tick++ } }
                GoldChip(if (vib) "الاهتزاز: يعمل" else "الاهتزاز: مغلق", vib) { vib = !vib; prefs.vibrateOn = vib }
                GoldChip(if (volOn) "أزرار الصوت: تعدّ" else "أزرار الصوت: مغلقة", volOn) { volOn = !volOn; prefs.tasbihVolume = volOn }
                GoldChip("ملء الشاشة", false) { full = true }
            }
        }
        item {
            val hist = remember(tick) { prefs.tasbihHistory(30) }
            val top = remember(tick) { prefs.tasbihTop(30).take(3) }
            val week = hist.takeLast(7)
            val maxV = (week.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
            val dn = listOf("إث", "ثل", "أر", "خم", "جم", "سب", "أح")
            Glass {
                Text("إحصائيات التسبيح", fontWeight = FontWeight.Bold, color = Gold)
                Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                    week.forEach { (d, v) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                            Text("$v", fontSize = 10.sp, color = Muted)
                            Box(Modifier.size(width = 22.dp, height = (6 + 70f * v / maxV).dp).clip(CircleShape).background(if (v > 0) Gold else Track))
                            Text(dn[d.dayOfWeek.value - 1], fontSize = 10.sp, color = Muted)
                        }
                    }
                }
                Text("آخر ٧ أيام: ${week.sumOf { it.second }} • آخر ٣٠ يومًا: ${hist.sumOf { it.second }}", fontSize = 12.sp, color = Muted)
                if (top.isNotEmpty()) Text("الأكثر خلال ٣٠ يومًا: " + top.joinToString("، ") { "${it.first} (${it.second})" }, fontSize = 12.sp, color = Muted)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { confirmReset = true }) { Text("تصفير هذا الذكر", color = Gold) }
                if (idx >= Content.tasbih.size) {
                    TextButton(onClick = {
                        val l = custom.toMutableList(); l.removeAt(idx - Content.tasbih.size)
                        prefs.customDhikr = l; custom = l; idx = 0; prefs.tasbihIndex = 0
                    }) { Text("حذف الذكر", color = Color(0xFFFF8A80)) }
                }
            }
        }
    }

    if (full) Dialog(onDismissRequest = { full = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color(0xF2050A08)).clickable { bump() },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(name, fontSize = 30.sp, color = Gold, fontFamily = Serif, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
                Text("$inRound", fontSize = 130.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("من $target • دورات: $rounds", color = Muted)
                Text("المس أي مكان للعدّ", color = Muted, fontSize = 12.sp)
                OutlinedButton(onClick = { full = false }) { Text("إغلاق", color = Gold) }
            }
        }
    }
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("تصفير العدّاد؟") },
        text = { Text("سيُصفَّر عدّاد «$name» لليوم فقط.") },
        confirmButton = { TextButton(onClick = { prefs.tasbihReset(day, name); tick++; confirmReset = false }) { Text("تصفير") } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("إلغاء") } }
    )
    if (addDialog) AlertDialog(
        onDismissRequest = { addDialog = false },
        title = { Text("ذكر جديد") },
        text = { OutlinedTextField(value = newText, onValueChange = { newText = it }, singleLine = true, label = { Text("نص الذكر") }) },
        confirmButton = {
            TextButton(onClick = {
                val t = newText.trim()
                if (t.isNotEmpty() && t !in all) { val l = custom + t; prefs.customDhikr = l; custom = l; idx = Content.tasbih.size + l.size - 1; prefs.tasbihIndex = idx }
                newText = ""; addDialog = false
            }) { Text("إضافة") }
        },
        dismissButton = { TextButton(onClick = { addDialog = false }) { Text("إلغاء") } }
    )
}

// ───────────────────────── القبلة ─────────────────────────

object Qibla {
    private const val KAABA_LAT = 21.422487
    private const val KAABA_LNG = 39.826206

    /** الاتجاه الحقيقي (من الشمال، مع عقارب الساعة) من الموقع إلى الكعبة بالدرجات */
    fun bearing(lat: Double, lng: Double): Double {
        val p1 = Math.toRadians(lat)
        val p2 = Math.toRadians(KAABA_LAT)
        val dl = Math.toRadians(KAABA_LNG - lng)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** المسافة التقريبية إلى الكعبة بالكيلومتر (haversine) */
    fun distanceKm(lat: Double, lng: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(KAABA_LAT - lat)
        val dLng = Math.toRadians(KAABA_LNG - lng)
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat)) * cos(Math.toRadians(KAABA_LAT)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * r * atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }
}
