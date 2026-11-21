@file:OptIn(ExperimentalMaterial3Api::class)

package com.perde.oynatici

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random

// ============================ Akıllı karıştırma ============================
/**
 * Aynı sanatçı arka arkaya gelmez (mümkün olduğunca), yakın zamanda dinlenenler en sona atılır.
 * Her adımda önceki sanatçıdan farklı ve elinde en çok şarkısı kalan sanatçı seçilir; böylece sona yığılma olmaz.
 */
object SmartShuffle {
    fun <T> order(items: List<T>, artist: (T) -> String, recent: (T) -> Boolean, lastArtist: String? = null, rnd: Random = Random.Default): List<T> {
        val (old, fresh) = items.partition(recent)
        val a = spread(fresh.shuffled(rnd), artist, lastArtist).let { l ->
            // az önce çalan sanatçıyla başlamasın (mümkünse)
            val la = norm(lastArtist)
            if (la != null && l.size > 1 && norm(artist(l[0])) == la) l.indexOfFirst { norm(artist(it)) != la }.takeIf { it > 0 }?.let { j -> l.toMutableList().apply { add(0, removeAt(j)) } } ?: l else l
        }
        return a + spread(old.shuffled(rnd), artist, a.lastOrNull()?.let(artist) ?: lastArtist)
    }

    private fun norm(s: String?) = s?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() && it != "<unknown>" && it != "bilinmeyen sanatçı" }

    /** Liste zaten rastgele; sadece aynı sanatçı 3. kez üst üste gelecekse onu ileriden biriyle değiştirir.
     *  Böylece aynı sanatçı arada bir art arda gelebilir ama "iki şarkıda bir" gibi bir düzen oluşmaz. */
    private fun <T> spread(list: List<T>, artist: (T) -> String, prev0: String?): List<T> {
        val a = list.toMutableList()
        val n = a.map { norm(artist(it)) }.toMutableList()
        fun runBefore(i: Int): Int {           // i'den önce kaç tane aynı sanatçı art arda
            val x = n[i] ?: return 0
            var c = 0; var j = i - 1
            while (j >= 0 && n[j] == x) { c++; j-- }
            if (j < 0 && norm(prev0) == x) c++
            return c
        }
        var i = 0; var guard = 0
        while (i < a.size && guard++ < a.size * 4) {
            if (runBefore(i) < 2) { i++; continue }
            val k = (i + 1 until a.size).firstOrNull { n[it] != n[i] }
            if (k != null) { a[i] = a[k].also { a[k] = a[i] }; n[i] = n[k].also { n[k] = n[i] }; i++; continue }
            // sonda kaldıysa: öne, aynı sanatçının yanına düşmeyecek bir yere yerleştir
            val x = a.removeAt(i); val xn = n.removeAt(i)
            val j = (0..i).firstOrNull { j -> (j == 0 || n[j - 1] != xn) && (j >= n.size || n[j] != xn) && !(j == 0 && norm(prev0) == xn && n.getOrNull(0) == xn) }
            if (j == null) { a.add(i, x); n.add(i, xn); i++ } else { a.add(j, x); n.add(j, xn); i = maxOf(0, j - 2) }
        }
        return a
    }
}

// ============================ Dinleme geçmişi ============================
data class Listen(val key: String, val title: String, val artist: String, val at: Long, val video: Boolean)

object ListenLog {
    private var file: File? = null
    private val cache = ArrayList<Listen>()
    var ver by mutableIntStateOf(0)

    fun init(ctx: Context) {
        file = File(ctx.filesDir, "dinleme.json")
        Thread { load() }.start()
    }

    @Synchronized private fun load() {
        cache.clear()
        try {
            val a = JSONArray(file?.takeIf { it.exists() }?.readText() ?: return)
            for (i in 0 until a.length()) { val o = a.getJSONArray(i); cache += Listen(o.getString(0), o.getString(1), o.getString(2), o.getLong(3), o.optBoolean(4)) }
        } catch (_: Exception) { }
    }

    @Synchronized fun add(key: String, md: MediaMetadata?) {
        val video = md?.mediaType == MediaMetadata.MEDIA_TYPE_VIDEO || isVideoName(key)
        val title = md?.title?.toString()?.takeIf { it.isNotBlank() } ?: File(key).nameWithoutExtension
        cache.add(0, Listen(key, title, md?.artist?.toString() ?: "", System.currentTimeMillis(), video))
        while (cache.size > 3000) cache.removeAt(cache.size - 1)
        val snap = ArrayList(cache)
        Thread { save(snap) }.start()
        ver++
    }

    private fun save(l: List<Listen>) = try {
        val a = JSONArray(); l.forEach { a.put(JSONArray().put(it.key).put(it.title).put(it.artist).put(it.at).put(it.video)) }
        file?.writeText(a.toString())
    } catch (_: Exception) { }

