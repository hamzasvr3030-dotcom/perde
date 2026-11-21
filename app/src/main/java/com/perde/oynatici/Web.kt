package com.perde.oynatici

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * YouTube, Instagram, TikTok, X, Facebook, Vimeo… bağlantılarını yt-dlp ile çözümler:
 * tek video → oynatılabilir akış, liste/kanal → içerik listesi, arama → sonuçlar.
 */
object Web {
    data class Stream(
        val page: String, val title: String, val thumb: String?, val uploader: String?, val durationSec: Int,
        val videoUrl: String, val audioUrl: String?, val headers: Map<String, String>, val height: Int
    )
    data class Entry(val id: String, val url: String, val title: String, val durationSec: Int, val uploader: String?, val thumb: String?)
    data class Preview(val title: String, val thumb: String?, val author: String?)

    sealed class Analysis {
        /** Tek video. playlistUrl: video bir listenin parçasıysa o listenin adresi. */
        data class Single(val s: Stream, val playlistUrl: String?) : Analysis()
        data class Multi(val url: String, val title: String, val uploader: String?, val count: Int, val entries: List<Entry>, val isChannel: Boolean) : Analysis()
    }

    @Volatile private var ready = false
    @Volatile private var appCtx: Context? = null
    /** Mobil veride (ölçülü bağlantı) mı? Dl tarafından güncellenir. */
    @Volatile var metered = false
    private val cache = ConcurrentHashMap<String, Pair<Long, Analysis>>()
    private val streamCache = ConcurrentHashMap<String, Pair<Long, Stream>>()
    /** Akış sunucusu → yt-dlp'nin istediği HTTP başlıkları (User-Agent, Referer, Cookie…). */
    val headers = ConcurrentHashMap<String, Map<String, String>>()

    private val DIRECT = VIDEO_EXT + AUDIO_EXT + setOf("m3u8", "mpd")
    const val PLAY_FMT = "bv*[height<=1080][vcodec!^=av01]+ba/b[height<=1080]/bv*+ba/b"
    /** Akıllı kalite: mobil veride izlerken en fazla 480p. */
    fun playFmt(): String = if (DlPrefs.smartQuality && metered) "bv*[height<=${DlPrefs.mobileHeight}][vcodec!^=av01]+ba/b[height<=${DlPrefs.mobileHeight}]/bv*+ba/b" else PLAY_FMT
    const val FALLBACK_FMT = "b[height<=720]/bv*[height<=720][vcodec^=avc1]+ba[ext=m4a]/bv*[height<=720]+ba/b/bv*+ba"

    fun isHttp(u: String) = u.startsWith("http://", true) || u.startsWith("https://", true)
    fun isDirect(u: String): Boolean {
        val path = u.substringBefore('?').substringBefore('#').lowercase(Locale.ROOT)
        return path.substringAfterLast('/').substringAfterLast('.', "") in DIRECT
    }
    fun isPage(u: String) = isHttp(u) && !isDirect(u)

    fun extractUrl(text: String?): String? = text?.let { Regex("https?://\\S+").find(it)?.value?.trimEnd('.', ',', ')', '"', '\'') }
    fun extractUrls(text: String): List<String> = Regex("https?://\\S+").findAll(text).map { it.value.trimEnd('.', ',', ')', '"', '\'') }.distinct().toList()

    private fun host(u: String) = try { URL(u).host.lowercase(Locale.ROOT) } catch (_: Exception) { "" }

    fun site(u: String): String {
        val h = host(u)
        return when {
            "youtu" in h -> "YouTube"; "instagram" in h -> "Instagram"; "tiktok" in h -> "TikTok"
            "twitter" in h || h == "x.com" || h.endsWith(".x.com") -> "X (Twitter)"; "facebook" in h || "fb.watch" in h -> "Facebook"
            "vimeo" in h -> "Vimeo"; "dailymotion" in h -> "Dailymotion"; "twitch" in h -> "Twitch"; "reddit" in h -> "Reddit"
            "soundcloud" in h -> "SoundCloud"
            else -> h.removePrefix("www.").removePrefix("m.")
        }
    }

    /** YouTube kanal adresi sekme içermiyorsa "/videos" ekler (aksi hâlde yt-dlp sekmeleri liste olarak verir). */
    fun normalize(u: String): String {
        val t = u.trim()
        val h = host(t)
        if ("youtube.com" in h) {
            val path = t.substringAfter(h).substringBefore('?')
            val m = Regex("^/(@[^/]+|channel/[^/]+|c/[^/]+|user/[^/]+)/?$").find(path)
            if (m != null) return "https://www.youtube.com/${m.groupValues[1]}/videos"
        }
        return t
    }

