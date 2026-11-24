@file:OptIn(ExperimentalMaterial3Api::class)

package com.perde.oynatici

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ============================ İndir › Takip ============================
// Liste/kanal takibe alınır; yeni şarkı ya da video gelince bildirim gelir, "Ekle ve indir" ile Kadraj listesine eklenip iner.

@Composable
fun FollowHeader() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = { adding = true }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Liste takip et", maxLines = 1)
        }
        OutlinedButton(enabled = !checking && Dl.follows.isNotEmpty(), onClick = {
            checking = true
            scope.launch {
                val n = withContext(Dispatchers.IO) { Dl.checkFollows(ctx, false) }
                checking = false; toast(ctx, if (n > 0) "$n yeni öğe bulundu" else "Hepsi güncel, yeni öğe yok")
            }
        }, modifier = Modifier.weight(1f)) {
            if (checking) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Filled.Refresh, null)
            Spacer(Modifier.width(6.dp)); Text("Hepsini kontrol et", maxLines = 1)
        }
    }
    Text("Takip edilen listeler 6 saatte bir kendiliğinden de kontrol edilir.", color = Muted, fontSize = 12.sp)
    if (adding) AddFollowSheet { adding = false }
}

@Composable
fun FollowEmpty() {
    Text("Henüz takip ettiğin liste yok.\nYouTube, YouTube Music, Spotify ya da SoundCloud listesi veya kanal bağlantısını ekle. " +
        "Yeni şarkı ya da video gelince haber veririm; \"Ekle ve indir\" ile listene eklenip iner.",
        color = Muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 18.dp))
}

@Composable
private fun AddFollowSheet(dismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clip = LocalClipboardManager.current
    var url by remember { mutableStateOf("") }
    var audio by remember { mutableStateOf(true) }
    var target by remember { mutableStateOf<Long?>(null) }      // null: takip edilen listenin adıyla yeni Kadraj listesi
    var all by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val lists = remember(audio) { if (audio) Playlists.music() else Playlists.videos() }
    ModalBottomSheet(onDismissRequest = { if (!busy) dismiss() }, containerColor = Surf) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Text("Liste takip et", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            OutlinedTextField(url, { url = it; err = null }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                placeholder = { Text("Liste ya da kanal bağlantısı") },
                trailingIcon = { IconButton(onClick = { clip.getText()?.text?.let { url = it.trim(); err = null } }) { Icon(Icons.Filled.ContentPaste, "Yapıştır") } })
            Text("Ne olarak inecek?", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(audio, { audio = true; target = null }, { Text("Şarkı (ses)") })
                FilterChip(!audio, { audio = false; target = null }, { Text("Video") })
            }
            Text("Yeniler hangi listeye eklensin?", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(target == null, { target = null }, { Text("Yeni liste (aynı adla)") })
                lists.forEach { p -> FilterChip(target == p.id, { target = p.id }, { Text(p.name) }) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp).clickable { all = !all }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Şu anki öğeleri de indir", fontWeight = FontWeight.Medium)
                    Text("Kapalıysa yalnızca bundan sonra eklenenler iner", color = Muted, fontSize = 12.sp)
                }
                Switch(all, { all = it })
            }
            err?.let { Text(it, color = Color(0xFFFF6B6B), fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)) }
            Button(enabled = !busy && url.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 16.dp), onClick = {
                busy = true; err = null
                scope.launch {
                    val e = withContext(Dispatchers.IO) { Dl.addFollow(ctx, url, audio, target, all) }
                    busy = false
                    if (e == null) { toast(ctx, if (all) "Takip ediliyor, şu anki öğeler de iniyor" else "Takip ediliyor"); dismiss() } else err = e
                }
            }) {
                if (busy) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White); Spacer(Modifier.width(8.dp)); Text("Liste okunuyor…") }
                else Text("Takip et")
            }
        }
    }
}

