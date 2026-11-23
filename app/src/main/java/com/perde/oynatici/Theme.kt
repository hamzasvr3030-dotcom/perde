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
import androidx.compose.ui.graphics.luminance
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
    /** -1: seçili arayüz stilinin kendi rengi */
    var accentIdx by mutableIntStateOf(-1)
    var amoled by mutableStateOf(false)
    var styleIdx by mutableIntStateOf(0)
    /** Çalan şarkının kapağından gelen renk (açıksa vurgu rengi olur). */
    var dyn by mutableStateOf<Color?>(null)
    fun load() { accentIdx = Prefs.accent; amoled = Prefs.amoled; styleIdx = Prefs.uiStyle }
    val style: UiStyle get() = UiStyle.ALL.getOrElse(styleIdx) { UiStyle.ALL[0] }
}

/**
 * Arayüz stili: zemin/yüzey/yazı renkleri, vurgu, köşe yuvarlaklığı, başlık kalınlığı, alt çubuk biçimi.
 * Ayarlar › Tema'dan canlı değişir.
 */
data class UiStyle(
    val name: String, val desc: String, val dark: Boolean,
    val bg: Long, val surf: Long, val surf2: Long, val ink: Long, val muted: Long, val accent: Long,
    val grad: List<Long> = emptyList(),
    val radius: Int = 20, val titleWeight: Int = 900, val titleSize: Int = 0,
    val floatNav: Boolean = false,
    val play: PlayLook = PlayLook(),
    /** Renk geçişli oynat düğmesinin ikinci rengi (btn = 4). */
    val btn2: Long = 0xFFFFB35C
) {
    companion object {
        val ALL = listOf(
            UiStyle("Mavi Gece", "Koyu, mavi vurgu, kalın başlıklar", true, 0xFF07080B, 0xFF16181E, 0xFF23262E, 0xFFECECEF, 0xFFB4B7C0, 0xFF3E8BFF, play = PlayLook()),
            UiStyle("Cam", "Mavi geçişli zemin, turkuaz vurgu, yüzen alt çubuk", true, 0xFF08131F, 0xFF12263A, 0xFF1B3550, 0xFFEEF8FF, 0xFFA9C4D9, 0xFF2DD4BF,
                grad = listOf(0xFF123C70, 0xFF0A1A30, 0xFF06343A), radius = 26, floatNav = true, play = PlayLook(art = 1, bg = 1, btn = 4, seek = 3, center = true), btn2 = 0xFF3B82F6),
            UiStyle("Siyah Sade", "Saf siyah, köşeli, ince başlık, turuncu", true, 0xFF000000, 0xFF0C0C0C, 0xFF181818, 0xFFEAEAEA, 0xFF8A8A8A, 0xFFFF7A1A,
                radius = 6, titleWeight = 300, titleSize = 40, play = PlayLook(art = 2, bg = 2, btn = 2, seek = 2, glass = false)),
            UiStyle("Açık Pastel", "Beyaz zemin, yumuşak renkler", false, 0xFFF4F5FB, 0xFFFFFFFF, 0xFFE9EAF4, 0xFF1D1F2B, 0xFF6E7389, 0xFF5B5BD6,
                radius = 26, floatNav = true, play = PlayLook(art = 0, bg = 3, btn = 4, seek = 1, center = true, glass = false)),
            UiStyle("Kâğıt", "Sıcak krem tonlar, kiremit vurgu", false, 0xFFF5EFE6, 0xFFFFFBF5, 0xFFEADFCF, 0xFF2B2118, 0xFF7D6E5E, 0xFFB5562B,
                radius = 14, titleWeight = 800, play = PlayLook(art = 2, bg = 3, btn = 3, seek = 1, glass = false)),
            UiStyle("Okyanus", "Lacivert-turkuaz geçiş", true, 0xFF06141F, 0xFF0D2233, 0xFF143247, 0xFFE6F6FF, 0xFF93B4C8, 0xFF22D3EE,
                grad = listOf(0xFF0A2A40, 0xFF06141F), radius = 22, play = PlayLook(art = 3, bg = 1, btn = 1, seek = 1, center = true)),
            UiStyle("Orman", "Koyu yeşil, açık yeşil vurgu", true, 0xFF0A120D, 0xFF132019, 0xFF1C2E23, 0xFFE8F3EC, 0xFF9DB5A6, 0xFF84CC16,
                radius = 18, titleWeight = 800, play = PlayLook(art = 0, bg = 2, btn = 1, seek = 1, glass = false)),
            UiStyle("Gün Batımı", "Mor-turuncu geçiş, mercan vurgu", true, 0xFF1A0E1A, 0xFF2A1726, 0xFF3A2133, 0xFFFFF1EC, 0xFFD9B3B0, 0xFFFF6B5A,
                grad = listOf(0xFF4A1A3A, 0xFF1A0E1A, 0xFF2B1608), radius = 24, floatNav = true, play = PlayLook(art = 1, bg = 0, btn = 4, seek = 3, center = true)),
            UiStyle("Neon", "Simsiyah zemin, parlak pembe", true, 0xFF05030A, 0xFF120C1C, 0xFF1D1430, 0xFFF5F0FF, 0xFFA99BC4, 0xFFFF2BD6,
                radius = 12, play = PlayLook(art = 3, bg = 2, btn = 2, seek = 1, center = true, glass = false)),
            UiStyle("Grafit", "Gri tonlar, altın vurgu", true, 0xFF1B1C1F, 0xFF26272B, 0xFF313237, 0xFFF0F0F2, 0xFFA3A4AB, 0xFFE8B931,
                radius = 16, titleWeight = 700, play = PlayLook(art = 2, bg = 2, btn = 3, seek = 2)),
            // ---- karma stiller: mevcutların beğenilen özelliklerinden ----
            UiStyle("Lavanta", "Açık lila zemin, mor vurgu, plak çalar", false, 0xFFF6F3FB, 0xFFFFFFFF, 0xFFECE6F6, 0xFF22192F, 0xFF7A6E8C, 0xFF8B5CF6,
                radius = 24, floatNav = true, play = PlayLook(art = 1, bg = 3, btn = 4, seek = 3, center = true, glass = false), btn2 = 0xFFEC4899),
            UiStyle("Gece Yarısı", "Lacivert geçiş, altın vurgu, parlayan kapak", true, 0xFF0B1020, 0xFF151B30, 0xFF1F2740, 0xFFEEF1FA, 0xFF9AA3BF, 0xFFF5C451,
                grad = listOf(0xFF1A2350, 0xFF0B1020), radius = 20, floatNav = true, play = PlayLook(art = 3, bg = 1, btn = 3, seek = 3, center = true)),
            UiStyle("Mercan", "Sıcak açık zemin, mercan vurgu", false, 0xFFFFF5F2, 0xFFFFFFFF, 0xFFFBE4DD, 0xFF2A1612, 0xFF8A6A62, 0xFFFF6B5A,
                radius = 22, floatNav = true, play = PlayLook(art = 0, bg = 3, btn = 4, seek = 1, center = true, glass = false)),
            UiStyle("Kutup Işığı", "Yeşil-mor geçiş, nane vurgu, yüzen alt çubuk", true, 0xFF07110F, 0xFF10201C, 0xFF18302A, 0xFFE9FFF8, 0xFF98BFB3, 0xFF34D399,
                grad = listOf(0xFF0E3B35, 0xFF07110F, 0xFF1E1440), radius = 26, floatNav = true, play = PlayLook(art = 1, bg = 1, btn = 4, seek = 3, center = true), btn2 = 0xFF8B5CF6),
            UiStyle("Kahve", "Koyu kahve, karamel vurgu, köşeli kapak", true, 0xFF15100C, 0xFF211912, 0xFF2D231A, 0xFFF5ECE3, 0xFFB4A291, 0xFFD9A066,
                radius = 14, titleWeight = 800, play = PlayLook(art = 2, bg = 2, btn = 3, seek = 1, glass = false)),
            UiStyle("Buz", "Açık soğuk tonlar, ince büyük başlık", false, 0xFFF2F7FB, 0xFFFFFFFF, 0xFFE3EDF5, 0xFF0F1C2A, 0xFF64788C, 0xFF0EA5E9,
                radius = 18, titleWeight = 300, titleSize = 40, play = PlayLook(art = 2, bg = 3, btn = 2, seek = 2, glass = false)),
            UiStyle("Bordo", "Şarap kırmızısı geçiş, gül vurgu", true, 0xFF140A0D, 0xFF22121A, 0xFF301A24, 0xFFFBEFF2, 0xFFC4A3AE, 0xFFF43F5E,
                grad = listOf(0xFF3A0F1E, 0xFF140A0D), radius = 20, play = PlayLook(art = 3, bg = 0, btn = 1, seek = 3, center = true)),
            UiStyle("Retro", "Mor zemin, sarı vurgu, kalın başlık", true, 0xFF1A1625, 0xFF252036, 0xFF312A47, 0xFFFFF8E7, 0xFFBDB3D6, 0xFFFACC15,
                radius = 8, titleWeight = 900, titleSize = 40, play = PlayLook(art = 2, bg = 2, btn = 3, seek = 1, center = true, glass = false)),
            UiStyle("Nane", "Açık nane yeşili, yumuşak köşeler, plak", false, 0xFFF1FAF6, 0xFFFFFFFF, 0xFFDDF2E8, 0xFF0F2A20, 0xFF5C7D70, 0xFF10B981,
                radius = 26, floatNav = true, play = PlayLook(art = 1, bg = 3, btn = 1, seek = 3, center = true, glass = false)),
            UiStyle("Uzay", "Derin siyah, mor-camgöbeği geçiş", true, 0xFF030308, 0xFF0E0E1A, 0xFF181828, 0xFFF2F2FF, 0xFF9C9CC0, 0xFF818CF8,
                grad = listOf(0xFF151536, 0xFF030308, 0xFF031A24), radius = 22, floatNav = true, play = PlayLook(art = 3, bg = 1, btn = 4, seek = 3, center = true), btn2 = 0xFF22D3EE),
        )
    }
}

