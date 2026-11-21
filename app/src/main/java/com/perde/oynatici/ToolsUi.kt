@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.perde.oynatici

import android.net.Uri
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

@Composable
private fun HubCard(icon: ImageVector, title: String, sub: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surf)
        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Accent, AccentDeep))), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White)
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(sub, color = Muted, fontSize = 13.sp)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = Muted)
    }
}

// ---------------- Ağ sekmesi ----------------
@Composable
fun NetScreen(go: (Route) -> Unit) {
    val servers = remember { Smb.servers() }
    Page("Ağ", null) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HubCard(Icons.Filled.Link, "Ağ akışı (URL)", "İnternet adresinden video aç: MP4, MKV, HLS, DASH") { go(Route.Network) }
            HubCard(Icons.Filled.Lan, "Ağ klasörleri (SMB)", if (servers.isEmpty()) "Bilgisayardaki paylaşılan klasörden indirmeden izle" else "${servers.size} kayıtlı sunucu") { go(Route.SmbList) }
            HubCard(Icons.Filled.Wifi, "Wi-Fi aktarım", "Bilgisayardan telefona video gönder / telefondan indir") { go(Route.Transfer) }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(AccentDeep.copy(alpha = 0.5f)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Cast, null, tint = Accent)
                Text("Televizyona yansıtmak için videoyu açıp üst çubuktaki yayın simgesine dokun (Chromecast / Google TV).",
                    fontSize = 13.sp, modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}

@Composable
fun NetworkScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(AppState.sharedUrl ?: "") }
    LaunchedEffect(AppState.sharedUrl) { AppState.sharedUrl?.let { url = it; AppState.sharedUrl = null } }
    var hist by remember { mutableStateOf(Prefs.netHistory.lines().filter { it.isNotBlank() }) }
    var info by remember { mutableStateOf<Web.Stream?>(null) }
    var loading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var pickQuality by remember { mutableStateOf(false) }
    val clip = androidx.compose.ui.platform.LocalClipboardManager.current

    fun remember(u: String) { val h = (listOf(u) + hist.filter { it != u }).take(30); Prefs.netHistory = h.joinToString("\n"); hist = h }
    fun play(u: String) {
        val s = u.trim(); if (s.isEmpty()) return
        remember(s)
        PlayerActivity.openKeys(ctx, listOf(s), listOf(info?.takeIf { it.page == s }?.title ?: Uri.parse(s).lastPathSegment ?: s), 0)
    }
    // Sayfa bağlantısı yapıştırılınca otomatik incele
    LaunchedEffect(url) {
        val u = url.trim()
        info = null; err = null
        if (!Web.isPage(u)) { loading = false; return@LaunchedEffect }
        delay(500)
        loading = true
        try { info = Web.resolve(ctx, u) } catch (e: Exception) { err = Web.explain(e) }
        loading = false
    }

    Page("Ağ akışı ve indirme", back) {
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            item {
                Text("YouTube, Instagram, TikTok, X, Facebook, Vimeo… bağlantısı ya da doğrudan video adresi yapıştır.", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(url, { url = it }, label = { Text("Bağlantı") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), shape = RoundedCornerShape(16.dp),
                    trailingIcon = {
                        if (url.isEmpty()) IconButton(onClick = { clip.getText()?.text?.let { t -> Web.extractUrl(t)?.let { url = it } ?: run { url = t } } }) { Icon(Icons.Filled.ContentPaste, "Yapıştır") }
                        else IconButton(onClick = { url = "" }) { Icon(Icons.Filled.Close, "Temizle") }
                    })
                Spacer(Modifier.height(12.dp))
                when {
                    loading -> Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surf).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(28.dp), color = Accent, strokeWidth = 3.dp)
                        Text("${Web.site(url.trim())} bağlantısı inceleniyor…", Modifier.padding(start = 14.dp))
                    }
                    err != null -> Text("⚠ $err", color = Color(0xFFFF8A80), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Surf).padding(14.dp))
                    info != null -> WebCard(info!!)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { play(url) }, enabled = url.isNotBlank() && Web.isHttp(url.trim()) && !loading, modifier = Modifier.weight(1f).height(52.dp)) {
                        Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Oynat")
                    }
                    FilledTonalButton(onClick = { pickQuality = true }, enabled = url.isNotBlank() && Web.isHttp(url.trim()) && !loading, modifier = Modifier.weight(1f).height(52.dp)) {
                        Icon(Icons.Filled.Download, null); Spacer(Modifier.width(6.dp)); Text("İndir")
                    }
                }
            }
            if (Downloads.items.isNotEmpty()) {
                item { Text("İndirmeler", color = Accent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp, bottom = 6.dp)) }
                items(Downloads.items, key = { it.id }) { d -> DownloadRow(d) }
            }
            if (hist.isNotEmpty()) {
                item { Text("Geçmiş", color = Accent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp, bottom = 6.dp)) }
                items(hist, key = { "h$it" }) { h ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { url = h }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.History, null, tint = Muted)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(Web.site(h), fontSize = 12.sp, color = Accent)
                            Text(h, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                        }
                        IconButton(onClick = { hist = hist - h; Prefs.netHistory = hist.joinToString("\n") }) { Icon(Icons.Filled.Close, "Sil") }
                    }
                }
            }
            item {
                Text("Not: Sadece izin verilen veya kendi içeriklerini indir; telif hakkı olan içerikleri paylaşma. Gizli hesaplar ve yaş sınırlı videolar giriş gerektirdiği için açılamayabilir.",
                    color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 20.dp))
            }
        }
    }
    if (pickQuality) ModalBottomSheet(onDismissRequest = { pickQuality = false }, containerColor = Surf) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            DownloadQualityContent { q ->
                val u = url.trim(); remember(u)
                Downloads.start(ctx, u, info?.title ?: Web.site(u), info?.thumb, q)
                pickQuality = false
                toast(ctx, "İndirme başladı")
            }
        }
    }
}

