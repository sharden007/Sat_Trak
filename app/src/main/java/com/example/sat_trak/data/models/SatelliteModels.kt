package com.example.sat_trak.data.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SatellitePositionResponse(
    @Json(name = "info") val info: SatelliteInfo,
    @Json(name = "positions") val positions: List<Position>
)

@JsonClass(generateAdapter = true)
data class SatelliteInfo(
    @Json(name = "satname") val satName: String,
    @Json(name = "satid") val satId: Int,
    @Json(name = "transactionscount") val transactionsCount: Int
)

@JsonClass(generateAdapter = true)
data class Position(
    @Json(name = "satlatitude") val latitude: Double,
    @Json(name = "satlongitude") val longitude: Double,
    @Json(name = "sataltitude") val altitude: Double,
    @Json(name = "timestamp") val timestamp: Long
)

data class SatelliteData(
    val id: Int,
    val name: String,
    val x: Double,
    val y: Double,
    val z: Double
)

