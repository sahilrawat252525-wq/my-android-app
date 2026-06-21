package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WallpaperDao {
    // SavedWallpaper queries (legacy fallback)
    @Query("SELECT * FROM wallpapers")
    fun getAllWallpapersFlow(): Flow<List<SavedWallpaper>>

    @Query("SELECT * FROM wallpapers WHERE weatherCategory = :category")
    suspend fun getWallpaperByCategory(category: String): SavedWallpaper?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWallpaper(wallpaper: SavedWallpaper)

    // WallpaperLibrary (multiple custom images per combinationId)
    @Query("SELECT * FROM wallpaper_library")
    fun getAllLibraryImagesFlow(): Flow<List<WallpaperImage>>

    @Query("SELECT * FROM wallpaper_library WHERE combinationId = :combinationId")
    suspend fun getLibraryImagesByCombination(combinationId: String): List<WallpaperImage>

    @Query("SELECT * FROM wallpaper_library WHERE combinationId = :combinationId")
    fun getLibraryImagesByCombinationFlow(combinationId: String): Flow<List<WallpaperImage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLibraryImage(image: WallpaperImage)

    @Query("DELETE FROM wallpaper_library WHERE id = :imageId")
    suspend fun deleteLibraryImage(imageId: Long)

    @Query("DELETE FROM wallpaper_library WHERE combinationId = :combinationId")
    suspend fun deleteLibraryForCombination(combinationId: String)

    // AppSettings
    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun getSettingsFlow(): Flow<AppSettings?>

    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun getSettingsDirect(): AppSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: AppSettings)

    // LocationConfig queries
    @Query("SELECT * FROM location_info WHERE id = 1")
    fun getLocationFlow(): Flow<LocationConfig?>

    @Query("SELECT * FROM location_info WHERE id = 1")
    suspend fun getLocationDirect(): LocationConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLocation(location: LocationConfig)

    // WeatherLog queries
    @Query("SELECT * FROM weather_logs ORDER BY timestamp DESC LIMIT 30")
    fun getAllLogsFlow(): Flow<List<WeatherLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: WeatherLog)

    @Query("DELETE FROM weather_logs")
    suspend fun clearLogs()
}
