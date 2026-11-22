@file:Suppress("UnsafeOptInUsageError")

package com.perde.oynatici

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.images.ArtworkFactory
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

private fun httpGet(url: String, timeout: Int = 8000): String? = try {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = timeout; c.readTimeout = timeout
    c.setRequestProperty("User-Agent", "Kadraj/1.4 (kisisel video ve muzik oynatici)")
    if (c.responseCode == 200) c.inputStream.bufferedReader().readText() else null
} catch (_: Exception) { null }

private fun md5(s: String) = MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

// ============================ İnternetten şarkı sözü (LRCLIB) ============================
object LyricsNet {
    /** (zaman, satır) — zaman -1 ise sözler zamansız. */
    suspend fun get(ctx: Context, key: String, artist: String, title: String, durationSec: Int): List<Pair<Long, String>> = withContext(Dispatchers.IO) {
        val dir = File(ctx.filesDir, "lyrics").apply { mkdirs() }
        val f = File(dir, md5(key) + ".lrc")
        if (f.exists()) return@withContext parse(f.readText())
        val text = fetchText(artist, title, durationSec)
        if (text.isNotEmpty()) f.writeText(text)
        parse(text)
    }

    /** LRCLIB'den söz metni (zamanlı LRC ya da "#plain" ile başlayan düz metin); bulunamazsa boş. */
    fun fetchText(artist: String, title: String, durationSec: Int): String {
        var (a, t) = artist to title
        if (a.isBlank() || a.startsWith("Bilinmeyen")) {
            Regex("^(.+?)\\s+[-–—]\\s+(.+)$").find(title)?.let { a = it.groupValues[1]; t = it.groupValues[2] }
        }
        t = t.replace(Regex("\\s*[(\\[][^)\\]]*(official|video|audio|lyric|klip|clip|remaster|hd)[^)\\]]*[)\\]]", RegexOption.IGNORE_CASE), "").trim()
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
        var body = httpGet("https://lrclib.net/api/get?artist_name=${enc(a)}&track_name=${enc(t)}" + if (durationSec > 0) "&duration=$durationSec" else "")
        var o = body?.let { try { JSONObject(it) } catch (_: Exception) { null } }
        if (o == null || (o.optString("syncedLyrics").isEmpty() && o.optString("plainLyrics").isEmpty())) {
            val arr = httpGet("https://lrclib.net/api/search?q=${enc("$a $t".trim())}")?.let { try { org.json.JSONArray(it) } catch (_: Exception) { null } }
            if (arr != null) {
                val cands = (0 until arr.length()).map { arr.getJSONObject(it) }
                o = cands.firstOrNull { it.optString("syncedLyrics").isNotEmpty() && (durationSec == 0 || abs(it.optDouble("duration") - durationSec) < 6) }
                    ?: cands.firstOrNull { it.optString("syncedLyrics").isNotEmpty() } ?: cands.firstOrNull { it.optString("plainLyrics").isNotEmpty() }
            }
        }
        return o?.optString("syncedLyrics")?.ifEmpty { null } ?: o?.optString("plainLyrics")?.ifEmpty { null }?.let { "#plain\n$it" } ?: ""
    }

    fun parse(t: String): List<Pair<Long, String>> =
        if (t.startsWith("#plain")) t.removePrefix("#plain").trim().lines().map { -1L to it } else Lrc.parse(t)
}

// ============================ Film / dizi bilgisi ============================
data class MediaMeta(
    val kind: String, val title: String, val year: String?, val summary: String, val poster: String?,
    val genres: String?, val rating: String?, val episode: String?
)

object MetaNet {
    private fun file(ctx: Context, key: String) = File(File(ctx.filesDir, "meta").apply { mkdirs() }, md5(key) + ".json")

    private fun posterFile(ctx: Context, key: String) = File(File(ctx.filesDir, "posters").apply { mkdirs() }, md5(key) + ".jpg")

    /** Afiş telefona inmişse yerel dosya yolunu kullan (internet gerekmez). */
    private fun local(ctx: Context, key: String, m: MediaMeta): MediaMeta {
        val f = posterFile(ctx, key)
        return if (f.exists() && f.length() > 0) m.copy(poster = Uri.fromFile(f).toString()) else m
    }

