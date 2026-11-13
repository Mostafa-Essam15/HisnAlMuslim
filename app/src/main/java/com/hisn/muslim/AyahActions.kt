package com.hisn.muslim

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** قائمة الآية في المصحف: تحديد نص، نسخ، مشاركة كنص، مشاركة كصورة. النص من نص المصحف المحمّل (حفص). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AyahActionsSheet(surah: Int, ayah: Int, onRange: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    var ready by remember { mutableStateOf(Quran.loaded) }
    LaunchedEffect(Unit) { if (!ready) ready = withContext(Dispatchers.IO) { Quran.load(ctx) } }
    var from by remember(surah, ayah) { mutableIntStateOf(ayah) }
    var to by remember(surah, ayah) { mutableIntStateOf(ayah) }
    var selectMode by remember(surah, ayah) { mutableStateOf(false) }
    val count = Quran.surahs.firstOrNull { it.number == surah }?.ayahCount ?: ayah
    LaunchedEffect(from, to) { onRange(from, to) }
    val name = Quran.surahName(surah)
    val sel = remember(from, to, ready) { Quran.ayahs.filter { it.surah == surah && it.num in from..to } }
    val body = sel.joinToString(" ") { it.text }
    val src = if (from == to) "[سورة $name: ${Quran.arabicDigits(from)}]" else "[سورة $name: ${Quran.arabicDigits(from)}–${Quran.arabicDigits(to)}]"
    val shareBody = "﴿ $body ﴾"

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Ink, contentColor = OnCard) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("سورة $name • ${if (from == to) "آية ${Quran.arabicDigits(from)}" else "الآيات ${Quran.arabicDigits(from)}–${Quran.arabicDigits(to)}"}", color = Gold, fontWeight = FontWeight.Bold)
            if (!ready || sel.isEmpty()) {
                Text("نص المصحف غير محمّل بعد. حمّله من وضع القراءة النصي ثم ارجع هنا.", color = Muted, fontSize = 13.sp)
            } else {
                val t = @Composable { Text(body, fontFamily = Serif, fontSize = if (selectMode) 24.sp else 20.sp, lineHeight = if (selectMode) 44.sp else 36.sp) }
                if (selectMode) SelectionContainer { t() } else t()
                if (selectMode) Text("اضغط مطولًا على الكلمة ثم اسحب لتحديد الجزء الذي تريده، ثم انسخه أو شاركه من القائمة التي تظهر.", color = Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { if (from > 1) from-- }, enabled = from > 1) { Text("＋ آية قبلها", color = Gold, fontSize = 12.sp) }
                    TextButton(onClick = { if (to < count) to++ }, enabled = to < count) { Text("＋ آية بعدها", color = Gold, fontSize = 12.sp) }
                }
                FlowRowButtons(
                    listOf(
                        (if (selectMode) "✓ تم التحديد" else "تحديد نص") to { selectMode = !selectMode },
                        "نسخ" to {
                            clip.setText(AnnotatedString("$shareBody\n$src"))
                            android.widget.Toast.makeText(ctx, "تم النسخ", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        "مشاركة كنص" to { shareText(ctx, "$shareBody\n$src") },
                        "مشاركة كصورة" to { ShareCard.share(ctx, shareBody, src) }
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowButtons(items: List<Pair<String, () -> Unit>>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (label, act) ->
            Button(onClick = act, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = androidx.compose.ui.graphics.Color.Black)) { Text(label) }
        }
    }
}
