package com.hisn.muslim

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** شاشة الأذان الكاملة (بتظهر فوق شاشة القفل وقت الأذان) */
class AdhanActivity : ComponentActivity() {
    // لما الشاشة الكاملة تظهر فعلًا نشيل النافذة العائمة عشان ما يبقاش فيه شاشتين أذان فوق بعض
    override fun onResume() {
        super.onResume()
        ForcedOverlay.dismiss(this, "adhan")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val prayer = runCatching { Prayer.valueOf(intent.getStringExtra("prayer") ?: "") }
            .getOrDefault(Prayer.FAJR)
        val prefs = Prefs(this)

        setContent {
            HisnTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    // لما الأذان يخلص (أو يتوقف) الشاشة تقفل لوحدها
                    LaunchedEffect(Unit) {
                        snapshotFlow { AdhanState.playing.value }.collect { if (!it) finish() }
                    }
                    AdhanScreen(
                        prayer = prayer,
                        city = prefs.city,
                        hijri = Fasting.hijriText(prefs),
                        onStop = { AdhanService.stop(this@AdhanActivity) },
                        onOpenApp = {
                            startActivity(
                                Intent(this@AdhanActivity, MainActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            )
                            finish()
                        }
                    )
                }
            }
        }
    }
}

private fun sceneFor(p: Prayer) = when (p) {
    Prayer.FAJR -> 1
    Prayer.DHUHR -> 3
    Prayer.ASR -> 4
    Prayer.MAGHRIB -> 2
    Prayer.ISHA -> 0
}

@Composable
fun AdhanScreen(prayer: Prayer, city: String, hijri: String = "", onStop: () -> Unit, onOpenApp: () -> Unit) {
    val tip = Content.tip(prayer)
    val time = SimpleDateFormat("hh:mm a", Locale("ar")).format(Date())

    // الهالة بتتنفّس بنعومة (الشفافية بس، من غير تكبير/تصغير للحلقة)
    val t = rememberInfiniteTransition(label = "breath")
    val glow by t.animateFloat(
        0.10f, 0.30f,
        infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )
    // موجات ناعمة بتتوسّع من الحلقة (زي صدى الصوت)
    val rip by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "rip")
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val appear by animateFloatAsState(if (shown) 1f else 0f, tween(700), label = "appear")
    val gold = Gold
    val cardShape = RoundedCornerShape(26.dp)

    Box(Modifier.fillMaxSize()) {
        MosqueScene(sceneFor(prayer), Modifier.fillMaxSize())
        // تعتيم خفيف فوق وتحت بس عشان النص يتقري ومشهد المسجد يفضل ظاهر
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color(0xAA000000), 0.32f to Color(0x10000000),
                    0.55f to Color(0x10000000), 1f to Color(0xDD000000)
                )
            )
        )
        Column(
            Modifier.fillMaxSize().alpha(appear).padding(horizontal = 24.dp, vertical = 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("حان الآن موعد أذان", color = Color(0xFFE9EFEC), fontSize = 16.sp)
                Spacer(Modifier.height(14.dp))
                Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(
                            Brush.radialGradient(listOf(gold.copy(alpha = glow), Color.Transparent), radius = size.minDimension / 2f)
                        )
                        for (k in 0..1) {
                            val ph = (rip + k * 0.5f) % 1f
                            drawCircle(gold.copy(alpha = (1f - ph) * 0.35f), radius = size.minDimension * (0.40f + 0.10f * ph), style = Stroke(1.5.dp.toPx()))
                        }
                        drawCircle(gold.copy(alpha = 0.85f), radius = size.minDimension * 0.40f, style = Stroke(3.dp.toPx()))
                        drawCircle(Color.White.copy(alpha = 0.10f), radius = size.minDimension * 0.44f, style = Stroke(1.dp.toPx()))
                    }
                    Text(prayer.label, color = gold, fontSize = 52.sp, fontWeight = FontWeight.Bold, fontFamily = Serif)
                }
                Spacer(Modifier.height(10.dp))
                Text(time, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                val sub = listOf(hijri, city).filter { it.isNotBlank() }.joinToString(" • ")
                if (sub.isNotBlank()) Text(sub, color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Column(
                    Modifier.fillMaxWidth().clip(cardShape)
                        .background(Color(0x66071611))
                        .background(Brush.verticalGradient(listOf(Color(0x22FFFFFF), Color(0x05FFFFFF))))
                        .border(1.dp, Brush.linearGradient(listOf(gold.copy(alpha = 0.55f), Color(0x22FFFFFF), gold.copy(alpha = 0.22f))), cardShape)
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(tip.kind, color = gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        tip.text, color = Color.White, fontSize = 17.sp, lineHeight = 28.sp,
                        textAlign = TextAlign.Center, fontFamily = Serif,
                        maxLines = 4, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(tip.source, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (prayer == Prayer.FAJR) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "في أذان الفجر يُقال: «الصلاة خير من النوم» مرتين",
                            color = gold, fontSize = 13.sp, textAlign = TextAlign.Center
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(
                    Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(gold).clickable { onStop() },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                ) { Text("⏹  إيقاف الأذان", color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                TextButton(onClick = onOpenApp) { Text("فتح التطبيق", color = gold, fontSize = 15.sp) }
            }
        }
    }
}
