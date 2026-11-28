package com.hisn.muslim

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/** عنصر قابل للتنزيل في قسم «التنزيلات». المفتاح: mushaf | tafsir:<slug> | audio:<reciter> | adhan:<key> */
class DlItem(val key: String, val title: String, val group: String)

private fun dirSize(f: File): Double = if (!f.exists()) 0.0 else f.walkTopDown().filter { it.isFile }.sumOf { it.length() } / 1048576.0

private fun mb(v: Double) = if (v < 0.5) "٠" else Quran.arabicDigits(Math.round(v).toInt())

/** طابور تنزيل واحد يشتغل واحدة واحدة (حتى مايتخنقش النت) ويفضل شغال لو خرجت من الصفحة. */
object DownloadQueue {
    var running by mutableStateOf(false)
    var label by mutableStateOf("")
    var done by mutableIntStateOf(0)
    var total by mutableIntStateOf(0)
    var version by mutableIntStateOf(0)
    @Volatile private var cancel = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun stop() { cancel = true; QuranAudio.cancelDownload() }

    fun liveFrac(): Float = when {
        label.startsWith("المصحف") -> MushafStore.bulkProgress / Quran.TOTAL_PAGES.toFloat()
        label.startsWith("تفسير") || label.startsWith("التفسير") -> TafsirStore.bulkProgress / 114f
        QuranAudio.dlTotal > 0 -> (QuranAudio.dlDone + QuranAudio.dlFailed) / QuranAudio.dlTotal.toFloat()
        else -> 0f
    }.coerceIn(0f, 1f)

    fun start(ctx: Context, items: List<DlItem>) {
        if (running || items.isEmpty()) return
        val app = ctx.applicationContext
        running = true; cancel = false; done = 0; total = items.size
        scope.launch {
            for (it in items) {
                if (cancel) break
                label = it.title
                try {
                    val k = it.key
                    when {
                        k == "mushaf" -> MushafStore.downloadAll(app)
                        k.startsWith("tafsir:") -> TafsirStore.downloadAll(app, k.removePrefix("tafsir:"))
                        k.startsWith("audio:") -> {
                            QuranAudio.download(app, k.removePrefix("audio:"), Quran.ayahs, "المصحف كله")
                            delay(600)
                            while (QuranAudio.dlActive && !cancel) delay(500)
                        }
                        k.startsWith("adhan:") -> Muezzins.download(app, Muezzins.byKey(k.removePrefix("adhan:")))
                    }
                } catch (_: Exception) {}
                done++
            }
            running = false
            version++
        }
    }

    fun delete(ctx: Context, items: List<DlItem>) {
        items.forEach {
            val k = it.key
            when {
                k == "mushaf" -> File(ctx.filesDir, "mushaf").deleteRecursively()
                k.startsWith("tafsir:") -> File(ctx.filesDir, "tafsir/" + k.removePrefix("tafsir:")).deleteRecursively()
                k.startsWith("audio:") -> QuranAudio.deleteStored(ctx, k.removePrefix("audio:"))
                k.startsWith("adhan:") -> Muezzins.delete(ctx, Muezzins.byKey(k.removePrefix("adhan:")))
            }
        }
        version++
    }
}

private class DlState(val text: String, val complete: Boolean, val partial: Boolean)

private fun stateOf(ctx: Context, k: String): DlState = when {
    k == "mushaf" -> {
        val n = MushafStore.downloadedCount(ctx)
        DlState("${Quran.arabicDigits(n)} من ${Quran.arabicDigits(Quran.TOTAL_PAGES)} صفحة • ${mb(dirSize(File(ctx.filesDir, "mushaf")))} ميجا", n >= Quran.TOTAL_PAGES, n > 0)
    }
    k.startsWith("tafsir:") -> {
        val slug = k.removePrefix("tafsir:")
        val n = TafsirStore.downloadedSurahs(ctx, slug)
        DlState("${Quran.arabicDigits(n)} من ١١٤ سورة • ${mb(dirSize(File(ctx.filesDir, "tafsir/$slug")))} ميجا", n >= 114, n > 0)
    }
    k.startsWith("audio:") -> {
        val s = QuranAudio.stored(ctx, k.removePrefix("audio:"))
        DlState("${Quran.arabicDigits(s.first)} من ${Quran.arabicDigits(Quran.ayahs.size)} آية • ${mb(s.second)} ميجا", s.first >= Quran.ayahs.size, s.first > 0)
    }
    else -> {
        val ok = Muezzins.isDownloaded(ctx, Muezzins.byKey(k.removePrefix("adhan:")))
        DlState(if (ok) "محمّل" else "غير محمّل", ok, false)
    }
}

