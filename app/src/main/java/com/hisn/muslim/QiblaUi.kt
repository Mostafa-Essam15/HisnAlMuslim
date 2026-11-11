package com.hisn.muslim

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.Locale
import kotlin.math.abs

private val Green = Color(0xFF66BB6A)

/** اتجاه الجهاز بالنسبة للشمال الحقيقي (مع تصحيح الانحراف المغناطيسي). [camera]=true لوضع الكاميرا (الهاتف رأسيًا). */
private class HeadingState { var heading by mutableFloatStateOf(0f); var accuracy by mutableIntStateOf(SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM); var has by mutableStateOf(true) }

@Composable
private fun rememberHeading(lat: Double, lng: Double, camera: Boolean): HeadingState {
    val ctx = LocalContext.current
    val st = remember { HeadingState() }
    val decl = remember(lat, lng) { try { GeomagneticField(lat.toFloat(), lng.toFloat(), 0f, System.currentTimeMillis()).declination } catch (e: Exception) { 0f } }
    DisposableEffect(camera, decl) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensor == null) { st.has = false; onDispose { } } else {
            val l = object : SensorEventListener {
                val r = FloatArray(9); val r2 = FloatArray(9); val o = FloatArray(3)
                override fun onSensorChanged(e: SensorEvent) {
                    SensorManager.getRotationMatrixFromVector(r, e.values)
                    if (camera) SensorManager.remapCoordinateSystem(r, SensorManager.AXIS_X, SensorManager.AXIS_Z, r2) else System.arraycopy(r, 0, r2, 0, 9)
                    SensorManager.getOrientation(r2, o)
                    val az = ((Math.toDegrees(o[0].toDouble()) + decl + 720.0) % 360.0).toFloat()
                    var d = az - st.heading
                    if (d > 180) d -= 360
                    if (d < -180) d += 360
                    st.heading = (st.heading + d * 0.2f + 360f) % 360f
                }
                override fun onAccuracyChanged(s: Sensor?, a: Int) { st.accuracy = a }
            }
            sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_GAME)
            onDispose { sm.unregisterListener(l) }
        }
    }
    return st
}

private fun diffTo(bearing: Float, heading: Float) = ((bearing - heading + 540f) % 360f) - 180f

private fun buzz(ctx: Context) {
    try {
        @Suppress("DEPRECATION") val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        v.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
    } catch (_: Exception) {}
}

private fun accuracyText(a: Int) = when (a) {
    SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "دقة البوصلة: عالية"
    SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "دقة البوصلة: متوسطة"
    SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "دقة البوصلة: منخفضة — حرّك الهاتف على شكل ٨"
    else -> "البوصلة تحتاج معايرة — حرّك الهاتف على شكل ٨ بعيدًا عن المعادن"
}

