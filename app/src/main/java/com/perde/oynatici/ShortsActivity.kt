@file:Suppress("UnsafeOptInUsageError")
@file:OptIn(ExperimentalMaterial3Api::class)

package com.perde.oynatici

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

data class ShortItem(val key: String, val title: String, val uploader: String?, val thumb: String?, val local: Boolean)

/** yt-dlp akışından oynatılabilir kaynak (görüntü + ses ayrıysa birleştirir). */
fun webSource(ctx: android.content.Context, s: Web.Stream, id: String): MediaSource {
    val f = DefaultMediaSourceFactory(AnyDataSource.Factory(ctx))
    fun item(u: String) = MediaItem.Builder().setUri(u).setMediaId(id).apply { if ("m3u8" in u) setMimeType(MimeTypes.APPLICATION_M3U8) }.build()
    val v = f.createMediaSource(item(s.videoUrl))
    return if (s.audioUrl != null) MergingMediaSource(true, true, v, f.createMediaSource(item(s.audioUrl))) else v
}

/** Kısa videolar: dikey kaydırarak izleme (YouTube Shorts + telefondaki dikey kısa videolar). */
class ShortsActivity : ComponentActivity() {
    lateinit var player: ExoPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Engine.player?.pause()
        player = ExoPlayer.Builder(this).setMediaSourceFactory(DefaultMediaSourceFactory(AnyDataSource.Factory(this))).build()
        player.repeatMode = Player.REPEAT_MODE_ONE
        setContent { PerdeTheme { ShortsScreen(this) } }
    }

    override fun onStop() { super.onStop(); player.pause() }
    override fun onDestroy() { player.release(); super.onDestroy() }
}

@Composable
private fun ShortsScreen(a: ShortsActivity) {
    val scope = rememberCoroutineScope()
    var source by remember { mutableIntStateOf(0) }  // 0 keşfet, 1 telefonum
    var query by remember { mutableStateOf("") }
    var askQuery by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf<List<ShortItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var muted by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(true) }
    var dlFor by remember { mutableStateOf<ShortItem?>(null) }

    fun load() {
        loading = true; err = null; items = emptyList()
        scope.launch {
            try {
                items = if (source == 1) Library.cache.filter { it.height > it.width && it.duration in 1..180_000 }.sortedByDescending { it.dateAdded }
                    .map { ShortItem(it.path, it.title, null, null, true) }
                else Web.search(a, "#shorts " + query.ifBlank { "türkiye" }, 30).filter { it.durationSec in 1..180 }
                    .map { ShortItem(if ("/shorts/" in it.url || it.url.contains("watch")) it.url else "https://www.youtube.com/shorts/${it.id}", it.title, it.uploader, it.thumb, false) }
                if (items.isEmpty()) err = if (source == 1) "Telefonda dikey kısa video bulunamadı." else "Sonuç bulunamadı."
            } catch (e: Exception) { err = Web.explain(e) }
            loading = false
        }
    }
    LaunchedEffect(source) { load() }

    val pager = rememberPagerState { items.size }
    var current by remember { mutableIntStateOf(-1) }
    LaunchedEffect(pager.currentPage, items) {
        if (items.isEmpty()) return@LaunchedEffect
        val i = pager.currentPage.coerceIn(0, items.size - 1); current = i
        val it = items[i]
        a.player.stop()
        try {
            if (it.local) a.player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(it.key))))
            else a.player.setMediaSource(webSource(a, Web.resolve(a, it.key), it.key))
            a.player.prepare(); a.player.playWhenReady = true; playing = true
        } catch (e: Exception) { err = Web.explain(e) }
        // sonraki iki videoyu önceden hazırla
        items.drop(i + 1).take(2).filter { !it.local }.forEach { n -> launch { try { Web.resolve(a, n.key) } catch (_: Exception) { } } }
    }
    LaunchedEffect(muted) { a.player.volume = if (muted) 0f else 1f }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (items.isNotEmpty()) VerticalPager(pager, Modifier.fillMaxSize(), key = { items[it].key }) { page ->
            val it = items[page]
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { if (a.player.isPlaying) { a.player.pause(); playing = false } else { a.player.play(); playing = true } }) }) {
                if (!it.local && it.thumb != null) AsyncImage(it.thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                if (page == current) AndroidView(factory = { c -> PlayerView(c).apply {
                    useController = false; player = a.player; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT) } }, modifier = Modifier.fillMaxSize())
                AnimatedVisibility(page == current && !playing, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(84.dp))
                }
                // alt bilgi
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                    .navigationBarsPadding().padding(start = 16.dp, end = 84.dp, bottom = 20.dp, top = 40.dp)) {
                    it.uploader?.let { u -> Text("@$u", color = Color.White, fontWeight = FontWeight.Bold) }
                    Text(it.title, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                }
                // sağ butonlar
                Column(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 10.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    var fav by remember(it.key) { mutableStateOf(Favs.has(it.key)) }
                    SideBtn(if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Beğen", if (fav) Color(0xFFFF5C7A) else Color.White) { fav = Favs.toggle(it.key) }
                    if (!it.local) SideBtn(Icons.Filled.Download, "İndir") { dlFor = it }
                    SideBtn(Icons.Filled.Share, "Paylaş") {
                        a.startActivity(Intent.createChooser(if (it.local) Intent(Intent.ACTION_SEND).setType("video/*").putExtra(Intent.EXTRA_STREAM, Uri.fromFile(File(it.key)))
                            else Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, it.key), "Paylaş"))
                    }
                    SideBtn(if (muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp, if (muted) "Sessiz" else "Ses") { muted = !muted }
                    SideBtn(Icons.Filled.Fullscreen, "Tam ekran") { a.player.pause(); PlayerActivity.openKeys(a, listOf(it.key), listOf(it.title), 0) }
                }
            }
        }
        // üst çubuk
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent)))
            .statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { a.finish() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = Color.White) }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                listOf("Keşfet", "Telefonum").forEachIndexed { i, n ->
                    Text(n, color = if (source == i) Color.White else Color.White.copy(alpha = 0.55f), fontWeight = if (source == i) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 17.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { if (source != i) { source = i } }.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
            if (source == 0) IconButton(onClick = { askQuery = true }) { Icon(Icons.Filled.Search, "Konu ara", tint = Color.White) } else Spacer(Modifier.width(48.dp))
        }
        if (loading) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Accent)
        err?.let { if (items.isEmpty()) Text(it, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(32.dp)) }
    }

    if (askQuery) {
        var q by remember { mutableStateOf(query) }
        AlertDialog(onDismissRequest = { askQuery = false }, title = { Text("Hangi konuda kısa video?") },
            text = { OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text("örn. komik, futbol, yemek") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { query = q; askQuery = false; load() })) },
            confirmButton = { TextButton(onClick = { query = q; askQuery = false; load() }) { Text("Ara") } },
            dismissButton = { TextButton(onClick = { askQuery = false }) { Text("Vazgeç") } })
    }
    dlFor?.let { it ->
        ModalBottomSheet(onDismissRequest = { dlFor = null }, containerColor = Surf) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                DlOptionsContent(false, 1, it.key) { o, at -> Dl.addSingles(a, listOf(Triple(it.key, it.title, it.thumb)), o, at); dlFor = null; toast(a, "İndirme başladı") }
            }
        }
    }
}

@Composable
private fun SideBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0x55000000)), contentAlignment = Alignment.Center) { Icon(icon, label, tint = tint, modifier = Modifier.size(28.dp)) }
        Text(label, color = Color.White, fontSize = 11.sp)
    }
}
