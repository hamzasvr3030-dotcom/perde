@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.perde.oynatici

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.videoFramePercent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

val TR: Locale = Locale.forLanguageTag("tr")

// ---------------- ortak ----------------
@Composable
fun Page(title: String, back: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}, fab: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    Scaffold(
        containerColor = Bg, contentWindowInsets = WindowInsets(0, 0, 0, 0), floatingActionButton = fab,
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold) },
                navigationIcon = { if (back != null) IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
            )
        }
    ) { pad -> Box(Modifier.padding(pad).fillMaxSize()) { content() } }
}

@Composable
fun Empty(icon: ImageVector, text: String) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(96.dp).clip(CircleShape).background(AccentDeep), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Accent, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(text, textAlign = TextAlign.Center, color = Muted)
    }
}

@Composable
fun SectionHeader(t: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(t, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action, color = Accent) }
    }
}

@Composable
fun SortButton(onChange: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sırala") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf("Ada göre", "Tarihe göre", "Boyuta göre", "Süreye göre").forEachIndexed { i, n ->
                DropdownMenuItem(text = { Text(n) }, onClick = { Prefs.sortBy = i; open = false; onChange() },
                    leadingIcon = { RadioButton(selected = Prefs.sortBy == i, onClick = null) })
            }
            HorizontalDivider()
            DropdownMenuItem(text = { Text("Ters sırala") }, onClick = { Prefs.sortDesc = !Prefs.sortDesc; open = false; onChange() },
                leadingIcon = { Checkbox(checked = Prefs.sortDesc, onCheckedChange = null) })
        }
    }
}

@Composable
fun GridToggle(onChange: () -> Unit) {
    IconButton(onClick = { Prefs.grid = !Prefs.grid; onChange() }) {
        Icon(if (Prefs.grid) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView, "Görünüm")
    }
}

@Composable
fun Thumb(v: Video, modifier: Modifier, showNew: Boolean = true) {
    val ctx = LocalContext.current
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(Surf2)) {
        AsyncImage(
            model = remember(v.uri) { ImageRequest.Builder(ctx).data(v.uri).videoFramePercent(0.1).build() },
            contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
        )
        if (v.duration > 0) Text(fmtTime(v.duration), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xB3000000)).padding(horizontal = 5.dp, vertical = 1.dp))
        val d = Prefs.dur(v.key); val p = Prefs.pos(v.key)
        if (d > 0 && p > 0) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color(0x66FFFFFF))) {
                Box(Modifier.fillMaxWidth((p.toFloat() / d).coerceIn(0f, 1f)).fillMaxHeight().background(Accent))
            }
        }
        if (showNew && v.isNew) Text("YENİ", color = Color(0xFF14100E), fontSize = 10.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.TopStart).padding(5.dp).clip(RoundedCornerShape(6.dp)).background(Accent).padding(horizontal = 5.dp, vertical = 1.dp))
        if (Favs.has(v.key)) Icon(Icons.Filled.Favorite, null, tint = Color(0xFFFF5C7A), modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(16.dp))
    }
}

private fun videoSub(v: Video) = listOf(resLabel(v.width, v.height), fmtSize(v.size)).filter { it.isNotEmpty() }.joinToString(" · ")