@Composable
fun QiblaScreen(prefs: Prefs) {
    val ctx = LocalContext.current
    val bearing = remember(prefs.lat, prefs.lng) { Qibla.bearing(prefs.lat, prefs.lng).toFloat() }
    val km = remember(prefs.lat, prefs.lng) { Qibla.distanceKm(prefs.lat, prefs.lng) }
    var ar by remember { mutableStateOf(false) }
    var hasCam by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> hasCam = ok; if (ok) ar = true }
    val hs = rememberHeading(prefs.lat, prefs.lng, camera = ar && hasCam)
    val diff = diffTo(bearing, hs.heading)
    val aligned = abs(diff) < 4f
    LaunchedEffect(aligned) { if (aligned && hs.has) buzz(ctx) }

    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GoldChip("🧭 بوصلة", !ar) { ar = false }
            GoldChip("📷 واقع معزز", ar) { if (hasCam) ar = true else launcher.launch(Manifest.permission.CAMERA) }
        }
        Spacer(Modifier.height(10.dp))
        if (!hs.has) {
            Glass {
                SectionTitle("اتجاه القبلة")
                Text("جهازك لا يدعم حساس البوصلة. اتجاه القبلة: ${Quran.arabicDigits(bearing.toInt())}° من الشمال؛ استعن ببوصلة خارجية.", color = Muted)
            }
            return@Column
        }
        if (ar && hasCam) {
            ArQibla(bearing, hs.heading, km, aligned, Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(26.dp)))
        } else {
            Glass {
                SectionTitle("اتجاه القبلة")
                Text(
                    "من ${if (prefs.city.isNotEmpty()) prefs.city else "موقعك المحفوظ"}: ${Quran.arabicDigits(bearing.toInt())}° من الشمال الحقيقي",
                    fontSize = 15.sp
                )
                Text("المسافة إلى الكعبة: ${Quran.arabicDigits(km.toInt())} كم تقريبًا", fontSize = 13.sp, color = Muted)
                if (!prefs.hasLocation) Text("لم يُحدَّد موقعك بعد؛ الاتجاه محسوب من القاهرة (الافتراضي).", color = Color(0xFFFFB74D), fontSize = 12.sp)
            }
            Spacer(Modifier.height(16.dp))
            CompassDial(hs.heading, bearing, aligned)
            Spacer(Modifier.height(10.dp))
            Text(
                if (aligned) "أنت تواجه القبلة ✓" else if (diff > 0) "لفّ يمينًا ${Quran.arabicDigits(abs(diff).toInt())}°" else "لفّ يسارًا ${Quran.arabicDigits(abs(diff).toInt())}°",
                color = if (aligned) Green else Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold
            )
            Text(accuracyText(hs.accuracy), fontSize = 12.sp, color = if (hs.accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) Muted else Color(0xFFFFB74D), textAlign = TextAlign.Center)
            Text("ضع الهاتف أفقيًا بعيدًا عن المعادن والمغانط. الشمال المعروض هو الشمال الحقيقي بعد تصحيح الانحراف المغناطيسي.", fontSize = 11.sp, color = Muted, textAlign = TextAlign.Center)
            if (!hasCam) Text("وضع الواقع المعزز يحتاج إذن الكاميرا (يُطلب عند الضغط عليه).", fontSize = 11.sp, color = Muted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun CompassDial(heading: Float, bearing: Float, aligned: Boolean) {
    val accent = if (aligned) Green else Gold
    val ink = OnCard
    Box(Modifier.size(290.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(CardBg).border(2.dp, accent.copy(alpha = 0.8f), CircleShape))
        // القرص يدور عكس اتجاه الجهاز فيفضل الشمال الحقيقي ثابتًا
        Box(Modifier.fillMaxSize().rotate(-heading)) {
            Canvas(Modifier.fillMaxSize()) {
                val c = center; val r = size.minDimension / 2f
                for (a in 0 until 360 step 10) {
                    val long = a % 90 == 0; val mid = a % 30 == 0
                    val len = if (long) 18f else if (mid) 13f else 7f
                    rotate(a.toFloat(), c) {
                        drawLine(ink.copy(alpha = if (long) 0.9f else 0.45f), Offset(c.x, c.y - r + 6f), Offset(c.x, c.y - r + 6f + len), strokeWidth = if (long) 3f else 2f)
                    }
                }
            }
            listOf("ش" to 0f, "ق" to 90f, "ج" to 180f, "غ" to 270f).forEach { (l, a) ->
                Box(Modifier.fillMaxSize().rotate(a)) {
                    Text(l, Modifier.align(Alignment.TopCenter).padding(top = 28.dp), color = if (a == 0f) Color(0xFFEF5350) else ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
            // خط وعلامة الكعبة عند اتجاه القبلة
            Box(Modifier.fillMaxSize().rotate(bearing)) {
                Canvas(Modifier.fillMaxSize()) {
                    drawLine(accent, Offset(center.x, center.y), Offset(center.x, center.y - size.minDimension / 2f + 78f), strokeWidth = 5f)
                }
                Text("🕋", Modifier.align(Alignment.TopCenter).padding(top = 52.dp), fontSize = 34.sp)
            }
        }
        // مؤشر ثابت أعلى القرص يمثّل اتجاه الجهاز
        Canvas(Modifier.size(290.dp)) {
            val p = Path().apply { moveTo(center.x, 4f); lineTo(center.x - 12f, -12f + 4f); lineTo(center.x + 12f, -12f + 4f); close() }
            drawPath(p, accent)
            drawCircle(accent, 6f, center)
        }
        Text("${Quran.arabicDigits(heading.toInt())}°", fontSize = 24.sp, fontWeight = FontWeight.Light, color = ink, modifier = Modifier.offset(y = 60.dp))
    }
}

@Composable
private fun ArQibla(bearing: Float, heading: Float, km: Double, aligned: Boolean, modifier: Modifier) {
    val ctx = LocalContext.current
    val diff = diffTo(bearing, heading)
    val fov = 60f
    Box(modifier) {
        AndroidView(
            factory = { c ->
                val pv = PreviewView(c).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
                val fut = ProcessCameraProvider.getInstance(c)
                fut.addListener({
                    try {
                        var o: Context? = c
                        while (o is android.content.ContextWrapper && o !is LifecycleOwner) o = o.baseContext
                        val owner = o as? LifecycleOwner ?: return@addListener
                        val provider = fut.get()
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
                        provider.unbindAll()
                        provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                    } catch (_: Exception) {}
                }, ContextCompat.getMainExecutor(c))
                pv
            },
            modifier = Modifier.fillMaxSize()
        )
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth.toPx() }
            val visible = abs(diff) < fov / 2f
            // خط الأفق الإرشادي
            Box(Modifier.align(Alignment.Center).fillMaxWidth().height(1.dp).background(Color(0x55FFFFFF)))
            if (visible) {
                val x = (diff / fov * wPx).toInt()
                Column(
                    Modifier.align(Alignment.Center).offset { IntOffset(x, -120) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.size(92.dp).clip(CircleShape).background(Color(0x66000000)).border(3.dp, if (aligned) Green else Gold, CircleShape), contentAlignment = Alignment.Center) {
                        Text("🕋", fontSize = 52.sp)
                    }
                    Text("القبلة • ${Quran.arabicDigits(km.toInt())} كم", color = Color.White, fontSize = 13.sp, modifier = Modifier.background(Color(0x88000000), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
                }
            } else {
                Text(
                    if (diff > 0) "لفّ يمينًا ▶" else "◀ لفّ يسارًا",
                    Modifier.align(if (diff > 0) Alignment.CenterEnd else Alignment.CenterStart).padding(12.dp)
                        .background(Color(0x99000000), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                    color = Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold
                )
            }
            Column(Modifier.align(Alignment.TopCenter).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (aligned) "أنت تواجه القبلة ✓" else "الفرق عن القبلة: ${Quran.arabicDigits(abs(diff).toInt())}°",
                    color = if (aligned) Green else Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(Color(0x99000000), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            Text(
                "ارفع الهاتف رأسيًا أمامك. الواقع المعزز يعتمد على حساس البوصلة وقد يحتاج معايرة (حرّكه على شكل ٨).",
                Modifier.align(Alignment.BottomCenter).padding(10.dp).background(Color(0x88000000), RoundedCornerShape(12.dp)).padding(8.dp),
                color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center
            )
        }
    }
}
