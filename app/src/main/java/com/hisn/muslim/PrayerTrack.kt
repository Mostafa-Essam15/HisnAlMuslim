package com.hisn.muslim

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

private val CGreen = Color(0xFF6DB35A)
private val CGold = Color(0xFFD4AF5A)
private val CRed = Color(0xFFE0675A)
private val CGray = Color(0x33FFFFFF)

private fun statusColor(s: Int) = when (s) { 2 -> CGreen; 1 -> CGold; 3 -> CRed; else -> CGray }

private fun LocalDate.key() = toString() // yyyy-MM-dd = نفس صيغة Prefs.dayKey

private fun LocalDate.asDate(): Date = Date.from(atStartOfDay(ZoneId.systemDefault()).plusHours(12).toInstant())

/** شاشة «متابعة الصلوات»: تقويم بحلقات ملونة + إحصائيات. */
@Composable
fun PrayerTrackPane(prefs: Prefs) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GoldChip("التقويم", tab == 0) { tab = 0 }
            GoldChip("الإحصائيات", tab == 1) { tab = 1 }
        }
        if (tab == 0) TrackCalendar(prefs) else TrackStats(prefs)
    }
}

@Composable
private fun DayRing(statuses: List<Int>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.minDimension
        val stroke = w * 0.09f
        val inset = stroke / 2
        val sz = Size(w - stroke, w - stroke)
        for (i in 0 until 5) {
            drawArc(
                color = statusColor(statuses[i]), startAngle = -90f + i * 72f + 7f, sweepAngle = 58f, useCenter = false,
                topLeft = Offset(inset, inset), size = sz, style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
    }
}

private fun dayStatuses(prefs: Prefs, d: LocalDate): List<Int> = Prayer.values().map { prefs.prayerStatus(d.key(), it) }

@Composable
private fun TrackCalendar(prefs: Prefs) {
    var offset by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    val today = remember { LocalDate.now() }
    var selected by remember { mutableStateOf(today) }
    val first = remember(offset) { today.withDayOfMonth(1).plusMonths(offset.toLong()) }
    val len = first.lengthOfMonth()
    val startIdx = (first.dayOfWeek.value + 1) % 7 // السبت = 0
    val dayNames = listOf("س", "ح", "ن", "ث", "ر", "خ", "ج")
    val monthFmt = remember { SimpleDateFormat("MMMM yyyy", Locale("ar")) }
    val dayFmt = remember { SimpleDateFormat("EEEE، d MMMM yyyy", Locale("ar")) }
    val timeFmt = remember { SimpleDateFormat("hh:mm a", Locale("ar")) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { offset-- }) { Text("‹", color = Gold, fontSize = 22.sp) }
                Text(monthFmt.format(first.asDate()), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                TextButton(onClick = { if (offset < 0) offset++ }, enabled = offset < 0) { Text("›", color = if (offset < 0) Gold else Muted, fontSize = 22.sp) }
            }
            Row(Modifier.fillMaxWidth()) {
                dayNames.forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, color = Muted, fontSize = 12.sp) }
            }
        }
        val cells = startIdx + len
        val rows = (cells + 6) / 7
        items(rows) { r ->
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val idx = r * 7 + c
                    val dayNum = idx - startIdx + 1
                    Box(Modifier.weight(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (dayNum in 1..len) {
                            val d = first.withDayOfMonth(dayNum)
                            val future = d.isAfter(today)
                            val st = remember(d, tick) { dayStatuses(prefs, d) }
                            val sel = d == selected
                            Box(
                                Modifier.size(44.dp)
                                    .then(if (sel) Modifier.border(2.dp, Gold, RoundedCornerShape(14.dp)) else Modifier)
                                    .clickable(enabled = !future) { selected = d },
                                contentAlignment = Alignment.Center
                            ) {
                                DayRing(if (future) List(5) { 0 } else st, Modifier.size(38.dp))
                                Text(Quran.arabicDigits(dayNum), fontSize = 13.sp, color = if (future) Color(0x55FFFFFF) else Color.White, fontWeight = if (d == today) FontWeight.Bold else FontWeight.Normal)
                            }
                        } else Spacer(Modifier.size(44.dp))
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(2 to "في وقتها", 1 to "متأخرة", 3 to "لم أصلِّ", 0 to "غير مسجّلة").forEach { (s, l) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(statusColor(s), RoundedCornerShape(5.dp)))
                        Text(" $l", fontSize = 11.sp, color = Muted)
                    }
                }
            }
        }
        item {
            val sel = selected
            val times = remember(sel) { Engine.times(prefs, sel.asDate()) }
            val sts = remember(sel, tick) { dayStatuses(prefs, sel) }
            Glass {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(dayFmt.format(sel.asDate()), fontWeight = FontWeight.Bold)
                        Text(Fasting.hijriText(prefs, sel), fontSize = 12.sp, color = Muted)
                    }
                    Text("${Quran.arabicDigits(sts.count { it == 1 || it == 2 })} / ${Quran.arabicDigits(5)}", color = Gold, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Prayer.values().forEachIndexed { i, pr ->
                    val cur = sts[i]
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(pr.ar, fontWeight = FontWeight.Bold, fontFamily = Serif, fontSize = 18.sp)
                        Text(timeFmt.format(Engine.timeOf(times, pr)), fontSize = 12.sp, color = Muted)
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(2 to "في وقتها", 1 to "متأخرة", 3 to "لم أصلِّ").forEach { (s, l) ->
                            val on = cur == s
                            Box(
                                Modifier.weight(1f).height(38.dp)
                                    .background(if (on) statusColor(s).copy(alpha = 0.28f) else Color(0x14FFFFFF), RoundedCornerShape(12.dp))
                                    .border(1.5.dp, if (on) statusColor(s) else Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                                    .clickable {
                                        prefs.setPrayerStatus(sel.key(), pr, if (on) 0 else s)
                                        tick++; refreshTick.intValue++
                                    },
                                contentAlignment = Alignment.Center
                            ) { Text(l, fontSize = 12.sp, color = if (on) Color.White else Muted, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal) }
                        }
                    }
                }
                Text("«في وقتها» = أديتها في وقتها، «متأخرة» = قضاء. التسجيل منك أنت للمتابعة فقط.", fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 10.dp))
            }
        }
    }
}

