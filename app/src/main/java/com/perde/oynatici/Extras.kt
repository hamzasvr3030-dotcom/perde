@file:Suppress("UnsafeOptInUsageError")

package com.perde.oynatici

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.RemoteViews
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ============================ Uygulama içi güncelleme ============================
object Updater {
    const val REPO = "hamzasvr3030-dotcom/perde"
    data class Rel(val version: String, val notes: String, val apkUrl: String, val size: Long)

    fun current(ctx: Context): String = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0" } catch (_: Exception) { "0" }

    fun newer(a: String, b: String): Boolean {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }; val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) { val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }; if (d != 0) return d > 0 }
        return false
    }

    suspend fun check(): Rel? = withContext(Dispatchers.IO) {
        try {
            val c = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
            c.setRequestProperty("Accept", "application/vnd.github+json"); c.setRequestProperty("User-Agent", "Kadraj")
            c.connectTimeout = 8000; c.readTimeout = 8000
            if (c.responseCode != 200) return@withContext null
            val o = JSONObject(c.inputStream.bufferedReader().readText())
            val assets = o.getJSONArray("assets")
            var url = ""; var size = 0L
            for (i in 0 until assets.length()) { val a = assets.getJSONObject(i); if (a.getString("name").endsWith(".apk")) { url = a.getString("browser_download_url"); size = a.optLong("size") } }
            if (url.isEmpty()) null else Rel(o.getString("tag_name").removePrefix("v"), o.optString("body"), url, size)
        } catch (_: Exception) { null }
    }

    suspend fun download(ctx: Context, r: Rel, progress: (Float) -> Unit): File? = withContext(Dispatchers.IO) {
        try {
            val f = File(ctx.cacheDir, "update/Kadraj-${r.version}.apk"); f.parentFile?.mkdirs()
            var c = URL(r.apkUrl).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = true; c.setRequestProperty("User-Agent", "Kadraj")
            var hops = 0
            while (c.responseCode in 300..399 && hops++ < 5) { val loc = c.getHeaderField("Location"); c.disconnect(); c = URL(loc).openConnection() as HttpURLConnection }
            val total = c.contentLengthLong.takeIf { it > 0 } ?: r.size
            c.inputStream.use { i -> FileOutputStream(f).use { o ->
                val buf = ByteArray(64 * 1024); var done = 0L
                while (true) { val n = i.read(buf); if (n < 0) break; o.write(buf, 0, n); done += n; if (total > 0) progress(done.toFloat() / total) }
            } }
            f
        } catch (_: Exception) { null }
    }

    /** false: önce "bilinmeyen kaynaklar" izni gerekiyor (ayar ekranı açıldı). */
    fun install(ctx: Context, f: File): Boolean {
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return false
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }
}

