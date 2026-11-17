package com.hisn.muslim

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/** خدمة تنزيل التحديث: بتفضل شغالة والتطبيق في الخلفية، وبتعرض نسبة التنزيل في إشعار. */
class UpdateService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val code = intent?.getIntExtra("code", 0) ?: 0
        val url = intent?.getStringExtra("url")
        val size = intent?.getLongExtra("size", 0L) ?: 0L
        if (url == null || code == 0) { Updater.progress = -1; stopSelf(); return START_NOT_STICKY }
        Notif.ensureChannels(this)
        fun note(pct: Int) = Notif.builder(this, Notif.CH_UPDATE_DL, "جارٍ تنزيل التحديث", if (pct > 0) "$pct%" else "بدء التنزيل…")
            .setOngoing(true).setOnlyAlertOnce(true).setAutoCancel(false)
            .setProgress(100, pct, pct <= 0).build()
        ServiceCompat.startForeground(
            this, Notif.ID_UPDATE_DL, note(0),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        )
        val app = applicationContext
        Thread {
            try {
                var last = -1
                val apk = Updater.downloadResumable(app, UpdateInfo(code, "", "", url, size)) { pct ->
                    Updater.progress = pct
                    if (pct != last && pct % 2 == 0) { last = pct; Notif.notify(app, Notif.ID_UPDATE_DL, note(pct)) }
                }
                Updater.progress = 100
                val pi = PendingIntent.getActivity(
                    app, 7, Updater.installIntent(app, apk), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                Notif.notify(
                    app, Notif.ID_UPDATE_DONE,
                    Notif.builder(app, Notif.CH_UPDATE_DL, "تم تنزيل التحديث", "اضغط لتثبيت النسخة الجديدة").setContentIntent(pi).build()
                )
                runCatching { Updater.install(app, apk) } // لو التطبيق قدّام المستخدم بيفتح التثبيت فورًا
                Updater.message = "بدأ التثبيت — أكمل من شاشة النظام."
            } catch (e: Exception) {
                Updater.message = "فشل التنزيل: ${e.message ?: "تحقق من الاتصال"}. اضغط تحميل مرة أخرى وهيكمّل من حيث وقف."
            } finally {
                Updater.progress = -1
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }.start()
        return START_NOT_STICKY
    }
}
