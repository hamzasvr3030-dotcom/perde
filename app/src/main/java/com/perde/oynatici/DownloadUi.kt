@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.perde.oynatici

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import kotlinx.coroutines.Job as CJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private val OK = Color(0xFF3DDC97)
private val BAD = Color(0xFFFF8A80)

/** İndirme isteği: tek videolar ya da bir liste. */
private sealed class DlReq {
    data class Singles(val items: List<Triple<String, String, String?>>) : DlReq()
    data class ListReq(val title: String, val entries: List<Web.Entry>, val follow: Boolean, val url: String, val allIds: List<String>) : DlReq()
    val count get() = when (this) { is Singles -> items.size; is ListReq -> entries.size }
}

@Composable
fun DownloadScreen(go: (Route) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clip = LocalClipboardManager.current
    var mode by rememberSaveableInt(0)              // 0 bağlantı, 1 arama
    var text by remember { mutableStateOf("") }
    var analysis by remember { mutableStateOf<Web.Analysis?>(null) }
    var preview by remember { mutableStateOf<Web.Preview?>(null) }
    var loading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var wholeList by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }
    var follow by remember { mutableStateOf(false) }
    var request by remember { mutableStateOf<Pair<DlReq, Boolean>?>(null) }   // istek + ses mi
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Web.Entry>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var accounts by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<CJob?>(null) }

    LaunchedEffect(Unit) { Dl.init(ctx) }
    LaunchedEffect(AppState.sharedUrl) { AppState.sharedUrl?.let { text = it; mode = 0; AppState.sharedUrl = null } }

    val urls = remember(text) { Web.extractUrls(text) }
    val single = urls.singleOrNull()

    fun analyze(u: String) {
        job?.cancel()
        analysis = null; preview = null; err = null; selected.clear(); wholeList = false
        if (!Web.isPage(u) && !Spotify.isSpotify(u)) { loading = false; return }
        loading = true
        job = scope.launch {
            if (!Spotify.isSpotify(u)) launch { preview = Web.preview(u) }
            try {
                val a = if (Spotify.isSpotify(u)) {
                    val (name, tracks) = Spotify.read(u)
                    Web.Analysis.Multi(u, name, "Spotify", tracks.size, tracks.mapIndexed { i, t ->
                        Web.Entry("sp:$i:${t.artist}-${t.title}", Spotify.searchUrl(t), "${t.artist} - ${t.title}", (t.durationMs / 1000).toInt(), t.artist, null)
                    }, false)
                } else Web.analyze(ctx, u)
                analysis = a
                if (a is Web.Analysis.Multi) { selected.addAll(a.entries.take(if (a.isChannel) 25 else 500).map { it.id }) }
            } catch (e: Exception) { err = Web.explain(e) }
            loading = false
        }
    }
    LaunchedEffect(single) { if (single != null) { delay(120); analyze(single) } else { analysis = null; preview = null; err = null; loading = false } }

    fun openList(u: String) {
        loading = true; err = null
        scope.launch {
            try { val a = Web.analyze(ctx, u); analysis = a; wholeList = true; if (a is Web.Analysis.Multi) { selected.clear(); selected.addAll(a.entries.map { it.id }) } }
            catch (e: Exception) { err = Web.explain(e) }
            loading = false
        }
    }

    fun ask(r: DlReq, audio: Boolean) {
        if ((audio && DlPrefs.askAudio) || (!audio && DlPrefs.askVideo)) request = r to audio
        else start(ctx, r, DlOpts.defaults(audio), 0L)
    }

    Page("İndir", null, actions = {
        IconButton(onClick = { ctx.startActivity(android.content.Intent(ctx, BrowserActivity::class.java)) }) { Icon(Icons.Filled.Public, "Tarayıcı") }
        IconButton(onClick = { go(Route.DlHistory) }) { Icon(Icons.Filled.History, "Geçmiş") }
        IconButton(onClick = { accounts = true }) { Icon(Icons.Filled.AccountCircle, "Hesaplar") }
        IconButton(onClick = { settings = true }) { Icon(Icons.Filled.Tune, "İndirme ayarları") }
    }) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
            item {
                Box(Modifier.padding(bottom = 14.dp)) { PillSwitch(listOf("Bağlantı", "YouTube'da ara"), mode) { mode = it } }
            }
            if (mode == 0) {
                item {
                    OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth(), shape = CircleShape.takeIf { !text.contains('\n') } ?: RoundedCornerShape(24.dp), maxLines = 5,
                        placeholder = { Text("Video veya ses bağlantısı yapıştırın") },
                        trailingIcon = {
                            if (text.isEmpty()) IconButton(onClick = { clip.getText()?.text?.let { text = it } }) { Icon(Icons.Filled.ContentPaste, "Yapıştır") }
                            else IconButton(onClick = { text = "" }) { Icon(Icons.Filled.Close, "Temizle") }
                        })
                    Text("YouTube, Instagram, TikTok, X, Facebook, Vimeo, SoundCloud, Spotify listeleri ve yüzlerce site · tek video, liste veya kanal",
                        color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
                }
                if (urls.size > 1) item { BulkCard(urls) { audio -> ask(DlReq.Singles(urls.map { Triple(it, Web.site(it), null) }), audio) } }
                else if (single != null) {
                    if (loading && analysis == null) item { LoadingCard(single, preview) }
                    err?.let { e -> item { Text("⚠ $e", color = BAD, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Surf).padding(14.dp)) } }
                    when (val a = analysis) {
                        is Web.Analysis.Single -> item {
                            SingleCard(a, onPlay = { PlayerActivity.openKeys(ctx, listOf(single), listOf(a.s.title), 0) },
                                onVideo = { ask(DlReq.Singles(listOf(Triple(single, a.s.title, a.s.thumb))), false) },
                                onAudio = { ask(DlReq.Singles(listOf(Triple(single, a.s.title, a.s.thumb))), true) },
                                onList = a.playlistUrl?.let { pl -> { openList(pl) } })
                        }
                        is Web.Analysis.Multi -> {
                            item { MultiHeader(a, selected, follow, { follow = it }, { n -> selected.clear(); selected.addAll(a.entries.take(n).map { it.id }) }) { audio ->
                                val es = a.entries.filter { it.id in selected }
                                if (es.isNotEmpty()) ask(DlReq.ListReq(a.title, es, follow, a.url, a.entries.map { it.id }), audio)
                            } }
                            itemsIndexed(a.entries, key = { i, e -> "e$i${e.id}" }) { i, e ->
                                EntryRow(i + 1, e, e.id in selected, onToggle = { if (e.id in selected) selected.remove(e.id) else selected.add(e.id) },
                                    onPlay = { PlayerActivity.openKeys(ctx, listOf(e.url), listOf(e.title), 0) })
                            }
                        }
                        null -> {}
                    }
                }
            } else {
                item {
                    OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), singleLine = true,
                        label = { Text("Şarkı, video veya kanal ara") }, leadingIcon = { Icon(Icons.Filled.Search, null) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            if (query.isNotBlank()) { searching = true; err = null
                                scope.launch { try { results = Web.search(ctx, query.trim()) } catch (e: Exception) { err = Web.explain(e) }; searching = false } }
                        }))
                    Spacer(Modifier.height(10.dp))
                    if (searching) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent)
                    err?.let { Text("⚠ $it", color = BAD, modifier = Modifier.padding(vertical = 8.dp)) }
                }
                results?.let { r ->
                    if (r.isEmpty()) item { Text("Sonuç bulunamadı.", color = Muted) }
                    items(r, key = { "s" + it.id }) { e ->
                        SearchRow(e, onPlay = { PlayerActivity.openKeys(ctx, listOf(e.url), listOf(e.title), 0) },
                            onVideo = { ask(DlReq.Singles(listOf(Triple(e.url, e.title, e.thumb))), false) },
                            onAudio = { ask(DlReq.Singles(listOf(Triple(e.url, e.title, e.thumb))), true) })
                    }
                }
            }

            // ---- takip edilen listeler ----
            if (Dl.follows.isNotEmpty()) {
                item { SectionTitle("Takip edilen listeler", "Kontrol et") { scope.launch { val n = Dl.checkFollows(ctx, false); toast(ctx, if (n > 0) "$n yeni video bulundu" else "Yeni video yok") } } }
                items(Dl.follows, key = { "f" + it.url }) { f -> FollowRow(f, onDownload = { scope.launch { Dl.downloadNew(ctx, f) }; toast(ctx, "Yeni videolar sıraya eklendi") }, onRemove = { Dl.unfollow(f) }) }
            }

            // ---- indirmeler ----
            if (Dl.jobs.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("İndirmeler", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                        if (Dl.jobs.any { it.active }) TextButton(onClick = { Dl.pauseAll() }) { Text("Tümünü duraklat") }
                        else if (Dl.jobs.any { it.state == Job.PAUSED }) TextButton(onClick = { Dl.resumeAll() }) { Text("Devam ettir") }
                        if (Dl.jobs.any { it.state == Job.DONE || it.state == Job.CANCEL || it.state == Job.SKIP }) TextButton(onClick = { Dl.clearFinished() }) { Text("Temizle") }
                    }
                }
                items(Dl.groups, key = { it.id }) { g -> GroupCard(g) }
                items(Dl.jobs.filter { it.group == null }, key = { it.id }) { j -> JobRow(j) }
            }
        }
    }

    request?.let { (r, audio) ->
        ModalBottomSheet(onDismissRequest = { request = null }, containerColor = Surf, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                DlOptionsContent(audio, r.count) { o, at -> start(ctx, r, o, at); request = null }
            }
        }
    }
    if (settings) ModalBottomSheet(onDismissRequest = { settings = false }, containerColor = Surf) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) { DlSettingsContent() }
    }
    if (accounts) ModalBottomSheet(onDismissRequest = { accounts = false }, containerColor = Surf) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) { AccountsContent() }
    }
}

