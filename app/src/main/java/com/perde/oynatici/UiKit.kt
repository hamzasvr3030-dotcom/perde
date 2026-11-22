package com.perde.oynatici

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Basınca hafifçe küçülen ve titreşim veren tıklanabilir alan. */
@Composable
fun Modifier.pressable(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val view = LocalView.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.97f else 1f, label = "press")
    return this.scale(sc).clickable(src, ripple(), enabled) { haptic(view); onClick() }
}

/** Gruplanmış liste başlığı + yuvarlatılmış kart. */
@Composable
fun Group(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (title != null) Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surf)
            .border(1.dp, Line, RoundedCornerShape(18.dp)), content = content)
    }
}

@Composable
fun GroupRow(
    icon: ImageVector, title: String, sub: String? = null, trailing: String? = null, tint: Color = Accent,
    last: Boolean = false, chevron: Boolean = true, onClick: () -> Unit
) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = if (sub == null) 13.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, fontSize = 12.sp, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) Text(trailing, color = Muted, fontSize = 14.sp, modifier = Modifier.padding(end = 4.dp))
        if (chevron) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Muted.copy(alpha = 0.7f))
    }
    if (!last) HorizontalDivider(Modifier.padding(start = 58.dp), color = Line)
}

@Composable
fun GroupSwitch(icon: ImageVector, title: String, sub: String?, value: Boolean, last: Boolean = false, onChange: (Boolean) -> Unit) {
    var v by remember { mutableStateOf(value) }
    Row(Modifier.fillMaxWidth().clickable { v = !v; onChange(v) }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(Accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Accent, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
            Text(title, fontSize = 16.sp)
            if (sub != null) Text(sub, fontSize = 12.sp, color = Muted)
        }
        Switch(v, { v = it; onChange(it) }, colors = SwitchDefaults.colors(checkedTrackColor = Accent))
    }
    if (!last) HorizontalDivider(Modifier.padding(start = 58.dp), color = Line)
}

/** Kadraj logosu: degrade kare içinde "K". */
@Composable
fun KLogo(size: Dp = 34.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(Brush.linearGradient(listOf(AccentSoft, Accent))), contentAlignment = Alignment.Center) {
        Text("K", color = Color.White, fontWeight = FontWeight.Black, fontSize = (size.value * 0.58f).sp)
    }
}

/** Kaynak sitesine göre renkli yuvarlak ikon. */
@Composable
fun SourceBadge(icon: ImageVector, color: Color, size: Dp = 46.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = color, modifier = Modifier.size(size * 0.5f))
    }
}

/** Örneklerdeki gibi hap (pill) şeklinde iki seçenekli anahtar. */
@Composable
fun PillSwitch(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val view = LocalView.current
    Row(Modifier.fillMaxWidth().clip(CircleShape).background(Surf2).padding(4.dp)) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Box(Modifier.weight(1f).height(42.dp).clip(CircleShape).background(if (on) Accent else Color.Transparent)
                .clickable { haptic(view); onSelect(i) }, contentAlignment = Alignment.Center) {
                Text(o, color = if (on) Color.White else Muted, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, fontSize = 15.sp)
            }
        }
    }
}
