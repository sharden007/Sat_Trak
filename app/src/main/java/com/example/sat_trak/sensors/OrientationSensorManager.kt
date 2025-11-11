package com.example.sat_trak.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlin.math.atan2
import kotlin.math.roundToInt

class OrientationSensorManager(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    var azimuth: Float = 0f
        private set
    var pitch: Float = 0f
        private set
    var roll: Float = 0f
        private set

    var onOrientationChanged: ((azimuth: Float, pitch: Float, roll: Float) -> Unit)? = null

    fun start() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        magnetometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
            }
        }

        updateOrientation()
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Handle accuracy changes if needed
    }

    private fun updateOrientation() {
        SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            accelerometerReading,
            magnetometerReading
        )

        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        // Convert to degrees
        azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
        if (azimuth < 0) azimuth += 360f

        pitch = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
        roll = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

        onOrientationChanged?.invoke(azimuth, pitch, roll)
    }
}

@Composable
fun rememberOrientationSensor(): OrientationSensorState {
    val context = LocalContext.current
    val sensorManager = remember { OrientationSensorManager(context) }

    var azimuth by remember { mutableStateOf(0f) }
    var pitch by remember { mutableStateOf(0f) }
    var roll by remember { mutableStateOf(0f) }

    DisposableEffect(Unit) {
        sensorManager.onOrientationChanged = { az, p, r ->
            azimuth = az
            pitch = p
            roll = r
        }
        sensorManager.start()

        onDispose {
            sensorManager.stop()
        }
    }

    return OrientationSensorState(azimuth, pitch, roll)
}

data class OrientationSensorState(
    val azimuth: Float,
    val pitch: Float,
    val roll: Float
) {
    val elevation: Float
        get() = pitch
}

