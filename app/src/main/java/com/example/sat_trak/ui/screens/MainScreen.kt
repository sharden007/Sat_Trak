package com.example.sat_trak.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sat_trak.ui.components.GlobeWebView
import com.example.sat_trak.ui.viewmodel.SatelliteViewModel

@Composable
fun MainScreen(viewModel: SatelliteViewModel = viewModel()) {
    val satellites = viewModel.satellites.value

    GlobeWebView(
        satellites = satellites,
        modifier = Modifier.fillMaxSize()
    )
}

