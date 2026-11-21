@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@file:Suppress("UnsafeOptInUsageError")

package com.perde.oynatici

import android.graphics.Typeface
import android.view.ContextThemeWrapper
import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val SUB_COLORS = listOf(0xFFFFFFFF.toInt() to "Beyaz", 0xFFFFE14D.toInt() to "Sarı", 0xFF7FE7FF.toInt() to "Mavi", 0xFFB6FF8A.toInt() to "Yeşil", 0xFFFFB38F.toInt() to "Turuncu")
private val TRL = Locale.forLanguageTag("tr")

fun fmtSpeed(s: Float): String =
    String.format(Locale.ROOT, "%.2f", s).trimEnd('0').trimEnd('.').replace('.', ',') + "×"

@Composable
fun PlayerScreen(a: PlayerActivity) {
    val ui = a.ui
    val density = LocalDensity.current

    LaunchedEffect(ui.showControls, ui.poke, ui.playing, ui.sheet, ui.casting) {
        if (ui.showControls && ui.playing && ui.sheet == null && !ui.casting) { delay(3800); ui.showControls = false }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val W = constraints.maxWidth.toFloat()
        val H = constraints.maxHeight.toFloat()
        val (bw, bh) = videoBox(ui, W, H)

        if (!ui.casting) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        player = a.player
                        subtitleView?.visibility = View.GONE
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                        setKeepContentOnPlayerReset(true)
                        a.playerView = this
                    }
                },
                modifier = Modifier.align(Alignment.Center)
                    .offset { IntOffset(ui.pan.x.roundToInt(), ui.pan.y.roundToInt()) }
                    .requiredSize(with(density) { bw.toDp() }, with(density) { bh.toDp() })
            )
        } else CastPanel(a)

        AndroidView(
            factory = { ctx -> SubtitleView(ctx).also { a.subtitleView = it } },
            update = { sv ->
                @Suppress("UNUSED_VARIABLE") val v = ui.subStyleVer
                val fg = SUB_COLORS.getOrElse(Prefs.subColor) { SUB_COLORS[0] }.first
                val bg = when (Prefs.subBg) { 0 -> android.graphics.Color.TRANSPARENT; 1 -> 0x99000000.toInt(); else -> android.graphics.Color.BLACK }
                sv.setStyle(CaptionStyleCompat(fg, bg, android.graphics.Color.TRANSPARENT,
                    if (Prefs.subBg == 0) CaptionStyleCompat.EDGE_TYPE_OUTLINE else CaptionStyleCompat.EDGE_TYPE_NONE,
                    android.graphics.Color.BLACK, Typeface.DEFAULT_BOLD))
                sv.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * Prefs.subScale)
                sv.setApplyEmbeddedFontSizes(false)
            },
            modifier = Modifier.fillMaxSize().padding(bottom = if (ui.showControls && !ui.inPip) 120.dp else 8.dp)
        )

        Box(Modifier.fillMaxSize().pointerInput(Unit) { playerGestures(a) })

        DoubleTapOverlay(ui)

        if (ui.buffering && !ui.inPip) CircularProgressIndicator(Modifier.align(Alignment.Center).size(56.dp), color = Accent, strokeWidth = 4.dp)

        ui.ind?.let { IndicatorView(it, if (it.kind == "seek") ui.thumb else null, Modifier.align(Alignment.Center)) }

        AnimatedVisibility(ui.showControls && !ui.locked && !ui.inPip && ui.countdown == null, enter = fadeIn(), exit = fadeOut()) { Controls(a) }

        if (ui.locked && !ui.inPip) LockOverlay(a)

        ui.countdown?.takeIf { !ui.inPip }?.let { CountdownCard(a, it, Modifier.align(Alignment.BottomEnd)) }
        ui.resumeAsk?.takeIf { !ui.inPip }?.let { ResumeCard(a, it, Modifier.align(Alignment.Center)) }
        ui.error?.let { ErrorCard(a, it, Modifier.align(Alignment.Center)) }
        ui.busy?.let { BusyCard(it, ui.busyProgress, Modifier.align(Alignment.Center)) }

        FlashOverlay(ui.flash)

        ui.toast?.takeIf { !ui.inPip }?.let {
            Text(it, color = Color.White, fontSize = 14.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp)
                .clip(RoundedCornerShape(20.dp)).background(Color(0xD9101014)).border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 9.dp))
        }
    }

    ui.sheet?.let { s -> PlayerSheet(a, s) }
}

private fun videoBox(ui: PlayerUi, W: Float, H: Float): Pair<Float, Float> {
    if (ui.vw == 0 || ui.vh == 0) return W to H
    val va = ui.vw * ui.par / ui.vh
    fun fit(r: Float) = if (W / H > r) H * r to H else W to W / r
    val (w, h) = when (ui.aspect) {
        1 -> if (W / H > va) W to W / va else H * va to H
        2 -> W to H
        3 -> fit(16f / 9f)
        4 -> fit(4f / 3f)
        5 -> ui.vw * ui.par to ui.vh.toFloat()
        else -> fit(va)
    }
    return w * ui.zoom to h * ui.zoom
}

// ---------------- hareketler ----------------
private suspend fun AwaitPointerEventScope.track(id: PointerId, onMove: (Offset) -> Unit) {
    while (true) {
        val ev = awaitPointerEvent()
        val c = ev.changes.firstOrNull { it.id == id } ?: break
        if (!c.pressed) break
        val d = c.positionChange(); c.consume(); onMove(d)
    }
}
private suspend fun AwaitPointerEventScope.waitAllUp() {
    while (true) { val ev = awaitPointerEvent(); if (ev.changes.none { it.pressed }) break }
}

