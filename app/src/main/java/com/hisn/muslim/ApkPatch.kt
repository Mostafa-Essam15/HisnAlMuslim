package com.hisn.muslim

import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.zip.InflaterInputStream

/**
 * تطبيق ملف فرق (bsdiff) بين نسخة التطبيق المثبّتة والنسخة الجديدة، فبدل تنزيل الـAPK كله ننزّل الفرق بس.
 * صيغة الملف (بيطلّعها tools/mkpatch.py في الـCI من مخرجات bsdiff):
 *   "HSNPT001" | حجم الملف الجديد (٨ بايت LE) | أطوال الأقسام المضغوطة ctrl/diff/extra (٣×٨ بايت LE) | ٣ أقسام zlib.
 * بعد التطبيق بنتحقق من SHA-256 للناتج؛ أي اختلاف = بنرجع للتنزيل الكامل.
 */
object ApkPatch {
    private const val MAGIC = "HSNPT001"

    private fun le64(b: ByteArray, o: Int): Long {
        var v = 0L
        for (i in 7 downTo 0) v = (v shl 8) or (b[o + i].toLong() and 0xFF)
        return v
    }

    /** عدد صحيح بإشارة (sign-magnitude) زي ما bsdiff بيكتبه */
    private fun offtin(b: ByteArray, o: Int): Long {
        var y = (b[o + 7].toLong() and 0x7F)
        for (i in 6 downTo 0) y = y * 256 + (b[o + i].toLong() and 0xFF)
        return if ((b[o + 7].toInt() and 0x80) != 0) -y else y
    }

    private fun readFully(s: InputStream, buf: ByteArray, off: Int, len: Int) {
        var d = 0
        while (d < len) {
            val n = s.read(buf, off + d, len - d)
            if (n < 0) throw java.io.IOException("ملف الفرق ناقص")
            d += n
        }
    }

    /** يطبّق [patch] على [old] ويكتب الناتج في [out]. [onPct] بيتنادى بنسبة ٠..١٠٠. */
    fun apply(old: File, patch: File, out: File, onPct: (Int) -> Unit = {}) {
        val p = patch.readBytes()
        if (p.size < 48 || String(p, 0, 8, Charsets.US_ASCII) != MAGIC) throw java.io.IOException("صيغة ملف الفرق غير معروفة")
        val newSize = le64(p, 8)
        val cl = le64(p, 16).toInt(); val dl = le64(p, 24).toInt(); val el = le64(p, 32).toInt()
        var pos = 40
        if (pos + cl.toLong() + dl + el != p.size.toLong()) throw java.io.IOException("أطوال ملف الفرق غير متطابقة")
        val ctrl = InflaterInputStream(ByteArrayInputStream(p, pos, cl)); pos += cl
        val diff = InflaterInputStream(ByteArrayInputStream(p, pos, dl)); pos += dl
        val extra = InflaterInputStream(ByteArrayInputStream(p, pos, el))
        val tmp = File(out.parentFile, out.name + ".tmp")
        RandomAccessFile(old, "r").use { ro ->
            val oldSize = ro.length()
            FileOutputStream(tmp).use { fo ->
                val cbuf = ByteArray(24)
                val a = ByteArray(65536); val b = ByteArray(65536)
                var newPos = 0L; var oldPos = 0L; var lastPct = -1
                while (newPos < newSize) {
                    readFully(ctrl, cbuf, 0, 24)
                    val x = offtin(cbuf, 0); val y = offtin(cbuf, 8); val z = offtin(cbuf, 16)
                    if (x < 0 || y < 0 || newPos + x + y > newSize) throw java.io.IOException("ملف الفرق تالف")
                    var left = x
                    while (left > 0) {
                        val n = minOf(left, a.size.toLong()).toInt()
                        readFully(diff, a, 0, n)
                        java.util.Arrays.fill(b, 0, n, 0)
                        val lo = maxOf(oldPos, 0L); val hi = minOf(oldPos + n, oldSize)
                        if (lo < hi) { ro.seek(lo); readFully(ro, b, (lo - oldPos).toInt(), (hi - lo).toInt()) }
                        for (i in 0 until n) a[i] = (a[i] + b[i]).toByte()
                        fo.write(a, 0, n)
                        left -= n; oldPos += n; newPos += n
                    }
                    left = y
                    while (left > 0) {
                        val n = minOf(left, a.size.toLong()).toInt()
                        readFully(extra, a, 0, n)
                        fo.write(a, 0, n)
                        left -= n; newPos += n
                    }
                    oldPos += z
                    val pct = (newPos * 100 / maxOf(newSize, 1)).toInt()
                    if (pct != lastPct) { lastPct = pct; onPct(pct) }
                }
            }
        }
        if (tmp.length() != newSize) { tmp.delete(); throw java.io.IOException("حجم الناتج غير مطابق") }
        out.delete()
        if (!tmp.renameTo(out)) throw java.io.IOException("تعذّر حفظ الناتج")
    }

    private fun readFully(r: RandomAccessFile, buf: ByteArray, off: Int, len: Int) {
        var d = 0
        while (d < len) {
            val n = r.read(buf, off + d, len - d)
            if (n < 0) throw java.io.IOException("النسخة المثبّتة أقصر من المتوقع")
            d += n
        }
    }

    fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { s ->
            val buf = ByteArray(65536)
            while (true) { val n = s.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(java.util.Locale.US, it) }
    }
}
