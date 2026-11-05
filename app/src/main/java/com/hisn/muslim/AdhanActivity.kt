package com.hisn.muslim

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
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
fun AdhanScreen(prayer: Prayer, city: String, onStop: () -> Unit, onOpenApp: () -> Unit) {
    val tip = Content.tip(prayer)
    val time = SimpleDateFormat("hh:mm a", Locale("ar")).format(Date())

    val t = rememberInfiniteTransition(label = "pulse")
    val pulse by t.animateFloat(
        0.88f, 1.18f,
        infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    Box(Modifier.fillMaxSize()) {
        MosqueScene(sceneFor(prayer), Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0x88000000), Color(0x11000000), Color(0x99000000)))
            )
        )
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(16.dp))
                Text("حان الآن موعد أذان", color = Color(0xFFE9EFEC), fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                Box(contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(210.dp).scale(pulse)) {
                        drawCircle(Gold.copy(alpha = 0.10f))
                        drawCircle(Gold.copy(alpha = 0.22f), radius = size.minDimension * 0.42f)
                    }
                    Text(
                        prayer.ar, color = Gold, fontSize = 64.sp,
                        fontWeight = FontWeight.Bold, fontFamily = Serif
                    )
                }
                Text(time, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                if (city.isNotBlank()) Text(city, color = Muted, fontSize = 15.sp)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Column(
                    Modifier.fillMaxWidth()
                        .background(Color(0x99000000), RoundedCornerShape(22.dp))
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(tip.kind, color = Gold, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        tip.text, color = Color.White, fontSize = 18.sp, lineHeight = 30.sp,
                        textAlign = TextAlign.Center, fontFamily = Serif
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(tip.source, color = Muted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(22.dp))
                Button(
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black)
                ) { Text("إيقاف الأذان", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onOpenApp,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(40.dp),
                    border = BorderStroke(1.dp, Gold.copy(alpha = 0.6f))
                ) { Text("فتح التطبيق", color = Gold) }
            }
        }
    }
}
