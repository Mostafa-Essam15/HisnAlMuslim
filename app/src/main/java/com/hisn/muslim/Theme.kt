package com.hisn.muslim

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Gold = Color(0xFFE8C766)
val GoldDeep = Color(0xFFD4AF37)
val Ink = Color(0xFF07130F)
val CardBg = Color(0xB3071611)
val Muted = Color(0xFFC9D4CF)
val Serif = FontFamily.Serif

class Tab(val title: String, val icon: ImageVector)

val appTabs = listOf(
    Tab("الرئيسية", Icons.Rounded.Home),
    Tab("المصحف", Icons.Rounded.MenuBook),
    Tab("الأذكار", Icons.Rounded.AutoAwesome),
    Tab("التحديات", Icons.Rounded.EmojiEvents),
    Tab("الإعدادات", Icons.Rounded.Settings)
)

@Composable
fun HisnTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Gold,
            onPrimary = Color.Black,
            secondary = GoldDeep,
            surface = Ink,
            onSurface = Color.White,
            background = Ink,
            onBackground = Color.White
        ),
        content = content
    )
}

@Composable
fun Glass(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg, contentColor = Color.White),
        border = BorderStroke(1.dp, Gold.copy(alpha = 0.28f))
    ) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

@Composable
fun SectionTitle(text: String, icon: ImageVector? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, tint = Gold)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = Gold, fontWeight = FontWeight.Bold, fontSize = 18.sp, fontFamily = Serif)
    }
}
