package com.perde.oynatici

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File
import java.util.Locale

data class Video(
    val id: Long, val uri: Uri, val path: String, val title: String, val folder: String,
    val duration: Long, val size: Long, val width: Int, val height: Int, val dateAdded: Long, val mime: String?
) {
    val key get() = path
    val isNew get() = !Prefs.played(path) && System.currentTimeMillis() / 1000 - dateAdded < 3 * 86400
}

data class Folder(val path: String, val name: String, val videos: List<Video>) {
    val size get() = videos.sumOf { it.size }
    val pinned get() = path in Prefs.set(Library.PINNED)
}

val VIDEO_EXT = setOf(
    "mp4", "mkv", "webm", "avi", "mov", "3gp", "3g2", "m4v", "ts", "flv", "wmv", "mpg", "mpeg",
    "vob", "m2ts", "mts", "ogv", "divx", "f4v", "asf", "rm", "rmvb"
)
val AUDIO_EXT = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "wma", "amr", "mka")
fun isVideoName(n: String) = n.substringAfterLast('.', "").lowercase(Locale.ROOT) in VIDEO_EXT
fun isAudioName(n: String) = n.substringAfterLast('.', "").lowercase(Locale.ROOT) in AUDIO_EXT

fun fmtTime(ms: Long): String {
    val s = maxOf(0L, ms / 1000)
    val h = s / 3600; val m = s % 3600 / 60; val sec = s % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec) else String.format(Locale.ROOT, "%d:%02d", m, sec)
}
fun fmtDur(ms: Long): String {
    val m = ms / 60000
    return if (m >= 60) "${m / 60} sa ${m % 60} dk" else "$m dk"
}
fun fmtSize(b: Long): String {
    val tr = Locale.forLanguageTag("tr")
    return when {
        b >= 1L shl 30 -> String.format(tr, "%.2f GB", b / (1L shl 30).toDouble())
        b >= 1L shl 20 -> String.format(tr, "%.1f MB", b / (1L shl 20).toDouble())
        b >= 1L shl 10 -> String.format(tr, "%.0f KB", b / 1024.0)
        else -> "$b B"
    }
}
fun resLabel(w: Int, h: Int): String {
    val p = minOf(w, h)
    return when {
        p >= 2160 -> "4K"; p >= 1440 -> "1440p"; p >= 1080 -> "1080p"; p >= 720 -> "720p"
        p >= 480 -> "480p"; p > 0 -> "${p}p"; else -> ""
    }
}
/** "Dizi.Adi.S01E02.1080p.WEB-DL.x264" → "Dizi Adi S01E02" */
fun cleanTitle(name: String): String {
    var s = name.substringBeforeLast('.').replace(Regex("[\\[(].*?[\\])]"), " ").replace('.', ' ').replace('_', ' ')
    s = s.replace(Regex("(?i)\\b(2160p|1080p|720p|480p|x264|x265|h264|h265|hevc|web-?dl|webrip|bluray|brrip|hdrip|dvdrip|aac|ac3|dts|yify|rarbg)\\b.*"), "")
    return s.replace(Regex("\\s+"), " ").trim().ifEmpty { name }
}

object Library {
    const val HIDDEN = "hidF"
    const val PINNED = "pinF"
    @Volatile var cache: List<Video> = emptyList()

