package com.perde.oynatici

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Favoriler (video ve şarkı ortak). */
object Favs {
    const val K = "fav"
    fun has(k: String) = k in Prefs.set(K)
    fun toggle(k: String) = Prefs.toggleIn(K, k)
    fun all() = Prefs.set(K)
}

data class Playlist(val id: Long, val name: String, val items: List<String>)

/** İzleme / çalma listeleri (video ve şarkı yolları). */
object Playlists {
    fun all(): List<Playlist> = try {
        val a = JSONArray(Prefs.playlists)
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i); val it = o.getJSONArray("items")
            Playlist(o.getLong("id"), o.getString("name"), (0 until it.length()).map { j -> it.getString(j) })
        }
    } catch (_: Exception) { emptyList() }

    private fun save(l: List<Playlist>) {
        val a = JSONArray()
        l.forEach { p -> a.put(JSONObject().put("id", p.id).put("name", p.name).put("items", JSONArray(p.items))) }
        Prefs.playlists = a.toString()
    }
    fun get(id: Long) = all().firstOrNull { it.id == id }
    fun create(name: String, items: List<String> = emptyList()): Playlist {
        val p = Playlist(System.currentTimeMillis(), name.ifBlank { "Yeni liste" }, items.distinct())
        save(all() + p); return p
    }
    fun add(id: Long, keys: List<String>) = save(all().map { if (it.id == id) it.copy(items = (it.items + keys).distinct()) else it })
    fun setItems(id: Long, items: List<String>) = save(all().map { if (it.id == id) it.copy(items = items) else it })
    fun remove(id: Long, key: String) = save(all().map { if (it.id == id) it.copy(items = it.items - key) else it })
    fun move(id: Long, from: Int, to: Int) = save(all().map {
        if (it.id != id) it else it.copy(items = it.items.toMutableList().apply { add(to.coerceIn(0, size - 1), removeAt(from)) })
    })
    fun rename(id: Long, name: String) = save(all().map { if (it.id == id) it.copy(name = name) else it })
    fun delete(id: Long) = save(all().filter { it.id != id })
    fun renameKey(old: String, new: String) {
        val l = all(); if (l.none { old in it.items }) return
        save(l.map { p -> p.copy(items = p.items.map { if (it == old) new else it }) })
    }
}

data class Bookmark(val t: Long, val label: String)

/** Video içi yer imleri. */
object Bookmarks {
    fun get(k: String): List<Bookmark> = try {
        val a = JSONArray(Prefs.sp.getString("bm:$k", "[]"))
        (0 until a.length()).map { a.getJSONObject(it).let { o -> Bookmark(o.getLong("t"), o.optString("l")) } }.sortedBy { it.t }
    } catch (_: Exception) { emptyList() }
    private fun put(k: String, l: List<Bookmark>) {
        val a = JSONArray(); l.sortedBy { it.t }.forEach { a.put(JSONObject().put("t", it.t).put("l", it.label)) }
        Prefs.sp.edit().apply { if (l.isEmpty()) remove("bm:$k") else putString("bm:$k", a.toString()) }.apply()
    }
    fun add(k: String, b: Bookmark) = put(k, get(k).filter { kotlin.math.abs(it.t - b.t) > 1000 } + b)
    fun remove(k: String, t: Long) = put(k, get(k).filter { it.t != t })
}

/** İzleme istatistikleri: gün başına ve video başına süre. */
object Stats {
    private val fmt = SimpleDateFormat("yyyyMMdd", Locale.ROOT)
    private var pendMs = 0L
    private var pendKey: String? = null

    @Synchronized fun tick(key: String?, ms: Long) {
        if (key == null) return
        if (pendKey != null && pendKey != key) flush()
        pendKey = key; pendMs += ms
        if (pendMs >= 15_000) flush()
    }

    @Synchronized fun flush() {
        val k = pendKey ?: return
        if (pendMs <= 0) return
        val day = "st:" + fmt.format(Date())
        Prefs.sp.edit()
            .putLong(day, Prefs.sp.getLong(day, 0) + pendMs)
            .putLong("sv:$k", Prefs.sp.getLong("sv:$k", 0) + pendMs)
            .apply()
        pendMs = 0
    }

    fun day(offset: Int): Long {
        val c = Calendar.getInstance(); c.add(Calendar.DAY_OF_YEAR, -offset)
        return Prefs.sp.getLong("st:" + fmt.format(c.time), 0)
    }

