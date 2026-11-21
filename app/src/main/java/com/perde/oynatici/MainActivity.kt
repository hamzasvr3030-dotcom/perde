@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.perde.oynatici

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.videoFramePercent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Locale

sealed interface Route {
    data object Folders : Route
    data class Videos(val folder: String) : Route
    data object Search : Route
    data object Settings : Route
    data object Network : Route
    data object SmbList : Route
    data class SmbBrowse(val server: SmbServer, val path: String) : Route
    data object Transfer : Route
    data object Private : Route
}

class LibVM(app: Application) : AndroidViewModel(app) {
    var videos by mutableStateOf<List<Video>>(emptyList())
    var loading by mutableStateOf(false)
    var ver by mutableIntStateOf(0)
    fun reload() {
        viewModelScope.launch {
            loading = true
            videos = withContext(Dispatchers.IO) { Library.load(getApplication()) }
            loading = false; ver++
        }
    }
}

fun hasStorage(ctx: Context): Boolean =
    if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
    else ctx.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

private val TR = Locale.forLanguageTag("tr")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PerdeTheme { MainApp() } }
    }
}

@Composable
fun MainApp() {
    val ctx = LocalContext.current
    val vm: LibVM = viewModel()
    var granted by remember { mutableStateOf(hasStorage(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = hasStorage(ctx)
        if (granted) vm.reload()
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        if (!granted) PermissionScreen { granted = hasStorage(ctx); if (granted) vm.reload() }
        else Nav(vm)
    }
}

@Composable
private fun PermissionScreen(onResult: () -> Unit) {
    val ctx = LocalContext.current
    val legacy = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onResult() }
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.VideoLibrary, null, tint = Accent, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(16.dp))
        Text("Perde'ye hoş geldin", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text("Telefondaki videoları bulmak, altyazı dosyalarını okumak ve yeniden adlandırma/silme yapabilmek için dosya erişim izni gerekiyor. Açılan ekranda \"Perde\" için izni aç, sonra geri dön.",
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Button(onClick = {
            if (Build.VERSION.SDK_INT >= 30) {
                try { ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))) }
                catch (_: Exception) { ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
            } else legacy.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }) { Text("İzin ver") }
    }
}

@Composable
private fun Nav(vm: LibVM) {
    val stack = remember { mutableStateListOf<Route>(Route.Folders) }
    val go: (Route) -> Unit = { stack.add(it) }
    val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    BackHandler(stack.size > 1) { back() }
    when (val r = stack.last()) {
        Route.Folders -> FoldersScreen(vm, go)
        is Route.Videos -> VideosScreen(vm, r.folder, back)
        Route.Search -> SearchScreen(vm, back)
        Route.Settings -> SettingsScreen(back)
        Route.Network -> NetworkScreen(back)
        Route.SmbList -> SmbListScreen(go, back)
        is Route.SmbBrowse -> SmbBrowseScreen(r.server, r.path, go, back)
        Route.Transfer -> TransferScreen(back)
        Route.Private -> PrivateScreen(vm, back)
    }
}

// ---------------- ortak parçalar ----------------
@Composable
fun BackBar(title: String, back: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
    )
}

@Composable
private fun SortButton(onChange: () -> Unit) {
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
private fun GridToggle(onChange: () -> Unit) {
    IconButton(onClick = { Prefs.grid = !Prefs.grid; onChange() }) {
        Icon(if (Prefs.grid) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView, "Görünüm")
    }
}

@Composable
fun Thumb(v: Video, modifier: Modifier) {
    val ctx = LocalContext.current
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(Surf2)) {
        AsyncImage(
            model = remember(v.uri) { ImageRequest.Builder(ctx).data(v.uri).videoFramePercent(0.1).build() },
            contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
        )
        if (v.duration > 0) Text(fmtTime(v.duration), color = Color.White, fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xB3000000)).padding(horizontal = 4.dp, vertical = 1.dp))
        val d = Prefs.dur(v.key); val p = Prefs.pos(v.key)
        if (d > 0 && p > 0) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color(0x66FFFFFF))) {
                Box(Modifier.fillMaxWidth((p.toFloat() / d).coerceIn(0f, 1f)).fillMaxHeight().background(Accent))
            }
        }
        if (v.isNew) Text("YENİ", color = Color(0xFF1A0E08), fontSize = 10.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopStart).padding(4.dp).clip(RoundedCornerShape(4.dp)).background(Accent).padding(horizontal = 4.dp, vertical = 1.dp))
    }
}

