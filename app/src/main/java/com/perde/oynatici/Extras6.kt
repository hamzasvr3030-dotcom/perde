@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.perde.oynatici

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Environment
import android.os.StatFs
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.IconCompat
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.random.Random

private val TRL = Locale.forLanguageTag("tr")

// ============================ Mix'ler ve radyo ============================
data class Mix(val title: String, val sub: String, val songs: List<Song>, val keepOrder: Boolean = false)

object Mixes {
    private const val DAY = 86_400_000L

    private fun line(l: List<Song>): String {
        val a = l.map { it.artist }.filter { it.isNotBlank() && it != "<unknown>" }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
        return if (a.isEmpty()) "${l.size} şarkı" else a.take(2).joinToString(", ") + if (l.size > 2) " ve ${l.size - 2} şarkı" else ""
    }

    fun all(songs: List<Song>): List<Mix> {
        if (songs.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val rnd = Random(SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date()).toInt())
        val byPlays = songs.filter { Stats.plays(it.key) > 0 }.sortedByDescending { Stats.plays(it.key) }
        val fav = Favs.all()
        val daily = (byPlays.take(10).shuffled(rnd) + songs.filter { it.key in fav }.shuffled(rnd).take(8) + songs.shuffled(rnd).take(20)).distinctBy { it.key }.take(25)
        val forgotten = songs.filter { Stats.plays(it.key) >= 2 && Prefs.playedAt(it.key) in 1 until now - 30 * DAY }.sortedByDescending { Stats.plays(it.key) }.take(30)
        val month = ListenLog.all().filter { !it.video && now - it.at < 30 * DAY }.groupingBy { it.key }.eachCount()
        val top = songs.filter { (month[it.key] ?: 0) > 0 }.sortedByDescending { month[it.key] }.take(25).ifEmpty { byPlays.take(25) }
        val newest = songs.sortedByDescending { it.dateAdded }.take(30)
        val never = songs.filter { Stats.plays(it.key) == 0 && Prefs.playedAt(it.key) == 0L }.shuffled(rnd).take(30)
        fun mix(l: List<Song>) = SmartShuffle.order(l, { it.artist }, { false }, null, rnd)
        return listOfNotNull(
            Mix("Günün mix'i", line(daily), mix(daily)),
            forgotten.takeIf { it.size >= 5 }?.let { Mix("Unuttukların", "Bir aydır çalmadın", mix(it)) },
            top.takeIf { it.size >= 5 }?.let { Mix("En çok 25", "Son 30 gün", it, keepOrder = true) },
            Mix("Yeni eklenenler", "${newest.size} şarkı", newest, keepOrder = true),
            never.takeIf { it.size >= 5 }?.let { Mix("Keşfet", "Hiç dinlemediklerin", mix(it)) },
        )
    }

    /** Bir şarkıdan radyo: aynı sanatçı/albüm, birlikte dinlediklerin ve aynı klasör. */
    fun radio(seed: Song, all: List<Song>): List<Song> {
        val a = seed.artist.trim().lowercase(TRL)
        val same = all.filter { it.key != seed.key && ((a.isNotEmpty() && a != "<unknown>" && it.artist.trim().lowercase(TRL) == a) || (seed.albumId > 0 && it.albumId == seed.albumId)) }
        val log = ListenLog.all().filter { !it.video }
        val times = log.filter { it.key == seed.key }.map { it.at }
        val co = HashMap<String, Int>()
        if (times.isNotEmpty()) log.forEach { l -> if (l.key != seed.key && times.any { kotlin.math.abs(it - l.at) < 45 * 60_000L }) co[l.key] = (co[l.key] ?: 0) + 1 }
        val byKey = all.associateBy { it.key }
        val coSongs = co.entries.sortedByDescending { it.value }.mapNotNull { byKey[it.key] }.take(25)
        val folder = all.filter { it.folder == seed.folder && it.key != seed.key }.shuffled().take(10)
        val pool = (same.shuffled().take(20) + coSongs + folder).distinctBy { it.key }
        val fill = if (pool.size < 25) all.filter { s -> s.key != seed.key && pool.none { it.key == s.key } }.sortedByDescending { Stats.plays(it.key) }.take(25 - pool.size).shuffled() else emptyList()
        return listOf(seed) + SmartShuffle.order(pool + fill, { it.artist }, { false }, seed.artist)
    }

