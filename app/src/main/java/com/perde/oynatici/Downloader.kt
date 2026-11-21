package com.perde.oynatici

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

/** İndirme ayarları. */
object DlPrefs {
    var height by IntP("dlH", 1080)
    var audioFmt by StrP("dlFmt", "m4a")
    var kbps by IntP("dlKbps", 192)
    var subs by BoolP("dlSubs", true)
    var sponsor by BoolP("dlSponsor", false)
    var chapters by BoolP("dlChap", true)
    var meta by BoolP("dlMeta", true)
    var smart by BoolP("dlSmart", true)
    var askVideo by BoolP("dlAskV", true)
    var askAudio by BoolP("dlAskA", true)
    var wifiOnly by BoolP("dlWifi", false)
    var parallel by IntP("dlPar3", 3)
    var fast by BoolP("dlFast", true)
    var rate by IntP("dlRate", 0)          // 0 sınırsız, 1: 1 MB/sn, 2: 2 MB/sn, 3: 5 MB/sn
    var archive by BoolP("dlArch", true)
    var clipboard by BoolP("dlClip", true)
    var lastClip by StrP("dlLastClip", "")
    val RATES = listOf("Sınırsız", "1 MB/sn", "2 MB/sn", "5 MB/sn")
    val HEIGHTS = listOf(2160, 1440, 1080, 720, 480, 360)
}

data class DlOpts(
    val audio: Boolean, val height: Int = 1080, val fmt: String = "m4a", val kbps: Int = 192, val subs: Boolean = true,
    val sponsor: Boolean = false, val chapters: Boolean = true, val meta: Boolean = true, val smart: Boolean = true
) {
    fun json(): JSONObject = JSONObject().put("a", audio).put("h", height).put("f", fmt).put("k", kbps).put("s", subs)
        .put("sp", sponsor).put("c", chapters).put("m", meta).put("sm", smart)
    val label get() = if (audio) "${fmt.uppercase()} · $kbps kbps" else if (height >= 2160) "4K" else "${height}p"

    companion object {
        fun from(o: JSONObject) = DlOpts(o.optBoolean("a"), o.optInt("h", 1080), o.optString("f", "m4a"), o.optInt("k", 192),
            o.optBoolean("s", true), o.optBoolean("sp"), o.optBoolean("c", true), o.optBoolean("m", true), o.optBoolean("sm", true))
        fun defaults(audio: Boolean) = DlOpts(audio, DlPrefs.height, DlPrefs.audioFmt, DlPrefs.kbps, DlPrefs.subs, DlPrefs.sponsor,
            DlPrefs.chapters, DlPrefs.meta, DlPrefs.smart)
    }
}

class Job(
    val id: String, val url: String, title: String, val thumb: String?, val opts: DlOpts,
    val group: String? = null, val index: Int = 0, val dir: String,
    var at: Long = 0L, val forceArtist: String? = null, val forceTitle: String? = null
) {
    companion object { const val Q = 0; const val RUN = 1; const val DONE = 2; const val ERR = 3; const val CANCEL = 4; const val PAUSED = 5; const val WAIT = 6; const val SKIP = 7; const val SCHED = 8 }
    var title by mutableStateOf(title)
    var state by mutableIntStateOf(Q)
    var progress by mutableFloatStateOf(0f)
    var phase by mutableStateOf("Sırada")
    var speed by mutableStateOf("")
    var eta by mutableStateOf("")
    var file by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)
    var netErr = false
    var pauseReq = false
    // ilerleme hesabı
    var fileIdx = 0
    var lastRaw = 0f
    val active get() = state == Q || state == RUN || state == WAIT || state == SCHED

    fun json(): JSONObject = JSONObject().put("id", id).put("u", url).put("t", title).put("th", thumb ?: "").put("o", opts.json())
        .put("g", group ?: "").put("i", index).put("d", dir).put("st", state).put("p", progress.toDouble()).put("f", file ?: "").put("e", error ?: "")
        .put("at", at).put("fa", forceArtist ?: "").put("ft", forceTitle ?: "")
}

