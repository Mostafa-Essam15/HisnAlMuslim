package com.hisn.muslim

import android.Manifest
import android.annotation.SuppressLint
import android.location.Address
import android.location.Geocoder
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** بيتغير لما الموقع أو الإعدادات تتحدّث عشان الشاشات تتحسب من جديد */
val refreshTick = mutableIntStateOf(0)

class MainActivity : ComponentActivity() {
    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { fetchLocation() }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val h = TasbihBus.onVolume
        if (h != null && (event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN)) {
            if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0) h()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val req = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 33) req.add(Manifest.permission.POST_NOTIFICATIONS)
        permLauncher.launch(req.toTypedArray())
        Notif.ensureChannels(this)
        Updater.check(this)
        HisnMessagingService.subscribe()
        Reminders.scheduleAll(this)
        // إذن «العرض فوق التطبيقات» ضروري لظهور الأذان وسؤال «هل صليت؟» إجباريًا على الشاشة؛ بنطلبه مرة واحدة
        val pf = Prefs(this)
        if (!ForcedOverlay.can(this) && !pf.sp.getBoolean("overlayAsked", false)) {
            pf.sp.edit().putBoolean("overlayAsked", true).apply()
            startActivity(
                Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName"))
            )
        }
        Look.light = pf.lightTheme
        setContent {
            HisnTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { App() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // بنحدّث حالة الأذونات (عدم الإزعاج، المنبهات الدقيقة…) لما نرجع من شاشة إعدادات الأندرويد
        refreshTick.intValue++
        Scheduler.schedule(this)
    }

    @SuppressLint("MissingPermission")
    fun fetchLocation() {
        val ok = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!ok) return
        LocationServices.getFusedLocationProviderClient(this)
            .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    val p = Prefs(this)
                    p.lat = loc.latitude
                    p.lng = loc.longitude
                    p.hasLocation = true
                    Reminders.scheduleAll(this)
                    refreshTick.intValue++
                    resolveCity(loc.latitude, loc.longitude)
                }
            }
    }

    /** بيحوّل الإحداثيات لاسم مدينة (محتاج إنترنت) */
    private fun resolveCity(lat: Double, lng: Double) {
        if (!Geocoder.isPresent()) return
        Thread {
            try {
                val g = Geocoder(this, Locale("ar"))
                var list: List<Address>? = null
                if (Build.VERSION.SDK_INT >= 33) {
                    val latch = CountDownLatch(1)
                    g.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            list = addresses
                            latch.countDown()
                        }

                        override fun onError(errorMessage: String?) {
                            latch.countDown()
                        }
                    })
                    latch.await(10, TimeUnit.SECONDS)
                } else {
                    @Suppress("DEPRECATION")
                    list = g.getFromLocation(lat, lng, 1)
                }
                val a = list?.firstOrNull()
                val city = a?.locality ?: a?.subAdminArea ?: a?.adminArea
                val region = a?.adminArea
                val name = listOfNotNull(city, region).distinct().joinToString("، ")
                if (name.isNotBlank()) {
                    Prefs(this).city = name
                    runOnUiThread { refreshTick.intValue++ }
                }
            } catch (e: Exception) {
                // مفيش إنترنت أو خدمة الجيوكودر مش متاحة: بنكمّل بالإحداثيات
            }
        }.start()
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var tab by remember { mutableIntStateOf(0) }
    // ترحيل: كان رقم «صورتك» = ٥ قبل إضافة مشهد النهار
    remember {
        if (!prefs.sp.getBoolean("bgMig2", false)) {
            prefs.sp.edit().putBoolean("bgMig2", true).apply()
            if (prefs.bg == 5 && hasCustomBg(ctx)) prefs.bg = customBgIndex
        }
        Unit
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { Look.light = prefs.lightTheme }
    var bg by remember { mutableIntStateOf(prefs.bg.let { if (it in 101..199 || it > PHOTO_BASE + Photos.COUNT) customBgIndex else it }) }
    val tickNow = refreshTick.intValue
    val auto by androidx.compose.runtime.produceState(sceneNow(prefs), tickNow) {
        while (true) {
            value = sceneNow(prefs)
            kotlinx.coroutines.delay(60_000)
        }
    }
    val autoPhoto by androidx.compose.runtime.produceState(Photos.forNow(prefs), tickNow) {
        while (true) {
            value = Photos.forNow(prefs)
            kotlinx.coroutines.delay(30_000)
        }
    }
    var bgVersion by remember { mutableIntStateOf(0) }
    var quranJump by remember { mutableIntStateOf(0) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && saveCustomBg(ctx, uri)) {
            bgVersion++
            bg = customBgIndex
            prefs.bg = bg
        }
    }

    Box(Modifier.fillMaxSize()) {
        AppBackground(bg, bgVersion, auto, autoPhoto)
        // في الثيم الفاتح: طبقة كريمية شفافة فوق الخلفية عشان الصورة تظهر والنص الغامق يفضل مقروء
        if (Look.light) Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xA6FBF7EA), Color(0x8CEDE6D0)))))
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = OnCard,
            bottomBar = {
                NavigationBar(containerColor = if (Look.light) Color(0xF2FFFFFF) else Color(0xE6051510)) {
                    appTabs.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(t.icon, contentDescription = t.title) },
                            label = { Text(t.title, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.Black,
                                selectedTextColor = Gold,
                                indicatorColor = Gold,
                                unselectedIconColor = Color(0xFFB7C4BE),
                                unselectedTextColor = Color(0xFFB7C4BE)
                            )
                        )
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    0 -> HomeScreen(prefs) { t ->
                        if (t == 1 && homeQuranJump.intValue > 0) { quranJump = homeQuranJump.intValue; homeQuranJump.intValue = 0 }
                        tab = t
                    }
                    1 -> QuranScreen(prefs, quranJump) { quranJump = 0 }
                    2 -> AzkarTasbihScreen(prefs)
                    3 -> ChallengesScreen(prefs)
                    4 -> MoreScreen(prefs) { p -> quranJump = p; tab = 1 }
                    else -> SettingsScreen(
                        prefs, bg, bgVersion,
                        onBg = { bg = it; prefs.bg = it },
                        onBgPhoto = { photoPicker.launch("image/*") }
                    )
                }
            }
        }
    }
}
