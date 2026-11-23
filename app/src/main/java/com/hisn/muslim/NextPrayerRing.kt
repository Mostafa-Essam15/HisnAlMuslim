package com.hisn.muslim

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * عدّاد الصلاة القادمة: حلقة ذهبية بتتملي مع الوقت لحد الأذان (طرفها بيلمع)، وجواها اسم الصلاة والوقت المتبقي.
 * الثواني في خانة ثابتة العرض عشان الأرقام ما تهتزّش.
 */
@Composable
fun NextPrayerRing(prayer: String, remainSec: Long, frac: Float, atText: String, rangeText: String?) {
    val f by animateFloatAsState(frac.coerceIn(0.005f, 1f), tween(900, easing = LinearEasing), label = "ring")
    val pulse by rememberInfiniteTransition(label = "p").animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "pa"
    )
    val gold = Gold
    val deep = GoldDeep
    val track = Track
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = 12.dp.toPx()
                val inset = sw + 8.dp.toPx()
                val d = size.minDimension - inset * 2
                val tl = Offset(inset, inset)
                val sz = Size(d, d)
                // حلقة خلفية + هالة ناعمة
                drawArc(track, 0f, 360f, false, tl, sz, style = Stroke(sw))
                drawArc(gold.copy(alpha = 0.08f), 0f, 360f, false, Offset(inset - 6.dp.toPx(), inset - 6.dp.toPx()), Size(d + 12.dp.toPx(), d + 12.dp.toPx()), style = Stroke(1.dp.toPx()))
                rotate(-90f) {
                    val sweep = 360f * f
                    drawArc(gold.copy(alpha = 0.20f), 0f, sweep, false, tl, sz, style = Stroke(sw * 2.1f, cap = StrokeCap.Round))
                    drawArc(
                        Brush.sweepGradient(0f to deep, f to Color(0xFFFFEFB0), 1f to deep, center = center),
                        0f, sweep, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round)
                    )
                }
                // نقطة لامعة عند طرف التقدّم
                val ang = Math.toRadians((-90.0 + 360.0 * f))
                val r = d / 2f
                val tip = Offset(center.x + (r * Math.cos(ang)).toFloat(), center.y + (r * Math.sin(ang)).toFloat())
                drawCircle(gold.copy(alpha = 0.35f * pulse), sw * 1.5f, tip)
                drawCircle(Color.White, sw * 0.42f, tip)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("الصلاة القادمة", fontSize = 12.sp, color = Muted)
                Text(prayer, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Gold, fontFamily = Serif)
                Spacer(Modifier.height(2.dp))
                val h = remainSec / 3600
                val m = (remainSec % 3600) / 60
                val s = remainSec % 60
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
                    Text(String.format(Locale.US, "%02d:%02d", h, m), fontSize = 38.sp, fontWeight = FontWeight.Medium)
                    Box(Modifier.width(30.dp), contentAlignment = Alignment.BottomStart) {
                        Text(
                            String.format(Locale.US, ":%02d", s), fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            color = Gold, modifier = Modifier.padding4()
                        )
                    }
                }
                Text("الساعة $atText", fontSize = 12.sp, color = Muted)
            }
        }
        if (rangeText != null) Text(rangeText, fontSize = 12.sp, color = Muted, textAlign = TextAlign.Center)
    }
}

private fun Modifier.padding4(): Modifier = this.then(Modifier.padding(bottom = 6.dp))