    fun load(ctx: Context): List<Video> {
        val out = ArrayList<Video>()
        val base = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(
            MediaStore.Video.Media._ID, MediaStore.Video.Media.DATA, MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION, MediaStore.Video.Media.SIZE, MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT, MediaStore.Video.Media.DATE_ADDED, MediaStore.Video.Media.MIME_TYPE
        )
        val appDir = (ctx.getExternalFilesDir(null) ?: ctx.filesDir).absolutePath
        try {
            ctx.contentResolver.query(base, proj, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val path = c.getString(1) ?: continue
                    if (path.startsWith(appDir)) continue
                    val id = c.getLong(0)
                    val f = File(path)
                    out += Video(
                        id, ContentUris.withAppendedId(base, id), path, c.getString(2) ?: f.name,
                        f.parent ?: "/", c.getLong(3), c.getLong(4), c.getInt(5), c.getInt(6), c.getLong(7), c.getString(8)
                    )
                }
            }
        } catch (_: Exception) { }
        cache = out
        return out
    }

    fun byKey(k: String) = cache.firstOrNull { it.key == k }

    fun folders(videos: List<Video>, includeHidden: Boolean = false): List<Folder> {
        val hidden = Prefs.set(HIDDEN)
        return videos.groupBy { it.folder }.filter { includeHidden || it.key !in hidden }
            .map { (p, v) -> Folder(p, File(p).name.ifEmpty { p }, v) }
    }

    fun sort(list: List<Video>): List<Video> {
        val s = when (Prefs.sortBy) {
            1 -> list.sortedBy { it.dateAdded }
            2 -> list.sortedBy { it.size }
            3 -> list.sortedBy { it.duration }
            else -> list.sortedWith(compareBy(NaturalOrder) { it.title })
        }
        return if (Prefs.sortDesc) s.reversed() else s
    }

    fun sortFolders(list: List<Folder>): List<Folder> {
        var s = when (Prefs.sortBy) {
            1 -> list.sortedBy { f -> f.videos.maxOf { it.dateAdded } }
            2 -> list.sortedBy { it.size }
            3 -> list.sortedBy { f -> f.videos.size }
            else -> list.sortedWith(compareBy(NaturalOrder) { it.name })
        }
        if (Prefs.sortDesc) s = s.reversed()
        val pinned = Prefs.set(PINNED)
        return s.sortedBy { if (it.path in pinned) 0 else 1 }
    }

    fun scan(ctx: Context, vararg paths: String) {
        MediaScannerConnection.scanFile(ctx.applicationContext, paths, null, null)
    }

    fun rename(ctx: Context, v: Video, newBase: String): Boolean {
        val f = File(v.path)
        val ext = f.extension
        val nf = File(f.parentFile, if (ext.isEmpty()) newBase else "$newBase.$ext")
        if (nf.exists() || newBase.isBlank() || newBase.contains('/')) return false
        val subs = Subs.siblings(f)
        val ok = f.renameTo(nf)
        if (ok) {
            subs.forEach { s -> s.renameTo(File(f.parentFile, newBase + s.name.substring(f.nameWithoutExtension.length))) }
            Prefs.moveKey(f.path, nf.path)
            scan(ctx, f.path, nf.path)
        }
        return ok
    }

    /** Silme: ayar açıksa çöp kutusuna, değilse kalıcı. */
    fun deletePath(ctx: Context, path: String, uri: Uri? = null): Boolean {
        val f = File(path)
        val ok = if (Prefs.useTrash) Trash.put(ctx, f) else (f.delete() || !f.exists())
        if (ok) {
            if (uri != null) try { ctx.contentResolver.delete(uri, null, null) } catch (_: Exception) { }
            if (!Prefs.useTrash) Prefs.forget(path)
            scan(ctx, f.path)
        }
        return ok
    }
    fun delete(ctx: Context, v: Video) = deletePath(ctx, v.path, v.uri)

    fun renameFolder(ctx: Context, folder: String, newName: String): Boolean {
        val d = File(folder)
        val nd = File(d.parentFile, newName.trim())
        if (newName.isBlank() || newName.contains('/') || nd.exists()) return false
        val old = (d.listFiles() ?: emptyArray()).filter { it.isFile }.map { it.path }
        if (!d.renameTo(nd)) return false
        old.forEach { o -> Prefs.moveKey(o, File(nd, File(o).name).path) }
        if (folder in Prefs.set(PINNED)) { Prefs.toggleIn(PINNED, folder); Prefs.toggleIn(PINNED, nd.path) }
        scan(ctx, *(old + old.map { File(nd, File(it).name).path }).toTypedArray())
        return true
    }

    fun moveFile(src: File, dest: File): Boolean {
        if (src.renameTo(dest)) return true
        return try {
            src.inputStream().use { i -> dest.outputStream().use { o -> i.copyTo(o, 1 shl 16) } }
            if (dest.length() == src.length()) { src.delete(); true } else { dest.delete(); false }
        } catch (_: Exception) { dest.delete(); false }
    }

    fun unique(dir: File, name: String): File {
        var f = File(dir, name); var n = 1
        val base = name.substringBeforeLast('.'); val ext = name.substringAfterLast('.', "")
        while (f.exists()) { f = File(dir, if (ext.isEmpty()) "$base ($n)" else "$base ($n).$ext"); n++ }
        return f
    }

    /** Videoları başka klasöre taşır ya da kopyalar (altyazılarıyla birlikte). Başarılı sayısını döner. */
    fun transfer(ctx: Context, paths: List<String>, destDir: File, copy: Boolean): Int {
        destDir.mkdirs()
        var ok = 0
        val scanList = ArrayList<String>()
        for (p in paths) {
            val src = File(p)
            if (src.parentFile?.absolutePath == destDir.absolutePath) continue
            val dest = unique(destDir, src.name)
            val done = if (copy) try { src.copyTo(dest); true } catch (_: Exception) { false } else moveFile(src, dest)
            if (done) {
                ok++
                Subs.siblings(src).forEach { s ->
                    val sd = File(destDir, dest.nameWithoutExtension + s.name.substring(src.nameWithoutExtension.length))
                    if (copy) try { s.copyTo(sd) } catch (_: Exception) { } else moveFile(s, sd)
                }
                if (!copy) { Prefs.moveKey(p, dest.path); scanList += p }
                scanList += dest.path
            }
        }
        if (scanList.isNotEmpty()) scan(ctx, *scanList.toTypedArray())
        return ok
    }

    fun privateDir(ctx: Context): File {
        val d = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "Gizli")
        d.mkdirs()
        val nm = File(d, ".nomedia"); if (!nm.exists()) try { nm.createNewFile() } catch (_: Exception) { }
        return d
    }

    fun moveToPrivate(ctx: Context, path: String, uri: Uri? = null): Boolean {
        val src = File(path)
        val dest = unique(privateDir(ctx), src.name)
        val ok = moveFile(src, dest)
        if (ok) {
            if (uri != null) try { ctx.contentResolver.delete(uri, null, null) } catch (_: Exception) { }
            Prefs.moveKey(path, dest.path); scan(ctx, src.path)
        }
        return ok
    }

    fun publicDir(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Perde").apply { mkdirs() }

    fun restoreFromPrivate(ctx: Context, f: File): Boolean {
        val dest = unique(publicDir(), f.name)
        val ok = moveFile(f, dest)
        if (ok) { Prefs.moveKey(f.path, dest.path); scan(ctx, dest.path) }
        return ok
    }

    fun privateList(ctx: Context): List<Video> =
        (privateDir(ctx).listFiles() ?: emptyArray()).filter { it.isFile && (isVideoName(it.name) || isAudioName(it.name)) }.map { f ->
            Video(-1, Uri.fromFile(f), f.path, f.name, f.parent ?: "", probeDuration(f.path), f.length(), 0, 0, f.lastModified() / 1000, null)
        }.sortedBy { it.title.lowercase() }

    fun probeDuration(path: String): Long = try {
        MediaMetadataRetriever().run {
            setDataSource(path)
            val d = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            release(); d
        }
    } catch (_: Exception) { 0L }

    fun displayName(ctx: Context, uri: Uri): String {
        if (uri.scheme == "content") try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0)?.let { n -> return n }
            }
        } catch (_: Exception) { }
        return Uri.decode(uri.lastPathSegment ?: uri.toString()).substringAfterLast('/')
    }

    fun titleOf(ctx: Context, key: String): String =
        byKey(key)?.title ?: Music.byKey(key)?.title ?: if (key.startsWith("/")) File(key).name else displayName(ctx, Uri.parse(key))
}

/** "Bölüm 2" < "Bölüm 10" sıralaması için doğal sıralama. */
object NaturalOrder : Comparator<String> {
    private val tr = java.text.Collator.getInstance(Locale.forLanguageTag("tr")).apply { strength = java.text.Collator.PRIMARY }
    private val rx = Regex("(\\d+)|(\\D+)")
    override fun compare(a: String, b: String): Int {
        val pa = rx.findAll(a).map { it.value }.toList(); val pb = rx.findAll(b).map { it.value }.toList()
        for (i in 0 until minOf(pa.size, pb.size)) {
            val x = pa[i]; val y = pb[i]
            val c = if (x[0].isDigit() && y[0].isDigit()) (x.toBigIntegerOrNull() ?: 0.toBigInteger()).compareTo(y.toBigIntegerOrNull() ?: 0.toBigInteger())
            else tr.compare(x, y)
            if (c != 0) return c
        }
        return pa.size - pb.size
    }
}
