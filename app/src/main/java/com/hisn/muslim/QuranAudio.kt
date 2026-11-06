package com.hisn.muslim

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class Reciter(val id: String, val name: String)

/**
 * تلاوة آية بآية من everyayah.com (ملف لكل آية: /data/<القارئ>/<سورة ٣ أرقام><آية ٣ أرقام>.mp3).
 * الملفات بتتخزن على الجهاز بعد أول تشغيل. أسماء المجلدات لم أتمكن من التحقق منها من بيئة العمل.
 */
object QuranAudio {
    val reciters = listOf(
        Reciter("Alafasy_128kbps", "مشاري العفاسي"),
        Reciter("Husary_128kbps", "محمود خليل الحصري"),
        Reciter("Minshawy_Murattal_128kbps", "محمد صديق المنشاوي"),
        Reciter("Abdul_Basit_Murattal_192kbps", "عبد الباسط عبد الصمد"),
        Reciter("Abdurrahmaan_As-Sudais_192kbps", "عبد الرحمن السديس")
    )

    var current by mutableStateOf<Pair<Int, Int>?>(null) // سورة، آية
        private set
    var playing by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var mp: MediaPlayer? = null
    private var gen = 0
    private val main = Handler(Looper.getMainLooper())

    private fun name(a: Ayah) = "%03d%03d.mp3".format(a.surah, a.num)

    private fun fileFor(ctx: Context, reciter: String, a: Ayah): File {
        val dir = File(ctx.filesDir, "quran_audio/$reciter")
        dir.mkdirs()
        return File(dir, name(a))
    }

    /** يرجّع null لو تمام أو رسالة الخطأ. من thread خلفي. */
    private fun ensure(ctx: Context, reciter: String, a: Ayah): String? {
        val f = fileFor(ctx, reciter, a)
        if (f.exists() && f.length() > 1000) return null
        val tmp = File(f.parentFile, f.name + ".part")
        var c: HttpURLConnection? = null
        return try {
            c = (URL("https://everyayah.com/data/$reciter/${name(a)}").openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000; readTimeout = 30_000
                setRequestProperty("User-Agent", "HisnAlMuslim/2.0")
            }
            if (c.responseCode !in 200..299) return "تعذّر تحميل التلاوة (كود ${c.responseCode})"
            c.inputStream.use { i -> FileOutputStream(tmp).use { o -> i.copyTo(o) } }
            if (tmp.length() < 1000 || !tmp.renameTo(f)) { tmp.delete(); "ملف التلاوة غير صالح" } else null
        } catch (e: Exception) {
            tmp.delete()
            "تعذّر تحميل التلاوة: تأكد من الاتصال بالإنترنت"
        } finally {
            c?.disconnect()
        }
    }

    /**
     * يشغّل آيات الصفحة من [start]. [repeat]: 0 بدون، 1 تكرار الآية، 2 تكرار الصفحة.
     * [onFinished] بيتنادى لما تخلص الصفحة (بدون تكرار).
     */
    fun play(ctx: Context, reciter: String, list: List<Ayah>, start: Int, repeat: () -> Int, onFinished: () -> Unit) {
        stop()
        if (list.isEmpty()) return
        val g = ++gen
        error = null
        playAt(ctx.applicationContext, reciter, list, start.coerceIn(0, list.size - 1), repeat, onFinished, g)
    }

    private fun playAt(ctx: Context, reciter: String, list: List<Ayah>, idx: Int, repeat: () -> Int, onFinished: () -> Unit, g: Int) {
        if (g != gen) return
        val a = list[idx]
        current = a.surah to a.num
        loading = true
        Thread {
            val err = ensure(ctx, reciter, a)
            main.post {
                if (g != gen) return@post
                loading = false
                if (err != null) { error = err; playing = false; current = null; return@post }
                try {
                    mp?.release()
                    val p = MediaPlayer()
                    p.setAudioAttributes(
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
                    )
                    p.setDataSource(fileFor(ctx, reciter, a).absolutePath)
                    p.setOnCompletionListener {
                        if (g != gen) return@setOnCompletionListener
                        val next = when {
                            repeat() == 1 -> idx
                            idx + 1 < list.size -> idx + 1
                            repeat() == 2 -> 0
                            else -> -1
                        }
                        if (next >= 0) playAt(ctx, reciter, list, next, repeat, onFinished, g)
                        else { stopInternal(); onFinished() }
                    }
                    p.setOnErrorListener { _, _, _ -> error = "تعذّر تشغيل الملف الصوتي"; stopInternal(); true }
                    p.prepare()
                    p.start()
                    mp = p
                    playing = true
                } catch (e: Exception) {
                    error = "تعذّر تشغيل الملف الصوتي"
                    stopInternal()
                }
            }
        }.start()
    }

    fun pauseResume() {
        val p = mp ?: return
        try {
            if (p.isPlaying) { p.pause(); playing = false } else { p.start(); playing = true }
        } catch (_: Exception) {}
    }

    private fun stopInternal() {
        try { mp?.release() } catch (_: Exception) {}
        mp = null
        playing = false
        loading = false
        current = null
    }

    fun stop() {
        gen++
        stopInternal()
    }
}
