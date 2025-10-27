package com.example.sat_trak.data.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Continent(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "areaKm2") val areaKm2: Long,
    @Json(name = "population") val population: Long,
    @Json(name = "populationYear") val populationYear: Int,
    @Json(name = "countriesCount") val countriesCount: Int,
    @Json(name = "landlockedCountriesCount") val landlockedCountriesCount: Int,
    @Json(name = "coastLengthKm") val coastLengthKm: Int,
    @Json(name = "densityPerKm2") val densityPerKm2: Double,
    @Json(name = "centroid") val centroid: LatLon,
    @Json(name = "boundingBox") val boundingBox: BoundingBox,
    @Json(name = "highestPoint") val highestPoint: ElevationPoint,
    @Json(name = "lowestPoint") val lowestPoint: ElevationPoint,
    @Json(name = "extremities") val extremities: Extremities,
    @Json(name = "subregions") val subregions: List<Subregion>,
    @Json(name = "majorOceans") val majorOceans: List<String>,
    @Json(name = "majorLanguages") val majorLanguages: List<String>,
    @Json(name = "notes") val notes: String
)

@JsonClass(generateAdapter = true)
data class LatLon(
    @Json(name = "lat") val lat: Double,
    @Json(name = "lon") val lon: Double
)

@JsonClass(generateAdapter = true)
data class BoundingBox(
    @Json(name = "minLat") val minLat: Double,
    @Json(name = "minLon") val minLon: Double,
    @Json(name = "maxLat") val maxLat: Double,
    @Json(name = "maxLon") val maxLon: Double
)

@JsonClass(generateAdapter = true)
data class ElevationPoint(
    @Json(name = "name") val name: String,
    @Json(name = "elevationM") val elevationM: Int,
    @Json(name = "location") val location: LatLon
)

@JsonClass(generateAdapter = true)
data class NamedLocation(
    @Json(name = "name") val name: String,
    @Json(name = "location") val location: LatLon
)

@JsonClass(generateAdapter = true)
data class Extremities(
    @Json(name = "northmost") val northmost: NamedLocation,
    @Json(name = "southmost") val southmost: NamedLocation,
    @Json(name = "eastmost") val eastmost: NamedLocation,
    @Json(name = "westmost") val westmost: NamedLocation
)

@JsonClass(generateAdapter = true)
data class Subregion(
    @Json(name = "name") val name: String,
    @Json(name = "countriesCount") val countriesCount: Int
)

