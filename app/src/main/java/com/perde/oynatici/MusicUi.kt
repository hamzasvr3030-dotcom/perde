@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.perde.oynatici

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

// ---------------- kapak ----------------
@Composable
fun ArtImage(key: String, uri: Uri?, path: String?, modifier: Modifier, shape: Shape = RoundedCornerShape(12.dp), px: Int = 256) {
    val ctx = LocalContext.current
    var bmp by remember(key, px) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(key, px) { bmp = Art.load(ctx, key, uri, path, px)?.asImageBitmap() }
    Box(modifier.clip(shape).background(Brush.linearGradient(listOf(AccentDeep, Surf2))), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Filled.MusicNote, null, tint = Accent.copy(alpha = 0.8f), modifier = Modifier.fillMaxSize(0.45f))
    }
}

@Composable
fun AudioBars(playing: Boolean, color: Color = Accent, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "bars")
    Row(modifier.height(16.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(420, 300, 520, 360).forEach { d ->
            val h by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(d), RepeatMode.Reverse), label = "b")
            Box(Modifier.width(3.dp).fillMaxHeight(if (playing) h else 0.25f).clip(RoundedCornerShape(2.dp)).background(color))
        }
    }
}

@Composable
fun SongRow(s: Song, onClick: () -> Unit, onMore: (() -> Unit)?) {
    val current = NowState.key == s.key && NowState.hasItem
    Row(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = { onMore?.invoke() }).padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically) {
        ArtImage(s.key, s.uri, s.path, Modifier.size(50.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, color = if (current) Accent else Color.Unspecified)
            Text("${s.artist} · ${fmtTime(s.duration)}", maxLines = 1, overflow = TextOverflow.Ellipsis, color = Muted, fontSize = 13.sp)
        }
        if (current) AudioBars(NowState.playing, modifier = Modifier.padding(horizontal = 8.dp))
        if (onMore != null) IconButton(onClick = onMore) { Icon(Icons.Filled.MoreVert, "Seçenekler", tint = Muted) }
    }
}

@Composable
private fun PlayButtons(songs: List<Song>) {
    val ctx = LocalContext.current
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = { Engine.playSongs(ctx, songs, 0); AppState.showNow = true }, enabled = songs.isNotEmpty()) {
            Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Tümünü çal")
        }
        FilledTonalButton(onClick = { Engine.playSongs(ctx, songs.shuffled(), 0, shuffle = false); AppState.showNow = true }, enabled = songs.isNotEmpty()) {
            Icon(Icons.Filled.Shuffle, null); Spacer(Modifier.width(6.dp)); Text("Karışık")
        }
    }
}