private suspend fun PointerInputScope.playerGestures(a: PlayerActivity) {
    val slop = viewConfiguration.touchSlop
    val lpt = viewConfiguration.longPressTimeoutMillis
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var mode = 0
        var moved = Offset.Zero
        var released = false
        val res = withTimeoutOrNull(lpt) {
            while (true) {
                val ev = awaitPointerEvent()
                if (ev.changes.count { it.pressed } >= 2) { mode = 4; break }
                val c = ev.changes.firstOrNull { it.id == down.id }
                if (c == null || !c.pressed) { released = true; break }
                moved += c.positionChange()
                if (moved.getDistance() > slop) {
                    mode = if (abs(moved.x) > abs(moved.y)) 1 else if (down.position.x < size.width / 2f) 2 else 3
                    break
                }
            }
            true
        }
        if (res == null) mode = 5
        val ui = a.ui
        if (released && mode == 0) { a.onTap(down.position.x, size.width); return@awaitEachGesture }
        if (ui.locked) { ui.lockHint++; waitAllUp(); return@awaitEachGesture }
        if (ui.casting) { waitAllUp(); return@awaitEachGesture }
        when (mode) {
            1 -> if (!Prefs.gSeek) waitAllUp() else {
                a.seekDragStart(); var dx = moved.x
                a.seekDragUpdate(dx / size.width)
                track(down.id) { dx += it.x; a.seekDragUpdate(dx / size.width) }
                a.seekDragEnd()
            }
            2, 3 -> {
                val bright = mode == 2
                if ((bright && !Prefs.gBright) || (!bright && !Prefs.gVolume)) waitAllUp() else {
                    a.vDragStart(bright); var dy = moved.y
                    a.vDragUpdate(bright, -dy / size.height * 1.2f)
                    track(down.id) { dy += it.y; a.vDragUpdate(bright, -dy / size.height * 1.2f) }
                    a.vDragEnd()
                }
            }
            4 -> if (!Prefs.gPinch) waitAllUp() else {
                while (true) {
                    val ev = awaitPointerEvent()
                    if (ev.changes.none { it.pressed }) break
                    val z = ev.calculateZoom(); val p = ev.calculatePan()
                    if (z != 1f || p != Offset.Zero) a.pinch(z, p)
                    ev.changes.forEach { it.consume() }
                }
                a.pinchEnd()
            }
            5 -> if (!Prefs.gLong) waitAllUp() else { a.longStart(); waitAllUp(); a.longEnd() }
        }
    }
}

// ---------------- katmanlar ----------------
@Composable
private fun BoxScope.DoubleTapOverlay(ui: PlayerUi) {
    val side = ui.tapSide
    AnimatedVisibility(side != 0, enter = fadeIn(tween(120)), exit = fadeOut(tween(250)),
        modifier = Modifier.align(if (side < 0) Alignment.CenterStart else Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.38f)) {
        val shape = if (side < 0) RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50) else RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)
        Box(Modifier.fillMaxSize().clip(shape).background(Color.White.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row {
                    repeat(3) { i ->
                        Icon(if (side < 0) Icons.Filled.FastRewind else Icons.Filled.FastForward, null, tint = Color.White.copy(alpha = 0.5f + i * 0.25f), modifier = Modifier.size(22.dp))
                    }
                }
                Text("${if (side < 0) "−" else "+"}${ui.tapTotal} sn", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun FlashOverlay(trigger: Int) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(trigger) { if (trigger > 0) { alpha.snapTo(0.75f); alpha.animateTo(0f, tween(450)) } }
    if (alpha.value > 0f) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = alpha.value)))
}

@Composable
private fun IndicatorView(i: Ind, thumb: ImageBitmap?, m: Modifier) {
    val icon = when (i.kind) {
        "bright" -> Icons.Filled.BrightnessMedium; "vol" -> if ((i.level ?: 1f) <= 0f) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp
        "zoom" -> Icons.Filled.ZoomIn; "fast" -> Icons.Filled.Speed; else -> Icons.Filled.SwapHoriz
    }
    Column(m.clip(RoundedCornerShape(20.dp)).background(Color(0xCC0E0F13)).border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
        .padding(horizontal = 20.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (thumb != null) Image(thumb, null, Modifier.width(200.dp).aspectRatio(thumb.width.toFloat() / thumb.height).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
        else Icon(icon, null, tint = Accent, modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(8.dp))
        Text(i.text, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        i.level?.let {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(progress = { it.coerceIn(0f, 1f) }, modifier = Modifier.width(150.dp).height(5.dp).clip(CircleShape), color = Accent, trackColor = Color(0x44FFFFFF))
        }
    }
}

@Composable
private fun Card(m: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(m.padding(24.dp).widthIn(max = 420.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xF0141519))
        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(22.dp)).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally, content = content)
}

@Composable
private fun ErrorCard(a: PlayerActivity, msg: String, m: Modifier) = Card(m) {
    Icon(Icons.Filled.ErrorOutline, null, tint = Accent, modifier = Modifier.size(40.dp))
    Spacer(Modifier.height(8.dp))
    Text(msg, color = Color.White, textAlign = TextAlign.Center)
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (a.ui.hasNext) Button(onClick = { a.ui.error = null; a.next() }) { Text("Sonraki video") }
        OutlinedButton(onClick = { a.closePlayer() }) { Text("Kapat") }
    }
}

