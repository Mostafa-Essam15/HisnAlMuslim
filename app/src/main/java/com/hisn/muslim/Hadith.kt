package com.hisn.muslim

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class HCategory(val id: String, val title: String, val count: Int)
class HItem(val id: String, val title: String)
class HDetail(
    val id: String, val title: String, val text: String,
    val attribution: String, val grade: String, val explanation: String, val reference: String
)

/**
 * مكتبة الأحاديث: بتجيب النص كما هو (مع الدرجة والتخريج) من موسوعة الأحاديث النبوية (hadeethenc.com)
 * ومبتألفش ولا بتعدّل أي نص. بتتخزن النتائج على الجهاز بعد أول تحميل.
 * ملاحظة: واجهة الموقع لم أتمكن من التحقق منها من بيئة التطوير، فالقراءة دفاعية وأي خطأ بيظهر برسالة.
 */
object HadithApi {
    private const val BASE = "https://hadeethenc.com/api/v1"

    private fun cacheFile(ctx: Context, name: String) = File(File(ctx.filesDir, "hadith").apply { mkdirs() }, name)

    private fun get(ctx: Context, path: String, cacheName: String): String {
        val cf = cacheFile(ctx, cacheName)
        try {
            val conn = (URL("$BASE/$path").openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000; readTimeout = 30_000
                setRequestProperty("User-Agent", "HisnAlMuslim/2.0")
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (conn.responseCode !in 200..299) throw IllegalStateException("كود ${conn.responseCode}")
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                if (body.trimStart().startsWith("[")) JSONArray(body) else JSONObject(body) // تحقق إنه JSON سليم
                cf.writeText(body)
                return body
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            if (cf.exists()) return cf.readText() // بدون إنترنت: آخر نسخة محفوظة
            throw e
        }
    }

    /** أقسام الأحاديث. لازم من thread خلفي. */
    fun categories(ctx: Context): List<HCategory> {
        val arr = JSONArray(get(ctx, "categories/list/?language=ar", "cats.json"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            HCategory(o.optString("id"), o.optString("title"), o.optString("hadeeths_count", "0").toIntOrNull() ?: 0)
        }.filter { it.id.isNotBlank() && it.title.isNotBlank() }
    }

    fun list(ctx: Context, categoryId: String, page: Int = 1): List<HItem> {
        val o = JSONObject(get(ctx, "hadeeths/list/?language=ar&category_id=$categoryId&page=$page&per_page=20", "list_${categoryId}_$page.json"))
        val arr = o.optJSONArray("data") ?: JSONArray()
        return (0 until arr.length()).map {
            val h = arr.getJSONObject(it)
            HItem(h.optString("id"), h.optString("title"))
        }.filter { it.id.isNotBlank() }
    }

    fun detail(ctx: Context, id: String): HDetail {
        val o = JSONObject(get(ctx, "hadeeths/one/?language=ar&id=$id", "one_$id.json"))
        val text = o.optString("hadeeth")
        if (text.isBlank()) throw IllegalStateException("استجابة غير متوقعة")
        return HDetail(
            id, o.optString("title"), text, o.optString("attribution"), o.optString("grade"),
            o.optString("explanation"), o.optString("reference")
        )
    }
}