// ---------------- müzik kütüphanesi ----------------
@Composable
fun MusicScreen(vm: LibVM, go: (Route) -> Unit) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var sel by remember { mutableStateOf<Song?>(null) }
    val songs = vm.songs
    Page("Müzik", null, actions = {
        IconButton(onClick = { go(Route.MusicSearch) }) { Icon(Icons.Filled.Search, "Ara") }
        IconButton(onClick = { if (songs.isNotEmpty()) { Engine.playSongs(ctx, songs.shuffled(), 0); AppState.showNow = true } }) { Icon(Icons.Filled.Shuffle, "Karışık çal") }
    }) {
        Column {
            val tabs = listOf("Şarkılar", "Albümler", "Sanatçılar", "Klasörler", "Listeler")
            ScrollableTabRow(selectedTabIndex = tab, containerColor = Bg, edgePadding = 12.dp, divider = {}) {
                tabs.forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal) }) }
            }
            if (songs.isEmpty()) {
                if (vm.loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) }
                else Empty(Icons.Filled.LibraryMusic, "Telefonda müzik bulunamadı.\nMP3, M4A, FLAC gibi dosyalar burada listelenir.")
                return@Column
            }
            when (tab) {
                0 -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    item { PlayButtons(songs) }
                    itemsIndexed(songs, key = { _, s -> s.key }) { i, s -> SongRow(s, { Engine.playSongs(ctx, songs, i) }) { sel = s } }
                }
                1 -> {
                    val albums = remember(songs) { Music.albums(songs) }
                    LazyVerticalGrid(GridCells.Adaptive(156.dp), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(albums, key = { it.id }) { a ->
                            Column(Modifier.clickable { go(Route.AlbumR(a.id)) }) {
                                val s = a.songs.first()
                                ArtImage(s.key, s.uri, s.path, Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(16.dp), 384)
                                Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                                Text("${a.artist} · ${a.songs.size}", maxLines = 1, overflow = TextOverflow.Ellipsis, color = Muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
                2 -> {
                    val artists = remember(songs) { Music.artists(songs) }
                    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                        items(artists, key = { it.name }) { a ->
                            Row(Modifier.fillMaxWidth().clickable { go(Route.ArtistR(a.name)) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                val s = a.songs.first()
                                ArtImage(s.key, s.uri, s.path, Modifier.size(54.dp), CircleShape)
                                Column(Modifier.padding(start = 12.dp)) {
                                    Text(a.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${a.songs.size} şarkı · ${a.albums} albüm", color = Muted, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
                3 -> {
                    val folders = remember(songs) { Music.folders(songs) }
                    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                        items(folders, key = { it.first }) { (p, l) ->
                            ListItem(headlineContent = { Text(File(p).name) }, supportingContent = { Text("${l.size} şarkı") },
                                leadingContent = { Icon(Icons.Filled.Folder, null, tint = Accent) },
                                modifier = Modifier.clickable { go(Route.MusicFolder(p)) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                        }
                    }
                }
                else -> {
                    val lists = remember(vm.ver) { Playlists.all() }
                    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                        item {
                            ListItem(headlineContent = { Text("Favoriler") }, leadingContent = { Icon(Icons.Filled.Favorite, null, tint = Color(0xFFFF5C7A)) },
                                modifier = Modifier.clickable { go(Route.Favorites) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                        }
                        items(lists, key = { it.id }) { p ->
                            ListItem(headlineContent = { Text(p.name) }, supportingContent = { Text("${p.items.size} öğe") },
                                leadingContent = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = Accent) },
                                modifier = Modifier.clickable { go(Route.PlaylistR(p.id)) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                        }
                        item { TextButton(onClick = { go(Route.Playlists) }, modifier = Modifier.padding(16.dp)) { Icon(Icons.Filled.Add, null); Text("Yeni liste / tüm listeler") } }
                    }
                }
            }
        }
    }
    sel?.let { s -> SongActions(s, vm) { sel = null } }
}

@Composable
private fun SongListPage(title: String, header: @Composable () -> Unit, songs: List<Song>, vm: LibVM, back: () -> Unit) {
    val ctx = LocalContext.current
    var sel by remember { mutableStateOf<Song?>(null) }
    Page(title, back) {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item { header() }
            item { PlayButtons(songs) }
            itemsIndexed(songs, key = { _, s -> s.key }) { i, s -> SongRow(s, { Engine.playSongs(ctx, songs, i) }) { sel = s } }
        }
    }
    sel?.let { s -> SongActions(s, vm) { sel = null } }
}

@Composable
fun AlbumScreen(vm: LibVM, id: Long, back: () -> Unit) {
    val a = remember(vm.songs) { Music.albums(vm.songs).firstOrNull { it.id == id } } ?: run { back(); return }
    SongListPage(a.name, {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val s = a.songs.first()
            ArtImage(s.key, s.uri, s.path, Modifier.size(220.dp).shadow(24.dp, RoundedCornerShape(22.dp)), RoundedCornerShape(22.dp), 512)
            Text(a.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
            Text("${a.artist} · ${a.songs.size} şarkı · ${fmtDur(a.songs.sumOf { it.duration })}", color = Muted)
        }
    }, a.songs, vm, back)
}

@Composable
fun ArtistScreen(vm: LibVM, name: String, go: (Route) -> Unit, back: () -> Unit) {
    val a = remember(vm.songs) { Music.artists(vm.songs).firstOrNull { it.name == name } } ?: run { back(); return }
    SongListPage(a.name, {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val s = a.songs.first()
            ArtImage(s.key, s.uri, s.path, Modifier.size(160.dp), CircleShape, 384)
            Text(a.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            Text("${a.songs.size} şarkı · ${a.albums} albüm", color = Muted)
        }
    }, a.songs, vm, back)
}

@Composable
fun MusicFolderScreen(vm: LibVM, path: String, back: () -> Unit) {
    val songs = remember(vm.songs) { vm.songs.filter { it.folder == path }.sortedWith(compareBy(NaturalOrder) { File(it.path).name }) }
    SongListPage(File(path).name, {}, songs, vm, back)
}

@Composable
fun MusicSearchScreen(vm: LibVM, back: () -> Unit) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    val res = remember(q, vm.songs) {
        val s = q.trim().lowercase(TR)
        if (s.isEmpty()) emptyList() else vm.songs.filter { it.title.lowercase(TR).contains(s) || it.artist.lowercase(TR).contains(s) || it.album.lowercase(TR).contains(s) }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") }
            OutlinedTextField(q, { q = it }, placeholder = { Text("Şarkı, sanatçı, albüm") }, singleLine = true, modifier = Modifier.weight(1f),
                leadingIcon = { Icon(Icons.Filled.Search, null) }, shape = CircleShape)
        }
        if (q.isNotBlank() && res.isEmpty()) Empty(Icons.Filled.SearchOff, "\"$q\" için sonuç yok.")
        else LazyColumn { itemsIndexed(res, key = { _, s -> s.key }) { i, s -> SongRow(s, { Engine.playSongs(ctx, res, i); AppState.showNow = true }, null) } }
    }
}

@Composable
private fun SongActions(s: Song, vm: LibVM, dismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableIntStateOf(0) }
    if (dialog == 0) ModalBottomSheet(onDismissRequest = dismiss, containerColor = Surf) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                ArtImage(s.key, s.uri, s.path, Modifier.size(52.dp))
                Column(Modifier.padding(start = 12.dp)) { Text(s.title, fontWeight = FontWeight.Bold, maxLines = 1); Text(s.artist, color = Muted, fontSize = 13.sp) }
            }
            ActionItem(Icons.Filled.PlayArrow, "Çal") { dismiss(); Engine.playSongs(ctx, listOf(s), 0) }
            ActionItem(Icons.AutoMirrored.Filled.QueueMusic, "Sıradaki olarak çal") {
                val p = Engine.player
                if (p != null && NowState.isMusic) { p.addMediaItem(p.currentMediaItemIndex + 1, Music.item(s)); toast(ctx, "Sıraya eklendi") }
                else Engine.playSongs(ctx, listOf(s), 0)
                dismiss()
            }
            ActionItem(if (Favs.has(s.key)) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, if (Favs.has(s.key)) "Favorilerden çıkar" else "Favorilere ekle") { Favs.toggle(s.key); vm.bump(); dismiss() }
            ActionItem(Icons.AutoMirrored.Filled.PlaylistAdd, "Listeye ekle") { dialog = 1 }
            ActionItem(Icons.Filled.Share, "Paylaş") {
                dismiss(); ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("audio/*").putExtra(Intent.EXTRA_STREAM, s.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Paylaş"))
            }
            ActionItem(Icons.Filled.Info, "Bilgi") { dialog = 3 }
            ActionItem(Icons.Filled.Delete, "Sil", Color(0xFFFF6B6B)) { dialog = 2 }
        }
    }
    when (dialog) {
        1 -> PlaylistPickerDialog(listOf(s.key), dismiss)
        2 -> AlertDialog(onDismissRequest = dismiss, title = { Text("Şarkı silinsin mi?") }, text = { Text(s.title) },
            confirmButton = { TextButton(onClick = {
                dismiss(); scope.launch { val ok = withContext(Dispatchers.IO) { Library.deletePath(ctx, s.path, s.uri) }; toast(ctx, if (ok) "Silindi" else "Silinemedi"); vm.reload() }
            }) { Text("Sil", color = Color(0xFFFF6B6B)) } },
            dismissButton = { TextButton(onClick = dismiss) { Text("Vazgeç") } })
        3 -> AlertDialog(onDismissRequest = dismiss, title = { Text("Şarkı bilgisi") }, confirmButton = { TextButton(onClick = dismiss) { Text("Tamam") } },
            text = {
                Column {
                    listOf("Ad" to s.title, "Sanatçı" to s.artist, "Albüm" to s.album, "Süre" to fmtTime(s.duration), "Boyut" to fmtSize(s.size), "Konum" to s.path)
                        .forEach { (k, v) -> Row(Modifier.padding(vertical = 3.dp)) { Text(k, Modifier.width(80.dp), color = Muted); Text(v) } }
                }
            })
    }
}

// ---------------- mini oynatıcı ----------------
@Composable
fun MiniPlayer(open: () -> Unit) {
    if (!NowState.hasItem || !NowState.isMusic || AppState.showNow) return
    val ctx = LocalContext.current
    val view = LocalView.current
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) { Engine.player?.let { p -> val d = p.duration; progress = if (d > 0 && d != C.TIME_UNSET) p.currentPosition.toFloat() / d else 0f }; delay(500) }
    }
    val s = Music.byKey(NowState.key)
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp).clip(RoundedCornerShape(18.dp)).background(Surf2).clickable(onClick = open)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ArtImage(NowState.key, s?.uri, s?.path ?: NowState.key.takeIf { it.startsWith("/") }, Modifier.size(46.dp), RoundedCornerShape(12.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(NowState.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(NowState.artist.ifEmpty { if (Engine.audioOnly) "Video · sadece ses" else "" }, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Muted, fontSize = 12.sp)
            }
            IconButton(onClick = { haptic(view); Engine.player?.let { if (it.isPlaying) it.pause() else it.play() } }) {
                Icon(if (NowState.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Oynat/Duraklat", modifier = Modifier.size(30.dp))
            }
            IconButton(onClick = { Engine.player?.seekToNextMediaItem() }) { Icon(Icons.Filled.SkipNext, "Sonraki") }
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(Color(0x22FFFFFF))) { Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(Accent)) }
    }
}

// ---------------- şimdi çalıyor ----------------
private enum class NpSheet { QUEUE, SLEEP, EQ, PLAYLIST }

@Composable
fun NowPlayingScreen(vm: LibVM, close: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val p = Engine.player
    if (p == null || !NowState.hasItem) { LaunchedEffect(Unit) { close() }; return }
    @Suppress("UNUSED_VARIABLE") val v = NowState.ver
    val key = NowState.key
    val song = Music.byKey(key)
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var drag by remember { mutableStateOf<Float?>(null) }
    var showLyrics by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<NpSheet?>(null) }
    var repeat by remember { mutableIntStateOf(p.repeatMode) }
    var shuffle by remember { mutableStateOf(p.shuffleModeEnabled) }
    var fav by remember(key) { mutableStateOf(Favs.has(key)) }
    var sleepLabel by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            pos = p.currentPosition; dur = p.duration.takeIf { it != C.TIME_UNSET } ?: 0
            sleepLabel = when { Engine.sleepAt > 0 -> fmtTime(Engine.sleepAt - SystemClock.elapsedRealtime()); Engine.sleepEnd -> "şarkı sonu"; else -> "" }
            delay(200)
        }
    }
    // kapaktan renk
    var tint by remember { mutableStateOf(AccentDeep) }
    var art by remember(key) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(key) {
        val b = Art.load(ctx, key, song?.uri, song?.path ?: key.takeIf { it.startsWith("/") }, 720)
        art = b?.asImageBitmap()
        tint = if (b != null) withContext(Dispatchers.Default) {
            val pal = Palette.from(b).generate()
            Color(pal.getVibrantColor(pal.getDominantColor(0xFF333333.toInt())))
        } else AccentDeep
    }
    val bgTop by animateColorAsState(lerp(tint, Color.Black, 0.35f), tween(600), label = "bg")
    val artScale by animateFloatAsState(if (NowState.playing) 1f else 0.86f, tween(400), label = "art")
    val lyrics = remember(key) { mutableStateOf<List<Pair<Long, String>>>(emptyList()) }
    LaunchedEffect(key) { lyrics.value = if (key.startsWith("/")) withContext(Dispatchers.IO) { Lrc.load(key) } else emptyList() }

    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(bgTop, Color(0xFF0B0B0E)))).systemBarsPadding().padding(horizontal = 22.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassButton(Icons.Filled.KeyboardArrowDown, "Kapat", 42.dp, 26.dp) { close() }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ŞİMDİ ÇALIYOR", fontSize = 11.sp, letterSpacing = 2.sp, color = Color.White.copy(alpha = 0.7f))
                Text(song?.album ?: if (Engine.audioOnly) "Video (sadece ses)" else "", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            }
            var menu by remember { mutableStateOf(false) }
            Box {
                GlassButton(Icons.Filled.MoreHoriz, "Diğer", 42.dp) { menu = true }
                DropdownMenu(menu, { menu = false }) {
                    if (Engine.audioOnly) DropdownMenuItem({ Text("Videoya dön") }, {
                        menu = false; close()
                        ctx.startActivity(Intent(ctx, PlayerActivity::class.java).putExtra(PlayerActivity.EXTRA_BACK_TO_VIDEO, true))
                    }, leadingIcon = { Icon(Icons.Filled.OndemandVideo, null) })
                    DropdownMenuItem({ Text("Listeye ekle") }, { menu = false; sheet = NpSheet.PLAYLIST }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) })
                    if (song != null) DropdownMenuItem({ Text("Paylaş") }, {
                        menu = false; ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("audio/*").putExtra(Intent.EXTRA_STREAM, song.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Paylaş"))
                    }, leadingIcon = { Icon(Icons.Filled.Share, null) })
                    DropdownMenuItem({ Text("Çalmayı durdur") }, {
                        menu = false; ctx.stopService(Intent(ctx, PlaybackService::class.java)); Engine.release(); close()
                    }, leadingIcon = { Icon(Icons.Filled.Stop, null) })
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (showLyrics) LyricsView(lyrics.value, pos) { p.seekTo(it) }
            else Box(Modifier.fillMaxWidth().aspectRatio(1f).scale(artScale).shadow(30.dp, RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(tint, AccentDeep))), contentAlignment = Alignment.Center) {
                val b = art
                if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Filled.MusicNote, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.fillMaxSize(0.4f))
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(NowState.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.basicMarquee())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AudioBars(NowState.playing, Color.White.copy(alpha = 0.8f), Modifier.padding(end = 8.dp))
                    Text(NowState.artist.ifEmpty { "Bilinmeyen sanatçı" }, color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = { haptic(view); fav = Favs.toggle(key) }) {
                Icon(if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favori", tint = if (fav) Color(0xFFFF5C7A) else Color.White, modifier = Modifier.size(28.dp))
            }
        }

        Slider(value = drag ?: if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f,
            onValueChange = { drag = it }, onValueChangeFinished = { drag?.let { p.seekTo((it * dur).toLong()) }; drag = null },
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.25f)),
            modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(fmtTime(drag?.let { (it * dur).toLong() } ?: pos), fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
            Spacer(Modifier.weight(1f))
            Text("−" + fmtTime((dur - pos).coerceAtLeast(0)), fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { shuffle = !shuffle; p.shuffleModeEnabled = shuffle; Prefs.musicShuffle = shuffle }) {
                Icon(Icons.Filled.Shuffle, "Karışık", tint = if (shuffle) Accent else Color.White.copy(alpha = 0.7f))
            }
            GlassButton(Icons.Filled.SkipPrevious, "Önceki", 56.dp, 32.dp) { if (p.currentPosition > 4000) p.seekTo(0) else p.seekToPreviousMediaItem() }
            Box(Modifier.size(80.dp).clip(CircleShape).background(Color.White).clickable { haptic(view); if (p.isPlaying) p.pause() else { if (p.playbackState == Player.STATE_ENDED) p.seekTo(0); p.play() } },
                contentAlignment = Alignment.Center) {
                Icon(if (NowState.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Oynat/Duraklat", tint = Color(0xFF111114), modifier = Modifier.size(46.dp))
            }
            GlassButton(Icons.Filled.SkipNext, "Sonraki", 56.dp, 32.dp) { p.seekToNextMediaItem() }
            IconButton(onClick = {
                repeat = when (repeat) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF }
                p.repeatMode = repeat; Prefs.musicRepeat = when (repeat) { Player.REPEAT_MODE_ONE -> 1; Player.REPEAT_MODE_ALL -> 2; else -> 0 }
            }) {
                Icon(if (repeat == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat, "Tekrar",
                    tint = if (repeat != Player.REPEAT_MODE_OFF) Accent else Color.White.copy(alpha = 0.7f))
            }
        }

        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            NpTool(Icons.Filled.Lyrics, "Sözler", showLyrics) { showLyrics = !showLyrics }
            NpTool(Icons.AutoMirrored.Filled.QueueMusic, "Sıra", false) { sheet = NpSheet.QUEUE }
            NpTool(Icons.Filled.Bedtime, if (sleepLabel.isEmpty()) "Uyku" else sleepLabel, sleepLabel.isNotEmpty()) { sheet = NpSheet.SLEEP }
            NpTool(Icons.Filled.Equalizer, "Ekolayzer", Prefs.eqOn) { sheet = NpSheet.EQ }
        }
    }

    sheet?.let { s ->
        ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = Surf, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            sheetGesturesEnabled = s != NpSheet.QUEUE) {
            when (s) {
                NpSheet.QUEUE -> QueueContent()
                else -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                    when (s) {
                        NpSheet.SLEEP -> SleepSheet { m ->
                            Engine.sleepAt = 0; Engine.sleepEnd = false; p.pauseAtEndOfMediaItems = false
                            when {
                                m > 0 -> { Engine.sleepAt = SystemClock.elapsedRealtime() + m * 60_000L; toast(ctx, "$m dakika sonra durdurulacak") }
                                m == -1 -> { Engine.sleepEnd = true; p.pauseAtEndOfMediaItems = true; toast(ctx, "Bu şarkı bitince durdurulacak") }
                                else -> toast(ctx, "Uyku zamanlayıcısı kapalı")
                            }
                            sheet = null
                        }
                        NpSheet.EQ -> EqContent()
                        NpSheet.PLAYLIST -> PlaylistPickerContent(listOf(key)) { msg -> toast(ctx, msg); sheet = null }
                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun NpTool(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, label, tint = if (active) Accent else Color.White.copy(alpha = 0.85f))
        Text(label, fontSize = 11.sp, color = if (active) Accent else Color.White.copy(alpha = 0.7f))
    }
}

@Composable
private fun LyricsView(lines: List<Pair<Long, String>>, pos: Long, seek: (Long) -> Unit) {
    if (lines.isEmpty()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Icon(Icons.Filled.Lyrics, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
            Text("Bu şarkı için söz dosyası bulunamadı.\nŞarkıyla aynı adlı .lrc dosyasını aynı klasöre koyarsan sözler burada akar.",
                textAlign = TextAlign.Center, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 10.dp))
        }
        return
    }
    val cur = lines.indexOfLast { it.first <= pos + 300 }.coerceAtLeast(0)
    val state = rememberLazyListState()
    LaunchedEffect(cur) { state.animateScrollToItem((cur - 3).coerceAtLeast(0)) }
    LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 40.dp)) {
        itemsIndexed(lines) { i, (t, s) ->
            val active = i == cur
            Text(s.ifEmpty { "♪" }, fontSize = if (active) 24.sp else 19.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                color = if (active) Color.White else Color.White.copy(alpha = 0.4f), lineHeight = 30.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { seek(t) }.padding(vertical = 8.dp, horizontal = 4.dp))
        }
    }
}

