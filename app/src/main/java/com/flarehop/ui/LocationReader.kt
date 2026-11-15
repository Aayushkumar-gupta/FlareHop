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

    private fun isBetter(next: Location, current: Location): Boolean = fixIsBetter(
        newerByMs = next.time - current.time,
        sameSource = next.provider == current.provider,
        nextAccuracyM = next.accuracy,
        currentAccuracyM = current.accuracy,
    )

    private fun access(): Access = when {
        granted(Manifest.permission.ACCESS_FINE_LOCATION) -> Access.PRECISE
        granted(Manifest.permission.ACCESS_COARSE_LOCATION) -> Access.APPROXIMATE
        else -> Access.NONE
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

// A much newer fix always wins, because the old one may be from before the phone moved. Close in
// time, the newest fix from the same source wins: GPS reports every 5 s with accuracy that
// wobbles by a few metres, and keeping only the most accurate one froze a moving phone's position
// for up to 2 minutes. A fix from another source wins only when it is more accurate, so a rough
// network fix cannot replace GPS.
fun fixIsBetter(
    newerByMs: Long,
    sameSource: Boolean,
    nextAccuracyM: Float,
    currentAccuracyM: Float,
): Boolean = when {
    newerByMs > STALE_MS -> true
    newerByMs < -STALE_MS -> false
    sameSource -> newerByMs > 0
    else -> nextAccuracyM <= currentAccuracyM
}

// The rescuer's own position in the form the location estimate takes.
fun Location.toGpsFix(): GpsFix = GpsFix(
    latitude = latitude,
    longitude = longitude,
    accuracyM = if (hasAccuracy()) accuracy.toDouble() else null,
    ageMin = ((System.currentTimeMillis() - time) / MS_PER_MIN).toInt().coerceAtLeast(0),
)

private const val UPDATE_MS = 5_000L
private const val STALE_MS = 120_000L
private const val PERMISSION_CHECK_MS = 2_000L
private const val MS_PER_MIN = 60_000L
