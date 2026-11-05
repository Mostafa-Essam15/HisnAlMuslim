package com.hisn.muslim

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

class Ayah(val surah: Int, val num: Int, val text: String, val page: Int, val juz: Int)

class SurahInfo(val number: Int, val name: String, val ayahCount: Int, val revelation: String, val firstPage: Int)

/**
 * نص المصحف (رواية حفص - الرسم العثماني) بيتحمّل مرة واحدة من واجهة Al-Quran Cloud
 * (النص من مشروع Tanzil) ويتخزن على الجهاز، وبعدها بيشتغل بدون إنترنت.
 */
object Quran {
    const val QURAN_URL = "https://api.alquran.cloud/v1/quran/quran-uthmani"
    const val TOTAL_PAGES = 604

    var surahs: List<SurahInfo> = emptyList()
        private set
    var ayahs: List<Ayah> = emptyList()
        private set
    var byPage: Map<Int, List<Ayah>> = emptyMap()
        private set
    var juzFirstPage: Map<Int, Int> = emptyMap()
        private set

    val loaded get() = ayahs.isNotEmpty()

    fun file(ctx: Context) = File(ctx.filesDir, "quran.json")

    fun isDownloaded(ctx: Context) = file(ctx).let { it.exists() && it.length() > 500_000 }

    fun surahName(n: Int) = surahs.firstOrNull { it.number == n }?.name ?: ""

    /** بيرجع null لو نجح، أو رسالة الخطأ. لازم يتنادى من thread خلفي. */
    fun download(ctx: Context, onProgress: (Int) -> Unit = {}): String? {
        val target = file(ctx)
        val tmp = File(ctx.filesDir, "quran.part")
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(QURAN_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", "HisnAlMuslim/2.0")
            }
            if (conn.responseCode !in 200..299) return "فشل التحميل (كود ${conn.responseCode})"
            val total = conn.contentLength
            var read = 0L
            conn.inputStream.use { input ->
                FileOutputStream(tmp).use { out ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0) onProgress((read * 100 / total).toInt())
                    }
                }
            }
            // تأكد إن الملف سليم قبل ما نحفظه
            try {
                parse(tmp.readText())
            } catch (e: Exception) {
                tmp.delete()
                return "الملف المحمّل غير مكتمل أو بصيغة غير متوقعة"
            }
            target.delete()
            if (!tmp.renameTo(target)) return "تعذّر حفظ المصحف"
            return null
        } catch (e: Exception) {
            tmp.delete()
            return "تعذّر التحميل: تأكد من الاتصال بالإنترنت"
        } finally {
            conn?.disconnect()
        }
    }

    /** تحميل النص من الملف المحفوظ للذاكرة. لازم من thread خلفي. */
    fun load(ctx: Context): Boolean {
        if (loaded) return true
        val f = file(ctx)
        if (!f.exists()) return false
        return try {
            parse(f.readText())
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun parse(json: String) {
        val data = JSONObject(json).getJSONObject("data")
        val sArr = data.getJSONArray("surahs")
        val sList = ArrayList<SurahInfo>(114)
        val aList = ArrayList<Ayah>(6300)
        for (i in 0 until sArr.length()) {
            val s = sArr.getJSONObject(i)
            val number = s.getInt("number")
            val arr = s.getJSONArray("ayahs")
            var firstPage = 1
            for (j in 0 until arr.length()) {
                val a = arr.getJSONObject(j)
                val num = a.optInt("numberInSurah", j + 1)
                var text = a.getString("text")
                if (num == 1 && number != 1) text = stripBasmala(text)
                val page = a.optInt("page", 1)
                if (j == 0) firstPage = page
                aList.add(Ayah(number, num, text, page, a.optInt("juz", 1)))
            }
            sList.add(SurahInfo(number, s.optString("name", "سورة $number"), arr.length(), s.optString("revelationType", ""), firstPage))
        }
        if (aList.size < 6000 || sList.size != 114) throw IllegalStateException("incomplete")
        surahs = sList
        ayahs = aList
        byPage = aList.groupBy { it.page }
        juzFirstPage = aList.groupBy { it.juz }.mapValues { e -> e.value.minOf { it.page } }
    }

    // الواجهة بترجّع البسملة جوه نص أول آية في كل سورة؛ بنشيلها ونعرضها كعنوان
    private fun stripBasmala(t: String): String {
        val parts = t.trim().split(" ")
        if (parts.size > 4 &&
            norm(parts[0]) == "بسم" && norm(parts[1]) == "الله" &&
            norm(parts[2]) == "الرحمن" && norm(parts[3]) == "الرحيم"
        ) return parts.drop(4).joinToString(" ")
        return t
    }

    private fun norm(s: String): String {
        val sb = StringBuilder()
        for (ch in s) {
            val c = ch.code
            if (c in 0x064B..0x065F || c == 0x0670 || c in 0x06D6..0x06ED || c == 0x0640) continue
            sb.append(if (ch == 'ٱ') 'ا' else ch)
        }
        return sb.toString()
    }

    fun arabicDigits(n: Int): String =
        n.toString().map { '٠' + (it - '0') }.joinToString("")
}

/** خطة ختمة القرآن (ورد يومي) */
object Khatma {
    const val TOTAL = Quran.TOTAL_PAGES

    class Stats(
        val read: Int,
        val remaining: Int,
        val daysLeft: Int,
        val daysTotal: Int,
        val goalToday: Int,
        val readToday: Int,
        val nextPage: Int,
        val finishDate: String
    )

    fun pagesPerDay(days: Int) = ceil(TOTAL.toDouble() / days.coerceAtLeast(1)).toInt()

    fun stats(p: Prefs): Stats? {
        if (!p.khatmaActive) return null
        val read = p.khatmaPagesRead()
        val readToday = p.khatmaReadToday().size
        val start = runCatching { LocalDate.parse(p.khatmaStart) }.getOrDefault(LocalDate.now())
        val elapsed = ChronoUnit.DAYS.between(start, LocalDate.now()).toInt().coerceAtLeast(0)
        val daysLeft = (p.khatmaDays - elapsed).coerceAtLeast(1)
        val remaining = (TOTAL - read.size).coerceAtLeast(0)
        val goal = if (remaining + readToday == 0) 0
        else ceil((remaining + readToday).toDouble() / daysLeft).toInt()
        val next = (1..TOTAL).firstOrNull { it !in read } ?: TOTAL
        val finish = start.plusDays((p.khatmaDays - 1).toLong()).toString()
        return Stats(read.size, remaining, daysLeft, p.khatmaDays, goal, readToday, next, finish)
    }
}