@Composable
fun VideoRow(v: Video, selected: Boolean, selecting: Boolean, onClick: () -> Unit, onLong: () -> Unit, onMore: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(if (selected) Accent.copy(alpha = 0.14f) else Color.Transparent)
        .combinedClickable(onClick = onClick, onLongClick = onLong).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box {
            Thumb(v, Modifier.size(132.dp, 74.dp))
            if (selecting) SelectMark(selected, Modifier.align(Alignment.TopStart).padding(4.dp))
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(v.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(videoSub(v), color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
        if (!selecting) IconButton(onClick = onMore) { Icon(Icons.Filled.MoreVert, "Seçenekler", tint = Muted) }
    }
}

@Composable
fun VideoCard(v: Video, selected: Boolean, selecting: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(14.dp)).background(if (selected) Accent.copy(alpha = 0.14f) else Color.Transparent)
        .combinedClickable(onClick = onClick, onLongClick = onLong).padding(6.dp)) {
        Box {
            Thumb(v, Modifier.fillMaxWidth().aspectRatio(16 / 9f))
            if (selecting) SelectMark(selected, Modifier.align(Alignment.TopStart).padding(4.dp))
        }
        Text(v.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 5.dp))
        Text(videoSub(v), color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun SelectMark(on: Boolean, m: Modifier) {
    Box(m.size(24.dp).clip(CircleShape).background(if (on) Accent else Color(0x99000000)).border(2.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
        if (on) Icon(Icons.Filled.Check, null, tint = Color(0xFF14100E), modifier = Modifier.size(16.dp))
    }
}

fun playKeys(ctx: Context, keys: List<String>, index: Int = 0) {
    if (keys.isEmpty()) return
    val songs = keys.mapNotNull { Music.byKey(it) }
    if (songs.size == keys.size) { Engine.playSongs(ctx, songs, index); AppState.showNow = true; return }
    PlayerActivity.openKeys(ctx, keys, keys.map { Library.titleOf(ctx, it) }, index)
}

// ---------------- ana sayfa ----------------
@Composable
fun HomeScreen(vm: LibVM, go: (Route) -> Unit) {
    val ctx = LocalContext.current
    var ver by remember { mutableIntStateOf(0) }
    var folderSel by remember { mutableStateOf<Folder?>(null) }
    var videoSel by remember { mutableStateOf<Video?>(null) }
    Scaffold(containerColor = Bg, contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(AccentSoft, Accent))), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.PlayArrow, null, tint = Color(0xFF14100E))
                    }
                    Text("Perde", fontWeight = FontWeight.Black, fontSize = 24.sp, modifier = Modifier.padding(start = 10.dp))
                }
            },
            actions = {
                IconButton(onClick = { go(Route.Search) }) { Icon(Icons.Filled.Search, "Ara") }
                GridToggle { ver++ }
                SortButton { ver++ }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
        )
    }) { pad ->
        val folders = remember(vm.videos, ver, vm.ver) { Library.sortFolders(Library.folders(vm.videos)) }
        val recent = remember(vm.videos, vm.ver) { vm.videos.filter { Prefs.playedAt(it.key) > 0 }.sortedByDescending { Prefs.playedAt(it.key) }.take(12) }
        val newest = remember(vm.videos, vm.ver) { vm.videos.sortedByDescending { it.dateAdded }.take(15) }
        val favs = remember(vm.videos, vm.ver) { val f = Favs.all(); vm.videos.filter { it.key in f } }
        val lists = remember(vm.ver) { Playlists.all() }
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (vm.videos.isEmpty()) {
                if (vm.loading) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Accent)
                else Empty(Icons.Filled.VideoLibrary, "Telefonda video bulunamadı.\nAğ sekmesindeki Wi-Fi aktarım ile bilgisayardan video gönderebilirsin.")
            } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (recent.isNotEmpty()) {
                    item { SectionHeader("İzlemeye devam et") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(recent, key = { it.key }) { v -> HeroCard(v) { openInFolder(ctx, vm, v) } }
                        }
                    }
                }
                item { SectionHeader("Son eklenenler") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(newest, key = { it.key }) { v ->
                            Column(Modifier.width(168.dp).combinedClickable(onClick = { openInFolder(ctx, vm, v) }, onLongClick = { videoSel = v })) {
                                Thumb(v, Modifier.fillMaxWidth().aspectRatio(16 / 9f))
                                Text(v.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
                            }
                        }
                    }
                }
                if (favs.isNotEmpty()) {
                    item { SectionHeader("Favoriler", "Tümü") { go(Route.Favorites) } }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(favs, key = { it.key }) { v ->
                                Column(Modifier.width(150.dp).clickable { PlayerActivity.open(ctx, favs, favs.indexOf(v)) }) {
                                    Thumb(v, Modifier.fillMaxWidth().aspectRatio(16 / 9f))
                                    Text(v.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
                                }
                            }
                        }
                    }
                }
                item { SectionHeader("Listeler", "Tümü") { go(Route.Playlists) } }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { AssistChip(onClick = { go(Route.Favorites) }, label = { Text("Favoriler") }, leadingIcon = { Icon(Icons.Filled.Favorite, null, tint = Color(0xFFFF5C7A)) }) }
                        items(lists, key = { it.id }) { p ->
                            AssistChip(onClick = { go(Route.PlaylistR(p.id)) }, label = { Text("${p.name} (${p.items.size})") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null) })
                        }
                        item { AssistChip(onClick = { go(Route.Playlists) }, label = { Text("Yeni liste") }, leadingIcon = { Icon(Icons.Filled.Add, null) }) }
                    }
                }
                item { SectionHeader("Klasörler (${folders.size})") }
                if (Prefs.grid) {
                    items(folders.chunked(2)) { row ->
                        Row(Modifier.padding(horizontal = 10.dp)) {
                            row.forEach { f -> Box(Modifier.weight(1f)) { FolderCard(f, { go(Route.Videos(f.path)) }, { folderSel = f }) } }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                } else items(folders, key = { it.path }) { f -> FolderRow(f, { go(Route.Videos(f.path)) }, { folderSel = f }) }
            }
        }
    }
    folderSel?.let { f -> FolderActions(f, vm, { folderSel = null }) { go(Route.Videos(f.path)) } }
    videoSel?.let { v -> VideoActions(v, vm, { videoSel = null }) { openInFolder(ctx, vm, v) } }
}

