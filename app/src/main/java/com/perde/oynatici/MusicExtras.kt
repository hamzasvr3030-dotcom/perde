@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.perde.oynatici

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ---------------- ses dalgası ----------------
/** Kapak etrafında müziğe göre hareket eden halka + altta çubuklar. */
@Composable
fun VizView(art: ImageBitmap?, tint: Color, playing: Boolean) {
    var bands by remember { mutableStateOf(FloatArray(32)) }
    var angle by remember { mutableFloatStateOf(0f) }
    DisposableEffect(Unit) { Viz.enabled = true; onDispose { Viz.enabled = false } }
    LaunchedEffect(playing) {
        while (isActive) { withFrameMillisCompat(); bands = Viz.bands; if (playing) angle = (angle + 0.15f) % 360f }
    }
    val accent = Accent
    Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2)
            val r = min(size.width, size.height) * 0.30f
            val maxLen = min(size.width, size.height) * 0.18f
            val n = 64
            for (i in 0 until n) {
                val b = bands[if (i < 32) i else 63 - i]
                val a = (i.toFloat() / n) * 2 * PI.toFloat() - PI.toFloat() / 2
                val len = 6f + b * maxLen
                val s = Offset(c.x + cos(a) * (r + 8f), c.y + sin(a) * (r + 8f))
                val e = Offset(c.x + cos(a) * (r + 8f + len), c.y + sin(a) * (r + 8f + len))
                drawLine(Brush.linearGradient(listOf(accent, tint.copy(alpha = 0.9f), Color.White), s, e), s, e, strokeWidth = 7f, cap = StrokeCap.Round)
            }
            drawCircle(accent.copy(alpha = 0.18f + 0.3f * bands.take(4).average().toFloat()), radius = r + 4f, center = c)
        }
        Box(Modifier.fillMaxSize(0.56f).clip(CircleShape).rotate(angle).background(Brush.linearGradient(listOf(tint, AccentDeep))), contentAlignment = Alignment.Center) {
            if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Filled.MusicNote, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.fillMaxSize(0.4f))
        }
    }
}

private suspend fun withFrameMillisCompat() = androidx.compose.runtime.withFrameMillis { }

// ---------------- geçiş ayarları ----------------
@Composable
fun TransitionSettings() {
    var cf by remember { mutableIntStateOf(Prefs.crossfade) }
    var ss by remember { mutableStateOf(Prefs.skipSilence) }
    var ol by remember { mutableStateOf(Prefs.onlineLyrics) }
    SheetTitle("Çalma ayarları")
    Text("Yumuşak geçiş", fontWeight = FontWeight.SemiBold)
    Text("Şarkı biterken ses yavaşça kısılır, yenisi yavaşça açılır.", color = Muted, fontSize = 12.sp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0, 2, 4, 6, 8, 12).forEach { s -> FilterChip(cf == s, { cf = s; Prefs.crossfade = s }, label = { Text(if (s == 0) "Kapalı" else "$s sn") }) }
    }
    Row(Modifier.fillMaxWidth().clickable { ss = !ss; Prefs.skipSilence = ss; Engine.player?.skipSilenceEnabled = ss }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text("Sessizliği atla", fontWeight = FontWeight.SemiBold); Text("Şarkı başı ve sonundaki sessiz kısımları atlar", color = Muted, fontSize = 12.sp) }
        Switch(ss, { ss = it; Prefs.skipSilence = it; Engine.player?.skipSilenceEnabled = it })
    }
    Row(Modifier.fillMaxWidth().clickable { ol = !ol; Prefs.onlineLyrics = ol }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text("İnternetten şarkı sözü", fontWeight = FontWeight.SemiBold); Text(".lrc dosyası yoksa sözleri LRCLIB'den bulur", color = Muted, fontSize = 12.sp) }
        Switch(ol, { ol = it; Prefs.onlineLyrics = it })
    }
}

