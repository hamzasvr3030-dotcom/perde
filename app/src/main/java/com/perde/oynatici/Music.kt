package com.perde.oynatici

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class Song(
    val id: Long, val uri: Uri, val path: String, val title: String, val artist: String, val album: String,
    val albumId: Long, val duration: Long, val track: Int, val dateAdded: Long, val size: Long
) {
    val key get() = path
    val folder get() = File(path).parent ?: ""
    val artUri: Uri get() = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
}

data class Album(val id: Long, val name: String, val artist: String, val songs: List<Song>)
data class Artist(val name: String, val songs: List<Song>) { val albums get() = songs.map { it.albumId }.distinct().size }

object Music {
    @Volatile var cache: List<Song> = emptyList()

    fun load(ctx: Context): List<Song> {
        val out = ArrayList<Song>()
        val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK, MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.SIZE
        )
        val appDir = (ctx.getExternalFilesDir(null) ?: ctx.filesDir).absolutePath
        try {
            ctx.contentResolver.query(base, proj, "${MediaStore.Audio.Media.DURATION} >= 20000", null, null)?.use { c ->
                while (c.moveToNext()) {
                    val path = c.getString(1) ?: continue
                    if (path.startsWith(appDir)) continue
                    val id = c.getLong(0)
                    fun clean(s: String?, d: String) = s?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: d
                    out += Song(
                        id, ContentUris.withAppendedId(base, id), path, clean(c.getString(2), File(path).nameWithoutExtension),
                        clean(c.getString(3), "Bilinmeyen sanatçı"), clean(c.getString(4), "Bilinmeyen albüm"), c.getLong(5),
                        c.getLong(6), c.getInt(7) % 1000, c.getLong(8), c.getLong(9)
                    )
                }
            }
        } catch (_: Exception) { }
        cache = out.sortedWith(compareBy(NaturalOrder) { it.title })
        return cache
    }

    fun byKey(k: String) = cache.firstOrNull { it.key == k }

    fun albums(l: List<Song>) = l.groupBy { it.albumId }.map { (id, s) ->
        Album(id, s.first().album, s.groupingBy { it.artist }.eachCount().maxByOrNull { it.value }?.key ?: "", s.sortedWith(compareBy({ it.track }, { it.title })))
    }.sortedWith(compareBy(NaturalOrder) { it.name })

    fun artists(l: List<Song>) = l.groupBy { it.artist }.map { (n, s) -> Artist(n, s.sortedWith(compareBy({ it.album }, { it.track }))) }
        .sortedWith(compareBy(NaturalOrder) { it.name })

    fun folders(l: List<Song>) = l.groupBy { it.folder }.toList().sortedWith(compareBy(NaturalOrder) { File(it.first).name })

    fun item(s: Song): MediaItem = MediaItem.Builder().setUri(Uri.fromFile(File(s.path))).setMediaId(s.key)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(s.title).setArtist(s.artist).setAlbumTitle(s.album)
            .setArtworkUri(s.artUri).setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).build()).build()
}

/** Albüm kapağı yükleyici (önbellekli). */
object Art {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val miss = HashSet<String>()

    suspend fun load(ctx: Context, key: String, uri: Uri?, path: String?, px: Int = 512): Bitmap? {
        val ck = "$key@$px"
        cache.get(ck)?.let { return it }
        if (ck in miss) return null
        val b = withContext(Dispatchers.IO) {
            var bmp: Bitmap? = null
            if (uri != null && Build.VERSION.SDK_INT >= 29) bmp = try { ctx.contentResolver.loadThumbnail(uri, Size(px, px), null) } catch (_: Exception) { null }
            if (bmp == null && path != null) bmp = try {
                MediaMetadataRetriever().run {
                    setDataSource(path)
                    val pic = embeddedPicture
                    release()
                    pic?.let {
                        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(it, 0, it.size, o)
                        var s = 1; while (o.outWidth / (s * 2) >= px) s *= 2
                        BitmapFactory.decodeByteArray(it, 0, it.size, BitmapFactory.Options().apply { inSampleSize = s })
                    }
                }
            } catch (_: Exception) { null }
            bmp
        }
        if (b != null) cache.put(ck, b) else synchronized(miss) { miss += ck }
        return b
    }

    suspend fun song(ctx: Context, s: Song, px: Int = 512) = load(ctx, s.key, s.uri, s.path, px)

    fun invalidate(key: String) {
        cache.snapshot().keys.filter { it.startsWith("$key@") }.forEach { cache.remove(it) }
        synchronized(miss) { miss.removeAll { it.startsWith("$key@") } }
    }
}

/** .lrc şarkı sözü dosyaları: [mm:ss.xx] satır */
object Lrc {
    fun find(audioPath: String): File? {
        val f = File(audioPath)
        val cand = File(f.parentFile, f.nameWithoutExtension + ".lrc")
        if (cand.exists()) return cand
        return f.parentFile?.listFiles()?.firstOrNull { it.extension.equals("lrc", true) && it.nameWithoutExtension.equals(f.nameWithoutExtension, true) }
    }

    private val tag = Regex("\\[(\\d+):(\\d{1,2})(?:[.:](\\d{1,3}))?]")

    fun parse(text: String): List<Pair<Long, String>> {
        val out = ArrayList<Pair<Long, String>>()
        val offset = Regex("\\[offset:\\s*([+-]?\\d+)\\s*]", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        text.lineSequence().forEach { line ->
            val tags = tag.findAll(line).toList()
            if (tags.isEmpty()) return@forEach
            val content = line.substring(tags.last().range.last + 1).trim()
            tags.forEach { m ->
                val frac = m.groupValues[3]
                val ms = when (frac.length) { 0 -> 0L; 1 -> frac.toLong() * 100; 2 -> frac.toLong() * 10; else -> frac.take(3).toLong() }
                out += (m.groupValues[1].toLong() * 60000 + m.groupValues[2].toLong() * 1000 + ms - offset) to content
            }
        }
        return out.sortedBy { it.first }
    }

    fun load(audioPath: String): List<Pair<Long, String>> = find(audioPath)?.let { parse(Subs.decode(it.readBytes(), 0)) } ?: emptyList()
}

/** Şu an çalan öğenin, arayüzlerin izleyebileceği durumu. */
object NowState {
    var ver by mutableIntStateOf(0)
    var hasItem by mutableStateOf(false)
    var isMusic by mutableStateOf(false)
    var playing by mutableStateOf(false)
    var title by mutableStateOf("")
    var artist by mutableStateOf("")
    var key by mutableStateOf("")

    fun refresh() {
        val p = Engine.player
        val item = p?.currentMediaItem
        hasItem = item != null
        isMusic = item != null && (item.mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_MUSIC || Engine.audioOnly)
        playing = p?.isPlaying == true
        title = item?.mediaMetadata?.title?.toString() ?: ""
        artist = item?.mediaMetadata?.artist?.toString() ?: ""
        key = item?.mediaId ?: ""
        ver++
    }
}