private fun openInFolder(ctx: Context, vm: LibVM, v: Video) {
    val list = Library.sort(vm.videos.filter { it.folder == v.folder })
    PlayerActivity.open(ctx, list, list.indexOf(v).coerceAtLeast(0))
}

@Composable
private fun HeroCard(v: Video, onClick: () -> Unit) {
    val p = Prefs.pos(v.key); val d = Prefs.dur(v.key)
    Box(Modifier.width(280.dp).aspectRatio(16 / 9f).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick)) {
        Thumb(v, Modifier.fillMaxSize(), showNew = false)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000)), startY = 80f)))
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(v.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (d > 0) Text("${fmtTime(p)} / ${fmtTime(d)} · %${(p * 100 / d)}", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
        }
        Box(Modifier.align(Alignment.Center).size(48.dp).clip(CircleShape).background(Accent.copy(alpha = 0.9f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.PlayArrow, null, tint = Color(0xFF14100E), modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun FolderRow(f: Folder, onClick: () -> Unit, onLong: () -> Unit) {
    val newCount = f.videos.count { it.isNew }
    Row(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLong).padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(54.dp).clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(listOf(AccentDeep, Surf2))), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Folder, null, tint = Accent, modifier = Modifier.size(30.dp))
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (f.pinned) Icon(Icons.Filled.PushPin, null, tint = Accent, modifier = Modifier.size(15.dp).padding(end = 3.dp))
                Text(f.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("${f.videos.size} video · ${fmtSize(f.size)}", fontSize = 13.sp, color = Muted)
        }
        if (newCount > 0) Badge(containerColor = Accent) { Text("$newCount yeni", color = Color(0xFF14100E)) }
        IconButton(onClick = onLong) { Icon(Icons.Filled.MoreVert, "Seçenekler", tint = Muted) }
    }
}

@Composable
private fun FolderCard(f: Folder, onClick: () -> Unit, onLong: () -> Unit) {
    Column(Modifier.padding(6.dp).combinedClickable(onClick = onClick, onLongClick = onLong)) {
        Box {
            Thumb(f.videos.maxBy { it.dateAdded }, Modifier.fillMaxWidth().aspectRatio(16 / 9f), showNew = false)
            Box(Modifier.matchParentSize().clip(RoundedCornerShape(12.dp)).background(Color(0x66000000)))
            Icon(Icons.Filled.Folder, null, tint = Accent, modifier = Modifier.align(Alignment.Center).size(36.dp))
            if (f.pinned) Icon(Icons.Filled.PushPin, null, tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(16.dp))
        }
        Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 5.dp))
        Text("${f.videos.size} video", fontSize = 12.sp, color = Muted)
    }
}

// ---------------- klasör işlemleri ----------------
@Composable
fun ActionItem(icon: ImageVector, t: String, color: Color = Color.Unspecified, onClick: () -> Unit) =
    ListItem(headlineContent = { Text(t, color = color) }, leadingContent = { Icon(icon, null, tint = if (color == Color.Unspecified) Muted else color) },
        modifier = Modifier.clickable(onClick = onClick), colors = ListItemDefaults.colors(containerColor = Color.Transparent))

