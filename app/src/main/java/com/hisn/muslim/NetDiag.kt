package com.hisn.muslim

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** فحص الاتصال: بيجرّب كل مصدر (DNS ثم HTTPS) ويطلّع سبب الفشل بالظبط عشان نعرف المشكلة من شاشة واحدة. */
object NetDiag {
    private val hosts = listOf(
        "everyayah.com" to "https://everyayah.com/data/Alafasy_128kbps/001001.mp3",
        "archive.org" to "https://archive.org/download/Hassan_Saleh_Hafs/001001.mp3",
        "github.com" to "https://github.com/",
        "raw.githubusercontent.com" to "https://raw.githubusercontent.com/",
        "google.com (للمقارنة)" to "https://www.google.com/generate_204"
    )

    fun run(ctx: Context): String {
        val sb = StringBuilder()
        sb.appendLine("الوقت على الجهاز: " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()))
        sb.appendLine("الجهاز: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} • أندرويد ${android.os.Build.VERSION.RELEASE}")
        try {
            val mode = android.provider.Settings.Global.getString(ctx.contentResolver, "private_dns_mode")
            val spec = android.provider.Settings.Global.getString(ctx.contentResolver, "private_dns_specifier")
            sb.appendLine("Private DNS: ${mode ?: "؟"}${if (!spec.isNullOrBlank()) " ($spec)" else ""}")
        } catch (_: Exception) {}
        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            sb.appendLine("توفير البيانات: " + when (cm.restrictBackgroundStatus) { 1 -> "غير مفعّل"; 2 -> "مفعّل لكن التطبيق مسموح"; 3 -> "مفعّل والتطبيق مقيّد"; else -> "؟" })
        } catch (_: Exception) {}
        sb.appendLine()
        hosts.forEach { (name, url) ->
            sb.append("• $name: ")
            val h = URL(url).host
            val t0 = System.currentTimeMillis()
            try {
                val ip = InetAddress.getByName(h).hostAddress
                sb.append("DNS ✓ ($ip) ")
            } catch (e: Exception) {
                sb.appendLine("DNS ✗ ${e.javaClass.simpleName}")
                return@forEach
            }
            var c: HttpURLConnection? = null
            try {
                c = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000; readTimeout = 10_000
                    setRequestProperty("User-Agent", "HisnAlMuslim/2.0"); setRequestProperty("Range", "bytes=0-0")
                }
                sb.appendLine("HTTPS ✓ كود ${c.responseCode} (${System.currentTimeMillis() - t0}ms)")
            } catch (e: Exception) {
                sb.appendLine("HTTPS ✗ ${e.javaClass.simpleName}: ${e.message?.take(70) ?: ""}")
            } finally { c?.disconnect() }
        }
        return sb.toString().trim()
    }
}

@Composable
fun NetDiagButton() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var out by remember { mutableStateOf<String?>(null) }
    OutlinedButton(onClick = {
        if (busy) return@OutlinedButton
        busy = true; out = null
        scope.launch { out = withContext(Dispatchers.IO) { NetDiag.run(ctx) }; busy = false }
    }) { Text(if (busy) "جارٍ الفحص…" else "🩺 فحص الاتصال", color = Gold, fontSize = 13.sp) }
    out?.let { r ->
        Spacer(Modifier.height(6.dp))
        Text(r, fontSize = 11.sp, lineHeight = 17.sp, color = OnCard)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("diag", r))
                android.widget.Toast.makeText(ctx, "تم النسخ", android.widget.Toast.LENGTH_SHORT).show()
            }) { Text("نسخ النتيجة", color = Gold, fontSize = 12.sp) }
        }
    }
}