@Composable
fun FollowCard(f: Follow) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf(false) }
    var confirmDel by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    val what = if (f.opts.audio) "şarkı" else "video"
    val listName = f.playlistId?.let { Playlists.get(it)?.name }
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(18.dp)).background(Surf).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(if (f.opts.audio) Icons.Filled.QueueMusic else Icons.Filled.VideoLibrary, null, tint = Accent)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(f.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(Web.site(f.url), "${f.known.size} $what", listName?.let { "→ $it" }).joinToString(" · "),
                    color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (f.newIds.isEmpty()) "Güncel" + (if (f.checkedAt > 0) " · son kontrol ${fmtAgo(f.checkedAt)}" else "") else "${f.newIds.size} yeni $what",
                    color = if (f.newIds.isEmpty()) Muted else Accent, fontWeight = if (f.newIds.isEmpty()) FontWeight.Normal else FontWeight.Bold, fontSize = 12.sp)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Diğer", tint = Muted) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("Kontrol et") }, {
                        menu = false; checking = true
                        scope.launch {
                            val n = withContext(Dispatchers.IO) { Dl.checkFollow(ctx, f, false) }
                            checking = false
                            toast(ctx, when { n < 0 -> "Liste açılamadı"; n == 0 -> "Güncel, yeni $what yok"; else -> "$n yeni $what" })
                        }
                    }, leadingIcon = { Icon(Icons.Filled.Refresh, null) })
                    DropdownMenuItem({ Text("Eklendiği listeyi değiştir") }, { menu = false; pick = true }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) })
                    DropdownMenuItem({ Text("Takibi sil") }, { menu = false; confirmDel = true }, leadingIcon = { Icon(Icons.Filled.Delete, null) })
                }
            }
        }
        if (checking) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp).clip(CircleShape), color = Accent, trackColor = Surf2)
        if (f.newIds.isNotEmpty()) Button(onClick = { scope.launch { Dl.downloadNew(ctx, f) }; toast(ctx, "${f.newIds.size} yeni $what listeye ekleniyor ve iniyor") },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Icon(Icons.Filled.Download, null); Spacer(Modifier.width(6.dp)); Text("Ekle ve indir (${f.newIds.size})")
        }
    }
    if (pick) {
        val lists = remember { if (f.opts.audio) Playlists.music() else Playlists.videos() }
        AlertDialog(onDismissRequest = { pick = false }, title = { Text("Yeniler hangi listeye eklensin?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (lists.isEmpty()) Text("Uygun liste yok, yeni bir tane oluşturabilirsin.", color = Muted)
                    lists.forEach { p ->
                        Text(p.name, fontWeight = if (p.id == f.playlistId) FontWeight.Bold else FontWeight.Normal, color = if (p.id == f.playlistId) Accent else Ink,
                            modifier = Modifier.fillMaxWidth().clickable { Dl.setFollowPlaylist(f, p.id); pick = false; toast(ctx, "Yeniler \"${p.name}\" listesine eklenecek") }
                                .padding(vertical = 12.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = {
                val p = Playlists.create(f.title, music = f.opts.audio); Dl.setFollowPlaylist(f, p.id); pick = false; toast(ctx, "\"${p.name}\" listesi oluşturuldu")
            }) { Text("Yeni liste") } },
            dismissButton = { TextButton(onClick = { pick = false }) { Text("Vazgeç") } })
    }
    if (confirmDel) AlertDialog(onDismissRequest = { confirmDel = false }, title = { Text("Takip silinsin mi?") },
        text = { Text("\"${f.title}\" artık kontrol edilmeyecek. İndirdiğin dosyalar ve Kadraj listen silinmez.") },
        confirmButton = { TextButton(onClick = { Dl.unfollow(f); confirmDel = false; toast(ctx, "Takip silindi") }) { Text("Sil") } },
        dismissButton = { TextButton(onClick = { confirmDel = false }) { Text("Vazgeç") } })
}

private fun fmtAgo(t: Long): String {
    val m = (System.currentTimeMillis() - t) / 60_000
    return when { m < 1 -> "az önce"; m < 60 -> "$m dk önce"; m < 1440 -> "${m / 60} sa önce"; else -> "${m / 1440} gün önce" }
}