    /** Afişi bir kere indirip kalıcı olarak saklar. */
    fun savePoster(ctx: Context, key: String, url: String?): Boolean {
        if (url == null || !url.startsWith("http")) return false
        val f = posterFile(ctx, key)
        if (f.exists() && f.length() > 0) return true
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 15000; c.setRequestProperty("User-Agent", "Kadraj/1.4 (kisisel uygulama)")
            if (c.responseCode != 200) return false
            val tmp = File(f.path + ".part")
            c.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
            tmp.renameTo(f)
        } catch (_: Exception) { false }
    }

    /** Daha önce bulunmuş ama afişi henüz inmemiş kayıtlar için. */
    suspend fun ensurePoster(ctx: Context, key: String): Boolean = withContext(Dispatchers.IO) {
        val raw = rawCached(ctx, key) ?: return@withContext false
        val f = posterFile(ctx, key)
        if (f.exists() && f.length() > 0) false else savePoster(ctx, key, raw.poster)
    }

    fun cached(ctx: Context, key: String): MediaMeta? = rawCached(ctx, key)?.let { local(ctx, key, it) }

    private fun rawCached(ctx: Context, key: String): MediaMeta? = try {
        val f = file(ctx, key); if (!f.exists()) null else JSONObject(f.readText()).let { o ->
            if (o.optBoolean("none")) null else MediaMeta(o.getString("k"), o.getString("t"), o.optString("y").ifEmpty { null }, o.optString("s"),
                o.optString("p").ifEmpty { null }, o.optString("g").ifEmpty { null }, o.optString("r").ifEmpty { null }, o.optString("e").ifEmpty { null })
        }
    } catch (_: Exception) { null }

    fun triedBefore(ctx: Context, key: String) = file(ctx, key).exists()

    private fun save(ctx: Context, key: String, m: MediaMeta?) {
        val o = if (m == null) JSONObject().put("none", true) else JSONObject().put("k", m.kind).put("t", m.title).put("y", m.year ?: "")
            .put("s", m.summary).put("p", m.poster ?: "").put("g", m.genres ?: "").put("r", m.rating ?: "").put("e", m.episode ?: "")
        file(ctx, key).writeSafe(o.toString())
    }

    fun forget(ctx: Context, key: String) { file(ctx, key).delete(); posterFile(ctx, key).delete() }

    private val SE = Regex("(?i)\\bS(\\d{1,2})\\s*E(\\d{1,3})\\b|\\b(\\d{1,2})x(\\d{2})\\b")
    private fun strip(h: String) = h.replace(Regex("<[^>]+>"), "").replace("&amp;", "&").replace("&quot;", "\"").trim()

    suspend fun lookup(ctx: Context, key: String, fileName: String, force: Boolean = false): MediaMeta? = withContext(Dispatchers.IO) {
        if (!force) { cached(ctx, key)?.let { return@withContext it }; if (triedBefore(ctx, key)) return@withContext null }
        val res = search(fileName)
        save(ctx, key, res)
        res?.let { savePoster(ctx, key, it.poster); local(ctx, key, it) }
    }

    /** Dosya adından film/dizi bilgisi (önbelleksiz). */
    fun search(fileName: String): MediaMeta? {
        val base = fileName.substringBeforeLast('.')
        val m = SE.find(base)
        return if (m != null) series(base.substring(0, m.range.first), (m.groupValues[1].ifEmpty { m.groupValues[3] }).toInt(), (m.groupValues[2].ifEmpty { m.groupValues[4] }).toInt())
        else film(base)
    }

    fun queryName(s: String): Pair<String, String?> {
        var t = s.replace(Regex("[._]"), " ")
        val year = Regex("\\b(19\\d{2}|20\\d{2})\\b").find(t)?.value
        if (year != null) t = t.substringBefore(year)
        t = cleanTitle("$t.x").replace(Regex("[\\[(].*"), "").trim()
        return t to year
    }

    private fun series(raw: String, s: Int, e: Int): MediaMeta? {
        val (name, _) = queryName(raw)
        if (name.isBlank()) return null
        val o = httpGet("https://api.tvmaze.com/singlesearch/shows?q=${URLEncoder.encode(name, "UTF-8")}&embed=episodes")?.let { JSONObject(it) } ?: return null
        val eps = o.optJSONObject("_embedded")?.optJSONArray("episodes")
        val ep = eps?.let { a -> (0 until a.length()).map { a.getJSONObject(it) }.firstOrNull { it.optInt("season") == s && it.optInt("number") == e } }
        val genres = o.optJSONArray("genres")?.let { g -> (0 until g.length()).joinToString(", ") { g.getString(it) } }
        return MediaMeta("dizi", o.optString("name"), o.optString("premiered").take(4).ifEmpty { null },
            strip(ep?.optString("summary")?.ifEmpty { null } ?: o.optString("summary")),
            o.optJSONObject("image")?.optString("original")?.ifEmpty { null } ?: o.optJSONObject("image")?.optString("medium"),
            genres, o.optJSONObject("rating")?.optDouble("average")?.takeIf { !it.isNaN() }?.let { String.format(java.util.Locale.ROOT, "%.1f", it) },
            "S%02dE%02d".format(s, e) + (ep?.optString("name")?.let { " · $it" } ?: ""))
    }

    private fun wiki(lang: String, q: String): JSONObject? {
        val sr = httpGet("https://$lang.wikipedia.org/w/api.php?action=query&list=search&format=json&srlimit=3&srsearch=${URLEncoder.encode(q, "UTF-8")}")
            ?.let { JSONObject(it).optJSONObject("query")?.optJSONArray("search") } ?: return null
        for (i in 0 until sr.length()) {
            val t = sr.getJSONObject(i).getString("title")
            val sum = httpGet("https://$lang.wikipedia.org/api/rest_v1/page/summary/${URLEncoder.encode(t.replace(' ', '_'), "UTF-8").replace("+", "%20")}")
                ?.let { JSONObject(it) } ?: continue
            val d = sum.optString("description").lowercase()
            if ("film" in d || "dizi" in d || "series" in d || "movie" in d) return sum
        }
        return null
    }

    private fun film(raw: String): MediaMeta? {
        val (name, year) = queryName(raw)
        if (name.length < 2) return null
        val s = wiki("tr", "$name ${year ?: ""} film".trim()) ?: wiki("en", "$name ${year ?: ""} film".trim()) ?: return null
        val desc = s.optString("description")
        return MediaMeta("film", s.optString("title"), year ?: Regex("(19|20)\\d{2}").find(desc)?.value, s.optString("extract"),
            s.optJSONObject("originalimage")?.optString("source") ?: s.optJSONObject("thumbnail")?.optString("source"),
            desc.ifEmpty { null }, null, null)
    }
}

