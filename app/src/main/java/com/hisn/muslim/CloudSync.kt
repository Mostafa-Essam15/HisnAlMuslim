package com.hisn.muslim

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * مزامنة سحابية بدون حساب أو مفتاح: المستخدم يختار ملفًا من «Google Drive» (أو أي تخزين سحابي ظاهر في منتقي الملفات
 * بتاع الأندرويد)، والتطبيق يكتب فيه نسخة احتياطية تلقائيًا ويقدر يسحب منها. الاستعادة بتكون يدوية دائمًا عشان
 * ما يتكتبش فوق بيانات أحدث على جهاز تاني بالغلط.
 */
object CloudSync {
    private const val MIN_GAP = 10 * 60_000L

    fun linked(p: Prefs) = !p.syncUri.isNullOrBlank()

    fun link(ctx: Context, uri: Uri) {
        try {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (_: Exception) {}
        Prefs(ctx).syncUri = uri.toString()
    }

    fun unlink(ctx: Context) {
        val p = Prefs(ctx)
        p.syncUri?.let { try { ctx.contentResolver.releasePersistableUriPermission(Uri.parse(it), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: Exception) {} }
        p.syncUri = null
    }

    /** يكتب النسخة في الملف المرتبط. يرجّع null لو تمام أو رسالة الخطأ. من thread خلفي. */
    fun push(ctx: Context): String? {
        val p = Prefs(ctx)
        val u = p.syncUri ?: return "لا يوجد ملف مرتبط"
        val bytes = Backup.export(ctx).toByteArray()
        return try {
            val uri = Uri.parse(u)
            val os = try { ctx.contentResolver.openOutputStream(uri, "wt") } catch (_: Exception) { ctx.contentResolver.openOutputStream(uri, "w") }
            if (os == null) "تعذّر فتح الملف المرتبط" else { os.use { it.write(bytes) }; p.syncAt = System.currentTimeMillis(); null }
        } catch (e: Exception) { "تعذّر الكتابة في الملف المرتبط (${e.javaClass.simpleName}). اربط الملف من جديد." }
    }

    /** يسحب من الملف المرتبط ويرجّع عدد الإعدادات المستعادة أو -1 */
    fun pull(ctx: Context): Int {
        val u = Prefs(ctx).syncUri ?: return -1
        return try {
            val text = ctx.contentResolver.openInputStream(Uri.parse(u))?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return -1
            Backup.import(ctx, text)
        } catch (e: Exception) { -1 }
    }

    /** يُنادى عند خروج التطبيق للخلفية: يرفع نسخة لو المزامنة التلقائية شغالة ومرّ وقت كافٍ. */
    fun autoPush(ctx: Context) {
        val p = Prefs(ctx)
        if (!linked(p) || !p.syncAuto || System.currentTimeMillis() - p.syncAt < MIN_GAP) return
        val app = ctx.applicationContext
        Thread { push(app) }.start()
    }
}

var Prefs.syncUri: String?
    get() = sp.getString("syncUri", null)
    set(v) { if (v == null) sp.edit().remove("syncUri").apply() else sp.edit().putString("syncUri", v).apply() }

var Prefs.syncAuto: Boolean
    get() = sp.getBoolean("syncAuto", true)
    set(v) { sp.edit().putBoolean("syncAuto", v).apply() }

var Prefs.syncAt: Long
    get() = sp.getLong("syncAt", 0L)
    set(v) { sp.edit().putLong("syncAt", v).apply() }
