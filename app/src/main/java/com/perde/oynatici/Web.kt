package com.perde.oynatici

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * YouTube, Instagram, TikTok, X, Facebook, Vimeo… gibi sayfa bağlantılarını yt-dlp ile
 * oynatılabilir akışa çevirir ve indirir.
 */
object Web {
    data class Stream(
        val page: String, val title: String, val thumb: String?, val uploader: String?, val durationSec: Int,
        val videoUrl: String, val audioUrl: String?, val headers: Map<String, String>, val height: Int
    )

    @Volatile private var ready = false
    private val cache = ConcurrentHashMap<String, Pair<Long, Stream>>()
    /** Akış sunucusu → yt-dlp'nin istediği HTTP başlıkları (User-Agent, Referer, Cookie…). */
    val headers = ConcurrentHashMap<String, Map<String, String>>()

    private val DIRECT = VIDEO_EXT + AUDIO_EXT + setOf("m3u8", "mpd")

    fun isHttp(u: String) = u.startsWith("http://", true) || u.startsWith("https://", true)
    fun isDirect(u: String): Boolean {
        val path = u.substringBefore('?').substringBefore('#').lowercase(Locale.ROOT)
        return path.substringAfterLast('/').substringAfterLast('.', "") in DIRECT
    }
    /** Sayfa bağlantısı mı (çözümlenmesi gerekiyor mu)? */
    fun isPage(u: String) = isHttp(u) && !isDirect(u)

    /** Metnin içinden ilk bağlantıyı çıkarır ("Şu videoya bak https://youtu.be/…"). */
    fun extractUrl(text: String?): String? = text?.let { Regex("https?://\\S+").find(it)?.value?.trimEnd('.', ',', ')', '"', '\'') }

    fun site(u: String): String {
        val h = try { android.net.Uri.parse(u).host ?: "" } catch (_: Exception) { "" }
        return when {
            "youtu" in h -> "YouTube"; "instagram" in h -> "Instagram"; "tiktok" in h -> "TikTok"
            "twitter" in h || h == "x.com" || h.endsWith(".x.com") -> "X (Twitter)"; "facebook" in h || "fb.watch" in h -> "Facebook"
            "vimeo" in h -> "Vimeo"; "dailymotion" in h -> "Dailymotion"; "twitch" in h -> "Twitch"; "reddit" in h -> "Reddit"
            else -> h.removePrefix("www.")
        }
    }

    @Synchronized
    fun init(ctx: Context) {
        if (ready) return
        val app = ctx.applicationContext
        YoutubeDL.getInstance().init(app)
        try { FFmpeg.getInstance().init(app) } catch (_: Exception) { }
        ready = true
    }

    /** yt-dlp'yi en fazla 3 günde bir günceller (siteler değiştikçe çözümleyici de güncel kalsın). */
    fun maybeUpdate(ctx: Context) {
        val last = Prefs.sp.getLong("ytdlpUpd", 0)
        if (System.currentTimeMillis() - last < 3 * 86_400_000L) return
        try {
            init(ctx)
            YoutubeDL.getInstance().updateYoutubeDL(ctx.applicationContext, YoutubeDL.UpdateChannel._STABLE)
            Prefs.sp.edit().putLong("ytdlpUpd", System.currentTimeMillis()).apply()
        } catch (_: Exception) { }
    }

    fun version(ctx: Context): String = try { init(ctx); YoutubeDL.getInstance().versionName(ctx.applicationContext) ?: "?" } catch (_: Exception) { "?" }

    /** Hata metnini kullanıcıya anlaşılır hale getirir. */
    fun explain(e: Throwable): String {
        val m = (e.message ?: e.toString())
        return when {
            "Sign in to confirm" in m || "login" in m.lowercase() || "cookies" in m.lowercase() ->
                "Bu içerik giriş yapmadan açılamıyor (yaş sınırı veya gizli hesap olabilir)."
            "Private video" in m || "private" in m.lowercase() -> "Bu video gizli."
            "Unsupported URL" in m -> "Bu site desteklenmiyor."
            "Video unavailable" in m || "not available" in m -> "Video kaldırılmış veya bu bölgede yok."
            "Unable to resolve host" in m || "Failed to resolve" in m || "Network is unreachable" in m || "timed out" in m ->
                "İnternet bağlantısı yok veya çok yavaş."
            "HTTP Error 429" in m -> "Site çok fazla istek nedeniyle geçici olarak engelledi. Birkaç dakika sonra dene."
            else -> m.lineSequence().lastOrNull { it.contains("ERROR") }?.substringAfter("ERROR:")?.trim()?.take(200) ?: m.take(200)
        }
    }

