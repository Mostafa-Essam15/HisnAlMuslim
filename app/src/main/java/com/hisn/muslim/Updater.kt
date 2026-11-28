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

class UpdateInfo(
    val code: Int, val name: String, val notes: String, val apkUrl: String, val size: Long,
    /** تحديث بالفرق: SHA-256 للـAPK الجديد + رابط ملف الفرق من نسختك المثبّتة (لو متاح) */
    val sha256: String = "", val patchUrl: String? = null, val patchSize: Long = 0
)

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
            val mine = installedCode(ctx)
            var patchUrl: String? = null; var patchSize = 0L
            o.optJSONArray("patches")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val pj = arr.getJSONObject(i)
                    if (pj.optLong("from") == mine) { patchUrl = "$BASE/" + pj.getString("file"); patchSize = pj.optLong("size", 0) }
                }
            }
            val info = UpdateInfo(
                o.getInt("versionCode"), o.optString("versionName"), o.optString("notes"),
                "$BASE/" + o.optString("apk", "HisnAlMuslim.apk"), o.optLong("size", 0),
                o.optString("sha256"), patchUrl, patchSize
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
        // التنزيل بيشتغل في خدمة أمامية (إشعار بالنسبة) عشان ما يتقطعش لو خرجت من التطبيق أو بدّلت التبويب،
        // وبيكمّل من آخر نقطة (Range) لو النت قطع أو النظام قفل التطبيق.
        try {
            androidx.core.content.ContextCompat.startForegroundService(
                app,
                Intent(app, UpdateService::class.java).putExtra("code", info.code).putExtra("url", info.apkUrl).putExtra("size", info.size)
                    .putExtra("sha", info.sha256).putExtra("patchUrl", info.patchUrl).putExtra("patchSize", info.patchSize)
            )
        } catch (e: Exception) {
            progress = -1; message = "تعذّر بدء التنزيل: ${e.message ?: ""}"
        }
    }

    /** تنزيل قابل للاستئناف. لو فيه ملف فرق لنسختك بنجرّبه الأول (أصغر بكتير)، ولو فشل بنرجع للـAPK الكامل. */
    fun downloadResumable(app: Context, info: UpdateInfo, onPct: (Int) -> Unit): File {
        val dir = File(app.cacheDir, "update").apply { mkdirs() }
        val prefix = "HisnAlMuslim-${info.code}"
        dir.listFiles()?.filter { !it.name.startsWith(prefix) }?.forEach { it.delete() }
        val out = File(dir, "$prefix.apk")
        if (out.exists() && info.size > 0 && out.length() == info.size) return out
        val pu = info.patchUrl
        if (pu != null && info.sha256.isNotBlank() && (info.size <= 0 || info.patchSize in 1 until info.size)) {
            try {
                val pf = fetchResumable(pu, File(dir, "$prefix.patch"), File(dir, "$prefix.patch.part"), info.patchSize) { onPct(it * 6 / 10) }
                val applied = File(dir, "$prefix.patched")
                ApkPatch.apply(File(app.applicationInfo.sourceDir), pf, applied) { onPct(60 + it * 35 / 100) }
                if (ApkPatch.sha256(applied) == info.sha256.lowercase()) {
                    out.delete()
                    if (applied.renameTo(out)) { pf.delete(); return out }
                }
                applied.delete()
                message = "تعذّر التحديث بالفرق، جارٍ تنزيل الملف كاملًا…"
            } catch (e: Exception) {
                message = "تعذّر التحديث بالفرق (${e.javaClass.simpleName})، جارٍ تنزيل الملف كاملًا…"
            }
            File(dir, "$prefix.patch").delete(); File(dir, "$prefix.patch.part").delete()
        }
        return fetchResumable(info.apkUrl, out, File(dir, "$prefix.part"), info.size, onPct)
    }

    /** تنزيل قابل للاستئناف من [url] إلى [out] (حجمه المتوقع [size]). بيرمي استثناء لو فشل. */
    private fun fetchResumable(url: String, out: File, part: File, size: Long, onPct: (Int) -> Unit): File {
        if (out.exists() && size > 0 && out.length() == size) return out
        var attempts = 0
        while (true) {
            var have = if (part.exists()) part.length() else 0L
            val c = open(url)
            try {
                c.readTimeout = 60_000
                if (have > 0) c.setRequestProperty("Range", "bytes=$have-")
                val rc = c.responseCode
                if (rc == 416) { part.delete(); if (attempts++ < 3) continue else throw java.io.IOException("كود 416") }
                if (rc !in 200..299) throw java.io.IOException("كود $rc")
                val append = rc == 206
                if (!append) have = 0L
                val len = c.contentLengthLong
                val total = if (len > 0) have + len else size
                var done = have
                c.inputStream.use { i ->
                    FileOutputStream(part, append).use { o ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = i.read(buf); if (n < 0) break
                            o.write(buf, 0, n); done += n
                            if (total > 0) onPct((done * 100 / total).toInt().coerceIn(0, 99))
                        }
                    }
                }
                if (total > 0 && done < total) throw java.io.IOException("تنزيل ناقص")
                out.delete()
                if (!part.renameTo(out)) throw java.io.IOException("تعذّر الحفظ")
                return out
            } catch (e: java.io.IOException) {
                // انقطاع مؤقت: نعيد المحاولة من آخر نقطة (حتى ٦ مرات)
                if (++attempts > 6) throw e
                try { Thread.sleep(3000L * attempts) } catch (_: InterruptedException) {}
            } finally {
                c.disconnect()
            }
        }
    }

    fun installIntent(ctx: Context, apk: File): Intent {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", apk)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
