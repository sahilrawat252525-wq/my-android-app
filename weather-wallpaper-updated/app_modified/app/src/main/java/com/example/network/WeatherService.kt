package com.example.network

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

data class GeocodingResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String?
)

data class LiveWeather(
    val temp: Double,
    val weatherCode: Int,
    val isDay: Boolean,
    val category: String, // "SUNNY", "CLOUDY", "RAINY", "SNOWY"
    val description: String
)

object WeatherService {
    private val client = OkHttpClient()

    fun searchLocation(query: String): List<GeocodingResult> {
        val results = mutableListOf<GeocodingResult>()
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://geocoding-api.open-meteo.com/v1/search?name=$encodedQuery&count=5&language=en&format=json"
            
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val bodyString = response.body?.string() ?: return emptyList()
                
                val json = JSONObject(bodyString)
                if (json.has("results")) {
                    val array = json.getJSONArray("results")
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        results.add(
                            GeocodingResult(
                                name = item.getString("name"),
                                latitude = item.getDouble("latitude"),
                                longitude = item.getDouble("longitude"),
                                country = item.optString("country", null)
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("WeatherService", "Error searching location: ${e.message}", e)
        }
        return results
    }

    fun getLiveWeather(latitude: Double, longitude: Double): LiveWeather? {
        try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=weather_code,temperature_2m,is_day&timezone=auto"
            val request = Request.Builder().url(url).build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyString = response.body?.string() ?: return null
                
                val json = JSONObject(bodyString)
                if (json.has("current")) {
                    val current = json.getJSONObject("current")
                    val temp = current.getDouble("temperature_2m")
                    val code = current.getInt("weather_code")
                    val isDay = current.getInt("is_day") == 1
                    
                    val (category, description) = mapWmoCode(code)
                    return LiveWeather(
                        temp = temp,
                        weatherCode = code,
                        isDay = isDay,
                        category = category,
                        description = description
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("WeatherService", "Error fetching weather info: ${e.message}", e)
        }
        return null
    }

    fun parseDmsToDecimal(dmsStr: String): Double? {
        try {
            val clean = dmsStr.trim().uppercase()
            // Matches e.g. 28° 37' 50'' N or 77° 16' 39'' E
            val regex = Regex("""(\d+)\s*[°°º]?\s*(\d+)\s*['\u2019\u2032]?\s*(\d+)?\s*["\u201D\u2033']*\s*([NSEW])""")
            val match = regex.find(clean) ?: return null
            val deg = match.groupValues[1].toDouble()
            val min = match.groupValues[2].toDouble()
            val sec = match.groupValues[3].takeIf { it.isNotEmpty() }?.toDouble() ?: 0.0
            val dir = match.groupValues[4]
            
            var decimal = deg + (min / 60.0) + (sec / 3600.0)
            if (dir == "S" || dir == "W") {
                decimal = -decimal
            }
            return decimal
        } catch (e: Exception) {
            return null
        }
    }

    fun getCombinationId(temp: Double, weatherCode: Int, hourOfDay: Int): String {
        val part = when (hourOfDay) {
            in 5..11 -> "DAWN"      // 05:00 AM to 11:59 AM
            in 12..16 -> "AFTERNOON" // 12:00 PM to 05:00 PM
            in 17..19 -> "EVENING"   // 05:00 PM to 08:00 PM
            else -> "NIGHT"          // 08:00 PM to 05:00 AM
        }
        
        // Match table weather categories: hot, cold, rain, autumn
        val isRainy = weatherCode in listOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99)
        val isCold = weatherCode in listOf(71, 73, 75, 77, 85, 86) || temp < 15.0
        val isHot = temp >= 25.0

        val cond = when {
            isRainy -> "RAIN"
            isCold -> "COLD"
            isHot -> "HOT"
            else -> "AUTUMN"
        }
        return "${part}_${cond}"
    }

    fun getCombinationLabel(combinationId: String): String {
        val parts = combinationId.uppercase().split("_")
        val pLabel = when (parts.getOrNull(0)) {
            "DAWN" -> "Dawn (05am-11:59am)"
            "AFTERNOON" -> "Afternoon (12pm-05pm)"
            "EVENING" -> "Evening (05pm-08pm)"
            "NIGHT" -> "Night (08pm-05am)"
            else -> "General Day"
        }
        val wLabel = when (parts.getOrNull(1)) {
            "HOT" -> "Hot Weather"
            "COLD" -> "Cold/Snow Weather"
            "RAIN" -> "Rainy Weather"
            "AUTUMN" -> "Autumn/Cloudy"
            else -> "Mild Weather"
        }
        return "$pLabel × $wLabel"
    }

    private fun mapWmoCode(code: Int): Pair<String, String> {
        return when (code) {
            0 -> "SUNNY" to "Clear Sky"
            1 -> "SUNNY" to "Mainly Clear"
            2 -> "CLOUDY" to "Partly Cloudy"
            3 -> "CLOUDY" to "Overcast"
            45, 48 -> "CLOUDY" to "Foggy Mist"
            51, 53, 55 -> "RAINY" to "Light Drizzle"
            56, 57 -> "RAINY" to "Freezing Drizzle"
            61, 63 -> "RAINY" to "Light Rain"
            65 -> "RAINY" to "Heavy Rain"
            66, 67 -> "RAINY" to "Freezing Rain"
            80, 81, 82 -> "RAINY" to "Rain Showers"
            95, 96, 99 -> "RAINY" to "Thunderstorm"
            71, 73, 75 -> "SNOWY" to "Snow Fall"
            77 -> "SNOWY" to "Snow Grains"
            85, 86 -> "SNOWY" to "Snow Showers"
            else -> "SUNNY" to "Clear Sky"
        }
    }
}
