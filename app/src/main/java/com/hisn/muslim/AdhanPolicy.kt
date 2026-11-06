package com.hisn.muslim

import android.app.NotificationManager
import android.content.Context

/** قرار تشغيل الأذان: دالة نقية عشان تتختبر بدون جهاز. */
object AdhanPolicy {
    enum class Decision { PLAY, SKIP_SILENT, SKIP_LATE }

    /** لو التنبيه اتسلّم متأخر أكتر من كده (جهاز كان مقفول مثلًا) مانشغّلش أذان فات وقته */
    const val MAX_LATE_MS = 15 * 60_000L

    /**
     * @param isTest تجربة يدوية من الإعدادات (مالهاش علاقة بالتأخير)
     * @param ringerNormal الموبايل على وضع الرنين العادي (مش صامت ولا اهتزاز)
     * @param playInSilent خيار «تشغيل الأذان في وضع الصامت»
     * @param lateMs الفرق بين وقت الوصول الفعلي ووقت الأذان المجدول (٠ لو مجهول)
     */
    fun decide(isTest: Boolean, ringerNormal: Boolean, playInSilent: Boolean, lateMs: Long): Decision = when {
        !isTest && lateMs > MAX_LATE_MS -> Decision.SKIP_LATE
        !ringerNormal && !playInSilent -> Decision.SKIP_SILENT
        else -> Decision.PLAY
    }

    /** منع التكرار: نفس الصلاة بنفس وقت الجدولة */
    fun isDuplicate(lastKey: String, key: String) = key.isNotEmpty() && lastKey == key
}

/**
 * «عدم الإزعاج» حاجة منفصلة عن الصامت. وضع «صمت تام» (TOTAL SILENCE) بيمنع حتى المنبهات.
 * التطبيق مش بيغيّر حاجة إلا لو: الخيار شغّال + المستخدم أدّى إذن «الوصول لسياسة الإشعارات» بنفسه،
 * وبيرجّع الحالة القديمة بعد الأذان مباشرة.
 */
object DndGuard {
    private fun nm(ctx: Context) = ctx.getSystemService(NotificationManager::class.java)

    fun hasAccess(ctx: Context) = nm(ctx).isNotificationPolicyAccessGranted

    fun filter(ctx: Context) = nm(ctx).currentInterruptionFilter

    fun isActive(ctx: Context): Boolean {
        val f = filter(ctx)
        return f != NotificationManager.INTERRUPTION_FILTER_ALL && f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }

    /** «صمت تام» بيكتم حتى قناة المنبه */
    fun blocksAlarms(ctx: Context) = filter(ctx) == NotificationManager.INTERRUPTION_FILTER_NONE

    /** بيحوّل مؤقتًا لـ«المنبهات فقط». بيرجّع true لو غيّر فعلًا. */
    fun allowAlarmsTemporarily(ctx: Context, prefs: Prefs): Boolean {
        if (!hasAccess(ctx) || !blocksAlarms(ctx)) return false
        return try {
            prefs.prevDnd = NotificationManager.INTERRUPTION_FILTER_NONE
            nm(ctx).setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
            true
        } catch (e: SecurityException) {
            prefs.prevDnd = 0
            false
        }
    }

    /** بيرجّع حالة «عدم الإزعاج» اللي كانت قبل الأذان (لو إحنا اللي غيّرناها). */
    fun restore(ctx: Context, prefs: Prefs) {
        val prev = prefs.prevDnd
        if (prev == 0) return
        prefs.prevDnd = 0
        if (!hasAccess(ctx)) return
        runCatching { nm(ctx).setInterruptionFilter(prev) }
    }
}