    fun startRadio(ctx: Context, seed: Song, all: List<Song>) {
        val l = radio(seed, all)
        Engine.playSongs(ctx, l, 0); AppState.showNow = true
        toast(ctx, "Radyo: \"${seed.title}\" ve ${l.size - 1} benzer şarkı")
    }
}

// ============================ Eksik kapaklar ============================
object CoverNet {
    private val pool = Executors.newSingleThreadExecutor()
    private val tried = HashSet<String>()

    private fun md5(s: String) = MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    fun file(ctx: Context, key: String) = File(ctx.filesDir, "kapak/${md5(key)}.jpg")

    fun cached(ctx: Context, key: String, px: Int): android.graphics.Bitmap? {
        val f = file(ctx, key); if (!f.exists()) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(f.path, o)
        var s = 1; while (o.outWidth / (s * 2) >= px) s *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
    }

    private fun get(u: String): String? = try {
        (URL(u).openConnection() as HttpURLConnection).run { connectTimeout = 8000; readTimeout = 8000; setRequestProperty("User-Agent", "Kadraj"); inputStream.bufferedReader().use { it.readText() } }
    } catch (_: Exception) { null }

    /** iTunes, olmazsa Deezer'dan kapak bulup kalıcı kaydeder (anahtar istemez). */
    fun fetchNow(ctx: Context, s: Song): Boolean {
        val f = file(ctx, s.key); if (f.exists()) return true
        val artist = s.artist.takeIf { it.isNotBlank() && it != "<unknown>" } ?: ""
        val title = cleanTitle(s.title + ".x")
        val q = "$artist $title".trim(); if (q.length < 3) return false
        val enc = URLEncoder.encode(q, "UTF-8")
        val art = get("https://itunes.apple.com/search?term=$enc&entity=song&limit=1&country=tr")?.let { t ->
            try { JSONObject(t).optJSONArray("results")?.optJSONObject(0)?.optString("artworkUrl100")?.takeIf { it.isNotEmpty() }?.replace("100x100bb", "600x600bb") } catch (_: Exception) { null }
        } ?: get("https://api.deezer.com/search?q=$enc&limit=1")?.let { t ->
            try { JSONObject(t).optJSONArray("data")?.optJSONObject(0)?.optJSONObject("album")?.optString("cover_big")?.takeIf { it.isNotEmpty() } } catch (_: Exception) { null }
        } ?: return false
        return try {
            val bytes = (URL(art).openConnection() as HttpURLConnection).run { connectTimeout = 8000; readTimeout = 10000; inputStream.use { it.readBytes() } }
            if (bytes.size < 2000) return false
            f.parentFile?.mkdirs(); f.writeBytes(bytes); true
        } catch (_: Exception) { false }
    }

    /** Arka planda sıraya al; bulununca kapaklar yenilenir. */
    fun request(ctx: Context, key: String) {
        if (!Prefs.fetchCovers) return
        val s = Music.byKey(key) ?: return
        synchronized(tried) { if (!tried.add(key)) return }
        val app = ctx.applicationContext
        pool.execute { if (fetchNow(app, s)) { Art.invalidate(key); android.os.Handler(android.os.Looper.getMainLooper()).post { Art.ver++ } } }
    }

