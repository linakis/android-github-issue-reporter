package com.ghreporter.shake

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Detects device shake gestures using the accelerometer.
 *
 * Uses a simple algorithm based on acceleration magnitude exceeding a threshold.
 * Includes debounce logic to prevent multiple triggers.
 *
 * @param context Android context for accessing sensor service
 * @param thresholdG Shake threshold in G-force units (default: 2.7G)
 * @param cooldownMs Minimum time between shake detections in milliseconds (default: 1000ms)
 * @param onShake Callback invoked when a shake is detected
 */
class ShakeDetector(
    context: Context,
    private val thresholdG: Float = 2.7f,
    private val cooldownMs: Long = 1000L,
    private val onShake: () -> Unit
) : SensorEventListener {

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var lastShakeTime: Long = 0
    private var isRegistered: Boolean = false

    // For calculating acceleration magnitude
    private var lastX: Float = 0f
    private var lastY: Float = 0f
    private var lastZ: Float = 0f
    private var lastUpdateTime: Long = 0
    private var isFirstReading: Boolean = true

    /**
     * Start listening for shake gestures.
     *
     * @return true if the sensor was registered successfully
     */
    fun start(): Boolean {
        if (isRegistered) return true
        if (accelerometer == null) return false

        isFirstReading = true
        isRegistered = sensorManager.registerListener(
            this,
            accelerometer,
            SensorManager.SENSOR_DELAY_UI // ~60ms updates
        )

        return isRegistered
    }

    /**
     * Stop listening for shake gestures.
     */
    fun stop() {
        if (isRegistered) {
            sensorManager.unregisterListener(this)
            isRegistered = false
        }
    }

    /**
     * Check if the detector is currently active.
     */
    fun isActive(): Boolean = isRegistered

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val currentTime = System.currentTimeMillis()

        // Skip first reading to establish baseline
        if (isFirstReading) {
            lastX = event.values[0]
            lastY = event.values[1]
            lastZ = event.values[2]
            lastUpdateTime = currentTime
            isFirstReading = false
            return
        }

        // Calculate time delta
        val timeDelta = currentTime - lastUpdateTime
        if (timeDelta < 50) return // Ignore if less than 50ms

        lastUpdateTime = currentTime

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Calculate delta acceleration
        val deltaX = x - lastX
        val deltaY = y - lastY
        val deltaZ = z - lastZ

        lastX = x
        lastY = y
        lastZ = z

        // Calculate acceleration magnitude (in G)
        // Divide by standard gravity (9.81 m/s²) to convert to G-force
        val accelerationMagnitude = sqrt(
            deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ
        ) / SensorManager.GRAVITY_EARTH

        // Check if exceeds threshold
        if (accelerationMagnitude > thresholdG) {
            // Check cooldown
            if (currentTime - lastShakeTime > cooldownMs) {
                lastShakeTime = currentTime
                onShake()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for shake detection
    }

    /**
     * Check if the device has an accelerometer.
     */
    fun hasAccelerometer(): Boolean = accelerometer != null

    companion object {
        /**
         * Default shake threshold in G-force.
         * A value of 2.7G works well for intentional shakes while avoiding false positives.
         */
        const val DEFAULT_THRESHOLD_G = 2.7f

        /**
         * Default cooldown between shake detections.
         */
        const val DEFAULT_COOLDOWN_MS = 1000L

        /**
         * Check if a device has an accelerometer without creating a detector instance.
         */
        fun isShakeDetectionSupported(context: Context): Boolean {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            return sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
        }
    }
}
