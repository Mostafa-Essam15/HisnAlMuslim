package com.hisn.muslim

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.Calendar

/** خلفيات الصور المرفقة داخل التطبيق (assets/bg/bgNN.jpg). أرقام الاختيار تبدأ من PHOTO_BASE. */
const val PHOTO_BASE = 200
const val BG_AUTO_PHOTO = -2

object Photos {
    const val COUNT = 19

    /** وسم كل صورة حسب وقتها (تقدير يدوي): ليل، فجر، نهار، عصر، مغرب */
    private val night = listOf(1, 5, 14, 17)
    private val dawn = listOf(10)
    private val day = listOf(2, 3, 6, 8, 11, 12, 16, 18, 19)
    private val asr = listOf(13, 7)
    private val maghrib = listOf(4, 9, 15, 7)

    fun name(i: Int) = "bg/bg%02d.jpg".format(i)

    /**
     * صورة الوقت الحالي في وضع «صور تلقائي»: بتتغيّر مع كل أذان (كل صلاة ليها ترتيبها)،
     * وكمان لما المشهد يتغيّر (مثلًا بعد الشروق)، وبتتبدّل من يوم لتاني.
     */
    fun forNow(p: Prefs, now: Long = System.currentTimeMillis()): Int {
        val scene = sceneNow(p, now)
        val prev = try { Engine.previous(p, now) } catch (e: Exception) { null }
        val cal = Calendar.getInstance().apply { if (prev != null) time = prev.second }
        val doy = cal.get(Calendar.DAY_OF_YEAR)
        val ord = prev?.first?.ordinal ?: 0
        return forScene(scene, doy * 7 + ord * 3)
    }

    /** صورة مناسبة لمشهد معيّن؛ seed بيحدد أي صورة من المجموعة */
    fun forScene(scene: Int, seed: Int = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)): Int {
        val pool = when (scene) {
            0 -> night
            1 -> dawn + day.take(2)
            5 -> day
            4 -> asr
            2 -> maghrib
            else -> day
        }
        return PHOTO_BASE + pool[Math.floorMod(seed, pool.size)]
    }

    /** بيحمّل صورة مصغّرة/كاملة من الـ assets (inSampleSize لتخفيف الذاكرة) */
    fun load(ctx: Context, i: Int, sample: Int = 1): ImageBitmap? = try {
        ctx.assets.open(name(i)).use { s ->
            val o = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeStream(s, null, o)?.asImageBitmap()
        }
    } catch (e: Exception) {
        null
    }
}