@Composable
private fun rememberSaveableInt(v: Int) = androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(v) }

private fun start(ctx: android.content.Context, r: DlReq, o: DlOpts, at: Long) {
    when (r) {
        is DlReq.Singles -> Dl.addSingles(ctx, r.items, o, at)
        is DlReq.ListReq -> Dl.addList(ctx, r.title, r.entries, o, r.follow, r.url, r.allIds, at = at)
    }
    toast(ctx, when {
        at > System.currentTimeMillis() + 60_000 -> "${r.count} öğe " + java.text.SimpleDateFormat("HH:mm", TR).format(at) + "'de indirilecek"
        r.count > 1 -> "${r.count} öğe sıraya eklendi"
        else -> "İndirme başladı"
    })
}

@Composable
private fun SectionTitle(t: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(t, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun Thumb169(url: String?, m: Modifier, dur: Int = 0) {
    Box(m.aspectRatio(16 / 9f).clip(RoundedCornerShape(12.dp)).background(Surf2)) {
        if (url != null) AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (dur > 0) Text(fmtTime(dur * 1000L), color = Color.White, fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xB3000000)).padding(horizontal = 4.dp))
    }
}

@Composable
private fun LoadingCard(u: String, p: Web.Preview?) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surf).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (p != null) Thumb169(p.thumb, Modifier.width(130.dp)) else Box(Modifier.width(130.dp).aspectRatio(16 / 9f).clip(RoundedCornerShape(12.dp)).background(Surf2), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(26.dp), color = Accent, strokeWidth = 3.dp)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(Web.site(u), color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(p?.title ?: "Bağlantı inceleniyor…", maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            p?.author?.let { Text(it, color = Muted, fontSize = 12.sp) }
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp).clip(CircleShape), color = Accent, trackColor = Surf2)
        }
    }
}

