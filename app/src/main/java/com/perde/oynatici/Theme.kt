package com.perde.oynatici

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Canlı değişebilen tema (vurgu rengi + OLED siyahı). */
object ThemeState {
    val ACCENTS = listOf(
        0xFF3E8BFF to "Mavi", 0xFFFF7A45 to "Turuncu", 0xFF9D7BFF to "Mor",
        0xFF2FD18B to "Yeşil", 0xFFFF5C9A to "Pembe", 0xFFFFB020 to "Altın"
    )
    var accentIdx by mutableIntStateOf(0)
    var amoled by mutableStateOf(false)
    fun load() { accentIdx = Prefs.accent; amoled = Prefs.amoled }
}

val Accent: Color get() = Color(ThemeState.ACCENTS.getOrElse(ThemeState.accentIdx) { ThemeState.ACCENTS[0] }.first)
val AccentSoft: Color get() = lerp(Accent, Color.White, 0.45f)
val AccentDeep: Color get() = lerp(Accent, Color.Black, 0.78f)
val Bg: Color get() = if (ThemeState.amoled) Color.Black else Color(0xFF07080B)
val Surf: Color get() = if (ThemeState.amoled) Color(0xFF111114) else Color(0xFF16181E)
val Surf2: Color get() = if (ThemeState.amoled) Color(0xFF1C1C21) else Color(0xFF23262E)
val Muted = Color(0xFFB4B7C0)

@Composable
fun PerdeTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Accent, onPrimary = Color(0xFF14100E), primaryContainer = AccentDeep, onPrimaryContainer = AccentSoft,
        secondary = AccentSoft, secondaryContainer = AccentDeep, onSecondaryContainer = AccentSoft,
        background = Bg, onBackground = Color(0xFFECECEF), surface = Surf, onSurface = Color(0xFFECECEF),
        surfaceVariant = Surf2, onSurfaceVariant = Muted, surfaceContainer = Surf, surfaceContainerHigh = Surf2,
        surfaceContainerLow = Surf, surfaceContainerLowest = Bg, surfaceContainerHighest = lerp(Surf2, Color.White, 0.06f),
        outline = Color(0xFF3A3D46), outlineVariant = Color(0xFF2A2D36)
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Premium cam görünümlü yuvarlak buton (basınca küçülür, titreşim verir). */
@Composable
fun GlassButton(
    icon: ImageVector, desc: String?, size: Dp = 44.dp, iconSize: Dp = 22.dp,
    active: Boolean = false, enabled: Boolean = true, onClick: () -> Unit
) {
    val view = LocalView.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.86f else 1f, label = "glass")
    Box(
        Modifier.size(size).scale(sc).clip(CircleShape)
            .background(
                if (active) Brush.verticalGradient(listOf(Accent.copy(alpha = 0.42f), Accent.copy(alpha = 0.22f)))
                else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.17f), Color.White.copy(alpha = 0.06f)))
            )
            .border(1.dp, if (active) Accent.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.2f), CircleShape)
            .clickable(src, ripple(), enabled = enabled) { haptic(view); onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, desc, tint = when { !enabled -> Color.White.copy(alpha = 0.35f); active -> Color.White; else -> Color.White }, modifier = Modifier.size(iconSize))
    }
}

fun haptic(view: android.view.View) {
    if (Prefs.haptics) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
}
