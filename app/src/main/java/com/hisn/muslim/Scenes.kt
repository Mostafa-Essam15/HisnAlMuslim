package com.hisn.muslim

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.io.FileOutputStream
import java.util.Random

class SceneStyle(
    val name: String,
    val sky: List<Color>,
    val silhouette: Color,
    val glow: Color,
    val windows: Color,
    val stars: Boolean
)

val sceneStyles = listOf(
    SceneStyle(
        "ليل المسجد",
        listOf(Color(0xFF050B1F), Color(0xFF0E2A55), Color(0xFF1B4B7A)),
        Color(0xFF040A14), Color(0xFFF2D27A), Color(0xFFFFD36E), true
    ),
    SceneStyle(
        "الفجر",
        listOf(Color(0xFF1B1B3A), Color(0xFF6A3D7A), Color(0xFFF3A66B)),
        Color(0xFF140F24), Color(0xFFFFE2B0), Color(0xFFFFC06B), true
    ),
    SceneStyle(
        "الغروب",
        listOf(Color(0xFF2B0F2E), Color(0xFFA23E48), Color(0xFFF6B04C)),
        Color(0xFF1A0A14), Color(0xFFFFE9B8), Color(0xFFFFD28A), false
    ),
    SceneStyle(
        "الليل الأخضر",
        listOf(Color(0xFF031A14), Color(0xFF0B4F3C), Color(0xFF1E8A6A)),
        Color(0xFF02100C), Color(0xFFF2D27A), Color(0xFFFFE08A), true
    ),
    SceneStyle(
        "الصحراء الذهبية",
        listOf(Color(0xFF2A1A0E), Color(0xFF8A5A2B), Color(0xFFE8B96A)),
        Color(0xFF1B1108), Color(0xFFFFF0C8), Color(0xFFFFE08A), false
    ),
    SceneStyle(
        "النهار",
        listOf(Color(0xFF2E6FB5), Color(0xFF6FB1E6), Color(0xFFCFE8F7)),
        Color(0xFF1C2B38), Color(0xFFFFF6D8), Color(0xFFFFFFFF), false
    )
)

/** رقم تيار الخلفية «تلقائي حسب وقت اليوم» */
const val BG_AUTO = -1

/** المشهد المناسب لوقت اليوم الحالي حسب مواقيت الصلاة (ليل، فجر، نهار، عصر ذهبي، غروب) */
fun sceneNow(p: Prefs, now: Long = System.currentTimeMillis()): Int {
    return try {
        val t = Engine.times(p, java.util.Date(now))
        val fajr = t.fajr.time
        val sunrise = t.sunrise.time
        val dhuhr = t.dhuhr.time
        val asr = t.asr.time
        val maghrib = t.maghrib.time
        val isha = t.isha.time
        val m30 = 30 * 60_000L
        when {
            now < fajr || now >= isha -> 0
            now < sunrise + m30 -> 1
            now < asr -> 5
            now < maghrib - m30 -> 4
            now < isha -> 2
            else -> 0
        }
    } catch (e: Exception) {
        0
    }
}

/** رقم الخلفية = عدد المشاهد الجاهزة → صورتك الخاصة من المعرض */
const val customBgIndex = 100

@Composable
fun MosqueScene(styleIndex: Int, modifier: Modifier = Modifier) {
    val s = sceneStyles[styleIndex.coerceIn(0, sceneStyles.lastIndex)]
    Canvas(modifier) { drawMosqueScene(s) }
}

