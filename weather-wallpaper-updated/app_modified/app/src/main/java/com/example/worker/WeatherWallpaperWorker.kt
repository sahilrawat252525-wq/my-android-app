package com.example.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.R
import com.example.data.AppDatabase
import com.example.data.WeatherLog
import com.example.network.WeatherService
import com.example.utils.WallpaperHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WeatherWallpaperWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val db = AppDatabase.getDatabase(context)
        val dao = db.wallpaperDao()

        // 1. Get save location
        val location = dao.getLocationDirect()
        if (location == null) {
            Log.w("WeatherWorker", "No location configured yet. Sparing update.")
            dao.insertLog(
                WeatherLog(
                    id = 0,
                    condition = "Unknown",
                    temperature = 0.0,
                    locationName = "No Location Set",
                    status = "Skipped: Location not set up"
                )
            )
            return Result.success()
        }

        Log.i("WeatherWorker", "Running weather check for ${location.cityName}...")

        // 2. Fetch live weather
        val weather = WeatherService.getLiveWeather(location.latitude, location.longitude)
        if (weather == null) {
            Log.e("WeatherWorker", "Failed to retrieve meteorology from internet.")
            dao.insertLog(
                WeatherLog(
                    id = 0,
                    condition = "Network Error",
                    temperature = 0.0,
                    locationName = location.cityName,
                    status = "Failed: Could not contact weather server"
                )
            )
            return Result.retry()
        }

        // 3. Determine custom combination ID based on current clock and weather attributes
        val calendar = java.util.Calendar.getInstance()
        val currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val combinationId = WeatherService.getCombinationId(weather.temp, weather.weatherCode, currentHour)

        // 4. Find custom wallpaper from library of images for this combination or fallback
        val libraryImages = dao.getLibraryImagesByCombination(combinationId)
        val uriStr = if (libraryImages.isNotEmpty()) {
            val picked = libraryImages.random()
            picked.uriString
        } else {
            // Check flat legacy category as secondary fallback
            val legacyMapping = dao.getWallpaperByCategory(combinationId) ?: dao.getWallpaperByCategory(weather.category)
            legacyMapping?.customUri
        }

        val isCustom = uriStr != null
        val comboLabel = WeatherService.getCombinationLabel(combinationId)
        Log.i("WeatherWorker", "Current combinations: $combinationId ($comboLabel). Weather: ${weather.description}. Custom URI: $uriStr")

        // 5. Update Lock Screen Wallpaper using resolved combination fallback
        val success = WallpaperHelper.setLockScreenWallpaper(
            context = context,
            imageUriStr = uriStr,
            category = combinationId
        )

        // 6. Save update result log with detailed combination context
        val logStatus = if (success) "Success" else "Failed to apply wallpaper bitmap"
        dao.insertLog(
            WeatherLog(
                id = 0,
                condition = "${weather.description} ($comboLabel)",
                temperature = weather.temp,
                locationName = location.cityName,
                status = logStatus
            )
        )

        if (success) {
            sendNotification(
                context,
                "Seemless Wallpaper Match",
                "It's currently ${weather.temp.toInt()}°C (${weather.description}) in ${location.cityName}. Locked screen updated."
            )
        }

        return Result.success()
    }

    private fun sendNotification(context: Context, title: String, message: String) {
        try {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "wallpaper_ambient_updates"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Weather Wallpaper Updates",
                    NotificationManager.IMPORTANCE_LOW
                )
                notificationManager.createNotificationChannel(channel)
            }

            // Using the launcher drawable since we know it exists statically.
            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.img_app_icon_fg)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(1821, notification)
        } catch (e: Exception) {
            Log.e("WeatherWorker", "Notification post failed: ${e.message}", e)
        }
    }
}