@Composable
private fun FolderActions(f: Folder, vm: LibVM, dismiss: () -> Unit, open: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableIntStateOf(0) }
    if (dialog == 0) ModalBottomSheet(onDismissRequest = dismiss, containerColor = Surf) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(f.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 20.dp))
            Text("${f.videos.size} video · ${fmtSize(f.size)}", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            ActionItem(Icons.Filled.FolderOpen, "Aç") { dismiss(); open() }
            ActionItem(Icons.Filled.PlayArrow, "Tümünü oynat") { dismiss(); PlayerActivity.open(ctx, Library.sort(f.videos), 0) }
            ActionItem(Icons.Filled.Edit, "Klasörü yeniden adlandır") { dialog = 1 }
            ActionItem(Icons.Filled.PushPin, if (f.pinned) "Sabitlemeyi kaldır" else "En üste sabitle") { Prefs.toggleIn(Library.PINNED, f.path); vm.bump(); dismiss() }
            ActionItem(Icons.AutoMirrored.Filled.PlaylistAdd, "Tümünü listeye ekle") { dialog = 4 }
            ActionItem(Icons.Filled.VisibilityOff, "Listeden gizle (silmez)") { Prefs.toggleIn(Library.HIDDEN, f.path); vm.bump(); toast(ctx, "Klasör gizlendi. Daha → Gizlenen klasörler'den geri açabilirsin."); dismiss() }
            ActionItem(Icons.Filled.Lock, "Tüm videoları gizli klasöre taşı") { dialog = 3 }
            ActionItem(Icons.Filled.Delete, "Klasördeki videoları sil", Color(0xFFFF6B6B)) { dialog = 2 }
        }
    }
    when (dialog) {
        1 -> {
            var name by remember { mutableStateOf(f.name) }
            AlertDialog(onDismissRequest = dismiss, title = { Text("Klasörü yeniden adlandır") },
                text = { OutlinedTextField(name, { name = it }, singleLine = true) },
                confirmButton = { TextButton(onClick = {
                    val ok = Library.renameFolder(ctx, f.path, name)
                    toast(ctx, if (ok) "Klasör adı değiştirildi" else "Değiştirilemedi (aynı adda klasör olabilir)")
                    dismiss(); vm.reload()
                }) { Text("Kaydet") } },
                dismissButton = { TextButton(onClick = dismiss) { Text("Vazgeç") } })
        }
        2 -> AlertDialog(onDismissRequest = dismiss, title = { Text("${f.videos.size} video silinsin mi?") },
            text = { Text(if (Prefs.useTrash) "Videolar çöp kutusuna taşınır, 30 gün içinde geri alabilirsin." else "Videolar kalıcı olarak silinecek.") },
            confirmButton = { TextButton(onClick = {
                scope.launch { val n = withContext(Dispatchers.IO) { f.videos.count { Library.delete(ctx, it) } }; toast(ctx, "$n video silindi"); vm.reload() }
                dismiss()
            }) { Text("Sil", color = Color(0xFFFF6B6B)) } },
            dismissButton = { TextButton(onClick = dismiss) { Text("Vazgeç") } })
        3 -> AlertDialog(onDismissRequest = dismiss, title = { Text("Gizli klasöre taşınsın mı?") },
            text = { Text("${f.videos.size} video galeriden gizlenir; sadece Perde'deki şifreli Gizli klasörde görünür.\n\nDikkat: Perde kaldırılırsa gizli videolar da silinir.") },
            confirmButton = { TextButton(onClick = {
                scope.launch { val n = withContext(Dispatchers.IO) { f.videos.count { Library.moveToPrivate(ctx, it.path, it.uri) } }; toast(ctx, "$n video taşındı"); vm.reload() }
                dismiss()
            }) { Text("Taşı") } },
            dismissButton = { TextButton(onClick = dismiss) { Text("Vazgeç") } })
        4 -> PlaylistPickerDialog(f.videos.map { it.key }, dismiss)
    }
}

fun toast(ctx: Context, t: String) = Toast.makeText(ctx, t, Toast.LENGTH_SHORT).show()

