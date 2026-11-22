@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.perde.oynatici

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaPlayer
import android.media.MediaScannerConnection
import android.media.RingtoneManager
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextPaint
import android.text.TextUtils
import androidx.annotation.OptIn as UnstableOptIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.palette.graphics.Palette
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val TRL7 = Locale.forLanguageTag("tr")

// ============================ Ses efektleri: 8D ve yankı ============================
@UnstableOptIn(UnstableApi::class)
class FxProcessor : BaseAudioProcessor() {
    @Volatile var enabled = false          // yalnızca müzikte
    @Volatile var eightD = false
    @Volatile var reverb = 0               // 0 kapalı, 1 oda, 2 salon, 3 konser

    private var sr = 44100; private var ch = 2
    private var t = 0.0
    private class Comb(n: Int) { val b = FloatArray(n); var i = 0; var st = 0f }
    private class All(n: Int) { val b = FloatArray(n); var i = 0 }
    private var combs = Array(2) { arrayOf<Comb>() }
    private var alls = Array(2) { arrayOf<All>() }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        sr = inputAudioFormat.sampleRate; ch = inputAudioFormat.channelCount
        build(); return inputAudioFormat
    }

    private fun build() {
        val k = sr / 44100f
        combs = Array(2) { c -> intArrayOf(1116, 1188, 1277, 1356, 1422, 1491).map { Comb(((it + c * 23) * k).toInt().coerceAtLeast(8)) }.toTypedArray() }
        alls = Array(2) { c -> intArrayOf(556, 441, 341).map { All(((it + c * 23) * k).toInt().coerceAtLeast(8)) }.toTypedArray() }
    }

    override fun onFlush() { build() }

    private fun rev(c: Int, x: Float, fb: Float, damp: Float): Float {
        var s = 0f
        for (cb in combs[c]) {
            val o = cb.b[cb.i]; cb.st = o * (1 - damp) + cb.st * damp
            cb.b[cb.i] = x + cb.st * fb; if (++cb.i >= cb.b.size) cb.i = 0; s += o
        }
        for (a in alls[c]) { val bo = a.b[a.i]; val o = -s + bo; a.b[a.i] = s + bo * 0.5f; if (++a.i >= a.b.size) a.i = 0; s = o }
        return s
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val n = inputBuffer.remaining(); if (n == 0) return
        val out = replaceOutputBuffer(n)
        val rv = reverb
        if (!enabled || (!eightD && rv == 0) || ch > 2) { out.put(inputBuffer); out.flip(); return }
        val (fb, damp, wet) = when (rv) { 1 -> Triple(0.72f, 0.35f, 0.22f); 2 -> Triple(0.81f, 0.3f, 0.3f); 3 -> Triple(0.87f, 0.25f, 0.38f); else -> Triple(0f, 0f, 0f) }
        val src = inputBuffer.order(ByteOrder.nativeOrder())
        val dt = 1.0 / sr
        while (src.remaining() >= 2 * ch) {
            var l = src.short / 32768f
            var r = if (ch == 2) src.short / 32768f else l
            if (rv > 0) {
                val x = (l + r) * 0.5f * 0.04f
                val wl = rev(0, x, fb, damp); val wr = rev(1, x, fb, damp)
                l = l * (1 - wet * 0.4f) + wl * wet; r = r * (1 - wet * 0.4f) + wr * wet
            }
            if (eightD) {
                val th = PI / 4 * (1 + sin(2 * PI * t / 8.0)); t += dt; if (t > 8.0) t -= 8.0
                val m = (l + r) * 0.5f
                l = m * cos(th).toFloat() * 1.25f + l * 0.15f; r = m * sin(th).toFloat() * 1.25f + r * 0.15f
            }
            fun s(v: Float) = (v.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            if (ch == 2) { out.putShort(s(l)); out.putShort(s(r)) } else out.putShort(s((l + r) * 0.5f))
        }
        out.flip()
    }
}

