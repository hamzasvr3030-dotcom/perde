@file:OptIn(ExperimentalMaterial3Api::class)

package com.perde.oynatici

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Route {
    data object Home : Route
    data class Videos(val folder: String) : Route
    data object Search : Route
    data object Favorites : Route
    data object Playlists : Route
    data class PlaylistR(val id: Long) : Route
    data object Music : Route
    data class AlbumR(val id: Long) : Route
    data class ArtistR(val name: String) : Route
    data class MusicFolder(val path: String) : Route
    data object MusicSearch : Route
    data object Net : Route
    data object Network : Route
    data object SmbList : Route
    data class SmbBrowse(val server: SmbServer, val path: String) : Route
    data object Transfer : Route
    data object More : Route
    data object Private : Route
    data object TrashR : Route
    data object StatsR : Route
    data object HiddenFolders : Route
}

/** Uygulama genelinde paylaşılan arayüz durumu. */
object AppState {
    var showNow by mutableStateOf(false)
    var tab by mutableIntStateOf(0)
    var update by mutableStateOf<Updater.Rel?>(null)
    var sharedUrl by mutableStateOf<String?>(null)
    var openNet by mutableIntStateOf(0)
}

class LibVM(app: Application) : AndroidViewModel(app) {
    var videos by mutableStateOf<List<Video>>(emptyList())
    var songs by mutableStateOf<List<Song>>(emptyList())
    var loading by mutableStateOf(false)
    var ver by mutableIntStateOf(0)
    fun reload() {
        viewModelScope.launch {
            loading = true
            val (v, s) = withContext(Dispatchers.IO) { Library.load(getApplication()) to Music.load(getApplication()) }
            videos = v; songs = s
            loading = false; ver++
        }
    }
    fun bump() { ver++ }
}

fun hasStorage(ctx: Context): Boolean =
    if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
    else ctx.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

fun Context.findActivity(): FragmentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is FragmentActivity) return c; c = c.baseContext }
    return null
}

class MainActivity : AppCompatActivity() {
    private var controller: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleExtras(intent)
        setContent { PerdeTheme { MainApp() } }
        controller = MediaController.Builder(this, SessionToken(this, ComponentName(this, PlaybackService::class.java))).buildAsync()
        Thread { try { Trash.purge(this) } catch (_: Exception) { } }.start()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleExtras(intent)
    }

    private fun handleExtras(i: Intent) {
        if (i.getBooleanExtra("openNow", false)) AppState.showNow = true
        if (i.action == Intent.ACTION_SEND) Web.extractUrl(i.getStringExtra(Intent.EXTRA_TEXT))?.let { AppState.sharedUrl = it; AppState.openNet++ }
        if (i.getBooleanExtra("openDownloads", false)) AppState.openNet++
    }

    override fun onDestroy() {
        controller?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }
}

@Composable
fun MainApp() {
    val ctx = LocalContext.current
    val vm: LibVM = viewModel()
    var granted by remember { mutableStateOf(hasStorage(ctx)) }
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        resumeTick++
        granted = hasStorage(ctx)
        if (granted) vm.reload()
        NowState.refresh()
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        launch(Dispatchers.IO) { try { Web.init(ctx); Web.maybeUpdate(ctx) } catch (_: Throwable) { } }
    }
    // Güncelleme kontrolü: uygulama her öne geldiğinde, en fazla 15 dakikada bir.
    // Sunucuya ulaşılamazsa zaman kaydedilmez, bir sonraki açılışta yeniden denenir.
    LaunchedEffect(resumeTick) {
        if (AppState.update == null && System.currentTimeMillis() - Prefs.lastUpdateCheck > 15 * 60_000L) {
            Updater.check()?.let { r ->
                Prefs.lastUpdateCheck = System.currentTimeMillis()
                if (Updater.newer(r.version, Updater.current(ctx)) && r.version != Prefs.skipVersion) AppState.update = r
            }
        }
    }
    Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
        if (!granted) PermissionScreen { granted = hasStorage(ctx); if (granted) vm.reload() }
        else Shell(vm)
    }
    AppState.update?.let { UpdateDialog(it) { AppState.update = null } }
}