class DlGroup(val id: String, val title: String, val audio: Boolean, val playlistId: Long?, val total: Int) {
    fun json(): JSONObject = JSONObject().put("id", id).put("t", title).put("a", audio).put("p", playlistId ?: -1L).put("n", total)
}

data class HistItem(val key: String, val title: String, val url: String, val file: String?, val audio: Boolean, val at: Long, val ok: Boolean, val note: String)

data class Follow(val url: String, val title: String, val opts: DlOpts, val playlistId: Long?, val known: Set<String>, val newIds: List<String>, val dir: String)

object Dl {
    val jobs = mutableStateListOf<Job>()
    val groups = mutableStateListOf<DlGroup>()
    val history = mutableStateListOf<HistItem>()
    val follows = mutableStateListOf<Follow>()
    var doneTick by mutableIntStateOf(0)
    private val main by lazy { Handler(Looper.getMainLooper()) }
    private val pool = Executors.newCachedThreadPool()
    private var app: Context? = null
    @Volatile private var unmetered = true
    @Volatile private var online = true

    // ---------------- başlat / kalıcılık ----------------
    fun init(ctx: Context) {
        if (app != null) return
        app = ctx.applicationContext
        load()
        loadHistory()
        loadFollows()
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        try {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    unmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED); online = true
                    main.post { netBack() }
                }
                override fun onLost(network: Network) { online = false }
            })
            cm.getNetworkCapabilities(cm.activeNetwork)?.let { unmetered = it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) }
        } catch (_: Exception) { }
        main.post { pump(); jobs.filter { it.state == Job.SCHED }.minOfOrNull { it.at }?.let { DlAlarm.schedule(ctx, it) } }
    }

    private fun qFile() = File(app!!.filesDir, "dl_queue.json")
    private fun hFile() = File(app!!.filesDir, "dl_history.json")
    fun archiveFile(ctx: Context) = File(ctx.filesDir, "dl_archive.txt")

    private fun load() {
        try {
            val o = JSONObject(qFile().readText())
            val ga = o.optJSONArray("g") ?: JSONArray()
            for (i in 0 until ga.length()) ga.getJSONObject(i).let { g ->
                groups += DlGroup(g.getString("id"), g.getString("t"), g.optBoolean("a"), g.optLong("p", -1).takeIf { it >= 0 }, g.optInt("n"))
            }
            val ja = o.optJSONArray("j") ?: JSONArray()
            for (i in 0 until ja.length()) ja.getJSONObject(i).let { j ->
                val job = Job(j.getString("id"), j.getString("u"), j.optString("t"), j.optString("th").ifEmpty { null }, DlOpts.from(j.getJSONObject("o")),
                    j.optString("g").ifEmpty { null }, j.optInt("i"), j.getString("d"), j.optLong("at"),
                    j.optString("fa").ifEmpty { null }, j.optString("ft").ifEmpty { null })
                job.state = j.optInt("st").let { if (it == Job.RUN || it == Job.WAIT) Job.Q else it }
                job.progress = j.optDouble("p").toFloat(); job.file = j.optString("f").ifEmpty { null }; job.error = j.optString("e").ifEmpty { null }
                job.phase = phaseText(job)
                jobs += job
            }
        } catch (_: Exception) { }
    }

    fun save() {
        val c = app ?: return
        try {
            val o = JSONObject().put("g", JSONArray().apply { groups.forEach { put(it.json()) } })
                .put("j", JSONArray().apply { jobs.take(400).forEach { put(it.json()) } })
            File(c.filesDir, "dl_queue.json").writeText(o.toString())
        } catch (_: Exception) { }
    }

    private fun loadHistory() {
        try {
            val a = JSONArray(hFile().readText())
            for (i in 0 until a.length()) a.getJSONObject(i).let { h ->
                history += HistItem(h.optString("k"), h.optString("t"), h.optString("u"), h.optString("f").ifEmpty { null }, h.optBoolean("a"),
                    h.optLong("at"), h.optBoolean("ok"), h.optString("n"))
            }
        } catch (_: Exception) { }
    }

    private fun saveHistory() {
        try {
            val a = JSONArray()
            history.take(1000).forEach { h ->
                a.put(JSONObject().put("k", h.key).put("t", h.title).put("u", h.url).put("f", h.file ?: "").put("a", h.audio)
                    .put("at", h.at).put("ok", h.ok).put("n", h.note))
            }
            hFile().writeText(a.toString())
        } catch (_: Exception) { }
    }

    fun clearHistory() { history.clear(); saveHistory() }

    private fun phaseText(j: Job) = when (j.state) {
        Job.Q -> "Sırada"; Job.DONE -> "İndirildi"; Job.ERR -> j.error ?: "Hata"; Job.CANCEL -> "İptal edildi"
        Job.PAUSED -> "Duraklatıldı"; Job.WAIT -> "Wi-Fi bekleniyor"; Job.SKIP -> "Zaten indirilmiş"
        Job.SCHED -> "⏰ " + java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.forLanguageTag("tr")).format(j.at) + " başlayacak"
        else -> "İndiriliyor"
    }

    /** Zamanı gelen zamanlanmış indirmeleri başlatır, kalanlar için alarmı yeniden kurar. */
    fun releaseScheduled(ctx: Context) {
        main.post {
            val now = System.currentTimeMillis()
            jobs.filter { it.state == Job.SCHED && it.at <= now + 60_000 }.forEach { it.state = Job.Q; it.phase = "Sırada" }
            jobs.filter { it.state == Job.SCHED }.minOfOrNull { it.at }?.let { DlAlarm.schedule(ctx, it) }
            save(); pump()
        }
    }
    fun startNow(j: Job) { if (j.state == Job.SCHED) { j.state = Job.Q; j.phase = "Sırada"; save(); pump() } }

    // ---------------- kuyruğa ekleme ----------------
    fun outDir(audio: Boolean, sub: String? = null): File {
        val base = File(Environment.getExternalStoragePublicDirectory(if (audio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES), "Kadraj")
        return (if (sub.isNullOrBlank()) base else File(base, safeName(sub))).apply { mkdirs() }
    }

    fun safeName(s: String) = s.replace(Regex("[\\\\/:*?\"<>|]"), " ").replace(Regex("\\s+"), " ").trim().take(80).ifEmpty { "Liste" }

    /** Tek tek bağlantılar (tek video veya toplu yapıştırma). */
    fun addSingles(ctx: Context, items: List<Triple<String, String, String?>>, opts: DlOpts, at: Long = 0L) {
        init(ctx)
        val dir = outDir(opts.audio).absolutePath
        items.forEach { (url, title, thumb) -> jobs.add(0, Job("j${System.nanoTime()}", url, title, thumb, opts, dir = dir, at = at).also { schedule(ctx, it) }) }
        save(); pump()
    }

    private fun schedule(ctx: Context, j: Job) {
        if (j.at > System.currentTimeMillis() + 60_000) { j.state = Job.SCHED; j.phase = phaseText(j); DlAlarm.schedule(ctx, jobs.filter { it.state == Job.SCHED }.minOfOrNull { it.at } ?: j.at) }
    }

    /** Liste/kanal: aynı adla çalma/izleme listesi oluşturur, kendi klasörüne indirir. */
    fun addList(ctx: Context, title: String, entries: List<Web.Entry>, opts: DlOpts, follow: Boolean, sourceUrl: String, allIds: List<String>, existingPlaylist: Long? = null, startIndex: Int = 0, at: Long = 0L): DlGroup {
        init(ctx)
        val pid = existingPlaylist ?: Playlists.create(title).id
        val g = DlGroup("g${System.nanoTime()}", title, opts.audio, pid, entries.size)
        groups.add(0, g)
        val dir = outDir(opts.audio, title).absolutePath
        entries.forEachIndexed { i, e ->
            val sp = e.url.startsWith("ytsearch")
            jobs += Job("j${System.nanoTime()}_$i", e.url, e.title, e.thumb, opts, g.id, startIndex + i, dir, at,
                if (sp) e.uploader else null, if (sp) e.title.substringAfter(" - ") else null).also { schedule(ctx, it) }
        }
        if (follow) {
            follows.removeAll { it.url == sourceUrl }
            follows += Follow(sourceUrl, title, opts, pid, allIds.toSet(), emptyList(), dir)
            saveFollows()
        }
        save(); pump()
        return g
    }

    fun groupJobs(g: DlGroup) = jobs.filter { it.group == g.id }

    // ---------------- denetim ----------------
    fun pause(j: Job) {
        if (j.state == Job.RUN) { j.pauseReq = true; try { YoutubeDL.getInstance().destroyProcessById(j.id) } catch (_: Exception) { } }
        else if (j.state == Job.Q || j.state == Job.WAIT) { j.state = Job.PAUSED; j.phase = "Duraklatıldı" }
        save()
    }
    fun resume(j: Job) { if (j.state == Job.PAUSED || j.state == Job.ERR || j.state == Job.CANCEL) { j.state = Job.Q; j.error = null; j.phase = "Sırada"; j.netErr = false; save(); pump() } }
    fun cancel(j: Job) {
        if (j.state == Job.RUN) { j.pauseReq = false; try { YoutubeDL.getInstance().destroyProcessById(j.id) } catch (_: Exception) { } }
        j.state = Job.CANCEL; j.phase = "İptal edildi"; save(); pump()
    }
    fun remove(j: Job) { if (j.active) cancel(j); jobs.remove(j); save() }
    fun pauseAll() = jobs.filter { it.active }.forEach { pause(it) }
    fun resumeAll() = jobs.filter { it.state == Job.PAUSED }.forEach { resume(it) }
    fun clearFinished() {
        jobs.removeAll { it.state == Job.DONE || it.state == Job.CANCEL || it.state == Job.SKIP }
        groups.removeAll { g -> jobs.none { it.group == g.id } }
        save()
    }
    fun removeGroup(g: DlGroup) { groupJobs(g).forEach { if (it.active) cancel(it) }; jobs.removeAll { it.group == g.id }; groups.remove(g); save() }

    private fun netBack() {
        jobs.filter { it.state == Job.ERR && it.netErr }.forEach { it.state = Job.Q; it.error = null; it.netErr = false; it.phase = "Sırada" }
        pump()
    }

    val activeCount get() = jobs.count { it.active }

    /** Kuyruğu işletir: Wi-Fi kuralı ve aynı anda indirme sınırı. */
    fun pump() {
        val c = app ?: return
        val waitWifi = DlPrefs.wifiOnly && !unmetered
        jobs.filter { it.state == Job.Q || it.state == Job.WAIT }.forEach { if (waitWifi) { it.state = Job.WAIT; it.phase = "Wi-Fi bekleniyor" } else if (it.state == Job.WAIT) { it.state = Job.Q; it.phase = "Sırada" } }
        if (!waitWifi) {
            var running = jobs.count { it.state == Job.RUN }
            for (j in jobs.reversed()) {
                if (running >= DlPrefs.parallel.coerceIn(1, 6)) break
                if (j.state != Job.Q) continue
                j.state = Job.RUN; j.phase = "Bağlanıyor…"; j.progress = 0f; j.fileIdx = 0; j.lastRaw = 0f; j.pauseReq = false
                running++
                pool.execute { run(c, j); main.post { finished(c, j) } }
            }
        }
        if (activeCount > 0) {
            val i = Intent(c, DownloadService::class.java)
            try { if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i) } catch (_: Exception) { }
        }
    }

    private fun finished(c: Context, j: Job) {
        j.phase = phaseText(j)
        if (j.state == Job.DONE || j.state == Job.SKIP || j.state == Job.ERR) {
            history.add(0, HistItem(Web.idOf(j.url), j.title, j.url, j.file, j.opts.audio, System.currentTimeMillis(), j.state != Job.ERR,
                if (j.state == Job.ERR) j.error ?: "Hata" else if (j.state == Job.SKIP) "Zaten indirilmişti" else j.opts.label))
            saveHistory()
        }
        // liste: tamamlanan dosyaları sırasıyla çalma listesine yaz
        j.group?.let { gid -> groups.firstOrNull { it.id == gid }?.let { g ->
            g.playlistId?.let { pid ->
                val files = jobs.filter { it.group == g.id && it.file != null && (it.state == Job.DONE || it.state == Job.SKIP) }.sortedBy { it.index }.mapNotNull { it.file }
                val old = Playlists.get(pid)?.items ?: emptyList()
                Playlists.setItems(pid, (old + files).distinct().sortedBy { f -> jobs.firstOrNull { it.file == f && it.group == g.id }?.index ?: -1 })
            }
            val gj = groupJobs(g)
            if (gj.none { it.active }) notifyDone(c, "${g.title}: ${gj.count { it.state == Job.DONE || it.state == Job.SKIP }}/${gj.size} indirildi",
                gj.count { it.state == Job.ERR }.let { if (it > 0) "$it video indirilemedi" else "Tamamlandı" })
        } } ?: run {
            if (j.state == Job.DONE) notifyDone(c, "İndirildi", j.title)
            if (j.state == Job.ERR && !j.netErr) notifyDone(c, "İndirilemedi", "${j.title} · ${j.error}")
        }
        doneTick++
        save(); pump()
    }

    private fun notifyDone(c: Context, title: String, text: String) {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        DownloadService.channel(nm)
        val open = PendingIntent.getActivity(c, 8, Intent(c, MainActivity::class.java).putExtra("openDownloads", true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        nm.notify((title + text).hashCode(), NotificationCompat.Builder(c, "dl").setSmallIcon(R.drawable.ic_download)
            .setContentTitle(title).setContentText(text).setAutoCancel(true).setContentIntent(open).build())
    }

    // ---------------- asıl indirme ----------------
    private val speedRx = Regex("at\\s+~?\\s*([\\d.]+)\\s*([KMG]?)i?B/s")
    private val etaRx = Regex("ETA\\s+(\\d+:\\d+(?::\\d+)?)")

    internal fun onProgress(j: Job, raw: Float, line: String) {
        if (raw < 0) return
        if (raw < j.lastRaw - 30f) j.fileIdx++      // yeni dosyaya geçildi (görüntü → ses)
        j.lastRaw = raw
        val f = raw / 100f
        val mapped = if (j.opts.audio) f * 0.92f else when (j.fileIdx) { 0 -> f * 0.80f; 1 -> 0.80f + f * 0.16f; else -> 0.96f }
        if (mapped > j.progress) j.progress = mapped.coerceAtMost(0.99f)   // asla geri gitmez
        j.phase = when {
            raw >= 100f && (j.opts.audio || j.fileIdx >= 1) -> if (j.opts.audio) "Dönüştürülüyor…" else "Birleştiriliyor…"
            j.opts.audio -> "İndiriliyor"
            j.fileIdx == 0 -> "Görüntü indiriliyor"
            else -> "Ses indiriliyor"
        }
        speedRx.find(line)?.let { m ->
            val v = m.groupValues[1].toFloatOrNull() ?: 0f
            val mb = when (m.groupValues[2]) { "K" -> v / 1024f; "G" -> v * 1024f; "" -> v / 1048576f; else -> v }
            j.speed = if (mb >= 1f) String.format(Locale.forLanguageTag("tr"), "%.1f MB/sn", mb) else String.format(Locale.forLanguageTag("tr"), "%.0f KB/sn", mb * 1024)
        }
        j.eta = etaRx.find(line)?.groupValues?.get(1)?.let { "$it kaldı" } ?: ""
    }

    private fun build(c: Context, j: Job, subs: Boolean, pathFile: File): YoutubeDLRequest {
        val o = j.opts
        val r = YoutubeDLRequest(j.url)
        r.addOption("--no-playlist"); r.addOption("--no-mtime"); r.addOption("--newline"); r.addOption("--progress")
        r.addOption("--retries", "10"); r.addOption("--fragment-retries", "10"); r.addOption("--no-warnings")
        if (DlPrefs.fast) { r.addOption("-N", "4"); r.addOption("--http-chunk-size", "10M") }
        listOf("", "1M", "2M", "5M").getOrElse(DlPrefs.rate) { "" }.takeIf { it.isNotEmpty() }?.let { r.addOption("-r", it) }
        if (DlPrefs.archive) r.addOption("--download-archive", archiveFile(c).absolutePath)
        Web.addCookies(c, r)
        r.addCommands(listOf("--print-to-file", "after_move:filepath", pathFile.absolutePath))
        if (o.audio) {
            r.addOption("-f", "ba[ext=m4a]/ba/b")
            r.addOption("-x"); r.addOption("--audio-format", o.fmt); r.addOption("--audio-quality", "${o.kbps}K")
            // Sabit değer: "%(yok|Değer)s" şablonu (tek kelimelik değerler alan adı sanılmasın diye); ( ) | : % temizlenir
            fun lit(s: String) = "%(kadraj_x|" + s.replace('(', '[').replace(')', ']').replace(Regex("[|:%]"), " ").replace(Regex("\\s+"), " ").trim() + ")s"
            if (j.forceArtist != null && j.forceTitle != null) {
                r.addOption("--parse-metadata", "${lit(j.forceArtist)}:%(artist)s")
                r.addOption("--parse-metadata", "${lit(j.forceTitle)}:%(title)s")
            } else if (o.smart) {
                r.addOption("--parse-metadata", "title:(?P<artist>.+?)\\s+[-–—]\\s+(?P<title>.+)")
                r.addCommands(listOf("--replace-in-metadata", "title",
                    "\\s*[\\(\\[][^\\)\\]]*(?i:official|video|audio|lyric|klip|clip|visualizer|hd|4k|remaster)[^\\)\\]]*[\\)\\]]", ""))
            }
            if (o.meta) { r.addOption("--embed-metadata"); r.addOption("--embed-thumbnail"); r.addOption("--convert-thumbnails", "jpg") }
            if (o.sponsor) r.addOption("--sponsorblock-remove", "sponsor,selfpromo,interaction,music_offtopic")
            r.addOption("-o", j.dir + "/" + (if (o.smart || j.forceArtist != null) "%(artist,uploader)s - %(title).80B.%(ext)s" else "%(title).90B.%(ext)s"))
        } else {
            val h = o.height
            val av1 = if (h > 1080) "" else "[vcodec!^=av01]"
            r.addOption("-f", "bv*[height<=$h][ext=mp4]$av1+ba[ext=m4a]/bv*[height<=$h]$av1+ba/b[height<=$h]/bv*+ba/b")
            r.addOption("--merge-output-format", "mp4")
            if (o.meta) r.addOption("--embed-metadata")
            if (o.chapters) r.addOption("--embed-chapters")
            if (subs) { r.addOption("--write-subs"); r.addOption("--write-auto-subs"); r.addOption("--sub-langs", "tr,en"); r.addOption("--convert-subs", "srt") }
            if (o.sponsor) r.addOption("--sponsorblock-remove", "sponsor,selfpromo,interaction")
            r.addOption("-o", j.dir + "/%(title).90B [%(id)s].%(ext)s")
        }
        return r
    }

    private val MEDIA = VIDEO_EXT + AUDIO_EXT

    private fun run(c: Context, j: Job) {
        try { Web.init(c) } catch (e: Exception) { j.state = Job.ERR; j.error = "İndirme motoru başlatılamadı"; return }
        val started = System.currentTimeMillis()
        val pathFile = File(c.cacheDir, "dlpath_${j.id}.txt").apply { delete() }
        var subs = j.opts.subs && !j.opts.audio
        var attempt = 0
        while (true) {
            attempt++
            var out = ""; var err: Exception? = null
            try {
                val res = YoutubeDL.getInstance().execute(build(c, j, subs, pathFile), j.id) { p, _, line -> onProgress(j, p, line) }
                out = res.out + "\n" + res.err
            } catch (e: YoutubeDL.CanceledException) {
                j.state = if (j.pauseReq) Job.PAUSED else Job.CANCEL; j.pauseReq = false; return
            } catch (e: Exception) { err = e; out = e.message ?: "" }

            // sonuç dosyası
            var path = try { pathFile.readLines().map { it.trim() }.lastOrNull { it.isNotEmpty() && File(it).exists() } } catch (_: Exception) { null }
            if (path == null) path = File(j.dir).listFiles()?.filter { it.isFile && it.lastModified() >= started - 2000 && it.extension.lowercase() in MEDIA }
                ?.maxByOrNull { it.lastModified() }?.absolutePath

            when {
                "has already been recorded in the archive" in out -> {
                    j.state = Job.SKIP; j.progress = 1f
                    j.file = history.firstOrNull { it.key == Web.idOf(j.url) && it.file != null && File(it.file).exists() }?.file
                    return
                }
                path != null -> {
                    // kapak/altyazı gibi son işlemler hata verse bile dosya sağlamsa başarılı say
                    Library.scan(c, path)
                    File(path).parentFile?.listFiles()?.filter { it.extension.lowercase() in setOf("jpg", "webp", "png") && it.nameWithoutExtension == File(path).nameWithoutExtension }?.forEach { it.delete() }
                    j.file = path; j.progress = 1f; j.state = Job.DONE; j.speed = ""; j.eta = ""
                    if (j.title.isBlank() || j.title.startsWith("http")) j.title = File(path).nameWithoutExtension
                    return
                }
                err != null && subs && "subtitle" in out.lowercase() && attempt < 3 -> { subs = false; continue }   // altyazı yüzünden düştüyse altyazısız dene
                err != null -> {
                    j.netErr = Web.isNetworkError(out)
                    j.error = if (j.netErr) "Bağlantı koptu — internet gelince kendiliğinden devam edecek" else Web.explain(err)
                    j.state = Job.ERR; return
                }
                else -> { j.state = Job.ERR; j.error = "Dosya bulunamadı"; return }
            }
        }
    }

    // ---------------- takip edilen listeler ----------------
    private fun loadFollows() {
        try {
            val a = JSONArray(Prefs.sp.getString("dlFollows", "[]"))
            for (i in 0 until a.length()) a.getJSONObject(i).let { f ->
                val k = f.optJSONArray("k") ?: JSONArray(); val n = f.optJSONArray("n") ?: JSONArray()
                follows += Follow(f.getString("u"), f.getString("t"), DlOpts.from(f.getJSONObject("o")), f.optLong("p", -1).takeIf { it >= 0 },
                    (0 until k.length()).map { k.getString(it) }.toSet(), (0 until n.length()).map { n.getString(it) }, f.optString("d"))
            }
        } catch (_: Exception) { }
    }

    fun saveFollows() {
        val a = JSONArray()
        follows.forEach { f ->
            a.put(JSONObject().put("u", f.url).put("t", f.title).put("o", f.opts.json()).put("p", f.playlistId ?: -1L)
                .put("k", JSONArray(f.known.toList())).put("n", JSONArray(f.newIds)).put("d", f.dir))
        }
        Prefs.sp.edit().putString("dlFollows", a.toString()).apply()
    }

    /** Takip edilen listelerde yeni video var mı? (arka planda çağrılır) */
    suspend fun checkFollows(ctx: Context, notify: Boolean): Int {
        var total = 0
        for (f in follows.toList()) {
            val a = try { Web.analyze(ctx, f.url) } catch (_: Exception) { continue }
            if (a !is Web.Analysis.Multi) continue
            val fresh = a.entries.map { it.id }.filter { it !in f.known }
            if (fresh.isNotEmpty()) {
                total += fresh.size
                main.post {
                    val i = follows.indexOfFirst { it.url == f.url }
                    if (i >= 0) { follows[i] = follows[i].copy(newIds = fresh); saveFollows() }
                }
                if (notify) notifyDone(ctx, "${f.title}: ${fresh.size} yeni video", "Kadraj > İndir sekmesinden tek dokunuşla indirebilirsin")
            }
        }
        Prefs.sp.edit().putLong("dlFollowChk", System.currentTimeMillis()).apply()
        return total
    }

    /** Takip edilen listenin yeni videolarını indir. */
    suspend fun downloadNew(ctx: Context, f: Follow) {
        val a = try { Web.analyze(ctx, f.url) } catch (_: Exception) { return }
        if (a !is Web.Analysis.Multi) return
        val fresh = a.entries.filter { it.id in f.newIds || it.id !in f.known }
        if (fresh.isEmpty()) return
        main.post {
            val start = f.playlistId?.let { Playlists.get(it)?.items?.size } ?: 0
            addList(ctx, f.title, fresh, f.opts, follow = false, sourceUrl = f.url, allIds = emptyList(), existingPlaylist = f.playlistId, startIndex = start)
            val i = follows.indexOfFirst { it.url == f.url }
            if (i >= 0) { follows[i] = f.copy(known = f.known + fresh.map { it.id }, newIds = emptyList()); saveFollows() }
        }
    }

    fun unfollow(f: Follow) { follows.removeAll { it.url == f.url }; saveFollows() }
}

/** İndirmeler ekran kapansa da sürsün diye ön plan servisi. */
class DownloadService : Service() {
    companion object {
        fun channel(nm: NotificationManager) {
            if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("dl", "İndirmeler", NotificationManager.IMPORTANCE_LOW))
        }
    }
    private val h = Handler(Looper.getMainLooper())
    private val nm by lazy { getSystemService(NOTIFICATION_SERVICE) as NotificationManager }
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notif(): Notification {
        channel(nm)
        val act = Dl.jobs.filter { it.active }
        val run = act.filter { it.state == Job.RUN }
        val open = PendingIntent.getActivity(this, 7, Intent(this, MainActivity::class.java).putExtra("openDownloads", true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val title = when {
            run.isEmpty() && act.any { it.state == Job.WAIT } -> "Kadraj · Wi-Fi bekleniyor"
            run.size == 1 -> run[0].title.take(50)
            else -> "Kadraj · ${run.size} indirme sürüyor"
        }
        val text = buildString {
            if (run.size == 1) append(run[0].phase).append(" · %").append((run[0].progress * 100).toInt())
            if (run.size == 1 && run[0].speed.isNotEmpty()) append(" · ").append(run[0].speed)
            if (act.size > run.size) { if (isNotEmpty()) append(" · "); append("${act.size - run.size} sırada") }
        }
        val avg = if (run.isEmpty()) 0f else run.map { it.progress }.average().toFloat()
        return NotificationCompat.Builder(this, "dl").setSmallIcon(R.drawable.ic_download).setContentTitle(title).setContentText(text)
            .setOnlyAlertOnce(true).setOngoing(true).setContentIntent(open).setProgress(100, (avg * 100).toInt(), run.isEmpty()).build()
    }

    private val tick = object : Runnable {
        override fun run() {
            if (Dl.activeCount == 0) { ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE); stopSelf(); started = false; return }
            nm.notify(41, notif())
            h.postDelayed(this, 1000)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(this, 41, notif(), if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0)
        if (!started) { started = true; h.post(tick) }
        return START_NOT_STICKY
    }

    override fun onDestroy() { h.removeCallbacks(tick); super.onDestroy() }
}