@Composable
private fun ResumeCard(a: PlayerActivity, pos: Long, m: Modifier) = Card(m) {
    Icon(Icons.Filled.History, null, tint = Accent, modifier = Modifier.size(36.dp))
    Spacer(Modifier.height(6.dp))
    Text("Kaldığın yerden devam edilsin mi?", color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Text("${fmtTime(pos)} / ${fmtTime(a.ui.dur)}", color = Muted)
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = { a.answerResume(false) }) { Icon(Icons.Filled.Replay, null); Spacer(Modifier.width(6.dp)); Text("Baştan") }
        Button(onClick = { a.answerResume(true) }) { Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Devam et") }
    }
}

@Composable
private fun CountdownCard(a: PlayerActivity, n: Int, m: Modifier) {
    Column(m.padding(20.dp).width(300.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xF0141519))
        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { n / 5f }, modifier = Modifier.size(44.dp), color = Accent, trackColor = Color(0x33FFFFFF), strokeWidth = 4.dp)
                Text("$n", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text("Sıradaki", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(a.ui.nextTitle, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { a.cancelCountdown() }, modifier = Modifier.weight(1f)) { Text("İptal") }
            Button(onClick = { a.playNextNow() }, modifier = Modifier.weight(1f)) { Text("Şimdi oynat") }
        }
    }
}

@Composable
private fun BusyCard(t: String, p: Float, m: Modifier) = Card(m) {
    Text(t, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Spacer(Modifier.height(12.dp))
    if (p < 0f) LinearProgressIndicator(modifier = Modifier.width(220.dp).height(6.dp).clip(CircleShape), color = Accent, trackColor = Color(0x33FFFFFF))
    else {
        LinearProgressIndicator(progress = { p.coerceIn(0f, 1f) }, modifier = Modifier.width(220.dp).height(6.dp).clip(CircleShape), color = Accent, trackColor = Color(0x33FFFFFF))
        Text("%${(p * 100).toInt()}", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun LockOverlay(a: PlayerActivity) {
    val ui = a.ui
    var visible by remember { mutableStateOf(true) }
    var pressing by remember { mutableStateOf(false) }
    LaunchedEffect(ui.lockHint) { visible = true; delay(2500); if (!pressing) visible = false }
    if (!visible) return
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                if (pressing && ui.kids) {
                    val p by produceState(0f) { val t0 = System.currentTimeMillis(); while (true) { value = ((System.currentTimeMillis() - t0) / 3000f).coerceAtMost(1f); delay(30) } }
                    CircularProgressIndicator(progress = { p }, modifier = Modifier.size(76.dp), color = Accent)
                }
                Box(Modifier.size(62.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.2f), Color.White.copy(alpha = 0.08f))))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                    .pointerInput(ui.kids) {
                        detectTapGestures(
                            onTap = { if (!ui.kids) a.unlock() else ui.lockHint++ },
                            onPress = {
                                if (ui.kids) {
                                    pressing = true
                                    val up = withTimeoutOrNull(3000) { tryAwaitRelease() }
                                    pressing = false
                                    if (up == null) a.unlock()
                                }
                            })
                    }, contentAlignment = Alignment.Center) {
                    Icon(if (ui.kids) Icons.Filled.ChildCare else Icons.Filled.Lock, null, tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(if (ui.kids) "Açmak için 3 saniye basılı tut" else "Kilidi açmak için dokun", color = Color.White, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0x99000000)).padding(horizontal = 10.dp, vertical = 4.dp))
        }
    }
}

@Composable
private fun CastPanel(a: PlayerActivity) {
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(AccentDeep, Color.Black))),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.CastConnected, null, tint = Accent, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(12.dp))
        Text("${a.ui.castDevice} cihazında oynatılıyor", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(a.ui.title, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 32.dp))
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = { a.endCast() }) { Text("Yayını durdur") }
    }
}

// ---------------- kontroller ----------------
@Composable
private fun CastButton() {
    AndroidView(factory = { ctx ->
        MediaRouteButton(ContextThemeWrapper(ctx, androidx.appcompat.R.style.Theme_AppCompat)).also {
            try { CastButtonFactory.setUpMediaRouteButton(ctx.applicationContext, it) } catch (_: Throwable) { }
        }
    }, modifier = Modifier.size(44.dp))
}

