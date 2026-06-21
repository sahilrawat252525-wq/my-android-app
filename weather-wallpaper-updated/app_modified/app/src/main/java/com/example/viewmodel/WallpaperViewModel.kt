package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.*
import com.example.network.GeocodingResult
import com.example.network.LiveWeather
import com.example.network.WeatherService
import com.example.worker.WeatherWallpaperWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class WallpaperViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val dao = db.wallpaperDao()
    private val workManager = WorkManager.getInstance(application)

    // Reactive database streams
    val locationFlow: StateFlow<LocationConfig?> = dao.getLocationFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val wallpapersFlow: StateFlow<List<SavedWallpaper>> = dao.getAllWallpapersFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val libraryImagesFlow: StateFlow<List<WallpaperImage>> = dao.getAllLibraryImagesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val logsFlow: StateFlow<List<WeatherLog>> = dao.getAllLogsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settingsFlow: StateFlow<AppSettings> = dao.getSettingsFlow()
        .map { it ?: AppSettings() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    // Live UI actions states
    private val _searchResults = MutableStateFlow<List<GeocodingResult>>(emptyList())
    val searchResults: StateFlow<List<GeocodingResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _currentWeather = MutableStateFlow<LiveWeather?>(null)
    val currentWeather: StateFlow<LiveWeather?> = _currentWeather.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        // Fetch current forecast and register dynamic work hours sync
        viewModelScope.launch {
            val settings = dao.getSettingsDirect() ?: AppSettings()
            rescheduleBackgroundWallpaperCheck(settings.syncIntervalMinutes)
        }
        
        // Refresh weather state reactive flow is triggered on location set
        viewModelScope.launch {
            locationFlow.collect { location ->
                if (location != null) {
                    refreshLocalWeather(location)
                }
            }
        }
    }

    fun rescheduleBackgroundWallpaperCheck(intervalMinutes: Int) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val checkInterval = intervalMinutes.coerceAtLeast(15)
        val periodicRequest = PeriodicWorkRequestBuilder<WeatherWallpaperWorker>(checkInterval.toLong(), TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            "weather_wallpaper_scheduler",
            ExistingPeriodicWorkPolicy.REPLACE, // REPLACE keeps configuration up-to-date
            periodicRequest
        )
        Log.i("WallpaperViewModel", "Scheduled background checker every $checkInterval minutes.")
    }

    fun searchCity(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _searchResults.value = emptyList()
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isSearching.value = true

            // 1. Latitude/Longitude decimal format parse e.g. "28.63, 77.27"
            val parts = trimmed.split(",")
            if (parts.size == 2) {
                val lat = parts[0].trim().toDoubleOrNull()
                val lon = parts[1].trim().toDoubleOrNull()
                if (lat != null && lon != null) {
                    _searchResults.value = listOf(
                        GeocodingResult(
                            name = "Manual Location ($lat, $lon)",
                            latitude = lat,
                            longitude = lon,
                            country = "Coordinates input"
                        )
                    )
                    _isSearching.value = false
                    return@launch
                }
            }

            // 2. DMS format parse e.g. "28° 37' 50'' N and 77° 16' 39'' E"
            val queryUpper = trimmed.uppercase()
            if (queryUpper.contains("N") || queryUpper.contains("S")) {
                val latPart = Regex("""[0-9].*?[NS]""").find(queryUpper)?.value
                val lonPart = Regex("""[0-9].*?[EW]""").find(queryUpper)?.value
                if (latPart != null && lonPart != null) {
                    val lat = WeatherService.parseDmsToDecimal(latPart)
                    val lon = WeatherService.parseDmsToDecimal(lonPart)
                    if (lat != null && lon != null) {
                        _searchResults.value = listOf(
                            GeocodingResult(
                                name = "Manual location (DMS: $latPart, $lonPart)",
                                latitude = lat,
                                longitude = lon,
                                country = "DMS Coordinates"
                            )
                        )
                        _isSearching.value = false
                        return@launch
                    }
                }
            }

            // 3. Standard Geocoding API lookup
            val results = WeatherService.searchLocation(trimmed)
            _searchResults.value = results
            _isSearching.value = false
        }
    }

    fun selectLocation(city: GeocodingResult) {
        viewModelScope.launch(Dispatchers.IO) {
            val config = LocationConfig(
                id = 1,
                cityName = city.name,
                latitude = city.latitude,
                longitude = city.longitude,
                country = city.country,
                lastChecked = System.currentTimeMillis()
            )
            dao.saveLocation(config)
            _searchResults.value = emptyList()
            
            // Force an immediate wallpaper assessment and display preview
            triggerManualSync()
        }
    }

    fun customizeWallpaper(category: String, uriString: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val wallpaper = SavedWallpaper(
                weatherCategory = category.uppercase(),
                customUri = uriString,
                lastUpdated = System.currentTimeMillis()
            )
            dao.insertWallpaper(wallpaper)
            
            // If the updated category matches active weather, trigger instant sync.
            val current = _currentWeather.value
            if (current != null) {
                val calendar = java.util.Calendar.getInstance()
                val currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
                val activeCombo = WeatherService.getCombinationId(current.temp, current.weatherCode, currentHour)
                if (activeCombo.uppercase() == category.uppercase() || current.category.uppercase() == category.uppercase()) {
                    triggerManualSync()
                }
            }
        }
    }

    fun resetWallpaperToDefault(category: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val wallpaper = SavedWallpaper(
                weatherCategory = category.uppercase(),
                customUri = null,
                lastUpdated = System.currentTimeMillis()
            )
            dao.insertWallpaper(wallpaper)
            
            // If mapping is currently active, sync.
            val current = _currentWeather.value
            if (current != null) {
                val calendar = java.util.Calendar.getInstance()
                val currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
                val activeCombo = WeatherService.getCombinationId(current.temp, current.weatherCode, currentHour)
                if (activeCombo.uppercase() == category.uppercase() || current.category.uppercase() == category.uppercase()) {
                    triggerManualSync()
                }
            }
        }
    }

    // Combination Wallpaper Library Helpers
    fun addLibraryImage(combinationId: String, uriString: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val detail = WallpaperImage(
                combinationId = combinationId.uppercase(),
                uriString = uriString
            )
            dao.insertLibraryImage(detail)
            
            // Sync instantly if combination match is live
            val current = _currentWeather.value
            if (current != null) {
                val calendar = java.util.Calendar.getInstance()
                val currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
                val activeCombo = WeatherService.getCombinationId(current.temp, current.weatherCode, currentHour)
                if (activeCombo.uppercase() == combinationId.uppercase()) {
                    triggerManualSync()
                }
            }
        }
    }

    fun deleteLibraryImage(imageId: Long, combinationId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteLibraryImage(imageId)
            
            // Sync instantly if currently showing this combination
            val current = _currentWeather.value
            if (current != null) {
                val calendar = java.util.Calendar.getInstance()
                val currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
                val activeCombo = WeatherService.getCombinationId(current.temp, current.weatherCode, currentHour)
                if (activeCombo.uppercase() == combinationId.uppercase()) {
                    triggerManualSync()
                }
            }
        }
    }

    fun clearLibraryForCombination(combinationId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteLibraryForCombination(combinationId.uppercase())
            
            // Sync instantly if currently showing this combination
            val current = _currentWeather.value
            if (current != null) {
                val calendar = java.util.Calendar.getInstance()
                val currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
                val activeCombo = WeatherService.getCombinationId(current.temp, current.weatherCode, currentHour)
                if (activeCombo.uppercase() == combinationId.uppercase()) {
                    triggerManualSync()
                }
            }
        }
    }

    fun saveSyncInterval(minutes: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val config = AppSettings(id = 1, syncIntervalMinutes = minutes)
            dao.saveSettings(config)
            rescheduleBackgroundWallpaperCheck(minutes)
        }
    }

    fun triggerManualSync() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            val isOneTimeRequestScheduled = OneTimeWorkRequestBuilder<WeatherWallpaperWorker>().build()
            workManager.enqueue(isOneTimeRequestScheduled)
            
            // Fetch live weather immediately for direct app layout update
            val location = dao.getLocationDirect()
            if (location != null) {
                refreshLocalWeather(location)
            }
            _isRefreshing.value = false
        }
    }

    private suspend fun refreshLocalWeather(location: LocationConfig) {
        val weatherRes = WeatherService.getLiveWeather(location.latitude, location.longitude)
        if (weatherRes != null) {
            _currentWeather.value = weatherRes
        }
    }

    fun clearLogHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.clearLogs()
        }
    }
}