// ---------------- video işlemleri ----------------
@Composable
fun VideoActions(v: Video, vm: LibVM, dismiss: () -> Unit, play: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    fun bg(work: () -> Boolean, ok: String, fail: String) {
        dismiss()
        scope.launch { val r = withContext(Dispatchers.IO) { work() }; toast(ctx, if (r) ok else fail); vm.reload() }
    }
    var dialog by remember { mutableIntStateOf(0) }
    if (dialog == 0) ModalBottomSheet(onDismissRequest = dismiss, containerColor = Surf) {
        Column(Modifier.padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Thumb(v, Modifier.size(96.dp, 54.dp))
                Text(v.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
            }
            ActionItem(Icons.Filled.PlayArrow, "Oynat") { dismiss(); play() }
            ActionItem(if (Favs.has(v.key)) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, if (Favs.has(v.key)) "Favorilerden çıkar" else "Favorilere ekle") {
                Favs.toggle(v.key); vm.bump(); dismiss()
            }
            ActionItem(Icons.AutoMirrored.Filled.PlaylistAdd, "Listeye ekle") { dialog = 5 }
            ActionItem(Icons.Filled.Edit, "Yeniden adlandır") { dialog = 1 }
            ActionItem(Icons.Filled.Share, "Paylaş") {
                dismiss()
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(v.mime ?: "video/*")
                    .putExtra(Intent.EXTRA_STREAM, v.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Paylaş"))
            }
            ActionItem(Icons.Filled.DriveFileMove, "Başka klasöre taşı") { dialog = 6 }
            ActionItem(Icons.Filled.FileCopy, "Başka klasöre kopyala") { dialog = 7 }
            ActionItem(Icons.Filled.Lock, "Gizli klasöre taşı") { dialog = 4 }
            ActionItem(Icons.Filled.Info, "Özellikler") { dialog = 3 }
            ActionItem(Icons.Filled.Delete, "Sil", Color(0xFFFF6B6B)) { dialog = 2 }
        }
    }
    when (dialog) {
        1 -> {
            var name by remember { mutableStateOf(File(v.path).nameWithoutExtension) }
            AlertDialog(onDismissRequest = dismiss, title = { Text("Yeniden adlandır") },
                text = { OutlinedTextField(name, { name = it }, singleLine = true, suffix = { Text("." + File(v.path).extension) }) },
                confirmButton = { TextButton(onClick = {
                    val ok = Library.rename(ctx, v, name.trim())
                    toast(ctx, if (ok) "Ad değiştirildi" else "Ad değiştirilemedi (aynı adda dosya olabilir)")
                    dismiss(); vm.reload()
                }) { Text("Kaydet") } },
                dismissButton = { TextButton(onClick = dismiss) { Text("Vazgeç") } })
        }
        2 -> AlertDialog(onDismissRequest = dismiss, title = { Text("Video silinsin mi?") },
            text = { Text(if (Prefs.useTrash) "\"${v.title}\" çöp kutusuna taşınacak (30 gün saklanır)." else "\"${v.title}\" kalıcı olarak silinecek.") },
            confirmButton = { TextButton(onClick = { bg({ Library.delete(ctx, v) }, "Silindi", "Silinemedi") }) { Text("Sil", color = Color(0xFFFF6B6B)) } },
            dismissButton = { TextButton(onClick = dismiss) { Text("Vazgeç") } })
        3 -> AlertDialog(onDismissRequest = dismiss, title = { Text("Özellikler") }, confirmButton = { TextButton(onClick = dismiss) { Text("Tamam") } },
            text = {
                Column {
                    val watched = Prefs.sp.getLong("sv:${v.key}", 0)
                    listOfNotNull("Ad" to File(v.path).name, "Konum" to v.folder, "Boyut" to fmtSize(v.size), "Süre" to fmtTime(v.duration),
                        "Çözünürlük" to if (v.width > 0) "${v.width}×${v.height}" else "?", "Tür" to (v.mime ?: "?"),
                        "Eklenme" to java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT, TR).format(v.dateAdded * 1000),
                        if (watched > 0) "İzleme süresi" to fmtDur(watched) else null
                    ).forEach { (k, x) -> Row(Modifier.padding(vertical = 3.dp)) { Text(k, Modifier.width(100.dp), color = Muted); Text(x) } }
                }
            })
        4 -> AlertDialog(onDismissRequest = dismiss, title = { Text("Gizli klasöre taşınsın mı?") },
            text = { Text("Video galeriden ve diğer uygulamalardan gizlenir, sadece Perde'deki şifreli Gizli klasörde görünür.\n\nDikkat: Perde kaldırılırsa gizli videolar da silinir.") },
            confirmButton = { TextButton(onClick = { bg({ Library.moveToPrivate(ctx, v.path, v.uri) }, "Gizli klasöre taşındı", "Taşınamadı") }) { Text("Taşı") } },
            dismissButton = { TextButton(onClick = dismiss) { Text("Vazgeç") } })
        5 -> PlaylistPickerDialog(listOf(v.key), dismiss)
        6, 7 -> FolderPickerDialog(vm, if (dialog == 7) "Kopyalanacak klasör" else "Taşınacak klasör", dismiss) { dir ->
            val copy = dialog == 7
            toast(ctx, if (copy) "Kopyalanıyor…" else "Taşınıyor…")
            bg({ Library.transfer(ctx, listOf(v.path), dir, copy) > 0 }, if (copy) "Kopyalandı" else "Taşındı", "İşlem yapılamadı")
        }
    }
}

/** Taşıma/kopyalama için klasör seçici. */
@Composable
fun FolderPickerDialog(vm: LibVM, title: String, dismiss: () -> Unit, pick: (File) -> Unit) {
    val folders = remember(vm.videos) { Library.folders(vm.videos, includeHidden = true).sortedWith(compareBy(NaturalOrder) { it.name }) }
    var newName by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(newName, { newName = it.replace("/", "") }, singleLine = true, label = { Text("Yeni klasör (Filmler içinde)") },
                    trailingIcon = { IconButton(enabled = newName.isNotBlank(), onClick = {
                        pick(File(Library.publicDir().parentFile, newName.trim()))
                    }) { Icon(Icons.Filled.CreateNewFolder, "Oluştur") } })
                Spacer(Modifier.height(8.dp))
                ListItem(headlineContent = { Text("Filmler/Perde") }, leadingContent = { Icon(Icons.Filled.Folder, null, tint = Accent) },
                    modifier = Modifier.clickable { pick(Library.publicDir()) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                folders.forEach { f ->
                    ListItem(headlineContent = { Text(f.name) }, supportingContent = { Text(f.path, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp) },
                        leadingContent = { Icon(Icons.Filled.Folder, null, tint = Accent) },
                        modifier = Modifier.clickable { pick(File(f.path)) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("Vazgeç") } })
}

/** Listeye ekleme içeriği (alt sayfa veya pencere içinde). */
@Composable
fun PlaylistPickerContent(keys: List<String>, done: (String) -> Unit) {
    var lists by remember { mutableStateOf(Playlists.all()) }
    var name by remember { mutableStateOf("") }
    SheetTitle("Listeye ekle")
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Yeni liste adı") }, modifier = Modifier.weight(1f))
        IconButton(enabled = name.isNotBlank(), onClick = { Playlists.create(name.trim(), keys); done("\"${name.trim()}\" listesi oluşturuldu") }) { Icon(Icons.Filled.Add, "Oluştur") }
    }
    Spacer(Modifier.height(8.dp))
    if (lists.isEmpty()) Text("Henüz liste yok. Yukarıdan bir ad yazıp oluştur.", color = Muted)
    lists.forEach { p ->
        ListItem(headlineContent = { Text(p.name) }, supportingContent = { Text("${p.items.size} öğe") },
            leadingContent = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = Accent) },
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { Playlists.add(p.id, keys); lists = Playlists.all(); done("\"${p.name}\" listesine eklendi") },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent))
    }
}