@Composable
private fun BigAction(icon: ImageVector, label: String, primary: Boolean, m: Modifier, onClick: () -> Unit) {
    if (primary) Button(onClick = onClick, modifier = m.height(52.dp), shape = RoundedCornerShape(14.dp)) { Icon(icon, null); Spacer(Modifier.width(6.dp)); Text(label, maxLines = 1) }
    else FilledTonalButton(onClick = onClick, modifier = m.height(52.dp), shape = RoundedCornerShape(14.dp)) { Icon(icon, null); Spacer(Modifier.width(6.dp)); Text(label, maxLines = 1) }
}

@Composable
private fun SingleCard(a: Web.Analysis.Single, onPlay: () -> Unit, onVideo: () -> Unit, onAudio: () -> Unit, onList: (() -> Unit)?) {
    val s = a.s
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surf).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Thumb169(s.thumb, Modifier.width(140.dp), s.durationSec)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(Web.site(s.page), color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(s.title, maxLines = 3, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                listOfNotNull(s.uploader, if (s.height > 0) "${s.height}p" else null).joinToString(" · ").takeIf { it.isNotEmpty() }?.let { Text(it, color = Muted, fontSize = 12.sp) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigAction(Icons.Filled.PlayArrow, "Oynat", false, Modifier.weight(0.8f), onPlay)
            BigAction(Icons.Filled.Movie, "Video", true, Modifier.weight(1f), onVideo)
            BigAction(Icons.Filled.MusicNote, "Ses", true, Modifier.weight(1f), onAudio)
        }
        if (onList != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onList, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null); Spacer(Modifier.width(6.dp)); Text("Bu video bir listenin parçası · Tüm listeyi göster")
            }
        }
    }
}

