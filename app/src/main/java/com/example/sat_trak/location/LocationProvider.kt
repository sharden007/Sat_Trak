package com.example.sat_trak.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*

class LocationProvider(private val context: Context) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        10000L // 10 seconds
    ).apply {
        setMinUpdateIntervalMillis(5000L)
    }.build()

    private var locationCallback: LocationCallback? = null

    var onLocationChanged: ((latitude: Double, longitude: Double) -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        if (!hasLocationPermission()) {
            return
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    onLocationChanged?.invoke(location.latitude, location.longitude)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback!!,
            Looper.getMainLooper()
        )
    }

    @SuppressLint("MissingPermission")
    fun getLastKnownLocation(callback: (latitude: Double, longitude: Double) -> Unit) {
        if (!hasLocationPermission()) {
            // Return default location (equator, prime meridian)
            callback(0.0, 0.0)
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                callback(location.latitude, location.longitude)
            } else {
                callback(0.0, 0.0)
            }
        }
    }

    fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}

@Composable
fun rememberLocationProvider(): LocationState {
    val context = LocalContext.current
    val locationProvider = remember { LocationProvider(context) }

    var latitude by remember { mutableStateOf(0.0) }
    var longitude by remember { mutableStateOf(0.0) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    DisposableEffect(Unit) {
        if (hasPermission) {
            locationProvider.onLocationChanged = { lat, lon ->
                latitude = lat
                longitude = lon
            }

            locationProvider.getLastKnownLocation { lat, lon ->
                latitude = lat
                longitude = lon
            }

            locationProvider.startLocationUpdates()
        }

        onDispose {
            locationProvider.stopLocationUpdates()
        }
    }

    return LocationState(latitude, longitude, hasPermission)
}

data class LocationState(
    val latitude: Double,
    val longitude: Double,
    val hasPermission: Boolean
)

