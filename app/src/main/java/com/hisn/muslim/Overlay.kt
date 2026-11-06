package com.hisn.muslim

import android.content.Context
import android.content.Intent
import android.graphics.Color as AColor
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

class OverlayButton(val label: String, val filled: Boolean, val action: () -> Unit)

/**
 * نافذة ملء الشاشة فوق أي تطبيق (تحتاج إذن «العرض فوق التطبيقات»).
 * بنستخدمها للأذان وللسؤال «هل أتممت الصلاة؟» عشان تظهر إجباريًا على الشاشة.
 * فوق شاشة القفل: الأذان بيعتمد على AdhanActivity/الإشعار ملء الشاشة لأن النظام ما بيسمحش لنوافذ العرض بالظهور هناك.
 */
object ForcedOverlay {
    private var view: View? = null
    private var key: String = ""
    private val handler = Handler(Looper.getMainLooper())

    fun can(ctx: Context) = Settings.canDrawOverlays(ctx)

    fun isShowing(k: String) = view != null && key == k

    fun dismiss(ctx: Context, onlyKey: String? = null) {
        if (onlyKey != null && key != onlyKey) return
        val v = view ?: return
        runCatching { ctx.applicationContext.getSystemService(WindowManager::class.java).removeView(v) }
        view = null
        key = ""
        handler.removeCallbacksAndMessages(null)
    }

    /** بيرجّع true لو النافذة اتعرضت */
    fun show(
        ctx: Context, k: String, headline: String, title: String, body: String,
        buttons: List<OverlayButton>, autoDismissMs: Long = 0L
    ): Boolean {
        val app = ctx.applicationContext
        if (!can(app)) return false
        dismiss(app)
        val dp = app.resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()

        val root = LinearLayout(app).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xF2063D2E.toInt(), 0xF2020F0B.toInt())
            )
            setPadding(px(28), px(48), px(28), px(48))
            isClickable = true // يمنع اللمس من النفاذ للتطبيق اللي وراه
        }
        fun tv(text: String, size: Float, color: Int, bold: Boolean = false, serif: Boolean = false) = TextView(app).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            if (bold) setTypeface(typeface, Typeface.BOLD)
            if (serif) typeface = Typeface.create(Typeface.SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        root.addView(tv(headline, 15f, 0xFFE8C766.toInt(), bold = true))
        root.addView(tv(title, 34f, AColor.WHITE, bold = true, serif = true).apply { setPadding(0, px(14), 0, px(14)) })
        if (body.isNotBlank()) root.addView(tv(body, 16f, 0xFFD8E2DD.toInt(), serif = true).apply {
            setLineSpacing(0f, 1.3f)
            setPadding(0, 0, 0, px(24))
        })
        buttons.forEach { b ->
            val t = TextView(app).apply {
                text = b.label
                textSize = 17f
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setTextColor(if (b.filled) AColor.BLACK else 0xFFE8C766.toInt())
                setPadding(px(24), px(14), px(24), px(14))
                background = GradientDrawable().apply {
                    cornerRadius = 40 * dp
                    if (b.filled) setColor(0xFFE8C766.toInt()) else setStroke(px(1), 0xFFE8C766.toInt())
                }
                setOnClickListener { b.action() }
            }
            root.addView(
                t,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { setMargins(0, px(8), 0, px(8)) }
            )
        }

        @Suppress("DEPRECATION")
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        )
        return try {
            app.getSystemService(WindowManager::class.java).addView(root, params)
            view = root
            key = k
            if (autoDismissMs > 0) handler.postDelayed({ if (view === root) dismiss(app) }, autoDismissMs)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** زر بيبعت نفس إجراءات إشعار التذكير (DONE/P_YES…) ثم يقفل النافذة */
    fun reminderButton(ctx: Context, k: String, label: String, filled: Boolean, type: String, prayer: Prayer) =
        OverlayButton(label, filled) {
            ctx.applicationContext.sendBroadcast(
                Intent(ctx.applicationContext, ReminderReceiver::class.java)
                    .setPackage(ctx.packageName)
                    .putExtra("type", type).putExtra("prayer", prayer.name)
            )
            dismiss(ctx, k)
        }
}
