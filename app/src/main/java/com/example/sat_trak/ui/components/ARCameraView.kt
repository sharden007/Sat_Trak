package com.example.sat_trak.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.sat_trak.data.models.SatelliteData
import com.example.sat_trak.utils.SatelliteColorUtils
import kotlin.math.*

@Composable
fun ARCameraView(
    satellites: List<SatelliteData>,
    userAzimuth: Float,
    userElevation: Float,
    userLatitude: Double,
    userLongitude: Double,
    modifier: Modifier = Modifier,
    onSatelliteClick: (SatelliteData) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = modifier) {
        if (hasCameraPermission) {
            // Camera Preview
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview
                            )
                        } catch (e: Exception) {
                            // Handle error
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // AR Overlay with satellite markers
            AROverlay(
                satellites = satellites,
                userAzimuth = userAzimuth,
                userElevation = userElevation,
                userLatitude = userLatitude,
                userLongitude = userLongitude,
                onSatelliteClick = onSatelliteClick
            )

        } else {
            // Permission not granted UI
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "📷 Camera Permission Required",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                        Text("Grant Camera Permission")
                    }
                }
            }
        }
    }
}

@Composable
fun AROverlay(
    satellites: List<SatelliteData>,
    userAzimuth: Float,
    userElevation: Float,
    userLatitude: Double,
    userLongitude: Double,
    onSatelliteClick: (SatelliteData) -> Unit
) {
    // Calculate all satellite positions (both in-view and off-screen)
    val allSatellitePositions = remember(satellites, userAzimuth, userElevation) {
        satellites.mapNotNull { satellite ->
            val azimuth = calculateAzimuth(userLatitude, userLongitude, satellite.latitude, satellite.longitude)
            val elevation = calculateElevation(userLatitude, userLongitude, satellite.latitude, satellite.longitude, satellite.altitude)

            // Only include satellites above horizon
            if (elevation < 0) return@mapNotNull null

            val relativeAzimuth = normalizeAngle(azimuth - userAzimuth)
            val relativeElevation = elevation - userElevation

            satellite to Triple(relativeAzimuth, relativeElevation, azimuth)
        }
    }

    // Separate in-view and off-screen satellites
    val horizontalFOV = 90f
    val verticalFOV = 60f

    val inViewSatellites = allSatellitePositions.filter { (_, pos) ->
        val (relAz, relEl, _) = pos
        abs(relAz) <= horizontalFOV / 2 && abs(relEl) <= verticalFOV / 2
    }

    val offScreenSatellites = allSatellitePositions.filter { (_, pos) ->
        val (relAz, relEl, _) = pos
        abs(relAz) > horizontalFOV / 2 || abs(relEl) > verticalFOV / 2
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Canvas for drawing satellite markers and off-screen indicators
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Draw in-view satellites
            inViewSatellites.forEach { (satellite, pos) ->
                val (relAz, relEl, _) = pos
                val x = width * (0.5f + (relAz / horizontalFOV))
                val y = height * (0.5f - (relEl / verticalFOV))

                val color = SatelliteColorUtils.getColorForSatellite(satellite.id)

                // Draw outer circle
                drawCircle(
                    color = color,
                    radius = 30f,
                    center = Offset(x, y),
                    style = Stroke(width = 3f)
                )

                // Draw inner filled circle
                drawCircle(
                    color = color.copy(alpha = 0.5f),
                    radius = 15f,
                    center = Offset(x, y)
                )

                // Draw cross-hair
                drawLine(
                    color = color,
                    start = Offset(x - 40f, y),
                    end = Offset(x + 40f, y),
                    strokeWidth = 2f
                )
                drawLine(
                    color = color,
                    start = Offset(x, y - 40f),
                    end = Offset(x, y + 40f),
                    strokeWidth = 2f
                )
            }

            // Draw off-screen satellite indicators with arrows
            offScreenSatellites.forEach { (satellite, pos) ->
                val (relAz, relEl, absoluteAzimuth) = pos
                val color = SatelliteColorUtils.getColorForSatellite(satellite.id)

                // Determine edge position for indicator
                val edgeMargin = 60f
                val indicatorX: Float
                val indicatorY: Float
                val arrowRotation: Float

                // Clamp to screen edges
                val clampedX = (0.5f + (relAz / horizontalFOV)).coerceIn(0f, 1f)
                val clampedY = (0.5f - (relEl / verticalFOV)).coerceIn(0f, 1f)

                // Determine which edge and position
                when {
                    // Left edge
                    relAz < -horizontalFOV / 2 -> {
                        indicatorX = edgeMargin
                        indicatorY = (height * clampedY).coerceIn(edgeMargin, height - edgeMargin)
                        arrowRotation = 180f // Arrow pointing left
                    }
                    // Right edge
                    relAz > horizontalFOV / 2 -> {
                        indicatorX = width - edgeMargin
                        indicatorY = (height * clampedY).coerceIn(edgeMargin, height - edgeMargin)
                        arrowRotation = 0f // Arrow pointing right
                    }
                    // Top edge
                    relEl > verticalFOV / 2 -> {
                        indicatorX = (width * clampedX).coerceIn(edgeMargin, width - edgeMargin)
                        indicatorY = edgeMargin
                        arrowRotation = -90f // Arrow pointing up
                    }
                    // Bottom edge
                    else -> {
                        indicatorX = (width * clampedX).coerceIn(edgeMargin, width - edgeMargin)
                        indicatorY = height - edgeMargin
                        arrowRotation = 90f // Arrow pointing down
                    }
                }

                // Draw arrow indicator
                val arrowPath = Path().apply {
                    // Arrow pointing right (will be rotated)
                    moveTo(0f, 0f)
                    lineTo(-20f, -15f)
                    lineTo(-20f, 15f)
                    close()
                }

                // Transform and draw arrow
                val radians = Math.toRadians(arrowRotation.toDouble()).toFloat()
                val cosR = cos(radians)
                val sinR = sin(radians)

                val transformedPath = Path().apply {
                    arrowPath.reset()
                    moveTo(indicatorX, indicatorY)
                    lineTo(indicatorX - 20f * cosR + 15f * sinR, indicatorY - 20f * sinR - 15f * cosR)
                    lineTo(indicatorX - 20f * cosR - 15f * sinR, indicatorY - 20f * sinR + 15f * cosR)
                    close()
                }

                drawPath(
                    path = transformedPath,
                    color = color,
                    style = androidx.compose.ui.graphics.drawscope.Fill
                )

                // Draw circle behind arrow
                drawCircle(
                    color = color.copy(alpha = 0.3f),
                    radius = 25f,
                    center = Offset(indicatorX, indicatorY)
                )
            }
        }

        // In-view satellite labels
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val screenWidth = constraints.maxWidth.toFloat()
            val screenHeight = constraints.maxHeight.toFloat()
            val density = LocalContext.current.resources.displayMetrics.density

            inViewSatellites.forEach { (satellite, pos) ->
                val (relAz, relEl, _) = pos
                val xDp = (screenWidth * (0.5f + (relAz / horizontalFOV)) / density).dp
                val yDp = (screenHeight * (0.5f - (relEl / verticalFOV)) / density).dp

                Card(
                    modifier = Modifier.offset(x = xDp, y = yDp + 50.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = satellite.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Alt: ${String.format("%.0f", satellite.altitude)} km",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Off-screen satellite labels with directional info
            offScreenSatellites.forEach { (satellite, pos) ->
                val (relAz, relEl, absoluteAzimuth) = pos
                val color = SatelliteColorUtils.getColorForSatellite(satellite.id)

                val edgeMargin = 60f / density
                val clampedX = (0.5f + (relAz / horizontalFOV)).coerceIn(0f, 1f)
                val clampedY = (0.5f - (relEl / verticalFOV)).coerceIn(0f, 1f)

                val labelX: Dp
                val labelY: Dp
                val direction: String

                when {
                    relAz < -horizontalFOV / 2 -> {
                        labelX = (edgeMargin + 30).dp
                        labelY = ((screenHeight / density) * clampedY).dp
                        direction = "← ${azimuthToDirection(absoluteAzimuth)}"
                    }
                    relAz > horizontalFOV / 2 -> {
                        labelX = ((screenWidth / density) - edgeMargin - 100).dp
                        labelY = ((screenHeight / density) * clampedY).dp
                        direction = "${azimuthToDirection(absoluteAzimuth)} →"
                    }
                    relEl > verticalFOV / 2 -> {
                        labelX = ((screenWidth / density) * clampedX - 50).dp
                        labelY = (edgeMargin + 30).dp
                        direction = "↑ ${String.format("%.0f", relEl)}°"
                    }
                    else -> {
                        labelX = ((screenWidth / density) * clampedX - 50).dp
                        labelY = ((screenHeight / density) - edgeMargin - 50).dp
                        direction = "↓ ${String.format("%.0f", abs(relEl))}°"
                    }
                }

                Card(
                    modifier = Modifier.offset(x = labelX, y = labelY),
                    colors = CardDefaults.cardColors(
                        containerColor = color.copy(alpha = 0.9f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = satellite.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = direction,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Compass indicator
        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🧭",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "${userAzimuth.toInt()}° ${azimuthToDirection(userAzimuth)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Elevation: ${userElevation.toInt()}°",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

// Calculate satellite position on screen based on user orientation
fun calculateSatelliteScreenPosition(
    satellite: SatelliteData,
    userLat: Double,
    userLon: Double,
    userAzimuth: Float,
    userElevation: Float
): Pair<Float, Float>? {
    // Calculate azimuth and elevation to satellite
    val satAzimuth = calculateAzimuth(userLat, userLon, satellite.latitude, satellite.longitude)
    val satElevation = calculateElevation(userLat, userLon, satellite.latitude, satellite.longitude, satellite.altitude)

    // Only show satellites above horizon
    if (satElevation < 0) return null

    // Calculate relative position to user's view
    val relativeAzimuth = normalizeAngle(satAzimuth - userAzimuth)
    val relativeElevation = satElevation - userElevation

    // Field of view (adjustable)
    val horizontalFOV = 90f // degrees
    val verticalFOV = 60f // degrees

    // Check if satellite is in view
    if (abs(relativeAzimuth) > horizontalFOV / 2 || abs(relativeElevation) > verticalFOV / 2) {
        return null
    }

    // Map to screen coordinates (0.0 to 1.0)
    val x = 0.5f + (relativeAzimuth / horizontalFOV)
    val y = 0.5f - (relativeElevation / verticalFOV)

    return x to y
}

fun calculateAzimuth(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val dLon = Math.toRadians(lon2 - lon1)
    val y = sin(dLon) * cos(Math.toRadians(lat2))
    val x = cos(Math.toRadians(lat1)) * sin(Math.toRadians(lat2)) -
            sin(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * cos(dLon)
    val azimuth = Math.toDegrees(atan2(y, x))
    return ((azimuth + 360) % 360).toFloat()
}

fun calculateElevation(lat1: Double, lon1: Double, lat2: Double, lon2: Double, altitude: Double): Float {
    // Simplified elevation calculation
    val earthRadius = 6371.0
    val distance = calculateDistance(lat1, lon1, lat2, lon2)
    val elevation = Math.toDegrees(atan2(altitude, distance))
    return elevation.toFloat()
}

fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadius = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadius * c
}

fun normalizeAngle(angle: Float): Float {
    var a = angle
    while (a > 180) a -= 360
    while (a < -180) a += 360
    return a
}

fun azimuthToDirection(azimuth: Float): String {
    return when {
        azimuth < 22.5 || azimuth >= 337.5 -> "N"
        azimuth < 67.5 -> "NE"
        azimuth < 112.5 -> "E"
        azimuth < 157.5 -> "SE"
        azimuth < 202.5 -> "S"
        azimuth < 247.5 -> "SW"
        azimuth < 292.5 -> "W"
        azimuth < 337.5 -> "NW"
        else -> "N"
    }
}