    /** Sayfa bağlantısını oynatılabilir akışa çevirir. fallback=true: tek dosyalı (birleşik) biçim. */
    suspend fun resolve(ctx: Context, url: String, fallback: Boolean = false): Stream = withContext(Dispatchers.IO) {
        val key = "$url#$fallback"
        cache[key]?.let { (t, s) -> if (System.currentTimeMillis() - t < 20 * 60_000L) return@withContext s }
        init(ctx)
        val req = YoutubeDLRequest(url)
        req.addOption("--no-playlist")
        req.addOption("-f", if (fallback) "b[height<=720]/bv*[height<=720][vcodec^=avc1]+ba[ext=m4a]/bv*[height<=720]+ba/b/bv*+ba" else "bv*[height<=1080][vcodec!^=av01]+ba/b[height<=1080]/bv*+ba/b")
        val info = try { YoutubeDL.getInstance().getInfo(req) } catch (e: Exception) {
            // eskimiş çözümleyici: bir kez güncelleyip tekrar dene
            Prefs.sp.edit().putLong("ytdlpUpd", 0).apply(); maybeUpdate(ctx)
            YoutubeDL.getInstance().getInfo(req)
        }
        val rf = info.requestedFormats
        var video: String; var audio: String? = null; var h = 0
        var hdr: Map<String, String> = info.httpHeaders ?: emptyMap()
        if (rf != null && rf.size >= 2) {
            val v = rf.firstOrNull { it.vcodec != null && it.vcodec != "none" } ?: rf[0]
            val a = rf.firstOrNull { it !== v && (it.vcodec == null || it.vcodec == "none") } ?: rf[1]
            video = v.url ?: throw IllegalStateException("Akış adresi yok"); audio = a.url; h = v.height
            v.httpHeaders?.let { hdr = it }
        } else {
            video = info.url ?: rf?.firstOrNull()?.url ?: throw IllegalStateException("Akış adresi yok")
            h = rf?.firstOrNull()?.height ?: 0
        }
        listOfNotNull(video, audio).forEach { u -> android.net.Uri.parse(u).host?.let { headers[it] = hdr } }
        val s = Stream(url, info.title ?: site(url), info.thumbnail, info.uploader, info.duration, video, audio, hdr, h)
        cache[key] = System.currentTimeMillis() to s
        s
    }

    fun cached(url: String): Stream? = cache["$url#false"]?.second
}

// ============================ İndirmeler ============================
class DL(val id: String, val url: String, val title: String, val thumb: String?, val quality: Int) {
    var state by mutableStateOf(0)          // 0 sırada, 1 iniyor, 2 bitti, 3 hata, 4 iptal
    var progress by mutableFloatStateOf(0f)
    var info by mutableStateOf("")
    var file by mutableStateOf<String?>(null)
}

object Downloads {
    val QUALITIES = listOf("En iyi (1080p'ye kadar)", "720p", "480p", "Sadece ses (M4A)")
    val items = mutableStateListOf<DL>()

    fun start(ctx: Context, url: String, title: String, thumb: String?, quality: Int) {
        val d = DL("dl${System.currentTimeMillis()}", url, title, thumb, quality)
        items.add(0, d)
        val i = Intent(ctx, DownloadService::class.java).putExtra("id", d.id)
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
    }

    fun cancel(d: DL) {
        if (d.state == 1) try { YoutubeDL.getInstance().destroyProcessById(d.id) } catch (_: Exception) { }
        d.state = 4; d.info = "İptal edildi"
    }

