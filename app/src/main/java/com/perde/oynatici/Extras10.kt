package com.perde.oynatici

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Ayarlar › Tema: 10 arayüz stili, her biri küçük bir telefon önizlemesiyle. Dokununca hemen uygulanır. */
@Composable
fun StylePicker() {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        UiStyle.ALL.indices.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { i ->
                    StyleCard(UiStyle.ALL[i], ThemeState.styleIdx == i, Modifier.weight(1f)) {
                        ThemeState.styleIdx = i; Prefs.uiStyle = i
                        ThemeState.accentIdx = -1; Prefs.accent = -1          // stilin kendi rengi
                        ThemeState.dyn = null
                        PerdeWidget.update(ctx)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StyleCard(s: UiStyle, on: Boolean, m: Modifier, pick: () -> Unit) {
    val bg = if (s.grad.isEmpty()) SolidColor(Color(s.bg)) else Brush.verticalGradient(s.grad.map { Color(it) })
    val acc = Color(s.accent); val surf = Color(s.surf); val ink = Color(s.ink); val mut = Color(s.muted)
    val r = (s.radius * 0.45f).dp
    Column(m.clip(RoundedCornerShape(18.dp)).border(if (on) 3.dp else 1.dp, if (on) Accent else Line, RoundedCornerShape(18.dp)).clickable(onClick = pick)) {
        // küçük telefon ekranı
        Box(Modifier.fillMaxWidth().height(170.dp).background(bg).padding(10.dp)) {
            Column {
                Text("Videolar", color = ink, fontSize = (if (s.titleSize > 0) 17 else 16).sp, fontWeight = FontWeight(s.titleWeight), maxLines = 1)
                Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.clip(CircleShape).background(acc).padding(horizontal = 8.dp, vertical = 3.dp)) { Text("Tümü", fontSize = 8.sp, color = if (s.dark || true) Color.White else ink) }
                    Box(Modifier.clip(CircleShape).background(Color(s.surf2)).padding(horizontal = 8.dp, vertical = 3.dp)) { Text("Klasör", fontSize = 8.sp, color = mut) }
                }
                repeat(3) { k ->
                    Row(Modifier.padding(vertical = 3.dp).fillMaxWidth().clip(RoundedCornerShape(r)).background(surf).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(30.dp, 18.dp).clip(RoundedCornerShape(r * 0.6f)).background(Brush.linearGradient(listOf(acc.copy(alpha = 0.9f - k * 0.2f), Color(s.surf2)))))
                        Column(Modifier.padding(start = 5.dp)) {
                            Box(Modifier.size(46.dp, 4.dp).clip(CircleShape).background(ink.copy(alpha = 0.8f)))
                            Spacer(Modifier.height(3.dp))
                            Box(Modifier.size(28.dp, 3.dp).clip(CircleShape).background(mut.copy(alpha = 0.7f)))
                        }
                    }
                }
            }
            // alt çubuk
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .then(if (s.floatNav) Modifier.clip(RoundedCornerShape(10.dp)) else Modifier)
                .background(surf).padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                repeat(4) { k -> Box(Modifier.size(if (k == 0) 16.dp else 8.dp, 8.dp).clip(CircleShape).background(if (k == 0) acc else mut.copy(alpha = 0.5f))) }
            }
            if (on) Box(Modifier.align(Alignment.TopEnd).size(22.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
            }
        }
        Column(Modifier.fillMaxWidth().background(Surf2).padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(s.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
            Text(s.desc, color = Muted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 13.sp)
        }
    }
}

// ======================= oynatıcı görünümleri =======================

/** Oynat/duraklat düğmesi: 0 beyaz daire, 1 vurgu daire, 2 halka, 3 yuvarlak kare, 4 renk geçişli daire */
@Composable
fun PlayBtn(kind: Int, playing: Boolean, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val shape = if (kind == 3) RoundedCornerShape(size * 0.3f) else CircleShape
    val onAcc = if (Accent.luminanceSafe() > 0.6f) Color(0xFF111114) else Color.White
    val (bgMod, tint) = when (kind) {
        1 -> Modifier.background(Accent) to onAcc
        2 -> Modifier.border(2.5.dp, Accent, CircleShape).background(Accent.copy(alpha = 0.12f)) to Accent
        3 -> Modifier.background(Accent) to onAcc
        4 -> Modifier.background(Brush.linearGradient(listOf(Accent, lerpC(Accent, Color(if (ThemeState.style.name == "Cam") 0xFF3B82F6 else 0xFFFFB35C), 0.55f)))) to Color.White
        else -> Modifier.background(Color.White) to Color(0xFF111114)
    }
    Box(Modifier.size(size)
        .then(if (kind == 1 || kind == 4) Modifier.androidxShadow(size * 0.25f, shape, Accent) else Modifier)
        .clip(shape).then(bgMod).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            "Oynat/Duraklat", tint = tint, modifier = Modifier.size(size * 0.56f))
    }
}