    /** YouTube "watch?v=…&list=…" bağlantısındaki listenin adresi. */
    fun playlistOf(u: String): String? {
        if ("youtu" !in host(u)) return null
        val list = Regex("[?&]list=([A-Za-z0-9_-]+)").find(u)?.groupValues?.get(1) ?: return null
        if (list.startsWith("RD")) return null // otomatik "mix" listeleri sonsuzdur
        val hasVideo = Regex("[?&]v=|youtu\\.be/|/shorts/").containsMatchIn(u)
        return if (hasVideo) "https://www.youtube.com/playlist?list=$list" else null
    }

    fun isChannel(u: String) = "youtube.com" in host(u) && Regex("/(@[^/]+|channel/|c/|user/)").containsMatchIn(u)

    /** URL'den video kimliği (YouTube için v=…), yoksa URL'nin kendisi. */
    fun idOf(u: String): String =
        Regex("(?:[?&]v=|youtu\\.be/|/shorts/)([A-Za-z0-9_-]{11})").find(u)?.groupValues?.get(1) ?: u.substringBefore('#')

    @Synchronized
    fun init(ctx: Context) {
        if (ready) return
        val app = ctx.applicationContext
        appCtx = app
        YoutubeDL.getInstance().init(app)
        try { FFmpeg.getInstance().init(app) } catch (_: Exception) { }
        ready = true
    }

    /** yt-dlp'yi en fazla 3 günde bir günceller. */
    fun maybeUpdate(ctx: Context, force: Boolean = false) {
        val last = Prefs.sp.getLong("ytdlpUpd", 0)
        if (!force && System.currentTimeMillis() - last < 3 * 86_400_000L) return
        if (force && System.currentTimeMillis() - last < 10 * 60_000L) return
        try {
            init(ctx)
            Prefs.sp.edit().putLong("ytdlpUpd", System.currentTimeMillis()).apply()
            YoutubeDL.getInstance().updateYoutubeDL(ctx.applicationContext, YoutubeDL.UpdateChannel._STABLE)
        } catch (_: Exception) { }
    }

    fun version(ctx: Context): String = try { init(ctx); YoutubeDL.getInstance().versionName(ctx.applicationContext) ?: "?" } catch (_: Exception) { "?" }

    // ---------------- hesap çerezleri ----------------
    fun cookieFile(ctx: Context) = File(ctx.filesDir, "cookies.txt")
    fun addCookies(ctx: Context, r: YoutubeDLRequest) {
        val f = cookieFile(ctx)
        if (f.exists() && f.length() > 40) r.addOption("--cookies", f.absolutePath)
        // YouTube'un bulmaca (imza/n) çözümleri kalıcı önbellekte tutulsun: her indirmede yeniden çözülmesin
        r.addOption("--cache-dir", File(ctx.filesDir, "ytdlp-cache").apply { mkdirs() }.absolutePath)
    }

    // ---------------- analiz bilgisini indirmede tekrar kullanma ----------------
    fun infoFile(ctx: Context, url: String) = File(File(ctx.cacheDir, "info").apply { mkdirs() }, md5(url.trim()) + ".json")
    private fun md5(s: String) = java.security.MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    /** 2 saatten yeni analiz dosyası varsa döner (akış adresleri ~6 saat geçerli). */
    fun freshInfo(ctx: Context, url: String): File? = infoFile(ctx, url).takeIf { it.exists() && System.currentTimeMillis() - it.lastModified() < 2 * 3600_000L }

