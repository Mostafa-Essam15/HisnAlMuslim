package com.hisn.muslim

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
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
    private val artCache = HashMap<String, Bitmap>()
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
        val cover = QuranAudio.current?.let { art(it.first, QuranAudio.lastReciter, reciter) }
        session?.setMetadata(
            MediaMetadataCompat.Builder().putString(MediaMetadataCompat.METADATA_KEY_TITLE, t).putString(MediaMetadataCompat.METADATA_KEY_ARTIST, reciter)
                .apply { if (cover != null) { putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, cover); putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, cover) } }.build()
        )
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
            .setLargeIcon(cover)
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
    /**
     * غلاف المشغّل: بيترسم بالكود (اسم السورة بخط أميري + دائرة القارئ). لو حطيت صورة للقارئ في
     * assets/reciters/<id>.jpg (بترخيص مناسب) بتظهر جوه الدائرة، وإلا بنعرض حرفه الأول.
     */
    private fun art(surah: Int, reciterId: String, reciterName: String): Bitmap {
        val key = "$surah|$reciterId"
        artCache[key]?.let { return it }
        val sz = 600
        val bm = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888)
        val c = Canvas(bm)
        val gold = 0xFFE8C766.toInt()
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, 0f, 0f, sz.toFloat(), 0xFF0E3326.toInt(), 0xFF04100B.toInt(), Shader.TileMode.CLAMP) }
        c.drawRoundRect(RectF(0f, 0f, sz.toFloat(), sz.toFloat()), 56f, 56f, bg)
        c.drawRoundRect(RectF(14f, 14f, sz - 14f, sz - 14f), 44f, 44f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = gold; alpha = 150 })
        val tf = try { androidx.core.content.res.ResourcesCompat.getFont(this, R.font.amiri_bold) } catch (_: Exception) { null } ?: Typeface.DEFAULT_BOLD
        // دائرة القارئ
        val cx = sz / 2f; val cy = 190f; val r = 105f
        c.drawCircle(cx, cy, r + 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5f; color = gold })
        val photo = try { assets.open("reciters/$reciterId.jpg").use { BitmapFactory.decodeStream(it) } } catch (_: Exception) { null }
        if (photo != null) {
            val clip = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
            c.save(); c.clipPath(clip)
            val side = minOf(photo.width, photo.height)
            val src = android.graphics.Rect((photo.width - side) / 2, (photo.height - side) / 2, (photo.width + side) / 2, (photo.height + side) / 2)
            c.drawBitmap(photo, src, RectF(cx - r, cy - r, cx + r, cy + r), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            c.restore()
        } else {
            c.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF16463A.toInt() })
            val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold; typeface = tf; textSize = 120f; textAlign = Paint.Align.CENTER }
            val letter = reciterName.trim().firstOrNull()?.toString() ?: "ق"
            c.drawText(letter, cx, cy + 38f, lp)
        }
        // اسم السورة (بيصغّر لو طويل)
        val title = "سورة ${Quran.surahName(surah)}"
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold; typeface = tf; textSize = 92f; textAlign = Paint.Align.CENTER }
        val maxW = sz - 90f
        val w = tp.measureText(title)
        if (w > maxW) tp.textSize = tp.textSize * maxW / w
        c.drawText(title, cx, 410f, tp)
        // اسم القارئ
        val rp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE9EFEC.toInt(); typeface = tf; textSize = 50f; textAlign = Paint.Align.CENTER }
        val rw = rp.measureText(reciterName)
        if (rw > maxW) rp.textSize = rp.textSize * maxW / rw
        c.drawText(reciterName, cx, 490f, rp)
        if (artCache.size > 8) artCache.clear()
        artCache[key] = bm
        return bm
    }
}
