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
import kotlinx.coroutines.launch

class LocationReader(private val context: Context, scope: CoroutineScope) {
    private val manager = context.getSystemService(LocationManager::class.java)
    private val listener = LocationListener { offer(it) }
    private var started = false

    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location.asStateFlow()

    init {
        // The permission dialog opens at app start, so the answer can come after this runs.
        scope.launch {
            while (!hasPermission()) delay(PERMISSION_CHECK_MS)
            start()
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
        if (started) manager.removeUpdates(listener)
        started = false
    }

    @SuppressLint("MissingPermission")
    private fun start() {
        PROVIDERS.filter { it in manager.allProviders }.forEach { provider ->
            manager.getLastKnownLocation(provider)?.let(::offer)
            manager.requestLocationUpdates(
                provider,
                UPDATE_MS,
                0f,
                listener,
                Looper.getMainLooper(),
            )
        }
        started = true
    }

    private fun offer(next: Location) {
        val current = _location.value
        if (current == null || isBetter(next, current)) _location.value = next
    }

    // A much newer fix always wins, because the old one may be from before the phone moved.
    // Close in time, the more accurate one wins, so a rough network fix cannot replace GPS.
    private fun isBetter(next: Location, current: Location): Boolean {
        val newerBy = next.time - current.time
        return when {
            newerBy > STALE_MS -> true
            newerBy < -STALE_MS -> false
            else -> next.accuracy <= current.accuracy
        }
    }

    private fun hasPermission(): Boolean = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ).any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

private val PROVIDERS = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
private const val UPDATE_MS = 5_000L
private const val STALE_MS = 120_000L
private const val PERMISSION_CHECK_MS = 2_000L
private const val MS_PER_MIN = 60_000L
