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
}

val VIDEO_EXT = setOf(
    "mp4", "mkv", "webm", "avi", "mov", "3gp", "3g2", "m4v", "ts", "flv", "wmv", "mpg", "mpeg",
    "vob", "m2ts", "mts", "ogv", "divx", "f4v", "asf", "rm", "rmvb"
)
fun isVideoName(n: String) = n.substringAfterLast('.', "").lowercase(Locale.ROOT) in VIDEO_EXT

fun fmtTime(ms: Long): String {
    val s = maxOf(0L, ms / 1000)
    val h = s / 3600; val m = s % 3600 / 60; val sec = s % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec) else String.format(Locale.ROOT, "%d:%02d", m, sec)
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

object Library {
    @Volatile var cache: List<Video> = emptyList()

    fun load(ctx: Context): List<Video> {
        val out = ArrayList<Video>()
        val base = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(
            MediaStore.Video.Media._ID, MediaStore.Video.Media.DATA, MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION, MediaStore.Video.Media.SIZE, MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT, MediaStore.Video.Media.DATE_ADDED, MediaStore.Video.Media.MIME_TYPE
        )
        val priv = privateDir(ctx).absolutePath
        try {
            ctx.contentResolver.query(base, proj, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val path = c.getString(1) ?: continue
                    if (path.startsWith(priv)) continue
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

    fun folders(videos: List<Video>): List<Folder> =
        videos.groupBy { it.folder }.map { (p, v) -> Folder(p, File(p).name.ifEmpty { p }, v) }

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
        val s = when (Prefs.sortBy) {
            1 -> list.sortedBy { f -> f.videos.maxOf { it.dateAdded } }
            2 -> list.sortedBy { it.size }
            3 -> list.sortedBy { f -> f.videos.size }
            else -> list.sortedWith(compareBy(NaturalOrder) { it.name })
        }
        return if (Prefs.sortDesc) s.reversed() else s
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
            Prefs.forget(v.key)
            scan(ctx, f.path, nf.path)
        }
        return ok
    }

    fun delete(ctx: Context, v: Video): Boolean {
        val f = File(v.path)
        val ok = f.delete() || !f.exists()
        if (ok) {
            try { ctx.contentResolver.delete(v.uri, null, null) } catch (_: Exception) { }
            Prefs.forget(v.key)
            scan(ctx, f.path)
        }
        return ok
    }

    fun privateDir(ctx: Context): File {
        val d = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "Gizli")
        d.mkdirs()
        val nm = File(d, ".nomedia"); if (!nm.exists()) try { nm.createNewFile() } catch (_: Exception) { }
        return d
    }

    private fun moveFile(src: File, dest: File): Boolean {
        if (src.renameTo(dest)) return true
        return try {
            src.inputStream().use { i -> dest.outputStream().use { o -> i.copyTo(o, 1 shl 16) } }
            if (dest.length() == src.length()) { src.delete(); true } else { dest.delete(); false }
        } catch (_: Exception) { dest.delete(); false }
    }

    private fun unique(dir: File, name: String): File {
        var f = File(dir, name); var n = 1
        val base = name.substringBeforeLast('.'); val ext = name.substringAfterLast('.', "")
        while (f.exists()) { f = File(dir, if (ext.isEmpty()) "$base ($n)" else "$base ($n).$ext"); n++ }
        return f
    }

    fun moveToPrivate(ctx: Context, v: Video): Boolean {
        val src = File(v.path)
        val ok = moveFile(src, unique(privateDir(ctx), src.name))
        if (ok) { try { ctx.contentResolver.delete(v.uri, null, null) } catch (_: Exception) { }; scan(ctx, src.path) }
        return ok
    }

    fun publicDir(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Perde").apply { mkdirs() }

    fun restoreFromPrivate(ctx: Context, f: File): Boolean {
        val dest = unique(publicDir(), f.name)
        val ok = moveFile(f, dest)
        if (ok) scan(ctx, dest.path)
        return ok
    }

    fun privateList(ctx: Context): List<Video> =
        (privateDir(ctx).listFiles() ?: emptyArray()).filter { it.isFile && isVideoName(it.name) }.map { f ->
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