    fun fetchAll(ctx: Context, songs: List<Song>, done: (Int) -> Unit) {
        val app = ctx.applicationContext
        pool.execute {
            var n = 0
            songs.forEach { s ->
                val has = runBlocking { Art.load(app, s.key, s.uri, s.path, 96, net = false) } != null
                if (!has && fetchNow(app, s)) { n++; Art.invalidate(s.key) }
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post { Art.ver++; done(n) }
        }
    }
}

// ============================ Kapağa göre tema rengi ============================
object CoverTint {
    fun update(ctx: Context, key: String?) {
        if (!Prefs.coverAccent || key == null) { ThemeState.dyn = null; return }
        val app = ctx.applicationContext
        Thread {
            val s = Music.byKey(key)
            val b = runBlocking { Art.load(app, key, s?.uri, s?.path ?: key.takeIf { it.startsWith("/") }, 160) }
            val c = b?.let { bmp ->
                val p = Palette.from(bmp).generate()
                val rgb = p.vibrantSwatch?.rgb ?: p.lightVibrantSwatch?.rgb ?: p.dominantSwatch?.rgb ?: return@let null
                val hsl = FloatArray(3); ColorUtils.colorToHSL(rgb, hsl)
                if (hsl[1] < 0.15f) return@let null                 // gri kapaklarda normal renk kalsın
                hsl[1] = hsl[1].coerceAtLeast(0.5f); hsl[2] = hsl[2].coerceIn(0.55f, 0.68f)
                Color(ColorUtils.HSLToColor(hsl))
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post { ThemeState.dyn = c }
        }.start()
    }
}

// ============================ Ortak görünüm parçaları ============================
/** Başlıktaki simgeleri tek bir yuvarlak grupta toplar. */
@Composable
fun ActionPill(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.padding(end = 10.dp).clip(RoundedCornerShape(16.dp)).background(Surf2), verticalAlignment = Alignment.CenterVertically, content = content)
}

@Composable
fun BigHeader(title: String, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(start = 18.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = (ThemeState.style.titleSize.takeIf { it > 0 } ?: 34).sp, fontWeight = FontWeight(ThemeState.style.titleWeight), letterSpacing = (-0.5).sp, modifier = Modifier.weight(1f), maxLines = 1)
        ActionPill(actions)
    }
}

@Composable
fun Pill(text: String, on: Boolean, onClick: () -> Unit) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else Ink.copy(alpha = 0.85f),
        modifier = Modifier.clip(CircleShape).background(if (on) Accent else Surf2).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp))
}

@Composable
fun SecTitle(t: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
        Text(t, fontSize = 21.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
        if (action != null) Text(action, color = Accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.clickable(onClick = onAction))
    }
}

/** Telefon depolaması: (kullanılan, toplam). */
fun storageUse(): Pair<Long, Long> = try { val s = StatFs(Environment.getExternalStorageDirectory().path); (s.totalBytes - s.availableBytes) to s.totalBytes } catch (_: Exception) { 0L to 1L }

