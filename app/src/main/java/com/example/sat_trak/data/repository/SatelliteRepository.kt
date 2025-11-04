package com.example.sat_trak.data.repository

import android.util.Log
import com.example.sat_trak.BuildConfig
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
    private val apiKey = BuildConfig.N2YO_API_KEY
    private val observerLat = 0.0
    private val observerLng = 0.0
    private val observerAlt = 0
    private val seconds = 1

    private val satellites = listOf(
        SatelliteInfo(25544, "ISS", "Space Station", "International Space Station - A habitable artificial satellite in low Earth orbit, serving as a microgravity laboratory"),
        SatelliteInfo(33591, "NOAA 19", "Weather Satellite", "NOAA-19 - Polar-orbiting environmental satellite monitoring Earth's weather, atmosphere, and environment"),
        SatelliteInfo(36585, "GPS BIIF-1", "GPS Navigation", "GPS BIIF-1 (Navstar) - Part of the Global Positioning System constellation providing navigation and timing services"),
        SatelliteInfo(43013, "STARLINK-1007", "Communication", "Starlink-1007 - SpaceX broadband satellite providing global internet connectivity"),
        SatelliteInfo(37820, "HUBBLE", "Space Telescope", "Hubble Space Telescope - NASA's premier space-based observatory for astronomical research and deep space imaging"),
        SatelliteInfo(48274, "TIANGONG", "Space Station", "Tiangong Space Station - China's modular space station in low Earth orbit for scientific research"),
        SatelliteInfo(25994, "TERRA", "Earth Observation", "Terra (EOS AM-1) - NASA Earth observation satellite monitoring land, atmosphere, and oceans"),
        SatelliteInfo(27424, "AQUA", "Earth Observation", "Aqua (EOS PM-1) - NASA satellite collecting data on Earth's water cycle and climate"),
        SatelliteInfo(41765, "SENTINEL-6", "Earth Observation", "Sentinel-6 Michael Freilich - Satellite for ocean surface topography monitoring")
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
            satellites.map { satInfo ->
                async {
                    try {
                        val response = apiService.getSatellitePosition(
                            satelliteId = satInfo.id,
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
                            SatelliteData(
                                id = satInfo.id,
                                name = satInfo.name,
                                x = coords.x,
                                y = coords.y,
                                z = coords.z,
                                latitude = position.latitude,
                                longitude = position.longitude,
                                altitude = position.altitude,
                                type = satInfo.type,
                                description = satInfo.description
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("SatelliteRepository", "Error fetching satellite ${satInfo.id} (${satInfo.name}): ${e.message}", e)
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

    private data class SatelliteInfo(
        val id: Int,
        val name: String,
        val type: String,
        val description: String
    )
}
