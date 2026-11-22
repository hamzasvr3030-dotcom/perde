@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.perde.oynatici

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.border
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
import androidx.compose.ui.layout.layout
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectHorizontalDragGestures

// ---------------- kapak ----------------
@Composable
fun ArtImage(key: String, uri: Uri?, path: String?, modifier: Modifier, shape: Shape = RoundedCornerShape(12.dp), px: Int = 256) {
    val ctx = LocalContext.current
    var bmp by remember(key, px) { mutableStateOf<ImageBitmap?>(Art.peek(key, px)?.asImageBitmap()) }
    LaunchedEffect(key, px, Art.ver) { bmp = Art.load(ctx, key, uri, path, px)?.asImageBitmap() }
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
fun SongRow(s: Song, onClick: () -> Unit, onMore: (() -> Unit)?, picked: Boolean? = null, onLong: (() -> Unit)? = null) {
    val current = NowState.key == s.key && NowState.hasItem
    Row(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = { (onLong ?: onMore)?.invoke() })
        .background(if (picked == true) Accent.copy(alpha = 0.12f) else Color.Transparent).padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (picked != null) SelMark(picked)
        ArtImage(s.key, s.uri, s.path, Modifier.size(54.dp), RoundedCornerShape(12.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, color = if (current) Accent else Color.Unspecified)
            Text("${s.artist} · ${fmtTime(s.duration)}", maxLines = 1, overflow = TextOverflow.Ellipsis, color = Muted, fontSize = 13.sp)
        }
        if (current) AudioBars(NowState.playing, modifier = Modifier.padding(horizontal = 8.dp))
        if (onMore != null && picked == null) IconButton(onClick = onMore) { Icon(Icons.Filled.MoreHoriz, "Seçenekler", tint = Muted) }
    }
}

/** Seçim destekli şarkı satırı: seçim varken dokunmak işaretler, basılı tutmak seçimi başlatır. */
@Composable
fun PickSongRow(s: Song, picked: MutableList<String>, onPlay: () -> Unit, onMore: () -> Unit) {
    val selecting = picked.isNotEmpty()
    SongRow(s, { if (selecting) picked.toggle(s.key) else onPlay() }, onMore, if (selecting) s.key in picked else null) { picked.toggle(s.key) }
}

@Composable
private fun PlayButtons(songs: List<Song>) {
    val ctx = LocalContext.current
    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { Engine.playSongs(ctx, songs, 0); AppState.showNow = true }, enabled = songs.isNotEmpty(), modifier = Modifier.weight(1f).height(52.dp), shape = CircleShape) {
            Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Tümünü çal", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Button(onClick = { Engine.playSongs(ctx, songs, songs.indices.random(), shuffle = true); AppState.showNow = true }, enabled = songs.isNotEmpty(),
            modifier = Modifier.weight(1f).height(52.dp), shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = Surf2, contentColor = Accent)) {
            Icon(Icons.Filled.Shuffle, null); Spacer(Modifier.width(6.dp)); Text("Karışık", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
        }
    }
}