// ============================ Müzik › Senin için ============================
@Composable
fun ForYouPage(vm: LibVM, go: (Route) -> Unit) {
    val ctx = LocalContext.current
    val songs = vm.songs
    val mixes = remember(songs, vm.ver) { Mixes.all(songs) }
    val week = remember(ListenLog.ver) { Stats.last7("stm:") }
    val prevWeek = remember(ListenLog.ver) { (7..13).sumOf { Stats.day(it, "stm:") } }
    val weekMs = week.sumOf { it.second }
    val weekCount = remember(ListenLog.ver) { ListenLog.all().count { !it.video && System.currentTimeMillis() - it.at < 7 * 86_400_000L } }
    val recent = remember(ListenLog.ver) { ListenLog.all().filter { !it.video }.distinctBy { it.key }.take(4) }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(AccentDeep, Surf))).padding(18.dp)) {
                Text("BU HAFTA", fontSize = 11.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold, color = AccentSoft)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text(fmtDur(weekMs), fontSize = 30.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("$weekCount şarkı", fontWeight = FontWeight.Bold)
                        if (prevWeek > 0) { val d = ((weekMs - prevWeek) * 100 / prevWeek).toInt(); Text(if (d >= 0) "%$d artış" else "%${-d} azalış", color = Muted, fontSize = 13.sp) }
                    }
                }
                val max = (week.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
                Row(Modifier.fillMaxWidth().height(56.dp).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                    week.forEachIndexed { i, (_, ms) ->
                        Box(Modifier.weight(1f).fillMaxHeight((ms.toFloat() / max).coerceAtLeast(0.08f)).clip(RoundedCornerShape(6.dp))
                            .background(if (i == week.lastIndex) Accent else Accent.copy(alpha = 0.35f)))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) { week.forEachIndexed { i, (d, _) -> Text(d.take(3), Modifier.weight(1f), fontSize = 11.sp, color = if (i == week.lastIndex) Ink else Muted) } }
            }
        }
        if (mixes.isNotEmpty()) {
            item { SecTitle("Mix'lerin") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(mixes, key = { it.title }) { m -> MixCard(m) { Engine.playSongs(ctx, m.songs, 0); AppState.showNow = true } }
                }
            }
        }
        val seed = (Music.byKey(NowState.key) ?: recent.firstOrNull()?.let { Music.byKey(it.key) })
        if (seed != null) item {
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surf)
                .clickable { Mixes.startRadio(ctx, seed, songs) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ArtImage(seed.key, seed.uri, seed.path, Modifier.size(52.dp), RoundedCornerShape(14.dp), 160)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("Radyo", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("\"${seed.title}\" gibi şarkılar", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Filled.Radio, null, tint = Accent)
            }
        }
        item { SecTitle("Son dinlediklerin", "Geçmiş") { go(Route.ListenHistR) } }
        if (recent.isEmpty()) item { Text("Bir şarkıyı 30 saniyeden fazla dinleyince burada görünür.", color = Muted, modifier = Modifier.padding(horizontal = 18.dp)) }
        items(recent, key = { "r" + it.key }) { l ->
            val s = Music.byKey(l.key)
            Row(Modifier.fillMaxWidth().clickable { s?.let { Engine.playSongs(ctx, listOf(it), 0); AppState.showNow = true } }.padding(horizontal = 18.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                ArtImage(l.key, s?.uri, s?.path, Modifier.size(50.dp), RoundedCornerShape(12.dp), 160)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(l.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(l.artist, color = Muted, fontSize = 13.sp, maxLines = 1)
                }
                s?.let { Text(fmtTime(it.duration), color = Muted, fontSize = 13.sp) }
            }
        }
        item {
            Row(Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(Accent, Color(0xFF5B2A86))))
                .clickable { go(Route.SummaryR) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("KADRAJ ÖZET", fontSize = 11.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.8f))
                    Text("Bu ayın özetini gör", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White)
                }
                Icon(Icons.Filled.ChevronRight, null, tint = Color.White)
            }
        }
    }
}

@Composable
private fun MixCard(m: Mix, onClick: () -> Unit) {
    Column(Modifier.width(156.dp)) {
        Box(Modifier.size(156.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick)) {
            val s = m.songs.first()
            ArtImage(s.key, s.uri, s.path, Modifier.fillMaxSize(), RoundedCornerShape(0.dp), 320)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Accent.copy(alpha = 0.25f), Color(0xE6000000)))))
            Box(Modifier.align(Alignment.TopEnd).padding(10.dp).size(34.dp).clip(CircleShape).background(Color(0x66000000)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White)
            }
            Text(m.title, fontWeight = FontWeight.Black, fontSize = 18.sp, lineHeight = 20.sp, color = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(12.dp))
        }
        Text(m.sub, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    }
}

