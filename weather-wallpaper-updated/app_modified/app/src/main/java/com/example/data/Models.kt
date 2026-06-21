package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wallpapers")
data class SavedWallpaper(
    @PrimaryKey val weatherCategory: String, // Keep legacy categories for fallback/safety
    val customUri: String?,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "wallpaper_library")
data class WallpaperImage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val combinationId: String, // e.g. "DAWN_HOT"
    val uriString: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,
    val syncIntervalMinutes: Int = 180 // Default of 3 hours (180 mins)
)

@Entity(tableName = "location_info")
data class LocationConfig(
    @PrimaryKey val id: Int = 1, // Singleton row
    val cityName: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    val lastChecked: Long = System.currentTimeMillis()
)

@Entity(tableName = "weather_logs")
data class WeatherLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val condition: String, // e.g. "Sunny", "Rainy"
    val temperature: Double,
    val locationName: String,
    val status: String // "Success", "Failed: <reason>"
)