private fun videoSub(v: Video) = listOf(resLabel(v.width, v.height), fmtSize(v.size)).filter { it.isNotEmpty() }.joinToString(" · ")

@Composable
private fun VideoRow(v: Video, onClick: () -> Unit, onLong: () -> Unit) {
    Row(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLong).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Thumb(v, Modifier.size(128.dp, 72.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(v.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
            Text(videoSub(v), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        IconButton(onClick = onLong) { Icon(Icons.Filled.MoreVert, "Seçenekler", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun VideoCard(v: Video, onClick: () -> Unit, onLong: () -> Unit) {
    Column(Modifier.combinedClickable(onClick = onClick, onLongClick = onLong).padding(6.dp)) {
        Thumb(v, Modifier.fillMaxWidth().aspectRatio(16 / 9f))
        Text(v.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        Text(videoSub(v), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }
}

@Composable
private fun Empty(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(10.dp))
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------- klasörler ----------------
@Composable
private fun FoldersScreen(vm: LibVM, go: (Route) -> Unit) {
    val ctx = LocalContext.current
    var ver by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Perde", fontWeight = FontWeight.Bold, color = Accent) },
            actions = {
                IconButton(onClick = { go(Route.Search) }) { Icon(Icons.Filled.Search, "Ara") }
                GridToggle { ver++ }
                SortButton { ver++ }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Menü") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("Ağ akışı (URL)") }, { menu = false; go(Route.Network) }, leadingIcon = { Icon(Icons.Filled.Link, null) })
                        DropdownMenuItem({ Text("Ağ klasörleri (SMB)") }, { menu = false; go(Route.SmbList) }, leadingIcon = { Icon(Icons.Filled.Lan, null) })
                        DropdownMenuItem({ Text("Wi-Fi aktarım") }, { menu = false; go(Route.Transfer) }, leadingIcon = { Icon(Icons.Filled.Wifi, null) })
                        DropdownMenuItem({ Text("Gizli klasör") }, { menu = false; go(Route.Private) }, leadingIcon = { Icon(Icons.Filled.VisibilityOff, null) })
                        DropdownMenuItem({ Text("Yenile") }, { menu = false; vm.reload() }, leadingIcon = { Icon(Icons.Filled.Refresh, null) })
                        DropdownMenuItem({ Text("Ayarlar") }, { menu = false; go(Route.Settings) }, leadingIcon = { Icon(Icons.Filled.Settings, null) })
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )
    }) { pad ->
        val folders = remember(vm.videos, ver, vm.ver) { Library.sortFolders(Library.folders(vm.videos)) }
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (vm.videos.isEmpty()) {
                if (vm.loading) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Accent)
                else Empty(Icons.Filled.VideoLibrary, "Telefonda video bulunamadı.\nWi-Fi aktarım ile bilgisayardan video gönderebilirsin.")
            } else {
                val last = remember(vm.ver) { Prefs.lastKey.takeIf { it.isNotEmpty() } }
                val lastVideo = remember(vm.videos, last) { vm.videos.firstOrNull { it.key == last } }
                LazyVerticalGrid(columns = if (Prefs.grid) GridCells.Adaptive(160.dp) else GridCells.Fixed(1), modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (last != null && (lastVideo != null || !last.startsWith("/"))) item(span = { GridItemSpan(maxLineSpan) }) {
                        ContinueCard(lastVideo, last) {
                            if (lastVideo != null) {
                                val list = Library.sort(vm.videos.filter { it.folder == lastVideo.folder })
                                PlayerActivity.open(ctx, list, list.indexOf(lastVideo).coerceAtLeast(0))
                            } else PlayerActivity.openKeys(ctx, listOf(last), listOf(Prefs.lastTitle), 0)
                        }
                    }
                    items(folders, key = { it.path }) { f ->
                        val newCount = f.videos.count { it.isNew }
                        if (Prefs.grid) FolderCard(f, newCount) { go(Route.Videos(f.path)) }
                        else FolderRow(f, newCount) { go(Route.Videos(f.path)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueCard(v: Video?, key: String, onClick: () -> Unit) {
    val p = Prefs.pos(key); val d = Prefs.dur(key)
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .background(Color(0xFF2A1A14)).clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (v != null) Thumb(v, Modifier.size(112.dp, 63.dp))
        else Box(Modifier.size(112.dp, 63.dp).clip(RoundedCornerShape(10.dp)).background(Surf2), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Link, null) }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text("İzlemeye devam et", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(v?.title ?: Prefs.lastTitle.ifEmpty { key }, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (d > 0) Text("${fmtTime(p)} / ${fmtTime(d)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.PlayCircle, null, tint = Accent, modifier = Modifier.size(36.dp))
    }
}

@Composable
private fun FolderRow(f: Folder, newCount: Int, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2A1A14)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Folder, null, tint = Accent, modifier = Modifier.size(30.dp))
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(f.name, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${f.videos.size} video · ${fmtSize(f.size)}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (newCount > 0) Badge(containerColor = Accent) { Text("$newCount yeni", color = Color(0xFF1A0E08)) }
    }
}

@Composable
private fun FolderCard(f: Folder, newCount: Int, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick).padding(6.dp)) {
        Box {
            Thumb(f.videos.maxBy { it.dateAdded }, Modifier.fillMaxWidth().aspectRatio(16 / 9f))
            Box(Modifier.matchParentSize().clip(RoundedCornerShape(10.dp)).background(Color(0x55000000)))
            Icon(Icons.Filled.Folder, null, tint = Accent, modifier = Modifier.align(Alignment.Center).size(34.dp))
        }
        Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        Text("${f.videos.size} video" + if (newCount > 0) " · $newCount yeni" else "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------- videolar ----------------
@Composable
private fun VideoList(videos: List<Video>, vm: LibVM, onOpen: (Int) -> Unit) {
    var sel by remember { mutableStateOf<Video?>(null) }
    if (Prefs.grid) LazyVerticalGrid(GridCells.Adaptive(160.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
        itemsIndexed(videos, key = { _, v -> v.path }) { i, v -> VideoCard(v, { onOpen(i) }, { sel = v }) }
    } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        itemsIndexed(videos, key = { _, v -> v.path }) { i, v -> VideoRow(v, { onOpen(i) }, { sel = v }) }
    }
    sel?.let { v -> VideoActions(v, vm, { sel = null }) { onOpen(videos.indexOf(v)) } }
}

@Composable
private fun VideosScreen(vm: LibVM, folder: String, back: () -> Unit) {
    val ctx = LocalContext.current
    var ver by remember { mutableIntStateOf(0) }
    val videos = remember(vm.videos, ver, vm.ver) { Library.sort(vm.videos.filter { it.folder == folder }) }
    Scaffold(topBar = { BackBar(File(folder).name, back) { GridToggle { ver++ }; SortButton { ver++ } } }) { pad ->
        Box(Modifier.padding(pad)) {
            if (videos.isEmpty()) Empty(Icons.Filled.Folder, "Bu klasörde video kalmadı.")
            else VideoList(videos, vm) { i -> PlayerActivity.open(ctx, videos, i) }
        }
    }
}

@Composable
private fun SearchScreen(vm: LibVM, back: () -> Unit) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    val res = remember(q, vm.videos) {
        val s = q.trim().lowercase(TR)
        if (s.isEmpty()) emptyList() else Library.sort(vm.videos.filter { it.title.lowercase(TR).contains(s) })
    }
    Scaffold(topBar = {
        TopAppBar(
            navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
            title = {
                TextField(q, { q = it }, placeholder = { Text("Video ara") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent))
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )
    }) { pad ->
        Box(Modifier.padding(pad)) {
            if (q.isNotBlank() && res.isEmpty()) Empty(Icons.Filled.SearchOff, "\"$q\" için sonuç yok.")
            else VideoList(res, vm) { i -> PlayerActivity.open(ctx, res, i) }
        }
    }
}

@Composable
private fun VideoActions(v: Video, vm: LibVM, dismiss: () -> Unit, play: () -> Unit) {
    val ctx = LocalContext.current
    var dialog by remember { mutableIntStateOf(0) } // 1 ad, 2 sil, 3 özellik, 4 gizle
    if (dialog == 0) ModalBottomSheet(onDismissRequest = dismiss, containerColor = Surf) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(v.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            @Composable fun Item(icon: androidx.compose.ui.graphics.vector.ImageVector, t: String, onClick: () -> Unit) =
                ListItem(headlineContent = { Text(t) }, leadingContent = { Icon(icon, null) }, modifier = Modifier.clickable(onClick = onClick),
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            Item(Icons.Filled.PlayArrow, "Oynat") { dismiss(); play() }
            Item(Icons.Filled.Edit, "Yeniden adlandır") { dialog = 1 }
            Item(Icons.Filled.Share, "Paylaş") {
                dismiss()
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(v.mime ?: "video/*")
                    .putExtra(Intent.EXTRA_STREAM, v.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Paylaş"))
            }
            Item(Icons.Filled.VisibilityOff, "Gizli klasöre taşı") { dialog = 4 }
            Item(Icons.Filled.Info, "Özellikler") { dialog = 3 }
            Item(Icons.Filled.Delete, "Sil") { dialog = 2 }
        }
    }
    when (dialog) {
        1 -> {
            var name by remember { mutableStateOf(File(v.path).nameWithoutExtension) }
            AlertDialog(onDismissRequest = dismiss, title = { Text("Yeniden adlandır") },
                text = { OutlinedTextField(name, { name = it }, singleLine = true, suffix = { Text("." + File(v.path).extension) }) },
                confirmButton = { TextButton(onClick = {
                    val ok = Library.rename(ctx, v, name.trim())
                    Toast.makeText(ctx, if (ok) "Ad değiştirildi" else "Ad değiştirilemedi (aynı adda dosya olabilir)", Toast.LENGTH_SHORT).show()
                    dismiss(); vm.reload()
                }) { Text("Kaydet") } },
                dismissButton = { TextButton(onClick = dismiss) { Text("Vazgeç") } })
        }
        2 -> AlertDialog(onDismissRequest = dismiss, title = { Text("Video silinsin mi?") }, text = { Text("\"${v.title}\" kalıcı olarak silinecek.") },
            confirmButton = { TextButton(onClick = {
                val ok = Library.delete(ctx, v)
                Toast.makeText(ctx, if (ok) "Silindi" else "Silinemedi", Toast.LENGTH_SHORT).show(); dismiss(); vm.reload()
            }) { Text("Sil", color = Color(0xFFFF6B6B)) } },
            dismissButton = { TextButton(onClick = dismiss) { Text("Vazgeç") } })
        3 -> AlertDialog(onDismissRequest = dismiss, title = { Text("Özellikler") }, confirmButton = { TextButton(onClick = dismiss) { Text("Tamam") } },
            text = {
                Column {
                    listOf("Ad" to File(v.path).name, "Konum" to v.folder, "Boyut" to fmtSize(v.size), "Süre" to fmtTime(v.duration),
                        "Çözünürlük" to if (v.width > 0) "${v.width}×${v.height}" else "?", "Tür" to (v.mime ?: "?"),
                        "Eklenme" to java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT, TR).format(v.dateAdded * 1000)
                    ).forEach { (k, x) -> Row(Modifier.padding(vertical = 3.dp)) { Text(k, Modifier.width(92.dp), color = MaterialTheme.colorScheme.onSurfaceVariant); Text(x) } }
                }
            })
        4 -> AlertDialog(onDismissRequest = dismiss, title = { Text("Gizli klasöre taşınsın mı?") },
            text = { Text("Video galeriden ve diğer uygulamalardan gizlenir, sadece Perde'deki şifreli Gizli klasörde görünür.\n\nDikkat: Perde kaldırılırsa gizli videolar da silinir.") },
            confirmButton = { TextButton(onClick = {
                val ok = Library.moveToPrivate(ctx, v)
                Toast.makeText(ctx, if (ok) "Gizli klasöre taşındı" else "Taşınamadı", Toast.LENGTH_SHORT).show(); dismiss(); vm.reload()
            }) { Text("Taşı") } },
            dismissButton = { TextButton(onClick = dismiss) { Text("Vazgeç") } })
    }
}

// ---------------- ağ akışı ----------------
@Composable
private fun NetworkScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    var url by remember { mutableStateOf("") }
    var hist by remember { mutableStateOf(Prefs.netHistory.lines().filter { it.isNotBlank() }) }
    fun play(u: String) {
        val s = u.trim(); if (s.isEmpty()) return
        val h = (listOf(s) + hist.filter { it != s }).take(30)
        Prefs.netHistory = h.joinToString("\n"); hist = h
        PlayerActivity.openKeys(ctx, listOf(s), listOf(Uri.parse(s).lastPathSegment ?: s), 0)
    }
    Scaffold(topBar = { BackBar("Ağ akışı", back) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp)) {
            Text("Bir video adresi yapıştır (MP4, MKV, HLS .m3u8, DASH .mpd …)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(url, { url = it }, label = { Text("https://…") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            Spacer(Modifier.height(10.dp))
            Button(onClick = { play(url) }, enabled = url.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.PlayArrow, null); Text("Oynat") }
            if (hist.isNotEmpty()) {
                Text("Geçmiş", color = Accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
                LazyColumn {
                    items(hist) { h ->
                        Row(Modifier.fillMaxWidth().clickable { play(h) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(h, Modifier.weight(1f).padding(horizontal = 12.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                            IconButton(onClick = { hist = hist - h; Prefs.netHistory = hist.joinToString("\n") }) { Icon(Icons.Filled.Close, "Sil") }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- SMB ----------------
@Composable
private fun SmbListScreen(go: (Route) -> Unit, back: () -> Unit) {
    var list by remember { mutableStateOf(Smb.servers()) }
    var edit by remember { mutableStateOf<SmbServer?>(null) }
    var adding by remember { mutableStateOf(false) }
    Scaffold(topBar = { BackBar("Ağ klasörleri (SMB)", back) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Sunucu ekle") }, containerColor = Accent) }) { pad ->
        Box(Modifier.padding(pad)) {
            if (list.isEmpty()) Empty(Icons.Filled.Lan, "Bilgisayarındaki veya modemindeki paylaşılan klasörü ekle; videoları indirmeden izle.\n\nÖrnek: Sunucu 192.168.1.20, Paylaşım adı Filmler")
            else LazyColumn {
                items(list) { s ->
                    ListItem(headlineContent = { Text(s.title) }, supportingContent = { Text("\\\\${s.host}\\${s.share}" + if (s.user.isNotBlank()) " · ${s.user}" else " · misafir") },
                        leadingContent = { Icon(Icons.Filled.Dns, null, tint = Accent) },
                        trailingContent = { IconButton(onClick = { edit = s }) { Icon(Icons.Filled.Edit, "Düzenle") } },
                        modifier = Modifier.clickable { go(Route.SmbBrowse(s, "")) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                }
            }
        }
    }
    if (adding || edit != null) SmbDialog(edit, onDelete = edit?.let { e -> { list = list - e; Smb.save(list); edit = null } }, dismiss = { adding = false; edit = null }) { s ->
        list = (if (edit != null) list.map { if (it == edit) s else it } else list + s); Smb.save(list); adding = false; edit = null
    }
}

@Composable
private fun SmbDialog(init: SmbServer?, onDelete: (() -> Unit)?, dismiss: () -> Unit, save: (SmbServer) -> Unit) {
    var name by remember { mutableStateOf(init?.name ?: "") }
    var host by remember { mutableStateOf(init?.host ?: "") }
    var share by remember { mutableStateOf(init?.share ?: "") }
    var user by remember { mutableStateOf(init?.user ?: "") }
    var pass by remember { mutableStateOf(init?.pass ?: "") }
    var domain by remember { mutableStateOf(init?.domain ?: "") }
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (init == null) "Sunucu ekle" else "Sunucuyu düzenle") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Görünen ad (isteğe bağlı)") }, singleLine = true)
                OutlinedTextField(host, { host = it.trim() }, label = { Text("Sunucu (IP veya ad)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                OutlinedTextField(share, { share = it.trim().trim('\\', '/') }, label = { Text("Paylaşım adı") }, singleLine = true)
                OutlinedTextField(user, { user = it }, label = { Text("Kullanıcı (boş = misafir)") }, singleLine = true)
                OutlinedTextField(pass, { pass = it }, label = { Text("Şifre") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(domain, { domain = it }, label = { Text("Etki alanı (genelde boş)") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(enabled = host.isNotBlank() && share.isNotBlank(), onClick = { save(SmbServer(name, host, share, user, pass, domain)) }) { Text("Kaydet") } },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Sil", color = Color(0xFFFF6B6B)) }
                TextButton(onClick = dismiss) { Text("Vazgeç") }
            }
        })
}

@Composable
private fun SmbBrowseScreen(s: SmbServer, path: String, go: (Route) -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current
    var entries by remember(s, path) { mutableStateOf<List<SmbEntry>?>(null) }
    var err by remember(s, path) { mutableStateOf<String?>(null) }
    LaunchedEffect(s, path) {
        try { entries = withContext(Dispatchers.IO) { Smb.list(s, path) } }
        catch (e: Exception) { Smb.drop(s); err = "Bağlanılamadı: ${e.message ?: e.javaClass.simpleName}\n\nSunucu adresini, paylaşım adını ve şifreyi kontrol et. Telefon ve bilgisayar aynı Wi-Fi'de olmalı." }
    }
    Scaffold(topBar = { BackBar(if (path.isEmpty()) s.title else path.substringAfterLast('\\'), back) }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val e = entries
            when {
                err != null -> Empty(Icons.Filled.ErrorOutline, err!!)
                e == null -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Accent)
                else -> {
                    val shown = e.filter { it.isDir || isVideoName(it.name) }
                    val vids = shown.filter { !it.isDir }
                    if (shown.isEmpty()) Empty(Icons.Filled.FolderOff, "Bu klasörde video yok.")
                    else LazyColumn {
                        items(shown) { x ->
                            val child = if (path.isEmpty()) x.name else "$path\\${x.name}"
                            ListItem(headlineContent = { Text(x.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                supportingContent = if (x.isDir) null else ({ Text(fmtSize(x.size)) }),
                                leadingContent = { Icon(if (x.isDir) Icons.Filled.Folder else Icons.Filled.Movie, null, tint = if (x.isDir) Accent else Color.White) },
                                modifier = Modifier.clickable {
                                    if (x.isDir) go(Route.SmbBrowse(s, child))
                                    else {
                                        val keys = vids.map { Smb.uriFor(s, if (path.isEmpty()) it.name else "$path\\${it.name}") }
                                        PlayerActivity.openKeys(ctx, keys, vids.map { it.name }, vids.indexOf(x))
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Wi-Fi aktarım ----------------
@Composable
private fun TransferScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    var ok by remember { mutableStateOf(false) }
    var ip by remember { mutableStateOf<String?>(null) }
    var uploaded by remember { mutableStateOf(listOf<String>()) }
    DisposableEffect(Unit) {
        LocalServer.transferEnabled = true
        ok = LocalServer.start(ctx); ip = LocalServer.ip()
        onDispose { LocalServer.transferEnabled = false }
    }
    LaunchedEffect(Unit) { while (true) { uploaded = synchronized(LocalServer.uploaded) { LocalServer.uploaded.toList() }; ip = LocalServer.ip(); delay(1500) } }
    Scaffold(topBar = { BackBar("Wi-Fi aktarım", back) }) { pad ->
        Column(Modifier.padding(pad).padding(20.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Wifi, null, tint = Accent, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(12.dp))
            if (!ok || ip == null) Text("Sunucu başlatılamadı. Telefonun bir Wi-Fi ağına bağlı olduğundan emin ol.", textAlign = TextAlign.Center)
            else {
                Text("Bilgisayarda veya başka bir telefonda tarayıcıyı aç ve bu adrese git:", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text("http://$ip:${LocalServer.PORT}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Accent,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Surf2).padding(horizontal = 18.dp, vertical = 12.dp))
                Spacer(Modifier.height(16.dp))
                Text("• Açılan sayfadan videoları telefona yükleyebilir (Filmler/Perde klasörüne kaydedilir) ya da telefondaki videoları indirebilirsin.\n• İki cihaz aynı Wi-Fi ağında olmalı.\n• Bu ekrandan çıkınca aktarım kapanır.",
                    fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (uploaded.isNotEmpty()) {
                Text("Gelen dosyalar", color = Accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp).fillMaxWidth())
                uploaded.forEach { Text("✓ $it", modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) }
            }
        }
    }
}

// ---------------- gizli klasör ----------------
private fun hashPin(p: String) = MessageDigest.getInstance("SHA-256").digest("perde:$p".toByteArray()).joinToString("") { "%02x".format(it) }

@Composable
private fun PrivateScreen(vm: LibVM, back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var unlocked by remember { mutableStateOf(false) }
    var hasPin by remember { mutableStateOf(Prefs.pinHash.isNotEmpty()) }
    var changing by remember { mutableStateOf(false) }
    var list by remember { mutableStateOf<List<Video>?>(null) }
    var sel by remember { mutableStateOf<Video?>(null) }
    fun load() { scope.launch { list = withContext(Dispatchers.IO) { Library.privateList(ctx) } } }

    Scaffold(topBar = { BackBar("Gizli klasör", back) { if (unlocked) IconButton(onClick = { changing = true }) { Icon(Icons.Filled.Password, "PIN değiştir") } } }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                !hasPin || changing -> PinSetup { p -> Prefs.pinHash = hashPin(p); hasPin = true; changing = false; unlocked = true; load() }
                !unlocked -> PinEntry { p -> if (hashPin(p) == Prefs.pinHash) { unlocked = true; load(); true } else false }
                else -> {
                    val l = list
                    Column {
                        Text("Bu videolar galeride görünmez. Uygulamayı kaldırırsan gizli videolar da silinir; önce geri taşı.",
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                        if (l == null) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally), color = Accent)
                        else if (l.isEmpty()) Empty(Icons.Filled.VisibilityOff, "Gizli klasör boş.\nBir videoya uzun basıp \"Gizli klasöre taşı\" de.")
                        else LazyColumn {
                            itemsIndexed(l) { i, v ->
                                VideoRow(v, { PlayerActivity.open(ctx, l, i) }, { sel = v })
                            }
                        }
                    }
                }
            }
        }
    }
    sel?.let { v ->
        AlertDialog(onDismissRequest = { sel = null }, title = { Text(v.title, maxLines = 2) },
            text = { Text("Bu videoyla ne yapmak istiyorsun?") },
            confirmButton = { TextButton(onClick = {
                val ok = Library.restoreFromPrivate(ctx, File(v.path))
                Toast.makeText(ctx, if (ok) "Filmler/Perde klasörüne taşındı" else "Taşınamadı", Toast.LENGTH_SHORT).show()
                sel = null; load(); vm.reload()
            }) { Text("Geri taşı") } },
            dismissButton = { Row {
                TextButton(onClick = { File(v.path).delete(); Prefs.forget(v.key); sel = null; load() }) { Text("Sil", color = Color(0xFFFF6B6B)) }
                TextButton(onClick = { sel = null }) { Text("Vazgeç") }
            } })
    }
}

@Composable
private fun PinField(v: String, label: String, onChange: (String) -> Unit) =
    OutlinedTextField(v, { s -> onChange(s.filter { it.isDigit() }.take(8)) }, label = { Text(label) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))

@Composable
private fun PinSetup(done: (String) -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Filled.Lock, null, tint = Accent, modifier = Modifier.size(56.dp))
        Text("Gizli klasör için PIN belirle (4–8 rakam)", modifier = Modifier.padding(vertical = 12.dp), textAlign = TextAlign.Center)
        PinField(a, "PIN") { a = it }
        Spacer(Modifier.height(8.dp))
        PinField(b, "PIN tekrar") { b = it }
        if (b.length >= 4 && a != b) Text("PIN'ler aynı değil", color = Color(0xFFFF6B6B), fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        Button(onClick = { done(a) }, enabled = a.length >= 4 && a == b) { Text("Kaydet") }
    }
}

@Composable
private fun PinEntry(check: (String) -> Boolean) {
    var p by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Filled.Lock, null, tint = Accent, modifier = Modifier.size(56.dp))
        Text("PIN'i gir", modifier = Modifier.padding(vertical = 12.dp))
        PinField(p, "PIN") { p = it; wrong = false }
        if (wrong) Text("Yanlış PIN", color = Color(0xFFFF6B6B), fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        Button(onClick = { if (!check(p)) { wrong = true; p = "" } }, enabled = p.length >= 4) { Text("Aç") }
    }
}

// ---------------- ayarlar ----------------
@Composable
private fun SettingsScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    var ver by remember { mutableIntStateOf(0) }
    Scaffold(topBar = { BackBar("Ayarlar", back) }) { pad ->
        key(ver) {
            Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
                SetHeader("Oynatıcı")
                SetSwitch("Kaldığı yerden devam et", "Videoyu yeniden açınca son konumdan başlar", Prefs.resume) { Prefs.resume = it }
                SetSwitch("Arka planda oynat", "Ekran kapanınca veya başka uygulamaya geçince ses devam eder", Prefs.bgPlay) { Prefs.bgPlay = it }
                SetSwitch("Otomatik küçük pencere", "Ana ekrana çıkınca video küçük pencerede sürer", Prefs.autoPip) { Prefs.autoPip = it }
                SetSwitch("Ses yükseltme (%200)", "Sağ tarafta yukarı kaydırınca ses %100'ün üstüne çıkabilir", Prefs.boost) { Prefs.boost = it }
                SetChoice("Varsayılan ekran yönü", PlayerActivity.ORIENTS, Prefs.orient) { Prefs.orient = it; ver++ }
                SetChoice("Çözücü", listOf("Donanım (önerilen, pil dostu)", "Yazılım öncelikli (AC3/DTS ses sorunlarında)"), if (Prefs.decoderSw) 1 else 0) { Prefs.decoderSw = it == 1; ver++ }

                SetHeader("Dokunma hareketleri")
                SetSwitch("Sol tarafta kaydır: parlaklık", null, Prefs.gBright) { Prefs.gBright = it }
                SetSwitch("Sağ tarafta kaydır: ses", null, Prefs.gVolume) { Prefs.gVolume = it }
                SetSwitch("Yatay kaydır: ileri/geri sar", null, Prefs.gSeek) { Prefs.gSeek = it }
                SetSwitch("Çift dokun: kenarlarda sar, ortada duraklat", null, Prefs.gDouble) { Prefs.gDouble = it }
                SetSwitch("İki parmakla yakınlaştır", null, Prefs.gPinch) { Prefs.gPinch = it }
                SetSwitch("Basılı tut: 2× hız", null, Prefs.gLong) { Prefs.gLong = it }
                val steps = listOf(5, 10, 15, 30)
                SetChoice("Sarma adımı", steps.map { "$it saniye" }, steps.indexOf(Prefs.seekStep).coerceAtLeast(0)) { Prefs.seekStep = steps[it]; ver++ }

                SetHeader("Altyazı")
                SetSwitch("Aynı adlı altyazıyı otomatik yükle", "film.mp4 yanında film.srt varsa açılır", Prefs.autoSub) { Prefs.autoSub = it }
                SetChoice("Karakter kodlaması", listOf("Otomatik", "UTF-8", "Türkçe (Windows-1254)"), Prefs.subEnc) { Prefs.subEnc = it; ver++ }
                val sizes = listOf(0.8f, 1f, 1.3f, 1.6f, 2f)
                SetChoice("Yazı boyutu", listOf("Küçük", "Normal", "Büyük", "Çok büyük", "Dev"), sizes.indexOfFirst { kotlin.math.abs(it - Prefs.subScale) < 0.05f }.coerceAtLeast(1)) { Prefs.subScale = sizes[it]; ver++ }

                SetHeader("Kütüphane")
                SetSwitch("Izgara görünümü", null, Prefs.grid) { Prefs.grid = it }

                SetHeader("Hakkında")
                ListItem(headlineContent = { Text("Perde 1.0.0") }, supportingContent = { Text("Kişisel video oynatıcı · Media3 (ExoPlayer) + FFmpeg ses çözücü") },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                ListItem(headlineContent = { Text("İzleme geçmişini temizle") }, supportingContent = { Text("Kaldığı yer bilgileri ve ilerleme çubukları sıfırlanır") },
                    modifier = Modifier.clickable {
                        val e = Prefs.sp.edit()
                        Prefs.sp.all.keys.filter { it.startsWith("p:") || it.startsWith("d:") }.forEach { e.remove(it) }
                        e.remove("last").apply()
                        Toast.makeText(ctx, "İzleme geçmişi temizlendi", Toast.LENGTH_SHORT).show()
                    }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            }
        }
    }
}

@Composable
private fun SetHeader(t: String) = Text(t, color = Accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp))

@Composable
private fun SetSwitch(title: String, sub: String?, value: Boolean, onChange: (Boolean) -> Unit) {
    var v by remember { mutableStateOf(value) }
    ListItem(headlineContent = { Text(title) }, supportingContent = sub?.let { { Text(it) } },
        trailingContent = { Switch(v, { v = it; onChange(it) }) },
        modifier = Modifier.clickable { v = !v; onChange(v) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent))
}

@Composable
private fun SetChoice(title: String, options: List<String>, selected: Int, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ListItem(headlineContent = { Text(title) }, supportingContent = { Text(options.getOrElse(selected) { "" }) },
        modifier = Modifier.clickable { open = true }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text(title) },
        text = { Column { options.forEachIndexed { i, o -> Choice(o, i == selected) { open = false; onChange(i) } } } },
        confirmButton = { TextButton(onClick = { open = false }) { Text("Kapat") } })
}
