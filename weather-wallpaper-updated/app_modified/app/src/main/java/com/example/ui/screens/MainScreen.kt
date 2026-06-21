package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.network.GeocodingResult
import com.example.network.WeatherService
import com.example.utils.WallpaperHelper
import com.example.viewmodel.WallpaperViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: WallpaperViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val location by viewModel.locationFlow.collectAsStateWithLifecycle()
    val libraryImages by viewModel.libraryImagesFlow.collectAsStateWithLifecycle()
    val logs by viewModel.logsFlow.collectAsStateWithLifecycle()
    val settings by viewModel.settingsFlow.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val currentWeather by viewModel.currentWeather.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var activeCategoryPicker by remember { mutableStateOf<String?>(null) }
    var activeTab by remember { mutableStateOf("home") }
    
    // For local interactive preview customization on Home tab
    var previewPartOfDay by remember { mutableStateOf<String?>(null) }
    var previewWeather by remember { mutableStateOf<String?>(null) }

    // Part of Day Tab for "Scenes" workspace
    var selectedScenesPart by remember { mutableStateOf("DAWN") }

    // Pulse animation for Active Sync light
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // ── CHANGE 1: Multi-photo picker (PickMultipleVisualMedia) ──────────────────
    val multiPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
        onResult = { uris ->
            val combinationId = activeCategoryPicker
            if (uris.isNotEmpty() && combinationId != null) {
                uris.forEach { uri ->
                    try {
                        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        context.contentResolver.takePersistableUriPermission(uri, flags)
                    } catch (e: Exception) {
                        Log.w("MainScreen", "Failed persistable read URI permission: ${e.message}")
                    }
                    viewModel.addLibraryImage(combinationId, uri.toString())
                }
            }
            activeCategoryPicker = null
        }
    )

    // Compute current real-time combination
    val currentLocalHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val solvedCurrentCombo = remember(currentWeather) {
        val weather = currentWeather
        if (weather != null) {
            WeatherService.getCombinationId(weather.temp, weather.weatherCode, currentLocalHour)
        } else {
            "DAWN_HOT"
        }
    }

    // Determine lock screen clock values for simulator
    val clockTime = remember { SimpleDateFormat("h:mm", Locale.getDefault()).format(Date()) }
    val clockDate = remember { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date()) }

    // ── CHANGE 2: Compute last sync time and next sync time from logs + settings ─
    val lastSuccessLog = remember(logs) { logs.firstOrNull { it.status.lowercase().contains("success") } }
    val lastSyncTimeMs = lastSuccessLog?.timestamp
    val syncIntervalMs = settings.syncIntervalMinutes.toLong() * 60 * 1000L
    val nextSyncTimeMs = if (lastSyncTimeMs != null) lastSyncTimeMs + syncIntervalMs else null
    val nowMs = System.currentTimeMillis()
    val minutesUntilNextSync = if (nextSyncTimeMs != null) {
        ((nextSyncTimeMs - nowMs) / 60_000L)
    } else null
    val nextSyncIsDue = nextSyncTimeMs != null && nowMs >= nextSyncTimeMs

    // Primary Immersive Black Canvas
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF07090E))
    ) {
        // Deep background light gradients for visual atmosphere
        Box(
            modifier = Modifier
                .size(380.dp)
                .align(Alignment.TopStart)
                .offset(y = (-140).dp, x = (-90).dp)
                .blur(110.dp)
                .background(Color(0xFF3B82F6).copy(alpha = 0.15f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.BottomEnd)
                .offset(y = 120.dp, x = 100.dp)
                .blur(100.dp)
                .background(Color(0xFF8B5CF6).copy(alpha = 0.1f), CircleShape)
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val simpleTime = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
                        Text(
                            text = simpleTime,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Wifi, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(13.dp))
                            Icon(Icons.Default.BatteryChargingFull, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(14.dp))
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                text = "Aura Weather",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = (-0.5).sp
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .scale(pulseScale)
                                        .background(Color(0xFF10B981), CircleShape)
                                        .border(BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.6f)), CircleShape)
                                )
                                Text(
                                    text = "Adaptive Lockscreen Sync",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF94A3B8),
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), RoundedCornerShape(20.dp))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFF3B82F6), CircleShape)
                                )
                                Text(
                                    text = location?.cityName ?: "Set Location",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 120.dp)
                                )
                            }
                        }
                    }
                }
            },
            bottomBar = {
                Column {
                    Divider(color = Color.White.copy(alpha = 0.05f), thickness = 1.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .background(Color(0xFF07090E).copy(alpha = 0.95f))
                            .padding(vertical = 12.dp, horizontal = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Nav Tab 1: Home
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { activeTab = "home" }
                                .padding(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (activeTab == "home") Color(0xFF3B82F6).copy(alpha = 0.2f) else Color.Transparent)
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Grid3x3,
                                    contentDescription = "Home",
                                    tint = if (activeTab == "home") Color(0xFF60A5FA) else Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "Home",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activeTab == "home") Color(0xFF60A5FA) else Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        // Nav Tab 2: Wallpaper Libraries
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { activeTab = "scenes" }
                                .padding(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (activeTab == "scenes") Color(0xFF3B82F6).copy(alpha = 0.2f) else Color.Transparent)
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = "Libraries",
                                    tint = if (activeTab == "scenes") Color(0xFF60A5FA) else Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "Wallpaper Libraries",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activeTab == "scenes") Color(0xFF60A5FA) else Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        // Nav Tab 3: Update Logs
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { activeTab = "logs" }
                                .padding(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (activeTab == "logs") Color(0xFF3B82F6).copy(alpha = 0.2f) else Color.Transparent)
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ReceiptLong,
                                    contentDescription = "Sync Logs",
                                    tint = if (activeTab == "logs") Color(0xFF60A5FA) else Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "Match Logs",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activeTab == "logs") Color(0xFF60A5FA) else Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AnimatedContent(
                    targetState = activeTab,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(200, easing = LinearEasing)) togetherWith
                                fadeOut(animationSpec = tween(200, easing = LinearEasing))
                    },
                    label = "main_navigation_crossfade"
                ) { targetState ->
                    when (targetState) {
                        "scenes" -> {
                            // ── CHANGE 1: Multi-photo library tab ────────────────────────────────
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                item {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "16 Lockscreen Combinations",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Each combination has its own photo library. Tap \"Add Photos\" to select multiple images — the system randomly picks one when that combination is active.",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                                    )

                                    val partOfDaySelectorList = listOf(
                                        "DAWN" to "Dawn (05am - 12pm)",
                                        "AFTERNOON" to "Afternoon (12pm - 05pm)",
                                        "EVENING" to "Evening (05pm - 08pm)",
                                        "NIGHT" to "Night (08pm - 05am)"
                                    )

                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    ) {
                                        items(partOfDaySelectorList) { (pKey, pLabel) ->
                                            val isSelected = selectedScenesPart == pKey
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(30.dp))
                                                    .background(if (isSelected) Color(0xFF2563EB) else Color.White.copy(alpha = 0.04f))
                                                    .border(BorderStroke(1.dp, if (isSelected) Color(0xFF3B82F6) else Color.White.copy(alpha = 0.08f)), RoundedCornerShape(30.dp))
                                                    .clickable { selectedScenesPart = pKey }
                                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                                            ) {
                                                Text(
                                                    text = pLabel,
                                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }

                                val weathersInSelectedPart = listOf(
                                    Triple("HOT", "Hot & Clear Weather", "Triggered during bright sunny clear conditions (Temp >= 25°C)"),
                                    Triple("COLD", "Winter Cold & Snow", "Triggered when temperature chills below 15°C or active snow"),
                                    Triple("RAIN", "Rain showers & Drizzle", "Triggered when rain, moisture, drizzle or thunderstorms exist"),
                                    Triple("AUTUMN", "Autumn & Cloudy Breeze", "Triggered during breezy cloudy mild days (Temp 15-25°C & overcast)")
                                )

                                items(weathersInSelectedPart) { (wKey, wLabel, wDesc) ->
                                    val combinationId = "${selectedScenesPart}_$wKey"
                                    val customPhotos = libraryImages.filter { it.combinationId.uppercase() == combinationId.uppercase() }
                                    val isCustomActive = customPhotos.isNotEmpty()
                                    // Highlight if this is the currently active combination
                                    val isLiveCombo = combinationId.uppercase() == solvedCurrentCombo.uppercase()

                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("comb_card_$combinationId"),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isLiveCombo) Color(0xFF1E3A5F).copy(alpha = 0.3f)
                                                            else Color.White.copy(alpha = 0.03f)
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isLiveCombo) Color(0xFF3B82F6).copy(alpha = 0.5f)
                                            else Color.White.copy(alpha = 0.06f)
                                        ),
                                        shape = RoundedCornerShape(24.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            // Live badge
                                            if (isLiveCombo) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(bottom = 8.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .scale(pulseScale)
                                                            .background(Color(0xFF10B981), CircleShape)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "CURRENTLY ACTIVE",
                                                        color = Color(0xFF34D399),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        letterSpacing = 0.8.sp
                                                    )
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(54.dp, 80.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(Color(0xFF131720))
                                                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), RoundedCornerShape(12.dp))
                                                ) {
                                                    if (isCustomActive) {
                                                        AsyncImage(
                                                            model = Uri.parse(customPhotos.first().uriString),
                                                            contentDescription = "Active customization first child",
                                                            modifier = Modifier.fillMaxSize(),
                                                            contentScale = ContentScale.Crop
                                                        )
                                                        // Photo count badge
                                                        if (customPhotos.size > 1) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .align(Alignment.BottomEnd)
                                                                    .padding(2.dp)
                                                                    .clip(RoundedCornerShape(4.dp))
                                                                    .background(Color.Black.copy(alpha = 0.75f))
                                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                                            ) {
                                                                Text(
                                                                    text = "+${customPhotos.size}",
                                                                    color = Color.White,
                                                                    fontSize = 8.sp,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                            }
                                                        }
                                                    } else {
                                                        AsyncImage(
                                                            model = WallpaperHelper.getDefaultDrawableResForCategory(combinationId),
                                                            contentDescription = "Fallback default combo card",
                                                            modifier = Modifier.fillMaxSize(),
                                                            contentScale = ContentScale.Crop
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.width(16.dp))

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = wLabel,
                                                        color = Color.White,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = wDesc,
                                                        color = Color.White.copy(alpha = 0.4f),
                                                        fontSize = 11.sp,
                                                        lineHeight = 14.sp,
                                                        modifier = Modifier.padding(top = 2.dp)
                                                    )
                                                    Text(
                                                        text = "ID: $combinationId  •  ${customPhotos.size} photo${if (customPhotos.size != 1) "s" else ""}",
                                                        color = Color(0xFF60A5FA),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(top = 4.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(14.dp))

                                            // Scrollable photo strip
                                            if (isCustomActive) {
                                                Text(
                                                    text = "Library Photos (${customPhotos.size}) — rotated randomly on sync:",
                                                    color = Color.White.copy(alpha = 0.8f),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(bottom = 6.dp)
                                                )

                                                LazyRow(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(bottom = 12.dp)
                                                ) {
                                                    items(customPhotos) { img ->
                                                        Box(
                                                            modifier = Modifier
                                                                .size(60.dp, 80.dp)
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)), RoundedCornerShape(8.dp))
                                                        ) {
                                                            AsyncImage(
                                                                model = Uri.parse(img.uriString),
                                                                contentDescription = "Thumbnail photo inside library",
                                                                modifier = Modifier.fillMaxSize(),
                                                                contentScale = ContentScale.Crop
                                                            )
                                                            Box(
                                                                modifier = Modifier
                                                                    .align(Alignment.TopEnd)
                                                                    .size(20.dp)
                                                                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(bottomStart = 8.dp))
                                                                    .clickable { viewModel.deleteLibraryImage(img.id, combinationId) },
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Delete,
                                                                    contentDescription = "Remove photo",
                                                                    tint = Color(0xFFF87171),
                                                                    modifier = Modifier.size(11.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                // ── CHANGE 1: "Add Photos" launches multi-picker ──
                                                Button(
                                                    onClick = {
                                                        activeCategoryPicker = combinationId
                                                        multiPhotoPickerLauncher.launch(
                                                            PickVisualMediaRequest(
                                                                ActivityResultContracts.PickVisualMedia.ImageOnly
                                                            )
                                                        )
                                                    },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = Color(0xFF3B82F6).copy(alpha = 0.15f),
                                                        contentColor = Color(0xFF60A5FA)
                                                    ),
                                                    shape = RoundedCornerShape(10.dp),
                                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                                    modifier = Modifier.height(30.dp)
                                                ) {
                                                    Icon(Icons.Default.AddPhotoAlternate, null, modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Add Photos", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }

                                                if (isCustomActive) {
                                                    TextButton(
                                                        onClick = { viewModel.clearLibraryForCombination(combinationId) },
                                                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF5350)),
                                                        modifier = Modifier.height(30.dp),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                    ) {
                                                        Text("Clear All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                item { Spacer(modifier = Modifier.height(32.dp)) }
                            }
                        }

                        "logs" -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                item {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Atmospheric Transition History",
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (logs.isNotEmpty()) {
                                            Text(
                                                text = "Wipe Logs",
                                                color = Color(0xFFF87171),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .clickable { viewModel.clearLogHistory() }
                                                    .padding(vertical = 4.dp, horizontal = 8.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Historic matches and clock logs recorded during periodic background updates.",
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                                    )
                                }

                                if (logs.isEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 64.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(
                                                    imageVector = Icons.Default.Timeline,
                                                    contentDescription = null,
                                                    tint = Color.White.copy(alpha = 0.1f),
                                                    modifier = Modifier.size(52.dp)
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    "No background sync operations matching yet",
                                                    color = Color.White.copy(alpha = 0.35f),
                                                    fontSize = 12.sp,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    items(logs) { log ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(Color.White.copy(alpha = 0.02f))
                                                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), RoundedCornerShape(16.dp))
                                                .padding(14.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            if (log.status.lowercase().contains("success")) Color(0xFF10B981)
                                                            else Color(0xFFEF4444)
                                                        )
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = log.locationName,
                                                            color = Color.White,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        val logTimeStr = remember { SimpleDateFormat("h:mm a, MMM d", Locale.getDefault()).format(Date(log.timestamp)) }
                                                        Text(
                                                            text = logTimeStr,
                                                            color = Color.White.copy(alpha = 0.3f),
                                                            fontSize = 10.sp
                                                        )
                                                    }
                                                    Text(
                                                        text = "Weather Match: ${log.condition} • ${log.temperature.toInt()}°C",
                                                        color = Color.White.copy(alpha = 0.7f),
                                                        fontSize = 12.sp,
                                                        modifier = Modifier.padding(top = 2.dp)
                                                    )
                                                    Text(
                                                        text = "Status: ${log.status}",
                                                        color = if (log.status.lowercase().contains("success")) Color(0xFF34D399) else Color(0xFFF87171),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.padding(top = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                item { Spacer(modifier = Modifier.height(24.dp)) }
                            }
                        }

                        else -> {
                            // Home Tab
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // 1. Phone preview mock
                                item {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(350.dp)
                                            .clip(RoundedCornerShape(32.dp))
                                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(32.dp))
                                            .testTag("smartphone_mock")
                                    ) {
                                        val activeWeatherPartStr = previewWeather ?: (if (currentWeather != null) {
                                            val isRainy = currentWeather!!.weatherCode in listOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99)
                                            val isCold = currentWeather!!.weatherCode in listOf(71, 73, 75, 77, 85, 86) || currentWeather!!.temp < 15.0
                                            when {
                                                isRainy -> "RAIN"
                                                isCold -> "COLD"
                                                currentWeather!!.temp >= 25.0 -> "HOT"
                                                else -> "AUTUMN"
                                            }
                                        } else "HOT")

                                        val activeTimePartStr = previewPartOfDay ?: (when (currentLocalHour) {
                                            in 5..11 -> "DAWN"
                                            in 12..16 -> "AFTERNOON"
                                            in 17..19 -> "EVENING"
                                            else -> "NIGHT"
                                        })

                                        val resolvedComboId = "${activeTimePartStr}_$activeWeatherPartStr"
                                        val combinedLabel = WeatherService.getCombinationLabel(resolvedComboId)
                                        val matchedCustomPhotos = libraryImages.filter { it.combinationId.uppercase() == resolvedComboId.uppercase() }
                                        
                                        if (matchedCustomPhotos.isNotEmpty()) {
                                            AsyncImage(
                                                model = Uri.parse(matchedCustomPhotos.first().uriString),
                                                contentDescription = "Simulated customized lock background",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            AsyncImage(
                                                model = WallpaperHelper.getDefaultDrawableResForCategory(resolvedComboId),
                                                contentDescription = "Simulated default background",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.verticalGradient(
                                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                                                    )
                                                )
                                        )

                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(20.dp),
                                            verticalArrangement = Arrangement.SpaceBetween,
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = clockTime,
                                                    fontSize = 46.sp,
                                                    fontWeight = FontWeight.W200,
                                                    color = Color.White,
                                                    letterSpacing = (-1).sp,
                                                    modifier = Modifier.padding(top = 10.dp)
                                                )
                                                Text(
                                                    text = clockDate,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color.White.copy(alpha = 0.85f)
                                                )
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(20.dp))
                                                    .background(Color.Black.copy(alpha = 0.4f))
                                                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(20.dp))
                                                    .padding(14.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column {
                                                        Text(
                                                            text = if (previewPartOfDay != null || previewWeather != null) "AURA SIMULATION" else "CURRENT ACTIVE AURA",
                                                            color = Color(0xFF60A5FA),
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            letterSpacing = 0.5.sp
                                                        )
                                                        Text(
                                                            text = combinedLabel,
                                                            color = Color.White,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(top = 2.dp),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }

                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(12.dp))
                                                            .background(Color.White.copy(alpha = 0.08f))
                                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                                    ) {
                                                        Text(
                                                            text = if (currentWeather != null && previewWeather == null && previewPartOfDay == null) {
                                                                "${currentWeather!!.temp.toInt()}°C"
                                                            } else "Simulated",
                                                            color = Color.White,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        if (previewPartOfDay != null || previewWeather != null) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(12.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(Color(0xFFDC2626))
                                                    .clickable {
                                                        previewPartOfDay = null
                                                        previewWeather = null
                                                    }
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text("Reset Live View", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                // ── CHANGE 2 & 3: Reference Time + Next Sync Status card ────────────
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.02f)),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                                        shape = RoundedCornerShape(24.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Sync Reference Times",
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Icon(
                                                    Icons.Default.AccessTime,
                                                    contentDescription = null,
                                                    tint = Color(0xFF60A5FA),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(12.dp))

                                            // ── Last sync row ─────────────────────────────────────────
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(Color.White.copy(alpha = 0.03f))
                                                    .padding(12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(28.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                                                    }
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Column {
                                                        Text("Last Successful Sync", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                                                        Text(
                                                            text = if (lastSyncTimeMs != null)
                                                                SimpleDateFormat("h:mm a, MMM d", Locale.getDefault()).format(Date(lastSyncTimeMs))
                                                            else "Never synced yet",
                                                            color = Color.White,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                                if (lastSyncTimeMs != null) {
                                                    val minutesAgo = (nowMs - lastSyncTimeMs) / 60_000L
                                                    Text(
                                                        text = when {
                                                            minutesAgo < 1 -> "just now"
                                                            minutesAgo < 60 -> "${minutesAgo}m ago"
                                                            else -> "${minutesAgo / 60}h ago"
                                                        },
                                                        color = Color.White.copy(alpha = 0.35f),
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            // ── Next sync row ─────────────────────────────────────────
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(
                                                        if (nextSyncIsDue) Color(0xFF7C3AED).copy(alpha = 0.12f)
                                                        else Color.White.copy(alpha = 0.03f)
                                                    )
                                                    .border(
                                                        BorderStroke(1.dp, if (nextSyncIsDue) Color(0xFF8B5CF6).copy(alpha = 0.4f) else Color.Transparent),
                                                        RoundedCornerShape(12.dp)
                                                    )
                                                    .padding(12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(28.dp)
                                                            .clip(CircleShape)
                                                            .background(
                                                                if (nextSyncIsDue) Color(0xFF8B5CF6).copy(alpha = 0.25f)
                                                                else Color(0xFF3B82F6).copy(alpha = 0.15f)
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            if (nextSyncIsDue) Icons.Default.NotificationsActive else Icons.Default.Schedule,
                                                            null,
                                                            tint = if (nextSyncIsDue) Color(0xFFA78BFA) else Color(0xFF60A5FA),
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Column {
                                                        Text("Next Scheduled Sync", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                                                        Text(
                                                            text = when {
                                                                nextSyncTimeMs == null -> "Sync once to start schedule"
                                                                nextSyncIsDue -> "Due now — running soon"
                                                                else -> SimpleDateFormat("h:mm a, MMM d", Locale.getDefault()).format(Date(nextSyncTimeMs))
                                                            },
                                                            color = if (nextSyncIsDue) Color(0xFFA78BFA) else Color.White,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                                if (minutesUntilNextSync != null && !nextSyncIsDue) {
                                                    Text(
                                                        text = "in ${minutesUntilNextSync}m",
                                                        color = Color(0xFF60A5FA),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(12.dp))
                                            Divider(color = Color.White.copy(alpha = 0.05f))
                                            Spacer(modifier = Modifier.height(12.dp))

                                            // ── Time band reference selector ──────────────────────────
                                            Text(
                                                text = "Time Band Reference — tap to preview:",
                                                color = Color.White.copy(alpha = 0.5f),
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(bottom = 8.dp)
                                            )

                                            val eraConfigs = listOf(
                                                Triple("DAWN", "05:00 AM – 11:59 AM", "Dawn Era"),
                                                Triple("AFTERNOON", "12:00 PM – 04:59 PM", "Afternoon Era"),
                                                Triple("EVENING", "05:00 PM – 07:59 PM", "Evening Era"),
                                                Triple("NIGHT", "08:00 PM – 04:59 AM", "Night Era")
                                            )

                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                val solvedCurrentTimePart = when (currentLocalHour) {
                                                    in 5..11 -> "DAWN"
                                                    in 12..16 -> "AFTERNOON"
                                                    in 17..19 -> "EVENING"
                                                    else -> "NIGHT"
                                                }

                                                eraConfigs.forEach { (eraKey, eraLimits, eraTitle) ->
                                                    val isSystemLive = solvedCurrentTimePart == eraKey
                                                    val isSimulatedActive = previewPartOfDay == eraKey

                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(12.dp))
                                                            .background(
                                                                if (isSimulatedActive) Color(0xFF2563EB).copy(alpha = 0.15f)
                                                                else if (isSystemLive) Color.White.copy(alpha = 0.03f)
                                                                else Color.Transparent
                                                            )
                                                            .border(
                                                                BorderStroke(
                                                                    1.dp,
                                                                    if (isSimulatedActive) Color(0xFF3B82F6)
                                                                    else if (isSystemLive) Color.White.copy(alpha = 0.1f)
                                                                    else Color.Transparent
                                                                ),
                                                                RoundedCornerShape(12.dp)
                                                            )
                                                            .clickable { previewPartOfDay = eraKey }
                                                            .padding(12.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Column {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Text(eraTitle, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                                if (isSystemLive) {
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .padding(start = 8.dp)
                                                                            .clip(RoundedCornerShape(6.dp))
                                                                            .background(Color(0xFF10B981).copy(alpha = 0.15f))
                                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                                    ) {
                                                                        Text("Current", color = Color(0xFF34D399), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                                    }
                                                                }
                                                            }
                                                            Text(eraLimits, color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
                                                        }

                                                        RadioButton(
                                                            selected = isSimulatedActive || (previewPartOfDay == null && isSystemLive),
                                                            onClick = { previewPartOfDay = eraKey },
                                                            colors = RadioButtonDefaults.colors(
                                                                selectedColor = Color(0xFF60A5FA),
                                                                unselectedColor = Color.White.copy(alpha = 0.2f)
                                                            ),
                                                            modifier = Modifier.scale(0.85f)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // ── CHANGE 2: Sync Frequency Settings card ────────────────────────
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.02f)),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                                        shape = RoundedCornerShape(24.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(
                                                        text = "Aura Sync Frequency",
                                                        color = Color.White,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "How often to check weather & change wallpaper",
                                                        color = Color.White.copy(alpha = 0.4f),
                                                        fontSize = 11.sp
                                                    )
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFF3B82F6).copy(alpha = 0.12f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Default.Timer, null, tint = Color(0xFF60A5FA), modifier = Modifier.size(13.dp))
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(14.dp))

                                            val syncTimersMinutesList = listOf(
                                                15 to "15m",
                                                30 to "30m",
                                                60 to "1h",
                                                180 to "3h",
                                                360 to "6h"
                                            )

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                syncTimersMinutesList.forEach { (minsVal, labelText) ->
                                                    val isSelected = settings.syncIntervalMinutes == minsVal
                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .clip(RoundedCornerShape(12.dp))
                                                            .background(if (isSelected) Color(0xFF3B82F6).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.03f))
                                                            .border(
                                                                BorderStroke(1.dp, if (isSelected) Color(0xFF3B82F6) else Color.White.copy(alpha = 0.06f)),
                                                                RoundedCornerShape(12.dp)
                                                            )
                                                            .clickable { viewModel.saveSyncInterval(minsVal) }
                                                            .padding(vertical = 10.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = labelText,
                                                            color = if (isSelected) Color(0xFF60A5FA) else Color.White.copy(alpha = 0.7f),
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(10.dp))
                                            Text(
                                                text = "Background sync runs every ${settings.syncIntervalMinutes} minutes. Minimum is 15 minutes (Android system limit).",
                                                color = Color.White.copy(alpha = 0.35f),
                                                fontSize = 10.sp,
                                                lineHeight = 13.sp
                                            )
                                        }
                                    }
                                }

                                // 4. Location Target card
                                item {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(24.dp))
                                            .background(Color.White.copy(alpha = 0.03f))
                                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), RoundedCornerShape(24.dp))
                                            .padding(16.dp)
                                    ) {
                                        Text(
                                            text = "Atmospheric Location Target",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Input a city name or latitude/longitude coordinates (Decimal or DMS formats) to target.",
                                            color = Color.White.copy(alpha = 0.4f),
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(bottom = 12.dp)
                                        )

                                        if (location != null) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(Color.White.copy(alpha = 0.04f))
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = location?.cityName ?: "Standard Station",
                                                        color = Color.White,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "Lat: ${"%.4f".format(location?.latitude)}°  Lon: ${"%.4f".format(location?.longitude)}°",
                                                        color = Color.White.copy(alpha = 0.4f),
                                                        fontSize = 11.sp
                                                    )
                                                }

                                                Button(
                                                    onClick = { viewModel.triggerManualSync() },
                                                    enabled = !isRefreshing,
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = Color(0xFF3B82F6).copy(alpha = 0.15f),
                                                        contentColor = Color(0xFF60A5FA)
                                                    ),
                                                    shape = RoundedCornerShape(10.dp),
                                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                                    modifier = Modifier.height(30.dp)
                                                ) {
                                                    if (isRefreshing) {
                                                        CircularProgressIndicator(modifier = Modifier.size(12.dp), color = Color(0xFF60A5FA), strokeWidth = 1.8.dp)
                                                    } else {
                                                        Text("Sync Now", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(10.dp))
                                        }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(Color.White.copy(alpha = 0.05f))
                                                .padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Search,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.3f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            TextField(
                                                value = searchQuery,
                                                onValueChange = {
                                                    searchQuery = it
                                                    viewModel.searchCity(it)
                                                },
                                                placeholder = { Text("Search location or coords (lat, lon)", color = Color.White.copy(alpha = 0.3f), fontSize = 13.sp) },
                                                colors = TextFieldDefaults.colors(
                                                    focusedContainerColor = Color.Transparent,
                                                    unfocusedContainerColor = Color.Transparent,
                                                    disabledContainerColor = Color.Transparent,
                                                    focusedIndicatorColor = Color.Transparent,
                                                    unfocusedIndicatorColor = Color.Transparent,
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.White
                                                ),
                                                singleLine = true,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("city_search_input")
                                            )
                                            if (searchQuery.isNotEmpty()) {
                                                IconButton(onClick = {
                                                    searchQuery = ""
                                                    viewModel.searchCity("")
                                                }) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Clear",
                                                        tint = Color.White.copy(alpha = 0.5f),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }

                                        AnimatedVisibility(
                                            visible = searchResults.isNotEmpty(),
                                            enter = fadeIn() + expandVertically(),
                                            exit = fadeOut() + shrinkVertically()
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 8.dp)
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(Color(0xFF0F1722))
                                                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(14.dp))
                                                    .padding(4.dp)
                                            ) {
                                                searchResults.forEach { result ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                viewModel.selectLocation(result)
                                                                searchQuery = ""
                                                            }
                                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.LocationOn,
                                                            contentDescription = null,
                                                            tint = Color(0xFF60A5FA),
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Column {
                                                            Text(result.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                            Text(result.country ?: "Coordinates mapped", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                item { Spacer(modifier = Modifier.height(24.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }
}


