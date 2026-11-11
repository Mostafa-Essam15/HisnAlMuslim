@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.hisn.muslim

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.batoulapps.adhan.PrayerTimes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private class PItem(val name: String, val time: Date, val prayer: Prayer?)

private fun items(t: PrayerTimes): List<PItem> = listOf(
    PItem(Prayer.FAJR.ar, t.fajr, Prayer.FAJR),
    PItem("الشروق", t.sunrise, null),
    PItem(Prayer.DHUHR.ar, t.dhuhr, Prayer.DHUHR),
    PItem(Prayer.ASR.ar, t.asr, Prayer.ASR),
    PItem(Prayer.MAGHRIB.ar, t.maghrib, Prayer.MAGHRIB),
    PItem(Prayer.ISHA.ar, t.isha, Prayer.ISHA)
)

/** شريط مواقيت اليوم تحت العدّاد: الصلاة القادمة بإطار ذهبي، والتي عدّت باهتة مع ✓ لو سجّلتها. */
@Composable
fun PrayerStrip(prefs: Prefs, times: PrayerTimes, next: Prayer?, day: String, now: Long) {
    val f = SimpleDateFormat("h:mm", Locale.US)
    androidx.compose.foundation.layout.FlowRow(
        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
        maxItemsInEachRow = if (bigFont()) 3 else 6
    ) {
        items(times).forEach { it ->
            val isNext = it.prayer != null && it.prayer == next
            val past = it.time.time < now
            val st = it.prayer?.let { p -> prefs.prayerStatus(day, p) } ?: 0
            Column(
                Modifier.weight(1f)
                    .then(if (isNext) Modifier.border(1.5.dp, Gold, RoundedCornerShape(14.dp)).background(Gold.copy(alpha = 0.14f), RoundedCornerShape(14.dp)) else Modifier)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(it.name, fontSize = 11.sp, color = if (isNext) Gold else Muted,
                    fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal)
                Text(f.format(it.time), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = if (past && !isNext) Muted else OnCard)
                Text(if (st == 2 || st == 1) "✓" else if (st == 3) "✗" else " ", fontSize = 11.sp,
                    color = if (st == 3) Color(0xFFFF8A80) else Gold)
            }
        }
    }
}

private fun statusLabel(s: Int) = when (s) { 2 -> "على وقتها"; 1 -> "متأخرة (قضاء)"; 3 -> "فاتتني"; else -> "" }

/** نافذة سفلية بكل مواقيت اليوم مع العدّاد والحالة. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerSheet(prefs: Prefs, times: PrayerTimes, next: Prayer?, day: String, now: Long, hijri: String, onSettings: () -> Unit, onDismiss: () -> Unit) {
    val f = SimpleDateFormat("hh:mm a", Locale("ar"))
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Ink, contentColor = OnCard) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("مواقيت اليوم", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Gold, fontFamily = Serif)
            Text(hijri + " • " + SimpleDateFormat("EEEE d MMMM yyyy", Locale("ar")).format(Date(now)), fontSize = 12.sp, color = Muted)
            Spacer(Modifier.height(10.dp))
            items(times).forEach { it ->
                val isNext = it.prayer != null && it.prayer == next
                val diff = (it.time.time - now) / 1000
                val st = it.prayer?.let { p -> prefs.prayerStatus(day, p) } ?: 0
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp)
                        .background(if (isNext) Gold.copy(alpha = 0.14f) else CardBg, RoundedCornerShape(16.dp))
                        .then(if (isNext) Modifier.border(1.5.dp, Gold, RoundedCornerShape(16.dp)) else Modifier)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(it.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = Serif, color = if (isNext) Gold else OnCard)
                        val sub = when {
                            isNext -> "القادمة • بعد %d:%02d".format(Locale.US, diff / 3600, (diff % 3600) / 60)
                            diff > 0 -> "بعد %d:%02d".format(Locale.US, diff / 3600, (diff % 3600) / 60)
                            st != 0 -> statusLabel(st)
                            it.prayer == null -> "عدّت"
                            else -> "عدّت • لم تُسجَّل"
                        }
                        Text(sub, fontSize = 12.sp, color = Muted)
                    }
                    Text(f.format(it.time), fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Gold, textAlign = TextAlign.End)
                }
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = { onDismiss(); onSettings() }) { Text("إعدادات الأذان والموقع", color = Gold) }
        }
    }
}