@Composable
private fun MusicBoostContent() {
    var v by remember { mutableFloatStateOf(Prefs.musicBoost.toFloat()) }
    Text("Ses yükseltme", fontSize = 21.sp, fontWeight = FontWeight.Bold)
    Text("Telefonun sesi en sonda olsa bile müziği daha yüksek çalar. Çok yükseltince bazı şarkılarda cızırtı olabilir.",
        color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
    Text("%${100 + v.roundToInt()}", fontSize = 40.sp, fontWeight = FontWeight.Black, color = if (v > 0) Accent else Ink)
    Slider(v, { v = it; Prefs.musicBoost = it.roundToInt(); Engine.applyMusicBoost() }, valueRange = 0f..100f, steps = 9)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0, 25, 50, 100).forEach { x ->
            FilterChip(v.roundToInt() == x, { v = x.toFloat(); Prefs.musicBoost = x; Engine.applyMusicBoost() }, label = { Text(if (x == 0) "Kapalı" else "%${100 + x}") })
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
    val picked = remember { mutableStateListOf<String>() }
    LaunchedEffect(tab) { picked.clear() }
    Page("Müzik", null, actions = {
        IconButton(onClick = { go(Route.MusicSearch) }) { Icon(Icons.Filled.Search, "Ara") }
        IconButton(onClick = { AppState.eqSheet = true }) { Icon(Icons.Filled.Equalizer, "Ekolayzer") }
        IconButton(onClick = { go(Route.SettingsPage(1)) }) { Icon(Icons.Filled.Settings, "Müzik ayarları") }
        IconButton(onClick = { go(Route.ListenHistR) }) { Icon(Icons.Filled.History, "Dinleme geçmişi") }
        IconButton(onClick = { if (songs.isNotEmpty()) { Engine.playSongs(ctx, songs, songs.indices.random(), shuffle = true); AppState.showNow = true } }) { Icon(Icons.Filled.Shuffle, "Karışık çal") }
    }) {
        Column {
            val tabs = listOf("Senin için", "Şarkılar", "Albümler", "Sanatçılar", "Klasörler", "Listeler")
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tabs.size) { i -> Pill(tabs[i], tab == i) { tab = i } }
            }
            if (songs.isEmpty()) {
                if (vm.loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) }
                else Empty(Icons.Filled.LibraryMusic, "Telefonda müzik bulunamadı.\nMP3, M4A, FLAC gibi dosyalar burada listelenir.")
                return@Column
            }
            when (tab) {
                0 -> ForYouPage(vm, go)
                1 -> Box(Modifier.fillMaxSize()) {
                    LazyColumn(contentPadding = PaddingValues(bottom = if (picked.isEmpty()) 24.dp else 150.dp)) {
                        item { PlayButtons(songs) }
                        itemsIndexed(songs, key = { _, s -> s.key }) { i, s -> PickSongRow(s, picked, { Engine.playSongs(ctx, songs, i) }) { sel = s } }
                    }
                    SongSelBar(songs, picked, vm, Modifier.align(Alignment.BottomCenter))
                }
                2 -> {
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
                3 -> {
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
                4 -> {
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
                        item { Text("Akıllı listeler", color = Accent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)) }
                        items(SmartLists.NAMES.size) { t ->
                            ListItem(headlineContent = { Text(SmartLists.NAMES[t]) }, supportingContent = { Text("Kendiliğinden güncellenir") },
                                leadingContent = { Icon(listOf(Icons.Filled.Whatshot, Icons.Filled.NewReleases, Icons.Filled.History, Icons.Filled.HearingDisabled)[t], null, tint = Accent) },
                                modifier = Modifier.clickable { go(Route.SmartList(t)) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                        }
                        item { Text("Listelerim", color = Accent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)) }
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
    val picked = remember { mutableStateListOf<String>() }
    Page(title, back) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(contentPadding = PaddingValues(bottom = if (picked.isEmpty()) 24.dp else 150.dp)) {
                item { header() }
                item { PlayButtons(songs) }
                itemsIndexed(songs, key = { _, s -> s.key }) { i, s -> PickSongRow(s, picked, { Engine.playSongs(ctx, songs, i) }) { sel = s } }
            }
            SongSelBar(songs, picked, vm, Modifier.align(Alignment.BottomCenter))
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
    val picked = remember { mutableStateListOf<String>() }
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
        else Box(Modifier.fillMaxSize()) {
            LazyColumn(contentPadding = PaddingValues(bottom = if (picked.isEmpty()) 24.dp else 150.dp)) { itemsIndexed(res, key = { _, s -> s.key }) { i, s ->
                val selecting = picked.isNotEmpty()
                SongRow(s, { if (selecting) picked.toggle(s.key) else { Engine.playSongs(ctx, res, i); AppState.showNow = true } }, null, if (selecting) s.key in picked else null) { picked.toggle(s.key) }
            } }
            SongSelBar(res, picked, vm, Modifier.align(Alignment.BottomCenter))
        }
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
                val playing = Engine.player != null && NowState.isMusic
                Engine.enqueue(ctx, listOf(s), next = true); if (playing) toast(ctx, "Bu şarkıdan sonra çalacak"); dismiss()
            }
            ActionItem(Icons.Filled.Radio, "Bu şarkıdan radyo başlat") { dismiss(); Mixes.startRadio(ctx, s, vm.songs) }
            ActionItem(Icons.Filled.AutoAwesome, "Story olarak paylaş") { dismiss(); StoryCard.share(ctx, s.key, s.title, s.artist) }
            ActionItem(Icons.AutoMirrored.Filled.PlaylistAdd, "Sıranın sonuna ekle") {
                val playing = Engine.player != null && NowState.isMusic
                Engine.enqueue(ctx, listOf(s), next = false); if (playing) toast(ctx, "Sıranın sonuna eklendi"); dismiss()
            }
            ActionItem(if (Favs.has(s.key)) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, if (Favs.has(s.key)) "Favorilerden çıkar" else "Favorilere ekle") { Favs.toggle(s.key); vm.bump(); dismiss() }
            ActionItem(Icons.AutoMirrored.Filled.PlaylistAdd, "Listeye ekle") { dialog = 1 }
            ActionItem(Icons.Filled.Share, "Paylaş") {
                dismiss(); ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("audio/*").putExtra(Intent.EXTRA_STREAM, s.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Paylaş"))
            }
            ActionItem(Icons.Filled.Edit, "Etiketleri düzenle") { dialog = 4 }
            ActionItem(Icons.Filled.NotificationsActive, "Zil sesi yap") { dialog = 5 }
            ActionItem(Icons.Filled.Info, "Bilgi") { dialog = 3 }
            ActionItem(Icons.Filled.Delete, "Sil", Color(0xFFFF6B6B)) { dialog = 2 }
        }
    }
    when (dialog) {
        1 -> PlaylistPickerDialog(listOf(s.key), dismiss)
        4 -> TagEditorDialog(s, vm, dismiss)
        5 -> RingtoneSheet(s, dismiss)
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
        Box(Modifier.fillMaxWidth().height(3.dp).background(Ink.copy(alpha = 0.13f))) { Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(Accent)) }
    }
}

// ---------------- şimdi çalıyor ----------------
private enum class NpSheet { QUEUE, SLEEP, EQ, PLAYLIST, SETTINGS, BOOST, FX }

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
    var npView by remember { mutableIntStateOf(0) }   // 0 kapak, 1 sözler, 2 ses dalgası
    var lyricsSrc by remember { mutableStateOf("") }
    var sheet by remember { mutableStateOf<NpSheet?>(null) }
    var repeat by remember { mutableIntStateOf(p.repeatMode) }
    var shuffle by remember { mutableStateOf(Engine.shuffled) }
    var fav by remember(key) { mutableStateOf(Favs.has(key)) }
    var sleepLabel by remember { mutableStateOf("") }
    LaunchedEffect(p) {
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
    LaunchedEffect(key) {
        lyrics.value = emptyList(); lyricsSrc = ""
        val local = if (key.startsWith("/")) withContext(Dispatchers.IO) { Lrc.load(key) } else emptyList()
        if (local.isNotEmpty()) { lyrics.value = local; lyricsSrc = "Yerel .lrc dosyası"; return@LaunchedEffect }
        if (Prefs.onlineLyrics) {
            val net = LyricsNet.get(ctx, key, NowState.artist, NowState.title, (dur / 1000).toInt().takeIf { it > 0 } ?: ((song?.duration ?: 0) / 1000).toInt())
            if (net.isNotEmpty()) { lyrics.value = net; lyricsSrc = "Sözler: LRCLIB" }
        }
    }

    val look = ThemeState.style.play
    val artShape = when (look.art) { 1 -> CircleShape; 2 -> RoundedCornerShape(6.dp); 3 -> RoundedCornerShape(34.dp); else -> RoundedCornerShape(Rad + 8.dp) }
    // plak: çalarken döner, durunca olduğu yerde kalır
    var spin by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(NowState.playing, look.art) {
        if (look.art != 1 || !NowState.playing) return@LaunchedEffect
        var last = 0L
        while (true) { androidx.compose.runtime.withFrameNanos { t -> if (last != 0L) spin = (spin + (t - last) / 1e9f * 24f) % 360f; last = t } }
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalContentColor provides NpFg, LocalGlassFg provides NpFg) {
    Box(Modifier.fillMaxSize()) {
    NpBackground(look.bg, bgTop, tint, art)
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 22.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassButton(Icons.Filled.KeyboardArrowDown, "Kapat", 42.dp, 26.dp) { close() }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ŞİMDİ ÇALIYOR", fontSize = 11.sp, letterSpacing = 2.sp, color = NpFg.copy(alpha = 0.7f))
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
                    if (song != null) DropdownMenuItem({ Text("Bu şarkıdan radyo") }, { menu = false; Mixes.startRadio(ctx, song, Music.cache) }, leadingIcon = { Icon(Icons.Filled.Radio, null) })
                    DropdownMenuItem({ Text("Listeye ekle") }, { menu = false; sheet = NpSheet.PLAYLIST }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) })
                    if (song != null) DropdownMenuItem({ Text("Paylaş") }, {
                        menu = false; ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("audio/*").putExtra(Intent.EXTRA_STREAM, song.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Paylaş"))
                    }, leadingIcon = { Icon(Icons.Filled.Share, null) })
                    DropdownMenuItem({ Text("Story olarak paylaş") }, { menu = false; StoryCard.share(ctx, key, NowState.title, NowState.artist) }, leadingIcon = { Icon(Icons.Filled.AutoAwesome, null) })
                    if (song != null) DropdownMenuItem({ Text("Zil sesi yap") }, { menu = false; RingReq.song = song }, leadingIcon = { Icon(Icons.Filled.NotificationsActive, null) })
                    DropdownMenuItem({ Text("Çalma ayarları") }, { menu = false; sheet = NpSheet.SETTINGS }, leadingIcon = { Icon(Icons.Filled.Tune, null) })
                    DropdownMenuItem({ Text("Çalmayı durdur") }, {
                        menu = false; ctx.stopService(Intent(ctx, PlaybackService::class.java)); Engine.release(); close()
                    }, leadingIcon = { Icon(Icons.Filled.Stop, null) })
                }
            }
        }

        // kapağı sağa/sola kaydırarak şarkı değiştir
        val swipe = remember { androidx.compose.animation.core.Animatable(0f) }
        val slotPx = remember { mutableFloatStateOf(1f) }
        val swScope = rememberCoroutineScope()
        val view = LocalView.current
        LaunchedEffect(key) {
            if (npView != 0) { if (swipe.value != 0f) { swipe.snapTo(-swipe.value.coerceIn(-1f, 1f) * 400f); swipe.animateTo(0f, tween(260)) } }
            else if (swipe.value != 0f && !swipe.isRunning) swipe.snapTo(0f)
        }
        Box(Modifier.weight(1f).fillMaxWidth()
            .pointerInput(p, npView) {
                val w = size.width.toFloat()
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val x = swipe.value
                        val cover = npView == 0
                        val tgt = if (cover) slotPx.floatValue else w
                        val thr = if (cover) tgt * 0.28f else w * 0.22f
                        swScope.launch {
                            when {
                                x < -thr && p.hasNextMediaItem() -> { swipe.animateTo(-tgt, tween(220)); haptic(view); p.seekToNextMediaItem(); p.play(); if (cover) swipe.snapTo(0f) }
                                x > thr && p.hasPreviousMediaItem() -> { swipe.animateTo(tgt, tween(220)); haptic(view); p.seekToPreviousMediaItem(); p.play(); if (cover) swipe.snapTo(0f) }
                                x > thr && p.currentPosition > 3000 -> { haptic(view); p.seekTo(0); swipe.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.7f)) }
                                else -> swipe.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.7f))
                            }
                        }
                    },
                    onDragCancel = { swScope.launch { swipe.animateTo(0f) } }
                ) { ch, dx -> ch.consume()
                    val stuck = npView == 0 && ((swipe.value + dx < 0 && !p.hasNextMediaItem()) || (swipe.value + dx > 0 && !p.hasPreviousMediaItem()))
                    swScope.launch { swipe.snapTo(swipe.value + if (stuck) dx * 0.3f else dx) } }
            }
            .graphicsLayer { if (npView != 0) { translationX = swipe.value; alpha = 1f - (kotlin.math.abs(swipe.value) / (size.width * 1.4f)).coerceIn(0f, 0.6f) } },
            contentAlignment = Alignment.Center) {
            if (npView == 1) Column { Box(Modifier.weight(1f)) { LyricsView(lyrics.value.map { -1L to it.second }.filter { it.second.isNotBlank() }, pos) { } }
                if (lyricsSrc.isNotEmpty()) Text(lyricsSrc, fontSize = 10.sp, color = NpFg.copy(alpha = 0.45f)) }
            else if (npView == 2) VizView(art, tint, NowState.playing)
            else BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // kapak şeridi: kaydırınca kapaklar birlikte kayar, sıradaki ortaya gelir
                val side = minOf(maxWidth * 0.84f, maxHeight * 0.96f)
                val sp = constraints.maxWidth * 0.78f
                SideEffect { slotPx.floatValue = sp }
                val frac = swipe.value / sp
                val n = p.mediaItemCount
                listOf(-1 to p.previousMediaItemIndex, 1 to p.nextMediaItemIndex, 0 to p.currentMediaItemIndex).forEach { (d, idx) ->
                    if (idx == C.INDEX_UNSET || idx < 0 || idx >= n) return@forEach
                    val pos = d + frac
                    val dist = kotlin.math.abs(pos).coerceAtMost(1f)
                    if (kotlin.math.abs(pos) > 1.6f) return@forEach
                    val k = p.getMediaItemAt(idx).mediaId
                    val sg = Music.byKey(k)
                    val sc = (1f - 0.2f * dist) * (artScale + (1f - artScale) * dist)
                    val glow = if (look.art == 3) Accent else Color.Black
                    Box(Modifier.zIndex(1f - dist).graphicsLayer { translationX = pos * sp; scaleX = sc; scaleY = sc; alpha = 1f - 0.6f * dist
                            if (look.art == 1 && d == 0) rotationZ = spin }
                        .size(side).shadow(if (dist < 0.5f) (if (look.art == 3) 44.dp else 30.dp) else 0.dp, artShape, ambientColor = glow, spotColor = glow).clip(artShape)
                        .background(Brush.linearGradient(listOf(tint, AccentDeep))), contentAlignment = Alignment.Center) {
                        val b = art
                        if (k == key && b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else ArtImage(k, sg?.uri, sg?.path ?: k.takeIf { it.startsWith("/") }, Modifier.fillMaxSize(), artShape, 640)
                        if (look.art == 1) {   // plak ortası
                            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.25f)))))
                            Box(Modifier.size(side * 0.2f).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).border(3.dp, Color.White.copy(alpha = 0.35f), CircleShape))
                            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF0B0B0E)))
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            if (look.center) Spacer(Modifier.width(48.dp))
            Column(Modifier.weight(1f), horizontalAlignment = if (look.center) Alignment.CenterHorizontally else Alignment.Start) {
                Text(NowState.title, fontSize = if (look.center) 24.sp else 22.sp, fontWeight = if (look.center) FontWeight.Black else FontWeight.Bold, maxLines = 1, modifier = Modifier.basicMarquee())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AudioBars(NowState.playing, NpFg.copy(alpha = 0.8f), Modifier.padding(end = 8.dp))
                    Text(NowState.artist.ifEmpty { "Bilinmeyen sanatçı" }, color = NpFg.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = { haptic(view); fav = Favs.toggle(key) }) {
                Icon(if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favori", tint = if (fav) Color(0xFFFF5C7A) else NpFg, modifier = Modifier.size(28.dp))
            }
        }

        val sf = drag ?: if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
        val onSeekDone: (Float) -> Unit = { f -> Engine.player?.seekTo((f * dur).toLong()); pos = (f * dur).toLong(); drag = null }
        when (look.seek) {
            3 -> WaveSeek(key, sf, Modifier.padding(top = 8.dp), onDrag = { drag = it }, onDone = onSeekDone)
            1 -> SeekLine(sf, Modifier.padding(top = 8.dp), active = Accent, track = NpFg.copy(alpha = 0.2f), thick = 7.dp, onDrag = { drag = it }, onDone = onSeekDone)
            2 -> SeekLine(sf, Modifier.padding(top = 8.dp), active = Accent, track = NpFg.copy(alpha = 0.18f), thick = 2.dp, knob = false, onDrag = { drag = it }, onDone = onSeekDone)
            else -> SeekLine(sf, Modifier.padding(top = 8.dp), active = NpFg, track = NpFg.copy(alpha = 0.25f), onDrag = { drag = it }, onDone = onSeekDone)
        }
        Row(Modifier.fillMaxWidth()) {
            Text(fmtTime(drag?.let { (it * dur).toLong() } ?: pos), fontSize = 12.sp, color = NpFg.copy(alpha = 0.7f))
            Spacer(Modifier.weight(1f))
            Text("−" + fmtTime((dur - pos).coerceAtLeast(0)), fontSize = 12.sp, color = NpFg.copy(alpha = 0.7f))
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { shuffle = !shuffle; Engine.setShuffle(shuffle); Prefs.musicShuffle = shuffle; toast(ctx, if (shuffle) "Karışık: sıra karıştırıldı" else "Karışık kapalı: eski sıra") }) {
                Icon(Icons.Filled.Shuffle, "Karışık", tint = if (shuffle) Accent else NpFg.copy(alpha = 0.7f))
            }
            SideBtn(look.glass, Icons.Filled.SkipPrevious, "Önceki") { if (p.currentPosition > 4000) p.seekTo(0) else p.seekToPreviousMediaItem() }
            PlayBtn(look.btn, NowState.playing, 80.dp) { haptic(view); if (p.isPlaying) p.pause() else { if (p.playbackState == Player.STATE_ENDED) p.seekTo(0); p.play() } }
            SideBtn(look.glass, Icons.Filled.SkipNext, "Sonraki") { p.seekToNextMediaItem() }
            IconButton(onClick = {
                repeat = when (repeat) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF }
                p.repeatMode = repeat; Prefs.musicRepeat = when (repeat) { Player.REPEAT_MODE_ONE -> 1; Player.REPEAT_MODE_ALL -> 2; else -> 0 }
            }) {
                Icon(if (repeat == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat, "Tekrar",
                    tint = if (repeat != Player.REPEAT_MODE_OFF) Accent else NpFg.copy(alpha = 0.7f))
            }
        }

        NextCard(p) { sheet = NpSheet.QUEUE }
        // sütunun 22dp kenar boşluğunu aşıp ekran kenarına kadar uzanır: son düğme kenar boşluğunda sertçe kesilmez
        Row(Modifier.fillMaxWidth().layout { m, c ->
                val ex = 44.dp.roundToPx(); val pl = m.measure(c.copy(minWidth = c.maxWidth + ex, maxWidth = c.maxWidth + ex))
                layout(c.maxWidth, pl.height) { pl.place(-ex / 2, 0) }
            }.horizontalScroll(rememberScrollState()).padding(start = 22.dp, end = 22.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NpTool(Icons.Filled.Lyrics, "Sözler", npView == 1) { npView = if (npView == 1) 0 else 1 }
            NpTool(Icons.Filled.GraphicEq, "Dalga", npView == 2) { npView = if (npView == 2) 0 else 2 }
            NpTool(Icons.AutoMirrored.Filled.QueueMusic, "Sıra", false) { sheet = NpSheet.QUEUE }
            NpTool(Icons.Filled.Bedtime, if (sleepLabel.isEmpty()) "Uyku" else sleepLabel, sleepLabel.isNotEmpty()) { sheet = NpSheet.SLEEP }
            NpTool(Icons.Filled.VolumeUp, if (Prefs.musicBoost > 0) "%${100 + Prefs.musicBoost}" else "Ses+", Prefs.musicBoost > 0) { sheet = NpSheet.BOOST }
            NpTool(Icons.Filled.Equalizer, "Ekolayzer", Prefs.eqOn) { sheet = NpSheet.EQ }
            NpTool(Icons.Filled.SurroundSound, if (Fx.eightD) "8D" else if (Fx.reverb > 0) listOf("", "Oda", "Salon", "Konser")[Fx.reverb] else "Efekt", Fx.on) { sheet = NpSheet.FX }
        }
    }
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
                        NpSheet.BOOST -> MusicBoostContent()
                        NpSheet.FX -> FxContent()
                        NpSheet.SETTINGS -> TransitionSettings()
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
    Row(Modifier.clip(CircleShape).background(if (active) Accent else NpFg.copy(alpha = 0.1f)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, label, tint = NpFg, modifier = Modifier.size(18.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = NpFg, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun LyricsView(lines: List<Pair<Long, String>>, pos: Long, seek: (Long) -> Unit) {
    if (lines.isEmpty()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Icon(Icons.Filled.Lyrics, null, tint = NpFg.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
            Text("Bu şarkı için söz bulunamadı.\nİnternette bulunamadıysa şarkıyla aynı adlı .lrc dosyasını aynı klasöre koyabilirsin.",
                textAlign = TextAlign.Center, color = NpFg.copy(alpha = 0.7f), modifier = Modifier.padding(top = 10.dp))
        }
        return
    }
    if (lines.first().first < 0) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 24.dp)) {
            itemsIndexed(lines) { _, (_, s) -> Text(s.ifEmpty { " " }, fontSize = 18.sp, color = NpFg.copy(alpha = 0.85f), lineHeight = 28.sp) }
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
                color = if (active) NpFg else NpFg.copy(alpha = 0.4f), lineHeight = 30.sp,
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
        Text(if (Engine.shuffled) "Karışık sıra · gerçekten bu sırayla çalacak · sürükleyerek değiştir" else "Sıralamak için sağdaki tutamaçtan sürükle", color = if (Engine.shuffled) Accent else Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
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


@Composable
private fun PeekArt(idx: Int, p: Player, m: Modifier) {
    if (idx == C.INDEX_UNSET || idx < 0 || idx >= p.mediaItemCount) return
    val k = p.getMediaItemAt(idx).mediaId; val s = Music.byKey(k)
    ArtImage(k, s?.uri, s?.path ?: k.takeIf { it.startsWith("/") }, m.size(220.dp).alpha(0.35f), RoundedCornerShape(26.dp), 320)
}

@Composable
private fun NextCard(p: Player, open: () -> Unit) {
    val ni = p.nextMediaItemIndex
    if (ni == C.INDEX_UNSET) return
    val it = p.getMediaItemAt(ni); val s = Music.byKey(it.mediaId)
    val rest = p.mediaItemCount - p.currentMediaItemIndex - 1
    Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp).clip(RoundedCornerShape(18.dp)).background(NpFg.copy(alpha = 0.08f)).clickable(onClick = open).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SIRADAKİ", fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = NpFg.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
            if (rest > 1) Text("$rest şarkı", fontSize = 12.sp, color = NpFg.copy(alpha = 0.7f))
        }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            ArtImage(it.mediaId, s?.uri, s?.path ?: it.mediaId.takeIf { k -> k.startsWith("/") }, Modifier.size(38.dp), RoundedCornerShape(9.dp), 128)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(it.mediaMetadata.title?.toString() ?: "", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(it.mediaMetadata.artist?.toString() ?: "", fontSize = 12.sp, color = NpFg.copy(alpha = 0.7f), maxLines = 1)
            }
            Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = NpFg.copy(alpha = 0.7f))
        }
    }
}
