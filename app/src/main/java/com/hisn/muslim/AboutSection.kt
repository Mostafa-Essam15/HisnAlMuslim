package com.hisn.muslim

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val DEV_NAME = "Mostafa Essam"
private const val DEV_EMAIL = "Mostafaesam15@gmail.com"
private const val FB_URL = "https://www.facebook.com/share/1FdCUBXAXh/"
private const val WA_URL = "https://wa.me/201090033790"
private const val APP_LINK = "https://github.com/Mostafa-Essam15/HisnAlMuslim/releases/download/latest/HisnAlMuslim.apk"

private val Emerald = Color(0xFF0E3B2C)
private val Night = Color(0xFF06130E)

/** حركة ظهور هادئة (تلاشي + صعود خفيف) بتأخير يرتّب ظهور البطاقات */
@Composable
private fun Reveal(index: Int, content: @Composable () -> Unit) {
    var on by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(80L * index); on = true }
    val a by animateFloatAsState(if (on) 1f else 0f, tween(450), label = "a")
    val y by animateFloatAsState(if (on) 0f else 24f, tween(450), label = "y")
    Box(Modifier.alpha(a).graphicsLayer { translationY = y }) { content() }
}

/** ضغطة بتصغّر العنصر شوية (بدون موجة) */
@Composable
private fun Modifier.pressable(onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.96f else 1f, tween(120), label = "s")
    return this.scale(s).clickable(interactionSource = src, indication = null, onClick = onClick)
}

/** نجمة ثمانية (رُبع الحزب) ذهبية: زخرفة إسلامية خفيفة */
@Composable
private fun Star8(size: androidx.compose.ui.unit.Dp, alpha: Float = 1f, filled: Boolean = false) {
    val g = Gold
    Canvas(Modifier.size(size)) {
        val c = center; val r = this.size.minDimension / 2f
        for (rot in listOf(0f, 45f)) {
            val p = Path()
            for (k in 0 until 4) {
                val ang = Math.toRadians((rot + k * 90 + 45).toDouble())
                val x = c.x + r * Math.cos(ang).toFloat(); val y = c.y + r * Math.sin(ang).toFloat()
                if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            p.close()
            if (filled) drawPath(p, g.copy(alpha = alpha * 0.25f))
            drawPath(p, g.copy(alpha = alpha), style = Stroke(width = 2.2f))
        }
    }
}

@Composable
private fun GoldDivider() {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, Gold.copy(alpha = 0.7f)))))
        Spacer(Modifier.width(8.dp)); Star8(14.dp); Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(Gold.copy(alpha = 0.7f), Color.Transparent))))
    }
}

@Composable
private fun PremiumCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(Emerald.copy(alpha = 0.78f), Night.copy(alpha = 0.88f))))
            .border(BorderStroke(1.dp, Brush.linearGradient(listOf(Gold.copy(alpha = 0.55f), Gold.copy(alpha = 0.12f)))), RoundedCornerShape(26.dp))
            .padding(20.dp),
        content = content
    )
}

private fun toast(ctx: Context, s: String) = android.widget.Toast.makeText(ctx, s, android.widget.Toast.LENGTH_SHORT).show()

private fun open(ctx: Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { toast(ctx, "تعذّر فتح الرابط") }
}