@Composable
private fun PillText(text: String, active: Boolean, onClick: () -> Unit) {
    val view = LocalView.current
    Box(Modifier.height(36.dp).clip(CircleShape)
        .background(if (active) Accent.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.12f))
        .border(1.dp, if (active) Accent.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.2f), CircleShape)
        .clickable { haptic(view); onClick() }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun BigPlay(playing: Boolean, onClick: () -> Unit) {
    val view = LocalView.current
    Box(Modifier.size(78.dp).clip(CircleShape)
        .background(Brush.linearGradient(listOf(AccentSoft, Accent)))
        .border(2.dp, Color.White.copy(alpha = 0.25f), CircleShape)
        .clickable { haptic(view); onClick() }, contentAlignment = Alignment.Center) {
        AnimatedContent(playing, transitionSpec = { (scaleIn(initialScale = 0.6f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.6f) + fadeOut()) }, label = "pp") { p ->
            Icon(if (p) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Oynat/Duraklat", tint = Color(0xFF14100E), modifier = Modifier.size(44.dp))
        }
    }
}

@Composable
private fun Controls(a: PlayerActivity) {
    val ui = a.ui
    Box(Modifier.fillMaxSize()) {
        // üst
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xE6000000), Color.Transparent)))
            .windowInsetsPadding(WindowInsets.displayCutout).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton(Icons.AutoMirrored.Filled.ArrowBack, "Geri", 42.dp) { a.closePlayer() }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(ui.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = listOfNotNull(resLabel(ui.vw, ui.vh).ifEmpty { null }, if (ui.night) "Gece modu" else null, if (ui.sleepLabel.isNotEmpty()) "Uyku ${ui.sleepLabel}" else null)
                if (sub.isNotEmpty()) Text(sub.joinToString(" · "), color = Muted, fontSize = 12.sp)
            }
            if (!ui.casting) {
                GlassButton(if (ui.fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favori", 40.dp, 20.dp, active = ui.fav) { a.toggleFav() }
                if (a.isWeb) GlassButton(Icons.Filled.Download, "İndir", 40.dp, 20.dp, active = true) { ui.sheet = Sheet.DOWNLOAD }
                GlassButton(Icons.Filled.ClosedCaption, "Altyazı", 40.dp, 20.dp) { ui.sheet = Sheet.SUBS }
                GlassButton(Icons.Filled.Audiotrack, "Ses", 40.dp, 20.dp) { ui.sheet = Sheet.AUDIO }
                PillText(fmtSpeed(ui.speed), ui.speed != 1f) { ui.sheet = Sheet.SPEED }
            }
            CastButton()
            if (!ui.casting) GlassButton(Icons.Filled.MoreHoriz, "Diğer", 40.dp, 22.dp) { ui.sheet = Sheet.MORE }
        }

        // orta
        Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassButton(Icons.Filled.SkipPrevious, "Önceki", 50.dp, 28.dp, enabled = !ui.casting) { a.prev() }
            GlassButton(Icons.Filled.Replay10, "Geri sar", 54.dp, 28.dp) { a.seekBy(-Prefs.seekStep * 1000L) }
            BigPlay(if (ui.casting) ui.castPlaying else ui.playing) { a.togglePlay() }
            GlassButton(Icons.Filled.Forward10, "İleri sar", 54.dp, 28.dp) { a.seekBy(Prefs.seekStep * 1000L) }
            GlassButton(Icons.Filled.SkipNext, "Sonraki", 50.dp, 28.dp, enabled = ui.hasNext && !ui.casting) { a.next() }
        }

        // alt
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF0000000))))
            .windowInsetsPadding(WindowInsets.displayCutout).padding(horizontal = 16.dp).padding(bottom = 10.dp, top = 30.dp)) {
            SeekBar(a)
            if (ui.abA >= 0) Text(
                if (ui.abB >= 0) "A-B: ${fmtTime(ui.abA)} – ${fmtTime(ui.abB)}" else "A: ${fmtTime(ui.abA)} (B için tekrar dokun)",
                color = AccentSoft, fontSize = 12.sp)
            if (!ui.casting) Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                GlassButton(Icons.Filled.LockOpen, "Kilitle", 40.dp, 20.dp) { a.lock() }
                GlassButton(Icons.Filled.AspectRatio, "Görüntü oranı", 40.dp, 20.dp, active = ui.aspect != 0) { a.cycleAspect() }
                GlassButton(Icons.Filled.ScreenRotation, "Ekran yönü", 40.dp, 20.dp) { a.cycleOrient() }
                GlassButton(Icons.Filled.PhotoCamera, "Ekran görüntüsü", 40.dp, 20.dp) { a.screenshot() }
                GlassButton(Icons.Filled.BookmarkAdd, "Yer imi", 40.dp, 20.dp) { a.addBookmark() }
                GlassButton(if (ui.repeat == 1) Icons.Filled.RepeatOne else Icons.Filled.Repeat, "Tekrar", 40.dp, 20.dp, active = ui.repeat > 0) { a.cycleRepeat() }
                GlassButton(Icons.Filled.PictureInPictureAlt, "Küçük pencere", 40.dp, 20.dp) { a.enterPip() }
            }
        }
    }
}

@Composable
private fun SeekBar(a: PlayerActivity) {
    val ui = a.ui
    var drag by remember { mutableStateOf<Float?>(null) }
    val frac = if (ui.dur > 0) (ui.pos.toFloat() / ui.dur).coerceIn(0f, 1f) else 0f
    val marks = remember(ui.bmVer, ui.chapters, ui.dur, ui.key) {
        if (ui.dur <= 0) emptyList() else
            (Bookmarks.get(ui.key).map { it.t to true } + ui.chapters.map { it.first to false }).map { (t, b) -> (t.toFloat() / ui.dur) to b }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w = maxWidth
        drag?.let { d ->
            val tw = 168.dp
            val x = (w - 60.dp) * d + 30.dp - tw / 2
            Column(Modifier.offset(x = x.coerceIn(0.dp, w - tw), y = (-118).dp).width(tw), horizontalAlignment = Alignment.CenterHorizontally) {
                ui.thumb?.let { Image(it, null, Modifier.fillMaxWidth().aspectRatio(16 / 9f).clip(RoundedCornerShape(10.dp)).border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop) }
                Text(fmtTime((d * ui.dur).toLong()), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp).clip(CircleShape).background(Color(0xCC000000)).padding(horizontal = 8.dp, vertical = 2.dp))
            }
        }
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(fmtTime(drag?.let { (it * ui.dur).toLong() } ?: ui.pos), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(60.dp))
                Box(Modifier.weight(1f)) {
                    Slider(
                        value = drag ?: frac,
                        onValueChange = { drag = it; ui.poke++; a.requestThumb((it * ui.dur).toLong()) },
                        onValueChangeFinished = { drag?.let { a.seekTo((it * ui.dur).toLong()) }; drag = null; a.requestThumb(null) },
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Accent, inactiveTrackColor = Color(0x44FFFFFF))
                    )
                    if (marks.isNotEmpty()) Canvas(Modifier.matchParentSize()) {
                        val pad = 10.dp.toPx(); val y = size.height / 2
                        marks.forEach { (f, isBm) ->
                            val x = pad + (size.width - 2 * pad) * f
                            drawCircle(if (isBm) Color(0xFFFFE14D) else Color.White, radius = if (isBm) 3.5.dp.toPx() else 2.dp.toPx(), center = Offset(x, y - 9.dp.toPx()))
                        }
                    }
                }
                Text(fmtTime(ui.dur), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.End, modifier = Modifier.width(60.dp))
            }
        }
    }
}

