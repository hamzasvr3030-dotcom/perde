@file:OptIn(ExperimentalMaterial3Api::class)

package com.perde.oynatici

import android.content.ComponentName
import android.content.Context
import android.database.ContentObserver
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// ============================ Yeni medya algılama ============================
/** Telefona yeni video/şarkı gelince (WhatsApp, kamera, indirme) kütüphaneyi yeniletir. */
object MediaWatch {
    var tick by mutableIntStateOf(0)
    private var registered = false
    fun start(ctx: Context) {
        if (registered) return
        registered = true
        val h = Handler(Looper.getMainLooper())
        val obs = object : ContentObserver(h) {
            override fun onChange(selfChange: Boolean) { tick++ }
        }
        try {
            ctx.contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, obs)
            ctx.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, obs)
        } catch (e: Exception) { Report.err("medya izleyici", e) }
    }
}

// ============================ Son müzik kuyruğu + kulaklık ============================
object LastQueue {
    fun save(keys: List<String>, index: Int, pos: Long) {
        if (keys.isEmpty()) return
        Prefs.sp.edit().putString("lastQueue", JSONObject().put("k", JSONArray(keys.take(500))).put("i", index).put("p", pos).toString()).apply()
    }
    fun load(): Triple<List<String>, Int, Long>? = try {
        val o = JSONObject(Prefs.sp.getString("lastQueue", null) ?: return null)
        val a = o.getJSONArray("k"); Triple((0 until a.length()).map { a.getString(it) }, o.optInt("i"), o.optLong("p"))
    } catch (_: Exception) { null }
}

/**
 * Arama gelince (çalarken, telefon ya da WhatsApp gibi uygulama araması) çalan şarkı/video durur;
 * arama yüzünden durduysa arama bitince kaldığı yerden sürer.
 * Android 12+: ses modu dinlenir (izin gerekmez). Daha eskisi: telefon durumu dinlenir (bu sürümlerde izin gerekmez).
 */
object CallPause {
    private var started = false
    private var pausedByCall = false
    var calling = false; private set

    private fun onCall(active: Boolean, why: String) {
        if (active) {
            if (!calling) { calling = true; if (Headset.pauseNow(why)) pausedByCall = true }
        } else if (calling) {
            calling = false
            if (pausedByCall) {
                pausedByCall = false
                // arama sesi kapanırken hoparlöre kısa süre taşmasın
                Handler(Looper.getMainLooper()).postDelayed({ Engine.player?.let { if (!it.playWhenReady && it.mediaItemCount > 0) { it.play(); Report.log("duraklatma", "arama bitti, devam") } } }, 1200)
            }
        }
    }
    private fun inCall(mode: Int) = mode == AudioManager.MODE_RINGTONE || mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION

    fun start(app: Context) {
        if (started) return; started = true
        val main = Handler(Looper.getMainLooper())
        try {
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                am.addOnModeChangedListener({ r -> main.post(r) }) { mode -> onCall(inCall(mode), "arama (ses modu $mode)") }
            } else {
                val tm = app.getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
                main.post {
                    @Suppress("DEPRECATION")
                    tm.listen(object : android.telephony.PhoneStateListener() {
                        @Deprecated("eski API") override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                            onCall(state != android.telephony.TelephonyManager.CALL_STATE_IDLE, "arama")
                        }
                    }, android.telephony.PhoneStateListener.LISTEN_CALL_STATE)
                }
            }
        } catch (e: Exception) { Report.err("arama dinleyici", e) }
    }
}

/** Kulaklık/Bluetooth takılınca son çalan müziği kaldığı yerden sürdürür. */
object Headset {
    private var since = 0L
    private var controller: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private val OUT = setOf(AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_USB_HEADSET, 26 /* TYPE_BLE_HEADSET */, 27 /* TYPE_BLE_SPEAKER */)

