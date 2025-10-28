package com.example.sat_trak.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sat_trak.data.models.SatelliteData
import com.example.sat_trak.ui.components.GlobeWebView
import com.example.sat_trak.ui.viewmodel.SatelliteViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: SatelliteViewModel = viewModel()) {
    val satellites = viewModel.satellites.value
    val isLoading = viewModel.isLoading.value
    val errorMessage = viewModel.errorMessage.value

    var selectedSatellite by remember { mutableStateOf<SatelliteData?>(null) }
    var userSelectedSatellite by remember { mutableStateOf(false) } // Track if user manually clicked
    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }
    var showApiDataDialog by remember { mutableStateOf(false) }
    var onZoomIn by remember { mutableStateOf<(() -> Unit)?>(null) }
    var onZoomOut by remember { mutableStateOf<(() -> Unit)?>(null) }

    // New UI state: trails on/off (trail length is now fixed at 130)
    var showTrails by remember { mutableStateOf(true) }

    // Auto-cycle through satellites for telemetry display (5 seconds each)
    LaunchedEffect(satellites, userSelectedSatellite) {
        if (satellites.isNotEmpty() && !userSelectedSatellite) {
            var currentIndex = 0
            while (true) {
                if (!userSelectedSatellite) {
                    selectedSatellite = satellites[currentIndex % satellites.size]
                    currentIndex++
                }
                kotlinx.coroutines.delay(5000L) // 5 seconds
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 3D Globe WebView
        GlobeWebView(
            satellites = satellites,
            modifier = Modifier.fillMaxSize(),
            onSatelliteClick = { satellite ->
                selectedSatellite = satellite
                userSelectedSatellite = true // User manually selected
                showBottomSheet = true
            },
            onZoomControlsReady = { zoomIn, zoomOut ->
                onZoomIn = zoomIn
                onZoomOut = zoomOut
            },
            // use named arguments for trailing parameters to avoid positional-after-named error
            showTrails = showTrails,
            selectedSatelliteId = selectedSatellite?.id
        )

        // Loading Indicator
        if (isLoading && satellites.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading satellites...",
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        // Error Message
        if (errorMessage.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = errorMessage,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Satellite Count Badge
        if (satellites.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🛰️ ${satellites.size}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Tracking",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // Telemetry HUD (top-left) - show which satellite this telemetry refers to
        val telemetrySatellite = selectedSatellite ?: satellites.firstOrNull()
        val telemetry = remember(telemetrySatellite) {
            telemetrySatellite?.let { s ->
                val orbitalSpeed = when (s.id) {
                    25544 -> 7.66
                    33591 -> 7.40
                    else -> 3.87
                }
                val heading = estimateHeading(s)
                Triple(orbitalSpeed, heading, s.altitude)
            } ?: Triple(0.0, 0.0, 0.0)
        }

        Card(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 100.dp, start = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Show satellite name in the header
                if (telemetrySatellite != null) {
                    Text(
                        text = "📊 Telemetry",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${telemetrySatellite.name}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "NORAD ${telemetrySatellite.id}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "📊 Telemetry",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "No satellite selected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                // use explicit locale-aware formatting
                Text("Speed: ${String.format(Locale.getDefault(), "%.2f", telemetry.first)} km/s", style = MaterialTheme.typography.bodySmall)
                Text("Heading: ${String.format(Locale.getDefault(), "%.1f", telemetry.second)}°", style = MaterialTheme.typography.bodySmall)
                Text("Elevation: ${String.format(Locale.getDefault(), "%.2f", telemetry.third)} km", style = MaterialTheme.typography.bodySmall)
            }
        }

        // API Data Viewer Button
        FloatingActionButton(
            onClick = { showApiDataDialog = true },
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "View API Data"
            )
        }

        // Zoom Controls
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Zoom In Button
            FloatingActionButton(
                onClick = { onZoomIn?.invoke() },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Zoom In",
                    modifier = Modifier.size(24.dp)
                )
            }

            // Zoom Out Button
            FloatingActionButton(
                onClick = { onZoomOut?.invoke() },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(56.dp)
            ) {
                Text(
                    text = "−",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Controls for trails (moved from top-center to bottom-center above rotation)
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp, start = 16.dp, end = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Orbital Trails", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(checked = showTrails, onCheckedChange = { showTrails = it })
                }
            }
        }

        // Removed the MiniMap box to declutter the display (per user request)

        // API Data Dialog
        if (showApiDataDialog) {
            AlertDialog(
                onDismissRequest = { showApiDataDialog = false },
                title = {
                    Text(
                        text = "📡 Live Satellite Data",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    ApiDataContent(satellites = satellites, isLoading = isLoading)
                },
                confirmButton = {
                    TextButton(onClick = { showApiDataDialog = false }) {
                        Text("Close")
                    }
                },
                modifier = Modifier.fillMaxWidth(0.95f)
            )
        }

        // Bottom Sheet for Satellite Details
        if (showBottomSheet && selectedSatellite != null) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState
            ) {
                SatelliteDetailSheet(satellite = selectedSatellite!!)
            }
        }
    }
}

@Composable
fun ApiDataContent(satellites: List<SatelliteData>, isLoading: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 500.dp)
            .verticalScroll(rememberScrollState())
    ) {
        if (isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Fetching data...")
            }
        } else if (satellites.isEmpty()) {
            Text(
                text = "No satellite data available yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val currentTime = dateFormat.format(Date())

            Text(
                text = "Last Updated: $currentTime",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            satellites.forEachIndexed { index, satellite ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = satellite.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Surface(
                                modifier = Modifier.size(12.dp),
                                shape = MaterialTheme.shapes.small,
                                color = when (satellite.id) {
                                    25544 -> MaterialTheme.colorScheme.error
                                    33591 -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.primaryContainer
                                }
                            ) {}
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        ApiDataRow("NORAD ID", satellite.id.toString())
                        ApiDataRow("Type", satellite.type)
                        ApiDataRow("Latitude", String.format(Locale.getDefault(), "%.6f°", satellite.latitude))
                        ApiDataRow("Longitude", String.format(Locale.getDefault(), "%.6f°", satellite.longitude))
                        ApiDataRow("Altitude", String.format(Locale.getDefault(), "%.2f km", satellite.altitude))
                        ApiDataRow("Position X", String.format(Locale.getDefault(), "%.2f km", satellite.x))
                        ApiDataRow("Position Y", String.format(Locale.getDefault(), "%.2f km", satellite.y))
                        ApiDataRow("Position Z", String.format(Locale.getDefault(), "%.2f km", satellite.z))
                    }
                }
            }
        }
    }
}