// ---------------- akıllı listeler ----------------
@Composable
fun SmartListScreen(vm: LibVM, type: Int, back: () -> Unit) {
    val ctx = LocalContext.current
    val songs = remember(vm.songs, vm.ver) { SmartLists.songs(type, vm.songs) }
    Page(SmartLists.NAMES[type], back) {
        if (songs.isEmpty()) Empty(Icons.Filled.QueueMusic, if (type == 0) "Henüz yeterince dinleme yok.\nBir şarkıyı 30 saniyeden fazla dinleyince burada sayılır." else "Liste boş.")
        else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { Engine.playSongs(ctx, songs, 0); AppState.showNow = true }) { Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Çal") }
                    FilledTonalButton(onClick = { Engine.playSongs(ctx, songs.shuffled(), 0); AppState.showNow = true }) { Icon(Icons.Filled.Shuffle, null); Spacer(Modifier.width(6.dp)); Text("Karışık") }
                    OutlinedButton(onClick = { Playlists.create(SmartLists.NAMES[type], songs.map { it.key }); toast(ctx, "Liste olarak kaydedildi") }) { Text("Kaydet") }
                }
            }
            itemsIndexed(songs, key = { _, s -> s.key }) { i, s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SongRow(s, { Engine.playSongs(ctx, songs, i) }, null) }
                    if (type == 0) Text("${Stats.plays(s.key)}×", color = Accent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 16.dp))
                }
            }
        }
    }
}