private fun Color.luminanceSafe() = 0.2126f * red + 0.7152f * green + 0.0722f * blue
private fun lerpC(a: Color, b: Color, f: Float) = androidx.compose.ui.graphics.lerp(a, b, f)
private fun Modifier.androidxShadow(e: androidx.compose.ui.unit.Dp, s: androidx.compose.ui.graphics.Shape, c: Color) =
    this.shadow(e, s, ambientColor = c, spotColor = c)

/** Önceki/sonraki: cam daire ya da sade büyük simge */
@Composable
fun SideBtn(glass: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    if (glass) GlassButton(icon, desc, 56.dp, 32.dp) { onClick() }
    else androidx.compose.material3.IconButton(onClick = onClick, modifier = Modifier.size(56.dp)) { Icon(icon, desc, tint = NpFg, modifier = Modifier.size(38.dp)) }
}

/** Müzik çalar arka planı */
@Composable
fun NpBackground(kind: Int, top: Color, tint: Color, art: androidx.compose.ui.graphics.ImageBitmap?) {
    when (kind) {
        1 -> Box(Modifier.fillMaxSize().background(Color(0xFF0B0B0E))) {
            if (art != null) androidx.compose.foundation.Image(art, null, Modifier.fillMaxSize().androidx_blur(70.dp).graphicsAlpha(0.85f),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.6f)))))
        }
        2 -> Box(Modifier.fillMaxSize().background(BgBrush)) {
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(tint.copy(alpha = 0.45f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(540f, 500f), radius = 1100f)))
        }
        3 -> Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(lerpC(tint, Color.White, 0.72f), Bg))))
        else -> Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(top, Color(0xFF0B0B0E)))))
    }
}

private fun Modifier.androidx_blur(r: androidx.compose.ui.unit.Dp) =
    if (android.os.Build.VERSION.SDK_INT >= 31) this.blur(r) else this
private fun Modifier.graphicsAlpha(a: Float) = this.alpha(a)

/** Ses dalgası şeklinde ilerleme çubuğu (şarkıya göre sabit desen); dokun/sürükle ile sarar. */
@Composable
fun WaveSeek(seed: String, frac: Float, modifier: Modifier = Modifier, onDrag: (Float) -> Unit, onDone: (Float) -> Unit) {
    val bars = remember(seed) { val r = java.util.Random(seed.hashCode().toLong()); List(56) { 0.25f + r.nextFloat() * 0.75f } }
    var drag by remember { mutableStateOf<Float?>(null) }
    val cur = rememberUpdatedState(onDrag); val done = rememberUpdatedState(onDone)
    val on = Accent; val off = NpFg.copy(alpha = 0.28f)
    androidx.compose.foundation.Canvas(modifier.fillMaxWidth().height(44.dp)
        .pointerInputTap { f -> done.value(f) }
        .pointerInputDrag({ f -> drag = f; cur.value(f) }, { drag?.let { done.value(it) }; drag = null }, { drag })) {
        val f = (drag ?: frac).coerceIn(0f, 1f)
        val w = size.width / bars.size
        bars.forEachIndexed { i, h ->
            val bh = size.height * h
            drawRoundRect(if ((i + 0.5f) / bars.size <= f) on else off, androidx.compose.ui.geometry.Offset(i * w + w * 0.2f, (size.height - bh) / 2),
                androidx.compose.ui.geometry.Size(w * 0.6f, bh), androidx.compose.ui.geometry.CornerRadius(w * 0.3f))
        }
    }
}

private fun Modifier.pointerInputTap(tap: (Float) -> Unit) = this.pointerInput(Unit) {
    detectTapGestures { o -> tap((o.x / size.width).coerceIn(0f, 1f)) }
}
private fun Modifier.pointerInputDrag(move: (Float) -> Unit, end: () -> Unit, cur: () -> Float?) = this.pointerInput(Unit) {
    detectHorizontalDragGestures(
        onDragStart = { o -> move((o.x / size.width).coerceIn(0f, 1f)) },
        onDragEnd = { end() }, onDragCancel = { end() }
    ) { ch, dx -> ch.consume(); move(((cur() ?: 0f) + dx / size.width).coerceIn(0f, 1f)) }
}