/**
 * Oynatıcı görünümü (her stilde farklı).
 * art: 0 yuvarlak köşe, 1 dönen plak, 2 köşeli kare, 3 parlayan kapak
 * bg: 0 kapak renginden koyu geçiş, 1 bulanık kapak, 2 stil zemini + renk ışıltısı, 3 açık renk
 * btn: 0 beyaz daire, 1 vurgu daire, 2 halka, 3 yuvarlak kare, 4 renk geçişli daire
 * seek: 0 ince beyaz, 1 kalın renkli, 2 kıl gibi ince, 3 ses dalgası
 */
data class PlayLook(val art: Int = 0, val bg: Int = 0, val btn: Int = 0, val seek: Int = 0, val center: Boolean = false, val glass: Boolean = true)

private val St get() = ThemeState.style
/** Müzik çaların üstündeki yazı/simge rengi */
val NpFg: Color get() = if (St.play.bg == 3) Ink else Color.White
/** GlassButton içindeki simge rengi (açık zeminli oynatıcıda koyu) */
val LocalGlassFg = androidx.compose.runtime.staticCompositionLocalOf { Color.White }
val Dark: Boolean get() = St.dark
val Accent: Color get() = ThemeState.dyn ?: ThemeState.ACCENTS.getOrNull(ThemeState.accentIdx)?.let { Color(it.first) } ?: Color(St.accent)
val AccentSoft: Color get() = lerp(Accent, if (Dark) Color.White else Color.Black, 0.45f)
val AccentDeep: Color get() = if (Dark) lerp(Accent, Color.Black, 0.78f) else lerp(Accent, Color.White, 0.82f)
private val oled get() = ThemeState.amoled && St.dark
val Bg: Color get() = if (oled) Color.Black else Color(St.bg)
val Surf: Color get() = if (oled) Color(0xFF111114) else Color(St.surf)
val Surf2: Color get() = if (oled) Color(0xFF1C1C21) else Color(St.surf2)
/** Zemin üstündeki yazı rengi (açık stillerde koyu). */
val Ink: Color get() = Color(St.ink)
val Muted: Color get() = Color(St.muted)
/** Çizgi/çerçeve rengi */
val Line: Color get() = Ink.copy(alpha = if (Dark) 0.07f else 0.10f)
val BgBrush: Brush get() = if (St.grad.isEmpty() || oled) androidx.compose.ui.graphics.SolidColor(Bg) else Brush.verticalGradient(St.grad.map { Color(it) })
val Rad: Dp get() = St.radius.dp

