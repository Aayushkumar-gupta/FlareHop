package com.flarehop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flarehop.flare.FlareEngine
import com.flarehop.flare.SosStatus

@Composable
fun NearbyScreen(engine: FlareEngine, modifier: Modifier = Modifier) {
    val mySos by engine.mySos.collectAsState()
    val group by engine.group.collectAsState()

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Nearby phone",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = when (mySos?.status) {
                null -> "Looking for an SOS close by"
                SosStatus.STANDING_BY -> "Backing up an SOS"
                SosStatus.SENDING -> "Carrying an SOS"
                SosStatus.HANDED_OFF -> "SOS handed to another phone"
            },
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        if (mySos != null) {
            Text(
                text = if (group.size ==
                    1
                ) {
                    "1 phone in this group"
                } else {
                    "${group.size} phones in this group"
                },
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Text(
            text = "Keep this phone close to the person who needs help.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}
