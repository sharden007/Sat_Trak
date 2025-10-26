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

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val _errorMessage = mutableStateOf("")
    val errorMessage: State<String> = _errorMessage

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
            _isLoading.value = true
            _errorMessage.value = ""

            val data = repository.fetchSatellitePositions()

            if (data.isNotEmpty()) {
                _satellites.value = data
                Log.d("SatelliteViewModel", "Successfully fetched ${data.size} satellites")
            } else {
                if (_satellites.value.isEmpty()) {
                    _errorMessage.value = "No satellite data available. Check API key."
                }
                Log.w("SatelliteViewModel", "No satellites returned from API")
            }
        } catch (e: Exception) {
            _errorMessage.value = "Error: ${e.message}"
            Log.e("SatelliteViewModel", "Error fetching satellites: ${e.message}", e)
        } finally {
            _isLoading.value = false
        }
    }
}