@Composable
private fun BulkCard(urls: List<String>, onDl: (Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surf).padding(14.dp)) {
        Text("${urls.size} bağlantı bulundu", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        urls.take(6).forEach { Text("• ${Web.site(it)} · ${it.take(60)}", color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        if (urls.size > 6) Text("… ve ${urls.size - 6} tane daha", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigAction(Icons.Filled.Movie, "Hepsi video", true, Modifier.weight(1f)) { onDl(false) }
            BigAction(Icons.Filled.MusicNote, "Hepsi ses", true, Modifier.weight(1f)) { onDl(true) }
        }
    }
}

@Composable
private fun MultiHeader(a: Web.Analysis.Multi, selected: List<String>, follow: Boolean, onFollow: (Boolean) -> Unit, pickFirst: (Int) -> Unit, onDl: (Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brush.verticalGradient(listOf(AccentDeep, Surf))).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Accent), contentAlignment = Alignment.Center) {
                Icon(if (a.isChannel) Icons.Filled.AccountBox else Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = Color(0xFF14100E))
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(if (a.isChannel) "Kanal" else "Oynatma listesi", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(a.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(a.uploader, "${a.count} video").joinToString(" · "), color = Muted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (a.isChannel) listOf(10, 25, 50).forEach { n -> AssistChip(onClick = { pickFirst(n) }, label = { Text("Son $n") }) }
            AssistChip(onClick = { pickFirst(a.entries.size) }, label = { Text("Tümünü seç") }, leadingIcon = { Icon(Icons.Filled.SelectAll, null, Modifier.size(18.dp)) })
            AssistChip(onClick = { pickFirst(0) }, label = { Text("Hiçbiri") })
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onFollow(!follow) }) {
            Checkbox(follow, onFollow)
            Column { Text("Listeyi takip et", fontWeight = FontWeight.Medium); Text("Yeni video eklenince haber ver", color = Muted, fontSize = 12.sp) }
        }
        Spacer(Modifier.height(6.dp))
        val sp = a.entries.firstOrNull()?.url?.startsWith("ytsearch") == true
        if (sp) Text("Spotify şarkıları YouTube'da bulunup indirilir; şarkı ve sanatçı adı Spotify'daki gibi yazılır.", color = AccentSoft, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        Text("${selected.size} ${if (sp) "şarkı" else "video"} seçili · İndirilince aynı adla liste oluşturulur", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigAction(Icons.Filled.Movie, "Video indir", true, Modifier.weight(1f)) { onDl(false) }
            BigAction(Icons.Filled.MusicNote, "Ses indir", true, Modifier.weight(1f)) { onDl(true) }
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun EntryRow(n: Int, e: Web.Entry, on: Boolean, onToggle: () -> Unit, onPlay: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onToggle).padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(on, { onToggle() })
        Text("$n", color = Muted, fontSize = 12.sp, modifier = Modifier.width(26.dp))
        Thumb169(e.thumb, Modifier.width(96.dp), e.durationSec)
        Text(e.title, Modifier.weight(1f).padding(horizontal = 10.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
        if (!e.url.startsWith("ytsearch")) IconButton(onClick = onPlay) { Icon(Icons.Filled.PlayCircleOutline, "Oynat", tint = Muted) }
    }
}

@Composable
private fun SearchRow(e: Web.Entry, onPlay: () -> Unit, onVideo: () -> Unit, onAudio: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onPlay).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Thumb169(e.thumb, Modifier.width(132.dp), e.durationSec)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(e.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            e.uploader?.let { Text(it, color = Muted, fontSize = 12.sp, maxLines = 1) }
        }
        IconButton(onClick = onVideo) { Icon(Icons.Filled.Movie, "Video indir", tint = Accent) }
        IconButton(onClick = onAudio) { Icon(Icons.Filled.MusicNote, "Ses indir", tint = Accent) }
    }
}

@Composable
private fun FollowRow(f: Follow, onDownload: () -> Unit, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(Surf).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Notifications, null, tint = Accent)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(f.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (f.newIds.isEmpty()) "Yeni video yok · ${if (f.opts.audio) "ses" else "video"}" else "${f.newIds.size} yeni video", color = if (f.newIds.isEmpty()) Muted else Accent, fontSize = 12.sp)
        }
        if (f.newIds.isNotEmpty()) Button(onClick = onDownload) { Text("İndir") }
        IconButton(onClick = onRemove) { Icon(Icons.Filled.NotificationsOff, "Takibi bırak", tint = Muted) }
    }
}

private fun sourceOf(url: String, audio: Boolean, list: Boolean = false): Pair<ImageVector, Color> {
    val site = Web.site(url)
    return when {
        list -> (if (audio) Icons.Filled.QueueMusic else Icons.Filled.VideoLibrary) to Accent
        url.startsWith("ytsearch") -> Icons.Filled.MusicNote to Color(0xFF1DB954)
        site == "YouTube" -> Icons.Filled.SmartDisplay to Color(0xFFFF3B30)
        site == "Instagram" -> Icons.Filled.CameraAlt to Color(0xFFE1306C)
        site == "TikTok" -> Icons.Filled.MusicVideo to Color(0xFF25F4EE)
        site.startsWith("X") -> Icons.Filled.Tag to Color(0xFFDDDDDD)
        site == "Facebook" -> Icons.Filled.Facebook to Color(0xFF1877F2)
        site == "SoundCloud" -> Icons.Filled.Cloud to Color(0xFFFF7700)
        audio -> Icons.Filled.MusicNote to Accent
        else -> Icons.Filled.Videocam to Accent
    }
}

@Composable
private fun JobRow(j: Job, compact: Boolean = false) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(vertical = if (compact) 2.dp else 5.dp).clip(RoundedCornerShape(20.dp)).background(if (compact) Color.Transparent else Surf)
        .clickable(enabled = j.state == Job.DONE || (j.state == Job.SKIP && j.file != null)) { j.file?.let { playKeys(ctx, listOf(it)) } }
        .padding(if (compact) 6.dp else 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!compact) {
            val (ic, col) = sourceOf(j.url, j.opts.audio)
            SourceBadge(ic, col)
        } else Text("${j.index + 1}", color = Muted, fontSize = 12.sp, modifier = Modifier.width(26.dp))
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(j.title, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            val sub = when (j.state) {
                Job.RUN -> listOf("%${(j.progress * 100).toInt()}", j.phase, j.speed, j.eta).filter { it.isNotEmpty() }.joinToString(" · ")
                Job.ERR -> j.error ?: "Hata"
                Job.DONE -> "İndirildi · ${j.opts.label}" + (j.file?.let { " · " + fmtSize(File(it).length()) } ?: "") + (if (j.timing.isNotEmpty()) "\n" + j.timing else "")
                else -> j.phase
            }
            Text(sub, color = when (j.state) { Job.ERR -> BAD; Job.DONE, Job.SKIP -> OK; else -> Muted }, fontSize = 12.sp, maxLines = 2)
            if (j.state == Job.RUN || j.state == Job.PAUSED || j.state == Job.Q) LinearProgressIndicator(progress = { j.progress }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(6.dp).clip(CircleShape), color = Accent, trackColor = Surf2, drawStopIndicator = {})
        }
        when (j.state) {
            Job.SCHED -> { TextButton(onClick = { Dl.startNow(j) }) { Text("Şimdi") }; IconButton(onClick = { Dl.cancel(j) }) { Icon(Icons.Filled.Close, "İptal", tint = Muted) } }
            Job.RUN, Job.Q, Job.WAIT -> { RoundBtn(Icons.Filled.Pause, "Duraklat") { Dl.pause(j) }; JobMenu(j) }
            Job.PAUSED -> { RoundBtn(Icons.Filled.PlayArrow, "Devam") { Dl.resume(j) }; JobMenu(j) }
            Job.ERR, Job.CANCEL -> { IconButton(onClick = { Dl.resume(j) }) { Icon(Icons.Filled.Refresh, "Tekrar dene", tint = Accent) }; if (!compact) IconButton(onClick = { Dl.remove(j) }) { Icon(Icons.Filled.Delete, "Kaldır", tint = Muted) } }
            Job.DONE -> Icon(Icons.Filled.CheckCircle, null, tint = OK, modifier = Modifier.padding(8.dp))
            else -> Icon(Icons.Filled.Done, null, tint = Muted, modifier = Modifier.padding(8.dp))
        }
    }
}