@Composable
private fun WebCard(s: Web.Stream) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surf).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(140.dp).aspectRatio(16 / 9f).clip(RoundedCornerShape(12.dp)).background(Surf2)) {
            if (s.thumb != null) coil3.compose.AsyncImage(s.thumb, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            if (s.durationSec > 0) Text(fmtTime(s.durationSec * 1000L), color = Color.White, fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xB3000000)).padding(horizontal = 4.dp))
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(Web.site(s.page), color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(s.title, maxLines = 3, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            listOfNotNull(s.uploader, if (s.height > 0) "${s.height}p" else null).joinToString(" · ").takeIf { it.isNotEmpty() }?.let { Text(it, color = Muted, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun DownloadRow(d: DL) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(14.dp)).background(Surf)
        .clickable(enabled = d.state == 2 && d.file != null) { d.file?.let { f -> playKeys(ctx, listOf(f)) } }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(when (d.state) { 2 -> Icons.Filled.CheckCircle; 3 -> Icons.Filled.ErrorOutline; 4 -> Icons.Filled.Cancel; else -> Icons.Filled.Downloading },
            null, tint = when (d.state) { 2 -> Color(0xFF3DDC97); 3 -> Color(0xFFFF6B6B); 4 -> Muted; else -> Accent })
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(d.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text("${Downloads.QUALITIES[d.quality]} · ${d.info.ifEmpty { "Sırada" }}", color = if (d.state == 3) Color(0xFFFF8A80) else Muted, fontSize = 12.sp, maxLines = 2)
            if (d.state <= 1) LinearProgressIndicator(progress = { d.progress }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clip(CircleShape), color = Accent, trackColor = Surf2)
        }
        when (d.state) {
            0, 1 -> IconButton(onClick = { Downloads.cancel(d) }) { Icon(Icons.Filled.Close, "İptal") }
            2 -> Icon(Icons.Filled.PlayArrow, "Oynat", tint = Accent)
            else -> IconButton(onClick = { Downloads.items.remove(d) }) { Icon(Icons.Filled.Delete, "Kaldır", tint = Muted) }
        }
    }
}

@Composable
fun SmbListScreen(go: (Route) -> Unit, back: () -> Unit) {
    var list by remember { mutableStateOf(Smb.servers()) }
    var edit by remember { mutableStateOf<SmbServer?>(null) }
    var adding by remember { mutableStateOf(false) }
    Page("Ağ klasörleri (SMB)", back, fab = {
        ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Sunucu ekle") }, containerColor = Accent, contentColor = Color(0xFF14100E))
    }) {
        if (list.isEmpty()) Empty(Icons.Filled.Lan, "Bilgisayarındaki veya modemindeki paylaşılan klasörü ekle; videoları indirmeden izle.\n\nÖrnek: Sunucu 192.168.1.20, Paylaşım adı Filmler")
        else LazyColumn {
            items(list) { s ->
                ListItem(headlineContent = { Text(s.title) }, supportingContent = { Text("\\\\${s.host}\\${s.share}" + if (s.user.isNotBlank()) " · ${s.user}" else " · misafir") },
                    leadingContent = { Icon(Icons.Filled.Dns, null, tint = Accent) },
                    trailingContent = { IconButton(onClick = { edit = s }) { Icon(Icons.Filled.Edit, "Düzenle") } },
                    modifier = Modifier.clickable { go(Route.SmbBrowse(s, s.basePath)) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
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
    var test by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (init == null) "Sunucu ekle" else "Sunucuyu düzenle") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Görünen ad (isteğe bağlı)") }, singleLine = true)
                OutlinedTextField(host, { host = it.trim() }, label = { Text("Sunucu (IP veya ad)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                OutlinedTextField(share, { share = it.trim().trim('\\', '/') }, label = { Text("Paylaşım adı") }, singleLine = true)
                OutlinedTextField(user, { user = it }, label = { Text("Kullanıcı (boş = misafir)") }, singleLine = true)
                OutlinedTextField(pass, { pass = it }, label = { Text("Şifre") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(domain, { domain = it }, label = { Text("Etki alanı (genelde boş)") }, singleLine = true)
                Text("İpucu: Sunucu alanına \\\\192.168.1.20\\Filmler gibi tam adres de yazabilirsin. Windows'ta Microsoft hesabıyla giriş yapıyorsan kullanıcı adı e-posta adresin, şifre hesap şifrendir.", fontSize = 12.sp, color = Muted)
                test?.let { t -> Text(if (t == "…") "Bağlanılıyor…" else t, fontSize = 13.sp, color = if (t.startsWith("✓")) Color(0xFF3DDC97) else if (t == "…") Muted else Color(0xFFFF8A80)) }
            }
        },
        confirmButton = { TextButton(enabled = host.isNotBlank(), onClick = { save(Smb.normalize(SmbServer(name, host, share, user, pass, domain))) }) { Text("Kaydet") } },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Sil", color = Color(0xFFFF6B6B)) }
                TextButton(enabled = host.isNotBlank() && test != "…", onClick = {
                    test = "…"
                    scope.launch { test = withContext(Dispatchers.IO) { Smb.test(Smb.normalize(SmbServer(name, host, share, user, pass, domain))) } }
                }) { Text("Test et") }
                TextButton(onClick = dismiss) { Text("Vazgeç") }
            }
        })
}

@Composable
fun SmbBrowseScreen(s: SmbServer, path: String, go: (Route) -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current
    var entries by remember(s, path) { mutableStateOf<List<SmbEntry>?>(null) }
    var err by remember(s, path) { mutableStateOf<String?>(null) }
    LaunchedEffect(s, path) {
        try { entries = withContext(Dispatchers.IO) { Smb.list(s, path) } }
        catch (e: Throwable) { Smb.drop(s); err = Smb.explain(e) }
    }
    Page(if (path.isEmpty()) s.title else path.substringAfterLast('\\'), back) {
        val e = entries
        when {
            err != null -> Empty(Icons.Filled.ErrorOutline, err!!)
            e == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) }
            else -> {
                val shown = e.filter { it.isDir || isVideoName(it.name) || isAudioName(it.name) }
                val media = shown.filter { !it.isDir }
                if (shown.isEmpty()) Empty(Icons.Filled.FolderOff, "Bu klasörde video yok.")
                else LazyColumn {
                    items(shown) { x ->
                        val child = if (path.isEmpty()) x.name else "$path\\${x.name}"
                        ListItem(headlineContent = { Text(x.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            supportingContent = if (x.isDir) null else ({ Text(fmtSize(x.size)) }),
                            leadingContent = { Icon(if (x.isDir) Icons.Filled.Folder else if (isAudioName(x.name)) Icons.Filled.MusicNote else Icons.Filled.Movie, null, tint = if (x.isDir) Accent else Color.White) },
                            modifier = Modifier.clickable {
                                if (x.isDir) go(Route.SmbBrowse(s, child))
                                else {
                                    val keys = media.map { Smb.uriFor(s, if (path.isEmpty()) it.name else "$path\\${it.name}") }
                                    PlayerActivity.openKeys(ctx, keys, media.map { it.name }, media.indexOf(x))
                                }
                            }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                    }
                }
            }
        }
    }
}

@Composable
fun TransferScreen(back: () -> Unit) {
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
    Page("Wi-Fi aktarım", back) {
        Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(110.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Accent.copy(alpha = 0.5f), Color.Transparent))), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Wifi, null, tint = Accent, modifier = Modifier.size(64.dp))
            }
            Spacer(Modifier.height(12.dp))
            if (!ok || ip == null) Text("Sunucu başlatılamadı. Telefonun bir Wi-Fi ağına bağlı olduğundan emin ol.", textAlign = TextAlign.Center)
            else {
                Text("Bilgisayarda veya başka bir telefonda tarayıcıyı aç ve bu adrese git:", textAlign = TextAlign.Center, color = Muted)
                Spacer(Modifier.height(12.dp))
                Text("http://$ip:${LocalServer.PORT}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Accent,
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Surf2).padding(horizontal = 18.dp, vertical = 14.dp))
                Spacer(Modifier.height(16.dp))
                Text("• Açılan sayfadan videoları telefona yükleyebilir (Filmler/Kadraj klasörüne kaydedilir) ya da telefondaki videoları indirebilirsin.\n• İki cihaz aynı Wi-Fi ağında olmalı.\n• Bu ekrandan çıkınca aktarım kapanır.",
                    fontSize = 14.sp, color = Muted)
            }
            if (uploaded.isNotEmpty()) {
                Text("Gelen dosyalar", color = Accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp).fillMaxWidth())
                uploaded.forEach { Text("✓ $it", modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) }
            }
        }
    }
}

