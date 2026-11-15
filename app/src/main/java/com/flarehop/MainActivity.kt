package com.flarehop

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flarehop.flare.FlareService
import com.flarehop.ui.FlareViewModel
import com.flarehop.ui.MainScreen
import com.flarehop.ui.theme.FlarehopTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FlarehopTheme {
                StartBeacon()
                val flare: FlareViewModel = viewModel()
                MainScreen(flare)
            }
        }
    }
}

private fun radioPermissions(): Array<String> = buildList {
    add(Manifest.permission.BLUETOOTH_ADVERTISE)
    add(Manifest.permission.BLUETOOTH_SCAN)
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    add(Manifest.permission.RECORD_AUDIO)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()

@Composable
private fun StartBeacon() {
    val context = LocalContext.current
    val askBackground = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { askBatteryExemption(context) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted[Manifest.permission.BLUETOOTH_ADVERTISE] == true) FlareService.start(context)
        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true && firstWakeAsk(context)) {
            askBackground.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }
    LaunchedEffect(Unit) { launcher.launch(radioPermissions()) }
}

// Waking with the app closed needs location "Allow all the time" (Android gives Bluetooth scan
// results to a background app only then) and no battery limits (so Android lets the woken app
// start its service). Both are asked once; Settings can change them later.
private fun firstWakeAsk(context: Context): Boolean {
    val prefs = context.getSharedPreferences(WAKE_PREFS, Context.MODE_PRIVATE)
    if (prefs.getBoolean(KEY_WAKE_ASKED, false)) return false
    prefs.edit { putBoolean(KEY_WAKE_ASKED, true) }
    return true
}

private fun askBatteryExemption(context: Context) {
    val power = context.getSystemService(PowerManager::class.java) ?: return
    if (power.isIgnoringBatteryOptimizations(context.packageName)) return
    val ask = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        .setData("package:${context.packageName}".toUri())
    runCatching { context.startActivity(ask) }
}

private const val WAKE_PREFS = "flarehop_wake"
private const val KEY_WAKE_ASKED = "asked"
