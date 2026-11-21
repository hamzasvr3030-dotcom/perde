@file:OptIn(ExperimentalMaterial3Api::class)

package com.perde.oynatici

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ---------------- depolama ----------------
@Composable
fun StorageScreen(vm: LibVM, back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<StorageInfo?>(null) }
    var ver by remember { mutableIntStateOf(0) }
    LaunchedEffect(ver, vm.ver) { info = StorageScan.scan(ctx, vm.videos, vm.songs) }
    val folders = remember(vm.videos) { Library.folders(vm.videos, true).sortedByDescending { it.size }.take(10) }
    val biggest = remember(vm.videos) { vm.videos.sortedByDescending { it.size }.take(15) }
    Page("Depolama", back) {
        val i = info
        if (i == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) }
        else LazyColumn(contentPadding = PaddingValues(16.dp)) {
            item {
                val used = i.total - i.free
                val other = (used - i.videos - i.music - i.trash - i.hidden - i.cache - i.engine).coerceAtLeast(0)
                val parts = listOf(Triple("Videolar", i.videos, Accent), Triple("Müzik", i.music, Color(0xFF4DA3FF)), Triple("Gizli klasör", i.hidden, Color(0xFF9D7BFF)),
                    Triple("Çöp kutusu", i.trash, Color(0xFFFF6B6B)), Triple("Kadraj önbelleği", i.cache + i.engine, Color(0xFFFFC23D)), Triple("Diğer uygulamalar ve dosyalar", other, Color(0xFF5A5D66)))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surf).padding(16.dp)) {
                    Text("${fmtSize(used)} / ${fmtSize(i.total)} dolu", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Boş: ${fmtSize(i.free)}", color = Muted)
                    Spacer(Modifier.height(12.dp))
                    Canvas(Modifier.fillMaxWidth().height(18.dp).clip(CircleShape)) {
                        drawRoundRect(Color(0xFF2A2D36), cornerRadius = CornerRadius(size.height))
                        var x = 0f
                        parts.forEach { (_, v, c) -> val w = size.width * v / i.total; drawRect(c, Offset(x, 0f), Size(w, size.height)); x += w }
                    }
                    Spacer(Modifier.height(12.dp))
                    parts.forEach { (n, v, c) ->
                        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(c))
                            Text(n, Modifier.weight(1f).padding(start = 10.dp)); Text(fmtSize(v), color = Muted)
                        }
                    }
                }
                Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { scope.launch(Dispatchers.IO) { StorageScan.clearCache(ctx) }.invokeOnCompletion { ver++ }; toast(ctx, "Önbellek temizlendi") }) { Text("Önbelleği temizle") }
                    if (i.trash > 0) FilledTonalButton(onClick = { scope.launch(Dispatchers.IO) { Trash.list(ctx).forEach { Trash.deleteForever(it) } }.invokeOnCompletion { ver++ }; toast(ctx, "Çöp boşaltıldı") }) { Text("Çöpü boşalt") }
                }
            }
            item { Text("En çok yer kaplayan klasörler", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)) }
            items(folders, key = { "f" + it.path }) { f ->
                val frac = if (i.videos > 0) f.size.toFloat() / i.videos else 0f
                Column(Modifier.padding(vertical = 5.dp)) {
                    Row { Text(f.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis); Text(fmtSize(f.size), color = Muted) }
                    LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp).clip(CircleShape), color = Accent, trackColor = Surf2)
                }
            }
            item { Text("En büyük videolar", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp)) }
            items(biggest, key = { "b" + it.path }) { v ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { PlayerActivity.open(ctx, listOf(v), 0) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Thumb(v, Modifier.size(96.dp, 54.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(v.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                        Text(File(v.folder).name, color = Muted, fontSize = 12.sp)
                    }
                    Text(fmtSize(v.size), fontWeight = FontWeight.Bold, color = Accent)
                }
            }
        }
    }
}

