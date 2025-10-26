package com.example.sat_trak.data.repository

import android.util.Log
import com.example.sat_trak.data.api.N2YOApiService
import com.example.sat_trak.data.models.SatelliteData
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import kotlin.math.cos
import kotlin.math.sin

class SatelliteRepository {
    private val apiKey = "YOUR_API_KEY_HERE"
    private val observerLat = 0.0
    private val observerLng = 0.0
    private val observerAlt = 0
    private val seconds = 1

    private val satellites = listOf(
        Pair(25544, "ISS"),
        Pair(33591, "NOAA 19"),
        Pair(36585, "GPS BIIF-1")
    )

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.n2yo.com/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val apiService = retrofit.create(N2YOApiService::class.java)

    suspend fun fetchSatellitePositions(): List<SatelliteData> = coroutineScope {
        try {
            satellites.map { (id, name) ->
                async {
                    try {
                        val response = apiService.getSatellitePosition(
                            satelliteId = id,
                            observerLat = observerLat,
                            observerLng = observerLng,
                            observerAlt = observerAlt,
                            seconds = seconds,
                            apiKey = apiKey
                        )

                        response.positions.firstOrNull()?.let { position ->
                            val coords = latLonAltToCartesian(
                                position.latitude,
                                position.longitude,
                                position.altitude
                            )
                            SatelliteData(id, name, coords.x, coords.y, coords.z)
                        }
                    } catch (e: Exception) {
                        Log.e("SatelliteRepository", "Error fetching satellite $id ($name): ${e.message}", e)
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        } catch (e: Exception) {
            Log.e("SatelliteRepository", "Error in fetchSatellitePositions: ${e.message}", e)
            emptyList()
        }
    }

    private fun latLonAltToCartesian(lat: Double, lon: Double, alt: Double): Coordinate {
        val earthRadius = 6371.0
        val totalRadius = earthRadius + alt

        val latRad = Math.toRadians(lat)
        val lonRad = Math.toRadians(lon)

        val x = totalRadius * cos(latRad) * cos(lonRad)
        val y = totalRadius * sin(latRad)
        val z = -totalRadius * cos(latRad) * sin(lonRad)

        return Coordinate(x, y, z)
    }

    data class Coordinate(val x: Double, val y: Double, val z: Double)
}