@Composable
fun ApiDataRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun SatelliteDetailSheet(satellite: SatelliteData) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = satellite.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = satellite.type,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            // Color indicator
            Surface(
                modifier = Modifier.size(40.dp),
                shape = MaterialTheme.shapes.medium,
                color = when (satellite.id) {
                    25544 -> MaterialTheme.colorScheme.error
                    33591 -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primaryContainer
                }
            ) {}
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Description
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text(
                text = satellite.description,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Position Details
        Text(
            text = "📡 Current Position",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        DetailRow("📍 Latitude", String.format(Locale.getDefault(), "%.4f°", satellite.latitude))
        DetailRow("📍 Longitude", String.format(Locale.getDefault(), "%.4f°", satellite.longitude))
        DetailRow("📏 Altitude", String.format(Locale.getDefault(), "%.2f km", satellite.altitude))
        DetailRow("🆔 NORAD ID", satellite.id.toString())

        Spacer(modifier = Modifier.height(24.dp))

        // Orbital Information
        Text(
            text = "🌍 Orbital Data",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        val orbitalRadius = 6371.0 + satellite.altitude
        val orbitalCircumference = 2 * Math.PI * orbitalRadius
        val orbitalSpeed = when (satellite.id) {
            25544 -> 7.66  // ISS approximate speed in km/s
            33591 -> 7.40  // NOAA 19 approximate speed
            else -> 3.87   // GPS satellite approximate speed
        }

        DetailRow("🔄 Orbital Radius", String.format(Locale.getDefault(), "%.2f km", orbitalRadius))
        DetailRow("⚡ Orbital Speed", String.format(Locale.getDefault(), "~%.2f km/s", orbitalSpeed))
        DetailRow("🕐 Est. Orbit Time", String.format(Locale.getDefault(), "%.1f min", orbitalCircumference / orbitalSpeed / 60))

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

/* -------------------------
   MiniMap and helpers
   ------------------------- */

@Suppress("unused")
@Composable
fun MiniMap(
    satellites: List<SatelliteData>,
    selected: SatelliteData?,
    showTrails: Boolean,
    trailSteps: Int
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Simple background gradient for sea/land impression
        drawRect(
            brush = Brush.verticalGradient(listOf(Color(0xFF0B3D91), Color(0xFF113A6C))),
            size = size
        )

        // Draw equator and meridian for context
        val centerX = w / 2f
        val centerY = h / 2f
        drawIntoCanvas { canvas ->
            canvas.drawLine(Offset(0f, centerY), Offset(w, centerY), androidx.compose.ui.graphics.Paint().apply {
                color = Color.White.copy(alpha = 0.08f)
                this.strokeWidth = 1f
            })
            canvas.drawLine(Offset(centerX, 0f), Offset(centerX, h), androidx.compose.ui.graphics.Paint().apply {
                color = Color.White.copy(alpha = 0.08f)
                this.strokeWidth = 1f
            })
        }

        satellites.forEach { s ->
            // Draw trail if requested: generate projected points
            if (showTrails) {
                val projected = projectOrbitPoints(s, trailSteps, stepSec = 30.0)
                val path = Path()
                projected.forEachIndexed { idx, (lat, lon) ->
                    val px = ((lon + 180.0) / 360.0 * w).toFloat()
                    val py = (((90.0 - lat) / 180.0) * h).toFloat()
                    if (idx == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }

                // fading stroke: draw multiple layered strokes with decreasing alpha
                val total = projected.size
                for (i in 0 until total - 1) {
                    val t = i.toFloat() / max(1f, (total - 1).toFloat())
                    val alpha = 0.9f * (1f - t)
                    drawPath(
                        path = path,
                        color = Color.Cyan.copy(alpha = alpha),
                        style = Stroke(width = 1f)
                    )
                }
            }

            // Draw satellite dot
            val x = ((s.longitude + 180.0) / 360.0 * w).toFloat()
            val y = (((90.0 - s.latitude) / 180.0) * h).toFloat()
            drawCircle(
                color = if (s == selected) Color.Yellow else Color.White,
                radius = if (s == selected) 4f else 3f,
                center = Offset(x, y)
            )
        }
    }
}

/* approximate projection of an orbit by advancing longitude using angular speed.
   This is a simple circular-orbit approximation for visualization only. */
fun projectOrbitPoints(s: SatelliteData, steps: Int, stepSec: Double): List<Pair<Double, Double>> {
    val orbitalRadius = 6371.0 + s.altitude
    val orbitalSpeed = when (s.id) {
        25544 -> 7.66
        33591 -> 7.40
        else -> 3.87
    } // km/s
    val angularSpeedRadPerSec = orbitalSpeed / orbitalRadius // rad/s
    val deltaAngleRad = angularSpeedRadPerSec * stepSec
    val deltaLonDeg = Math.toDegrees(deltaAngleRad)

    val points = mutableListOf<Pair<Double, Double>>()
    var lon = s.longitude
    val lat = s.latitude // assume roughly constant latitude for short projection
    repeat(steps) {
        points.add(Pair(lat, normalizeLongitude(lon)))
        lon += deltaLonDeg
    }
    return points
}

fun normalizeLongitude(lon: Double): Double {
    var l = lon % 360.0
    if (l > 180.0) l -= 360.0
    if (l < -180.0) l += 360.0
    return l
}

/* crude heading estimate: compute bearing between first two projected points */
fun estimateHeading(s: SatelliteData): Double {
    val projected = projectOrbitPoints(s, steps = 2, stepSec = 10.0)
    if (projected.size < 2) return 0.0
    val (lat1, lon1) = projected[0]
    val (lat2, lon2) = projected[1]
    return bearingBetween(lat1, lon1, lat2, lon2)
}

/* bearing formula (initial bearing) */
fun bearingBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val deltaLambda = Math.toRadians(lon2 - lon1)
    val y = sin(deltaLambda) * cos(phi2)
    val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
    var theta = Math.toDegrees(atan2(y, x))
    theta = (theta + 360.0) % 360.0
    return theta
}
