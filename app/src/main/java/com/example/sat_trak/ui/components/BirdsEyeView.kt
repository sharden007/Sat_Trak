package com.example.sat_trak.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.sat_trak.data.models.SatelliteData
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.*

@Composable
fun BirdsEyeViewDialog(
    satellite: SatelliteData,
    onDismiss: () -> Unit,
    userLatitude: Double = 0.0,
    userLongitude: Double = 0.0
) {
    var zoomPhase by remember { mutableStateOf(0) } // 0=space, 1=zooming, 2=ground
    var scanLineProgress by remember { mutableStateOf(0f) }
    var coverageRadius by remember { mutableStateOf(0f) }
    var populationCount by remember { mutableStateOf(0L) }

    // Calculate if user is in satellite's view
    val userInView = remember(satellite.latitude, satellite.longitude, userLatitude, userLongitude) {
        if (userLatitude != 0.0 || userLongitude != 0.0) {
            val distance = calculateDistanceBetween(
                satellite.latitude, satellite.longitude,
                userLatitude, userLongitude
            )
            distance < getSatelliteViewRadius(satellite.altitude)
        } else false
    }

    // Calculate population in satellite's field of view
    LaunchedEffect(satellite.latitude, satellite.longitude, satellite.altitude) {
        populationCount = calculatePopulationInView(
            satellite.latitude,
            satellite.longitude,
            satellite.altitude
        )
    }

    // Animate zoom phases
    LaunchedEffect(Unit) {
        delay(500)
        zoomPhase = 1 // Start zooming
        delay(2000)
        zoomPhase = 2 // Show ground view

        // Animate scan line
        while (true) {
            for (i in 0..100) {
                scanLineProgress = i / 100f
                delay(30)
            }
            delay(500)
        }
    }

    // Animate coverage circle expansion
    LaunchedEffect(zoomPhase) {
        if (zoomPhase == 2) {
            while (true) {
                for (i in 0..100) {
                    coverageRadius = i / 100f
                    delay(20)
                }
                delay(1000)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Main content
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with satellite info
                BirdsEyeHeader(
                    satellite = satellite,
                    onDismiss = onDismiss,
                    userInView = userInView
                )

                // Main visualization
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    when (zoomPhase) {
                        0 -> SpaceView()
                        1 -> ZoomingView()
                        2 -> GroundView(
                            satellite = satellite,
                            scanProgress = scanLineProgress,
                            userLatitude = userLatitude,
                            userLongitude = userLongitude,
                            userInView = userInView,
                            coverageRadius = coverageRadius
                        )
                    }
                }

                // Footer with controls and info
                BirdsEyeFooter(
                    satellite = satellite,
                    zoomPhase = zoomPhase,
                    populationCount = populationCount,
                    userInView = userInView
                )
            }
        }
    }
}

@Composable
fun BirdsEyeHeader(
    satellite: SatelliteData,
    onDismiss: () -> Unit,
    userInView: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF0A0E27).copy(alpha = 0.95f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "🛰️ BIRD'S EYE VIEW",
                        color = Color(0xFF00FF41),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    if (userInView) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "📍 YOU ARE VISIBLE",
                            color = Color(0xFFFFD700),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(
                                    Color(0xFFFFD700).copy(alpha = 0.2f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = satellite.name,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "NORAD ${satellite.id}",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.Red.copy(alpha = 0.2f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun SpaceView() {
    val infiniteTransition = rememberInfiniteTransition(label = "space")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Animated Earth from space
        Canvas(modifier = Modifier.size(300.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2

            // Draw Earth
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1E3A8A),
                        Color(0xFF0F172A)
                    ),
                    center = center
                ),
                radius = radius,
                center = center
            )

            // Draw atmosphere glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF3B82F6).copy(alpha = 0.3f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius * 1.2f
                ),
                radius = radius * 1.2f,
                center = center
            )

            // Draw continents (simplified)
            rotate(rotation, center) {
                drawCircle(
                    color = Color(0xFF22C55E).copy(alpha = 0.6f),
                    radius = radius * 0.3f,
                    center = Offset(center.x + radius * 0.4f, center.y)
                )
                drawCircle(
                    color = Color(0xFF22C55E).copy(alpha = 0.6f),
                    radius = radius * 0.25f,
                    center = Offset(center.x - radius * 0.5f, center.y - radius * 0.3f)
                )
            }
        }

        Text(
            text = "ACQUIRING TARGET...",
            color = Color(0xFF00FF41),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
        )
    }
}