@Composable
private fun RoundBtn(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(Surf2).pressable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = Color.White)
    }
}

@Composable
private fun JobMenu(j: Job) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreHoriz, "Diğer", tint = Muted) }
        DropdownMenu(open, { open = false }) {
            if (j.state == Job.PAUSED || j.state == Job.ERR) DropdownMenuItem({ Text("Devam et") }, { open = false; Dl.resume(j) }, leadingIcon = { Icon(Icons.Filled.PlayArrow, null) })
            if (j.active) DropdownMenuItem({ Text("İptal et") }, { open = false; Dl.cancel(j) }, leadingIcon = { Icon(Icons.Filled.Close, null) })
            DropdownMenuItem({ Text("Listeden kaldır") }, { open = false; Dl.remove(j) }, leadingIcon = { Icon(Icons.Filled.Delete, null) })
        }
    }
}

@Composable
private fun GroupCard(g: DlGroup) {
    var open by remember { mutableStateOf(false) }
    val js = Dl.jobs.filter { it.group == g.id }
    val done = js.count { it.state == Job.DONE || it.state == Job.SKIP }
    val failed = js.count { it.state == Job.ERR }
    val run = js.firstOrNull { it.state == Job.RUN }
    val prog = if (js.isEmpty()) 0f else (js.sumOf { (if (it.state == Job.DONE || it.state == Job.SKIP) 1f else it.progress).toDouble() } / js.size).toFloat()
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(20.dp)).background(Surf)) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            SourceBadge(if (g.audio) Icons.Filled.QueueMusic else Icons.Filled.VideoLibrary, Accent)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(g.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$done/${js.size} tamamlandı" + (if (failed > 0) " · $failed hata" else "") + (run?.let { " · ${it.title.take(24)}" } ?: ""),
                    color = if (failed > 0) BAD else Muted, fontSize = 12.sp, maxLines = 1)
                LinearProgressIndicator(progress = { prog }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(5.dp).clip(CircleShape), color = Accent, trackColor = Surf2)
            }
            if (js.any { it.active }) IconButton(onClick = { js.forEach { Dl.pause(it) } }) { Icon(Icons.Filled.Pause, "Duraklat") }
            else if (js.any { it.state == Job.PAUSED || it.state == Job.ERR }) IconButton(onClick = { js.filter { it.state == Job.PAUSED || it.state == Job.ERR }.forEach { Dl.resume(it) } }) { Icon(Icons.Filled.PlayArrow, "Devam", tint = Accent) }
            else IconButton(onClick = { Dl.removeGroup(g) }) { Icon(Icons.Filled.Close, "Kaldır", tint = Muted) }
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = Muted)
        }
        if (open) Column(Modifier.padding(start = 8.dp, end = 4.dp, bottom = 8.dp)) { js.sortedBy { it.index }.forEach { JobRow(it, compact = true) } }
    }
}

