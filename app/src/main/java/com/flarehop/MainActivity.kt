package com.flarehop

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flarehop.flare.Flare
import com.flarehop.flare.FlareService
import com.flarehop.flare.RealFlareEngine
import com.flarehop.flare.Role
import com.flarehop.flare.SosDetails
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

private val testSos = SosDetails(
    people = 2,
    injured = true,
    trapped = true,
    water = false,
    medical = false,
    fire = false,
)

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

@Composable
fun RadioTestScreen(modifier: Modifier = Modifier, onOpenApp: () -> Unit = {}) {
    val context = LocalContext.current
    val engine = Flare.engine
    val mySos by engine.mySos.collectAsState()
    val known by engine.knownSos.collectAsState()
    var role by remember { mutableStateOf<Role?>(null) }
    var status by remember { mutableStateOf("Pick a role to start the beacon") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        val canScan = granted[Manifest.permission.BLUETOOTH_SCAN] == true &&
            granted[Manifest.permission.ACCESS_FINE_LOCATION] == true
        status = when {
            granted[Manifest.permission.BLUETOOTH_ADVERTISE] != true ->
                "Nearby devices permission denied: the beacon cannot start"

            !canScan -> "Broadcasting only: allow precise Location to hear other phones"

            else -> "Beacon running: broadcasting and listening"
        }
        if (granted[Manifest.permission.BLUETOOTH_ADVERTISE] == true) {
            FlareService.start(context)
            onOpenApp()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Radio test")
        Text("Phone ID: ${(engine as? RealFlareEngine)?.phoneId ?: "-"}")
        Button(onClick = onOpenApp, modifier = Modifier.fillMaxWidth()) {
            Text("Open Flarehop")
        }
        Role.entries.forEach { choice ->
            OutlinedButton(
                onClick = {
                    role = choice
                    engine.setRole(choice)
                    permissionLauncher.launch(radioPermissions())
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (choice == role) "${choice.name} (selected)" else choice.name)
            }
        }
        Button(
            onClick = { engine.sendSos(testSos, null) },
            enabled = role == Role.VICTIM,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Send test SOS: 2 people, trapped, injured")
        }
        Button(
            onClick = { engine.cancelSos() },
            enabled = mySos != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Cancel SOS")
        }
        OutlinedButton(
            onClick = {
                FlareService.stop(context)
                status = "Beacon service stopped"
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Stop beacon service")
        }
        Text(status)
        Text(
            mySos?.let { "My SOS ${it.record.messageId}: ${it.status}, acked ${it.record.acked}" }
                ?: "My SOS: none",
        )
        Text("SOS heard: ${known.size}")
        known.forEach { sos ->
            Text(
                "SOS ${sos.messageId} from ${sos.originId} via ${sos.carrierId}: " +
                    "${sos.lastRssi} dBm, battery ${sos.battery ?: "?"}, " +
                    "acked ${sos.acked}, cancelled ${sos.cancelled}",
            )
        }
    }
}
