package com.example.data.remote

import com.example.data.model.NewsHeadline
import com.example.data.model.WeatherInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class LiveAssistantInfoClient {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchWeather(cityQuery: String): Result<WeatherInfo> = withContext(Dispatchers.IO) {
        try {
            val cleanCity = cityQuery.trim().ifBlank { "New Delhi" }
            val encoded = URLEncoder.encode(cleanCity, "UTF-8")
            val geoReq = Request.Builder()
                .url("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=en&format=json")
                .build()
            val (lat, lon, resolvedName) = httpClient.newCall(geoReq).execute().use { resp ->
                if (!resp.isSuccessful) error("Geocoding failed (${resp.code})")
                val json = JSONObject(resp.body?.string().orEmpty())
                val results = json.optJSONArray("results")
                if (results == null || results.length() == 0) {
                    error("Could not find location '$cleanCity'.")
                }
                val first = results.getJSONObject(0)
                Triple(
                    first.getDouble("latitude"),
                    first.getDouble("longitude"),
                    "${first.optString("name")}, ${first.optString("country")}".trim(',', ' ')
                )
            }

            val weatherReq = Request.Builder()
                .url("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,wind_speed_10m,weather_code")
                .build()
            httpClient.newCall(weatherReq).execute().use { resp ->
                if (!resp.isSuccessful) error("Weather service failed (${resp.code})")
                val current = JSONObject(resp.body?.string().orEmpty()).getJSONObject("current")
                val temp = current.getDouble("temperature_2m")
                val wind = current.getDouble("wind_speed_10m")
                val code = current.optInt("weather_code", 0)
                Result.success(
                    WeatherInfo(
                        locationName = resolvedName,
                        temperatureC = temp,
                        windSpeedKmh = wind,
                        conditionLabel = weatherCodeToLabel(code)
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchDailyNews(hindi: Boolean): Result<List<NewsHeadline>> = withContext(Dispatchers.IO) {
        try {
            val url = if (hindi) {
                "https://news.google.com/rss?hl=hi&gl=IN&ceid=IN:hi"
            } else {
                "https://news.google.com/rss?hl=en-IN&gl=IN&ceid=IN:en"
            }
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "JarvisVIP-Android/1.0")
                .build()
            val xml = httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("News feed error (${resp.code})")
                resp.body?.string().orEmpty()
            }
            Result.success(parseRssHeadlines(xml).take(6))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchQuickKnowledge(topic: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val clean = topic.trim().replace(' ', '_')
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val req = Request.Builder()
                .url("https://en.wikipedia.org/api/rest_v1/page/summary/$encoded")
                .header("User-Agent", "JarvisVIP-Android/1.0")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("No instant summary found for '$topic'.")
                val json = JSONObject(resp.body?.string().orEmpty())
                val extract = json.optString("extract", "").trim()
                if (extract.isBlank()) error("No instant summary found for '$topic'.")
                Result.success(extract)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseRssHeadlines(xml: String): List<NewsHeadline> {
        val list = mutableListOf<NewsHeadline>()
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))
        var event = parser.eventType
        var insideItem = false
        var title = ""
        var link = ""
        var source = "Google News"
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "item" -> {
                            insideItem = true
                            title = ""
                            link = ""
                            source = "News"
                        }
                        "title" -> if (insideItem) title = parser.nextText().trim()
                        "link" -> if (insideItem) link = parser.nextText().trim()
                        "source" -> if (insideItem) source = parser.nextText().trim()
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "item" && insideItem) {
                        if (title.isNotBlank()) {
                            list += NewsHeadline(title = title, source = source, link = link)
                        }
                        insideItem = false
                    }
                }
            }
            event = parser.next()
        }
        return list
    }

    private fun weatherCodeToLabel(code: Int): String = when (code) {
        0 -> "Clear Sky"
        1, 2, 3 -> "Partly Cloudy"
        45, 48 -> "Foggy"
        51, 53, 55, 61, 63, 65 -> "Rainy"
        71, 73, 75 -> "Snow"
        80, 81, 82 -> "Showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "Fair"
    }
}
