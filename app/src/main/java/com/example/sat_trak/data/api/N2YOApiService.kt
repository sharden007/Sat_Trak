package com.example.sat_trak.data.api

import com.example.sat_trak.data.models.SatellitePositionResponse
import retrofit2.http.GET
import retrofit2.http.Path

interface N2YOApiService {
    @GET("rest/v1/satellite/positions/{id}/{observer_lat}/{observer_lng}/{observer_alt}/{seconds}/&apiKey={apiKey}")
    suspend fun getSatellitePosition(
        @Path("id") satelliteId: Int,
        @Path("observer_lat") observerLat: Double,
        @Path("observer_lng") observerLng: Double,
        @Path("observer_alt") observerAlt: Int,
        @Path("seconds") seconds: Int,
        @Path("apiKey") apiKey: String
    ): SatellitePositionResponse
}