@Composable
private fun Shell(vm: LibVM) {
    val stacks = remember {
        listOf(mutableStateListOf<Route>(Route.Home), mutableStateListOf<Route>(Route.Music),
            mutableStateListOf<Route>(Route.Net), mutableStateListOf<Route>(Route.More))
    }
    val tab = AppState.tab
    val stack = stacks[tab]
    val go: (Route) -> Unit = { stack.add(it) }
    val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    LaunchedEffect(AppState.openNet) {
        if (AppState.openNet > 0) {
            AppState.showNow = false; AppState.tab = 2
            val s = stacks[2]; while (s.size > 1) s.removeAt(s.lastIndex); s.add(Route.Network)
        }
    }
    BackHandler(AppState.showNow || stack.size > 1 || tab != 0) {
        when {
            AppState.showNow -> AppState.showNow = false
            stack.size > 1 -> back()
            else -> AppState.tab = 0
        }
    }
    Scaffold(
        containerColor = Bg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Column {
                MiniPlayer { AppState.showNow = true }
                NavigationBar(containerColor = Surf, tonalElevation = 0.dp) {
                    listOf(
                        Triple("Videolar", Icons.Filled.Home, Icons.Outlined.Home),
                        Triple("Müzik", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
                        Triple("Ağ", Icons.Filled.Wifi, Icons.Outlined.Wifi),
                        Triple("Daha", Icons.Filled.Settings, Icons.Outlined.Settings)
                    ).forEachIndexed { i, (label, on, off) ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { if (tab == i) { while (stack.size > 1) stack.removeAt(stack.lastIndex) } else AppState.tab = i },
                            icon = { Icon(if (tab == i) on else off, label) },
                            label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFF14100E), indicatorColor = Accent, selectedTextColor = Accent)
                        )
                    }
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (val r = stack.last()) {
                Route.Home -> HomeScreen(vm, go)
                is Route.Videos -> VideosScreen(vm, r.folder, back)
                Route.Search -> SearchScreen(vm, back)
                Route.Favorites -> FavoritesScreen(vm, back)
                Route.Playlists -> PlaylistsScreen(vm, go, back)
                is Route.PlaylistR -> PlaylistScreen(vm, r.id, back)
                Route.Music -> MusicScreen(vm, go)
                is Route.AlbumR -> AlbumScreen(vm, r.id, back)
                is Route.ArtistR -> ArtistScreen(vm, r.name, go, back)
                is Route.MusicFolder -> MusicFolderScreen(vm, r.path, back)
                Route.MusicSearch -> MusicSearchScreen(vm, back)
                Route.Net -> NetScreen(go)
                Route.Network -> NetworkScreen(back)
                Route.SmbList -> SmbListScreen(go, back)
                is Route.SmbBrowse -> SmbBrowseScreen(r.server, r.path, go, back)
                Route.Transfer -> TransferScreen(back)
                Route.More -> MoreScreen(vm, go)
                Route.Private -> PrivateScreen(vm, back)
                Route.TrashR -> TrashScreen(vm, back)
                Route.StatsR -> StatsScreen(vm, back)
                Route.HiddenFolders -> HiddenFoldersScreen(vm, back)
            }
        }
    }
    if (AppState.showNow) NowPlayingScreen(vm) { AppState.showNow = false }
}

@Composable
private fun PermissionScreen(onResult: () -> Unit) {
    val ctx = LocalContext.current
    val legacy = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onResult() }
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(AccentDeep, Bg))).padding(32.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.VideoLibrary, null, tint = Accent, modifier = Modifier.size(80.dp))
        Spacer(Modifier.height(16.dp))
        Text("Kadraj'a hoş geldin", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text("Videolarını ve müziklerini bulmak, altyazı dosyalarını okumak ve dosyaları düzenleyebilmek için dosya erişim izni gerekiyor. Açılan ekranda \"Kadraj\" için izni aç, sonra geri dön.",
            textAlign = TextAlign.Center, color = Muted)
        Spacer(Modifier.height(28.dp))
        Button(onClick = {
            if (Build.VERSION.SDK_INT >= 30) {
                try { ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))) }
                catch (_: Exception) { ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
            } else legacy.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }, modifier = Modifier.height(52.dp).widthIn(min = 220.dp)) { Text("İzin ver", fontSize = 16.sp) }
    }
}

@Composable
fun UpdateDialog(r: Updater.Rel, dismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<Float?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = { if (progress == null) dismiss() },
        icon = { Icon(Icons.Filled.SystemUpdate, null, tint = Accent) },
        title = { Text("Yeni sürüm: ${r.version}") },
        text = {
            Column {
                Text("Şu anki sürüm: ${Updater.current(ctx)}", color = Muted, fontSize = 13.sp)
                if (r.notes.isNotBlank()) Text(r.notes.take(600), modifier = Modifier.padding(top = 8.dp), fontSize = 14.sp)
                progress?.let {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth(), color = Accent)
                    Text("İndiriliyor %${(it * 100).toInt()}", fontSize = 12.sp, color = Muted)
                }
                err?.let { Text(it, color = Color(0xFFFF6B6B), modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(enabled = progress == null, onClick = {
                progress = 0f; err = null
                scope.launch {
                    val f = Updater.download(ctx, r) { progress = it }
                    progress = null
                    if (f == null) err = "İndirilemedi, internet bağlantını kontrol et."
                    else if (!Updater.install(ctx, f)) err = "Açılan ekranda \"Bu kaynaktan izin ver\"i aç, sonra tekrar Güncelle'ye bas."
                }
            }) { Text("Güncelle") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { Prefs.skipVersion = r.version; dismiss() }) { Text("Bu sürümü atla") }
                TextButton(onClick = dismiss) { Text("Sonra") }
            }
        })
}
