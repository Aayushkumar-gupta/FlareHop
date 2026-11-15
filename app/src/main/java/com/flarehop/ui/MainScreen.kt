package com.flarehop.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.flarehop.flare.Role

// true  = development: screens run on FakeEngine sample data, Bluetooth stays off.
// false = test: screens run on the real engine, with real Bluetooth scanning and advertising.
const val DEVELOPMENT = false

@Composable
fun MainScreen(flare: FlareViewModel) {
    val tab by flare.tab.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                RoleTab(label = "Survivor", selected = tab == Role.SURVIVOR) {
                    flare.openTab(Role.SURVIVOR)
                }
                RoleTab(label = "Rescuer", selected = tab == Role.RESCUER) {
                    flare.openTab(Role.RESCUER)
                }
            }
        },
    ) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)
        when (tab) {
            Role.RESCUER -> SosListScreen(flare = flare, modifier = modifier)
            Role.SURVIVOR -> VictimScreen(flare = flare, modifier = modifier)
        }
    }
}

// Text sits in the icon slot because the icon library is not on the Build list,
// and a large word is easier to hit than a small icon anyway.
@Composable
private fun RowScope.RoleTab(label: String, selected: Boolean, onClick: () -> Unit) {
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