// ---------------- kopya dosyalar ----------------
@Composable
fun DupesScreen(vm: LibVM, back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var groups by remember { mutableStateOf<List<List<DupItem>>?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    val picked = remember { mutableStateListOf<String>() }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(vm.ver) {
        groups = null
        val g = Dupes.find(vm.videos, vm.songs) { progress = it }
        groups = g; picked.clear(); g.forEach { grp -> picked.addAll(grp.drop(1).map { it.path }) }   // en eskisi kalsın, diğerleri seçili
    }
    val freed = groups?.flatten()?.filter { it.path in picked }?.sumOf { it.size } ?: 0L
    Page("Kopya dosyalar", back) {
        val g = groups
        when {
            g == null -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(progress = { progress }, color = Accent); Text("Dosyalar karşılaştırılıyor… %${(progress * 100).toInt()}", color = Muted, modifier = Modifier.padding(top = 12.dp))
            }
            g.isEmpty() -> Empty(Icons.Filled.CheckCircle, "Kopya dosya bulunamadı. 🎉")
            else -> Column {
                Row(Modifier.fillMaxWidth().background(AccentDeep).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${g.size} grup · ${picked.size} dosya seçili", fontWeight = FontWeight.Bold)
                        Text("Silinince ${fmtSize(freed)} yer açılır", color = AccentSoft, fontSize = 13.sp)
                    }
                    Button(enabled = picked.isNotEmpty(), onClick = { confirm = true }) { Text("Sil") }
                }
                LazyColumn(contentPadding = PaddingValues(16.dp)) {
                    items(g, key = { it.first().path }) { grp ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(16.dp)).background(Surf).padding(10.dp)) {
                            Text("${grp.first().title} · ${fmtSize(grp.first().size)} × ${grp.size}", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            grp.forEach { d ->
                                Row(Modifier.fillMaxWidth().clickable { if (d.path in picked) picked.remove(d.path) else picked.add(d.path) }, verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(d.path in picked, { if (it) picked.add(d.path) else picked.remove(d.path) })
                                    Icon(if (d.audio) Icons.Filled.MusicNote else Icons.Filled.Movie, null, tint = Muted, modifier = Modifier.size(18.dp))
                                    Column(Modifier.padding(start = 8.dp)) {
                                        Text(File(d.path).parent ?: "", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, TR).format(d.dateAdded * 1000), color = Muted, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("${picked.size} kopya silinsin mi?") },
        text = { Text(if (Prefs.useTrash) "Dosyalar çöp kutusuna taşınır (30 gün saklanır)." else "Dosyalar kalıcı olarak silinir.") },
        confirmButton = { TextButton(onClick = {
            confirm = false
            val all = groups?.flatten()?.filter { it.path in picked } ?: emptyList()
            scope.launch { val n = withContext(Dispatchers.IO) { all.count { Library.deletePath(ctx, it.path, it.uri) } }; toast(ctx, "$n kopya silindi"); vm.reload() }
        }) { Text("Sil", color = Color(0xFFFF6B6B)) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Vazgeç") } })
}

// ---------------- film / dizi bilgisi ----------------
@Composable
fun MetaContent(key: String, fileName: String, onPlay: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var meta by remember(key) { mutableStateOf(MetaNet.cached(ctx, key)) }
    var loading by remember(key) { mutableStateOf(false) }
    var tried by remember(key) { mutableStateOf(MetaNet.triedBefore(ctx, key)) }
    fun search(force: Boolean) { loading = true; scope.launch { meta = MetaNet.lookup(ctx, key, fileName, force); tried = true; loading = false } }
    LaunchedEffect(key) { if (meta == null && !tried) search(false) }
    val m = meta
    when {
        loading -> Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(24.dp), color = Accent); Text("İnternette aranıyor…", Modifier.padding(start = 12.dp)) }
        m == null -> Column {
            Text("Bu video için film/dizi bilgisi bulunamadı.", color = Muted)
            Text("Dosya adı filmin adını içeriyorsa daha iyi sonuç verir (örn. \"Başlangıç 2010.mkv\" veya \"Dizi S01E02.mkv\").", color = Muted, fontSize = 12.sp)
            TextButton(onClick = { search(true) }) { Text("Tekrar ara") }
        }
        else -> Column {
            Row {
                Box(Modifier.width(110.dp).aspectRatio(2 / 3f).clip(RoundedCornerShape(12.dp)).background(Surf2)) {
                    if (m.poster != null) AsyncImage(m.poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Column(Modifier.padding(start = 14.dp)) {
                    Text(if (m.kind == "dizi") "DİZİ" else "FİLM", color = Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(m.title, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    listOfNotNull(m.year, m.rating?.let { "★ $it" }).joinToString(" · ").takeIf { it.isNotEmpty() }?.let { Text(it, color = Muted) }
                    m.genres?.let { Text(it, color = Muted, fontSize = 13.sp) }
                    m.episode?.let { Text(it, color = AccentSoft, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
                }
            }
            if (m.summary.isNotBlank()) Text(m.summary, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
            Text("Kaynak: ${if (m.kind == "dizi") "TVmaze" else "Vikipedi"}", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.padding(top = 6.dp)) {
                if (onPlay != null) Button(onClick = onPlay) { Icon(Icons.Filled.PlayArrow, null); Text("Oynat") }
                TextButton(onClick = { MetaNet.forget(ctx, key); search(true) }) { Text("Yanlış mı? Tekrar ara") }
            }
        }
    }
}

/** Ana sayfada "Filmler ve diziler" satırı: uzun videolar için arka planda bilgi bulur. */
@Composable
fun FilmsRow(vm: LibVM, open: (Video) -> Unit, info: (Video) -> Unit) {
    val ctx = LocalContext.current
    val long = remember(vm.videos) { vm.videos.filter { it.duration >= 40 * 60_000 }.sortedByDescending { it.dateAdded } }
    var ver by remember { mutableIntStateOf(0) }
    LaunchedEffect(long) {
        if (!Prefs.autoMeta) return@LaunchedEffect
        long.filter { !MetaNet.triedBefore(ctx, it.key) }.take(12).forEach { v -> MetaNet.lookup(ctx, v.key, File(v.path).name); ver++ }
        long.forEach { v -> if (MetaNet.ensurePoster(ctx, v.key)) ver++ }
    }
    val withMeta = remember(long, ver) { long.mapNotNull { v -> MetaNet.cached(ctx, v.key)?.let { v to it } } }
    if (withMeta.isEmpty()) return
    SectionHeader("Filmler ve diziler")
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(withMeta, key = { it.first.key }) { (v, m) ->
            Column(Modifier.width(120.dp).clickable { open(v) }) {
                Box(Modifier.fillMaxWidth().aspectRatio(2 / 3f).clip(RoundedCornerShape(14.dp)).background(Surf2)) {
                    if (m.poster != null) AsyncImage(m.poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Icon(Icons.Filled.Movie, null, tint = Muted, modifier = Modifier.align(Alignment.Center))
                    m.rating?.let { Text("★ $it", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xB3000000)).padding(horizontal = 5.dp)) }
                    IconButton(onClick = { info(v) }, modifier = Modifier.align(Alignment.BottomEnd).size(34.dp)) {
                        Icon(Icons.Filled.Info, "Bilgi", tint = Color.White, modifier = Modifier.clip(CircleShape).background(Color(0x99000000)).padding(4.dp))
                    }
                }
                Text(m.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
                Text(listOfNotNull(m.year, m.episode?.substringBefore(" ·")).joinToString(" · "), color = Muted, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}
