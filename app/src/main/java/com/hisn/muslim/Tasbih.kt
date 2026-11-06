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

private fun vibrate(ctx: Context, ms: Long) {
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
                    .clickable {
                        prefs.tasbihAdd(day, name)
                        prefs.tasbihCount = count + 1
                        tick++
                        if (vib) vibrate(ctx, if ((count + 1) % target == 0) 200 else 25)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("$inRound", fontSize = 68.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }
        item {
            LinearProgressIndicator(
                progress = { inRound / target.toFloat() },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = Gold, trackColor = Color(0x33FFFFFF)
            )
            Text("الهدف: $target • دورات مكتملة: $rounds • إجمالي اليوم لهذا الذكر: $count", color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
            Text("إجمالي التسبيح اليوم (كل الأذكار): $total", color = Gold, fontSize = 13.sp)
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(33, 34, 99, 100, 1000).forEach { t -> GoldChip("$t", target == t) { prefs.setTasbihTarget(name, t); tick++ } }
                GoldChip(if (vib) "الاهتزاز: يعمل" else "الاهتزاز: مغلق", vib) { vib = !vib; prefs.vibrateOn = vib }
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
}

@Composable
fun QiblaScreen(prefs: Prefs) {
    val ctx = LocalContext.current
    val bearing = remember(prefs.lat, prefs.lng) { Qibla.bearing(prefs.lat, prefs.lng).toFloat() }
    var heading by remember { mutableFloatStateOf(0f) }
    var hasSensor by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensor == null) { hasSensor = false; onDispose { } } else {
            val l = object : SensorEventListener {
                val r = FloatArray(9)
                val o = FloatArray(3)
                override fun onSensorChanged(e: SensorEvent) {
                    SensorManager.getRotationMatrixFromVector(r, e.values)
                    SensorManager.getOrientation(r, o)
                    val az = ((Math.toDegrees(o[0].toDouble()) + 360.0) % 360.0).toFloat()
                    // تنعيم بسيط للحركة
                    var d = az - heading
                    if (d > 180) d -= 360
                    if (d < -180) d += 360
                    heading = (heading + d * 0.2f + 360f) % 360f
                }
                override fun onAccuracyChanged(s: Sensor?, a: Int) {}
            }
            sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_UI)
            onDispose { sm.unregisterListener(l) }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Glass {
            SectionTitle("اتجاه القبلة")
            Text(
                "من ${if (prefs.city.isNotEmpty()) prefs.city else "موقعك المحفوظ"}: ${"%.1f".format(java.util.Locale.US, bearing)}° من الشمال الحقيقي",
                fontSize = 15.sp
            )
            if (!prefs.hasLocation) Text("لم يُحدَّد موقعك بعد؛ الاتجاه محسوب من القاهرة (الافتراضي).", color = Color(0xFFFFB74D), fontSize = 12.sp)
        }
        Spacer(Modifier.height(20.dp))
        if (hasSensor) {
            // السهم بيشاور على القبلة: دوران = اتجاه القبلة − اتجاه الجهاز
            val rot = bearing - heading
            val aligned = kotlin.math.abs(((rot + 540f) % 360f) - 180f) < 4f
            Box(
                Modifier.size(260.dp).clip(CircleShape).background(Color(0x22FFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                Text("↑", fontSize = 150.sp, color = if (aligned) Color(0xFF66BB6A) else Gold, modifier = Modifier.rotate(rot))
            }
            Spacer(Modifier.height(12.dp))
            Text(if (aligned) "أنت تواجه القبلة ✓" else "لفّ الجهاز حتى يشير السهم لأعلى", color = if (aligned) Color(0xFF66BB6A) else Muted)
            Text(
                "ضع الهاتف أفقيًا بعيدًا عن المعادن والمغانط. إن بدا الاتجاه غير دقيق فحرّك الهاتف على شكل ٨ لمعايرة البوصلة.",
                fontSize = 12.sp, color = Muted, textAlign = TextAlign.Center
            )
        } else {
            Text("جهازك لا يدعم حساس البوصلة. اتجاه القبلة: ${"%.0f".format(java.util.Locale.US, bearing)}° من الشمال؛ استعن ببوصلة خارجية.", color = Muted)
        }
    }
}
