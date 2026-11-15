package com.flarehop.ui

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

enum class RadioState { READY, NO_PERMISSION, APPROXIMATE_LOCATION, BLUETOOTH_OFF, LOCATION_OFF }

data class RadioProblemText(val title: String, val hint: String, val action: String)

// Bluetooth, Location and permissions can all change while the app is open, so recheck.
@Composable
fun rememberRadioState(): RadioState {
    val context = LocalContext.current
    var state by remember { mutableStateOf(radioState(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(RADIO_CHECK_MS)
            state = radioState(context)
        }
    }
    return state
}

fun radioState(context: Context): RadioState {
    if (!granted(context, Manifest.permission.BLUETOOTH_SCAN) ||
        !granted(context, Manifest.permission.BLUETOOTH_ADVERTISE)
    ) {
        return RadioState.NO_PERMISSION
    }
    // BLUETOOTH_SCAN is not declared neverForLocation, so Android only delivers scan results
    // to apps with precise location. With Approximate it drops them without any error.
    if (!granted(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
        return RadioState.APPROXIMATE_LOCATION
    }
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    if (adapter?.isEnabled != true) return RadioState.BLUETOOTH_OFF
    // With Location switched off the scan keeps running but Android hands it nothing.
    val location = context.getSystemService(LocationManager::class.java)
    if (location?.isLocationEnabled != true) return RadioState.LOCATION_OFF
    return RadioState.READY
}

fun radioProblemText(state: RadioState): RadioProblemText? = when (state) {
    RadioState.READY -> null

    RadioState.NO_PERMISSION -> RadioProblemText(
        title = "Nearby devices is not allowed",
        hint = "Allow Nearby devices for Flarehop in Settings, then reopen the app.",
        action = "Open app settings",
    )

    RadioState.APPROXIMATE_LOCATION -> RadioProblemText(
        title = "Location is set to Approximate",
        hint = "Android only shows Bluetooth signals to apps with precise location. " +
            "Set Flarehop's location to Precise in Settings.",
        action = "Open app settings",
    )

    RadioState.BLUETOOTH_OFF -> RadioProblemText(
        title = "Bluetooth is off",
        hint = "Turn on Bluetooth to send and hear SOS signals.",
        action = "Open Bluetooth settings",
    )

    RadioState.LOCATION_OFF -> RadioProblemText(
        title = "Location is off",
        hint = "Android hears no Bluetooth signals while Location is off. " +
            "Turn on Location to hear SOS signals and receipts.",
        action = "Open Location settings",
    )
}

fun openRadioSettings(context: Context, state: RadioState) {
    val intent = when (state) {
        RadioState.READY -> return

        RadioState.NO_PERMISSION, RadioState.APPROXIMATE_LOCATION -> Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        )

        RadioState.BLUETOOTH_OFF -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)

        RadioState.LOCATION_OFF -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
    }
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
fun RadioBanner(state: RadioState, modifier: Modifier = Modifier) {
    val text = radioProblemText(state) ?: return
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp)) {
            Text(
                text = text.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = text.hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = { openRadioSettings(context, state) }) { Text(text.action) }
        }
    }
}

private fun granted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private const val RADIO_CHECK_MS = 2_000L
