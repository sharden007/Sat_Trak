package com.example.sat_trak.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.sat_trak.R
import com.example.sat_trak.data.models.SatelliteData
import kotlin.math.*

class SatelliteNotificationManager(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "satellite_visibility_channel"
        private const val CHANNEL_NAME = "Satellite Visibility Alerts"
        private const val CHANNEL_DESCRIPTION = "Notifications when satellites are visible overhead"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun checkAndNotifyVisibleSatellites(
        satellites: List<SatelliteData>,
        userLatitude: Double,
        userLongitude: Double
    ) {
        // Check permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val visibleSatellites = satellites.filter { satellite ->
            isVisibleFromLocation(satellite, userLatitude, userLongitude)
        }

        if (visibleSatellites.isNotEmpty()) {
            sendVisibilityNotification(visibleSatellites)
        }
    }

    private fun isVisibleFromLocation(
        satellite: SatelliteData,
        userLat: Double,
        userLon: Double
    ): Boolean {
        // Calculate distance from user
        val distance = calculateDistance(userLat, userLon, satellite.latitude, satellite.longitude)

        // Calculate elevation angle
        val earthRadius = 6371.0
        val elevation = Math.toDegrees(atan2(satellite.altitude, distance))

        // Satellite is visible if:
        // 1. Elevation angle is above 10 degrees (above horizon with margin)
        // 2. Distance is reasonable (within ~2000 km for good visibility)
        // 3. For ISS specifically, check if it's in sunlight and user is in darkness (simplified)

        return elevation > 10.0 && distance < 2000.0
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    private fun sendVisibilityNotification(satellites: List<SatelliteData>) {
        val notificationManager = NotificationManagerCompat.from(context)

        satellites.forEachIndexed { index, satellite ->
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("🛰️ ${satellite.name} is Visible!")
                .setContentText("Look up! ${satellite.name} is passing overhead at ${String.format("%.0f", satellite.altitude)} km altitude")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText("${satellite.name} is visible from your location!\n\n" +
                            "Altitude: ${String.format("%.2f", satellite.altitude)} km\n" +
                            "Type: ${satellite.type}\n\n" +
                            "${satellite.description}"))

            try {
                notificationManager.notify(satellite.id, builder.build())
            } catch (e: SecurityException) {
                // Permission not granted
            }
        }
    }

    fun notifyISSPass(altitude: Double, timeToPass: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🌟 ISS Overhead Alert!")
            .setContentText("The International Space Station is passing overhead in $timeToPass")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("The ISS is visible from your location!\n\n" +
                        "Time to pass: $timeToPass\n" +
                        "Altitude: ${String.format("%.2f", altitude)} km\n\n" +
                        "Look for a bright, fast-moving star in the sky!"))

        try {
            NotificationManagerCompat.from(context).notify(25544, builder.build())
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }
}