@Composable
fun PlaylistPickerDialog(keys: List<String>, dismiss: () -> Unit) {
    val ctx = LocalContext.current
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = Surf) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            PlaylistPickerContent(keys) { msg -> toast(ctx, msg); dismiss() }
        }
    }
}

// ---------------- video listesi + çoklu seçim ----------------
@Composable
fun VideoList(videos: List<Video>, vm: LibVM, title: String, back: () -> Unit, extraActions: @Composable RowScope.() -> Unit = {}, onOpen: (Int) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var sel by remember { mutableStateOf<Video?>(null) }
    val picked = remember { mutableStateListOf<String>() }
    var dialog by remember { mutableIntStateOf(0) }
    val selecting = picked.isNotEmpty()
    BackHandlerCompat(selecting) { picked.clear() }
    val pickedVideos = { videos.filter { it.key in picked } }
    Page(
        title = if (selecting) "${picked.size} seçildi" else title,
        back = if (selecting) ({ picked.clear() }) else back,
        actions = {
            if (selecting) {
                IconButton(onClick = { if (picked.size == videos.size) picked.clear() else { picked.clear(); picked.addAll(videos.map { it.key }) } }) { Icon(Icons.Filled.SelectAll, "Tümünü seç") }
                IconButton(onClick = {
                    val uris = ArrayList(pickedVideos().map { it.uri })
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND_MULTIPLE).setType("video/*")
                        .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Paylaş"))
                }) { Icon(Icons.Filled.Share, "Paylaş") }
                IconButton(onClick = { dialog = 1 }) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Listeye ekle") }
                var more by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { more = true }) { Icon(Icons.Filled.MoreVert, "Diğer") }
                    DropdownMenu(more, { more = false }) {
                        DropdownMenuItem({ Text("Oynat") }, { more = false; val l = pickedVideos(); picked.clear(); PlayerActivity.open(ctx, l, 0) }, leadingIcon = { Icon(Icons.Filled.PlayArrow, null) })
                        DropdownMenuItem({ Text("Favorilere ekle") }, { more = false; pickedVideos().forEach { if (!Favs.has(it.key)) Favs.toggle(it.key) }; picked.clear(); vm.bump() }, leadingIcon = { Icon(Icons.Filled.Favorite, null) })
                        DropdownMenuItem({ Text("Taşı") }, { more = false; dialog = 2 }, leadingIcon = { Icon(Icons.Filled.DriveFileMove, null) })
                        DropdownMenuItem({ Text("Kopyala") }, { more = false; dialog = 3 }, leadingIcon = { Icon(Icons.Filled.FileCopy, null) })
                        DropdownMenuItem({ Text("Gizli klasöre taşı") }, { more = false; dialog = 4 }, leadingIcon = { Icon(Icons.Filled.Lock, null) })
                        DropdownMenuItem({ Text("Sil", color = Color(0xFFFF6B6B)) }, { more = false; dialog = 5 }, leadingIcon = { Icon(Icons.Filled.Delete, null, tint = Color(0xFFFF6B6B)) })
                    }
                }
            } else extraActions()
        }
    ) {
        if (videos.isEmpty()) Empty(Icons.Filled.VideoLibrary, "Burada video yok.")
        else {
            val click: (Int, Video) -> Unit = { i, v -> if (selecting) { if (v.key in picked) picked.remove(v.key) else picked.add(v.key) } else onOpen(i) }
            val long: (Video) -> Unit = { v -> if (v.key in picked) picked.remove(v.key) else picked.add(v.key) }
            if (Prefs.grid) LazyVerticalGrid(GridCells.Adaptive(165.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
                gridItemsIndexed(videos, key = { _, v -> v.path }) { i, v -> VideoCard(v, v.key in picked, selecting, { click(i, v) }, { long(v) }) }
            } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                itemsIndexed(videos, key = { _, v -> v.path }) { i, v -> VideoRow(v, v.key in picked, selecting, { click(i, v) }, { long(v) }, { sel = v }) }
            }
        }
    }
    sel?.let { v -> VideoActions(v, vm, { sel = null }) { onOpen(videos.indexOf(v)) } }
    val close = { dialog = 0; picked.clear() }
    when (dialog) {
        1 -> PlaylistPickerDialog(picked.toList()) { close() }
        2, 3 -> FolderPickerDialog(vm, if (dialog == 3) "Kopyalanacak klasör" else "Taşınacak klasör", { dialog = 0 }) { dir ->
            val copy = dialog == 3; val paths = picked.toList()
            close()
            scope.launch { val n = withContext(Dispatchers.IO) { Library.transfer(ctx, paths, dir, copy) }; toast(ctx, "$n video ${if (copy) "kopyalandı" else "taşındı"}"); vm.reload() }
        }
        4 -> AlertDialog(onDismissRequest = { dialog = 0 }, title = { Text("${picked.size} video gizli klasöre taşınsın mı?") },
            text = { Text("Dikkat: Perde kaldırılırsa gizli videolar da silinir.") },
            confirmButton = { TextButton(onClick = {
                val l = pickedVideos(); close()
                scope.launch { val n = withContext(Dispatchers.IO) { l.count { Library.moveToPrivate(ctx, it.path, it.uri) } }; toast(ctx, "$n video taşındı"); vm.reload() }
            }) { Text("Taşı") } },
            dismissButton = { TextButton(onClick = { dialog = 0 }) { Text("Vazgeç") } })
        5 -> AlertDialog(onDismissRequest = { dialog = 0 }, title = { Text("${picked.size} video silinsin mi?") },
            text = { Text(if (Prefs.useTrash) "Videolar çöp kutusuna taşınır (30 gün saklanır)." else "Videolar kalıcı olarak silinecek.") },
            confirmButton = { TextButton(onClick = {
                val l = pickedVideos(); close()
                scope.launch { val n = withContext(Dispatchers.IO) { l.count { Library.delete(ctx, it) } }; toast(ctx, "$n video silindi"); vm.reload() }
            }) { Text("Sil", color = Color(0xFFFF6B6B)) } },
            dismissButton = { TextButton(onClick = { dialog = 0 }) { Text("Vazgeç") } })
    }
}

