@file:OptIn(ExperimentalMaterial3Api::class)

package com.perde.oynatici

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val RED = Color(0xFFFF6B6B)

/**
 * Seçim modu çubuğu: ekranın altında yüzer. Solda kapat + "N seçildi", ortada "Tümü", sağda işlemler.
 * Her listede aynı görünür.
 */
@Composable
fun SelBar(count: Int, total: Int, onAll: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp).shadow(12.dp, RoundedCornerShape(24.dp))
        .clip(RoundedCornerShape(24.dp)).background(Surf2).padding(horizontal = 6.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Seçimi kapat") }
            Text("$count seçildi", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            val all = count >= total && total > 0
            Row(Modifier.clip(CircleShape).background(if (all) Accent else Accent.copy(alpha = 0.15f)).clickable(onClick = onAll).padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(if (all) Icons.Filled.Deselect else Icons.Filled.SelectAll, null, Modifier.size(18.dp), tint = if (all) Color.White else Accent)
                Text(if (all) " Hiçbiri" else " Tümünü seç", color = if (all) Color.White else Accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            Spacer(Modifier.width(6.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, content = actions)
    }
}

/** Çubuktaki simgeli işlem düğmesi (simge + küçük yazı). */
@Composable
fun SelAction(icon: ImageVector, label: String, tint: Color = Ink, enabled: Boolean = true, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(14.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = if (enabled) tint else tint.copy(alpha = 0.35f), modifier = Modifier.size(22.dp))
        Text(label, fontSize = 11.sp, color = if (enabled) tint else tint.copy(alpha = 0.35f), maxLines = 1)
    }
}

/** Satır başındaki seçim işareti. */
@Composable
fun SelMark(on: Boolean) {
    Box(Modifier.padding(end = 10.dp).size(24.dp).clip(CircleShape).background(if (on) Accent else Color.Transparent)
        .then(if (on) Modifier else Modifier.background(Ink.copy(alpha = 0.12f))), contentAlignment = Alignment.Center) {
        if (on) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
    }
}

fun <T> MutableList<T>.toggle(x: T) { if (x in this) remove(x) else add(x) }

/** Şarkı listelerinin seçim çubuğu ve işlemleri (çal, sıraya ekle, listeye ekle, paylaş, sil). */
@Composable
fun SongSelBar(songs: List<Song>, picked: MutableList<String>, vm: LibVM, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableIntStateOf(0) }
    val sel = { songs.filter { it.key in picked } }
    androidx.activity.compose.BackHandler(picked.isNotEmpty()) { picked.clear() }
    if (picked.isNotEmpty()) SelBar(picked.size, songs.size, { if (picked.size >= songs.size) picked.clear() else { picked.clear(); picked.addAll(songs.map { it.key }) } }, { picked.clear() }, modifier) {
        SelAction(Icons.Filled.PlayArrow, "Çal") { val l = sel(); picked.clear(); Engine.playSongs(ctx, l, 0); AppState.showNow = true }
        SelAction(Icons.AutoMirrored.Filled.QueueMusic, "Sıraya ekle") { val l = sel(); picked.clear(); Engine.enqueue(ctx, l, false); toast(ctx, "${l.size} şarkı sıraya eklendi") }
        SelAction(Icons.AutoMirrored.Filled.PlaylistAdd, "Listeye") { dialog = 1 }
        SelAction(Icons.Filled.Share, "Paylaş") {
            val uris = ArrayList(sel().map { it.uri })
            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND_MULTIPLE).setType("audio/*")
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Paylaş"))
        }
        SelAction(Icons.Filled.Delete, "Sil", RED) { dialog = 2 }
    }
    when (dialog) {
        1 -> PlaylistPickerDialog(picked.toList()) { dialog = 0; picked.clear() }
        2 -> AlertDialog(onDismissRequest = { dialog = 0 }, title = { Text("${picked.size} şarkı silinsin mi?") }, text = { Text("Dosyalar telefondan silinir.") },
            confirmButton = { TextButton(onClick = {
                val l = sel(); dialog = 0; picked.clear()
                scope.launch { val n = withContext(Dispatchers.IO) { l.count { Library.deletePath(ctx, it.path, it.uri) } }; toast(ctx, "$n şarkı silindi"); vm.reload() }
            }) { Text("Sil", color = RED) } },
            dismissButton = { TextButton(onClick = { dialog = 0 }) { Text("Vazgeç") } })
    }
}

/** İndirmeler listesindeki seçim (gruplu satırlar da kullanabilsin diye ortak). */
object DlSel { val ids = mutableStateListOf<String>() }

@Composable
fun DlSelBar(modifier: Modifier = Modifier) {
    val ids = DlSel.ids
    androidx.activity.compose.BackHandler(ids.isNotEmpty()) { ids.clear() }
    // silinen işler seçimde kalmasın
    LaunchedEffect(Dl.jobs.size) { ids.retainAll(Dl.jobs.map { it.id }.toSet()) }
    if (ids.isEmpty()) return
    val sel = { Dl.jobs.filter { it.id in ids } }
    SelBar(ids.size, Dl.jobs.size, { if (ids.size >= Dl.jobs.size) ids.clear() else { ids.clear(); ids.addAll(Dl.jobs.map { it.id }) } }, { ids.clear() }, modifier) {
        SelAction(Icons.Filled.Pause, "Duraklat") { sel().filter { it.active }.forEach { Dl.pause(it) }; ids.clear() }
        SelAction(Icons.Filled.PlayArrow, "Devam") { sel().forEach { Dl.resume(it) }; ids.clear() }
        SelAction(Icons.Filled.VerticalAlignTop, "Önce indir") { sel().filter { it.state == Job.Q || it.state == Job.WAIT || it.state == Job.PAUSED }.reversed().forEach { Dl.prioritize(it) }; ids.clear() }
        SelAction(Icons.Filled.Close, "Kaldır", RED) { sel().forEach { Dl.remove(it) }; ids.clear() }
    }
}
