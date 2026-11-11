package com.hisn.muslim

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** تحديث الموقع (من الشاشة أو من المنبّه الدوري في الخلفية) وتحويله لاسم مدينة. */
object LocationUpdater {
    fun hasPermission(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasBackgroundPermission(ctx: Context) =
        Build.VERSION.SDK_INT < 29 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** هل خدمات Google Play موجودة (أجهزة هواوي الجديدة مثلًا مفيهاش) */
    fun hasGms(ctx: Context) = try {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(ctx.applicationContext) == ConnectionResult.SUCCESS
    } catch (e: Throwable) { false }

    /** بديل بدون Google: LocationManager بتاع الأندرويد (شبكة ثم GPS) */
    @SuppressLint("MissingPermission")
    private fun fetchPlatform(app: Context, onDone: (Boolean) -> Unit) {
        val lm = app.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
        if (lm == null) { onDone(false); return }
        val fine = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val providers = (listOf(android.location.LocationManager.NETWORK_PROVIDER) + if (fine) listOf(android.location.LocationManager.GPS_PROVIDER) else emptyList())
            .filter { try { lm.isProviderEnabled(it) } catch (e: Exception) { false } }
        fun lastKnown(): android.location.Location? = providers.mapNotNull { try { lm.getLastKnownLocation(it) } catch (e: Exception) { null } }.maxByOrNull { it.time }
        val done = java.util.concurrent.atomic.AtomicBoolean(false)
        fun finish(loc: android.location.Location?) {
            if (!done.compareAndSet(false, true)) return
            val l = loc ?: lastKnown()
            if (l != null) apply(app, l)
            onDone(l != null)
        }
        if (providers.isEmpty()) { finish(null); return }
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                lm.getCurrentLocation(providers.first(), null, ContextCompat.getMainExecutor(app)) { finish(it) }
            } else {
                @Suppress("DEPRECATION")
                lm.requestSingleUpdate(providers.first(), object : android.location.LocationListener {
                    override fun onLocationChanged(l: android.location.Location) { finish(l) }
                    @Deprecated("Deprecated in Java") override fun onStatusChanged(p: String?, st: Int, e: android.os.Bundle?) {}
                    override fun onProviderEnabled(p: String) {}
                    override fun onProviderDisabled(p: String) {}
                }, android.os.Looper.getMainLooper())
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ finish(null) }, 20_000L)
            }
        } catch (e: Exception) { finish(null) }
    }

    /** يحدّث الموقع ويبلّغ بالنتيجة: true لو اتحدّد موقع. [onDone] بيتنادى مرة واحدة دايمًا. */
    @SuppressLint("MissingPermission")
    fun fetch(ctx: Context, onDone: (Boolean) -> Unit) {
        val app = ctx.applicationContext
        if (!hasPermission(app)) { onDone(false); return }
        if (!hasGms(app)) { fetchPlatform(app, onDone); return }
        val client = LocationServices.getFusedLocationProviderClient(app)
        fun finish(loc: android.location.Location?) {
            if (loc != null) apply(app, loc)
            onDone(loc != null)
        }
        try {
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    if (loc != null) finish(loc)
                    else client.lastLocation.addOnSuccessListener { finish(it) }.addOnFailureListener { finish(null) }
                }
                .addOnFailureListener { finish(null) }
        } catch (e: Exception) { onDone(false) }
    }

    fun apply(ctx: Context, loc: android.location.Location) {
        val p = Prefs(ctx)
        val moved = !p.hasLocation || FloatArray(1).also { android.location.Location.distanceBetween(p.lat, p.lng, loc.latitude, loc.longitude, it) }[0] > 500f
        p.lat = loc.latitude
        p.lng = loc.longitude
        p.hasLocation = true
        p.locAt = System.currentTimeMillis()
        if (moved) {
            Reminders.scheduleAll(ctx)
            PrayerWidget.updateAll(ctx)
        }
        refreshTick.intValue++
        if (moved || p.city.isBlank()) resolveCity(ctx, loc.latitude, loc.longitude)
    }

    /** بيحوّل الإحداثيات لاسم مدينة (محتاج إنترنت) */
    fun resolveCity(ctx: Context, lat: Double, lng: Double) {
        if (!Geocoder.isPresent()) return
        Thread {
            try {
                val g = Geocoder(ctx, Locale("ar"))
                var list: List<Address>? = null
                if (Build.VERSION.SDK_INT >= 33) {
                    val latch = CountDownLatch(1)
                    g.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) { list = addresses; latch.countDown() }
                        override fun onError(errorMessage: String?) { latch.countDown() }
                    })
                    latch.await(10, TimeUnit.SECONDS)
                } else {
                    @Suppress("DEPRECATION")
                    list = g.getFromLocation(lat, lng, 1)
                }
                val a = list?.firstOrNull()
                val city = a?.locality ?: a?.subAdminArea ?: a?.adminArea
                val name = listOfNotNull(city, a?.adminArea).distinct().joinToString("، ")
                if (name.isNotBlank()) {
                    Prefs(ctx).city = name
                    refreshTick.intValue++
                    PrayerWidget.updateAll(ctx)
                }
            } catch (e: Exception) {
                // مفيش إنترنت أو خدمة الجيوكودر مش متاحة: بنكمّل بالإحداثيات
            }
        }.start()
    }
}

var Prefs.bgLocation: Boolean
    get() = sp.getBoolean("bgLocation", true)
    set(v) { sp.edit().putBoolean("bgLocation", v).apply() }