// ============================ Spotify listesi ============================
object Spotify {
    fun isSpotify(u: String) = "open.spotify.com" in u
    data class Track(val title: String, val artist: String, val durationMs: Long)

    /** Spotify'ın gömülü oynatıcı sayfasından liste/albüm adı ve şarkılar (giriş gerekmez). */
    suspend fun read(url: String): Pair<String, List<Track>> = withContext(Dispatchers.IO) {
        val m = Regex("open\\.spotify\\.com/(?:intl-[a-z]+/)?(playlist|album|track)/([A-Za-z0-9]+)").find(url) ?: throw IllegalArgumentException("Spotify bağlantısı tanınmadı")
        val html = httpGet("https://open.spotify.com/embed/${m.groupValues[1]}/${m.groupValues[2]}", 12000) ?: throw IllegalStateException("Spotify sayfası açılamadı")
        val json = Regex("<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1)
            ?: throw IllegalStateException("Spotify listesi okunamadı")
        val e = JSONObject(json).getJSONObject("props").getJSONObject("pageProps").getJSONObject("state").getJSONObject("data").getJSONObject("entity")
        val name = e.optString("name").ifEmpty { e.optString("title") }
        val tl = e.optJSONArray("trackList")
        val tracks = if (tl != null) (0 until tl.length()).map { tl.getJSONObject(it) }.map {
            Track(it.optString("title"), it.optString("subtitle").replace(' ', ' ').split(",").first().trim(), it.optLong("duration"))
        } else listOf(Track(name, e.optJSONArray("artists")?.optJSONObject(0)?.optString("name") ?: e.optString("subtitle"), e.optLong("duration")))
        name to tracks.filter { it.title.isNotBlank() }
    }

    fun searchUrl(t: Track) = "ytsearch1:${t.artist} - ${t.title} audio"
}

// ============================ Ses dalgası (izin gerektirmez) ============================
/** Çalınan sesi oynatıcının içinden dinleyip 32 frekans bandına ayırır. */
object Viz {
    @Volatile var enabled = false
    @Volatile var bands = FloatArray(32)
    private const val N = 1024
    private val buf = FloatArray(N)
    private var fill = 0
    private val re = FloatArray(N); private val im = FloatArray(N)
    private val window = FloatArray(N) { (0.5 - 0.5 * cos(2 * Math.PI * it / (N - 1))).toFloat() }