// ============================ Ana ekran widget'ı ============================
class PerdeWidget : AppWidgetProvider() {
    companion object {
        const val TOGGLE = "com.perde.oynatici.W_TOGGLE"
        const val NEXT = "com.perde.oynatici.W_NEXT"

        fun update(ctx: Context) {
            try {
                val m = AppWidgetManager.getInstance(ctx)
                val ids = m.getAppWidgetIds(ComponentName(ctx, PerdeWidget::class.java))
                if (ids.isNotEmpty()) m.updateAppWidget(ids, views(ctx))
            } catch (_: Exception) { }
        }

        private fun bc(ctx: Context, a: String, rc: Int) =
            PendingIntent.getBroadcast(ctx, rc, Intent(ctx, PerdeWidget::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        fun views(ctx: Context): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget)
            val p = Engine.player
            val active = p != null && p.mediaItemCount > 0
            val title = if (active) NowState.title else Prefs.lastTitle
            v.setTextViewText(R.id.w_title, title.ifEmpty { "Kadraj" })
            v.setTextViewText(R.id.w_sub, when {
                active && NowState.isMusic -> NowState.artist.ifEmpty { "Müzik" }
                active -> if (p?.isPlaying == true) "Oynatılıyor" else "Duraklatıldı"
                Prefs.lastKey.isNotEmpty() -> "İzlemeye devam et"
                else -> "Video ve müzik oynatıcı"
            })
            v.setImageViewResource(R.id.w_play, if (p?.isPlaying == true) R.drawable.ic_pause else R.drawable.ic_play)
            val open = PendingIntent.getActivity(ctx, 3, Intent(ctx, MainActivity::class.java).putExtra("openNow", active)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            v.setOnClickPendingIntent(R.id.w_root, open)
            if (active) v.setOnClickPendingIntent(R.id.w_play, bc(ctx, TOGGLE, 1))
            else if (Prefs.lastKey.isNotEmpty()) {
                val i = Intent(ctx, PlayerActivity::class.java)
                    .putStringArrayListExtra(PlayerActivity.EXTRA_LIST, arrayListOf(Prefs.lastKey))
                    .putStringArrayListExtra(PlayerActivity.EXTRA_TITLES, arrayListOf(Prefs.lastTitle))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                v.setOnClickPendingIntent(R.id.w_play, PendingIntent.getActivity(ctx, 4, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            } else v.setOnClickPendingIntent(R.id.w_play, open)
            v.setOnClickPendingIntent(R.id.w_next, bc(ctx, NEXT, 2))
            return v
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetManager.updateAppWidget(appWidgetIds, views(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val p = Engine.player
        when (intent.action) {
            TOGGLE -> if (p != null && p.mediaItemCount > 0) { if (p.isPlaying) p.pause() else p.play() }
            NEXT -> if (p != null && p.hasNextMediaItem()) p.seekToNextMediaItem()
        }
        update(context)
    }
}

// ============================ Bölümler (MKV / MP4) ============================
object Chapters {
    fun read(path: String): List<Pair<Long, String>> = try {
        when (File(path).extension.lowercase()) {
            "mkv", "webm", "mka" -> mkv(path)
            "mp4", "m4v", "mov", "m4a" -> mp4(path)
            else -> emptyList()
        }.sortedBy { it.first }
    } catch (_: Exception) { emptyList() }

    private class Ebml(val f: RandomAccessFile) {
        fun id(): Long {
            val b = f.read(); if (b < 0) throw EOFException()
            var len = 1; var mask = 0x80
            while (len <= 4 && (b and mask) == 0) { mask = mask shr 1; len++ }
            if (len > 4) throw IOException("id")
            var v = b.toLong(); repeat(len - 1) { v = (v shl 8) or f.read().toLong() }
            return v
        }
        fun size(): Long {
            val b = f.read(); if (b < 0) throw EOFException()
            var len = 1; var mask = 0x80
            while (len <= 8 && (b and mask) == 0) { mask = mask shr 1; len++ }
            if (len > 8) throw IOException("size")
            var v = (b and (mask - 1)).toLong(); repeat(len - 1) { v = (v shl 8) or f.read().toLong() }
            return if (v == (1L shl (7 * len)) - 1) -1 else v
        }
        fun uint(n: Long): Long { var v = 0L; repeat(n.toInt()) { v = (v shl 8) or f.read().toLong() }; return v }
        fun str(n: Long): String { val b = ByteArray(n.toInt()); f.readFully(b); return String(b, Charsets.UTF_8).trimEnd('\u0000') }
        inline fun children(start: Long, end: Long, block: (id: Long, size: Long, pos: Long) -> Unit) {
            f.seek(start)
            while (f.filePointer < end) {
                val id = id(); val sz = size(); val pos = f.filePointer
                if (sz < 0) return
                block(id, sz, pos)
                f.seek(pos + sz)
            }
        }
    }

    private fun mkv(path: String): List<Pair<Long, String>> = RandomAccessFile(path, "r").use { f ->
        val e = Ebml(f)
        if (e.id() != 0x1A45DFA3L) return emptyList()
        f.seek(f.filePointer + e.size())
        if (e.id() != 0x18538067L) return emptyList()
        val segSize = e.size(); val seg = f.filePointer
        val segEnd = if (segSize < 0) f.length() else minOf(f.length(), seg + segSize)
        var chapPos = -1L
        while (f.filePointer < segEnd) {
            val id = e.id(); val sz = e.size(); val pos = f.filePointer
            when (id) {
                0x1043A770L -> return parseChapters(e, pos, pos + sz)
                0x114D9B74L -> e.children(pos, pos + sz) { sid, ssz, spos ->
                    if (sid == 0x4DBBL) {
                        var target = 0L; var p = -1L
                        e.children(spos, spos + ssz) { cid, csz, _ -> if (cid == 0x53ABL) target = e.uint(csz) else if (cid == 0x53ACL) p = e.uint(csz) }
                        if (target == 0x1043A770L && p >= 0) chapPos = seg + p
                    }
                }
                0x1F43B675L -> {
                    if (chapPos > 0) { f.seek(chapPos); if (e.id() == 0x1043A770L) { val s = e.size(); return parseChapters(e, f.filePointer, f.filePointer + s) } }
                    return emptyList()
                }
            }
            if (sz < 0) return emptyList()
            f.seek(pos + sz)
        }
        emptyList()
    }

    private fun parseChapters(e: Ebml, start: Long, end: Long): List<Pair<Long, String>> {
        val out = ArrayList<Pair<Long, String>>()
        e.children(start, end) { id, sz, pos ->
            if (id == 0x45B9L && out.isEmpty()) e.children(pos, pos + sz) { aid, asz, apos ->
                if (aid == 0xB6L) {
                    var t = 0L; var name = ""
                    e.children(apos, apos + asz) { cid, csz, cpos ->
                        when (cid) {
                            0x91L -> t = e.uint(csz) / 1_000_000
                            0x80L -> if (name.isEmpty()) e.children(cpos, cpos + csz) { did, dsz, _ -> if (did == 0x85L && name.isEmpty()) name = e.str(dsz) }
                        }
                    }
                    out += t to name.ifEmpty { "Bölüm ${out.size + 1}" }
                }
            }
        }
        return out
    }

    private fun mp4(path: String): List<Pair<Long, String>> = RandomAccessFile(path, "r").use { f ->
        fun findAtom(start: Long, end: Long, type: String): Pair<Long, Long>? {
            var p = start
            while (p + 8 <= end) {
                f.seek(p)
                var size = f.readInt().toLong() and 0xFFFFFFFFL
                val t = ByteArray(4).also { f.readFully(it) }.toString(Charsets.ISO_8859_1)
                var header = 8L
                if (size == 1L) { size = f.readLong(); header = 16 } else if (size == 0L) size = end - p
                if (size < header) return null
                if (t == type) return (p + header) to (p + size)
                p += size
            }
            return null
        }
        val moov = findAtom(0, f.length(), "moov") ?: return emptyList()
        val udta = findAtom(moov.first, moov.second, "udta") ?: return emptyList()
        val chpl = findAtom(udta.first, udta.second, "chpl") ?: return emptyList()
        f.seek(chpl.first)
        val version = f.read(); f.skipBytes(3)
        if (version != 0) f.skipBytes(4)
        val n = f.read()
        (0 until n).map {
            val t = f.readLong() / 10_000
            val l = f.read()
            val b = ByteArray(l).also { f.readFully(it) }
            t to String(b, Charsets.UTF_8)
        }
    }
}

// ============================ GIF kodlayıcı ============================
/** Sabit 252 renkli palet + sıralı titreme (dithering) ile hareketli GIF yazar. */
class GifEncoder(out: OutputStream, private val w: Int, private val h: Int, private val delayCs: Int) {
    private val o = BufferedOutputStream(out)
    private val bayer = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

    init {
        o.write("GIF89a".toByteArray())
        short(w); short(h); o.write(0xF7); o.write(0); o.write(0)
        for (i in 0 until 256) {
            if (i < 252) { o.write((i / 42) * 255 / 5); o.write((i / 6 % 7) * 255 / 6); o.write((i % 6) * 255 / 5) }
            else { o.write(0); o.write(0); o.write(0) }
        }
        o.write(byteArrayOf(0x21, 0xFF.toByte(), 0x0B)); o.write("NETSCAPE2.0".toByteArray()); o.write(byteArrayOf(3, 1, 0, 0, 0))
    }

    private fun short(v: Int) { o.write(v and 0xFF); o.write(v shr 8 and 0xFF) }

    fun addFrame(b: Bitmap) {
        val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
        val idx = ByteArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val c = px[y * w + x]; val t = bayer[(y and 3) * 4 + (x and 3)] / 16f - 0.5f
            val r = ((c shr 16 and 0xFF) / 255f * 5 + t).toInt().coerceIn(0, 5)
            val g = ((c shr 8 and 0xFF) / 255f * 6 + t).toInt().coerceIn(0, 6)
            val bl = ((c and 0xFF) / 255f * 5 + t).toInt().coerceIn(0, 5)
            idx[y * w + x] = (r * 42 + g * 6 + bl).toByte()
        }
        addIndexed(idx)
    }

    /** Palet indekslerinden oluşan bir kare yazar (test edilebilir kısım). */
    fun addIndexed(idx: ByteArray) {
        o.write(byteArrayOf(0x21, 0xF9.toByte(), 4, 0)); short(delayCs); o.write(0); o.write(0)
        o.write(0x2C); short(0); short(0); short(w); short(h); o.write(0)
        lzw(idx)
    }

    private fun lzw(data: ByteArray) {
        o.write(8)
        val dict = HashMap<Int, Int>(8192)
        var nBits = 9; var maxCode = 511; var free = 258; var clearFlag = false
        var cur = 0L; var bits = 0
        val block = ByteArray(255); var bn = 0
        fun put(b: Int) { block[bn++] = b.toByte(); if (bn == 255) { o.write(255); o.write(block, 0, 255); bn = 0 } }
        fun output(code: Int) {
            cur = cur or (code.toLong() shl bits); bits += nBits
            while (bits >= 8) { put((cur and 0xFF).toInt()); cur = cur ushr 8; bits -= 8 }
            if (clearFlag) { nBits = 9; maxCode = 511; clearFlag = false }
            else if (free > maxCode) { nBits++; maxCode = if (nBits == 12) 4096 else (1 shl nBits) - 1 }
        }
        output(256)
        var prefix = data[0].toInt() and 0xFF
        for (i in 1 until data.size) {
            val c = data[i].toInt() and 0xFF
            val key = (prefix shl 8) or c
            val v = dict[key]
            if (v != null) { prefix = v; continue }
            output(prefix)
            if (free < 4096) dict[key] = free++
            else { dict.clear(); free = 258; clearFlag = true; output(256) }
            prefix = c
        }
        output(prefix); output(257)
        while (bits > 0) { put((cur and 0xFF).toInt()); cur = cur ushr 8; bits -= 8 }
        if (bn > 0) { o.write(bn); o.write(block, 0, bn) }
        o.write(0)
    }

    fun finish() { o.write(0x3B); o.flush(); o.close() }
}

// ============================ Medya araçları ============================
object MediaTools {
    private fun stamp() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())
    fun picturesDir(): File = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Kadraj").apply { mkdirs() }

    fun saveImage(ctx: Context, b: Bitmap, base: String): File? = try {
        val f = Library.unique(picturesDir(), "${base.take(40)}_${stamp()}.jpg")
        FileOutputStream(f).use { b.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        Library.scan(ctx, f.path); f
    } catch (_: Exception) { null }

    fun frame(r: MediaMetadataRetriever, ms: Long, w: Int, h: Int): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= 27) r.getScaledFrameAtTime(ms * 1000, MediaMetadataRetriever.OPTION_CLOSEST, w, h)
        else r.getFrameAtTime(ms * 1000, MediaMetadataRetriever.OPTION_CLOSEST)?.let { Bitmap.createScaledBitmap(it, w, h, true) }
    } catch (_: Exception) { null }

    /** start–end aralığından GIF üretir (10 kare/sn, 360 px genişlik). */
    suspend fun gif(ctx: Context, path: String, start: Long, end: Long, progress: (Float) -> Unit): File? = withContext(Dispatchers.IO) {
        try {
            val r = MediaMetadataRetriever(); r.setDataSource(path)
            val vw = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 16
            val vh = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 9
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val (sw, sh) = if (rot == 90 || rot == 270) vh to vw else vw to vh
            val w = 360; val h = (360f * sh / sw).toInt().coerceAtLeast(2) / 2 * 2
            val f = Library.unique(picturesDir(), "${File(path).nameWithoutExtension.take(40)}_${stamp()}.gif")
            val enc = GifEncoder(FileOutputStream(f), w, h, 10)
            var t = start; val total = (end - start).coerceAtLeast(1)
            while (t <= end) {
                frame(r, t, w, h)?.let { b -> val s = if (b.width != w || b.height != h) Bitmap.createScaledBitmap(b, w, h, true) else b; enc.addFrame(s) }
                progress((t - start).toFloat() / total); t += 100
            }
            enc.finish(); r.release()
            Library.scan(ctx, f.path); f
        } catch (_: Exception) { null }
    }

    /** Media3 Transformer ile videodan kesit alır (MP4). Ana iş parçacığında çağrılmalı. */
    fun trim(ctx: Context, path: String, start: Long, end: Long, progress: (Int) -> Unit, done: (File?, String?) -> Unit) {
        val out = Library.unique(Library.publicDir(), "${File(path).nameWithoutExtension.take(40)}_kesit_${fmtTime(start).replace(':', '.')}.mp4")
        val item = MediaItem.Builder().setUri(Uri.fromFile(File(path)))
            .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(start).setEndPositionMs(end).build()).build()
        val handler = Handler(Looper.getMainLooper())
        val t = Transformer.Builder(ctx).addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) { Library.scan(ctx, out.path); done(out, null) }
            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                out.delete(); done(null, exportException.errorCodeName)
            }
        }).build()
        try { t.start(EditedMediaItem.Builder(item).build(), out.path) } catch (e: Exception) { done(null, e.message); return }
        val holder = ProgressHolder()
        handler.post(object : Runnable {
            override fun run() {
                val s = t.getProgress(holder)
                if (s == Transformer.PROGRESS_STATE_AVAILABLE) progress(holder.progress)
                if (s != Transformer.PROGRESS_STATE_NOT_STARTED) handler.postDelayed(this, 300)
            }
        })
    }
}

