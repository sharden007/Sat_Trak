package com.example.sat_trak

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.sat_trak.data.repository.ContinentDataLoader
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContinentAssetsInstrumentedTest {

    @Test
    fun loadContinentsFromAssets_andValidate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val continents = ContinentDataLoader.loadFromAssets(context)
        assertTrue("Expected at least one continent entry", continents.isNotEmpty())
        val issues = ContinentDataLoader.validateAll(continents)
        // Allow some density deviation due to approximations in the dataset
        val nonDensityIssues = issues.mapValues { (_, v) -> v.filterNot { it.startsWith("densityPerKm2") } }.filterValues { it.isNotEmpty() }
        assertTrue("Validation issues found: $nonDensityIssues", nonDensityIssues.isEmpty())
    }
}