    fun start(ctx: Context) {
        val app = ctx.applicationContext
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        since = System.currentTimeMillis()
        am.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) {
                // kayıt anında mevcut cihazlar da bildirilir; onları yok say
                if (System.currentTimeMillis() - since < 2000) return
                if (!Prefs.headsetResume) return
                if (added.none { it.isSink && it.type in OUT }) return
                Handler(Looper.getMainLooper()).postDelayed({ resume(app) }, 800)
            }

            // Bluetooth/kablolu kulaklık çıkınca hoparlörden çalmaya devam etmesin (bazı telefonlar "noisy" yayınını göndermiyor)
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
                if (removed.none { it.isSink && it.type in OUT }) return
                if (CallPause.calling) return   // aramada ses yolu değişir; aramadan sonra devam etsin
                pauseNow("kulaklık/Bluetooth ayrıldı")
            }
        }, Handler(Looper.getMainLooper()))
        CallPause.start(app)
    }

    /** Çalan şarkı ya da video ne ise durdurur. */
    fun pauseNow(why: String): Boolean {
        val p = Engine.player ?: return false
        if (!p.playWhenReady) return false
        p.pause(); Report.log("duraklatma", why); return true
    }

    private fun bind(ctx: Context) {
        if (controller == null) controller = MediaController.Builder(ctx, SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))).buildAsync()
    }

    fun resume(ctx: Context) {
        val p = Engine.player
        if (p != null && p.isPlaying) return
        if (p != null && p.mediaItemCount > 0 && NowState.isMusic) { bind(ctx); p.play(); Report.log("kulaklık", "devam: ${NowState.title}"); return }
        val q = LastQueue.load() ?: return
        Thread {
            if (Music.cache.isEmpty()) Music.load(ctx)
            val songs = q.first.mapNotNull { Music.byKey(it) }
            if (songs.isEmpty()) return@Thread
            Handler(Looper.getMainLooper()).post {
                val idx = songs.indexOfFirst { it.key == q.first.getOrNull(q.second) }.coerceAtLeast(0)
                Engine.playSongs(ctx, songs, idx)
                if (q.third > 3000) Engine.player?.seekTo(idx, q.third)
                bind(ctx)
                Report.log("kulaklık", "son liste sürdürüldü (${songs.size} şarkı)")
            }
        }.start()
    }
}

// ============================ Ses ve parlaklığı hatırlama ============================
object Levels {
    fun saveVol(k: String, v: Float) { if (Prefs.rememberLevels) Prefs.sp.edit().putFloat("vol:$k", v).apply() }
    fun saveBright(k: String, v: Float) { if (Prefs.rememberLevels) Prefs.sp.edit().putFloat("br:$k", v).apply() }
    fun vol(k: String): Float? = if (Prefs.rememberLevels && Prefs.sp.contains("vol:$k")) Prefs.sp.getFloat("vol:$k", 0.5f) else null
    fun bright(k: String): Float? = if (Prefs.rememberLevels && Prefs.sp.contains("br:$k")) Prefs.sp.getFloat("br:$k", 0.5f) else null
}

// ============================ Yer açma ============================
data class CleanItem(val v: Video, val reason: String)

object Cleanup {
    /** İzlenmiş (bitirilmiş) ve 30 günden eski videolar; favoriler hariç. */
    fun candidates(videos: List<Video>, days: Int = 30): List<CleanItem> {
        val fav = Favs.all()
        val cutoff = System.currentTimeMillis() / 1000 - days * 86400L
        return videos.filter { it.key !in fav && it.dateAdded < cutoff }.mapNotNull { v ->
            val d = Prefs.dur(v.key); val p = Prefs.pos(v.key)
            when {
                d > 0 && p >= d - 5000 -> CleanItem(v, "İzlendi")
                d > 0 && p > d * 0.9 -> CleanItem(v, "%90 izlendi")
                else -> null
            }
        }.sortedByDescending { it.v.size }
    }

