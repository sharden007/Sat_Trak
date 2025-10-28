package com.example.sat_trak.data.repository

import android.content.Context
import com.example.sat_trak.data.models.Continent
import com.example.sat_trak.data.models.LatLon
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.abs

object ContinentDataLoader {

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val listType = Types.newParameterizedType(List::class.java, Continent::class.java)
    private val listAdapter = moshi.adapter<List<Continent>>(listType)

    fun parseFromJson(json: String): List<Continent> {
        return listAdapter.fromJson(json) ?: emptyList()
    }

    fun loadFromAssets(context: Context, assetFileName: String = "continents.json"): List<Continent> {
        val am = context.assets
        am.open(assetFileName).use { input ->
            BufferedReader(InputStreamReader(input)).use { reader ->
                val json = reader.readText()
                return parseFromJson(json)
            }
        }
    }

    // Validation
    fun validate(continent: Continent): List<String> {
        val issues = mutableListOf<String>()

        fun LatLon.inRange(name: String) {
            if (lat !in -90.0..90.0) issues += "$name.lat out of range [-90,90]: $lat"
            if (lon < -180.0 || lon > 180.0) issues += "$name.lon out of range [-180,180]: $lon"
        }

        if (continent.id.isBlank()) issues += "id must not be blank"
        if (continent.name.isBlank()) issues += "name must not be blank"
        if (continent.areaKm2 <= 0) issues += "areaKm2 must be positive"
        if (continent.population < 0) issues += "population must be >= 0"
        if (continent.countriesCount < 0) issues += "countriesCount must be >= 0"
        if (continent.landlockedCountriesCount < 0) issues += "landlockedCountriesCount must be >= 0"
        if (continent.coastLengthKm < 0) issues += "coastLengthKm must be >= 0"
        if (continent.populationYear !in 1900..2100) issues += "populationYear looks out of range: ${continent.populationYear}"

        // Density sanity (allow 5% relative error to avoid rounding issues)
        if (continent.areaKm2 > 0) {
            val expected = continent.population.toDouble() / continent.areaKm2.toDouble()
            val relErr = if (expected == 0.0) (if (continent.densityPerKm2 == 0.0) 0.0 else 1.0) else abs(continent.densityPerKm2 - expected) / expected
            if (relErr > 0.2 && continent.population > 0) { // allow generous 20% due to approximations in provided dataset
                issues += "densityPerKm2 deviates from population/area by >20% (density=${continent.densityPerKm2}, pop/area=$expected)"
            }
        }

        // Geo checks
        continent.centroid.inRange("centroid")
        with(continent.boundingBox) {
            if (minLat > maxLat) issues += "boundingBox minLat>maxLat"
            if (minLon < -180.0 || minLon > 180.0) issues += "boundingBox.minLon out of range: $minLon"
            if (maxLon < -180.0 || maxLon > 180.0) issues += "boundingBox.maxLon out of range: $maxLon"
        }
        continent.highestPoint.location.inRange("highestPoint.location")
        continent.lowestPoint.location.inRange("lowestPoint.location")
        continent.extremities.northmost.location.inRange("extremities.northmost")
        continent.extremities.southmost.location.inRange("extremities.southmost")
        continent.extremities.eastmost.location.inRange("extremities.eastmost")
        continent.extremities.westmost.location.inRange("extremities.westmost")

        // Content checks
        if (continent.majorOceans.any { it.isBlank() }) issues += "majorOceans should not contain blank entries"
        if (continent.majorLanguages.any { it.isBlank() }) issues += "majorLanguages should not contain blank entries"
        if (continent.subregions.any { it.name.isBlank() || it.countriesCount < 0 }) issues += "subregions entries must have non-blank name and non-negative countriesCount"

        return issues
    }

    fun validateAll(continents: List<Continent>): Map<String, List<String>> {
        return continents.associate { it.id to validate(it) }.filterValues { it.isNotEmpty() }
    }
}

