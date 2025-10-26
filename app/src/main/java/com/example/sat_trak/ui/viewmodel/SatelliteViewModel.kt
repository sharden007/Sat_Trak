package com.example.sat_trak.ui.viewmodel

import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sat_trak.data.models.SatelliteData
import com.example.sat_trak.data.repository.SatelliteRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SatelliteViewModel : ViewModel() {
    private val repository = SatelliteRepository()

    private val _satellites = mutableStateOf<List<SatelliteData>>(emptyList())
    val satellites: State<List<SatelliteData>> = _satellites

    init {
        startPeriodicUpdate()
    }

    private fun startPeriodicUpdate() {
        viewModelScope.launch {
            while (true) {
                fetchSatellites()
                delay(10000) // Update every 10 seconds
            }
        }
    }

    private suspend fun fetchSatellites() {
        try {
            val data = repository.fetchSatellitePositions()
            _satellites.value = data
            Log.d("SatelliteViewModel", "Fetched ${data.size} satellites")
        } catch (e: Exception) {
            Log.e("SatelliteViewModel", "Error fetching satellites: ${e.message}", e)
        }
    }
}