/** تبويب «التنزيلات» في الإعدادات: اختَر اللي عايزه وحمّله مرة واحدة. */
fun LazyListScope.downloadsItems(ctx: Context) {
    val groups: List<Pair<String, List<DlItem>>> = listOf(
        "المصحف" to listOf(DlItem("mushaf", "المصحف (٦٠٤ صفحة بخط المدينة)", "المصحف")),
        "التفاسير" to TafsirStore.editions.map { DlItem("tafsir:${it.slug}", it.name, "التفاسير") },
        "صوت القرّاء (المصحف كله)" to QuranAudio.reciters.filter { it.surahUrl == null }
            .map { DlItem("audio:${it.id}", "صوت ${it.name}", "صوت القرّاء") },
        "المؤذنون" to Muezzins.all.filter { !it.isCustom }.map { DlItem("adhan:${it.key}", it.name, "المؤذنون") }
    )
    val all = groups.flatMap { it.second }
    item {
        val sel = remember { mutableStateListOf<String>() }
        var confirm by remember { mutableStateOf(false) }
        val ver = DownloadQueue.version
        val stateMap = remember(ver) { all.associate { it.key to stateOf(ctx, it.key) } }
        val used = remember(ver) {
            dirSize(File(ctx.filesDir, "mushaf")) + dirSize(File(ctx.filesDir, "tafsir")) +
                dirSize(File(ctx.filesDir, "quran_audio")) + dirSize(File(ctx.filesDir, "adhan"))
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.fillMaxWidth().glassSurface(24.dp).padding(18.dp)) {
                SectionTitle("التنزيلات", Icons.Rounded.CheckCircle)
                Text("المساحة المستخدمة: ${mb(used)} ميجا", fontSize = 13.sp, color = Muted)
                Text("علّم على اللي عايز تحمّله وبعدين اضغط «تحميل». بيتحمّلوا واحد ورا التاني، وبعدها بيشتغلوا بدون إنترنت.", fontSize = 12.sp, color = Muted)
                Spacer(Modifier.height(10.dp))
                if (DownloadQueue.running) {
                    Text("جاري: ${DownloadQueue.label} (${Quran.arabicDigits(DownloadQueue.done + 1)} من ${Quran.arabicDigits(DownloadQueue.total)})", color = Gold, fontSize = 13.sp)
                    LinearProgressIndicator(progress = { DownloadQueue.liveFrac() }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(8.dp).clip(CircleShape), color = Gold, trackColor = Track)
                    Text("سيب التطبيق مفتوحًا لحد ما يخلص.", fontSize = 11.sp, color = Muted)
                    OutlinedButton(onClick = { DownloadQueue.stop() }) { Text("إيقاف", color = Gold) }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = sel.isNotEmpty(),
                            onClick = { DownloadQueue.start(ctx, all.filter { it.key in sel }); sel.clear() },
                            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black)
                        ) { Text("تحميل المحدد (${Quran.arabicDigits(sel.size)})") }
                        OutlinedButton(enabled = sel.isNotEmpty(), onClick = { confirm = true }) { Text("حذف المحدد", color = if (sel.isNotEmpty()) Color(0xFFFF8A80) else Muted) }
                    }
                    Row {
                        TextButton(onClick = { sel.clear(); sel.addAll(all.filter { stateMap[it.key]?.complete != true }.map { it.key }) }) { Text("حدّد غير المحمّل", color = Gold, fontSize = 12.sp) }
                        TextButton(onClick = { sel.clear() }) { Text("إلغاء التحديد", color = Muted, fontSize = 12.sp) }
                    }
                    val err = QuranAudio.dlError ?: MushafStore.bulkError ?: TafsirStore.bulkMsg
                    if (err != null) Text(err, fontSize = 11.sp, color = Muted)
                }
            }
            groups.forEach { (title, items) ->
                Column(Modifier.fillMaxWidth().glassSurface(24.dp).padding(vertical = 10.dp, horizontal = 8.dp)) {
                    Text(title, color = Gold, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    items.forEach { d ->
                        val st = stateMap[d.key]
                        val on = d.key in sel
                        Row(
                            Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                                .clickable(enabled = !DownloadQueue.running) { if (on) sel.remove(d.key) else sel.add(d.key) }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = on, enabled = !DownloadQueue.running, onCheckedChange = { if (it) sel.add(d.key) else sel.remove(d.key) },
                                colors = CheckboxDefaults.colors(checkedColor = Gold, checkmarkColor = Color.Black))
                            Column(Modifier.weight(1f)) {
                                Text(d.title, fontSize = 14.sp)
                                Text(st?.text ?: "", fontSize = 11.sp, color = if (st?.partial == true && !st.complete) Gold else Muted)
                            }
                            if (st?.complete == true) Icon(Icons.Rounded.CheckCircle, null, tint = Gold, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
        if (confirm) AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = Ink, titleContentColor = Gold, textContentColor = OnCard,
            title = { Text("حذف المحدد؟") },
            text = { Text("هيتحذف من الجهاز ${Quran.arabicDigits(sel.size)} عنصر، وتقدر تحمّله تاني بعدين.") },
            confirmButton = { TextButton(onClick = { DownloadQueue.delete(ctx, all.filter { it.key in sel }); sel.clear(); confirm = false }) { Text("حذف", color = Color(0xFFFF8A80)) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("إلغاء", color = Gold) } }
        )
    }
}