// ---------------- seçenekler ----------------
@Composable
fun DlOptionsContent(audioInit: Boolean, count: Int, onStart: (DlOpts, Long) -> Unit) {
    var audio by remember { mutableStateOf(audioInit) }
    var h by remember { mutableIntStateOf(DlPrefs.height) }
    var fmt by remember { mutableStateOf(DlPrefs.audioFmt) }
    var kbps by remember { mutableIntStateOf(DlPrefs.kbps) }
    var subs by remember { mutableStateOf(DlPrefs.subs) }
    var sponsor by remember { mutableStateOf(DlPrefs.sponsor) }
    var chapters by remember { mutableStateOf(DlPrefs.chapters) }
    var meta by remember { mutableStateOf(DlPrefs.meta) }
    var smart by remember { mutableStateOf(DlPrefs.smart) }
    var saveDefault by remember { mutableStateOf(false) }
    var whenMode by remember { mutableIntStateOf(0) }      // 0 şimdi, 1 bu gece, 2 saat seç
    var pickTime by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(DlAlarm.tonight(2, 0)) }
    Text(if (count > 1) "$count öğeyi indir" else "İndir", fontSize = 21.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(!audio, { audio = false }, SegmentedButtonDefaults.itemShape(0, 2), icon = { Icon(Icons.Filled.Movie, null, Modifier.size(18.dp)) }) { Text("Video olarak") }
        SegmentedButton(audio, { audio = true }, SegmentedButtonDefaults.itemShape(1, 2), icon = { Icon(Icons.Filled.MusicNote, null, Modifier.size(18.dp)) }) { Text("Ses olarak") }
    }
    if (!audio) {
        OptLabel("Kalite")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DlPrefs.HEIGHTS.forEach { v -> FilterChip(h == v, { h = v }, label = { Text(if (v >= 2160) "4K" else if (v == 1440) "2K" else "${v}p") }) }
        }
        Text("Seçilen kalite yoksa en yakın düşük kalite iner. 4K için telefonun desteklemesi gerekir.", color = Muted, fontSize = 12.sp)
        OptSwitch("Altyazıyla indir", "Türkçe/İngilizce altyazı da iner, oynatırken kendiliğinden açılır", subs) { subs = it }
        OptSwitch("Bölümleri koru", "YouTube bölüm başlıkları videoya yazılır", chapters) { chapters = it }
        OptSwitch("SponsorBlock", "YouTube'daki sponsor reklamlarını kesip çıkar", sponsor) { sponsor = it }
    } else {
        OptLabel("Biçim")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("m4a" to "M4A (önerilen)", "mp3" to "MP3 (her yerde çalar)").forEach { (k, n) -> FilterChip(fmt == k, { fmt = k }, label = { Text(n) }) }
        }
        OptLabel("Ses kalitesi")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(128, 192, 320).forEach { v -> FilterChip(kbps == v, { kbps = v }, label = { Text("$v kbps") }) }
        }
        OptSwitch("Kapak ve şarkı bilgisi", "Kapak resmi, sanatçı, şarkı adı ve albüm dosyaya yazılır", meta) { meta = it }
        OptSwitch("Akıllı ad düzeltme", "\"Tarkan - Kuzu Kuzu (Official Video)\" → Tarkan / Kuzu Kuzu", smart) { smart = it }
        OptSwitch("SponsorBlock", "Sponsor reklamlarını ve müzik videolarındaki konuşma kısımlarını kes", sponsor) { sponsor = it }
    }
    OptLabel("Ne zaman?")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(whenMode == 0, { whenMode = 0 }, label = { Text("Şimdi") })
        FilterChip(whenMode == 1, { whenMode = 1 }, label = { Text("Bu gece 02:00") }, leadingIcon = { Icon(Icons.Filled.Bedtime, null, Modifier.size(16.dp)) })
        FilterChip(whenMode == 2, { whenMode = 2; pickTime = true }, label = { Text(if (whenMode == 2) java.text.SimpleDateFormat("d MMM HH:mm", TR).format(custom) else "Saat seç") },
            leadingIcon = { Icon(Icons.Filled.Schedule, null, Modifier.size(16.dp)) })
    }
    if (whenMode > 0) Text("Telefon uykudayken de zamanı gelince başlar; Wi-Fi'nin boş olduğu saatler için idealdir.", color = Muted, fontSize = 12.sp)
    if (pickTime) {
        val st = rememberTimePickerState(2, 0, true)
        AlertDialog(onDismissRequest = { pickTime = false }, title = { Text("Başlama saati") }, text = { TimePicker(st) },
            confirmButton = { TextButton(onClick = { custom = DlAlarm.tonight(st.hour, st.minute); pickTime = false }) { Text("Tamam") } },
            dismissButton = { TextButton(onClick = { pickTime = false; if (whenMode == 2) whenMode = 0 }) { Text("Vazgeç") } })
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { saveDefault = !saveDefault }.padding(top = 6.dp)) {
        Checkbox(saveDefault, { saveDefault = it })
        Text("Bu ayarları varsayılan yap ve bir daha sorma", fontSize = 14.sp)
    }
    Spacer(Modifier.height(10.dp))
    Button(onClick = {
        val o = DlOpts(audio, h, fmt, kbps, subs, sponsor, chapters, meta, smart)
        if (saveDefault) {
            DlPrefs.height = h; DlPrefs.audioFmt = fmt; DlPrefs.kbps = kbps; DlPrefs.subs = subs; DlPrefs.sponsor = sponsor
            DlPrefs.chapters = chapters; DlPrefs.meta = meta; DlPrefs.smart = smart
            if (audio) DlPrefs.askAudio = false else DlPrefs.askVideo = false
        }
        onStart(o, when (whenMode) { 1 -> DlAlarm.tonight(2, 0); 2 -> custom; else -> 0L })
    }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
        Icon(if (whenMode == 0) Icons.Filled.Download else Icons.Filled.Schedule, null); Spacer(Modifier.width(8.dp))
        Text(if (whenMode > 0) "Zamanla" else if (count > 1) "$count öğeyi indir" else "İndirmeyi başlat", fontSize = 16.sp)
    }
}

@Composable
private fun OptLabel(t: String) = Text(t, color = Accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))

@Composable
private fun OptSwitch(title: String, sub: String, v: Boolean, on: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { on(!v) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Medium); Text(sub, color = Muted, fontSize = 12.sp) }
        Switch(v, on)
    }
}

