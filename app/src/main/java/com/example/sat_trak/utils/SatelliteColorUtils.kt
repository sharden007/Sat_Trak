package com.example.sat_trak.utils

import androidx.compose.ui.graphics.Color

object SatelliteColorUtils {
    // Predefined distinct colors for satellites
    private val colorPalette = listOf(
        Color(0xFFFF5252), // Red
        Color(0xFF4CAF50), // Green
        Color(0xFFFFC107), // Amber
        Color(0xFF2196F3), // Blue
        Color(0xFF9C27B0), // Purple
        Color(0xFFFF9800), // Orange
        Color(0xFF00BCD4), // Cyan
        Color(0xFFE91E63), // Pink
        Color(0xFF8BC34A), // Light Green
        Color(0xFFFF5722), // Deep Orange
        Color(0xFF3F51B5), // Indigo
        Color(0xFFFFEB3B), // Yellow
        Color(0xFF009688), // Teal
        Color(0xFFF44336), // Deep Red
        Color(0xFF673AB7), // Deep Purple
    )

    // Map satellite ID to color
    private val satelliteColorMap = mutableMapOf<Int, Color>()
    private var nextColorIndex = 0

    /**
     * Get a distinct color for a satellite ID.
     * Same ID always returns the same color.
     */
    fun getColorForSatellite(satelliteId: Int): Color {
        return satelliteColorMap.getOrPut(satelliteId) {
            val color = colorPalette[nextColorIndex % colorPalette.size]
            nextColorIndex++
            color
        }
    }

    /**
     * Get hex color string for use in JavaScript/HTML (format: #RRGGBB)
     */
    fun getHexColorForSatellite(satelliteId: Int): String {
        val color = getColorForSatellite(satelliteId)
        return String.format(
            "#%02X%02X%02X",
            (color.red * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue * 255).toInt()
        )
    }

    /**
     * Get integer color for use in JavaScript (format: 0xRRGGBB)
     */
    fun getIntColorForSatellite(satelliteId: Int): String {
        val color = getColorForSatellite(satelliteId)
        return String.format(
            "0x%02X%02X%02X",
            (color.red * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue * 255).toInt()
        )
    }

    /**
     * Reset the color mapping (useful for testing)
     */
    fun reset() {
        satelliteColorMap.clear()
        nextColorIndex = 0
    }
}