// ---------------- alt sayfalar ----------------
@Composable
fun SheetTitle(t: String) = Text(t, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))

@Composable
fun Section(t: String) = Text(t, color = Accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))

@Composable
fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (selected) Accent.copy(alpha = 0.12f) else Color.Transparent)
        .clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun OffsetRow(value: Long, onChange: (Long) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilledTonalButton(onClick = { onChange(value - 500) }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("−0,5") }
        FilledTonalButton(onClick = { onChange(value - 100) }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("−0,1") }
        Text(String.format(TRL, "%+.1f sn", value / 1000f), fontWeight = FontWeight.Bold, modifier = Modifier.width(76.dp), textAlign = TextAlign.Center)
        FilledTonalButton(onClick = { onChange(value + 100) }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("+0,1") }
        FilledTonalButton(onClick = { onChange(value + 500) }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("+0,5") }
    }
    if (value != 0L) TextButton(onClick = { onChange(0) }) { Text("Sıfırla") }
}

@Composable
fun Tile(icon: ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    val view = LocalView.current
    Column(Modifier.width(100.dp).clip(RoundedCornerShape(18.dp))
        .background(if (active) Brush.verticalGradient(listOf(Accent.copy(alpha = 0.35f), Accent.copy(alpha = 0.15f))) else Brush.verticalGradient(listOf(Surf2, Surf2)))
        .border(1.dp, if (active) Accent.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
        .clickable { haptic(view); onClick() }.padding(vertical = 14.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = if (active) Accent else Color.White)
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 14.sp)
    }
}

@Composable
private fun PlayerSheet(a: PlayerActivity, s: Sheet) {
    val ui = a.ui
    ModalBottomSheet(onDismissRequest = { ui.sheet = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Surf) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            when (s) {
                Sheet.SUBS -> SubsSheet(a)
                Sheet.AUDIO -> AudioSheet(a)
                Sheet.SPEED -> SpeedSheet(a)
                Sheet.MORE -> MoreSheet(a)
                Sheet.SLEEP -> SleepSheet { a.setSleep(it) }
                Sheet.EQ -> EqContent()
                Sheet.INFO -> InfoSheet(a)
                Sheet.CLIP -> ClipSheet(a)
                Sheet.CHAPTERS -> ChaptersSheet(a)
                Sheet.BOOKMARKS -> BookmarksSheet(a)
                Sheet.ONLINE -> OnlineSubsSheet(a)
                Sheet.DOWNLOAD -> DlOptionsContent(false, 1) { o, at ->
                    a.curKey()?.let { k -> Dl.addSingles(a, listOf(Triple(k, ui.title, Web.cached(k)?.thumb)), o, at) }
                    ui.sheet = null; a.toast("İndirme başladı — İndir sekmesinden takip edebilirsin")
                }
                Sheet.META -> a.curKey()?.let { k -> MetaContent(k, java.io.File(k).name) }
                Sheet.PLAYLIST -> PlaylistPickerContent(listOfNotNull(a.curKey())) { msg -> ui.sheet = null; a.toast(msg) }
            }
        }
    }
}

@Composable
private fun SubsSheet(a: PlayerActivity) {
    val ui = a.ui
    SheetTitle("Altyazı")
    key(ui.tracksVer) {
        val list = a.tracks(C.TRACK_TYPE_TEXT)
        var ver by remember { mutableIntStateOf(0) }
        key(ver) {
            Choice("Kapalı", a.subsDisabled || list.none { it.selected }) { a.subsOff(); ver++ }
            list.forEach { t -> Choice(t.label, t.selected && !a.subsDisabled) { a.selectTrack(t, C.TRACK_TYPE_TEXT); ver++ } }
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { a.pickSub.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.FileOpen, null); Spacer(Modifier.width(6.dp)); Text("Dosyadan aç")
        }
        Button(onClick = { ui.sheet = Sheet.ONLINE }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.TravelExplore, null); Spacer(Modifier.width(6.dp)); Text("İnternette ara")
        }
    }
    if (ui.hasExtSub) {
        Section("Senkron — altyazı erken/geç kalıyorsa")
        OffsetRow(ui.subOff) { a.setSubOffset(it) }
        TextButton(onClick = { a.removeExtSub() }) { Text("Harici altyazıyı kaldır") }
    }
    Section("Görünüm")
    var scale by remember { mutableFloatStateOf(Prefs.subScale) }
    Text("Yazı boyutu: %${(scale * 100).toInt()}")
    Slider(scale, { scale = it; Prefs.subScale = it; a.applySubStyle() }, valueRange = 0.5f..2.5f)
    var color by remember { mutableIntStateOf(Prefs.subColor) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SUB_COLORS.forEachIndexed { i, (c, n) ->
            FilterChip(selected = color == i, onClick = { color = i; Prefs.subColor = i; a.applySubStyle() }, label = { Text(n) },
                leadingIcon = { Box(Modifier.size(14.dp).clip(CircleShape).background(Color(c))) })
        }
    }
    var bg by remember { mutableIntStateOf(Prefs.subBg) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Arka plan yok", "Yarı saydam", "Siyah kutu").forEachIndexed { i, n ->
            FilterChip(selected = bg == i, onClick = { bg = i; Prefs.subBg = i; a.applySubStyle() }, label = { Text(n) })
        }
    }
    Section("Karakter kodlaması (ş, ğ, ı bozuk çıkıyorsa değiştir)")
    var enc by remember { mutableIntStateOf(Prefs.subEnc) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Otomatik", "UTF-8", "Türkçe (Windows-1254)").forEachIndexed { i, n ->
            FilterChip(selected = enc == i, onClick = { enc = i; Prefs.subEnc = i; if (ui.hasExtSub) a.reloadSub() }, label = { Text(n) })
        }
    }
}

