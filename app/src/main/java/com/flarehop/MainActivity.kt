package com.flarehop

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flarehop.flare.FlareService
import com.flarehop.ui.DEVELOPMENT
import com.flarehop.ui.FlareViewModel
import com.flarehop.ui.MainScreen
import com.flarehop.ui.theme.FlarehopTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FlarehopTheme {
                // Real Bluetooth only in test mode; development runs on sample data.
                if (!DEVELOPMENT) StartBeacon()
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
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()

@Composable
private fun StartBeacon() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted[Manifest.permission.BLUETOOTH_ADVERTISE] == true) FlareService.start(context)
    }
    LaunchedEffect(Unit) { launcher.launch(radioPermissions()) }
}
