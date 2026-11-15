package com.flarehop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun DemoSettingsScreen(flare: FlareViewModel, modifier: Modifier = Modifier) {
    val override by flare.batteryOverride.collectAsState()
    val continuous by flare.listenContinuously.collectAsState()
    val keepScreenOn by flare.keepScreenOn.collectAsState()
    var confirmReset by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Demo settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        BatteryOverride(override = override, onChange = flare::setBatteryOverride)
        HorizontalDivider()

        Button(
            onClick = flare::fakeKnock,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(text = "Fake a knock", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            text = "Puts a knock into this phone's SOS, for when the hall is too loud to " +
                "test live.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider()

        SwitchRow(
            title = "Listen continuously",
            hint = "Normally the microphone listens 5 seconds in every 15.",
            checked = continuous,
            onChange = flare::setListenContinuously,
        )
        SwitchRow(
            title = "Keep screen on",
            hint = "Stops the screen from turning off during the demo.",
            checked = keepScreenOn,
            onChange = flare::setKeepScreenOn,
        )
        HorizontalDivider()

        OutlinedButton(
            onClick = { confirmReset = true },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(text = "Reset this phone", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            text = "New phone ID; forgets every SOS, receipt and log entry.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset this phone?") },
            text = {
                Text("Any SOS this phone is sending stops, and everything it heard is lost.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    flare.reset()
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Keep") }
            },
        )
    }
}

@Composable
private fun BatteryOverride(override: Int?, onChange: (Int?) -> Unit) {
    // The slider moves freely and only sends its level when let go, so the engine does not get
    // a new battery value for every pixel dragged.
    var dragging by remember(override) { mutableFloatStateOf((override ?: 50).toFloat()) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SwitchRow(
            title = "Override battery",
            hint = if (override == null) {
                "Using the phone's real battery level."
            } else {
                "Rules use $override% instead of the real level. Below 10% the SOS hands off."
            },
            checked = override != null,
            onChange = { on -> onChange(if (on) dragging.roundToInt() else null) },
        )
        if (override != null) {
            Text(
                text = "${dragging.roundToInt()}%",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Slider(
                value = dragging,
                onValueChange = { dragging = it },
                onValueChangeFinished = { onChange(dragging.roundToInt()) },
                valueRange = 0f..100f,
            )
        }
    }
}

@Composable
private fun SwitchRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
