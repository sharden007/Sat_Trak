package com.example.sat_trak.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.sat_trak.data.models.SatelliteData
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun BirdsEyeViewDialog(
    satellite: SatelliteData,
    onDismiss: () -> Unit
) {
    var zoomPhase by remember { mutableStateOf(0) } // 0=space, 1=zooming, 2=ground
    var scanLineProgress by remember { mutableStateOf(0f) }

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
                BirdsEyeHeader(satellite = satellite, onDismiss = onDismiss)

                // Main visualization
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    when (zoomPhase) {
                        0 -> SpaceView(satellite)
                        1 -> ZoomingView(satellite)
                        2 -> GroundView(satellite, scanLineProgress)
                    }
                }

                // Footer with controls and info
                BirdsEyeFooter(satellite = satellite, zoomPhase = zoomPhase)
            }
        }
    }
}

@Composable
fun BirdsEyeHeader(satellite: SatelliteData, onDismiss: () -> Unit) {
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
                Text(
                    text = "🛰️ BIRD'S EYE VIEW",
                    color = Color(0xFF00FF41),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
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
fun SpaceView(satellite: SatelliteData) {
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
fun ZoomingView(satellite: SatelliteData) {
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
fun GroundView(satellite: SatelliteData, scanProgress: Float) {
    val shimmer by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Simulated ground view
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

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
                    center = Offset(width * 0.5f, height * 0.6f),
                    radius = width * 0.4f
                )

                // Cities (dots)
                for (i in 1..5) {
                    val angle = (i * 72f) + shimmer * 360f
                    val cityX = width * 0.5f + cos(Math.toRadians(angle.toDouble())).toFloat() * (width * 0.2f)
                    val cityY = height * 0.6f + sin(Math.toRadians(angle.toDouble())).toFloat() * (width * 0.2f)

                    drawCircle(
                        color = Color(0xFFFBBF24).copy(alpha = 0.6f + shimmer * 0.4f),
                        center = Offset(cityX, cityY),
                        radius = 8f
                    )
                }
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
                text = String.format(Locale.US, "LAT: %.2f° LON: %.2f°", satellite.latitude, satellite.longitude),
                color = Color.White,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun BirdsEyeFooter(satellite: SatelliteData, zoomPhase: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF0A0E27).copy(alpha = 0.95f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
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
