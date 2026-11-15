package com.flarehop.ui

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

// Pure angle math for the Track arrow, kept free of Android so it can be checked without a phone.
object Angles {
    // Any angle into 0 until 360.
    fun normalize(degrees: Float): Float = ((degrees % FULL) + FULL) % FULL

    // The signed turn from one angle to another, the short way round: -180 until 180.
    fun shortestTurn(from: Float, to: Float): Float {
        val turn = normalize(to - from)
        return if (turn >= HALF) turn - FULL else turn
    }

    // A compass reading jumps by a few degrees every sample, so each one only moves the heading
    // part of the way, across north the short way.
    fun smooth(previous: Float?, next: Float, weight: Float = SMOOTHING): Float {
        if (previous == null) return normalize(next)
        return normalize(previous + shortestTurn(previous, next) * weight)
    }

    // The angle to animate to: the same direction as target, as close as possible to current, so
    // the arrow never spins the long way round when the angle passes 0.
    fun unwrapNear(current: Float, target: Float): Float = current + shortestTurn(current, target)

    const val SMOOTHING = 0.15f
    private const val FULL = 360f
    private const val HALF = 180f
}

// The direction the top of the phone points, in degrees from north, or null on a phone without
// the sensors for it. Assumes the phone is held upright in portrait, like the rest of the app.
@Composable
fun rememberHeading(): Float? {
    val context = LocalContext.current
    var heading by remember { mutableStateOf<Float?>(null) }
    DisposableEffect(Unit) {
        val manager = context.getSystemService(SensorManager::class.java)
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val rotation = FloatArray(MATRIX_SIZE)
        val orientation = FloatArray(AXES)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                SensorManager.getOrientation(rotation, orientation)
                val azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                heading = Angles.smooth(heading, azimuth)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) {
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { manager?.unregisterListener(listener) }
    }
    return heading
}

private const val MATRIX_SIZE = 9
private const val AXES = 3
