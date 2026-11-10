package com.hisn.muslim

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class UpdateInfo(val code: Int, val name: String, val notes: String, val apkUrl: String, val size: Long)

/**
 * التحديث من داخل التطبيق: بيقرأ update.json من صفحة الـRelease على GitHub، ولو فيه نسخة أحدث
 * بيعرضها وبينزّل الـAPK ويفتح شاشة التثبيت. أندرويد بيرفض أي APK توقيعه مختلف عن النسخة المثبتة،
 * فلو الملف اتعدّل في الطريق مش هيتثبّت.
 */
object Updater {
    private const val BASE = "https://github.com/Mostafa-Essam15/HisnAlMuslim/releases/download/latest"

    var available by mutableStateOf<UpdateInfo?>(null)
    /** -1 = لا تنزيل، 0..100 = نسبة التنزيل */
    var progress by mutableIntStateOf(-1)
    var message by mutableStateOf("")

    fun installedCode(ctx: Context): Long {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = true
            setRequestProperty("User-Agent", "HisnAlMuslim/2.0")
        }

    /** بيرجع معلومات النسخة الأحدث لو أحدث من المثبتة، وإلا null. من thread خلفي. بيرمي استثناء لو فشل الاتصال. */
    fun fetch(ctx: Context): UpdateInfo? {
        val c = open("$BASE/update.json")
        try {
            if (c.responseCode !in 200..299) throw java.io.IOException("كود ${c.responseCode}")
            val o = JSONObject(c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            val info = UpdateInfo(
                o.getInt("versionCode"), o.optString("versionName"), o.optString("notes"),
                "$BASE/" + o.optString("apk", "HisnAlMuslim.apk"), o.optLong("size", 0)
            )
            return if (info.code > installedCode(ctx)) info else null
        } finally { c.disconnect() }
    }

    /** فحص يدوي/عند الفتح؛ بيحدّث [available] و[message]. */
    fun check(ctx: Context, manual: Boolean = false) {
        val app = ctx.applicationContext
        Thread {
            try {
                val i = fetch(app)
                available = i
                if (manual) message = if (i == null) "أنت على أحدث نسخة ✓" else "يوجد تحديث جديد (${i.code})"
            } catch (e: Exception) {
                if (manual) message = "تعذّر التحقق: تأكد من الاتصال بالإنترنت"
            }
        }.start()
    }

    fun canInstall(ctx: Context) = Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    /** ينزّل الـAPK ثم يفتح التثبيت. لو إذن «تثبيت التطبيقات» ناقص، يفتح شاشة الإذن أولًا. */
    fun downloadAndInstall(ctx: Context, info: UpdateInfo) {
        val app = ctx.applicationContext
        if (progress >= 0) return
        if (!canInstall(app)) {
            message = "اسمح للتطبيق بتثبيت التحديثات من الشاشة التالية ثم اضغط «تحميل وتثبيت» مرة أخرى."
            app.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        progress = 0; message = ""
        Thread {
            val dir = File(app.cacheDir, "update").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val tmp = File(dir, "HisnAlMuslim.part")
            val out = File(dir, "HisnAlMuslim.apk")
            val c = open(info.apkUrl)
            try {
                c.readTimeout = 60_000
                if (c.responseCode !in 200..299) throw java.io.IOException("كود ${c.responseCode}")
                val total = c.contentLengthLong.takeIf { it > 0 } ?: info.size
                var done = 0L
                c.inputStream.use { i ->
                    FileOutputStream(tmp).use { o ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = i.read(buf); if (n < 0) break
                            o.write(buf, 0, n); done += n
                            if (total > 0) progress = (done * 100 / total).toInt().coerceIn(0, 99)
                        }
                    }
                }
                if (total > 0 && done < total) throw java.io.IOException("تنزيل ناقص")
                if (!tmp.renameTo(out)) throw java.io.IOException("تعذّر الحفظ")
                progress = 100
                install(app, out)
                message = "بدأ التثبيت — أكمل من شاشة النظام."
            } catch (e: Exception) {
                tmp.delete(); message = "فشل التنزيل: ${e.message ?: "تحقق من الاتصال"}"
            } finally {
                c.disconnect(); progress = -1
            }
        }.start()
    }

    private fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