    fun explain(e: Throwable): String {
        val m = (e.message ?: e.toString())
        val low = m.lowercase()
        return when {
            "sign in to confirm" in low && "bot" in low -> "YouTube bu isteği bot sandı. İndir sekmesi › Hesaplar'dan YouTube'a giriş yapıp tekrar dene."
            "sign in to confirm" in low || "login" in low || "logged-in" in low || "cookies" in low || ("age" in low && "restrict" in low) ->
                "Bu içerik giriş gerektiriyor (yaş sınırı veya gizli hesap). İndir sekmesi › Hesaplar'dan giriş yapabilirsin."
            "private video" in low || "is private" in low -> "Bu video gizli."
            "unsupported url" in low -> "Bu site desteklenmiyor."
            "video unavailable" in low || "not available" in low || "has been removed" in low -> "Video kaldırılmış veya bu bölgede yok."
            "drm" in low -> "Bu içerik kopya korumalı (DRM), indirilemez."
            "unable to resolve host" in low || "failed to resolve" in low || "network is unreachable" in low || "timed out" in low ||
                "connection reset" in low || "getaddrinfo" in low || "connection refused" in low -> "İnternet bağlantısı yok veya koptu."
            "http error 429" in low -> "Site çok fazla istek yüzünden geçici olarak engelledi. Birkaç dakika sonra dene."
            "http error 403" in low -> "Site erişimi reddetti (403). Biraz sonra tekrar dene."
            "no space left" in low -> "Telefonda yer kalmadı."
            else -> m.lineSequence().lastOrNull { it.contains("ERROR") }?.substringAfter("ERROR:")?.trim()?.take(200) ?: m.take(200)
        }
    }

    fun isNetworkError(m: String?): Boolean {
        val l = (m ?: "").lowercase()
        return listOf("unable to resolve host", "failed to resolve", "network is unreachable", "timed out", "connection reset",
            "getaddrinfo", "connection refused", "connection aborted", "remote end closed", "incompleteread", "ssl").any { it in l }
    }

    private fun runJson(ctx: Context, r: YoutubeDLRequest): JSONObject {
        init(ctx)
        addCookies(ctx, r)
        r.addOption("-J"); r.addOption("--no-warnings")
        val out = try { YoutubeDL.getInstance().execute(r).out } catch (e: Exception) {
            val low = (e.message ?: "").lowercase()
            if ("unsupported url" in low || "private" in low || "unavailable" in low || isNetworkError(low)) throw e
            // eskimiş çözümleyici olabilir: bir kez güncelleyip tekrar dene
            maybeUpdate(ctx, force = true)
            YoutubeDL.getInstance().execute(r).out
        }
        val line = out.lineSequence().map { it.trim() }.lastOrNull { it.startsWith("{") } ?: throw IllegalStateException("Bilgi alınamadı")
        return JSONObject(line)
    }

    private fun hdrMap(o: JSONObject?): Map<String, String> {
        if (o == null) return emptyMap()
        val m = HashMap<String, String>(); o.keys().forEach { k -> m[k] = o.optString(k) }; return m
    }

    private fun streamFrom(o: JSONObject, page: String): Stream {
        val rf = o.optJSONArray("requested_formats")
        val video: String; var audio: String? = null; var h = o.optInt("height")
        var hdr = hdrMap(o.optJSONObject("http_headers"))
        if (rf != null && rf.length() >= 2) {
            val fs = (0 until rf.length()).map { rf.getJSONObject(it) }
            val v = fs.firstOrNull { it.optString("vcodec", "none") != "none" } ?: fs[0]
            val a = fs.firstOrNull { it !== v && it.optString("vcodec", "none") == "none" } ?: fs[1]
            video = v.getString("url"); audio = a.optString("url").ifEmpty { null }; h = v.optInt("height")
            v.optJSONObject("http_headers")?.let { hdr = hdrMap(it) }
        } else {
            video = o.optString("url").ifEmpty { rf?.optJSONObject(0)?.optString("url") ?: "" }
            if (video.isEmpty()) throw IllegalStateException("Oynatılabilir akış bulunamadı")
        }
        listOfNotNull(video, audio).forEach { u -> host(u).takeIf { it.isNotEmpty() }?.let { headers[it] = hdr } }
        return Stream(page, o.optString("title").ifEmpty { site(page) }, o.optString("thumbnail").ifEmpty { null },
            o.optString("uploader").ifEmpty { o.optString("channel") }.ifEmpty { null }, o.optDouble("duration", 0.0).toInt(),
            video, audio, hdr, h)
    }

