package com.perde.oynatici

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFFFF7A45)
val AccentSoft = Color(0xFFFFB38F)
val Bg = Color(0xFF0E0F13)
val Surf = Color(0xFF17191F)
val Surf2 = Color(0xFF22252D)

private val scheme = darkColorScheme(
    primary = Accent, onPrimary = Color(0xFF1A0E08), primaryContainer = Color(0xFF5A2A14), onPrimaryContainer = AccentSoft,
    secondary = AccentSoft, background = Bg, onBackground = Color(0xFFECECEF), surface = Surf, onSurface = Color(0xFFECECEF),
    surfaceVariant = Surf2, onSurfaceVariant = Color(0xFFB4B7C0), surfaceContainer = Surf, surfaceContainerHigh = Surf2,
    surfaceContainerLow = Surf, surfaceContainerHighest = Color(0xFF2A2D36), outline = Color(0xFF3A3D46)
)

@Composable
fun PerdeTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, content = content)
