package com.hisn.muslim

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class AdhanService : Service() {
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val prayer = runCatching { Prayer.valueOf(intent?.getStringExtra("prayer") ?: "") }
            .getOrDefault(Prayer.FAJR)
        val prefs = Prefs(this)

        createChannel()
        val tip = Content.tip(prayer)
        val stopPi = PendingIntent.getService(
            this, 2, Intent(this, AdhanService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val openPi = PendingIntent.getActivity(
            this, 3, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("حان الآن موعد أذان ${prayer.ar}")
            .setContentText("${tip.kind}: ${tip.text}")
            .setStyle(NotificationCompat.BigTextStyle().bigText("${tip.kind}: ${tip.text}\n${tip.source}"))
            .setContentIntent(openPi)
            .addAction(0, "إيقاف الأذان", stopPi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .build()

        ServiceCompat.startForeground(
            this, NOTIF_ID, notification,
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        )
        play(prefs)
        return START_NOT_STICKY
    }

    private fun play(prefs: Prefs) {
        releasePlayer()
        val muezzin = Muezzins.byKey(prefs.muezzin)
        val file = Muezzins.file(this, muezzin)
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(attrs)
            if (Muezzins.isDownloaded(this, muezzin)) {
                mp.setDataSource(file.absolutePath)
                // الأذان القصير: أول ٣٠ ثانية فقط من الملف المحمّل
                if (!prefs.fullAdhan) {
                    handler.postDelayed({ stopSelf() }, Muezzins.SHORT_SECONDS * 1000L)
                }
            } else {
                // المؤذن المختار لسه متحملش → صوت المنبه الافتراضي لمدة ١٠ ثواني
                mp.setDataSource(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                handler.postDelayed({ stopSelf() }, 10_000)
            }
            mp.setOnCompletionListener { stopSelf() }
            mp.setOnErrorListener { _, _, _ -> stopSelf(); true }
            mp.prepare()
            mp.start()
            player = mp
        } catch (e: Exception) {
            stopSelf()
        }
    }

    private fun releasePlayer() {
        player?.let {
            runCatching { if (it.isPlaying) it.stop() }
            it.release()
        }
        player = null
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "الأذان", NotificationManager.IMPORTANCE_HIGH)
            ch.setSound(null, null) // الصوت بيشغله المشغّل بنفسه
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        releasePlayer()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val CHANNEL = "adhan_channel"
        const val NOTIF_ID = 10
        const val ACTION_STOP = "com.hisn.muslim.STOP"

        fun start(ctx: Context, prayer: Prayer) {
            androidx.core.content.ContextCompat.startForegroundService(
                ctx, Intent(ctx, AdhanService::class.java).putExtra("prayer", prayer.name)
            )
        }
    }
}