    fun feed(src: ByteBuffer, channels: Int) {
        if (!enabled) return
        val b = src.duplicate().order(ByteOrder.nativeOrder())
        val step = 2 * channels
        while (b.remaining() >= step) {
            var s = 0f
            for (c in 0 until channels) s += b.short / 32768f
            buf[fill++] = s / channels
            if (fill == N) { analyze(); fill = 0 }
        }
    }

    private fun analyze() {
        for (i in 0 until N) { re[i] = buf[i] * window[i]; im[i] = 0f }
        fft(re, im)
        val out = FloatArray(32)
        for (k in 0 until 32) {
            val lo = (2.0 * Math.pow(256.0, k / 32.0)).toInt().coerceAtLeast(1)
            val hi = (2.0 * Math.pow(256.0, (k + 1) / 32.0)).toInt().coerceAtMost(N / 2).coerceAtLeast(lo + 1)
            var m = 0f
            for (i in lo until hi) m = max(m, sqrt(re[i] * re[i] + im[i] * im[i]))
            out[k] = ((ln(1 + m) / 4f)).toFloat().coerceIn(0f, 1f)
        }
        val old = bands
        bands = FloatArray(32) { i -> if (out[i] > old[i]) out[i] else old[i] * 0.82f + out[i] * 0.18f }
    }

    private fun fft(x: FloatArray, y: FloatArray) {
        val n = x.size
        var j = 0
        for (i in 1 until n) { var bit = n shr 1; while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }; j = j xor bit
            if (i < j) { var t = x[i]; x[i] = x[j]; x[j] = t; t = y[i]; y[i] = y[j]; y[j] = t } }
        var len = 2
        while (len <= n) {
            val ang = -2 * Math.PI / len
            val wr = cos(ang).toFloat(); val wi = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var cr = 1f; var ci = 0f
                for (k in 0 until len / 2) {
                    val ur = x[i + k]; val ui = y[i + k]
                    val vr = x[i + k + len / 2] * cr - y[i + k + len / 2] * ci
                    val vi = x[i + k + len / 2] * ci + y[i + k + len / 2] * cr
                    x[i + k] = ur + vr; y[i + k] = ui + vi; x[i + k + len / 2] = ur - vr; y[i + k + len / 2] = ui - vi
                    val nr = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = nr
                }
                i += len
            }
            len = len shl 1
        }
    }

    fun decay() { bands = FloatArray(32) { bands[it] * 0.85f } }
}

class VizProcessor : BaseAudioProcessor() {
    private var ch = 2
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        ch = inputAudioFormat.channelCount.coerceAtLeast(1)
        return inputAudioFormat
    }
    override fun queueInput(inputBuffer: ByteBuffer) {
        val n = inputBuffer.remaining(); if (n == 0) return
        Viz.feed(inputBuffer, ch)
        val out = replaceOutputBuffer(n); out.put(inputBuffer); out.flip()
    }
}

// ============================ Zil sesi ============================
object Ringtone {
    val TYPES = listOf("Zil sesi" to RingtoneManager.TYPE_RINGTONE, "Bildirim sesi" to RingtoneManager.TYPE_NOTIFICATION, "Alarm sesi" to RingtoneManager.TYPE_ALARM)

    fun dir(type: Int): File = File(Environment.getExternalStoragePublicDirectory(when (type) {
        RingtoneManager.TYPE_NOTIFICATION -> Environment.DIRECTORY_NOTIFICATIONS
        RingtoneManager.TYPE_ALARM -> Environment.DIRECTORY_ALARMS
        else -> Environment.DIRECTORY_RINGTONES
    }), "Kadraj").apply { mkdirs() }

