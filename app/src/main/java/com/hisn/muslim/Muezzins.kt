package com.hisn.muslim

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class Muezzin(val key: String, val name: String, val group: String, val remoteId: Int) {
    /** رابط التحميل (موقع assabile.com) */
    val url get() = "https://www.assabile.com/download/adhan/$remoteId"
    val isCustom get() = remoteId == 0
}

object Muezzins {
    const val EGY = "مؤذنون مصريون"
    const val HARAM = "الحرمان الشريفان"
    const val OTHER = "آخرون"
    const val MINE = "ملفك الخاص (أعلى جودة)"

    /** مدة الأذان "القصير": بيشتغل أول ٣٠ ثانية */
    const val SHORT_SECONDS = 30

    val all = listOf(
        Muezzin("refaat", "الشيخ محمد رفعت", EGY, 149),
        Muezzin("abdelbasit", "الشيخ عبد الباسط عبد الصمد", EGY, 136),
        Muezzin("mostafa_ismail", "الشيخ مصطفى إسماعيل", EGY, 141),
        Muezzin("hussary", "الشيخ محمود خليل الحصري", EGY, 142),
        Muezzin("minshawi", "الشيخ محمد صديق المنشاوي", EGY, 143),
        Muezzin("shuaisha", "الشيخ أبو العينين شعيشع", EGY, 145),
        Muezzin("banna", "الشيخ محمود علي البنا", EGY, 148),
        Muezzin("nuinaa", "الشيخ أحمد نعينع", EGY, 146),
        Muezzin("ismail_sheikh", "الشيخ إسماعيل الشيخ", EGY, 8),
        Muezzin("makkah", "الحرم المكي - علي أحمد ملا", HARAM, 119),
        Muezzin("madinah", "الحرم المدني - عاصم بخاري", HARAM, 129),
        Muezzin("afasy", "مشاري العفاسي", OTHER, 130),
        Muezzin("custom", "ملف أذان من جهازك", MINE, 0)
    )

    val groups = listOf(EGY, HARAM, OTHER, MINE)

    fun byKey(key: String): Muezzin = all.firstOrNull { it.key == key } ?: all.first()

    fun dir(ctx: Context): File = File(ctx.filesDir, "adhan").apply { mkdirs() }

    fun file(ctx: Context, m: Muezzin): File = File(dir(ctx), "${m.key}.mp3")

    fun isDownloaded(ctx: Context, m: Muezzin): Boolean = file(ctx, m).let { it.exists() && it.length() > 20_000 }

    fun delete(ctx: Context, m: Muezzin) {
        file(ctx, m).delete()
    }

    /** وصف الملف: المدة والجودة التقريبية (kbps) */
    fun info(ctx: Context, m: Muezzin): String {
        val f = file(ctx, m)
        if (!f.exists()) return ""
        return try {
            val r = MediaMetadataRetriever()
            r.setDataSource(f.absolutePath)
            val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val br = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
                ?: if (ms > 0) f.length() * 8 * 1000 / ms else 0L
            r.release()
            val sec = ms / 1000
            "%d:%02d • ≈%d kbps".format(sec / 60, sec % 60, br / 1000)
        } catch (e: Exception) {
            ""
        }
    }

    /** نسخ ملف أذان اختاره المستخدم من جهازه (أي صيغة صوت يفتحها الأندرويد: mp3, m4a, ogg, flac, wav) */
    fun importCustom(ctx: Context, uri: Uri): String? {
        val m = byKey("custom")
        val tmp = File(dir(ctx), "custom.part")
        return try {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tmp).use { out -> input.copyTo(out) }
            } ?: return "تعذّر فتح الملف"
            if (!validAudio(tmp)) {
                tmp.delete()
                return "الملف ده مش ملف صوت صالح"
            }
            val target = file(ctx, m)
            target.delete()
            if (!tmp.renameTo(target)) "تعذّر حفظ الملف" else null
        } catch (e: Exception) {
            tmp.delete()
            "تعذّر استيراد الملف"
        }
    }

    private fun validAudio(f: File): Boolean {
        if (f.length() < 20_000) return false
        val probe = MediaPlayer()
        return try {
            probe.setDataSource(f.absolutePath)
            probe.prepare()
            true
        } catch (e: Exception) {
            false
        } finally {
            probe.release()
        }
    }

    /**
     * تحميل ملف الأذان. لازم يتنادى من thread خلفي (مش الـ Main).
     * بيرجع null لو نجح، أو رسالة الخطأ.
     */
    fun download(ctx: Context, m: Muezzin, onProgress: (Int) -> Unit = {}): String? {
        val target = file(ctx, m)
        val tmp = File(target.parentFile, "${m.key}.part")
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(m.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android) HisnAlMuslim/2.0")
            }
            if (conn.responseCode !in 200..299) return "فشل التحميل (كود ${conn.responseCode})"
            val type = conn.contentType.orEmpty().lowercase()
            if (type.contains("text/html")) return "الرابط رجّع صفحة ويب مش ملف صوت"
            val total = conn.contentLength
            var read = 0L
            conn.inputStream.use { input ->
                FileOutputStream(tmp).use { out ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0) onProgress((read * 100 / total).toInt())
                    }
                }
            }
            if (!validAudio(tmp)) {
                tmp.delete()
                return "الملف المحمّل مش ملف صوت صالح"
            }
            target.delete()
            if (!tmp.renameTo(target)) return "تعذّر حفظ الملف"
            return null
        } catch (e: Exception) {
            tmp.delete()
            return "تعذّر التحميل: تأكد من الاتصال بالإنترنت"
        } finally {
            conn?.disconnect()
        }
    }

    /** مشغّل بسيط للاستماع للأذان المحمّل من الإعدادات */
    fun previewPlayer(ctx: Context, m: Muezzin): MediaPlayer? = try {
        MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setDataSource(file(ctx, m).absolutePath)
            prepare()
        }
    } catch (e: Exception) {
        null
    }
}