object Fx {
    var eightD by mutableStateOf(Prefs.sp.getBoolean("fx8d", false))
    var reverb by mutableIntStateOf(Prefs.sp.getInt("fxRev", 0))
    fun apply() { Engine.fx.eightD = eightD; Engine.fx.reverb = reverb; Prefs.sp.edit().putBoolean("fx8d", eightD).putInt("fxRev", reverb).apply() }
    val on get() = eightD || reverb > 0
}

@Composable
fun FxContent() {
    Text("Ses efektleri", fontSize = 21.sp, fontWeight = FontWeight.Bold)
    Text("Yalnızca müzikte çalışır. Kulaklıkla en iyi sonucu verir.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surf2).clickable { Fx.eightD = !Fx.eightD; Fx.apply() }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Headphones, null, tint = Accent)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text("8D ses", fontWeight = FontWeight.Bold)
            Text("Ses başının etrafında yavaşça döner", color = Muted, fontSize = 12.sp)
        }
        Switch(Fx.eightD, { Fx.eightD = it; Fx.apply() })
    }
    Text("Yankı", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Kapalı", "Oda", "Salon", "Konser").forEachIndexed { i, t -> Pill(t, Fx.reverb == i) { Fx.reverb = i; Fx.apply() } }
    }
    Spacer(Modifier.height(20.dp))
}

// Çalan ekrandan mevcut zil sesi sayfasını açmak için
object RingReq { var song by mutableStateOf<Song?>(null) }

// ============================ Eşleştirme ve yinelenenler ============================
object SongMatch {
    private val junk = Regex("\\b(official|video|audio|lyrics?|lyric video|music video|hd|4k|klip|resmi|şarkı sözleri|sözleri|remastered|feat\\.?|ft\\.?)\\b")
    fun norm(s: String?): String = (s ?: "").lowercase(TRL7).replace(Regex("\\(.*?\\)|\\[.*?]"), " ").replace(junk, " ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    fun artistNorm(a: String?) = norm(a).takeIf { it.isNotEmpty() && it != "unknown" && it != "bilinmeyen sanatçı" } ?: ""

    /** Bu başlıktaki şarkı telefonda var mı? (Spotify "Sanatçı - Şarkı" veya YouTube başlığı) */
    fun have(songs: List<Song>, title: String, uploader: String?): Boolean {
        val e = " " + norm(title) + " " + norm(uploader) + " "
        return songs.any { s ->
            val t = norm(s.title); if (t.length < 3 || !e.contains(" $t ")) return@any false
            val a = artistNorm(s.artist); a.isEmpty() || a.split(' ').first().let { e.contains(" $it") }
        }
    }

    /** Listede telefonda olmayanların id'leri. Şarkılar bir kez normalize edilir (her öğe için tekrar regex çalışmaz). */
    fun missing(songs: List<Song>, entries: List<Web.Entry>): List<String> {
        // sanatçısı bilinmeyen şarkıda kısa başlık ("aşk", "intro") her şeye uymasın: en az iki kelime ya da 8 harf
        val idx = songs.mapNotNull { s ->
            val t = norm(s.title); val a = artistNorm(s.artist).substringBefore(' ')
            t.takeIf { it.length >= 3 && (a.isNotEmpty() || ' ' in it || it.length >= 8) }?.let { " $it " to a }
        }.distinct()
        return entries.filter { en ->
            val e = " " + norm(en.title) + " " + norm(en.uploader) + " "
            idx.none { (t, a) -> e.contains(t) && (a.isEmpty() || e.contains(" $a")) }
        }.map { it.id }
    }

    /** Aynı şarkının birden çok dosyası: aynı ad, aynı/boş sanatçı, süre farkı < 4 sn. */
    fun duplicates(songs: List<Song>): List<List<Song>> {
        val out = ArrayList<List<Song>>()
        songs.groupBy { norm(it.title) }.filter { it.key.length >= 2 && it.value.size > 1 }.values.forEach { g ->
            val left = g.toMutableList()
            while (left.isNotEmpty()) {
                val a = left.removeAt(0)
                val same = left.filter { b ->
                    kotlin.math.abs(a.duration - b.duration) < 4000 &&
                        (artistNorm(a.artist).isEmpty() || artistNorm(b.artist).isEmpty() || artistNorm(a.artist) == artistNorm(b.artist))
                }
                left.removeAll(same)
                if (same.isNotEmpty()) out += (listOf(a) + same).sortedByDescending { kbps(it) }
            }
        }
        return out.sortedBy { it.first().title.lowercase(TRL7) }
    }

    fun kbps(s: Song) = if (s.duration > 0) (s.size * 8 / s.duration).toInt() else 0
}

@Composable
fun DuplicatesScreen(vm: LibVM, back: () -> Unit) {
    val ctx = LocalContext.current
    val groups = remember(vm.songs) { SongMatch.duplicates(vm.songs) }
    val del = remember(groups) { mutableStateListOf<String>().apply { groups.forEach { g -> addAll(g.drop(1).map { it.key }) } } }
    val all = groups.flatten()
    val size = all.filter { it.key in del }.sumOf { it.size }
    Page("Yinelenen şarkılar", back) {
        if (groups.isEmpty()) { Empty(Icons.Filled.LibraryMusic, "Yinelenen şarkı yok. Aynı şarkının birden çok dosyası bulunmadı."); return@Page }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 110.dp)) {
            item { Text("${groups.size} şarkının birden çok dosyası var. En iyi kaliteli olan tutulur, diğerleri işaretlendi.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp)) }
            items(groups, key = { it.first().key }) { g ->
                Column(Modifier.padding(vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surf).padding(vertical = 6.dp)) {
                    Text(g.first().title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
                    g.forEach { s ->
                        val on = s.key in del
                        Row(Modifier.fillMaxWidth().clickable { if (on) del.remove(s.key) else del.add(s.key) }.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(on, { if (on) del.remove(s.key) else del.add(s.key) })
                            Column(Modifier.weight(1f)) {
                                Text(File(s.path).name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${SongMatch.kbps(s)} kbps · ${fmtSize(s.size)} · ${File(s.path).parentFile?.name ?: ""}", color = Muted, fontSize = 12.sp, maxLines = 1)
                            }
                            if (!on) Text("TUTULUR", color = Color(0xFF3DDC97), fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(end = 8.dp))
                        }
                    }
                }
            }
        }
        if (del.isNotEmpty()) Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
            Button(onClick = {
                val list = all.filter { it.key in del }
                Thread {
                    var n = 0; list.forEach { s -> if (Library.deletePath(ctx, s.path, s.uri)) n++ }
                    Handler(Looper.getMainLooper()).post { toast(ctx, "$n dosya silindi"); vm.reload() }
                }.start()
            }, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5484D))) {
                Icon(Icons.Filled.Delete, null); Text(" ${del.size} dosyayı sil · ${fmtSize(size)}")
            }
        }
    }
}