// ============================ Online altyazı (OpenSubtitles) ============================
object OpenSubs {
    private const val API = "https://api.opensubtitles.com/api/v1"
    data class Result(val fileId: Long, val name: String, val lang: String, val downloads: Int)
    private var token: String? = null

    private fun conn(path: String, method: String = "GET"): HttpURLConnection {
        val c = URL(API + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.setRequestProperty("Api-Key", Prefs.osKey.trim())
        c.setRequestProperty("User-Agent", "Kadraj v1.1")
        c.setRequestProperty("Accept", "application/json")
        c.connectTimeout = 10000; c.readTimeout = 15000
        token?.let { c.setRequestProperty("Authorization", "Bearer $it") }
        return c
    }

    private fun body(c: HttpURLConnection): String {
        val ok = c.responseCode in 200..299
        val s = (if (ok) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: ""
        if (!ok) throw IOException(try { JSONObject(s).optString("message", "HTTP ${c.responseCode}") } catch (_: Exception) { "HTTP ${c.responseCode}" })
        return s
    }

    private fun login() {
        if (token != null || Prefs.osUser.isBlank()) return
        val c = conn("/login", "POST"); c.doOutput = true; c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(JSONObject().put("username", Prefs.osUser).put("password", Prefs.osPass).toString().toByteArray()) }
        token = JSONObject(body(c)).optString("token").ifEmpty { null }
    }

    suspend fun search(query: String): List<Result> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query.lowercase(Locale.ROOT), "UTF-8")
        val c = conn("/subtitles?query=$q&languages=${URLEncoder.encode(Prefs.osLangs.lowercase(), "UTF-8")}")
        val data = JSONObject(body(c)).getJSONArray("data")
        (0 until data.length()).mapNotNull { i ->
            val a = data.getJSONObject(i).getJSONObject("attributes")
            val files = a.optJSONArray("files") ?: return@mapNotNull null
            if (files.length() == 0) return@mapNotNull null
            val f = files.getJSONObject(0)
            Result(f.getLong("file_id"), a.optString("release").ifEmpty { f.optString("file_name") }, a.optString("language"), a.optInt("download_count"))
        }
    }

    /** Altyazıyı indirip dest klasörüne yazar. */
    suspend fun download(r: Result, destDir: File, baseName: String): File = withContext(Dispatchers.IO) {
        login()
        val c = conn("/download", "POST"); c.doOutput = true; c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(JSONObject().put("file_id", r.fileId).toString().toByteArray()) }
        val o = JSONObject(body(c))
        val link = o.getString("link")
        val ext = o.optString("file_name").substringAfterLast('.', "srt").lowercase().let { if (it in Subs.EXT) it else "srt" }
        destDir.mkdirs()
        val f = Library.unique(destDir, "$baseName.${r.lang}.$ext")
        (URL(link).openConnection() as HttpURLConnection).inputStream.use { i -> FileOutputStream(f).use { i.copyTo(it) } }
        f
    }
}