    fun lowSpace(): Boolean = try { val s = StatFs(Environment.getExternalStorageDirectory().path); s.availableBytes < s.totalBytes * 0.12 } catch (_: Exception) { false }
}

@Composable
fun CleanupScreen(vm: LibVM, back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var days by remember { mutableIntStateOf(30) }
    val items = remember(vm.videos, vm.ver, days) { Cleanup.candidates(vm.videos, days) }
    val picked = remember(items) { mutableStateListOf<String>().apply { addAll(items.map { it.v.key }) } }
    var confirm by remember { mutableStateOf(false) }
    val total = items.filter { it.v.key in picked }.sumOf { it.v.size }
    Page("Yer aç", back) {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(AccentDeep, Surf))).padding(18.dp)) {
                    Text(fmtSize(total), fontSize = 34.sp, fontWeight = FontWeight.Black)
                    Text("${picked.size} video silinerek açılabilir", color = Muted)
                    Text("Sonuna kadar izlediğin ve $days günden eski videolar. Favoriler hiç seçilmez.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(7, 30, 90).forEach { d -> FilterChip(days == d, { days = d }, label = { Text("$d gün") }) }
                    }
                    Button(onClick = { confirm = true }, enabled = picked.isNotEmpty(), modifier = Modifier.padding(top = 12.dp).fillMaxWidth().height(50.dp)) {
                        Icon(Icons.Filled.CleaningServices, null); Spacer(Modifier.width(8.dp)); Text("${fmtSize(total)} yer aç")
                    }
                }
            }
            if (items.isEmpty()) item { Empty(Icons.Filled.CheckCircle, "Silinebilecek izlenmiş eski video yok.") }
            items(items, key = { it.v.key }) { c ->
                Row(Modifier.fillMaxWidth().clickable { if (c.v.key in picked) picked.remove(c.v.key) else picked.add(c.v.key) }.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(c.v.key in picked, { if (it) picked.add(c.v.key) else picked.remove(c.v.key) })
                    Thumb(c.v, Modifier.size(96.dp, 54.dp), showNew = false)
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(c.v.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                        Text("${c.reason} · ${File(c.v.folder).name}", color = Muted, fontSize = 12.sp)
                    }
                    Text(fmtSize(c.v.size), fontWeight = FontWeight.Bold, color = Accent)
                }
            }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("${picked.size} video silinsin mi?") },
        text = { Text(if (Prefs.useTrash) "Önce çöp kutusuna gider; 30 gün içinde geri alabilirsin. Yer hemen açılsın istersen sonra çöpü boşalt." else "Videolar kalıcı olarak silinecek.") },
        confirmButton = { TextButton(onClick = {
            confirm = false
            val list = items.filter { it.v.key in picked }.map { it.v }
            scope.launch { val n = withContext(Dispatchers.IO) { list.count { Library.delete(ctx, it) } }; toast(ctx, "$n video silindi"); vm.reload() }
        }) { Text("Sil", color = Color(0xFFFF6B6B)) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Vazgeç") } })
}

/** Ana sayfada: yer azaldıysa öneri kartı. */
@Composable
fun LowSpaceCard(vm: LibVM, go: (Route) -> Unit) {
    val low = remember(vm.ver) { Cleanup.lowSpace() }
    val size = remember(vm.videos, vm.ver) { Cleanup.candidates(vm.videos).sumOf { it.v.size } }
    if (!low || size < 200L * 1024 * 1024) return
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0x33FF8A3D))
        .clickable { go(Route.CleanupR) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Warning, null, tint = Color(0xFFFFB020))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text("Telefonda yer azaldı", fontWeight = FontWeight.Bold)
            Text("İzlediğin eski videoları silerek ${fmtSize(size)} açabilirsin", color = Muted, fontSize = 13.sp)
        }
        Text("Yer aç", color = Accent, fontWeight = FontWeight.Bold)
    }
}
