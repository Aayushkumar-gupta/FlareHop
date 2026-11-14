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
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.flarehop.flare.Flare
import com.flarehop.flare.FlareService
import com.flarehop.flare.RealFlareEngine
import com.flarehop.flare.Role
import com.flarehop.flare.SosDetails
import com.flarehop.ui.theme.FlarehopTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FlarehopTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RadioTestScreen(modifier = Modifier.padding(innerPadding))
                }
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
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()

@Composable
fun RadioTestScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val engine = Flare.engine
    val mySos by engine.mySos.collectAsState()
    val known by engine.knownSos.collectAsState()
    var role by remember { mutableStateOf<Role?>(null) }
    var status by remember { mutableStateOf("Pick a role to start the beacon") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted[Manifest.permission.BLUETOOTH_ADVERTISE] == true) {
            FlareService.start(context)
            status = "Beacon service running"
        } else {
            status = "Nearby devices permission denied: the beacon cannot start"
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Radio test")
        Text("Phone ID: ${(engine as? RealFlareEngine)?.phoneId ?: "-"}")
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
    }
}
