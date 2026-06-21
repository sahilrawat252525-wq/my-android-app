package com.example.utils

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.R

object WallpaperHelper {

    fun getDefaultDrawableResForCategory(category: String): Int {
        val upper = category.uppercase()
        if (upper.contains("_")) {
            val weatherPart = upper.substringAfter("_", "HOT")
            return when (weatherPart) {
                "HOT" -> R.drawable.img_weather_sunny
                "COLD" -> R.drawable.img_weather_snowy
                "RAIN" -> R.drawable.img_weather_rainy
                "AUTUMN" -> R.drawable.img_weather_cloudy
                else -> R.drawable.img_weather_sunny
            }
        }
        return when (upper) {
            "SUNNY", "HOT" -> R.drawable.img_weather_sunny
            "RAINY", "RAIN" -> R.drawable.img_weather_rainy
            "SNOWY", "COLD" -> R.drawable.img_weather_snowy
            "CLOUDY", "AUTUMN" -> R.drawable.img_weather_cloudy
            else -> R.drawable.img_weather_sunny
        }
    }

    suspend fun setLockScreenWallpaper(
        context: Context,
        imageUriStr: String?,
        category: String
    ): Boolean {
        return try {
            val wallpaperManager = WallpaperManager.getInstance(context)
            val defaultResId = getDefaultDrawableResForCategory(category)

            val bitmap: Bitmap? = if (!imageUriStr.isNullOrEmpty()) {
                try {
                    // Force grant URI permission if it's a content URI, or try to load via Coil
                    val imageLoader = ImageLoader(context)
                    val request = ImageRequest.Builder(context)
                        .data(Uri.parse(imageUriStr))
                        .allowHardware(false) // Bitmap needs to be software-backed for WallpaperManager
                        .build()
                    val result = imageLoader.execute(request)
                    if (result is SuccessResult) {
                        result.drawable.toBitmap()
                    } else {
                        Log.w("WallpaperHelper", "Failed loading custom URI: $imageUriStr, falling back to default resource")
                        BitmapFactory.decodeResource(context.resources, defaultResId)
                    }
                } catch (ex: Exception) {
                    Log.e("WallpaperHelper", "Error loading custom URI: ${ex.message}", ex)
                    BitmapFactory.decodeResource(context.resources, defaultResId)
                }
            } else {
                BitmapFactory.decodeResource(context.resources, defaultResId)
            }

            if (bitmap != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    // Try to set lock screen only
                    try {
                        wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
                        Log.i("WallpaperHelper", "Successfully set Lock Screen wallpaper directly!")
                    } catch (e: Exception) {
                        Log.e("WallpaperHelper", "Failed to set FLAG_LOCK, setting both lock and system as fallback: ${e.message}")
                        wallpaperManager.setBitmap(bitmap)
                    }
                } else {
                    wallpaperManager.setBitmap(bitmap)
                }
                true
            } else {
                Log.e("WallpaperHelper", "Decompressed bitmap was null")
                false
            }
        } catch (e: Exception) {
            Log.e("WallpaperHelper", "Error modifying wallpaper: ${e.message}", e)
            false
        }
    }
}