    /** Şarkının bir kısmını kesip m4a olarak kaydeder; bitince Uri döner. Ana iş parçacığında çağrılmalı. */
    fun make(ctx: Context, src: String, name: String, start: Long, end: Long, type: Int, done: (Uri?, String?) -> Unit) {
        val out = Library.unique(dir(type), Dl.safeName(name) + ".m4a")
        val item = MediaItem.Builder().setUri(Uri.fromFile(File(src)))
            .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(start).setEndPositionMs(end).build()).build()
        val t = Transformer.Builder(ctx).addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                MediaScannerConnection.scanFile(ctx, arrayOf(out.path), arrayOf("audio/mp4")) { _, uri ->
                    android.os.Handler(android.os.Looper.getMainLooper()).post { done(uri, null) }
                }
            }
            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) { out.delete(); done(null, exportException.errorCodeName) }
        }).build()
        try { t.start(EditedMediaItem.Builder(item).setRemoveVideo(true).build(), out.path) } catch (e: Exception) { done(null, e.message) }
    }

    fun canSetDefault(ctx: Context) = android.provider.Settings.System.canWrite(ctx)
    fun setDefault(ctx: Context, uri: Uri, type: Int) = try { RingtoneManager.setActualDefaultRingtoneUri(ctx, type, uri); true } catch (_: Exception) { false }
}

// ============================ Etiket düzenleme ============================
object Tags {
    data class Info(val title: String, val artist: String, val album: String, val year: String, val genre: String, val track: String)

    init { try { TagOptionSingleton.getInstance().isAndroid = true } catch (_: Throwable) { } }

    fun read(path: String): Info? = try {
        val t = AudioFileIO.read(File(path)).tagOrCreateDefault
        Info(t.getFirst(FieldKey.TITLE), t.getFirst(FieldKey.ARTIST), t.getFirst(FieldKey.ALBUM), t.getFirst(FieldKey.YEAR), t.getFirst(FieldKey.GENRE), t.getFirst(FieldKey.TRACK))
    } catch (_: Throwable) { null }

    /** cover: null = dokunma, boş dizi = kapağı sil, bayt = yeni kapak */
    fun write(ctx: Context, path: String, i: Info, cover: ByteArray?): String? =
        writeFile(path, i, cover, ctx.cacheDir).also { if (it == null) Library.scan(ctx, path) }

    fun writeFile(path: String, i: Info, cover: ByteArray?, tmpDir: File): String? = try {
        val af = AudioFileIO.read(File(path))
        val t = af.tagOrCreateAndSetDefault
        fun set(k: FieldKey, v: String) { if (v.isBlank()) try { t.deleteField(k) } catch (_: Exception) { } else t.setField(k, v.trim()) }
        set(FieldKey.TITLE, i.title); set(FieldKey.ARTIST, i.artist); set(FieldKey.ALBUM, i.album)
        set(FieldKey.YEAR, i.year); set(FieldKey.GENRE, i.genre)
        if (i.track.toIntOrNull() != null) t.setField(FieldKey.TRACK, i.track.trim()) else try { t.deleteField(FieldKey.TRACK) } catch (_: Exception) { }
        if (cover != null) {
            t.deleteArtworkField()
            if (cover.isNotEmpty()) {
                val tmp = File(tmpDir, "cover.jpg"); tmp.writeBytes(cover)
                t.setField(ArtworkFactory.createArtworkFromFile(tmp)); tmp.delete()
            }
        }
        af.commit()
        null
    } catch (e: Throwable) { e.message ?: e.javaClass.simpleName }

    /** Seçilen resmi kapak için 800 px'e küçültüp JPEG yapar. */
    fun prepareCover(ctx: Context, uri: Uri): ByteArray? = try {
        val b = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: null
        b?.let {
            val s = minOf(1f, 800f / maxOf(it.width, it.height))
            val sc = android.graphics.Bitmap.createScaledBitmap(it, (it.width * s).toInt(), (it.height * s).toInt(), true)
            java.io.ByteArrayOutputStream().also { o -> sc.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, o) }.toByteArray()
        }
    } catch (_: Exception) { null }
}

// ============================ Kopya dosyalar ============================
data class DupItem(val path: String, val size: Long, val title: String, val dateAdded: Long, val audio: Boolean, val uri: Uri?)

