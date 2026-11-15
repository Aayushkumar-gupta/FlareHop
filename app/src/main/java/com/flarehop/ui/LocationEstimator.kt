package com.flarehop.ui

import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sqrt

enum class EstimateSource { GPS, BLUETOOTH, GPS_AND_BLUETOOTH }

data class GpsFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Double?,
    val ageMin: Int,
)

data class GpsReference(val distanceM: Double, val bearingDeg: Double, val errorM: Double)

data class BluetoothReference(val rssi: Int, val distanceM: Double, val errorM: Double)

data class LocationEstimate(
    val distanceM: Double,
    val errorM: Double,
    val bearingDeg: Double?,
    val source: EstimateSource,
    val gps: GpsReference?,
    val bluetooth: BluetoothReference?,
)

// Kept free of Android so the numbers can be checked without a phone.
object LocationEstimator {
    // Starting values, like Contract 6: tune at the hall radio test with two phones a measured
    // distance apart.
    const val RSSI_AT_1M_DBM = -59.0
    const val PATH_LOSS_EXPONENT = 2.0
    const val BLUETOOTH_ERROR_FRACTION = 0.6
    const val BLUETOOTH_MIN_M = 0.5
    const val BLUETOOTH_MAX_RANGE_M = 150.0
    const val MIN_ERROR_M = 1.0
    const val UNKNOWN_GPS_ACCURACY_M = 50.0
    const val DRIFT_M_PER_MIN = 2.0
    const val MAX_GPS_ERROR_M = 150.0

    // Log-distance path loss. Walls and bodies make the signal swing by several dB, so the error
    // grows with the distance instead of staying fixed.
    fun bluetooth(rssi: Int): BluetoothReference {
        val meters = 10.0.pow((RSSI_AT_1M_DBM - rssi) / (10 * PATH_LOSS_EXPONENT))
            .coerceIn(BLUETOOTH_MIN_M, BLUETOOTH_MAX_RANGE_M)
        return BluetoothReference(
            rssi = rssi,
            distanceM = meters,
            errorM = maxOf(MIN_ERROR_M, meters * BLUETOOTH_ERROR_FRACTION),
        )
    }

    fun gps(here: GpsFix, there: GpsFix): GpsReference? {
        val error = hypot(
            here.accuracyM ?: UNKNOWN_GPS_ACCURACY_M,
            there.accuracyM ?: UNKNOWN_GPS_ACCURACY_M,
        ) + DRIFT_M_PER_MIN * (here.ageMin + there.ageMin)
        if (error > MAX_GPS_ERROR_M) return null
        return GpsReference(
            distanceM = distanceMeters(
                here.latitude,
                here.longitude,
                there.latitude,
                there.longitude,
            ),
            bearingDeg = bearingDegrees(
                here.latitude,
                here.longitude,
                there.latitude,
                there.longitude,
            ),
            errorM = maxOf(MIN_ERROR_M, error),
        )
    }

    fun estimate(gps: GpsReference?, bluetooth: BluetoothReference?): LocationEstimate? {
        // Hearing the phone at all proves it is within Bluetooth range, so a GPS point well outside
        // that range is stale or wrong.
        val usableGps = gps?.takeIf {
            bluetooth == null || it.distanceM - it.errorM <= BLUETOOTH_MAX_RANGE_M
        }
        val distance: Double
        val error: Double
        val source: EstimateSource
        when {
            usableGps != null && bluetooth != null -> {
                // Inverse-variance weighting: the source with the smaller error counts for more.
                val gpsWeight = 1 / (usableGps.errorM * usableGps.errorM)
                val bluetoothWeight = 1 / (bluetooth.errorM * bluetooth.errorM)
                val total = gpsWeight + bluetoothWeight
                distance =
                    (gpsWeight * usableGps.distanceM + bluetoothWeight * bluetooth.distanceM) /
                    total
                error = sqrt(1 / total)
                source = EstimateSource.GPS_AND_BLUETOOTH
            }

            usableGps != null -> {
                distance = usableGps.distanceM
                error = usableGps.errorM
                source = EstimateSource.GPS
            }

            bluetooth != null -> {
                distance = bluetooth.distanceM
                error = bluetooth.errorM
                source = EstimateSource.BLUETOOTH
            }

            else -> return null
        }
        // Bluetooth gives no direction, and inside its own error circle the GPS bearing could
        // point anywhere.
        val bearing = usableGps?.takeIf { it.distanceM > it.errorM }?.bearingDeg
        return LocationEstimate(distance, error, bearing, source, gps, bluetooth)
    }
}