@Composable
fun BackHandlerCompat(enabled: Boolean, onBack: () -> Unit) = androidx.activity.compose.BackHandler(enabled, onBack)

@Composable
fun VideosScreen(vm: LibVM, folder: String, back: () -> Unit) {
    val ctx = LocalContext.current
    var ver by remember { mutableIntStateOf(0) }
    val videos = remember(vm.videos, ver, vm.ver) { Library.sort(vm.videos.filter { it.folder == folder }) }
    VideoList(videos, vm, File(folder).name, back, { GridToggle { ver++ }; SortButton { ver++ } }) { i -> PlayerActivity.open(ctx, videos, i) }
}

@Composable
fun SearchScreen(vm: LibVM, back: () -> Unit) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    val res = remember(q, vm.videos) {
        val s = q.trim().lowercase(TR)
        if (s.isEmpty()) emptyList() else Library.sort(vm.videos.filter { it.title.lowercase(TR).contains(s) })
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") }
            OutlinedTextField(q, { q = it }, placeholder = { Text("Video ara") }, singleLine = true, modifier = Modifier.weight(1f),
                leadingIcon = { Icon(Icons.Filled.Search, null) }, shape = CircleShape)
        }
        Box(Modifier.weight(1f)) {
            if (q.isNotBlank() && res.isEmpty()) Empty(Icons.Filled.SearchOff, "\"$q\" için sonuç yok.")
            else LazyColumn {
                itemsIndexed(res, key = { _, v -> v.path }) { i, v -> VideoRow(v, false, false, { PlayerActivity.open(ctx, res, i) }, {}, {}) }
            }
        }
    }
}

// ---------------- favoriler ve listeler ----------------
@Composable
fun FavoritesScreen(vm: LibVM, back: () -> Unit) {
    val ctx = LocalContext.current
    val favs = remember(vm.ver) { Favs.all() }
    val videos = remember(vm.videos, favs) { vm.videos.filter { it.key in favs } }
    val songs = remember(vm.songs, favs) { vm.songs.filter { it.key in favs } }
    Page("Favoriler", back) {
        if (videos.isEmpty() && songs.isEmpty()) Empty(Icons.Filled.FavoriteBorder, "Henüz favori yok.\nVideo veya şarkıda kalp simgesine dokun.")
        else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            if (videos.isNotEmpty()) {
                item { SectionHeader("Videolar (${videos.size})", "Oynat") { PlayerActivity.open(ctx, videos, 0) } }
                itemsIndexed(videos, key = { _, v -> "v" + v.key }) { i, v -> VideoRow(v, false, false, { PlayerActivity.open(ctx, videos, i) }, {}, { Favs.toggle(v.key); vm.bump() }) }
            }
            if (songs.isNotEmpty()) {
                item { SectionHeader("Şarkılar (${songs.size})", "Çal") { Engine.playSongs(ctx, songs, 0); AppState.showNow = true } }
                itemsIndexed(songs, key = { _, s -> "s" + s.key }) { i, s -> SongRow(s, { Engine.playSongs(ctx, songs, i) }, null) }
            }
        }
    }
}

