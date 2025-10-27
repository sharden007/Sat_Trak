package com.example.sat_trak.data

import com.example.sat_trak.data.repository.ContinentDataLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinentDataLoaderTest {

    @Test
    fun parse_validJson_returnsContinents() {
        val json = """
            [
              {
                "id": "EU",
                "name": "Europe",
                "areaKm2": 10180000,
                "population": 745000000,
                "populationYear": 2024,
                "countriesCount": 44,
                "landlockedCountriesCount": 17,
                "coastLengthKm": 38000,
                "densityPerKm2": 73.2,
                "centroid": { "lat": 54.5, "lon": 15.25 },
                "boundingBox": { "minLat": 34.5, "minLon": -31.0, "maxLat": 71.2, "maxLon": 69.0 },
                "highestPoint": {
                  "name": "Mount Elbrus",
                  "elevationM": 5642,
                  "location": { "lat": 43.349, "lon": 42.445 }
                },
                "lowestPoint": {
                  "name": "Caspian Sea Shore",
                  "elevationM": -28,
                  "location": { "lat": 41.0, "lon": 50.0 }
                },
                "extremities": {
                  "northmost": { "name": "Nordkinn (Norway)", "location": { "lat": 71.17, "lon": 27.72 } },
                  "southmost": { "name": "Gavdos (Greece)", "location": { "lat": 34.84, "lon": 24.08 } },
                  "eastmost": { "name": "Ural Mountains (Russia)", "location": { "lat": 67.5, "lon": 66.0 } },
                  "westmost": { "name": "Monchique Islet (Azores, Portugal)", "location": { "lat": 39.48, "lon": -31.27 } }
                },
                "subregions": [
                  { "name": "Eastern Europe", "countriesCount": 10 },
                  { "name": "Northern Europe", "countriesCount": 10 },
                  { "name": "Southern Europe", "countriesCount": 15 },
                  { "name": "Western Europe", "countriesCount": 9 }
                ],
                "majorOceans": ["Arctic Ocean", "Atlantic Ocean"],
                "majorLanguages": ["Russian", "German", "French", "English", "Italian", "Spanish", "Turkish"],
                "notes": "Test sample"
              }
            ]
        """.trimIndent()

        val continents = ContinentDataLoader.parseFromJson(json)
        assertEquals(1, continents.size)
        val eu = continents[0]
        assertEquals("EU", eu.id)
        assertEquals("Europe", eu.name)
        assertEquals(10180000L, eu.areaKm2)
        assertEquals(745000000L, eu.population)

        val issues = ContinentDataLoader.validate(eu)
        assertTrue("Expected no validation issues, got: $issues", issues.isEmpty())
    }

    @Test
    fun validate_detectsProblems() {
        val badJson = """
            [
              {
                "id": "",
                "name": "",
                "areaKm2": -5,
                "population": -1,
                "populationYear": 2150,
                "countriesCount": -1,
                "landlockedCountriesCount": -2,
                "coastLengthKm": -3,
                "densityPerKm2": 9999.0,
                "centroid": { "lat": 100.0, "lon": 200.0 },
                "boundingBox": { "minLat": 10.0, "minLon": -200.0, "maxLat": -10.0, "maxLon": 300.0 },
                "highestPoint": { "name": "X", "elevationM": 1, "location": { "lat": 95.0, "lon": 0.0 } },
                "lowestPoint": { "name": "Y", "elevationM": -1, "location": { "lat": -95.0, "lon": 0.0 } },
                "extremities": {
                  "northmost": { "name": "N", "location": { "lat": 91.0, "lon": 0.0 } },
                  "southmost": { "name": "S", "location": { "lat": -91.0, "lon": 0.0 } },
                  "eastmost": { "name": "E", "location": { "lat": 0.0, "lon": 181.0 } },
                  "westmost": { "name": "W", "location": { "lat": 0.0, "lon": -181.0 } }
                },
                "subregions": [ { "name": "", "countriesCount": -1 } ],
                "majorOceans": [""],
                "majorLanguages": [""],
                "notes": ""
              }
            ]
        """.trimIndent()
        val continents = ContinentDataLoader.parseFromJson(badJson)
        assertEquals(1, continents.size)
        val issues = ContinentDataLoader.validate(continents[0])
        // Expect multiple issues
        assertTrue("Expected multiple validation issues", issues.size >= 10)
    }
}