    /** Son 7 gün (bugün sonda): kısa gün adı ve süre. */
    fun last7(): List<Pair<String, Long>> {
        val names = SimpleDateFormat("EEE", Locale.forLanguageTag("tr"))
        return (6 downTo 0).map { o ->
            val c = Calendar.getInstance(); c.add(Calendar.DAY_OF_YEAR, -o)
            names.format(c.time).replaceFirstChar { it.titlecase() } to day(o)
        }
    }

    fun total(): Long = Prefs.sp.all.entries.filter { it.key.startsWith("st:") }.sumOf { (it.value as? Long) ?: 0L }

    fun top(n: Int): List<Pair<String, Long>> =
        Prefs.sp.all.entries.filter { it.key.startsWith("sv:") }.map { it.key.removePrefix("sv:") to ((it.value as? Long) ?: 0L) }
            .sortedByDescending { it.second }.take(n)

    fun clear() { val e = Prefs.sp.edit(); Prefs.sp.all.keys.filter { it.startsWith("st:") || it.startsWith("sv:") }.forEach { e.remove(it) }; e.apply() }

    fun countPlay(k: String) { Prefs.sp.edit().putInt("pc:$k", Prefs.sp.getInt("pc:$k", 0) + 1).apply() }
    fun plays(k: String) = Prefs.sp.getInt("pc:$k", 0)
}

/** Kendiliğinden oluşan müzik listeleri. */
object SmartLists {
    val NAMES = listOf("En çok dinlenenler", "Son eklenenler", "Son dinlenenler", "Hiç dinlenmeyenler")
    fun songs(type: Int, all: List<Song>): List<Song> = when (type) {
        0 -> all.filter { Stats.plays(it.key) > 0 }.sortedByDescending { Stats.plays(it.key) }.take(100)
        1 -> all.sortedByDescending { it.dateAdded }.take(100)
        2 -> all.filter { Prefs.playedAt(it.key) > 0 }.sortedByDescending { Prefs.playedAt(it.key) }.take(100)
        else -> all.filter { Stats.plays(it.key) == 0 && Prefs.playedAt(it.key) == 0L }
    }
}

data class TrashItem(val file: File, val original: String, val deletedAt: Long) {
    val daysLeft get() = (30 - (System.currentTimeMillis() - deletedAt) / 86_400_000L).coerceAtLeast(0)
}

/** Geri dönüşüm kutusu: silinen dosyalar 30 gün saklanır. */
object Trash {
    fun dir(ctx: Context): File {
        val d = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "Cop"); d.mkdirs()
        val nm = File(d, ".nomedia"); if (!nm.exists()) try { nm.createNewFile() } catch (_: Exception) { }
        return d
    }
    private fun meta(): JSONObject = try { JSONObject(Prefs.trashMeta) } catch (_: Exception) { JSONObject() }

    fun put(ctx: Context, src: File): Boolean {
        val name = "${System.currentTimeMillis()}_${src.name}"
        val dest = File(dir(ctx), name)
        if (!Library.moveFile(src, dest)) return false
        val m = meta(); m.put(name, JSONObject().put("o", src.path).put("t", System.currentTimeMillis()))
        Prefs.trashMeta = m.toString()
        return true
    }

    fun list(ctx: Context): List<TrashItem> {
        val m = meta()
        return (dir(ctx).listFiles() ?: emptyArray()).filter { it.isFile && it.name != ".nomedia" }.map { f ->
            val o = m.optJSONObject(f.name)
            TrashItem(f, o?.optString("o") ?: f.name.substringAfter('_'), o?.optLong("t") ?: f.lastModified())
        }.sortedByDescending { it.deletedAt }
    }

    fun restore(ctx: Context, t: TrashItem): Boolean {
        var dest = File(t.original); dest.parentFile?.mkdirs()
        if (dest.exists()) dest = File(dest.parentFile, dest.nameWithoutExtension + " (geri)." + dest.extension)
        val ok = Library.moveFile(t.file, dest)
        if (ok) { drop(t.file.name); Library.scan(ctx, dest.path) }
        return ok
    }

    fun deleteForever(t: TrashItem) { t.file.delete(); drop(t.file.name) }

    private fun drop(name: String) { val m = meta(); m.remove(name); Prefs.trashMeta = m.toString() }

    fun purge(ctx: Context) = list(ctx).filter { it.daysLeft <= 0 }.forEach { deleteForever(it) }
}
