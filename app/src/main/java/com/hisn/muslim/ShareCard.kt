package com.hisn.muslim

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import java.io.File
import java.io.FileOutputStream

/**
 * بطاقة مشاركة كصورة (آية/حديث/ذكر/دعاء): خلفية داكنة بإطار ذهبي، والنص كما هو بدون تعديل،
 * مع المصدر أسفل البطاقة. بتتحفظ مؤقتًا في كاش التطبيق وتتشارك عبر FileProvider.
 */
object ShareCard {
    private const val W = 1080

    fun render(ctx: Context, body: String, source: String): Bitmap {
        val amiri = ResourcesCompat.getFont(ctx, R.font.amiri_regular)
        val amiriB = ResourcesCompat.getFont(ctx, R.font.amiri_bold)
        val len = body.length
        val size = when { len < 120 -> 62f; len < 300 -> 52f; len < 600 -> 42f; len < 1100 -> 34f; else -> 28f }
        val textP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = size; typeface = amiri }
        val srcP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8C766.toInt(); textSize = 34f; typeface = amiriB }
        val brandP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9FB3AB.toInt(); textSize = 28f; typeface = amiri }
        val pad = 90
        val inner = W - pad * 2
        fun layout(t: String, p: TextPaint, spacing: Float) = StaticLayout.Builder.obtain(t, 0, t.length, p, inner)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(0f, spacing).build()
        val bl = layout(body, textP, 1.5f)
        val sl = layout(source, srcP, 1.3f)
        val h = (pad + bl.height + 60 + sl.height + 70 + 40 + pad).coerceAtLeast(900)
        val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = Paint().apply { shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF0F5C45.toInt(), 0xFF06261D.toInt(), Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, W.toFloat(), h.toFloat(), bg)
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = 0xFFE8C766.toInt() }
        c.drawRoundRect(30f, 30f, W - 30f, h - 30f, 36f, 36f, frame)
        val total = bl.height + 60 + sl.height
        val top = (h - 70 - total) / 2f
        c.save(); c.translate(pad.toFloat(), top); bl.draw(c); c.restore()
        val line = Paint().apply { color = 0x88E8C766.toInt(); strokeWidth = 2f }
        c.drawLine(W / 2f - 80, top + bl.height + 30, W / 2f + 80, top + bl.height + 30, line)
        c.save(); c.translate(pad.toFloat(), top + bl.height + 60); sl.draw(c); c.restore()
        val brand = "حصن المسلم"
        c.drawText(brand, (W - brandP.measureText(brand)) / 2f, h - 60f, brandP)
        return bmp
    }

    fun share(ctx: Context, body: String, source: String) {
        try {
            val bmp = render(ctx, body, source)
            val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val f = File(dir, "card_${System.currentTimeMillis()}.png")
            FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
            val i = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ctx.startActivity(Intent.createChooser(i, "مشاركة كصورة").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            android.widget.Toast.makeText(ctx, "تعذّر إنشاء الصورة", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: OutOfMemoryError) {
            android.widget.Toast.makeText(ctx, "النص طويل جدًا لصورة", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