@Composable
fun ZoomingView() {
    val scale by rememberInfiniteTransition(label = "zoom").animateFloat(
        initialValue = 1f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(400.dp)
                .scale(scale)
                .blur(2.dp)
        ) {
            val center = Offset(size.width / 2, size.height / 2)

            // Draw zooming Earth surface
            drawCircle(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1E40AF),
                        Color(0xFF22C55E),
                        Color(0xFF15803D)
                    )
                ),
                center = center,
                radius = size.minDimension / 2
            )
        }

        // Targeting reticle
        Canvas(modifier = Modifier.size(200.dp)) {
            val center = Offset(size.width / 2, size.height / 2)

            // Crosshair
            drawLine(
                color = Color(0xFF00FF41),
                start = Offset(center.x - 100, center.y),
                end = Offset(center.x + 100, center.y),
                strokeWidth = 2f
            )
            drawLine(
                color = Color(0xFF00FF41),
                start = Offset(center.x, center.y - 100),
                end = Offset(center.x, center.y + 100),
                strokeWidth = 2f
            )

            // Circle reticle
            drawCircle(
                color = Color(0xFF00FF41),
                center = center,
                radius = 80f,
                style = Stroke(width = 2f)
            )
            drawCircle(
                color = Color(0xFF00FF41),
                center = center,
                radius = 60f,
                style = Stroke(width = 2f)
            )
        }

        Text(
            text = "ZOOMING IN...",
            color = Color(0xFF00FF41),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
        )
    }
}