private fun DrawScope.drawMosqueScene(s: SceneStyle) {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(s.sky))

    // نجوم
    if (s.stars) {
        val rnd = Random(7)
        repeat(70) {
            val x = rnd.nextFloat() * w
            val y = rnd.nextFloat() * h * 0.5f
            val r = (0.5f + rnd.nextFloat() * 1.5f) * density
            drawCircle(Color.White.copy(alpha = 0.2f + rnd.nextFloat() * 0.6f), r, Offset(x, y))
        }
    }

    // هلال
    val c = Offset(w * 0.78f, h * 0.12f)
    val r = w * 0.06f
    drawCircle(
        Brush.radialGradient(listOf(s.glow.copy(alpha = 0.28f), Color.Transparent), c, r * 4.5f),
        r * 4.5f, c
    )
    val outer = Path().apply { addOval(Rect(c, r)) }
    val inner = Path().apply { addOval(Rect(Offset(c.x + r * 0.5f, c.y - r * 0.12f), r * 0.88f)) }
    drawPath(Path.combine(PathOperation.Difference, outer, inner), s.glow)

    // أرضية + وهج الأفق
    val base = h * 0.87f
    val u = w / 100f
    drawRect(
        Brush.verticalGradient(
            listOf(Color.Transparent, s.glow.copy(alpha = 0.22f)),
            startY = base - 22 * u, endY = base
        ),
        topLeft = Offset(0f, base - 22 * u),
        size = androidx.compose.ui.geometry.Size(w, 22 * u)
    )
    drawRect(s.silhouette, topLeft = Offset(0f, base), size = androidx.compose.ui.geometry.Size(w, h - base))

    val sil = s.silhouette

    // الجناحان
    drawRect(sil, Offset(14 * u, base - 8 * u), androidx.compose.ui.geometry.Size(72 * u, 8 * u))
    for (cx in listOf(26f, 74f)) {
        val dome = Path().apply {
            addArc(Rect(Offset((cx - 7) * u, base - 8 * u - 7 * u), Offset((cx + 7) * u, base - 8 * u + 7 * u)), 180f, 180f)
            close()
        }
        drawPath(dome, sil)
        drawLine(sil, Offset(cx * u, base - 15 * u), Offset(cx * u, base - 19 * u), strokeWidth = 0.5f * u)
    }

    // القاعة الوسطى
    val y1 = base - 15 * u
    drawRect(sil, Offset(32 * u, y1), androidx.compose.ui.geometry.Size(36 * u, 15 * u))
    val dome = Path().apply {
        moveTo(33 * u, y1)
        cubicTo(33 * u, y1 - 15 * u, 43 * u, y1 - 20 * u, 50 * u, y1 - 27 * u)
        cubicTo(57 * u, y1 - 20 * u, 67 * u, y1 - 15 * u, 67 * u, y1)
        close()
    }
    drawPath(dome, sil)
    drawLine(sil, Offset(50 * u, y1 - 27 * u), Offset(50 * u, y1 - 34 * u), strokeWidth = 0.7f * u)
    drawCircle(sil, 1.1f * u, Offset(50 * u, y1 - 30 * u))

    // المآذن
    for (mx in listOf(11f, 89f)) {
        val top = base - 46 * u
        drawRect(sil, Offset((mx - 1.6f) * u, top + 8 * u), androidx.compose.ui.geometry.Size(3.2f * u, 46 * u - 8 * u))
        drawRect(sil, Offset((mx - 2.8f) * u, base - 34 * u), androidx.compose.ui.geometry.Size(5.6f * u, 1.4f * u))
        drawRect(sil, Offset((mx - 2f) * u, top + 6 * u), androidx.compose.ui.geometry.Size(4f * u, 2f * u))
        val cap = Path().apply {
            moveTo((mx - 2.2f) * u, top + 6 * u)
            lineTo(mx * u, top - 3 * u)
            lineTo((mx + 2.2f) * u, top + 6 * u)
            close()
        }
        drawPath(cap, sil)
        drawLine(sil, Offset(mx * u, top - 3 * u), Offset(mx * u, top - 7 * u), strokeWidth = 0.5f * u)
    }

    // نوافذ مضيئة
    // cx و ww بالوحدات (u)، و hh بالبكسل
    fun arch(cx: Float, bottom: Float, ww: Float, hh: Float) {
        val left = (cx - ww / 2) * u
        val right = (cx + ww / 2) * u
        val half = ww * u / 2
        val p = Path().apply {
            moveTo(left, bottom)
            lineTo(left, bottom - hh)
            addArc(Rect(Offset(left, bottom - hh - half), Offset(right, bottom - hh + half)), 180f, 180f)
            lineTo(right, bottom)
            close()
        }
        drawPath(p, s.windows.copy(alpha = 0.85f))
    }
    for (cx in listOf(40f, 50f, 60f)) arch(cx, base, 3.4f, 7f * u)
    for (cx in listOf(22f, 30f, 70f, 78f)) arch(cx, base, 2.4f, 3.6f * u)
    for (mx in listOf(11f, 89f)) {
        drawRect(s.windows.copy(alpha = 0.7f), Offset((mx - 0.4f) * u, base - 28 * u), androidx.compose.ui.geometry.Size(0.8f * u, 4 * u))
        drawRect(s.windows.copy(alpha = 0.7f), Offset((mx - 0.4f) * u, base - 18 * u), androidx.compose.ui.geometry.Size(0.8f * u, 4 * u))
    }
    // قوس زخرفي رفيع حول الباب
    drawArc(
        s.glow.copy(alpha = 0.5f), 180f, 180f, false,
        Offset(45 * u, base - 14 * u), androidx.compose.ui.geometry.Size(10 * u, 10 * u),
        style = Stroke(0.4f * u)
    )
}

