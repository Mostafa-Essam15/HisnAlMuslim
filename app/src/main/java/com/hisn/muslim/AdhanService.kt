package com.hisn.muslim

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

/** حالة الأذان الحالية: بتتشارك بين الخدمة وشاشة الأذان الكاملة */
object AdhanState {
    val playing = mutableStateOf(false)
    val prayer = mutableStateOf<Prayer?>(null)
}

class AdhanService : Service() {
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var audioManager: AudioManager? = null
    private var prevVolume = -1
    private var focusRequest: AudioFocusRequest? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val prayer = runCatching { Prayer.valueOf(intent?.getStringExtra("prayer") ?: "") }
            .getOrDefault(Prayer.FAJR)
        val prefs = Prefs(this)

        Notif.ensureChannels(this)
        val tip = Content.tip(prayer)
        val stopPi = PendingIntent.getService(
            this, 2, Intent(this, AdhanService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val fullPi = PendingIntent.getActivity(
            this, 4,
            Intent(this, AdhanActivity::class.java).putExtra("prayer", prayer.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // الحالة لازم تتظبط قبل ما الشاشة الكاملة تفتح
        AdhanState.prayer.value = prayer
        AdhanState.playing.value = true

        val notification = Notif.builder(
            this, Notif.CH_ADHAN, "حان الآن موعد أذان ${prayer.ar}", "${tip.kind}: ${tip.text}",
            "${tip.kind}: ${tip.text}\n${tip.source}"
        )
            .setContentIntent(fullPi)
            .setFullScreenIntent(fullPi, true)
            .addAction(0, "إيقاف الأذان", stopPi)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
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
        val am = getSystemService(AudioManager::class.java)
        audioManager = am

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        // التركيز الصوتي: يوقف أي مشغّل تاني أثناء الأذان
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(attrs)
            .build()
        am.requestAudioFocus(req)
        focusRequest = req

        // مستوى صوت الأذان (قناة المنبه) حسب الإعدادات
        try {
            prevVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            am.setStreamVolume(
                AudioManager.STREAM_ALARM,
                (max * prefs.adhanVolume / 100f).roundToInt().coerceIn(1, max), 0
            )
        } catch (e: Exception) {
            prevVolume = -1 // وضع عدم الإزعاج ممكن يمنع تغيير الصوت
        }

        val muezzin = Muezzins.byKey(prefs.muezzin)
        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(attrs)
            if (Muezzins.isDownloaded(this, muezzin)) {
                mp.setDataSource(Muezzins.file(this, muezzin).absolutePath)
                // الأذان القصير: أول ٣٠ ثانية فقط
                if (!prefs.fullAdhan) handler.postDelayed({ stopSelf() }, Muezzins.SHORT_SECONDS * 1000L)
            } else {
                // المؤذن المختار لسه متحملش → صوت المنبه الافتراضي ١٠ ثواني
                mp.setDataSource(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                handler.postDelayed({ stopSelf() }, 10_000)
            }
            mp.setOnCompletionListener { stopSelf() }
            mp.setOnErrorListener { _, _, _ -> stopSelf(); true }
            mp.prepare()
            mp.setVolume(0f, 0f)
            mp.start()
            player = mp
            fadeIn(mp)
        } catch (e: Exception) {
            stopSelf()
        }
    }

    /** تصاعد تدريجي للصوت في ثانيتين عشان ما يفاجئش */
    private fun fadeIn(mp: MediaPlayer) {
        var step = 0
        val r = object : Runnable {
            override fun run() {
                if (player !== mp) return
                step++
                val v = (step / 12f).coerceAtMost(1f)
                runCatching { mp.setVolume(v, v) }
                if (step < 12) handler.postDelayed(this, 160)
            }
        }
        handler.post(r)
    }

    private fun releasePlayer() {
        player?.let {
            runCatching { if (it.isPlaying) it.stop() }
            it.release()
        }
        player = null
    }

    override fun onDestroy() {
        AdhanState.playing.value = false
        handler.removeCallbacksAndMessages(null)
        releasePlayer()
        audioManager?.let { am ->
            focusRequest?.let { am.abandonAudioFocusRequest(it) }
            if (prevVolume >= 0) runCatching { am.setStreamVolume(AudioManager.STREAM_ALARM, prevVolume, 0) }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val NOTIF_ID = 10
        const val ACTION_STOP = "com.hisn.muslim.STOP"

        fun start(ctx: Context, prayer: Prayer) {
            ContextCompat.startForegroundService(
                ctx, Intent(ctx, AdhanService::class.java).putExtra("prayer", prayer.name)
            )
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, AdhanService::class.java).setAction(ACTION_STOP))
        }
    }
}