@Composable
private fun OnlineSubsSheet(a: PlayerActivity) {
    SheetTitle("İnternette altyazı ara")
    if (Prefs.osKey.isBlank()) {
        Text("Bu özellik OpenSubtitles.com kullanır. Ücretsiz bir hesap açıp \"API Consumers\" sayfasından API anahtarı al, sonra Ayarlar → Online altyazı bölümüne gir.\n\nAdres: opensubtitles.com/consumers", color = Muted)
        return
    }
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf(cleanTitle(a.ui.title)) }
    var res by remember { mutableStateOf<List<OpenSubs.Result>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    fun search() {
        loading = true; err = null
        scope.launch { try { res = OpenSubs.search(q) } catch (e: Exception) { err = e.message ?: "Arama başarısız" }; loading = false }
    }
    LaunchedEffect(Unit) { search() }
    OutlinedTextField(q, { q = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text("Film / dizi adı") },
        trailingIcon = { IconButton(onClick = { search() }) { Icon(Icons.Filled.Search, "Ara") } },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search() }))
    Text("Diller: ${Prefs.osLangs}", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    Spacer(Modifier.height(8.dp))
    when {
        loading -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent)
        err != null -> Text("Hata: $err", color = Color(0xFFFF6B6B))
        res?.isEmpty() == true -> Text("Sonuç bulunamadı. Adı kısaltmayı dene.", color = Muted)
    }
    res?.forEach { r ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
            loading = true
            scope.launch {
                try { a.useDownloadedSub(OpenSubs.download(r, a.subDownloadDir(), a.subBaseName())); a.ui.sheet = null }
                catch (e: Exception) { err = e.message ?: "İndirilemedi" }
                loading = false
            }
        }.padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(r.lang.uppercase(), color = Accent, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
            Column(Modifier.weight(1f)) {
                Text(r.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                Text("${r.downloads} indirme", color = Muted, fontSize = 12.sp)
            }
            Icon(Icons.Filled.Download, null, tint = Muted)
        }
    }
}

@Composable
private fun AudioSheet(a: PlayerActivity) {
    val ui = a.ui
    SheetTitle("Ses")
    key(ui.tracksVer) {
        val list = a.tracks(C.TRACK_TYPE_AUDIO)
        var ver by remember { mutableIntStateOf(0) }
        if (list.isEmpty()) Text("Bu videoda seçilebilir ses parçası yok.")
        key(ver) { list.forEach { t -> Choice(t.label, t.selected) { a.selectTrack(t, C.TRACK_TYPE_AUDIO); ver++ } } }
    }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Gece modu", fontWeight = FontWeight.SemiBold)
            Text("Patlamaları kısar, konuşmaları yükseltir", color = Muted, fontSize = 13.sp)
        }
        Switch(ui.night, { a.toggleNight() })
    }
    Section("Ses senkronu — ses görüntüden önce/sonra geliyorsa")
    OffsetRow(ui.audioOff) { a.setAudioOffset(it) }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = { ui.sheet = Sheet.EQ }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Equalizer, null); Spacer(Modifier.width(8.dp)); Text("Ekolayzer") }
}