// ---------------- Daha sekmesi (araçlar + ayarlar) ----------------
@Composable
fun MoreScreen(vm: LibVM, go: (Route) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var ver by remember { mutableIntStateOf(0) }
    var checking by remember { mutableStateOf(false) }
    Page("Daha fazla", null) {
        key(ver) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickTile(Icons.Filled.Lock, "Gizli klasör", Modifier.weight(1f)) { go(Route.Private) }
                        QuickTile(Icons.Filled.DeleteSweep, "Çöp kutusu", Modifier.weight(1f)) { go(Route.TrashR) }
                        QuickTile(Icons.Filled.BarChart, "İstatistikler", Modifier.weight(1f)) { go(Route.StatsR) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickTile(Icons.Filled.Favorite, "Favoriler", Modifier.weight(1f)) { go(Route.Favorites) }
                        QuickTile(Icons.AutoMirrored.Filled.PlaylistPlay, "Listeler", Modifier.weight(1f)) { go(Route.Playlists) }
                        QuickTile(Icons.Filled.VisibilityOff, "Gizlenen klasörler", Modifier.weight(1f)) { go(Route.HiddenFolders) }
                    }
                    HubCard(Icons.Filled.SystemUpdate, "Güncellemeleri denetle", if (checking) "Denetleniyor…" else "Sürüm ${Updater.current(ctx)}") {
                        if (checking) return@HubCard
                        checking = true
                        scope.launch {
                            val r = Updater.check(); checking = false
                            when {
                                r == null -> toast(ctx, "Sunucuya ulaşılamadı")
                                Updater.newer(r.version, Updater.current(ctx)) -> AppState.update = r
                                else -> toast(ctx, "En güncel sürümü kullanıyorsun")
                            }
                        }
                    }
                }

                SetHeader("Görünüm")
                Text("Vurgu rengi", modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ThemeState.ACCENTS.forEachIndexed { i, (c, n) ->
                        Box(Modifier.size(40.dp).clip(CircleShape).background(Color(c))
                            .border(3.dp, if (ThemeState.accentIdx == i) Color.White else Color.Transparent, CircleShape)
                            .clickable { ThemeState.accentIdx = i; Prefs.accent = i; PerdeWidget.update(ctx) }, contentAlignment = Alignment.Center) {
                            if (ThemeState.accentIdx == i) Icon(Icons.Filled.Check, n, tint = Color(0xFF14100E))
                        }
                    }
                }
                SetSwitch("Tam siyah tema (OLED)", "OLED ekranlarda pil tasarrufu sağlar", Prefs.amoled) { Prefs.amoled = it; ThemeState.amoled = it }
                SetSwitch("Dokunma titreşimi", "Butonlara basınca hafif titreşim", Prefs.haptics) { Prefs.haptics = it }

                SetHeader("Oynatıcı")
                SetChoice("Kaldığı yer", listOf("Otomatik devam et", "Her seferinde sor", "Her zaman baştan başla"), Prefs.resumeMode) { Prefs.resumeMode = it; ver++ }
                SetChoice("Video bitince", listOf("5 sn geri sayımla sonrakine geç", "Hemen sonrakine geç", "Dur"), Prefs.autoNext) { Prefs.autoNext = it; ver++ }
                SetSwitch("Arka planda oynat", "Ekran kapanınca veya başka uygulamaya geçince ses devam eder", Prefs.bgPlay) { Prefs.bgPlay = it }
                SetSwitch("Otomatik küçük pencere", "Ana ekrana çıkınca video küçük pencerede sürer", Prefs.autoPip) { Prefs.autoPip = it }
                SetSwitch("Gece modu", "Yüksek sesleri kısar, konuşmaları yükseltir", Prefs.night) { Engine.setNight(it) }
                SetSwitch("Ses yükseltme (%200)", "Sağ tarafta yukarı kaydırınca ses %100'ün üstüne çıkabilir", Prefs.boost) { Prefs.boost = it }
                SetSwitch("Sarma önizlemesi", "Sararken o anın küçük resmini göster", Prefs.seekThumbs) { Prefs.seekThumbs = it }
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
                OnlineSubSettings()

                SetHeader("Kütüphane")
                SetSwitch("Silinenleri çöp kutusuna taşı", "30 gün içinde geri alabilirsin", Prefs.useTrash) { Prefs.useTrash = it }
                SetSwitch("Izgara görünümü", null, Prefs.grid) { Prefs.grid = it }

                SetHeader("Hakkında")
                ListItem(headlineContent = { Text("Kadraj ${Updater.current(ctx)}") }, supportingContent = { Text("Kişisel video ve müzik oynatıcı · Media3 (ExoPlayer) + FFmpeg") },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                ListItem(headlineContent = { Text("İzleme geçmişini temizle") }, supportingContent = { Text("Kaldığı yer bilgileri ve ilerleme çubukları sıfırlanır") },
                    modifier = Modifier.clickable {
                        val e = Prefs.sp.edit()
                        Prefs.sp.all.keys.filter { it.startsWith("p:") || it.startsWith("d:") || it.startsWith("t:") }.forEach { e.remove(it) }
                        e.remove("last").apply(); vm.bump()
                        toast(ctx, "İzleme geçmişi temizlendi")
                    }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            }
        }
    }
}