    fun outDir(audio: Boolean): File =
        File(Environment.getExternalStoragePublicDirectory(if (audio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES), "Perde").apply { mkdirs() }

    /** Asıl indirme işi (servisin iş parçacığında çalışır). */
    fun run(ctx: Context, d: DL, onProgress: () -> Unit) {
        if (d.state == 4) return
        d.state = 1; d.info = "Hazırlanıyor…"; onProgress()
        try {
            Web.init(ctx)
            val audio = d.quality == 3
            val dir = outDir(audio)
            val req = YoutubeDLRequest(d.url)
            req.addOption("--no-playlist"); req.addOption("--no-mtime"); req.addOption("--newline"); req.addOption("--progress")
            req.addOption("-o", dir.absolutePath + "/%(title).90B [%(id)s].%(ext)s")
            req.addOption("--print", "after_move:filepath")
            when (d.quality) {
                3 -> { req.addOption("-f", "ba[ext=m4a]/ba/b"); req.addOption("-x"); req.addOption("--audio-format", "m4a") }
                else -> {
                    val h = when (d.quality) { 1 -> 720; 2 -> 480; else -> 1080 }
                    req.addOption("-f", "bv*[height<=$h][ext=mp4][vcodec!^=av01]+ba[ext=m4a]/bv*[height<=$h][vcodec!^=av01]+ba/b[height<=$h]/bv*+ba/b")
                    req.addOption("--merge-output-format", "mp4")
                }
            }
            val res = YoutubeDL.getInstance().execute(req, d.id) { p, eta, _ ->
                if (p >= 0) { d.progress = p / 100f; d.info = "%${p.toInt()}" + if (eta > 0) " · ${fmtTime(eta * 1000)} kaldı" else "" }
                onProgress()
            }
            val path = res.out.lines().map { it.trim() }.lastOrNull { it.isNotEmpty() && File(it).exists() }
            if (path != null) {
                Library.scan(ctx, path)
                d.file = path; d.progress = 1f; d.state = 2; d.info = "İndirildi · ${fmtSize(File(path).length())}"
            } else { d.state = 2; d.progress = 1f; d.info = "İndirildi (Filmler/Perde)" }
        } catch (e: YoutubeDL.CanceledException) {
            d.state = 4; d.info = "İptal edildi"
        } catch (e: Exception) {
            if (d.state != 4) { d.state = 3; d.info = Web.explain(e) }
        }
        onProgress()
    }
}

/** İndirmeler ekran kapansa da sürsün diye ön plan servisi. */
class DownloadService : Service() {
    private val exec = Executors.newSingleThreadExecutor()
    private var running = 0
    private val nm by lazy { getSystemService(NOTIFICATION_SERVICE) as NotificationManager }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notif(text: String, progress: Float?): Notification {
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("dl", "İndirmeler", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 7, Intent(this, MainActivity::class.java).putExtra("openDownloads", true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, "dl").setSmallIcon(R.drawable.ic_download).setContentTitle("Perde · İndiriliyor")
            .setContentText(text).setOnlyAlertOnce(true).setOngoing(true).setContentIntent(open)
            .apply { if (progress != null) setProgress(100, (progress * 100).toInt(), progress <= 0f) }.build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(this, 41, notif("Hazırlanıyor…", 0f),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0)
        val d = Downloads.items.firstOrNull { it.id == intent?.getStringExtra("id") }
        if (d == null) { if (running == 0) stopSelf(); return START_NOT_STICKY }
        running++
        exec.execute {
            var last = 0L
            Downloads.run(this, d) {
                val now = System.currentTimeMillis()
                if (now - last > 700) { last = now; nm.notify(41, notif("${d.title.take(40)} · ${d.info}", d.progress)) }
            }
            done(d)
        }
        return START_NOT_STICKY
    }

    private fun done(d: DL) {
        running--
        if (d.state == 2 || d.state == 3) {
            val n = NotificationCompat.Builder(this, "dl").setSmallIcon(R.drawable.ic_download)
                .setContentTitle(if (d.state == 2) "İndirme tamamlandı" else "İndirme başarısız")
                .setContentText(d.title.take(60) + if (d.state == 3) " · ${d.info}" else "").setAutoCancel(true).build()
            nm.notify(d.id.hashCode(), n)
        }
        if (running <= 0) { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE); stopSelf() }
    }

    override fun onDestroy() { exec.shutdownNow(); super.onDestroy() }
}