@Composable
private fun TrackStats(prefs: Prefs) {
    var range by remember { mutableIntStateOf(7) }
    val tick = refreshTick.intValue
    val today = remember { LocalDate.now() }
    val days = remember(range, tick) { (range - 1 downTo 0).map { today.minusDays(it.toLong()) } }
    val grid = remember(range, tick) { days.map { dayStatuses(prefs, it) } }
    val flat = grid.flatten()
    val onTime = flat.count { it == 2 }
    val late = flat.count { it == 1 }
    val missed = flat.count { it == 3 }
    val none = flat.count { it == 0 }
    val recorded = onTime + late + missed
    val pct = if (recorded == 0) 0 else (onTime + late) * 100 / recorded
    val fullDays = grid.count { d -> d.all { it == 1 || it == 2 } }
    var longest = 0; var run = 0
    grid.forEach { d -> if (d.all { it == 1 || it == 2 }) { run++; if (run > longest) longest = run } else run = 0 }
    val streak = remember(tick) { prefs.streak() }
    val bucket = when (range) { 7, 30 -> 1; 90 -> 7; else -> 30 }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7 to "أسبوع", 30 to "شهر", 90 to "٣ أشهر", 365 to "سنة").forEach { (r, l) -> GoldChip(l, range == r) { range = r } }
            }
        }
        item {
            Glass {
                Text("آخر ${Quran.arabicDigits(range)} يوم", fontSize = 12.sp, color = Muted)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(Triple(2, "في وقتها", onTime), Triple(1, "متأخرة", late), Triple(3, "لم أصلِّ", missed), Triple(0, "غير مسجّلة", none)).forEach { (s, l, n) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).background(statusColor(s), RoundedCornerShape(5.dp)))
                                Text("  $l: ${Quran.arabicDigits(n)}", fontSize = 13.sp)
                            }
                        }
                    }
                    Box(Modifier.size(110.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val sw = size.minDimension * 0.13f
                            val sz = Size(size.width - sw, size.height - sw)
                            val tot = (onTime + late + missed + none).coerceAtLeast(1).toFloat()
                            var a = -90f
                            listOf(onTime to CGreen, late to CGold, missed to CRed, none to CGray).forEach { (n, c) ->
                                val sweep = 360f * n / tot
                                if (n > 0) drawArc(c, a, sweep, false, Offset(sw / 2, sw / 2), sz, style = Stroke(sw))
                                a += sweep
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("%${Quran.arabicDigits(pct)}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text("الالتزام", fontSize = 11.sp, color = Muted)
                        }
                    }
                }
            }
        }
        item {
            Glass {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf(streak to "أيام متواصلة", longest to "أطول سلسلة", fullDays to "أيام كاملة").forEach { (n, l) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(Quran.arabicDigits(n), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Gold)
                            Text(l, fontSize = 11.sp, color = Muted)
                        }
                    }
                }
            }
        }
        item {
            Glass {
                Text("الالتزام عبر الفترة", fontWeight = FontWeight.Bold)
                val buckets = grid.chunked(bucket)
                Row(Modifier.fillMaxWidth().height(150.dp).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(if (buckets.size > 14) 2.dp else 6.dp), verticalAlignment = Alignment.Bottom) {
                    buckets.forEachIndexed { bi, ch ->
                        val f = ch.flatten()
                        val cap = (ch.size * 5).toFloat()
                        val o = f.count { it == 2 } / cap; val l = f.count { it == 1 } / cap; val m = f.count { it == 3 } / cap
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                            Column(Modifier.fillMaxWidth().height(120.dp), verticalArrangement = Arrangement.Bottom) {
                                if (m > 0f) Box(Modifier.fillMaxWidth().height((120 * m).dp).background(CRed))
                                if (l > 0f) Box(Modifier.fillMaxWidth().height((120 * l).dp).background(CGold))
                                if (o > 0f) Box(Modifier.fillMaxWidth().height((120 * o).dp).background(CGreen, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)))
                            }
                            if (bucket == 1 && range == 7) {
                                val dn = listOf("إث", "ثل", "أر", "خم", "جم", "سب", "أح")
                                Text(dn[days[bi].dayOfWeek.value - 1], fontSize = 10.sp, color = Muted)
                            }
                        }
                    }
                }
            }
        }
        item {
            Glass {
                Text("التزامك بكل صلاة", fontWeight = FontWeight.Bold)
                Prayer.values().forEachIndexed { i, pr ->
                    val col = grid.map { it[i] }
                    val rec = col.count { it != 0 }
                    val o = col.count { it == 2 }; val l = col.count { it == 1 }; val m = col.count { it == 3 }
                    val p = if (rec == 0) 0 else (o + l) * 100 / rec
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(pr.ar, Modifier.weight(0.25f), fontFamily = Serif)
                        Row(Modifier.weight(0.6f).height(10.dp).background(CGray, RoundedCornerShape(5.dp))) {
                            if (rec > 0) {
                                if (o > 0) Box(Modifier.weight(o.toFloat()).height(10.dp).background(CGreen))
                                if (l > 0) Box(Modifier.weight(l.toFloat()).height(10.dp).background(CGold))
                                if (m > 0) Box(Modifier.weight(m.toFloat()).height(10.dp).background(CRed))
                            }
                        }
                        Text("%${Quran.arabicDigits(p)}", Modifier.weight(0.15f), textAlign = TextAlign.End, fontSize = 12.sp, color = Muted)
                    }
                }
                Text("الإحصاء من تسجيلك أنت فقط، والأيام غير المسجّلة لا تُحسب في النسبة.", fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}