// ============================ Story kartı ============================
object StoryCard {
    fun share(ctx: Context, key: String, title: String, artist: String) {
        val app = ctx.applicationContext
        toast(ctx, "Kart hazırlanıyor…")
        Thread {
            val s = Music.byKey(key)
            val art = runBlocking { Art.load(app, key, s?.uri, s?.path ?: key.takeIf { it.startsWith("/") }, 900) }
            val W = 1080; val H = 1920
            val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888); val c = Canvas(bmp)
            val pal = art?.let { Palette.from(it).generate() }
            val top = pal?.vibrantSwatch?.rgb ?: pal?.dominantSwatch?.rgb ?: 0xFF3E8BFF.toInt()
            val bottom = pal?.darkMutedSwatch?.rgb ?: pal?.darkVibrantSwatch?.rgb ?: 0xFF101218.toInt()
            c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply { shader = LinearGradient(0f, 0f, 0f, H.toFloat(), top, bottom, Shader.TileMode.CLAMP) })
            c.drawColor(0x40000000)
            val side = 820f; val l = (W - side) / 2; val t = 380f
            val r = RectF(l, t, l + side, t + side)
            val sh = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66000000; setShadowLayer(60f, 0f, 24f, 0x99000000.toInt()) }
            c.drawRoundRect(r, 56f, 56f, sh)
            if (art != null) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                val sc = Bitmap.createScaledBitmap(art, side.toInt(), side.toInt(), true)
                p.shader = android.graphics.BitmapShader(sc, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(android.graphics.Matrix().apply { setTranslate(l, t) }) }
                c.drawRoundRect(r, 56f, 56f, p)
            } else c.drawRoundRect(r, 56f, 56f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF })
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = 68f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
            val title2 = TextUtils.ellipsize(title, tp, W - 160f, TextUtils.TruncateAt.END).toString()
            c.drawText(title2, l, t + side + 130f, tp)
            val ap = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt(); textSize = 46f }
            c.drawText(TextUtils.ellipsize(artist, ap, W - 160f, TextUtils.TruncateAt.END).toString(), l, t + side + 200f, ap)
            val bp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99FFFFFF.toInt(); textSize = 36f; letterSpacing = 0.15f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
            c.drawText("♪ KADRAJ İLE DİNLİYORUM", l, H - 170f, bp)
            val dir = File(app.getExternalFilesDir(null) ?: app.filesDir, "story").apply { mkdirs() }
            val f = File(dir, "kadraj_story.png")
            FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            Handler(Looper.getMainLooper()).post {
                try {
                    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Story olarak paylaş").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) { toast(ctx, "Paylaşılamadı: ${e.message}") }
            }
        }.start()
    }
}