private fun mail(ctx: Context) {
    runCatching {
        ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$DEV_EMAIL"))
            .putExtra(Intent.EXTRA_SUBJECT, "حصن المسلم — ملاحظات").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { toast(ctx, "لا يوجد تطبيق بريد على الجهاز") }
}

@Composable
private fun ActionPill(label: String, modifier: Modifier = Modifier, filled: Boolean = false, icon: @Composable () -> Unit, onClick: () -> Unit) {
    Row(
        modifier.heightIn(min = 52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (filled) Brush.horizontalGradient(listOf(GoldDeep, Gold)) else Brush.horizontalGradient(listOf(Color(0x22FFFFFF), Color(0x11FFFFFF))))
            .border(1.dp, Gold.copy(alpha = if (filled) 0f else 0.35f), RoundedCornerShape(18.dp))
            .pressable(onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        icon(); Spacer(Modifier.width(8.dp))
        Text(label, color = if (filled) Color(0xFF1A1405) else Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

/** قسم «عن التطبيق والمطوّر» بتصميم فاخر. كل البيانات حقيقية؛ ما فيش رابط تقييم لأن التطبيق مش منشور على متجر. */
fun LazyListScope.aboutItems() {
    item {
        val ctx = LocalContext.current
        val pi = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0) }.getOrNull() }
        val ver = pi?.versionName ?: "2.0"
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

            // ───── 1) التعريف بالتطبيق ─────
            Reveal(0) {
                PremiumCard {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(contentAlignment = Alignment.Center) {
                            Star8(92.dp, 0.9f, filled = true)
                            Icon(Icons.Rounded.AutoAwesome, null, tint = Gold, modifier = Modifier.size(32.dp))
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("حصن المسلم", color = Gold, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 30.sp)
                        Box(Modifier.padding(top = 6.dp).clip(RoundedCornerShape(50)).background(Gold.copy(alpha = 0.16f)).border(1.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 4.dp)) {
                            Text("النسخة $ver", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        GoldDivider()
                        Text(
                            "رفيقك اليومي إلى الطمأنينة وذكر الله، تطبيق يجمع الأذكار والأدعية في تجربة بسيطة وأنيقة، تساعدك على المحافظة على وردك اليومي والاقتراب من الله في كل وقت.",
                            color = Color.White.copy(alpha = 0.92f), fontSize = 15.sp, lineHeight = 26.sp, textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // ───── 2) بطاقة المطوّر ─────
            Reveal(1) {
                PremiumCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(64.dp).clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Gold, GoldDeep)))
                                .border(2.dp, Gold.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) { Text("ME", color = Color(0xFF1A1405), fontWeight = FontWeight.Black, fontSize = 22.sp) }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(DEV_NAME, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                            Text("Electrical Engineer | Application Developer", color = Gold, fontSize = 12.sp)
                            Text("Is an Egyptian electrical engineer", color = Muted, fontSize = 11.sp)
                        }
                    }
                    GoldDivider()
                    Text(
                        "أؤمن أن التكنولوجيا يمكن أن تكون وسيلة لنشر الخير وتسهيل الوصول إلى المعرفة النافعة.\n\nبدأت تطوير هذا التطبيق بدافع المحبة والرغبة في تقديم عمل نافع، يجمع بين البساطة والتصميم العصري، ويساعد المسلمين على جعل ذكر الله جزءًا من حياتهم اليومية.",
                        color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, lineHeight = 25.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("هذا التطبيق خالص لوجه الله تعالى 🤍", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            // ───── 3) رسالتنا ─────
            Reveal(2) {
                PremiumCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Favorite, null, tint = Gold, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("رسالتنا", color = Gold, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("أن يكون هذا العمل سببًا في تذكيرك بالله، وأن ينتفع به كل من يستخدمه.", color = Color.White, fontSize = 15.sp, lineHeight = 26.sp)
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Gold.copy(alpha = 0.10f)).border(1.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(18.dp)).padding(16.dp)) {
                        Text(
                            "«اللهم اجعل هذا العمل خالصًا لوجهك الكريم، واجعله علمًا يُنتفع به وصدقةً جارية».",
                            color = Gold, fontFamily = Serif, fontSize = 17.sp, lineHeight = 30.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // ───── 4) التواصل ─────
            Reveal(3) {
                PremiumCard {
                    Text("تواصل مع المطوّر", color = Gold, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x22FFFFFF)).padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Email, null, tint = Gold, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(DEV_EMAIL, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Box(Modifier.size(40.dp).clip(CircleShape).pressable {
                            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("email", DEV_EMAIL))
                            toast(ctx, "تم نسخ البريد الإلكتروني")
                        }, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.ContentCopy, "نسخ البريد", tint = Gold, modifier = Modifier.size(20.dp)) }
                    }
                    Spacer(Modifier.height(12.dp))
                    ActionPill("تواصل عبر البريد", Modifier.fillMaxWidth(), filled = true, icon = { Icon(Icons.Rounded.Email, null, tint = Color(0xFF1A1405), modifier = Modifier.size(20.dp)) }) { mail(ctx) }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActionPill("Facebook", Modifier.weight(1f), icon = { Image(painterResource(R.drawable.ic_facebook), null, Modifier.size(24.dp)) }) { open(ctx, FB_URL) }
                        ActionPill("WhatsApp", Modifier.weight(1f), icon = { Image(painterResource(R.drawable.ic_whatsapp), null, Modifier.size(24.dp)) }) { open(ctx, WA_URL) }
                    }
                    Spacer(Modifier.height(10.dp))
                    ActionPill("شارك التطبيق مع أصدقائك", Modifier.fillMaxWidth(), icon = { Icon(Icons.Rounded.Share, null, tint = Gold, modifier = Modifier.size(20.dp)) }) {
                        shareText(ctx, "حصن المسلم — أذكار وأدعية ومواقيت الصلاة والمصحف في تطبيق واحد.\nتحميل مباشر (ملف APK):\n$APP_LINK")
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("أرسل أي ملاحظة أو خطأ في نص أو مصدر وسيتم تصحيحه.", color = Muted, fontSize = 11.sp)
                }
            }

            // ───── 5) معلومات التطبيق ─────
            Reveal(4) {
                val build = pi?.let { if (android.os.Build.VERSION.SDK_INT >= 28) it.longVersionCode else @Suppress("DEPRECATION") it.versionCode.toLong() }
                val updated = pi?.lastUpdateTime?.takeIf { it > 0 }?.let { SimpleDateFormat("d MMMM yyyy", Locale("ar")).format(Date(it)) }
                PremiumCard {
                    Text("معلومات التطبيق", color = Gold, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(Modifier.height(6.dp))
                    @Composable fun row(k: String, v: String) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(k, color = Muted, fontSize = 13.sp)
                            Text(v, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.End, modifier = Modifier.weight(1f).padding(start = 12.dp))
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x14FFFFFF)))
                    }
                    row("اسم التطبيق", "حصن المسلم")
                    row("الإصدار", if (build != null) "$ver (بناء $build)" else ver)
                    if (updated != null) row("آخر تحديث للنسخة المثبتة", updated)
                    row("المطوّر", DEV_NAME)
                    row("حقوق الملكية", "© $DEV_NAME — جميع الحقوق محفوظة")
                }
            }

            // ───── 6) الختام ─────
            Reveal(5) {
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Star8(26.dp, 0.8f)
                    Spacer(Modifier.height(8.dp))
                    Text("شكرًا لأنك جزء من رحلتنا في نشر الخير 🤍", color = Gold, fontFamily = Serif, fontSize = 17.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Text("صُنع بحب لخدمة كتاب الله وسنة رسوله ﷺ.", color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(4.dp))
                    Text("$DEV_NAME ©", color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
}
