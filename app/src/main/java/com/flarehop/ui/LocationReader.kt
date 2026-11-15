package com.flarehop.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.flarehop.flare.Fix
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LocationReader(private val context: Context, scope: CoroutineScope) {
    private enum class Access { NONE, APPROXIMATE, PRECISE }

    private val manager = context.getSystemService(LocationManager::class.java)
    private val listener = LocationListener { offer(it) }
    private var listening = Access.NONE

    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location.asStateFlow()

    init {
        // The permission dialog opens at app start, so the answer can come after this runs,
        // and the user can later switch Approximate to Precise in Settings.
        scope.launch {
            while (isActive) {
                val access = access()
                if (access != listening) listen(access)
                delay(PERMISSION_CHECK_MS)
            }
        }
    }

    fun lastFix(): Fix? {
        val location = _location.value ?: return null
        val ageMs = System.currentTimeMillis() - location.time
        return Fix(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyM = if (location.hasAccuracy()) location.accuracy.roundToInt() else null,
            fixAgeMin = (ageMs / MS_PER_MIN).toInt().coerceAtLeast(0),
        )
    }

    fun stop() {
        if (listening != Access.NONE) manager.removeUpdates(listener)
        listening = Access.NONE
    }

    // The GPS provider throws a SecurityException without precise location, so with
    // Approximate only the network provider is used.
    @SuppressLint("MissingPermission")
    private fun listen(access: Access) {
        stop()
        val providers = when (access) {
            Access.NONE -> return
            Access.APPROXIMATE -> listOf(LocationManager.NETWORK_PROVIDER)
            Access.PRECISE -> listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        }
        providers.filter { it in manager.allProviders }.forEach { provider ->
            manager.getLastKnownLocation(provider)?.let(::offer)
            manager.requestLocationUpdates(
                provider,
                UPDATE_MS,
                0f,
                listener,
                Looper.getMainLooper(),
            )
        }
        listening = access
    }

    private fun offer(next: Location) {
        val current = _location.value
        if (current == null || isBetter(next, current)) _location.value = next
    }

    // Every newer fix wins so the radar keeps moving, unless it is much rougher than the one held:
    // a network fix arriving between GPS readings must not pull the position away. After
    // STALE_MS even a rough fix wins, because the old one may be from before the phone moved.
    private fun isBetter(next: Location, current: Location): Boolean {
        val newerBy = next.time - current.time
        return when {
            newerBy <= 0 -> false
            newerBy > STALE_MS -> true
            else -> next.accuracy <= maxOf(current.accuracy * ROUGHER_FACTOR, ROUGH_SLACK_M)
        }
    }

    private fun access(): Access = when {
        granted(Manifest.permission.ACCESS_FINE_LOCATION) -> Access.PRECISE
        granted(Manifest.permission.ACCESS_COARSE_LOCATION) -> Access.APPROXIMATE
        else -> Access.NONE
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

// The rescuer's own position in the form the location estimate takes.
fun Location.toGpsFix(): GpsFix = GpsFix(
    latitude = latitude,
    longitude = longitude,
    accuracyM = if (hasAccuracy()) accuracy.toDouble() else null,
    ageMin = ((System.currentTimeMillis() - time) / MS_PER_MIN).toInt().coerceAtLeast(0),
)

private const val UPDATE_MS = 1_000L
private const val STALE_MS = 10_000L
private const val ROUGHER_FACTOR = 2f
private const val ROUGH_SLACK_M = 20f
private const val PERMISSION_CHECK_MS = 2_000L
private const val MS_PER_MIN = 60_000L
