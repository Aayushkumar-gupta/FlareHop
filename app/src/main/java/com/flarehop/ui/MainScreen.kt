package com.flarehop.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flarehop.flare.Role

// true  = development: screens run on FakeEngine sample data, Bluetooth stays off.
// false = test: screens run on the real engine, with real Bluetooth scanning and advertising.
const val DEVELOPMENT = false

@Composable
fun MainScreen(flare: FlareViewModel) {
    val role by flare.role.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { ModeBanner() },
        bottomBar = {
            NavigationBar {
                RoleTab(label = "Victim", selected = role == Role.VICTIM) {
                    flare.selectRole(Role.VICTIM)
                }
                RoleTab(label = "Nearby", selected = role == Role.NEARBY) {
                    flare.selectRole(Role.NEARBY)
                }
                RoleTab(label = "Rescuer", selected = role == Role.RESCUER) {
                    flare.selectRole(Role.RESCUER)
                }
            }
        },
    ) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)
        when (role) {
            Role.RESCUER -> SosListScreen(engine = flare.engine, modifier = modifier)
            Role.NEARBY -> NearbyScreen(engine = flare.engine, modifier = modifier)
            Role.VICTIM -> VictimScreen(engine = flare.engine, modifier = modifier)
        }
    }
}

@Composable
private fun ModeBanner() {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = if (DEVELOPMENT) colors.tertiaryContainer else colors.primaryContainer,
        contentColor = if (DEVELOPMENT) colors.onTertiaryContainer else colors.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = if (DEVELOPMENT) {
                "DEVELOPMENT = true · sample data (FakeEngine)"
            } else {
                "DEVELOPMENT = false · real Bluetooth"
            },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(vertical = 6.dp),
        )
    }
}

// Text sits in the icon slot because the icon library is not on the Build list,
// and a large word is easier to hit than a small icon anyway.
@Composable
private fun RowScope.RoleTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
        },
    )
}