@Composable
private fun SpeedSheet(a: PlayerActivity) {
    val ui = a.ui
    SheetTitle("Oynatma hızı")
    Text(fmtSpeed(ui.speed), fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Accent, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    Slider(ui.speed, { a.setSpeed(Math.round(it * 20) / 20f) }, valueRange = 0.25f..4f)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 4f).forEach { v ->
            FilterChip(selected = ui.speed == v, onClick = { a.setSpeed(v) }, label = { Text(fmtSpeed(v)) })
        }
    }
    Text("İpucu: Ekrana basılı tutarak geçici olarak 2× hızda izleyebilirsin.", fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun MoreSheet(a: PlayerActivity) {
    val ui = a.ui
    var decSw by remember { mutableStateOf(Prefs.decoderSw) }
    SheetTitle("Araçlar")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (a.isWeb) Tile(Icons.Filled.Download, "İndir", true) { ui.sheet = Sheet.DOWNLOAD }
        Tile(Icons.Filled.PhotoCamera, "Ekran görüntüsü") { ui.sheet = null; a.screenshot() }
        Tile(Icons.Filled.ContentCut, "Kes / GIF yap") { ui.sheet = Sheet.CLIP }
        Tile(Icons.Filled.Toc, "Bölümler ve sahneler", ui.chapters.isNotEmpty()) { ui.sheet = Sheet.CHAPTERS }
        Tile(Icons.Filled.Bookmarks, "Yer imleri") { ui.sheet = Sheet.BOOKMARKS }
        Tile(Icons.Filled.Loop, when { ui.abA < 0 -> "A-B tekrar"; ui.abB < 0 -> "B noktasını işaretle"; else -> "A-B'yi kapat" }, ui.abA >= 0) { a.abStep() }
        Tile(Icons.Filled.Bedtime, if (ui.sleepLabel.isEmpty()) "Uyku zamanlayıcısı" else "Uyku: ${ui.sleepLabel}", ui.sleepLabel.isNotEmpty()) { ui.sheet = Sheet.SLEEP }
        Tile(Icons.Filled.NightsStay, "Gece modu", ui.night) { a.toggleNight() }
        Tile(Icons.Filled.Equalizer, "Ekolayzer", Prefs.eqOn) { ui.sheet = Sheet.EQ }
        Tile(Icons.Filled.Headphones, "Sadece ses (müzik gibi)") { ui.sheet = null; a.audioOnly() }
        Tile(Icons.Filled.Shuffle, "Karışık çal", ui.shuffle) { a.toggleShuffle() }
        Tile(Icons.Filled.PlayCircleOutline, "Arka planda oynat", ui.bg) { a.toggleBg() }
        Tile(if (ui.fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favori", ui.fav) { a.toggleFav() }
        Tile(Icons.AutoMirrored.Filled.PlaylistAdd, "Listeye ekle") { ui.sheet = Sheet.PLAYLIST }
        Tile(Icons.Filled.ScreenRotation, PlayerActivity.ORIENTS[ui.orient]) { a.cycleOrient() }
        Tile(Icons.Filled.Lock, "Ekran kilidi") { a.lock() }
        Tile(Icons.Filled.ChildCare, "Çocuk kilidi") { a.kidsLock() }
        Tile(Icons.Filled.Info, "Video bilgisi") { ui.sheet = Sheet.INFO }
        Tile(Icons.Filled.Memory, if (decSw) "Çözücü: yazılım" else "Çözücü: donanım", decSw) {
            decSw = !decSw; Prefs.decoderSw = decSw; a.toast("Çözücü ayarı oynatıcı yeniden açılınca geçerli olur")
        }
    }
}

@Composable
fun SleepSheet(onPick: (Int) -> Unit) {
    SheetTitle("Uyku zamanlayıcısı")
    listOf(0 to "Kapalı", 15 to "15 dakika", 30 to "30 dakika", 45 to "45 dakika", 60 to "1 saat", 90 to "1,5 saat", 120 to "2 saat", -1 to "Bu video/şarkı bitince")
        .forEach { (m, l) -> Choice(l, false) { onPick(m) } }
}

@Composable
private fun ClipSheet(a: PlayerActivity) {
    val ui = a.ui
    SheetTitle("Kes / GIF yap")
    if (!a.isLocal) { Text("Bu özellik sadece telefondaki videolarda çalışır.", color = Muted); return }
    val d = ui.dur.coerceAtLeast(1)
    var range by remember { mutableStateOf(ui.pos.toFloat()..minOf(d, ui.pos + 10_000).toFloat()) }
    val len = (range.endInclusive - range.start).toLong()
    Text("${fmtTime(range.start.toLong())} – ${fmtTime(range.endInclusive.toLong())}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Accent)
    Text("Süre: ${fmtTime(len)}", color = Muted)
    RangeSlider(range, { range = it }, valueRange = 0f..d.toFloat())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = { range = ui.pos.toFloat()..range.endInclusive.coerceAtLeast(ui.pos.toFloat() + 1000) }) { Text("Başı = şu an") }
        FilledTonalButton(onClick = { range = range.start.coerceAtMost(ui.pos.toFloat() - 1000).coerceAtLeast(0f)..ui.pos.toFloat() }) { Text("Sonu = şu an") }
    }
    Spacer(Modifier.height(14.dp))
    Button(onClick = { a.trim(range.start.toLong(), range.endInclusive.toLong()) }, enabled = len >= 1000, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.ContentCut, null); Spacer(Modifier.width(8.dp)); Text("Videoyu kes (MP4 olarak kaydet)")
    }
    OutlinedButton(onClick = { a.makeGif(range.start.toLong(), range.endInclusive.toLong()) }, enabled = len in 500..15_000, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Gif, null); Spacer(Modifier.width(8.dp)); Text(if (len > 15_000) "GIF en fazla 15 saniye olabilir" else "GIF yap")
    }
    Text("Kesitler Filmler/Kadraj, GIF'ler Resimler/Kadraj klasörüne kaydedilir.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun ChaptersSheet(a: PlayerActivity) {
    val ui = a.ui
    if (ui.chapters.isNotEmpty()) {
        SheetTitle("Bölümler")
        ui.chapters.forEachIndexed { i, (t, n) ->
            val active = ui.pos >= t && (i == ui.chapters.lastIndex || ui.pos < ui.chapters[i + 1].first)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (active) Accent.copy(alpha = 0.14f) else Color.Transparent)
                .clickable { a.seekTo(t); ui.sheet = null }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(fmtTime(t), color = Accent, fontWeight = FontWeight.Bold, modifier = Modifier.width(72.dp))
                Text(n, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        return
    }
    SheetTitle("Sahneler")
    if (!a.isLocal || ui.dur <= 0) { Text("Bu videoda bölüm bilgisi yok.", color = Muted); return }
    Text("Videoda bölüm bilgisi yok; eşit aralıklı sahnelerden birine dokun.", color = Muted, fontSize = 13.sp)
    val n = 12
    val times = remember(ui.dur) { (0 until n).map { ui.dur * (it * 2 + 1) / (n * 2) } }
    val frames = remember { mutableStateListOf<ImageBitmap?>().apply { repeat(n) { add(null) } } }
    val k = a.curKey()
    LaunchedEffect(k) {
        if (k != null) times.forEachIndexed { i, t -> frames[i] = withContext(a.serial) { a.frameAt(k, t, 320, 180)?.asImageBitmap() } }
    }
    LazyVerticalGrid(GridCells.Adaptive(150.dp), Modifier.heightIn(max = 520.dp).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(times) { i, t ->
            Box(Modifier.aspectRatio(16 / 9f).clip(RoundedCornerShape(12.dp)).background(Surf2).clickable { a.seekTo(t); ui.sheet = null }) {
                frames[i]?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                Text(fmtTime(t), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                    .clip(RoundedCornerShape(6.dp)).background(Color(0xB3000000)).padding(horizontal = 5.dp, vertical = 1.dp))
            }
        }
    }
}

@Composable
private fun BookmarksSheet(a: PlayerActivity) {
    val ui = a.ui
    SheetTitle("Yer imleri")
    Button(onClick = { a.addBookmark() }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.BookmarkAdd, null); Spacer(Modifier.width(8.dp)); Text("Şu anki anı işaretle (${fmtTime(ui.pos)})") }
    val list = remember(ui.bmVer, ui.key) { Bookmarks.get(ui.key) }
    if (list.isEmpty()) Text("Henüz yer imi yok. Alt çubuktaki yer imi düğmesiyle de ekleyebilirsin.", color = Muted, modifier = Modifier.padding(top = 12.dp))
    list.forEach { b ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { a.seekTo(b.t); ui.sheet = null }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Bookmark, null, tint = Color(0xFFFFE14D))
            Text(fmtTime(b.t), color = Accent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp))
            Text(b.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = { Bookmarks.remove(ui.key, b.t); ui.bmVer++ }) { Icon(Icons.Filled.Close, "Sil", tint = Muted) }
        }
    }
}