// ============================ Arka planda kesilmesin ============================
/** Müzik çalınca oynatma servisine bağlanır; böylece servis ön planda kalır, ekran kapanınca sistem müziği öldürmez. */
object ServiceLink {
    private var fut: com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.MediaController>? = null
    private var linked: androidx.media3.common.Player? = null

    fun ensure(ctx: Context) {
        val p = Engine.player ?: return
        val f = fut
        if (f != null && linked === p) {
            if (!f.isDone) return
            if (try { f.get().isConnected } catch (_: Exception) { false }) return
        }
        drop()
        linked = p
        val app = ctx.applicationContext
        fut = androidx.media3.session.MediaController.Builder(app, androidx.media3.session.SessionToken(app, android.content.ComponentName(app, PlaybackService::class.java))).buildAsync()
    }

    fun drop() { fut?.let { androidx.media3.session.MediaController.releaseFuture(it) }; fut = null; linked = null }
}

object BgGuard {
    fun ok(ctx: Context): Boolean = try { (ctx.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isIgnoringBatteryOptimizations(ctx.packageName) } catch (_: Exception) { true }
    val xiaomi get() = android.os.Build.MANUFACTURER.lowercase(Locale.ROOT).let { it.contains("xiaomi") || it.contains("redmi") || it.contains("poco") }

    fun request(ctx: Context) {
        try { ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))) }
        catch (_: Exception) { try { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } catch (_: Exception) { } }
    }

    fun autostart(ctx: Context) {
        val tries = listOf(
            Intent().setComponent(android.content.ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
        for (i in tries) try { ctx.startActivity(i); return } catch (_: Exception) { }
    }
}

@Composable
fun BgAskDialog() {
    val ctx = LocalContext.current
    var show by remember { mutableStateOf(!Prefs.sp.getBoolean("bgAsked", false) && !BgGuard.ok(ctx)) }
    if (!show) return
    fun done() { Prefs.sp.edit().putBoolean("bgAsked", true).apply(); show = false }
    AlertDialog(onDismissRequest = { done() }, icon = { Icon(Icons.Filled.BatteryChargingFull, null) },
        title = { Text("Müzik ekran kapanınca kesilmesin") },
        text = { Text("Telefon pil tasarrufu için arka planda çalan uygulamaları kapatabiliyor. Kadraj'ı pil kısıtlamasından çıkarırsan müzik ekran kapalıyken de kesilmez." +
            if (BgGuard.xiaomi) "\n\nXiaomi'de ayrıca \"Otomatik başlatma\"yı da aç." else "") },
        confirmButton = { TextButton(onClick = { done(); BgGuard.request(ctx); if (BgGuard.xiaomi) Handler(Looper.getMainLooper()).postDelayed({ BgGuard.autostart(ctx) }, 400) }) { Text("İzin ver") } },
        dismissButton = { TextButton(onClick = { done() }) { Text("Sonra") } })
}