    @Synchronized fun all(): List<Listen> = ArrayList(cache)
    @Synchronized fun clear() { cache.clear(); file?.delete(); ver++ }
}

private fun dayLabel(t: Long): String {
    val c = Calendar.getInstance(); val today = c.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    return when {
        t >= today -> "Bugün"
        t >= today - 86_400_000L -> "Dün"
        else -> SimpleDateFormat("d MMMM EEEE", Locale.forLanguageTag("tr")).format(Date(t))
    }
}

@Composable
fun ListenHistoryScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    var filter by remember { mutableIntStateOf(1) }   // 0 hepsi, 1 müzik, 2 video
    val all = remember(ListenLog.ver) { ListenLog.all() }
    val list = all.filter { filter == 0 || (filter == 1) != it.video }
    val tf = remember { SimpleDateFormat("HH:mm", Locale.ROOT) }
    Page("Dinleme geçmişi", back, actions = { if (all.isNotEmpty()) TextButton(onClick = { ListenLog.clear() }) { Text("Temizle") } }) {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Hepsi", "Müzik", "Video").forEachIndexed { i, n -> FilterChip(filter == i, { filter = i }, label = { Text(n) }) }
                }
            }
            if (list.isEmpty()) item { Empty(Icons.Filled.History, "Henüz geçmiş yok.\nBir şarkıyı 30 saniyeden fazla dinleyince buraya düşer.") }
            var lastDay = ""
            list.forEach { l ->
                val d = dayLabel(l.at)
                if (d != lastDay) { lastDay = d; item(key = "h$d") { Text(d, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp)) } }
                item(key = "${l.key}@${l.at}") {
                    val s = Music.byKey(l.key)
                    Row(Modifier.fillMaxWidth().clickable { playKeys(ctx, listOf(l.key)) }.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        ArtImage(l.key, s?.uri, s?.path ?: l.key.takeIf { it.startsWith("/") }, Modifier.size(48.dp), RoundedCornerShape(10.dp), 128)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(l.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            Text(listOf(l.artist, if (l.video) "Video" else "").filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { " " }, color = Muted, fontSize = 12.sp, maxLines = 1)
                        }
                        Text(tf.format(Date(l.at)), color = Muted, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// ============================ Klasörde sonraki bölüm ============================
/** Aynı klasörde bu videodan sonra gelen videolar (1, 2, 10 doğal sırasıyla). */
fun folderAfter(path: String, max: Int = 100): List<String> {
    val f = File(path); val dir = f.parentFile ?: return emptyList()
    val l = (dir.listFiles() ?: return emptyList()).filter { it.isFile && isVideoName(it.name) }.sortedWith(compareBy(NaturalOrder) { it.name }).map { it.path }
    val i = l.indexOf(f.path)
    return if (i < 0) emptyList() else l.drop(i + 1).take(max)
}

/** Video bitmeden birkaç saniye önce sağ altta "Sıradaki" kartı. */
@Composable
fun UpNextHint(a: PlayerActivity, m: Modifier) {
    Row(m.padding(20.dp).widthIn(max = 320.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xE6141519))
        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp)).clickable { a.playNextNow() }.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.SkipNext, null, tint = Accent)
        Column(Modifier.weight(1f, fill = false).padding(horizontal = 10.dp)) {
            Text("Sıradaki", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(a.ui.nextTitle, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("Şimdi", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

// ============================ Uygulama önde mi ============================
object Fg {
    @Volatile var started = 0
    val visible get() = started > 0
    fun init(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(a: Activity) { started++ }
            override fun onActivityStopped(a: Activity) { started = (started - 1).coerceAtLeast(0) }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
    }
}

/** Bir dosyayı türüne göre açar: şarkıysa müzik çalar, videoysa oynatıcı. */
suspend fun playFile(ctx: Context, path: String) {
    if (isAudioName(path)) {
        val s = Music.byKey(path) ?: withContext(Dispatchers.IO) { Music.load(ctx); Music.byKey(path) }
            ?: File(path).let { f -> Song(-1, android.net.Uri.fromFile(f), f.path, f.nameWithoutExtension, "", "", -1, 0, 0, f.lastModified() / 1000, f.length()) }
        Engine.playSongs(ctx, listOf(s), 0); AppState.showNow = true
    } else PlayerActivity.openKeys(ctx, listOf(path), listOf(File(path).nameWithoutExtension), 0)
}

/** "Bu zaten telefonda" kartı. */
@Composable
fun AlreadyHaveCard(path: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Accent.copy(alpha = 0.14f)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.CheckCircle, null, tint = Accent)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text("Bu zaten telefonda", fontWeight = FontWeight.Bold)
            Text(File(path).name, color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Button(onClick = { scope.launch { playFile(ctx, path) } }) { Text("Aç") }
    }
}