@Composable
private fun QuickTile(icon: ImageVector, label: String, m: Modifier, onClick: () -> Unit) {
    Column(m.clip(RoundedCornerShape(18.dp)).background(Surf).border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(18.dp))
        .clickable(onClick = onClick).padding(vertical = 16.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = Accent, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
private fun OnlineSubSettings() {
    var open by remember { mutableStateOf(false) }
    ListItem(headlineContent = { Text("Online altyazı (OpenSubtitles)") },
        supportingContent = { Text(if (Prefs.osKey.isBlank()) "Ayarlanmadı — API anahtarı gerekli" else "Ayarlı · diller: ${Prefs.osLangs}") },
        modifier = Modifier.clickable { open = true }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
    if (open) {
        var key by remember { mutableStateOf(Prefs.osKey) }
        var user by remember { mutableStateOf(Prefs.osUser) }
        var pass by remember { mutableStateOf(Prefs.osPass) }
        var langs by remember { mutableStateOf(Prefs.osLangs) }
        AlertDialog(onDismissRequest = { open = false }, title = { Text("OpenSubtitles") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("opensubtitles.com'da ücretsiz hesap aç, \"API Consumers\" sayfasından bir API anahtarı oluştur. İndirme için kullanıcı adı ve şifre de gerekebilir.", fontSize = 13.sp, color = Muted)
                    OutlinedTextField(key, { key = it.trim() }, label = { Text("API anahtarı") }, singleLine = true)
                    OutlinedTextField(user, { user = it.trim() }, label = { Text("Kullanıcı adı") }, singleLine = true)
                    OutlinedTextField(pass, { pass = it }, label = { Text("Şifre") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                    OutlinedTextField(langs, { langs = it.replace(" ", "") }, label = { Text("Diller (ör. tr,en)") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { Prefs.osKey = key; Prefs.osUser = user; Prefs.osPass = pass; Prefs.osLangs = langs.ifBlank { "tr,en" }; open = false }) { Text("Kaydet") } },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Vazgeç") } })
    }
}

@Composable
fun SetHeader(t: String) = Text(t, color = Accent, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(start = 16.dp, top = 22.dp, bottom = 4.dp))

@Composable
fun SetSwitch(title: String, sub: String?, value: Boolean, onChange: (Boolean) -> Unit) {
    var v by remember { mutableStateOf(value) }
    ListItem(headlineContent = { Text(title) }, supportingContent = sub?.let { { Text(it) } },
        trailingContent = { Switch(v, { v = it; onChange(it) }) },
        modifier = Modifier.clickable { v = !v; onChange(v) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
}

@Composable
fun SetChoice(title: String, options: List<String>, selected: Int, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ListItem(headlineContent = { Text(title) }, supportingContent = { Text(options.getOrElse(selected) { "" }, color = Accent) },
        modifier = Modifier.clickable { open = true }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text(title) },
        text = { Column { options.forEachIndexed { i, o -> Choice(o, i == selected) { open = false; onChange(i) } } } },
        confirmButton = { TextButton(onClick = { open = false }) { Text("Kapat") } })
}

// ---------------- gizlenen klasörler ----------------
@Composable
fun HiddenFoldersScreen(vm: LibVM, back: () -> Unit) {
    var hidden by remember { mutableStateOf(Prefs.set(Library.HIDDEN).toList().sorted()) }
    Page("Gizlenen klasörler", back) {
        if (hidden.isEmpty()) Empty(Icons.Filled.Visibility, "Gizlenmiş klasör yok.\nBir klasörün menüsünden \"Listeden gizle\" diyebilirsin.")
        else LazyColumn {
            items(hidden) { p ->
                ListItem(headlineContent = { Text(File(p).name) }, supportingContent = { Text(p, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { Icon(Icons.Filled.FolderOff, null, tint = Muted) },
                    trailingContent = { TextButton(onClick = { Prefs.toggleIn(Library.HIDDEN, p); hidden = Prefs.set(Library.HIDDEN).toList().sorted(); vm.bump() }) { Text("Göster") } },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            }
        }
    }
}

// ---------------- çöp kutusu ----------------
@Composable
fun TrashScreen(vm: LibVM, back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<TrashItem>?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }
    fun load() { scope.launch { list = withContext(Dispatchers.IO) { Trash.list(ctx) } } }
    LaunchedEffect(Unit) { load() }
    Page("Çöp kutusu", back, actions = {
        if (!list.isNullOrEmpty()) TextButton(onClick = { confirmEmpty = true }) { Text("Boşalt", color = Color(0xFFFF6B6B)) }
    }) {
        val l = list
        when {
            l == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) }
            l.isEmpty() -> Empty(Icons.Filled.DeleteOutline, "Çöp kutusu boş.\nSilinen videolar 30 gün burada saklanır.")
            else -> LazyColumn {
                item { Text("Toplam ${fmtSize(l.sumOf { it.file.length() })}", color = Muted, modifier = Modifier.padding(16.dp)) }
                items(l, key = { it.file.name }) { t ->
                    ListItem(headlineContent = { Text(File(t.original).name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text("${fmtSize(t.file.length())} · ${t.daysLeft} gün sonra silinecek") },
                        leadingContent = { Icon(if (isAudioName(t.original)) Icons.Filled.MusicNote else Icons.Filled.Movie, null, tint = Muted) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { scope.launch { val ok = withContext(Dispatchers.IO) { Trash.restore(ctx, t) }; toast(ctx, if (ok) "Geri yüklendi" else "Geri yüklenemedi"); load(); vm.reload() } }) { Icon(Icons.Filled.RestoreFromTrash, "Geri yükle", tint = Accent) }
                                IconButton(onClick = { Trash.deleteForever(t); load() }) { Icon(Icons.Filled.DeleteForever, "Kalıcı sil", tint = Color(0xFFFF6B6B)) }
                            }
                        }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                }
            }
        }
    }
    if (confirmEmpty) AlertDialog(onDismissRequest = { confirmEmpty = false }, title = { Text("Çöp kutusu boşaltılsın mı?") },
        text = { Text("İçindeki tüm dosyalar kalıcı olarak silinir.") },
        confirmButton = { TextButton(onClick = { list?.forEach { Trash.deleteForever(it) }; confirmEmpty = false; load() }) { Text("Boşalt", color = Color(0xFFFF6B6B)) } },
        dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Vazgeç") } })
}

// ---------------- istatistikler ----------------
@Composable
fun StatsScreen(vm: LibVM, back: () -> Unit) {
    val ctx = LocalContext.current
    var ver by remember { mutableIntStateOf(0) }
    val days = remember(ver) { Stats.flush(); Stats.last7() }
    val top = remember(ver) { Stats.top(10) }
    val total = remember(ver) { Stats.total() }
    val week = days.sumOf { it.second }
    Page("İstatistikler", back, actions = { TextButton(onClick = { Stats.clear(); ver++ }) { Text("Sıfırla") } }) {
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatBox("Bugün", fmtDur(days.last().second), Modifier.weight(1f))
                    StatBox("Bu hafta", fmtDur(week), Modifier.weight(1f))
                    StatBox("Toplam", fmtDur(total), Modifier.weight(1f))
                }
            }
            item {
                Text("Son 7 gün", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp, bottom = 10.dp))
                val max = (days.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(60_000L)
                val accent = Accent
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surf).padding(16.dp)) {
                    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                        val n = days.size; val gap = 14.dp.toPx(); val bw = (size.width - gap * (n - 1)) / n
                        days.forEachIndexed { i, (_, ms) ->
                            val h = (size.height * ms / max).coerceAtLeast(4.dp.toPx())
                            drawRoundRect(if (i == n - 1) accent else accent.copy(alpha = 0.45f), Offset(i * (bw + gap), size.height - h), Size(bw, h), CornerRadius(8.dp.toPx()))
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) { days.forEach { (d, _) -> Text(d, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, color = Muted) } }
                }
            }
            item {
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatBox("Video", "${vm.videos.size}", Modifier.weight(1f))
                    StatBox("Şarkı", "${vm.songs.size}", Modifier.weight(1f))
                    StatBox("Video boyutu", fmtSize(vm.videos.sumOf { it.size }), Modifier.weight(1f))
                }
            }
            item { Text("En çok izlenenler", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp)) }
            if (top.isEmpty()) item { Text("Henüz izleme kaydı yok.", color = Muted) }
            itemsIndexed(top) { i, (k, ms) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", color = Accent, fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.width(32.dp))
                    Text(Library.titleOf(ctx, k), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(fmtDur(ms), color = Muted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, m: Modifier) {
    Column(m.clip(RoundedCornerShape(18.dp)).background(Brush.verticalGradient(listOf(AccentDeep, Surf))).padding(14.dp)) {
        Text(value, fontWeight = FontWeight.Black, fontSize = 18.sp, maxLines = 1)
        Text(label, color = Muted, fontSize = 12.sp)
    }
}

// ---------------- gizli klasör (PIN + parmak izi) ----------------
private fun hashPin(p: String) = MessageDigest.getInstance("SHA-256").digest("perde:$p".toByteArray()).joinToString("") { "%02x".format(it) }

@Composable
fun PrivateScreen(vm: LibVM, back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var unlocked by remember { mutableStateOf(false) }
    var hasPin by remember { mutableStateOf(Prefs.pinHash.isNotEmpty()) }
    var changing by remember { mutableStateOf(false) }
    var list by remember { mutableStateOf<List<Video>?>(null) }
    var sel by remember { mutableStateOf<Video?>(null) }
    fun load() { scope.launch { list = withContext(Dispatchers.IO) { Library.privateList(ctx) } } }
    val bioOk = remember { BiometricManager.from(ctx).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS }
    fun bio() {
        val act = ctx.findActivity() ?: return
        val prompt = BiometricPrompt(act, ContextCompat.getMainExecutor(ctx), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { unlocked = true; load() }
        })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Gizli klasör").setSubtitle("Parmak izinle aç")
            .setNegativeButtonText("PIN kullan").setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK).build())
    }
    LaunchedEffect(Unit) { if (hasPin && Prefs.bio && bioOk) bio() }

    Page("Gizli klasör", back, actions = {
        if (unlocked) {
            IconButton(onClick = { changing = true }) { Icon(Icons.Filled.Password, "PIN değiştir") }
            IconButton(onClick = { unlocked = false }) { Icon(Icons.Filled.Lock, "Kilitle") }
        }
    }) {
        when {
            !hasPin || changing -> PinSetup { p -> Prefs.pinHash = hashPin(p); hasPin = true; changing = false; unlocked = true; load() }
            !unlocked -> PinEntry(bioOk && Prefs.bio, { bio() }) { p -> if (hashPin(p) == Prefs.pinHash) { unlocked = true; load(); true } else false }
            else -> {
                val l = list
                Column {
                    if (bioOk) Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Fingerprint, null, tint = Accent)
                        Text("Parmak iziyle aç", Modifier.weight(1f).padding(start = 10.dp))
                        var b by remember { mutableStateOf(Prefs.bio) }
                        Switch(b, { b = it; Prefs.bio = it })
                    }
                    Text("Bu dosyalar galeride görünmez. Uygulamayı kaldırırsan gizli dosyalar da silinir; önce geri taşı.",
                        fontSize = 13.sp, color = Muted, modifier = Modifier.padding(16.dp))
                    if (l == null) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally), color = Accent)
                    else if (l.isEmpty()) Empty(Icons.Filled.VisibilityOff, "Gizli klasör boş.\nBir videonun menüsünden \"Gizli klasöre taşı\" de.")
                    else LazyColumn {
                        itemsIndexed(l) { i, v -> VideoRow(v, false, false, { PlayerActivity.open(ctx, l, i) }, { sel = v }, { sel = v }) }
                    }
                }
            }
        }
    }
    sel?.let { v ->
        AlertDialog(onDismissRequest = { sel = null }, title = { Text(v.title, maxLines = 2) },
            text = { Text("Bu dosyayla ne yapmak istiyorsun?") },
            confirmButton = { TextButton(onClick = {
                sel = null
                scope.launch { val ok = withContext(Dispatchers.IO) { Library.restoreFromPrivate(ctx, File(v.path)) }; toast(ctx, if (ok) "Filmler/Kadraj klasörüne taşındı" else "Taşınamadı"); load(); vm.reload() }
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
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), shape = RoundedCornerShape(14.dp))

@Composable
private fun PinSetup(done: (String) -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Filled.Lock, null, tint = Accent, modifier = Modifier.size(60.dp))
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
private fun PinEntry(bio: Boolean, onBio: () -> Unit, check: (String) -> Boolean) {
    var p by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Filled.Lock, null, tint = Accent, modifier = Modifier.size(60.dp))
        Text("PIN'i gir", modifier = Modifier.padding(vertical = 12.dp))
        PinField(p, "PIN") { p = it; wrong = false }
        if (wrong) Text("Yanlış PIN", color = Color(0xFFFF6B6B), fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        Button(onClick = { if (!check(p)) { wrong = true; p = "" } }, enabled = p.length >= 4) { Text("Aç") }
        if (bio) TextButton(onClick = onBio, modifier = Modifier.padding(top = 8.dp)) { Icon(Icons.Filled.Fingerprint, null); Spacer(Modifier.width(6.dp)); Text("Parmak izi kullan") }
    }
}