    private fun entries(o: JSONObject): List<Entry> {
        val a = o.optJSONArray("entries") ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val e = a.optJSONObject(i) ?: return@mapNotNull null
            val id = e.optString("id")
            var url = e.optString("url").ifEmpty { e.optString("webpage_url") }
            val yt = e.optString("ie_key").contains("Youtube", true) || "youtu" in url
            if (yt && id.isNotEmpty() && !url.startsWith("http")) url = "https://www.youtube.com/watch?v=$id"
            if (url.isEmpty()) return@mapNotNull null
            val thumb = if (yt && id.isNotEmpty()) "https://i.ytimg.com/vi/$id/mqdefault.jpg"
            else e.optJSONArray("thumbnails")?.let { t -> if (t.length() > 0) t.optJSONObject(t.length() - 1)?.optString("url") else null }
            Entry(id.ifEmpty { url }, url, e.optString("title").ifEmpty { "Video ${i + 1}" }, e.optDouble("duration", 0.0).toInt(),
                e.optString("channel").ifEmpty { e.optString("uploader") }.ifEmpty { null }, thumb)
        }
    }

    /** Bağlantıyı inceler: tek video mu, liste/kanal mı? (sonuç 20 dk önbellekte) */
    suspend fun analyze(ctx: Context, raw: String): Analysis = withContext(Dispatchers.IO) {
        val url = normalize(raw)
        cache[url]?.let { (t, a) -> if (System.currentTimeMillis() - t < 20 * 60_000L) return@withContext a }
        val r = YoutubeDLRequest(url)
        r.addOption("--flat-playlist")
        r.addOption("-f", playFmt())
        val pl = playlistOf(url)
        if (pl != null) r.addOption("--no-playlist")
        r.addOption("-I", if (isChannel(url)) "1:200" else "1:500")
        val o = runJson(ctx, r)
        if (o.optString("_type") != "playlist" && !o.has("entries")) try { infoFile(ctx, raw).writeText(o.toString()) } catch (_: Exception) { }
        val a: Analysis = if (o.optString("_type") == "playlist" || o.has("entries")) {
            val es = entries(o)
            Analysis.Multi(url, o.optString("title").ifEmpty { site(url) }, o.optString("uploader").ifEmpty { o.optString("channel") }.ifEmpty { null },
                o.optInt("playlist_count", es.size).coerceAtLeast(es.size), es, isChannel(url))
        } else {
            val s = streamFrom(o, raw.trim())
            streamCache["${raw.trim()}#false"] = System.currentTimeMillis() to s
            Analysis.Single(s, pl)
        }
        cache[url] = System.currentTimeMillis() to a
        a
    }

    /** Oynatma için akış. fallback=true: tek dosyalı / daha uyumlu biçim. */
    suspend fun resolve(ctx: Context, url: String, fallback: Boolean = false): Stream = withContext(Dispatchers.IO) {
        val key = "$url#$fallback"
        streamCache[key]?.let { (t, s) -> if (System.currentTimeMillis() - t < 20 * 60_000L) return@withContext s }
        val r = YoutubeDLRequest(url)
        r.addOption("--no-playlist")
        r.addOption("-f", if (fallback) FALLBACK_FMT else playFmt())
        val s = streamFrom(runJson(ctx, r), url)
        streamCache[key] = System.currentTimeMillis() to s
        s
    }

    fun cached(url: String): Stream? = streamCache["$url#false"]?.second ?: streamCache["$url#true"]?.second

    /** YouTube'da arama. */
    suspend fun search(ctx: Context, q: String, n: Int = 30): List<Entry> = withContext(Dispatchers.IO) {
        val r = YoutubeDLRequest("ytsearch$n:$q")
        r.addOption("--flat-playlist")
        entries(runJson(ctx, r))
    }

    /** Sitenin kendi hızlı bilgi servisinden (oEmbed) başlık + kapak: genelde 1 saniyenin altında. */
    suspend fun preview(url: String): Preview? = withContext(Dispatchers.IO) {
        val h = host(url)
        val endpoint = when {
            "youtu" in h -> "https://www.youtube.com/oembed?format=json&url="
            "vimeo" in h -> "https://vimeo.com/api/oembed.json?url="
            "tiktok" in h -> "https://www.tiktok.com/oembed?url="
            "soundcloud" in h -> "https://soundcloud.com/oembed?format=json&url="
            "dailymotion" in h -> "https://www.dailymotion.com/services/oembed?url="
            else -> return@withContext null
        }
        try {
            val c = URL(endpoint + URLEncoder.encode(url, "UTF-8")).openConnection() as HttpURLConnection
            c.connectTimeout = 4000; c.readTimeout = 4000; c.setRequestProperty("User-Agent", "Mozilla/5.0")
            if (c.responseCode != 200) return@withContext null
            val o = JSONObject(c.inputStream.bufferedReader().readText())
            Preview(o.optString("title").ifEmpty { site(url) }, o.optString("thumbnail_url").ifEmpty { null }, o.optString("author_name").ifEmpty { null })
        } catch (_: Exception) { null }
    }
}