@Composable
fun PerdeTheme(content: @Composable () -> Unit) {
    val onAcc = if (Accent.luminance() > 0.55f) Color(0xFF14100E) else Color.White
    val scheme = if (Dark) darkColorScheme(
        primary = Accent, onPrimary = Color(0xFF14100E), primaryContainer = AccentDeep, onPrimaryContainer = AccentSoft,
        secondary = AccentSoft, secondaryContainer = AccentDeep, onSecondaryContainer = AccentSoft,
        background = Bg, onBackground = Ink, surface = Surf, onSurface = Ink,
        surfaceVariant = Surf2, onSurfaceVariant = Muted, surfaceContainer = Surf, surfaceContainerHigh = Surf2,
        surfaceContainerLow = Surf, surfaceContainerLowest = Bg, surfaceContainerHighest = lerp(Surf2, Color.White, 0.06f),
        outline = lerp(Surf2, Ink, 0.15f), outlineVariant = lerp(Surf2, Ink, 0.07f)
    ) else androidx.compose.material3.lightColorScheme(
        primary = Accent, onPrimary = onAcc, primaryContainer = AccentDeep, onPrimaryContainer = lerp(Accent, Color.Black, 0.5f),
        secondary = Accent, secondaryContainer = AccentDeep, onSecondaryContainer = lerp(Accent, Color.Black, 0.5f),
        background = Bg, onBackground = Ink, surface = Surf, onSurface = Ink,
        surfaceVariant = Surf2, onSurfaceVariant = Muted, surfaceContainer = Surf, surfaceContainerHigh = Surf2,
        surfaceContainerLow = Surf, surfaceContainerLowest = Bg, surfaceContainerHighest = lerp(Surf2, Color.Black, 0.04f),
        outline = lerp(Surf2, Ink, 0.25f), outlineVariant = lerp(Surf2, Ink, 0.1f)
    )
    val r = Rad
    val shapes = androidx.compose.material3.Shapes(
        small = androidx.compose.foundation.shape.RoundedCornerShape(r * 0.5f), medium = androidx.compose.foundation.shape.RoundedCornerShape(r * 0.7f),
        large = androidx.compose.foundation.shape.RoundedCornerShape(r), extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(r * 1.3f))
    // durum çubuğu simgeleri: açık stillerde koyu
    val view = LocalView.current
    val dark = Dark
    androidx.compose.runtime.SideEffect {
        (view.context as? android.app.Activity)?.window?.let { w ->
            androidx.core.view.WindowCompat.getInsetsController(w, view).apply { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark }
        }
    }
    MaterialTheme(colorScheme = scheme, shapes = shapes, content = content)
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
    val fg = LocalGlassFg.current
    Box(
        Modifier.size(size).scale(sc).clip(CircleShape)
            .background(
                if (active) Brush.verticalGradient(listOf(Accent.copy(alpha = 0.42f), Accent.copy(alpha = 0.22f)))
                else Brush.verticalGradient(listOf(fg.copy(alpha = 0.17f), fg.copy(alpha = 0.06f)))
            )
            .border(1.dp, if (active) Accent.copy(alpha = 0.8f) else fg.copy(alpha = 0.2f), CircleShape)
            .clickable(src, ripple(), enabled = enabled) { haptic(view); onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, desc, tint = when { !enabled -> fg.copy(alpha = 0.35f); active -> Color.White; else -> fg }, modifier = Modifier.size(iconSize))
    }
}

fun haptic(view: android.view.View) {
    if (Prefs.haptics) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
}
