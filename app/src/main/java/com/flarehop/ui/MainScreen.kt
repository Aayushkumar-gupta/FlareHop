package com.flarehop.ui

import androidx.compose.foundation.layout.Column
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
import com.flarehop.ui.theme.MeanderBorder

@Composable
fun MainScreen(flare: FlareViewModel) {
    val tab by flare.tab.collectAsState()
    val findTarget by flare.findTarget.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            Column {
                MeanderBorder()
                NavigationBar {
                    Tab.entries.forEach { entry ->
                        TabItem(label = tabLabel(entry), selected = tab == entry) {
                            flare.openTab(entry)
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)
        val target = findTarget
        when {
            tab == Tab.RESCUER && target != null ->
                FindScreen(flare = flare, messageId = target, modifier = modifier)

            tab == Tab.RESCUER -> SosListScreen(flare = flare, modifier = modifier)

            tab == Tab.NEARBY -> NearbyScreen(flare = flare, modifier = modifier)

            else -> VictimScreen(flare = flare, modifier = modifier)
        }
    }
}

private fun tabLabel(tab: Tab) = when (tab) {
    Tab.VICTIM -> "Victim"
    Tab.NEARBY -> "Nearby"
    Tab.RESCUER -> "Rescuer"
}

// Text sits in the icon slot because the icon library is not on the Build list,
// and a large word is easier to hit than a small icon anyway.
@Composable
private fun RowScope.TabItem(label: String, selected: Boolean, onClick: () -> Unit) {
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
