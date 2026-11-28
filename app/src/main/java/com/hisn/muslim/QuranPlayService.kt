package com.hisn.muslim

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * مشغّل التلاوة في الخلفية: خدمة أمامية بإشعار وسائط (سابق/إيقاف مؤقت/تالي/إغلاق) وجلسة MediaSession
 * فتظهر أزرار التحكم على شاشة القفل وسماعات البلوتوث. الصوت نفسه بيشغّله QuranAudio، والخدمة بتبقي التطبيق حيًّا وتعرض الحالة.
 */
class QuranPlayService : Service() {
    private val h = Handler(Looper.getMainLooper())
    private var session: MediaSessionCompat? = null
    private var sig = ""
    private var idle = 0

    private val loop = object : Runnable {
        override fun run() {
            if (QuranAudio.current == null && !QuranAudio.loading) {
                if (++idle >= 3) { stopSelf(); return }
            } else idle = 0
            refresh(false)
            h.postDelayed(this, 700)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notif.ensureChannels(this)
        session = MediaSessionCompat(this, "QuranPlay").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { if (!QuranAudio.playing) QuranAudio.pauseResume() }
                override fun onPause() { if (QuranAudio.playing) QuranAudio.pauseResume() }
                override fun onStop() { QuranAudio.stop() }
                override fun onSkipToNext() { QuranAudio.skip(1) }
                override fun onSkipToPrevious() { QuranAudio.skip(-1) }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "PAUSE" -> QuranAudio.pauseResume()
            "NEXT" -> QuranAudio.skip(1)
            "PREV" -> QuranAudio.skip(-1)
            "STOP" -> { QuranAudio.stop(); stopSelf(); return START_NOT_STICKY }
        }
        refresh(true)
        h.removeCallbacks(loop)
        h.postDelayed(loop, 700)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        h.removeCallbacks(loop)
        session?.release(); session = null
        super.onDestroy()
    }

    private fun title(): String {
        val c = QuranAudio.current ?: return "تلاوة القرآن"
        return "سورة ${Quran.surahName(c.first)}" + if (c.second > 0) " • آية ${Quran.arabicDigits(c.second)}" else ""
    }

    private fun act(action: String, code: Int) = PendingIntent.getService(
        this, code, Intent(this, QuranPlayService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun refresh(force: Boolean) {
        val s = "${QuranAudio.current}|${QuranAudio.playing}|${QuranAudio.loading}"
        if (!force && s == sig) return
        sig = s
        val reciter = QuranAudio.reciters.firstOrNull { it.id == QuranAudio.lastReciter }?.name ?: ""
        val t = title()
        val playing = QuranAudio.playing
        session?.setMetadata(MediaMetadataCompat.Builder().putString(MediaMetadataCompat.METADATA_KEY_TITLE, t).putString(MediaMetadataCompat.METADATA_KEY_ARTIST, reciter).build())
        session?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
                .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_STOP or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
                .build()
        )
        val n = NotificationCompat.Builder(this, Notif.CH_QURAN)
            .setSmallIcon(R.drawable.ic_stat_mosque)
            .setColor(0xFF0F5C45.toInt())
            .setContentTitle(t)
            .setContentText(if (QuranAudio.loading) "جارٍ التحميل…" else reciter)
            .setContentIntent(Notif.openApp(this))
            .setOnlyAlertOnce(true)
            .setOngoing(playing || QuranAudio.loading)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, "السابقة", act("PREV", 1))
            .addAction(if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, if (playing) "إيقاف مؤقت" else "تشغيل", act("PAUSE", 2))
            .addAction(android.R.drawable.ic_media_next, "التالية", act("NEXT", 3))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "إغلاق", act("STOP", 4))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(session?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
        ServiceCompat.startForeground(
            this, Notif.ID_QURAN, n,
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        )
    }
}