@Composable
fun DlSettingsContent() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var ver by remember { mutableIntStateOf(0) }
    var ytver by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { ytver = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Web.version(ctx) } }
    key(ver) {
        Text("İndirme ayarları", fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        SetSwitch("Sadece Wi-Fi'de indir", "Mobil veride indirmeler bekletilir", DlPrefs.wifiOnly) { DlPrefs.wifiOnly = it; Dl.pump() }
        SetChoice("Aynı anda indirme", listOf("1", "2", "3", "4", "5", "6"), (DlPrefs.parallel - 1).coerceIn(0, 5)) { DlPrefs.parallel = it + 1; ver++; Dl.pump() }
        SetSwitch("Hızlı indirme", "YouTube dosyasını 16 parçaya bölüp aynı anda indirir", DlPrefs.fast) { DlPrefs.fast = it }
        SetSwitch("Akıllı kalite", "Wi-Fi'de seçtiğin kalite, mobil veride en fazla aşağıdaki kalite (izlerken de)", DlPrefs.smartQuality) { DlPrefs.smartQuality = it }
        SetChoice("Mobil veride en fazla", listOf("360p", "480p", "720p"), listOf(360, 480, 720).indexOf(DlPrefs.mobileHeight).coerceAtLeast(1)) { DlPrefs.mobileHeight = listOf(360, 480, 720)[it]; ver++ }
        SetChoice("Hız sınırı", DlPrefs.RATES, DlPrefs.rate) { DlPrefs.rate = it; ver++ }
        SetSwitch("Tekrar indirmeyi önle", "Daha önce indirilen video atlanır", DlPrefs.archive) { DlPrefs.archive = it }
        SetSwitch("Panodaki bağlantıyı algıla", "Bağlantı kopyalayıp Kadraj'ı açınca indirmeyi önerir", DlPrefs.clipboard) { DlPrefs.clipboard = it }
        SetSwitch("Video için her seferinde sor", "Kapalıysa varsayılan kalite ile hemen başlar", DlPrefs.askVideo) { DlPrefs.askVideo = it }
        SetSwitch("Ses için her seferinde sor", "Kapalıysa varsayılan biçim ile hemen başlar", DlPrefs.askAudio) { DlPrefs.askAudio = it }
        SetChoice("Varsayılan video kalitesi", DlPrefs.HEIGHTS.map { if (it >= 2160) "4K" else "${it}p" }, DlPrefs.HEIGHTS.indexOf(DlPrefs.height).coerceAtLeast(0)) { DlPrefs.height = DlPrefs.HEIGHTS[it]; ver++ }
        SetChoice("Varsayılan ses biçimi", listOf("M4A", "MP3"), if (DlPrefs.audioFmt == "mp3") 1 else 0) { DlPrefs.audioFmt = if (it == 1) "mp3" else "m4a"; ver++ }
        SetChoice("Varsayılan ses kalitesi", listOf("128 kbps", "192 kbps", "320 kbps"), listOf(128, 192, 320).indexOf(DlPrefs.kbps).coerceAtLeast(1)) { DlPrefs.kbps = listOf(128, 192, 320)[it]; ver++ }
        ListItem(headlineContent = { Text("İndirme motorunu güncelle") }, supportingContent = { Text("yt-dlp $ytver · siteler değişince bağlantılar açılmazsa dokun") },
            modifier = Modifier.clickable { toast(ctx, "Güncelleniyor…"); scope.launch(kotlinx.coroutines.Dispatchers.IO) { Web.maybeUpdate(ctx, force = true); Prefs.sp.edit().putLong("ytdlpUpd", 0).apply(); Web.maybeUpdate(ctx, force = true); ytver = Web.version(ctx) } },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent))
        ListItem(headlineContent = { Text("İndirme arşivini sıfırla") }, supportingContent = { Text("Daha önce indirilenler tekrar indirilebilir") },
            modifier = Modifier.clickable { Dl.archiveFile(ctx).delete(); toast(ctx, "Arşiv sıfırlandı") }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
    }
}

// ---------------- hesaplar ----------------
data class Site(val name: String, val login: String, val domains: List<String>)
val SITES = listOf(
    Site("YouTube", "https://accounts.google.com/ServiceLogin?service=youtube&continue=https://www.youtube.com/", listOf(".youtube.com", ".google.com")),
    Site("Instagram", "https://www.instagram.com/accounts/login/", listOf(".instagram.com")),
    Site("TikTok", "https://www.tiktok.com/login", listOf(".tiktok.com")),
    Site("Facebook", "https://m.facebook.com/login", listOf(".facebook.com")),
    Site("X (Twitter)", "https://x.com/i/flow/login", listOf(".x.com", ".twitter.com"))
)

