package com.hisn.muslim

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/** نافذة تنزيل صوت القارئ للاستماع بدون إنترنت: الصفحة / السورة / الجزء / المصحف كله. */
@Composable
fun AudioDownloadDialog(reciter: String, page: Int, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var bump by remember { mutableIntStateOf(0) }
    val name = QuranAudio.reciters.firstOrNull { it.id == reciter }?.name ?: reciter
    val stored = remember(bump, QuranAudio.dlDone, QuranAudio.dlActive) { QuranAudio.stored(ctx, reciter) }
    val first = Quran.byPage[page]?.firstOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink, titleContentColor = Gold, textContentColor = OnCard,
        title = { Text("تنزيل صوت: $name") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "المحمّل على الجهاز: ${Quran.arabicDigits(stored.first)} آية (${"%.0f".format(Locale.US, stored.second)} ميجا). " +
                        "اللي بيتحمّل بيشتغل بدون إنترنت.",
                    fontSize = 12.sp, color = Muted
                )
                if (QuranAudio.dlActive) {
                    val done = QuranAudio.dlDone + QuranAudio.dlFailed
                    Text("${QuranAudio.dlLabel}: ${Quran.arabicDigits(done)} من ${Quran.arabicDigits(QuranAudio.dlTotal)}", fontSize = 13.sp, color = Gold)
                    LinearProgressIndicator(progress = { done / QuranAudio.dlTotal.toFloat() }, modifier = Modifier.fillMaxWidth(), color = Gold, trackColor = Track)
                    Text("سيب التطبيق مفتوحًا لحد ما يخلص.", fontSize = 11.sp, color = Muted)
                    OutlinedButton(onClick = { QuranAudio.cancelDownload() }) { Text("إيقاف التنزيل", color = Gold) }
                } else {
                    if (QuranAudio.dlTotal > 0) Text("خلص: تم تنزيل ${Quran.arabicDigits(QuranAudio.dlDone)}" + if (QuranAudio.dlFailed > 0) " وفشل ${Quran.arabicDigits(QuranAudio.dlFailed)}" else "", fontSize = 13.sp, color = Gold)
                    OutlinedButton(onClick = { QuranAudio.download(ctx, reciter, Quran.byPage[page].orEmpty(), "الصفحة") }, modifier = Modifier.fillMaxWidth()) { Text("الصفحة الحالية", color = Gold) }
                    if (first != null) {
                        OutlinedButton(onClick = { QuranAudio.download(ctx, reciter, Quran.ayahs.filter { it.surah == first.surah }, Quran.surahName(first.surah)) }, modifier = Modifier.fillMaxWidth()) {
                            Text("السورة الحالية: ${Quran.surahName(first.surah)}", color = Gold)
                        }
                        OutlinedButton(onClick = { QuranAudio.download(ctx, reciter, Quran.ayahs.filter { it.juz == first.juz }, "الجزء ${Quran.arabicDigits(first.juz)}") }, modifier = Modifier.fillMaxWidth()) {
                            Text("الجزء الحالي: ${Quran.arabicDigits(first.juz)}", color = Gold)
                        }
                    }
                    OutlinedButton(onClick = { QuranAudio.download(ctx, reciter, Quran.ayahs, "المصحف كله") }, modifier = Modifier.fillMaxWidth()) {
                        Text("المصحف كله (حجم كبير: مئات الميجا)", color = Gold)
                    }
                    if (stored.first > 0) TextButton(onClick = { QuranAudio.deleteStored(ctx, reciter); bump++ }) { Text("حذف صوت هذا القارئ من الجهاز", color = Color(0xFFFF8A80)) }
                }
                QuranAudio.dlError?.let { Text(it, fontSize = 12.sp, color = Color(0xFFFF8A80)) }
                Spacer(Modifier.height(2.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق", color = Gold) } }
    )
}
