package com.flarehop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// One fixed dark theme on every phone. Wallpaper colours made each demo phone look different,
// and the night palette is easier on a victim's eyes and battery.
private val NightColorScheme = darkColorScheme(
    primary = Bronze,
    onPrimary = NightSea,
    primaryContainer = BronzeDeep,
    onPrimaryContainer = Parchment,
    secondary = Aegean,
    onSecondary = NightSea,
    secondaryContainer = AegeanDeep,
    onSecondaryContainer = Parchment,
    tertiary = EmberCore,
    onTertiary = NightSea,
    error = Ember,
    onError = NightSea,
    errorContainer = Ash,
    onErrorContainer = EmberMist,
    background = NightSea,
    onBackground = Parchment,
    surface = NightSea,
    onSurface = Parchment,
    surfaceVariant = Tide,
    onSurfaceVariant = Mist,
    surfaceContainerLowest = NightSea,
    surfaceContainerLow = DeepSea,
    surfaceContainer = DeepSea,
    surfaceContainerHigh = Tide,
    surfaceContainerHighest = TideHigh,
    outline = Rope,
    outlineVariant = Tide,
)

@Composable
fun FlarehopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NightColorScheme,
        typography = Typography,
        content = content,
    )
}