// ============================ Aylık özet ============================
@Composable
fun SummaryScreen(vm: LibVM, back: () -> Unit) {
    val ctx = LocalContext.current
    var ago by remember { mutableIntStateOf(0) }
    val cal = remember(ago) { Calendar.getInstance().apply { add(Calendar.MONTH, -ago) } }
    val ym = SimpleDateFormat("yyyyMM", Locale.ROOT).format(cal.time)
    val prevYm = SimpleDateFormat("yyyyMM", Locale.ROOT).format(Calendar.getInstance().apply { time = cal.time; add(Calendar.MONTH, -1) }.time)
    val monthName = SimpleDateFormat("MMMM", TRL).format(cal.time).uppercase(TRL)
    val music = Stats.month("stm:", ym); val video = Stats.month("stv:", ym); val total = Stats.month("st:", ym)
    val prevMusic = Stats.month("stm:", prevYm)
    val log = remember(ym, ListenLog.ver) { val f = SimpleDateFormat("yyyyMM", Locale.ROOT); ListenLog.all().filter { f.format(Date(it.at)) == ym } }
    val songCounts = log.filter { !it.video }.groupingBy { it.key }.eachCount().entries.sortedByDescending { it.value }
    val artistCounts = log.filter { !it.video && it.artist.isNotBlank() && it.artist != "<unknown>" }.groupingBy { it.artist }.eachCount().entries.sortedByDescending { it.value }
    val titles = log.associate { it.key to (it.title to it.artist) }
    val dls = remember(ym) { val f = SimpleDateFormat("yyyyMM", Locale.ROOT); Dl.history.filter { it.ok && f.format(Date(it.at)) == ym } }
    val dlSize = remember(dls) { dls.sumOf { h -> h.file?.let { File(it).length() } ?: 0L } }
    val head = when { music > 0 -> "${ago.let { if (it == 0) "Bu ay" else "O ay" }} ${music / 3_600_000} saat\nmüzik dinledin."; total > 0 -> "Bu ay ${fmtDur(total)}\nKadraj'daydın."; else -> "Henüz yeterli\nveri yok." }
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides Color.White) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Accent, Color(0xFF5B2A86), Color(0xFF0B0B0E))))) {
        LazyColumn(Modifier.windowInsetsPadding(WindowInsets.statusBars), contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 30.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                    GlassButton(Icons.AutoMirrored.Filled.ArrowBack, "Geri", 40.dp, 22.dp) { back() }
                    Spacer(Modifier.weight(1f))
                    Pill("Bu ay", ago == 0) { ago = 0 }; Spacer(Modifier.width(8.dp)); Pill("Geçen ay", ago == 1) { ago = 1 }
                }
            }
            item {
                Text("KADRAJ ÖZET · $monthName", fontSize = 13.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(top = 18.dp))
                Text(head, fontSize = 38.sp, lineHeight = 42.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 8.dp))
                if (music > 0 && prevMusic > 0) { val d = (music - prevMusic) / 3_600_000; Text(if (d >= 0) "Geçen aya göre $d saat daha fazla." else "Geçen aya göre ${-d} saat daha az.", color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(top = 6.dp)) }
            }
            item {
                FlowRow(Modifier.padding(top = 22.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), maxItemsInEachRow = 2) {
                    StatTile("En çok sanatçı", artistCounts.firstOrNull()?.key ?: "—", artistCounts.firstOrNull()?.let { "${it.value} kez" } ?: "", Modifier.weight(1f))
                    StatTile("En çok şarkı", songCounts.firstOrNull()?.let { titles[it.key]?.first } ?: "—", songCounts.firstOrNull()?.let { "${it.value} kez" } ?: "", Modifier.weight(1f))
                    StatTile("Video izleme", if (video > 0) fmtDur(video) else "—", "", Modifier.weight(1f))
                    StatTile("İndirilen", "${dls.size} dosya", if (dlSize > 0) fmtSize(dlSize) else "", Modifier.weight(1f))
                }
            }
            if (songCounts.isNotEmpty()) {
                item { Text("En çok 5", fontWeight = FontWeight.Black, fontSize = 19.sp, modifier = Modifier.padding(top = 24.dp, bottom = 4.dp)) }
                items(songCounts.take(5).withIndex().toList(), key = { "t" + it.value.key }) { (i, e) ->
                    val s = Music.byKey(e.key); val t = titles[e.key]
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.width(26.dp))
                        ArtImage(e.key, s?.uri, s?.path, Modifier.size(46.dp), RoundedCornerShape(12.dp), 128)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(t?.first ?: "", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(t?.second ?: "", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 1)
                        }
                        Text("${e.value}", fontWeight = FontWeight.Bold)
                    }
                }
                item {
                    Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = {
                            val l = songCounts.take(25).mapNotNull { Music.byKey(it.key) }
                            if (l.isNotEmpty()) { Engine.playSongs(ctx, l, 0); AppState.showNow = true; back() }
                        }, modifier = Modifier.weight(1f).height(50.dp)) { Icon(Icons.Filled.PlayArrow, null); Text("En çok 25'i çal") }
                        FilledTonalButton(onClick = {
                            val txt = buildString {
                                append("Kadraj özetim · ").append(monthName.lowercase(TRL).replaceFirstChar { it.titlecase(TRL) }).append('\n')
                                if (music > 0) append("🎧 ${fmtDur(music)} müzik\n"); if (video > 0) append("🎬 ${fmtDur(video)} video\n")
                                artistCounts.firstOrNull()?.let { append("En çok: ${it.key}\n") }
                                songCounts.take(5).forEachIndexed { i, e -> titles[e.key]?.let { append("${i + 1}. ${it.first} – ${it.second}\n") } }
                            }
                            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, txt), "Paylaş"))
                        }, modifier = Modifier.weight(1f).height(50.dp)) { Icon(Icons.Filled.Share, null); Text(" Paylaş") }
                    }
                }
            } else item { Text("Bu ay için şarkı geçmişi yok. Dinledikçe burada en sevdiklerin çıkar.", color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(top = 20.dp)) }
        }
    }
    }
}

