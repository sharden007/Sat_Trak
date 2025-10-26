package com.example.sat_trak.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sat_trak.data.models.SatelliteData
import com.example.sat_trak.ui.components.GlobeWebView
import com.example.sat_trak.ui.viewmodel.SatelliteViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: SatelliteViewModel = viewModel()) {
    val satellites = viewModel.satellites.value
    val isLoading = viewModel.isLoading.value
    val errorMessage = viewModel.errorMessage.value

    var selectedSatellite by remember { mutableStateOf<SatelliteData?>(null) }
    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // 3D Globe WebView
        GlobeWebView(
            satellites = satellites,
            modifier = Modifier.fillMaxSize(),
            onSatelliteClick = { satellite ->
                selectedSatellite = satellite
                showBottomSheet = true
            }
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

        DetailRow("📍 Latitude", "${String.format("%.4f", satellite.latitude)}°")
        DetailRow("📍 Longitude", "${String.format("%.4f", satellite.longitude)}°")
        DetailRow("📏 Altitude", "${String.format("%.2f", satellite.altitude)} km")
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

        DetailRow("🔄 Orbital Radius", "${String.format("%.2f", orbitalRadius)} km")
        DetailRow("⚡ Orbital Speed", "~${String.format("%.2f", orbitalSpeed)} km/s")
        DetailRow("🕐 Est. Orbit Time", "${String.format("%.1f", orbitalCircumference / orbitalSpeed / 60)} min")

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