object Dupes {
    /** Aynı boyut + aynı baş/son içerik → aynı dosya. */
    suspend fun find(videos: List<Video>, songs: List<Song>, progress: (Float) -> Unit): List<List<DupItem>> = withContext(Dispatchers.IO) {
        val all = videos.map { DupItem(it.path, it.size, it.title, it.dateAdded, false, it.uri) } + songs.map { DupItem(it.path, it.size, it.title, it.dateAdded, true, it.uri) }
        val bySize = all.filter { it.size > 64 * 1024 }.groupBy { it.size }.values.filter { it.size > 1 }
        val out = ArrayList<List<DupItem>>()
        bySize.forEachIndexed { i, g ->
            progress(i.toFloat() / bySize.size.coerceAtLeast(1))
            g.groupBy { sig(it.path) }.forEach { (k, v) -> if (k != null && v.size > 1) out += v.sortedBy { it.dateAdded } }
        }
        out.sortedByDescending { it.first().size * (it.size - 1) }
    }

    private fun sig(path: String): String? = try {
        RandomAccessFile(path, "r").use { f ->
            val md = MessageDigest.getInstance("MD5"); val b = ByteArray(256 * 1024)
            val n1 = f.read(b); md.update(b, 0, maxOf(0, n1))
            if (f.length() > b.size * 2) { f.seek(f.length() / 2); val n2 = f.read(b); md.update(b, 0, maxOf(0, n2)); f.seek(f.length() - b.size); val n3 = f.read(b); md.update(b, 0, maxOf(0, n3)) }
            md.digest().joinToString("") { "%02x".format(it) }
        }
    } catch (_: Exception) { null }
}

// ============================ Depolama ============================
data class StorageInfo(val total: Long, val free: Long, val videos: Long, val music: Long, val trash: Long, val hidden: Long, val cache: Long, val engine: Long)

object StorageScan {
    private fun dirSize(f: File?): Long = if (f == null || !f.exists()) 0 else if (f.isFile) f.length() else (f.listFiles() ?: emptyArray()).sumOf { dirSize(it) }

    suspend fun scan(ctx: Context, videos: List<Video>, songs: List<Song>): StorageInfo = withContext(Dispatchers.IO) {
        val st = StatFs(Environment.getExternalStorageDirectory().path)
        StorageInfo(st.totalBytes, st.availableBytes, videos.sumOf { it.size }, songs.sumOf { it.size },
            dirSize(Trash.dir(ctx)), dirSize(Library.privateDir(ctx)), dirSize(ctx.cacheDir) + dirSize(ctx.externalCacheDir),
            dirSize(File(ctx.noBackupFilesDir, "youtubedl-android")) + dirSize(File(ctx.filesDir, "youtubedl-android")))
    }

    fun clearCache(ctx: Context) {
        ctx.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        ctx.externalCacheDir?.listFiles()?.forEach { it.deleteRecursively() }
    }
}

// ============================ Zamanlanmış indirme ============================
class DlAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Dl.init(context)
        Dl.releaseScheduled(context)
    }
}

object DlAlarm {
    fun schedule(ctx: Context, at: Long) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(ctx, 77, Intent(ctx, DlAlarmReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (_: SecurityException) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) }
    }

    /** Bu gece 02:00 (geçtiyse yarın). */
    fun tonight(hour: Int = 2, minute: Int = 0): Long {
        val c = java.util.Calendar.getInstance()
        c.set(java.util.Calendar.HOUR_OF_DAY, hour); c.set(java.util.Calendar.MINUTE, minute); c.set(java.util.Calendar.SECOND, 0)
        if (c.timeInMillis <= System.currentTimeMillis() + 60_000) c.add(java.util.Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }
}

// ============================ Güncelleme kurulumu ============================
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(android.content.pm.PackageInstaller.EXTRA_STATUS, -999)) {
            android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION") val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { try { context.startActivity(it) } catch (_: Exception) { Updater.lastError = "Kurulum ekranı açılamadı" } }
            }
            android.content.pm.PackageInstaller.STATUS_SUCCESS -> Updater.lastError = null
            else -> Updater.lastError = "Kurulum başarısız: " + (intent.getStringExtra(android.content.pm.PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "bilinmeyen hata")
        }
    }
}