// ───────────── صورة الخلفية الخاصة ─────────────

fun customBgFile(ctx: Context) = File(ctx.filesDir, "bg_custom.jpg")

fun hasCustomBg(ctx: Context) = customBgFile(ctx).exists()

fun loadCustomBg(ctx: Context): ImageBitmap? = try {
    val f = customBgFile(ctx)
    if (!f.exists()) null else BitmapFactory.decodeFile(f.path)?.asImageBitmap()
} catch (e: Exception) {
    null
}

/** بيحفظ الصورة المختارة من المعرض بعد تصغيرها (أقصى ١٨٠٠ بكسل) */
fun saveCustomBg(ctx: Context, uri: Uri): Boolean = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / sample > 1800 || bounds.outHeight / sample > 1800) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val bmp: Bitmap? = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    if (bmp == null) false else {
        FileOutputStream(customBgFile(ctx)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        true
    }
} catch (e: Exception) {
    false
}

@Composable
fun AppBackground(bg: Int, version: Int, auto: Int = 0, autoPhoto: Int = PHOTO_BASE + 2, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val eff = when (bg) {
        BG_AUTO -> auto
        BG_AUTO_PHOTO -> autoPhoto
        else -> bg
    }
    Box(modifier.fillMaxSize()) {
        androidx.compose.animation.Crossfade(targetState = eff, animationSpec = androidx.compose.animation.core.tween(1500), label = "bg") { e ->
            Box(Modifier.fillMaxSize()) {
                if (e in 0 until sceneStyles.size) {
                    MosqueScene(e, Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Color(0x22000000)))
                } else {
                    val isPhoto = e >= PHOTO_BASE && e < PHOTO_BASE + 100
                    // فك الصورة في thread خلفي عشان ما يحصلش تقطيع أثناء التبديل
                    val loaded by androidx.compose.runtime.produceState<Pair<Boolean, androidx.compose.ui.graphics.ImageBitmap?>>(false to null, e, version) {
                        value = true to kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            if (isPhoto) Photos.load(ctx, e - PHOTO_BASE, 1) else loadCustomBg(ctx)
                        }
                    }
                    val bmp = loaded.second
                    if (bmp != null) {
                        Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else if (loaded.first) {
                        MosqueScene(0, Modifier.fillMaxSize())
                    } else {
                        Box(Modifier.fillMaxSize().background(Color(0xFF051510)))
                    }
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(listOf(Color(0x80000000), Color(0x55000000), Color(0xAA000000)))
                        )
                    )
                }
            }
        }
    }
}
