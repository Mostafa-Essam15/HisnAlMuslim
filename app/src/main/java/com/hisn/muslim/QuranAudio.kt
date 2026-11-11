package com.hisn.muslim

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class Reciter(val id: String, val name: String, val alt: String? = null)

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
        Reciter("Abdurrahmaan_As-Sudais_192kbps", "عبد الرحمن السديس"),
        // اسم المجلد على everyayah لم أستطع التحقق منه من بيئة العمل؛ لو الأول فشل بيجرّب البديل تلقائيًا
        Reciter("Mohammad_al_Tablaway_128kbps", "محمد محمود الطبلاوي", "Mohammad_al_Tablaway_64kbps"),
        // نفس الملاحظة: الاسم من الذاكرة ولم أتحقق منه؛ بيجرّب بدائل كتابة المجلد تلقائيًا
        Reciter("mahmoud_ali_al_banna_32kbps", "محمود علي البنا", "Mahmoud_Ali_Al_Banna_32kbps,mahmoud_ali_al_banna_64kbps")
    )

    var current by mutableStateOf<Pair<Int, Int>?>(null) // سورة، آية
        private set
    var playing by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** سرعة التشغيل (٠٫٧٥–٢) */
    var speed by mutableFloatStateOf(1f)
        private set
    /** وقت إيقاف مؤقت النوم بالميلي ثانية (٠ = متوقف) */
    var sleepAt by mutableLongStateOf(0L)
        private set
    private val sleepRun = Runnable { sleepAt = 0L; stop() }

    fun changeSpeed(v: Float) {
        speed = v.coerceIn(0.5f, 2f)
        val p = mp ?: return
        try { if (p.isPlaying) p.playbackParams = p.playbackParams.setSpeed(speed) } catch (_: Exception) {}
    }

    /** مؤقت النوم: بعد [min] دقيقة يتوقف التشغيل. 0 يلغيه. */
    fun setSleep(min: Int) {
        main.removeCallbacks(sleepRun)
        if (min <= 0) { sleepAt = 0L; return }
        sleepAt = System.currentTimeMillis() + min * 60_000L
        main.postDelayed(sleepRun, min * 60_000L)
    }

    private var mp: MediaPlayer? = null
    private var gen = 0
    private val main = Handler(Looper.getMainLooper())

    private fun name(a: Ayah) = "%03d%03d.mp3".format(a.surah, a.num)

    private fun fileFor(ctx: Context, reciter: String, a: Ayah): File {
        val dir = File(ctx.filesDir, "quran_audio/$reciter")
        dir.mkdirs()
        return File(dir, name(a))
    }

    fun has(ctx: Context, reciter: String, a: Ayah) = fileFor(ctx, reciter, a).let { it.exists() && it.length() > 1000 }

    /** يرجّع null لو تمام أو رسالة الخطأ. من thread خلفي. */
    private fun ensure(ctx: Context, reciter: String, a: Ayah): String? {
        val f = fileFor(ctx, reciter, a)
        if (f.exists() && f.length() > 1000) return null
        val tmp = File(f.parentFile, f.name + ".part")
        val folders = listOf(reciter) + (reciters.firstOrNull { it.id == reciter }?.alt?.split(",") ?: emptyList())
        var lastErr = "تعذّر تحميل التلاوة"
        for (folder in folders) {
            var c: HttpURLConnection? = null
            try {
                c = (URL("https://everyayah.com/data/$folder/${name(a)}").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000; readTimeout = 30_000
                    setRequestProperty("User-Agent", "HisnAlMuslim/2.0")
                }
                if (c.responseCode !in 200..299) { lastErr = "تعذّر تحميل التلاوة (كود ${c.responseCode})"; continue }
                c.inputStream.use { i -> FileOutputStream(tmp).use { o -> i.copyTo(o) } }
                if (tmp.length() < 1000 || !tmp.renameTo(f)) { tmp.delete(); lastErr = "ملف التلاوة غير صالح"; continue }
                return null
            } catch (e: Exception) {
                tmp.delete()
                lastErr = "تعذّر تحميل التلاوة: تأكد من الاتصال بالإنترنت (${e.javaClass.simpleName})"
            } finally {
                c?.disconnect()
            }
        }
        return lastErr
    }

    // ───────────── التنزيل للاستماع بدون إنترنت ─────────────
    var dlTotal by mutableIntStateOf(0)
        private set
    var dlDone by mutableIntStateOf(0)
        private set
    var dlFailed by mutableIntStateOf(0)
        private set
    var dlLabel by mutableStateOf("")
        private set
    var dlError by mutableStateOf<String?>(null)
        private set
    @Volatile private var dlCancel = false
    val dlActive: Boolean get() = dlTotal > 0 && dlDone + dlFailed < dlTotal

    /** عدد آيات القارئ المحمّلة على الجهاز وحجمها بالميجا */
    fun stored(ctx: Context, reciter: String): Pair<Int, Double> {
        val fs = File(ctx.filesDir, "quran_audio/$reciter").listFiles { f -> f.name.endsWith(".mp3") } ?: return 0 to 0.0
        return fs.size to fs.sumOf { it.length() } / 1048576.0
    }

    fun deleteStored(ctx: Context, reciter: String) {
        File(ctx.filesDir, "quran_audio/$reciter").listFiles()?.forEach { it.delete() }
    }

    fun cancelDownload() { dlCancel = true }

    /** ينزّل الآيات الناقصة (بتكمّل من حيث وقفت). يحتاج التطبيق مفتوحًا. */
    fun download(ctx: Context, reciter: String, list: List<Ayah>, label: String) {
        if (dlActive) return
        val app = ctx.applicationContext
        val todo = list.filter { !has(app, reciter, it) }
        dlCancel = false; dlError = null; dlLabel = label; dlDone = 0; dlFailed = 0
        dlTotal = todo.size
        if (todo.isEmpty()) { dlError = "كل الآيات دي محمّلة بالفعل"; return }
        Thread {
            val pool = java.util.concurrent.Executors.newFixedThreadPool(3)
            todo.forEach { a ->
                pool.execute {
                    if (dlCancel) return@execute
                    val err = ensure(app, reciter, a)
                    main.post { if (err == null) dlDone++ else { dlFailed++; dlError = err } }
                }
            }
            pool.shutdown()
            pool.awaitTermination(6, java.util.concurrent.TimeUnit.HOURS)
            main.post { if (dlCancel) { dlTotal = 0; dlError = "اتوقف التنزيل. اللي نزل محفوظ وتقدر تكمّل بعدين." } }
        }.start()
    }

    /**
     * يشغّل آيات الصفحة من [start]. [repeat]: 0 بدون، 1 تكرار الآية، 2 تكرار الصفحة.
     * [onFinished] بيتنادى لما تخلص الصفحة (بدون تكرار).
     */
    /** عدد مرات تكرار القائمة كلها المتبقية (٠ = حسب وضع التكرار العادي) */
    private var loopsLeft = 0

    fun play(ctx: Context, reciter: String, list: List<Ayah>, start: Int, repeat: () -> Int, loops: Int = 0, onFinished: () -> Unit) {
        stop()
        loopsLeft = loops
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
                            loopsLeft > 1 -> { loopsLeft--; 0 }
                            loopsLeft == 0 && repeat() == 2 -> 0
                            else -> -1
                        }
                        if (next >= 0) playAt(ctx, reciter, list, next, repeat, onFinished, g)
                        else { stopInternal(); onFinished() }
                    }
                    p.setOnErrorListener { _, what, extra -> error = "تعذّر تشغيل الملف الصوتي (خطأ $what/$extra)"; stopInternal(); true }
                    p.prepare()
                    p.start()
                    if (speed != 1f) try { p.playbackParams = p.playbackParams.setSpeed(speed) } catch (_: Exception) {}
                    mp = p
                    playing = true
                } catch (e: Exception) {
                    error = "تعذّر تشغيل الملف الصوتي (${e.javaClass.simpleName})"
                    try { fileFor(ctx, reciter, a).delete() } catch (_: Exception) {}
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
