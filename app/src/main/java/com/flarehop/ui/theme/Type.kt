package com.flarehop.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Headings in the phone's own serif for a carved, classical feel, with no font to download;
// everything a person has to read quickly stays in the plain sans.
private val Default = Typography()
private val Carved = FontFamily.Serif

val Typography = Typography(
    displayLarge = Default.displayLarge.copy(fontFamily = Carved, fontWeight = FontWeight.Bold),
    displayMedium = Default.displayMedium.copy(fontFamily = Carved, fontWeight = FontWeight.Bold),
    displaySmall = Default.displaySmall.copy(fontFamily = Carved, fontWeight = FontWeight.Bold),
    headlineLarge = Default.headlineLarge.copy(fontFamily = Carved),
    headlineMedium = Default.headlineMedium.copy(fontFamily = Carved),
    headlineSmall = Default.headlineSmall.copy(fontFamily = Carved),
    titleLarge = Default.titleLarge.copy(fontFamily = Carved),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
)

// Spaced capitals in bronze, for the short epic lines above the plain instructions.
val Inscription = TextStyle(
    fontFamily = Carved,
    fontWeight = FontWeight.SemiBold,
    fontSize = 20.sp,
    lineHeight = 28.sp,
    letterSpacing = 4.sp,
)