object Cookies {
    /** WebView çerezlerini yt-dlp'nin okuyabildiği Netscape biçiminde kaydeder. */
    fun save(ctx: android.content.Context) {
        val cm = CookieManager.getInstance(); cm.flush()
        val sb = StringBuilder("# Netscape HTTP Cookie File\n")
        val exp = System.currentTimeMillis() / 1000 + 365L * 86400
        SITES.flatMap { it.domains }.distinct().forEach { d ->
            val host = "https://www" + d
            cm.getCookie(host)?.split(";")?.map { it.trim() }?.filter { '=' in it }?.forEach { c ->
                sb.append(d).append("\tTRUE\t/\tTRUE\t").append(exp).append('\t').append(c.substringBefore('=')).append('\t').append(c.substringAfter('=')).append('\n')
            }
        }
        Web.cookieFile(ctx).writeText(sb.toString())
    }
    fun loggedIn(s: Site): Boolean = s.domains.any { d -> CookieManager.getInstance().getCookie("https://www$d")?.let { c ->
        listOf("SAPISID", "sessionid", "sid_tt", "c_user", "auth_token", "LOGIN_INFO").any { it in c } } == true }
    fun logout(ctx: android.content.Context, s: Site) {
        val cm = CookieManager.getInstance()
        s.domains.forEach { d -> cm.getCookie("https://www$d")?.split(";")?.map { it.trim().substringBefore('=') }?.forEach { n ->
            cm.setCookie("https://www$d", "$n=; Max-Age=0; Domain=$d; Path=/") } }
        cm.flush(); save(ctx)
    }
}

@Composable
private fun AccountsContent() {
    val ctx = LocalContext.current
    var ver by remember { mutableIntStateOf(0) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { ver++ }
    Text("Hesaplar", fontSize = 21.sp, fontWeight = FontWeight.Bold)
    Text("Gizli hesaplar, yaş sınırlı videolar veya \"bot değilim\" doğrulaması için siteye giriş yap. Giriş sitenin kendi sayfasında yapılır; şifren Kadraj'a kaydedilmez, sadece oturum bilgisi telefonda saklanır.",
        color = Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
    key(ver) {
        SITES.forEach { s ->
            val ok = Cookies.loggedIn(s)
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(Surf2).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (ok) Icons.Filled.CheckCircle else Icons.Filled.AccountCircle, null, tint = if (ok) OK else Muted)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(s.name, fontWeight = FontWeight.Medium)
                    Text(if (ok) "Giriş yapıldı" else "Giriş yapılmadı", color = Muted, fontSize = 12.sp)
                }
                if (ok) TextButton(onClick = { Cookies.logout(ctx, s); ver++ }) { Text("Çıkış") }
                else Button(onClick = { ctx.startActivity(android.content.Intent(ctx, LoginActivity::class.java).putExtra("url", s.login).putExtra("name", s.name)) }) { Text("Giriş yap") }
            }
        }
    }
}

/** Sitenin kendi giriş sayfası (WebView). Kapatınca çerezler kaydedilir. */
class LoginActivity : ComponentActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra("url") ?: run { finish(); return }
        val name = intent.getStringExtra("name") ?: ""
        CookieManager.getInstance().setAcceptCookie(true)
        setContent {
            PerdeTheme {
                Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { finishLogin() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") }
                        Text("$name girişi", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Button(onClick = { finishLogin() }) { Text("Bitti") }
                    }
                    Text("Giriş yaptıktan sonra \"Bitti\"ye dokun.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
                    AndroidView(factory = { c ->
                        WebView(c).apply {
                            settings.javaScriptEnabled = true; settings.domStorageEnabled = true
                            // Google gömülü tarayıcıdan girişi engelleyebildiği için masaüstü tarayıcı kimliği
                            settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webViewClient = WebViewClient()
                            loadUrl(url)
                        }
                    }, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    private fun finishLogin() { Cookies.save(this); finish() }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { finishLogin() }
}

// ---------------- geçmiş ----------------
@Composable
fun DlHistoryScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    val list = Dl.history.filter { q.isBlank() || it.title.contains(q, true) }
    Page("İndirme geçmişi", back, actions = { if (Dl.history.isNotEmpty()) TextButton(onClick = { confirm = true }) { Text("Temizle") } }) {
        Column {
            OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text("Geçmişte ara") }, leadingIcon = { Icon(Icons.Filled.Search, null) },
                shape = CircleShape, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp))
            if (list.isEmpty()) Empty(Icons.Filled.History, "Henüz indirme geçmişi yok.")
            else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(list, key = { "${it.at}${it.key}" }) { h ->
                    val exists = h.file != null && File(h.file).exists()
                    val date = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT, TR).format(h.at)
                    ListItem(
                        headlineContent = { Text(h.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text("$date · ${Web.site(h.url)} · ${h.note}" + if (!exists && h.ok) " · dosya silinmiş" else "", color = if (h.ok) Muted else BAD, fontSize = 12.sp) },
                        leadingContent = { Icon(if (h.audio) Icons.Filled.MusicNote else Icons.Filled.Movie, null, tint = if (h.ok) Accent else BAD) },
                        trailingContent = {
                            if (exists) IconButton(onClick = { playKeys(ctx, listOf(h.file!!)) }) { Icon(Icons.Filled.PlayArrow, "Oynat", tint = Accent) }
                            else IconButton(onClick = { Dl.addSingles(ctx, listOf(Triple(h.url, h.title, null)), DlOpts.defaults(h.audio)); toast(ctx, "Tekrar indiriliyor") }) { Icon(Icons.Filled.Download, "Tekrar indir") }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                }
            }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Geçmiş temizlensin mi?") }, text = { Text("İndirilen dosyalar silinmez.") },
        confirmButton = { TextButton(onClick = { Dl.clearHistory(); confirm = false }) { Text("Temizle") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Vazgeç") } })
}