@Composable
fun GroundView(
    satellite: SatelliteData,
    scanProgress: Float,
    userLatitude: Double,
    userLongitude: Double,
    userInView: Boolean,
    coverageRadius: Float
) {
    val shimmer by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    val pulseScale by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Simulated ground view
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerX = width / 2
            val centerY = height / 2

            // Draw terrain (simplified)
            // Ocean
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0C4A6E),
                        Color(0xFF075985)
                    )
                ),
                size = size
            )

            // Land masses (based on satellite position)
            val isOverLand = (satellite.latitude > -60 && satellite.latitude < 70) &&
                    ((satellite.longitude > -30 && satellite.longitude < 60) ||
                            (satellite.longitude > -130 && satellite.longitude < -60))

            if (isOverLand) {
                // Draw land
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF16A34A),
                            Color(0xFF15803D),
                            Color(0xFF14532D)
                        )
                    ),
                    center = Offset(centerX, centerY * 1.2f),
                    radius = width * 0.4f
                )

                // Cities (dots)
                for (i in 1..5) {
                    val angle = (i * 72f) + shimmer * 360f
                    val cityX = centerX + cos(Math.toRadians(angle.toDouble())).toFloat() * (width * 0.2f)
                    val cityY = centerY * 1.2f + sin(Math.toRadians(angle.toDouble())).toFloat() * (width * 0.2f)

                    drawCircle(
                        color = Color(0xFFFBBF24).copy(alpha = 0.6f + shimmer * 0.4f),
                        center = Offset(cityX, cityY),
                        radius = 8f
                    )
                }
            }

            // Draw COVERAGE CIRCLE - Animated expanding ring showing satellite's field of view
            val maxCoverageRadius = width * 0.45f
            val currentRadius = maxCoverageRadius * coverageRadius

            // Multiple expanding rings for dramatic effect
            for (i in 0..2) {
                val ringRadius = currentRadius - (i * 50f)
                if (ringRadius > 0) {
                    drawCircle(
                        color = Color(0xFF00FF41).copy(alpha = 0.3f - (i * 0.1f)),
                        center = Offset(centerX, centerY),
                        radius = ringRadius,
                        style = Stroke(width = 3f)
                    )
                }
            }

            // Filled coverage area with gradient
            if (coverageRadius > 0.3f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF00FF41).copy(alpha = 0.15f),
                            Color(0xFF00FF41).copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        center = Offset(centerX, centerY),
                        radius = currentRadius
                    ),
                    center = Offset(centerX, centerY),
                    radius = currentRadius
                )
            }

            // Draw USER LOCATION PIN if in view
            if (userInView && (userLatitude != 0.0 || userLongitude != 0.0)) {
                // Calculate user position relative to satellite
                val userPosX = centerX + ((userLongitude - satellite.longitude) * 10).toFloat()
                val userPosY = centerY - ((userLatitude - satellite.latitude) * 10).toFloat()

                // Pulsing outer circle
                drawCircle(
                    color = Color(0xFFFFD700).copy(alpha = 0.4f),
                    center = Offset(userPosX, userPosY),
                    radius = 30f * pulseScale
                )

                // Middle ring
                drawCircle(
                    color = Color(0xFFFFD700).copy(alpha = 0.6f),
                    center = Offset(userPosX, userPosY),
                    radius = 20f,
                    style = Stroke(width = 3f)
                )

                // Inner solid dot
                drawCircle(
                    color = Color(0xFFFFD700),
                    center = Offset(userPosX, userPosY),
                    radius = 12f
                )

                // Crosshair on user location
                val crossSize = 25f
                drawLine(
                    color = Color(0xFFFFD700),
                    start = Offset(userPosX - crossSize, userPosY),
                    end = Offset(userPosX + crossSize, userPosY),
                    strokeWidth = 2f
                )
                drawLine(
                    color = Color(0xFFFFD700),
                    start = Offset(userPosX, userPosY - crossSize),
                    end = Offset(userPosX, userPosY + crossSize),
                    strokeWidth = 2f
                )
            }

            // Scan line effect
            val scanY = height * scanProgress
            drawLine(
                color = Color(0xFF00FF41).copy(alpha = 0.7f),
                start = Offset(0f, scanY),
                end = Offset(width, scanY),
                strokeWidth = 3f
            )

            // Grid overlay
            for (i in 0..10) {
                val x = width * i / 10
                drawLine(
                    color = Color(0xFF00FF41).copy(alpha = 0.1f),
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = 1f
                )
            }
            for (i in 0..10) {
                val y = height * i / 10
                drawLine(
                    color = Color(0xFF00FF41).copy(alpha = 0.1f),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1f
                )
            }
        }

        // "YOU ARE HERE" label
        if (userInView && (userLatitude != 0.0 || userLongitude != 0.0)) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 60.dp)
                    .background(
                        Color(0xFFFFD700).copy(alpha = 0.9f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "📍 YOU ARE HERE",
                    color = Color.Black,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Satellite can see you!",
                    color = Color.Black,
                    fontSize = 10.sp
                )
            }
        }

        // Info overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "SURVEILLANCE ACTIVE",
                color = Color(0xFF00FF41),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = String.format(
                    Locale.US,
                    "LAT: %.2f° LON: %.2f°",
                    satellite.latitude,
                    satellite.longitude
                ),
                color = Color.White,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun BirdsEyeFooter(
    satellite: SatelliteData,
    zoomPhase: Int,
    populationCount: Long,
    userInView: Boolean
) {
    val animatedPopulation by animateLongAsState(
        targetValue = populationCount,
        animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
        label = "population"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF0A0E27).copy(alpha = 0.95f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Population Counter - PROMINENT DISPLAY
            if (zoomPhase >= 2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF00FF41).copy(alpha = 0.2f),
                                    Color(0xFF00FF41).copy(alpha = 0.1f),
                                    Color(0xFF00FF41).copy(alpha = 0.2f)
                                )
                            ),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "👥 POPULATION IN VIEW",
                            color = Color(0xFF00FF41),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatPopulation(animatedPopulation),
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Based on satellite field of view",
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                InfoChip("ALT", "${String.format(Locale.US, "%.0f", satellite.altitude)} km")
                InfoChip("LAT", String.format(Locale.US, "%.2f°", satellite.latitude))
                InfoChip("LON", String.format(Locale.US, "%.2f°", satellite.longitude))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Status bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusDot(active = zoomPhase >= 0)
                Spacer(modifier = Modifier.width(8.dp))
                StatusDot(active = zoomPhase >= 1)
                Spacer(modifier = Modifier.width(8.dp))
                StatusDot(active = zoomPhase >= 2)

                if (userInView) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "📍 VISIBLE",
                        color = Color(0xFFFFD700),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun InfoChip(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(
                Color(0xFF1E293B).copy(alpha = 0.8f),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = Color(0xFF00FF41),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun StatusDot(active: Boolean) {
    Box(
        modifier = Modifier
            .size(12.dp)
            .background(
                if (active) Color(0xFF00FF41) else Color.Gray,
                CircleShape
            )
    )
}

// Helper function to calculate distance between two points (Haversine formula)
fun calculateDistanceBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadius = 6371.0 // km
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadius * c
}

// Calculate satellite's visible radius based on altitude
fun getSatelliteViewRadius(altitude: Double): Double {
    val earthRadius = 6371.0 // km
    val satelliteHeight = altitude
    // Calculate horizon distance
    return sqrt(2 * earthRadius * satelliteHeight + satelliteHeight * satelliteHeight)
}

// Calculate approximate population in satellite's field of view
fun calculatePopulationInView(
    satelliteLat: Double,
    satelliteLon: Double,
    altitude: Double
): Long {
    // Approximate population density map (people per sq km)
    val viewRadius = getSatelliteViewRadius(altitude)
    val viewArea = PI * viewRadius * viewRadius // sq km

    // Simple population density estimation based on latitude
    val avgDensity = when {
        // Populated regions (mid-latitudes)
        abs(satelliteLat) in 20.0..60.0 -> {
            // Check if over land (simplified - Eastern hemisphere more populated)
            if (satelliteLon in -130.0..60.0) {
                when {
                    // Europe/Asia - densely populated
                    satelliteLon > 0 && abs(satelliteLat) < 50 -> 100.0
                    // North America - moderately populated
                    satelliteLon < 0 && satelliteLat > 25 -> 40.0
                    else -> 30.0
                }
            } else 5.0 // Ocean
        }
        // Equatorial regions
        abs(satelliteLat) < 20.0 -> {
            if (satelliteLon in -80.0..150.0) 80.0 // Populated tropical regions
            else 3.0 // Ocean
        }
        // Polar regions
        abs(satelliteLat) > 60.0 -> 1.0
        else -> 10.0
    }

    // Calculate population with some randomness for realism
    val basePopulation = (viewArea * avgDensity).toLong()
    val variation = (basePopulation * 0.15).toLong() // ±15% variation
    return maxOf(0, basePopulation + (Math.random() * variation * 2 - variation).toLong())
}

// Format population number with millions/billions
fun formatPopulation(population: Long): String {
    return when {
        population >= 1_000_000_000 -> String.format(Locale.US, "%.2f Billion", population / 1_000_000_000.0)
        population >= 1_000_000 -> String.format(Locale.US, "%.2f Million", population / 1_000_000.0)
        population >= 1_000 -> String.format(Locale.US, "%,d Thousand", population / 1000)
        else -> String.format(Locale.US, "%,d", population)
    }
}

// Animated Long state
@Composable
fun animateLongAsState(
    targetValue: Long,
    animationSpec: AnimationSpec<Float> = spring(),
    label: String = "LongAnimation"
): State<Long> {
    val animatedFloat by animateFloatAsState(
        targetValue = targetValue.toFloat(),
        animationSpec = animationSpec,
        label = label
    )
    return remember { derivedStateOf { animatedFloat.toLong() } }
}
