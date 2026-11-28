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

class Reciter(val id: String, val name: String, val alt: String? = null, val key: String? = null, val surahUrl: String? = null, val baseUrl: String? = null)

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
        Reciter("Mohammad_al_Tablaway_128kbps", "محمد محمود الطبلاوي", "Mohammad_al_Tablaway_64kbps", "tablaw"),
        // نفس الملاحظة: الاسم من الذاكرة ولم أتحقق منه؛ بيجرّب بدائل كتابة المجلد تلقائيًا
        Reciter("mahmoud_ali_al_banna_32kbps", "محمود علي البنا", "Mahmoud_Ali_Al_Banna_32kbps,mahmoud_ali_al_banna_64kbps", "banna"),
        // حسن محمد صالح (رواية حفص): ملف لكل آية على archive.org (نفس تسمية everyayah: SSSAAA.mp3)
        Reciter("hassan_saleh", "حسن صالح", baseUrl = "https://archive.org/download/Hassan_Saleh_Hafs/")
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

    private fun name(a: Ayah) = "%03d%03d.mp3".format(java.util.Locale.US, a.surah, a.num)

    private fun fileFor(ctx: Context, reciter: String, a: Ayah): File {
        val dir = File(ctx.filesDir, "quran_audio/$reciter")
        dir.mkdirs()
        return File(dir, name(a))
    }

    fun has(ctx: Context, reciter: String, a: Ayah) = fileFor(ctx, reciter, a).let { it.exists() && it.length() > 1000 }

    /** يرجّع null لو تمام أو رسالة الخطأ. من thread خلفي. */
    private fun ensure(ctx: Context, reciter: String, a: Ayah): String? =
        synchronized((reciter + "/" + name(a)).intern()) { ensureLocked(ctx, reciter, a) }

    private fun ensureLocked(ctx: Context, reciter: String, a: Ayah): String? {
        val f = fileFor(ctx, reciter, a)
        if (f.exists() && f.length() > 1000) return null
        val tmp = File(f.parentFile, f.name + ".part")
        val rec = reciters.firstOrNull { it.id == reciter }
        val saved = if (rec?.key != null) ctx.getSharedPreferences("hisn", Context.MODE_PRIVATE).getString("folder_$reciter", null) else null
        val folders = (listOfNotNull(saved) + listOf(reciter) + (rec?.alt?.split(",") ?: emptyList())).distinct().toMutableList()
        var lastErr = "تعذّر تحميل التلاوة"
        var triedResolve = false
        var idx = 0
        while (true) {
            if (idx >= folders.size) {
                // كل الأسماء المعروفة فشلت: نسأل everyayah عن اسم المجلد الصحيح مرة واحدة
                if (triedResolve || rec?.key == null) break
                triedResolve = true
                resolveFolders(rec.key).filter { it !in folders }.forEach { folders.add(it) }
                if (idx >= folders.size) break
            }
            val folder = folders[idx++]
            var c: HttpURLConnection? = null
            try {
                c = (URL(if (rec?.baseUrl != null) rec.baseUrl + name(a) else "https://everyayah.com/data/$folder/${name(a)}").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000; readTimeout = 30_000
                    setRequestProperty("User-Agent", "HisnAlMuslim/2.0")
                }
                if (c.responseCode !in 200..299) { lastErr = "تعذّر تحميل التلاوة (كود ${c.responseCode}) — ${reciters.firstOrNull { it.id == reciter }?.name ?: reciter}، ${name(a)}"; continue }
                c.inputStream.use { i -> FileOutputStream(tmp).use { o -> i.copyTo(o) } }
                if (tmp.length() < 1000 || !tmp.renameTo(f)) { tmp.delete(); lastErr = "ملف التلاوة غير صالح"; continue }
                if (rec?.key != null) ctx.getSharedPreferences("hisn", Context.MODE_PRIVATE).edit().putString("folder_$reciter", folder).apply()
                return null
            } catch (e: Exception) {
                tmp.delete()
                lastErr = "تعذّر تحميل التلاوة: تأكد من الاتصال بالإنترنت (${e.javaClass.simpleName}${e.message?.take(60)?.let { " — $it" } ?: ""})"
            } finally {
                c?.disconnect()
            }
        }
        return lastErr
    }

    /** يجيب أسماء مجلدات القارئ من قائمة everyayah نفسها (بحث بكلمة من الاسم)؛ الأعلى جودة الأول. */
    private fun resolveFolders(key: String): List<String> {
        var c: HttpURLConnection? = null
        return try {
            c = (URL("https://everyayah.com/data/recitations.js").openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000; readTimeout = 15_000
            }
            if (c.responseCode !in 200..299) return emptyList()
            val text = c.inputStream.bufferedReader().use { it.readText() }
            Regex("[A-Za-z0-9_\\-]+kbps").findAll(text).map { it.value }.distinct()
                .filter { it.lowercase().contains(key) }
                .sortedByDescending { Regex("(\\d+)kbps").find(it)?.groupValues?.get(1)?.toIntOrNull() ?: 0 }
                .toList()
        } catch (e: Exception) { emptyList() } finally { c?.disconnect() }
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
        if (reciters.firstOrNull { it.id == reciter }?.surahUrl != null) {
            dlError = "هذا القارئ يشغّل السورة كاملة بالبث المباشر (يحتاج إنترنت) ولا يدعم التنزيل آية بآية."
            return
        }
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

    /** آخر قارئ/قائمة تشغيل (للمشغّل الخلفي: اسم القارئ وأزرار التالي/السابق) */
    var lastReciter: String = ""
        private set
    private var lastCtx: Context? = null
    private var lastList: List<Ayah> = emptyList()
    private var lastIdx = 0
    private var lastRepeat: () -> Int = { 0 }
    private var lastFinished: () -> Unit = {}

    /** انتقال للآية التالية/السابقة في القائمة الحالية (من أزرار الإشعار) */
    fun skip(delta: Int) {
        val c = lastCtx ?: return
        val n = lastIdx + delta
        if (lastList.isEmpty() || n !in lastList.indices) return
        val g = ++gen
        playAt(c, lastReciter, lastList, n, lastRepeat, lastFinished, g)
    }

    fun play(ctx: Context, reciter: String, list: List<Ayah>, start: Int, repeat: () -> Int, loops: Int = 0, onFinished: () -> Unit) {
        stop()
        loopsLeft = loops
        if (list.isEmpty()) return
        lastReciter = reciter
        // مشغّل في الخلفية بإشعار (زي أي مشغّل صوت): الصوت يكمّل لو خرجت من التطبيق
        try { androidx.core.content.ContextCompat.startForegroundService(ctx.applicationContext, android.content.Intent(ctx.applicationContext, QuranPlayService::class.java)) } catch (_: Exception) {}
        reciters.firstOrNull { it.id == reciter }?.surahUrl?.let { url ->
            playSurah(url, list[start.coerceIn(0, list.size - 1)].surah, repeat)
            return
        }
        val g = ++gen
        error = null
        playAt(ctx.applicationContext, reciter, list, start.coerceIn(0, list.size - 1), repeat, onFinished, g)
    }

    private fun playAt(ctx: Context, reciter: String, list: List<Ayah>, idx: Int, repeat: () -> Int, onFinished: () -> Unit, g: Int) {
        if (g != gen) return
        val a = list[idx]
        lastCtx = ctx; lastList = list; lastIdx = idx; lastRepeat = repeat; lastFinished = onFinished
        current = a.surah to a.num
        loading = true
        Thread {
            val err = ensure(ctx, reciter, a)
            // نجهّز المشغّل (prepare) على خيط الخلفية عشان الانتقال بين الآيات ما يحصلش فيه تأخير على الخيط الرئيسي
            var p: MediaPlayer? = null
            var perr: String? = null
            if (err == null) {
                try {
                    p = MediaPlayer().also {
                        it.setWakeMode(ctx, android.os.PowerManager.PARTIAL_WAKE_LOCK)
                        it.setAudioAttributes(
                            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
                        )
                        it.setDataSource(fileFor(ctx, reciter, a).absolutePath)
                        it.prepare()
                    }
                } catch (e: Exception) {
                    perr = "تعذّر تشغيل الملف الصوتي (${e.javaClass.simpleName})"
                    try { p?.release() } catch (_: Exception) {}
                    p = null
                    try { fileFor(ctx, reciter, a).delete() } catch (_: Exception) {}
                }
            }
            main.post {
                if (g != gen) { try { p?.release() } catch (_: Exception) {}; return@post }
                loading = false
                if (err != null) { error = err; playing = false; current = null; return@post }
                if (p == null) { error = perr; stopInternal(); return@post }
                try {
                    mp?.release()
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
                    p.start()
                    if (speed != 1f) try { p.playbackParams = p.playbackParams.setSpeed(speed) } catch (_: Exception) {}
                    mp = p
                    playing = true
                    // تنزيل مسبق للآيات الجاية أثناء ما الحالية بتشتغل (عشان ما نستناش الشبكة بين الآيات)
                    Thread {
                        for (k in 1..3) {
                            val n = idx + k
                            if (n >= list.size || g != gen) break
                            ensure(ctx, reciter, list[n])
                        }
                    }.start()
                } catch (e: Exception) {
                    error = "تعذّر تشغيل الملف الصوتي (${e.javaClass.simpleName})"
                    stopInternal()
                }
            }
        }.start()
    }

    /** تشغيل سورة كاملة من ملف واحد (للقراء اللي مالهمش ملفات آية بآية). بدون تظليل آية؛ current = (سورة، ٠). */
    private fun playSurah(urlPattern: String, surah: Int, repeat: () -> Int) {
        val g = ++gen
        error = null
        current = surah to 0
        loading = true
        Thread {
            try {
                val p = MediaPlayer()
                p.setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
                )
                p.setDataSource(urlPattern.format(java.util.Locale.US, surah))
                p.prepare()
                main.post {
                    if (g != gen) { p.release(); return@post }
                    try { mp?.release() } catch (_: Exception) {}
                    p.setOnCompletionListener {
                        if (g != gen) return@setOnCompletionListener
                        if (repeat() != 0) { try { p.seekTo(0); p.start() } catch (_: Exception) { stopInternal() } }
                        else stopInternal()
                    }
                    p.setOnErrorListener { _, what, extra -> error = "تعذّر تشغيل التلاوة (خطأ $what/$extra)"; stopInternal(); true }
                    p.start()
                    if (speed != 1f) try { p.playbackParams = p.playbackParams.setSpeed(speed) } catch (_: Exception) {}
                    mp = p
                    loading = false
                    playing = true
                }
            } catch (e: Exception) {
                main.post {
                    if (g != gen) return@post
                    error = "تعذّر تحميل التلاوة: تأكد من الاتصال بالإنترنت (${e.javaClass.simpleName})"
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