@Composable
fun PlaylistsScreen(vm: LibVM, go: (Route) -> Unit, back: () -> Unit) {
    var lists by remember { mutableStateOf(Playlists.all()) }
    var creating by remember { mutableStateOf(false) }
    Page("Listeler", back, fab = {
        ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Yeni liste") }, containerColor = Accent, contentColor = Color(0xFF14100E))
    }) {
        if (lists.isEmpty()) Empty(Icons.AutoMirrored.Filled.PlaylistPlay, "Henüz liste yok.\nVideo ve şarkıları listelere ekleyerek kendi izleme/çalma listelerini oluştur.")
        else LazyColumn(contentPadding = PaddingValues(bottom = 90.dp)) {
            item {
                ListItem(headlineContent = { Text("Favoriler") }, leadingContent = { PlaylistIcon(Icons.Filled.Favorite) },
                    modifier = Modifier.clickable { go(Route.Favorites) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            }
            items(lists, key = { it.id }) { p ->
                ListItem(headlineContent = { Text(p.name, fontWeight = FontWeight.Medium) }, supportingContent = { Text("${p.items.size} öğe") },
                    leadingContent = { PlaylistIcon(Icons.AutoMirrored.Filled.PlaylistPlay) },
                    modifier = Modifier.clickable { go(Route.PlaylistR(p.id)) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            }
        }
    }
    if (creating) {
        var name by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { creating = false }, title = { Text("Yeni liste") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Liste adı") }) },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { Playlists.create(name.trim()); lists = Playlists.all(); creating = false }) { Text("Oluştur") } },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Vazgeç") } })
    }
}

@Composable
private fun PlaylistIcon(icon: ImageVector) {
    Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Accent, AccentDeep))), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White)
    }
}

@Composable
fun PlaylistScreen(vm: LibVM, id: Long, back: () -> Unit) {
    val ctx = LocalContext.current
    var p by remember { mutableStateOf(Playlists.get(id)) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val pl = p ?: run { back(); return }
    Page(pl.name, back, actions = {
        IconButton(onClick = { renaming = true }) { Icon(Icons.Filled.Edit, "Yeniden adlandır") }
        IconButton(onClick = { deleting = true }) { Icon(Icons.Filled.Delete, "Listeyi sil") }
    }) {
        if (pl.items.isEmpty()) Empty(Icons.AutoMirrored.Filled.PlaylistPlay, "Liste boş.\nBir video veya şarkının menüsünden \"Listeye ekle\" de.")
        else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { playKeys(ctx, pl.items, 0) }) { Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Oynat") }
                    OutlinedButton(onClick = { playKeys(ctx, pl.items.shuffled(), 0) }) { Icon(Icons.Filled.Shuffle, null); Spacer(Modifier.width(6.dp)); Text("Karışık") }
                }
            }
            itemsIndexed(pl.items, key = { i, k -> "$i$k" }) { i, k ->
                val v = Library.byKey(k); val s = Music.byKey(k)
                Row(Modifier.fillMaxWidth().clickable { playKeys(ctx, pl.items, i) }.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    when {
                        v != null -> Thumb(v, Modifier.size(96.dp, 54.dp))
                        s != null -> ArtImage(s.key, s.uri, s.path, Modifier.size(54.dp), RoundedCornerShape(10.dp))
                        else -> Box(Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)).background(Surf2), contentAlignment = Alignment.Center) { Icon(Icons.Filled.HelpOutline, null, tint = Muted) }
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(v?.title ?: s?.title ?: File(k).name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(when { v != null -> fmtTime(v.duration); s != null -> s.artist; else -> "Dosya bulunamadı" }, color = Muted, fontSize = 12.sp)
                    }
                    IconButton(enabled = i > 0, onClick = { Playlists.move(id, i, i - 1); p = Playlists.get(id) }) { Icon(Icons.Filled.KeyboardArrowUp, "Yukarı") }
                    IconButton(onClick = { Playlists.remove(id, k); p = Playlists.get(id) }) { Icon(Icons.Filled.Close, "Çıkar", tint = Muted) }
                }
            }
        }
    }
    if (renaming) {
        var name by remember { mutableStateOf(pl.name) }
        AlertDialog(onDismissRequest = { renaming = false }, title = { Text("Listeyi yeniden adlandır") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { Playlists.rename(id, name.trim()); p = Playlists.get(id); renaming = false }) { Text("Kaydet") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Vazgeç") } })
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Liste silinsin mi?") }, text = { Text("Dosyalar silinmez, sadece liste kaldırılır.") },
        confirmButton = { TextButton(onClick = { Playlists.delete(id); deleting = false; back() }) { Text("Sil", color = Color(0xFFFF6B6B)) } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Vazgeç") } })
}
