package com.example.sat_trak.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sat_trak.data.models.SatelliteData
import com.example.sat_trak.data.repository.SatelliteRepository
import com.example.sat_trak.notifications.SatelliteNotificationManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SatelliteViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SatelliteRepository()
    private val notificationManager = SatelliteNotificationManager(application)

    private val _satellites = mutableStateOf<List<SatelliteData>>(emptyList())
    val satellites: State<List<SatelliteData>> = _satellites

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val _errorMessage = mutableStateOf("")
    val errorMessage: State<String> = _errorMessage

    private val _userLatitude = mutableStateOf(0.0)
    val userLatitude: State<Double> = _userLatitude

    private val _userLongitude = mutableStateOf(0.0)
    val userLongitude: State<Double> = _userLongitude

    init {
        startPeriodicUpdate()
    }

    fun updateUserLocation(latitude: Double, longitude: Double) {
        _userLatitude.value = latitude
        _userLongitude.value = longitude
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

                // Check for visible satellites and send notifications
                if (_userLatitude.value != 0.0 || _userLongitude.value != 0.0) {
                    notificationManager.checkAndNotifyVisibleSatellites(
                        data,
                        _userLatitude.value,
                        _userLongitude.value
                    )
                }
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