// ---------------- etiket düzenleyici ----------------
@Composable
fun TagEditorDialog(s: Song, vm: LibVM, dismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var t by remember { mutableStateOf("") }; var ar by remember { mutableStateOf("") }; var al by remember { mutableStateOf("") }
    var y by remember { mutableStateOf("") }; var g by remember { mutableStateOf("") }; var tr by remember { mutableStateOf("") }
    var cover by remember { mutableStateOf<ByteArray?>(null) }
    var coverChanged by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(s.key) {
        val i = withContext(Dispatchers.IO) { Tags.read(s.path) }
        if (i == null) failed = true else { t = i.title; ar = i.artist; al = i.album; y = i.year; g = i.genre; tr = i.track; loaded = true }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { cover = withContext(Dispatchers.IO) { Tags.prepareCover(ctx, uri) }; coverChanged = cover != null }
    }
    AlertDialog(onDismissRequest = { if (!saving) dismiss() }, title = { Text("Etiketleri düzenle") },
        text = {
            when {
                failed -> Text("Bu dosyanın etiketleri okunamadı (desteklenmeyen biçim).")
                !loaded -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent)
                else -> Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).background(Surf2), contentAlignment = Alignment.Center) {
                            val c = cover
                            if (coverChanged && c != null && c.isNotEmpty()) {
                                val bmp: ImageBitmap? = remember(c) { android.graphics.BitmapFactory.decodeByteArray(c, 0, c.size)?.asImageBitmapCompat() }
                                if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else if (!coverChanged) ArtImage(s.key, s.uri, s.path, Modifier.fillMaxSize())
                            else Icon(Icons.Filled.HideImage, null, tint = Muted)
                        }
                        Column(Modifier.padding(start = 12.dp)) {
                            TextButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Kapak seç") }
                            TextButton(onClick = { cover = ByteArray(0); coverChanged = true }) { Text("Kapağı kaldır") }
                        }
                    }
                    OutlinedTextField(t, { t = it }, label = { Text("Şarkı adı") }, singleLine = true)
                    OutlinedTextField(ar, { ar = it }, label = { Text("Sanatçı") }, singleLine = true)
                    OutlinedTextField(al, { al = it }, label = { Text("Albüm") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(y, { y = it.filter { c -> c.isDigit() }.take(4) }, label = { Text("Yıl") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(tr, { tr = it.filter { c -> c.isDigit() }.take(3) }, label = { Text("Sıra") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(g, { g = it }, label = { Text("Tür") }, singleLine = true)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = loaded && !saving, onClick = {
                saving = true
                scope.launch {
                    val err = withContext(Dispatchers.IO) { Tags.write(ctx, s.path, Tags.Info(t, ar, al, y, g, tr), if (coverChanged) cover else null) }
                    saving = false
                    if (err == null) { Art.invalidate(s.key); toast(ctx, "Etiketler kaydedildi"); dismiss(); delay(800); vm.reload() }
                    else toast(ctx, "Kaydedilemedi: $err")
                }
            }) { Text(if (saving) "Kaydediliyor…" else "Kaydet") }
        },
        dismissButton = { TextButton(onClick = dismiss, enabled = !saving) { Text("Vazgeç") } })
}

private fun android.graphics.Bitmap.asImageBitmapCompat(): ImageBitmap = this.asImageBitmap()

// ---------------- zil sesi ----------------
@Composable
fun RingtoneSheet(s: Song, dismiss: () -> Unit) {
    val ctx = LocalContext.current
    val d = s.duration.coerceAtLeast(1000)
    var range by remember { mutableStateOf(0f..minOf(30_000f, d.toFloat())) }
    var type by remember { mutableIntStateOf(0) }
    var setDefault by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var mp by remember { mutableStateOf<MediaPlayer?>(null) }
    var previewing by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { mp?.release() } }
    LaunchedEffect(previewing, range) {
        if (!previewing) { mp?.pause(); return@LaunchedEffect }
        val m = mp ?: MediaPlayer.create(ctx, Uri.fromFile(File(s.path)))?.also { mp = it } ?: return@LaunchedEffect
        Engine.player?.pause()
        m.seekTo(range.start.toInt()); m.start()
        while (isActive && previewing) { if (m.currentPosition >= range.endInclusive) { m.pause(); previewing = false }; delay(100) }
    }
    ModalBottomSheet(onDismissRequest = { if (!busy) dismiss() }, containerColor = Surf) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            SheetTitle("Zil sesi yap")
            Text(s.title, fontWeight = FontWeight.SemiBold); Text(s.artist, color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Text("${fmtTime(range.start.toLong())} – ${fmtTime(range.endInclusive.toLong())}  (${fmtTime((range.endInclusive - range.start).toLong())})",
                fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Accent)
            RangeSlider(range, { r -> range = if (r.endInclusive - r.start > 60_000f) r.start..(r.start + 60_000f) else r }, valueRange = 0f..d.toFloat())
            Text("En fazla 60 saniye. Nakarat genelde 0:45 – 1:15 arasındadır.", color = Muted, fontSize = 12.sp)
            OutlinedButton(onClick = { previewing = !previewing }, modifier = Modifier.padding(top = 8.dp)) {
                Icon(if (previewing) Icons.Filled.Stop else Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(if (previewing) "Durdur" else "Dinle")
            }
            Section("Tür")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Ringtone.TYPES.forEachIndexed { i, (n, _) -> FilterChip(type == i, { type = i }, label = { Text(n) }) }
            }
            Row(Modifier.fillMaxWidth().clickable { setDefault = !setDefault }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(setDefault, { setDefault = it }); Text("Telefonun varsayılan ${Ringtone.TYPES[type].first.lowercase()} olarak ayarla")
            }
            Button(enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp), onClick = {
                if (setDefault && !Ringtone.canSetDefault(ctx)) {
                    ctx.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    toast(ctx, "Kadraj'a \"Sistem ayarlarını değiştirme\" izni ver, sonra tekrar dokun"); return@Button
                }
                busy = true; previewing = false
                val tp = Ringtone.TYPES[type].second
                Ringtone.make(ctx, s.path, "${s.artist} - ${s.title}", range.start.toLong(), range.endInclusive.toLong(), tp) { uri, err ->
                    busy = false
                    if (uri == null) toast(ctx, "Yapılamadı: ${err ?: "bilinmeyen hata"}")
                    else {
                        val ok = setDefault && Ringtone.setDefault(ctx, uri, tp)
                        toast(ctx, if (ok) "${Ringtone.TYPES[type].first} olarak ayarlandı" else "Kaydedildi: ${Ringtone.TYPES[type].first} listesinde")
                        dismiss()
                    }
                }
            }) { if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text("Kaydet") }
        }
    }
}