@Composable
private fun QueueContent() {
    val p = Engine.player ?: return
    val density = LocalDensity.current
    val rowH = 64.dp
    val rowPx = with(density) { rowH.toPx() }
    var ver by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val nv = NowState.ver
    val items = remember(ver, NowState.ver) { (0 until p.mediaItemCount).map { p.getMediaItemAt(it) } }
    var dragIdx by remember { mutableIntStateOf(-1) }
    var dragOff by remember { mutableFloatStateOf(0f) }
    Column(Modifier.padding(bottom = 20.dp)) {
        Text("Çalma sırası (${items.size})", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        Text("Sıralamak için sağdaki tutamaçtan sürükle", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
        LazyColumn(Modifier.heightIn(max = 520.dp), state = rememberLazyListState((p.currentMediaItemIndex - 2).coerceAtLeast(0))) {
            itemsIndexed(items, key = { i, it -> "${it.mediaId}#$i" }) { i, it ->
                val current = i == p.currentMediaItemIndex
                Row(Modifier.zIndex(if (i == dragIdx) 1f else 0f).offset { IntOffset(0, if (i == dragIdx) dragOff.roundToInt() else 0) }
                    .fillMaxWidth().height(rowH).background(if (i == dragIdx) Surf2 else if (current) Accent.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable { p.seekTo(i, 0); p.play(); ver++ }.padding(start = 20.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    val s = Music.byKey(it.mediaId)
                    ArtImage(it.mediaId, s?.uri, s?.path, Modifier.size(44.dp), RoundedCornerShape(10.dp), 128)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(it.mediaMetadata.title?.toString() ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (current) Accent else Color.Unspecified, fontWeight = FontWeight.Medium)
                        Text(it.mediaMetadata.artist?.toString() ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, color = Muted, fontSize = 12.sp)
                    }
                    if (!current) IconButton(onClick = { p.removeMediaItem(i); ver++ }) { Icon(Icons.Filled.Close, "Çıkar", tint = Muted) }
                    Icon(Icons.Filled.DragHandle, "Sürükle", tint = Muted, modifier = Modifier.size(44.dp).padding(10.dp).pointerInput(i, items.size) {
                        detectDragGestures(
                            onDragStart = { dragIdx = i; dragOff = 0f },
                            onDrag = { c, d -> c.consume(); dragOff += d.y },
                            onDragEnd = {
                                val to = (dragIdx + (dragOff / rowPx).roundToInt()).coerceIn(0, items.size - 1)
                                if (dragIdx >= 0 && to != dragIdx) p.moveMediaItem(dragIdx, to)
                                dragIdx = -1; dragOff = 0f; ver++
                            },
                            onDragCancel = { dragIdx = -1; dragOff = 0f })
                    })
                }
            }
        }
    }
}
