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
    private var keepNotification = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val prayer = runCatching { Prayer.valueOf(intent?.getStringExtra("prayer") ?: "") }
            .getOrDefault(Prayer.FAJR)
        val isTest = intent?.getBooleanExtra("test", false) ?: false
        val scheduledAt = intent?.getLongExtra("at", 0L) ?: 0L
        val prefs = Prefs(this)

        Notif.ensureChannels(this)
        val am = getSystemService(AudioManager::class.java)
        audioManager = am
        val ringerNormal = am.ringerMode == AudioManager.RINGER_MODE_NORMAL
        val lateMs = if (scheduledAt > 0) System.currentTimeMillis() - scheduledAt else 0L
        val decision = AdhanPolicy.decide(isTest, ringerNormal, prefs.playInSilent, lateMs)

        // نفس الأذان شغّال فعلًا → ما نبدأهوش تاني (منع التكرار)
        val duplicate = decision == AdhanPolicy.Decision.PLAY && !isTest &&
            AdhanState.playing.value && AdhanState.prayer.value == prayer && player != null

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

        val notification = if (decision == AdhanPolicy.Decision.PLAY) {
            // الحالة لازم تتظبط قبل ما الشاشة الكاملة تفتح
            AdhanState.prayer.value = prayer
            AdhanState.playing.value = true
            Notif.builder(
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
        } else {
            val why = if (decision == AdhanPolicy.Decision.SKIP_SILENT)
                "الموبايل على الصامت أو الاهتزاز، فالأذان ظهر كإشعار بدون صوت. لتشغيله بصوت فعّل «تشغيل الأذان في وضع الصامت» من الإعدادات."
            else "وصل التنبيه متأخرًا عن وقت الأذان، فما اتشغّلش الصوت."
            Notif.builder(this, Notif.CH_ADHAN, "حان موعد أذان ${prayer.ar}", why)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .build()
        }

        // لازم نناديها خلال ٥ ثواني من startForegroundService في كل الحالات
        ServiceCompat.startForeground(
            this, NOTIF_ID, notification,
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        )

        if (decision == AdhanPolicy.Decision.PLAY && !duplicate) forceAdhanOnScreen(prayer, tip)

        when {
            decision != AdhanPolicy.Decision.PLAY -> {
                // سيب الإشعار ظاهر واقفل الخدمة
                keepNotification = true
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
            duplicate -> Unit
            else -> play(prefs, prayer)
        }
        return START_NOT_STICKY
    }

    private fun play(prefs: Prefs, prayer: Prayer) {
        releasePlayer()
        val am = audioManager ?: getSystemService(AudioManager::class.java)

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        // «عدم الإزعاج - صمت تام» بيكتم المنبهات كمان. لو المستخدم فعّل الخيار وأدّى الإذن بنفسه،
        // نحوّل مؤقتًا لـ«المنبهات فقط» ونرجّع الحالة القديمة بعد الأذان.
        if (prefs.playInSilent) DndGuard.allowAlarmsTemporarily(this, prefs)

        // التركيز الصوتي: يوقف أي مشغّل تاني أثناء الأذان
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(attrs)
            .build()
        am.requestAudioFocus(req)
        focusRequest = req

        // مستوى الصوت اللي اخترته بيتطبق فورًا (من غير تصاعد تدريجي)
        try {
            if (prevVolume < 0) prevVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            am.setStreamVolume(
                AudioManager.STREAM_ALARM,
                (max * prefs.adhanVolume / 100f).roundToInt().coerceIn(1, max), 0
            )
        } catch (e: Exception) {
            // وضع عدم الإزعاج ممكن يمنع تغيير مستوى الصوت؛ بنكمّل بالمستوى الحالي
        }

        val muezzin = Muezzins.byKey(prefs.effectiveMuezzin(prayer))
        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(attrs)
            if (prayer == Prayer.FAJR && prefs.fajrCustomOn && Muezzins.hasFajr(this)) {
                // أذان الفجر الخاص بيشتغل كامل دايمًا: «الصلاة خير من النوم» بتيجي بعد أول ٣٠ ثانية
                mp.setDataSource(Muezzins.fajrFile(this).absolutePath)
            } else if (Muezzins.isDownloaded(this, muezzin)) {
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
            mp.start() // بأعلى مستوى للمشغّل، والتحكم بالصوت من قناة المنبه فقط
            player = mp
        } catch (e: Exception) {
            stopSelf()
        }
    }

    /** إظهار شاشة الأذان إجباريًا: نافذة فوق التطبيقات (لو الإذن مفعّل) + محاولة فتح الشاشة الكاملة مباشرة */
    private fun forceAdhanOnScreen(prayer: Prayer, tip: Tip) {
        ForcedOverlay.show(
            this, "adhan", "🕌 حان الآن موعد", "أذان ${prayer.ar}", "${tip.kind}: ${tip.text}\n${tip.source}" + (if (prayer == Prayer.FAJR) "\n\nفي أذان الفجر يُقال: «الصلاة خير من النوم» مرتين" else ""),
            listOf(
                OverlayButton("⏹ إيقاف الأذان", true) {
                    ForcedOverlay.dismiss(this, "adhan")
                    AdhanService.stop(this)
                },
                OverlayButton("فتح التطبيق", false) {
                    ForcedOverlay.dismiss(this, "adhan")
                    startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            )
        )
        // بإذن «العرض فوق التطبيقات» النظام بيسمح بفتح الشاشة من الخلفية؛ لو رفض فالإشعار ملء الشاشة بيكمّل
        runCatching {
            startActivity(
                Intent(this, AdhanActivity::class.java).putExtra("prayer", prayer.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }
    }

    private fun releasePlayer() {
        player?.let {
            runCatching { if (it.isPlaying) it.stop() }
            it.release()
        }
        player = null
    }

    override fun onDestroy() {
        ForcedOverlay.dismiss(this, "adhan")
        AdhanState.playing.value = false
        handler.removeCallbacksAndMessages(null)
        releasePlayer()
        audioManager?.let { am ->
            focusRequest?.let { am.abandonAudioFocusRequest(it) }
            if (prevVolume >= 0) runCatching { am.setStreamVolume(AudioManager.STREAM_ALARM, prevVolume, 0) }
        }
        DndGuard.restore(this, Prefs(this))
        if (!keepNotification) stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val NOTIF_ID = 10
        const val ACTION_STOP = "com.hisn.muslim.STOP"

        fun start(ctx: Context, prayer: Prayer, scheduledAt: Long = 0L, test: Boolean = false) {
            ContextCompat.startForegroundService(
                ctx,
                Intent(ctx, AdhanService::class.java)
                    .putExtra("prayer", prayer.name)
                    .putExtra("at", scheduledAt)
                    .putExtra("test", test)
            )
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, AdhanService::class.java).setAction(ACTION_STOP))
        }
    }
}
