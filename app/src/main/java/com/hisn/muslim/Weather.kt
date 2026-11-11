@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.hisn.muslim

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

class WDay(val date: String, val code: Int, val max: Int, val min: Int)
class WSnap(val temp: Int, val feels: Int, val humidity: Int, val wind: Int, val code: Int, val isDay: Boolean, val days: List<WDay>)

/**
 * الطقس من Open-Meteo (مجاني بدون مفتاح، بيانات CC BY 4.0). بيتخزّن آخر رد على الجهاز
 * ويتحدّث كل ٣٠ دقيقة أو لو الموقع اتغيّر، ويظهر آخر نسخة محفوظة لو مفيش إنترنت.
 */
object Weather {
    var snap by mutableStateOf<WSnap?>(null)
        private set

    private fun parse(json: String): WSnap? = try {
        val o = JSONObject(json)
        val c = o.getJSONObject("current")
        val d = o.getJSONObject("daily")
        val t = d.getJSONArray("time"); val cd = d.getJSONArray("weather_code")
        val mx = d.getJSONArray("temperature_2m_max"); val mn = d.getJSONArray("temperature_2m_min")
        WSnap(
            c.getDouble("temperature_2m").roundToInt(), c.getDouble("apparent_temperature").roundToInt(),
            c.getInt("relative_humidity_2m"), c.getDouble("wind_speed_10m").roundToInt(),
            c.getInt("weather_code"), c.optInt("is_day", 1) == 1,
            (0 until t.length()).map { WDay(t.getString(it), cd.getInt(it), mx.getDouble(it).roundToInt(), mn.getDouble(it).roundToInt()) }
        )
    } catch (_: Exception) { null }

    /** من thread خلفي */
    fun refresh(prefs: Prefs) {
        val key = "%.2f,%.2f".format(Locale.US, prefs.lat, prefs.lng)
        val cached = prefs.sp.getString("weatherJson", null)
        val at = prefs.sp.getLong("weatherAt", 0L)
        if (cached != null && snap == null) parse(cached)?.let { snap = it }
        val fresh = cached != null && prefs.sp.getString("weatherKey", "") == key && System.currentTimeMillis() - at < 30 * 60_000L
        if (fresh) return
        var c: HttpURLConnection? = null
        try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=${prefs.lat}&longitude=${prefs.lng}" +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,is_day" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=5"
            c = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 10_000; readTimeout = 15_000 }
            if (c.responseCode !in 200..299) return
            val body = c.inputStream.bufferedReader().use { it.readText() }
            val s = parse(body) ?: return
            prefs.sp.edit().putString("weatherJson", body).putLong("weatherAt", System.currentTimeMillis()).putString("weatherKey", key).apply()
            snap = s
        } catch (_: Exception) {
        } finally { c?.disconnect() }
    }

    /** رموز WMO حسب توثيق Open-Meteo */
    fun label(code: Int) = when (code) {
        0 -> "صافٍ"; 1 -> "غالبًا صافٍ"; 2 -> "غائم جزئيًا"; 3 -> "غائم"
        45, 48 -> "ضباب"
        51, 53, 55, 56, 57 -> "رذاذ"
        61, 63, 65, 66, 67 -> "مطر"
        71, 73, 75, 77 -> "ثلج"
        80, 81, 82 -> "زخات مطر"
        85, 86 -> "زخات ثلج"
        95, 96, 99 -> "عاصفة رعدية"
        else -> "—"
    }

    fun icon(code: Int, day: Boolean = true) = when (code) {
        0, 1 -> if (day) "☀️" else "🌙"
        2 -> if (day) "⛅" else "☁️"
        3 -> "☁️"
        45, 48 -> "🌫️"
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> "🌧️"
        71, 73, 75, 77, 85, 86 -> "❄️"
        95, 96, 99 -> "⛈️"
        else -> "🌡️"
    }

    private val dayNames = listOf("الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد")
    fun dayName(date: String): String = try {
        dayNames[java.time.LocalDate.parse(date).dayOfWeek.value - 1]
    } catch (_: Exception) { date }
}

/** سطر صغير داخل كارت الصلاة القادمة */
@Composable
fun WeatherLine(onOpen: () -> Unit = {}) {
    val s = Weather.snap ?: return
    Text(
        "${Weather.icon(s.code, s.isDay)} ${Quran.arabicDigits(s.temp)}° • ${Weather.label(s.code)} • العظمى ${Quran.arabicDigits(s.days.firstOrNull()?.max ?: s.temp)}° / الصغرى ${Quran.arabicDigits(s.days.firstOrNull()?.min ?: s.temp)}°",
        fontSize = 13.sp, color = Muted
    )
}

/** قسم الطقس: الحالة الحالية وتوقعات ٥ أيام */
@Composable
fun WeatherCardContent(city: String) {
    val s = Weather.snap
    SectionTitle("الطقس" + if (city.isNotBlank()) " • $city" else "", Icons.Rounded.AutoAwesome)
    if (s == null) {
        Text("جارٍ تحميل الطقس… يحتاج اتصالًا بالإنترنت أول مرة.", color = Muted, fontSize = 13.sp)
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text("${Quran.arabicDigits(s.temp)}°", fontSize = 46.sp, fontWeight = FontWeight.Light)
            Text(Weather.label(s.code), color = Gold, fontSize = 15.sp)
        }
        Text(Weather.icon(s.code, s.isDay), fontSize = 56.sp)
    }
    Spacer(Modifier.height(6.dp))
    Text(
        "الإحساس ${Quran.arabicDigits(s.feels)}° • الرطوبة ${Quran.arabicDigits(s.humidity)}٪ • الرياح ${Quran.arabicDigits(s.wind)} كم/س",
        fontSize = 12.sp, color = Muted
    )
    Spacer(Modifier.height(8.dp))
    androidx.compose.foundation.layout.FlowRow(
        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp),
        maxItemsInEachRow = if (bigFont()) 3 else 5
    ) {
        s.days.forEachIndexed { i, d ->
            Column(Modifier.weight(1f).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (i == 0) "اليوم" else Weather.dayName(d.date), fontSize = 11.sp, color = Muted, textAlign = TextAlign.Center)
                Text(Weather.icon(d.code), fontSize = 22.sp)
                Text("${Quran.arabicDigits(d.max)}°", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("${Quran.arabicDigits(d.min)}°", fontSize = 12.sp, color = Muted)
            }
        }
    }
    Text("بيانات الطقس: Open-Meteo.com", fontSize = 10.sp, color = Muted)
}