@Composable
private fun StatTile(label: String, value: String, sub: String, m: Modifier) {
    Column(m.clip(RoundedCornerShape(20.dp)).background(Color(0x47000000)).padding(14.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
        Text(value, fontSize = 21.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        Text(sub, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
    }
}

// ============================ İndirme: toplam hız paneli ============================
fun fmtBps(bps: Long): String = if (bps >= 1_048_576) String.format(TRL, "%.1f", bps / 1048576.0) else String.format(TRL, "%.0f", bps / 1024.0)
fun speedUnit(bps: Long) = if (bps >= 1_048_576) "MB/sn" else "KB/sn"

@Composable
fun SpeedPanel() {
    val run = Dl.jobs.filter { it.state == Job.RUN }
    if (run.isEmpty()) return
    val waiting = Dl.jobs.count { it.state == Job.Q || it.state == Job.WAIT }
    val bps = Dl.totalBps
    val left = run.sumOf { (it.totalBytes - it.gotBytes).coerceAtLeast(0) }
    Column(Modifier.padding(top = 18.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Surf).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("TOPLAM HIZ", fontSize = 11.sp, letterSpacing = 1.5.sp, color = Muted, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(fmtBps(bps), fontSize = 30.sp, fontWeight = FontWeight.Black)
                    Text(" " + speedUnit(bps), color = Muted, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 5.dp))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${run.size} indiriliyor", fontWeight = FontWeight.Bold)
                Text(listOfNotNull(if (waiting > 0) "$waiting sırada" else null, if (bps > 0 && left > 0) "≈ " + fmtEta(left / bps) else null).joinToString(" · "), color = Muted, fontSize = 13.sp)
            }
        }
        val h = Dl.speedHist
        val max = (h.maxOrNull() ?: 0L).coerceAtLeast(1L)
        Row(Modifier.fillMaxWidth().height(54.dp).padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
            repeat(30 - h.size) { Box(Modifier.weight(1f).fillMaxHeight(0.05f).clip(RoundedCornerShape(3.dp)).background(Surf2)) }
            h.forEach { v -> Box(Modifier.weight(1f).fillMaxHeight((v.toFloat() / max).coerceAtLeast(0.05f)).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                .background(Brush.verticalGradient(listOf(Accent, AccentDeep)))) }
        }
    }
}

fun fmtEta(sec: Long): String = when { sec >= 3600 -> "${sec / 3600} sa ${sec % 3600 / 60} dk"; sec >= 60 -> "${sec / 60} dk ${sec % 60} sn"; else -> "$sec sn" }

// ============================ Kaliteye göre boyut ============================
object SizeEst {
    class Info(val dur: Double, val video: Map<Int, Long>)

    fun of(ctx: Context, url: String?): Info? {
        if (url == null) return null
        val f = Web.freshInfo(ctx, url) ?: return null
        return try {
            val o = JSONObject(f.readText())
            val dur = o.optDouble("duration", 0.0)
            val fm = o.optJSONArray("formats") ?: return Info(dur, emptyMap())
            val list = (0 until fm.length()).map { fm.getJSONObject(it) }
            fun size(x: JSONObject): Long {
                x.optLong("filesize", 0).takeIf { it > 0 }?.let { return it }
                x.optLong("filesize_approx", 0).takeIf { it > 0 }?.let { return it }
                val tbr = x.optDouble("tbr", 0.0); return if (tbr > 0 && dur > 0) (tbr * 125 * dur).toLong() else 0
            }
            val audio = list.filter { it.optString("vcodec") == "none" && it.optString("acodec", "none") != "none" }
                .maxByOrNull { if (it.optString("ext") == "m4a") 1000 + it.optDouble("abr", 0.0) else it.optDouble("abr", 0.0) }
            val aBytes = audio?.let(::size) ?: 0L
            val vids = list.filter { it.optString("vcodec", "none") != "none" && it.optInt("height", 0) > 0 }
            Info(dur, DlPrefs.HEIGHTS.associateWith { h ->
                vids.filter { it.optInt("height") <= h }.maxWithOrNull(compareBy({ it.optInt("height") }, { if (it.optString("ext") == "mp4") 1 else 0 }, { it.optDouble("tbr", 0.0) }))
                    ?.let { b -> size(b).takeIf { it > 0 }?.let { it + if (b.optString("acodec", "none") == "none") aBytes else 0 } } ?: 0L
            })
        } catch (_: Exception) { null }
    }
}

// ============================ Ana ekran kısayolları ============================
object Shortcuts {
    fun setup(ctx: Context) = try {
        fun sc(id: String, label: String, icon: Int) = ShortcutInfoCompat.Builder(ctx, id).setShortLabel(label).setIcon(IconCompat.createWithResource(ctx, icon))
            .setIntent(Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra("shortcut", id).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)).build()
        ShortcutManagerCompat.setDynamicShortcuts(ctx, listOf(
            sc("resume", "Son videoya devam", R.drawable.sc_play),
            sc("shuffle", "Karışık müzik", R.drawable.sc_music),
            sc("paste", "Bağlantı yapıştır", R.drawable.sc_download)))
    } catch (_: Exception) { false }

    /** Kısayol isteğini kütüphane yüklenince uygula. */
    suspend fun run(ctx: Context, id: String, vm: LibVM) {
        when (id) {
            "resume" -> {
                val v = vm.videos.filter { Prefs.playedAt(it.key) > 0 }.maxByOrNull { Prefs.playedAt(it.key) } ?: return toast(ctx, "Henüz izlenen video yok")
                val list = Library.sort(vm.videos.filter { it.folder == v.folder })
                PlayerActivity.open(ctx, list, list.indexOf(v).coerceAtLeast(0))
            }
            "shuffle" -> if (vm.songs.isNotEmpty()) { Engine.playSongs(ctx, vm.songs, vm.songs.indices.random(), shuffle = true); AppState.showNow = true; AppState.tab = 1 }
            "paste" -> AppState.forceClip = true
        }
    }
}

@Suppress("unused") private suspend fun ioNoop() = withContext(Dispatchers.IO) { }

fun mbOf(b: Long): String = String.format(TRL, "%.1f", b / 1048576.0)