@Composable
fun EqContent() {
    SheetTitle("Ekolayzer")
    LaunchedEffect(Unit) { Engine.ensureEffects() }
    val eq = Engine.eq
    if (eq == null) { Text("Ekolayzer bu cihazda kullanılamıyor ya da oynatma henüz başlamadı.", color = Muted); return }
    var on by remember { mutableStateOf(Prefs.eqOn) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Ekolayzer açık", Modifier.weight(1f), fontWeight = FontWeight.SemiBold); Switch(on, { on = it; Prefs.eqOn = it; Engine.applyEq() })
    }
    val n = eq.numberOfBands.toInt()
    val range = eq.bandLevelRange
    val levels = remember { mutableStateListOf<Float>().apply { repeat(n) { add(eq.getBandLevel(it.toShort()).toFloat()) } } }
    var preset by remember { mutableIntStateOf(Prefs.eqPreset) }
    Section("Hazır ayarlar")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = preset < 0, onClick = { preset = -1; Prefs.eqPreset = -1 }, label = { Text("Özel") })
        for (p in 0 until eq.numberOfPresets) {
            FilterChip(selected = preset == p, onClick = {
                preset = p; Prefs.eqPreset = p; Prefs.eqOn = true; on = true; Engine.applyEq()
                for (i in 0 until n) levels[i] = eq.getBandLevel(i.toShort()).toFloat()
            }, label = { Text(eqName(eq.getPresetName(p.toShort()))) })
        }
    }
    Section("Bantlar")
    Row(Modifier.fillMaxWidth().height(200.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        for (i in 0 until n) {
            val hz = eq.getCenterFreq(i.toShort()) / 1000
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text("${(levels[i] / 100).roundToInt()} dB", fontSize = 11.sp, color = Muted)
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Slider(levels[i], {
                        levels[i] = it; preset = -1; Prefs.eqPreset = -1; Prefs.eqOn = true; on = true
                        Prefs.eqBands = levels.joinToString(",") { v -> v.toInt().toString() }; Engine.applyEq()
                    }, valueRange = range[0].toFloat()..range[1].toFloat(),
                        modifier = Modifier.requiredWidth(150.dp).graphicsRotate())
                }
                Text(if (hz >= 1000) "${hz / 1000}k" else "$hz", fontSize = 11.sp)
            }
        }
    }
    if (Engine.bassBoost?.strengthSupported == true) {
        Section("Bas güçlendirme")
        var bass by remember { mutableFloatStateOf(Prefs.bass.toFloat()) }
        Slider(bass, { bass = it; Prefs.bass = it.toInt(); Prefs.eqOn = true; on = true; Engine.applyEq() }, valueRange = 0f..1000f)
    }
    Text("Ses yükseltme: Videoda ekranın sağ yarısında yukarı kaydırarak sesi %200'e kadar çıkarabilirsin.", fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 12.dp))
}

private fun Modifier.graphicsRotate() = this.graphicsLayer { rotationZ = 270f }

private fun eqName(n: String) = when (n.lowercase()) {
    "normal" -> "Normal"; "classical" -> "Klasik"; "dance" -> "Dans"; "flat" -> "Düz"; "folk" -> "Halk"
    "heavy metal" -> "Metal"; "hip hop" -> "Hip hop"; "jazz" -> "Caz"; "pop" -> "Pop"; "rock" -> "Rock"; else -> n
}

@Composable
private fun InfoSheet(a: PlayerActivity) {
    SheetTitle("Video bilgisi")
    a.infoLines().forEach { (k, v) ->
        Row(Modifier.padding(vertical = 5.dp)) {
            Text(k, color = Muted, modifier = Modifier.width(120.dp), fontSize = 14.sp)
            Text(v, fontSize = 14.sp)
        }
    }
}
