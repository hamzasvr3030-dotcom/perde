@file:Suppress("UnsafeOptInUsageError")

package com.perde.oynatici

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.provider.Settings
import android.util.Rational
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import com.google.android.gms.cast.MediaMetadata as CastMeta

enum class Sheet { SUBS, AUDIO, SPEED, MORE, SLEEP, EQ, INFO, CLIP, CHAPTERS, BOOKMARKS, ONLINE, PLAYLIST }

data class Ind(val kind: String, val text: String, val level: Float? = null)

class PlayerUi {
    var showControls by mutableStateOf(true)
    var poke by mutableIntStateOf(0)
    var playing by mutableStateOf(false)
    var buffering by mutableStateOf(false)
    var pos by mutableLongStateOf(0L)
    var dur by mutableLongStateOf(0L)
    var title by mutableStateOf("")
    var vw by mutableIntStateOf(0)
    var vh by mutableIntStateOf(0)
    var par by mutableFloatStateOf(1f)
    var aspect by mutableIntStateOf(0)
    var zoom by mutableFloatStateOf(1f)
    var pan by mutableStateOf(Offset.Zero)
    var speed by mutableFloatStateOf(1f)
    var locked by mutableStateOf(false)
    var kids by mutableStateOf(false)
    var lockHint by mutableIntStateOf(0)
    var ind by mutableStateOf<Ind?>(null)
    var toast by mutableStateOf<String?>(null)
    var sheet by mutableStateOf<Sheet?>(null)
    var tracksVer by mutableIntStateOf(0)
    var inPip by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var orient by mutableIntStateOf(Prefs.orient)
    var repeat by mutableIntStateOf(Prefs.repeat)
    var shuffle by mutableStateOf(Prefs.shuffle)
    var bg by mutableStateOf(Prefs.bgPlay)
    var night by mutableStateOf(Prefs.night)
    var abA by mutableLongStateOf(-1L)
    var abB by mutableLongStateOf(-1L)
    var sleepLabel by mutableStateOf("")
    var subOff by mutableLongStateOf(0L)
    var audioOff by mutableLongStateOf(0L)
    var hasExtSub by mutableStateOf(false)
    var subStyleVer by mutableIntStateOf(0)
    var casting by mutableStateOf(false)
    var castDevice by mutableStateOf("")
    var castPlaying by mutableStateOf(false)
    var hasPrev by mutableStateOf(false)
    var hasNext by mutableStateOf(false)
    var nextTitle by mutableStateOf("")
    var key by mutableStateOf("")
    // yeni
    var resumeAsk by mutableStateOf<Long?>(null)
    var countdown by mutableStateOf<Int?>(null)
    var tapSide by mutableIntStateOf(0)
    var tapTotal by mutableIntStateOf(0)
    var thumb by mutableStateOf<ImageBitmap?>(null)
    var flash by mutableIntStateOf(0)
    var busy by mutableStateOf<String?>(null)
    var busyProgress by mutableFloatStateOf(0f)
    var chapters by mutableStateOf<List<Pair<Long, String>>>(emptyList())
    var bmVer by mutableIntStateOf(0)
    var fav by mutableStateOf(false)
}

class PlayerActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_LIST = "list"
        const val EXTRA_TITLES = "titles"
        const val EXTRA_INDEX = "index"
        const val EXTRA_BACK_TO_VIDEO = "backToVideo"
        private const val ACTION_PIP = "com.perde.oynatici.PIP_TOGGLE"
        val ASPECTS = listOf("Sığdır", "Doldur (kırp)", "Uzat", "16:9", "4:3", "Orijinal boyut")
        val ORIENTS = listOf("Videoya göre", "Otomatik döndür", "Yatay", "Dikey")
        val REPEATS = listOf("Tekrar kapalı", "Bu videoyu tekrarla", "Klasörü tekrarla")
        fun open(ctx: Context, videos: List<Video>, index: Int) =
            openKeys(ctx, videos.map { it.path }, videos.map { it.title }, index)
        fun openKeys(ctx: Context, keys: List<String>, titles: List<String>, index: Int) {
            ctx.startActivity(Intent(ctx, PlayerActivity::class.java)
                .putStringArrayListExtra(EXTRA_LIST, ArrayList(keys))
                .putStringArrayListExtra(EXTRA_TITLES, ArrayList(titles))
                .putExtra(EXTRA_INDEX, index))
        }
    }

    val ui = PlayerUi()
    lateinit var player: ExoPlayer
    lateinit var audio: AudioManager
    var subtitleView: SubtitleView? = null
    var playerView: PlayerView? = null
    private var controller: ListenableFuture<MediaController>? = null
    private var castCtx: CastContext? = null
    var castSession: CastSession? = null
    private var boostLevel = 0f
    private var tapJob: Job? = null
    private var tapHideJob: Job? = null
    private var lastTap = 0L
    private var indJob: Job? = null
    private var toastJob: Job? = null
    private var countJob: Job? = null
    private var leavingForAudio = false
    private val thumbReq = MutableStateFlow<Long?>(null)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val serial = Dispatchers.IO.limitedParallelism(1)
    private var mmr: MediaMetadataRetriever? = null
    private var mmrKey: String? = null

    val pickSub = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
            val k = curKey() ?: return@registerForActivityResult
            Prefs.setSub(k, uri.toString()); Prefs.setSubOff(k, 0)
            reloadSub(); toast("Altyazı eklendi")
        }
    }

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { togglePlay() }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            ui.playing = isPlaying
            if (isPlaying) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (isPlaying) { ui.countdown = null; countJob?.cancel() }
            updatePip()
        }
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width == 0) return
            ui.vw = videoSize.width; ui.vh = videoSize.height; ui.par = videoSize.pixelWidthHeightRatio
            applyOrientation(); updatePip()
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            ui.title = mediaItem?.mediaMetadata?.title?.toString() ?: ""
            ui.zoom = 1f; ui.pan = Offset.Zero; ui.error = null; ui.countdown = null; countJob?.cancel()
            Engine.abA = -1; Engine.abB = -1; ui.abA = -1; ui.abB = -1
            updateNav(); itemChanged()
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) onNewItem(reason)
        }
        override fun onTracksChanged(tracks: Tracks) { ui.tracksVer++ }
        override fun onCues(cueGroup: CueGroup) { subtitleView?.setCues(cueGroup.cues) }
        override fun onPlayerError(error: PlaybackException) { ui.error = describe(error); ui.showControls = true }
        override fun onPlaybackStateChanged(playbackState: Int) {
            ui.buffering = playbackState == Player.STATE_BUFFERING
            if (playbackState == Player.STATE_ENDED) { ui.showControls = true; ui.poke++ }
        }
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) onItemEnded()
        }
        override fun onPlaybackParametersChanged(p: androidx.media3.common.PlaybackParameters) { if (!longPressing) ui.speed = p.speed }
        override fun onRepeatModeChanged(repeatMode: Int) { updateNav() }
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { updateNav() }
    }

    private val castListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) { startCast(session) }
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) { startCast(session) }
        override fun onSessionEnded(session: CastSession, error: Int) { stopCast() }
        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionStartFailed(session: CastSession, error: Int) { toast("Yayın başlatılamadı") }
        override fun onSessionEnding(session: CastSession) { castSession?.remoteMediaClient?.approximateStreamPosition?.let { if (it > 0) player.seekTo(it) } }
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
        override fun onSessionResumeFailed(session: CastSession, error: Int) {}
        override fun onSessionSuspended(session: CastSession, reason: Int) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 28) window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        hideBars()
        audio = getSystemService(AUDIO_SERVICE) as AudioManager
        volumeControlStream = AudioManager.STREAM_MUSIC
        player = Engine.get(this)
        player.addListener(listener)
        ui.playing = player.isPlaying
        ui.speed = player.playbackParameters.speed
        ui.abA = Engine.abA; ui.abB = Engine.abB
        ui.audioOff = Engine.delay.delayMs
        ContextCompat.registerReceiver(this, pipReceiver, IntentFilter(ACTION_PIP), ContextCompat.RECEIVER_NOT_EXPORTED)
        castCtx = try { @Suppress("DEPRECATION") CastContext.getSharedInstance(this) } catch (_: Throwable) { null }
        castCtx?.sessionManager?.addSessionManagerListener(castListener, CastSession::class.java)

        if (!handleIntent(intent)) return

        onBackPressedDispatcher.addCallback(this) {
            when {
                ui.locked -> ui.lockHint++
                ui.sheet != null -> ui.sheet = null
                else -> closePlayer()
            }
        }
        setContent { PerdeTheme { PlayerScreen(this) } }

        controller = MediaController.Builder(this, SessionToken(this, ComponentName(this, PlaybackService::class.java))).buildAsync()
        lifecycleScope.launch {
            while (true) {
                if (!ui.casting) {
                    ui.pos = player.currentPosition.coerceAtLeast(0)
                    val d = player.duration
                    ui.dur = if (d == C.TIME_UNSET) 0 else d
                } else castSession?.remoteMediaClient?.let { c ->
                    ui.pos = c.approximateStreamPosition; ui.dur = c.streamDuration; ui.castPlaying = c.isPlaying
                }
                ui.sleepLabel = when {
                    Engine.sleepAt > 0 -> fmtTime(Engine.sleepAt - SystemClock.elapsedRealtime())
                    Engine.sleepEnd -> "video sonu"
                    else -> ""
                }
                delay(250)
            }
        }
        lifecycleScope.launch {
            thumbReq.collectLatest { ms ->
                if (ms == null) { ui.thumb = null; return@collectLatest }
                val k = curKey()
                if (!Prefs.seekThumbs || k == null || !k.startsWith("/")) return@collectLatest
                val b = withContext(serial) { frameAt(k, ms, 320, 180) }
                ui.thumb = b?.asImageBitmap()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Arka plan iş parçacığında (serial) çağrılmalı. */
    fun frameAt(k: String, ms: Long, w: Int, h: Int): Bitmap? = try {
        if (mmrKey != k) { mmr?.release(); mmr = MediaMetadataRetriever().apply { setDataSource(k) }; mmrKey = k }
        if (Build.VERSION.SDK_INT >= 27) mmr!!.getScaledFrameAtTime(ms * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, w, h)
        else mmr!!.getFrameAtTime(ms * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    } catch (_: Exception) { null }

    fun requestThumb(ms: Long?) { thumbReq.value = ms }

    // ---------------- yükleme ----------------
    fun curKey(): String? = player.currentMediaItem?.mediaId?.takeIf { it.isNotEmpty() }
    val isLocal get() = curKey()?.startsWith("/") == true

    /** false dönerse aktivite kapanıyor. */
    private fun handleIntent(i: Intent): Boolean {
        val list = i.getStringArrayListExtra(EXTRA_LIST)
        val data = i.data
        val keys: List<String> = list ?: data?.let { listOf(if (it.scheme == "file") it.path ?: it.toString() else it.toString()) } ?: emptyList()
        if (keys.isEmpty()) {
            if (player.mediaItemCount == 0) { finish(); return false }
            if (i.getBooleanExtra(EXTRA_BACK_TO_VIDEO, false) || Engine.audioOnly) {
                Engine.audioOnly = false
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false).build()
                NowState.refresh()
            } else if (NowState.isMusic) {
                startActivity(Intent(this, MainActivity::class.java).putExtra("openNow", true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                finish(); return false
            }
            ui.title = player.currentMediaItem?.mediaMetadata?.title?.toString() ?: ""
            updateNav(); refreshSubState(); itemChanged()
            return true
        }
        if (data != null && data.scheme == "content") try { contentResolver.takePersistableUriPermission(data, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
        // Başka uygulamadan ses dosyası açıldıysa müzik olarak çal
        val first = keys.first()
        val isAudio = list == null && (i.type?.startsWith("audio/") == true || isAudioName(nameOf(first)))
        if (isAudio) {
            val title = nameOf(first).substringBeforeLast('.')
            val item = MediaItem.Builder().setUri(uriOf(first)).setMediaId(first)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).build()).build()
            Engine.audioOnly = false
            player.setMediaItem(item); player.prepare(); player.play()
            startActivity(Intent(this, MainActivity::class.java).putExtra("openNow", true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finish(); return false
        }

        Engine.audioOnly = false
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false).build()
        val titles = i.getStringArrayListExtra(EXTRA_TITLES)
        val idx = i.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, keys.size - 1)
        val items = keys.mapIndexed { n, k ->
            val t = titles?.getOrNull(n) ?: nameOf(k)
            buildItem(k, t, if (n == idx) subFor(k) else null)
        }
        val saved = savedPos(keys[idx])
        val start = if (Prefs.resumeMode == 2) 0L else saved
        Engine.abA = -1; Engine.abB = -1; ui.abA = -1; ui.abB = -1
        player.setMediaItems(items, idx, start)
        player.repeatMode = repeatModeOf(ui.repeat)
        player.shuffleModeEnabled = ui.shuffle
        player.setPlaybackSpeed(1f)
        player.prepare()
        if (Prefs.resumeMode == 1 && saved > 0) { player.playWhenReady = false; ui.resumeAsk = saved }
        else { player.playWhenReady = true; if (start > 0) toast("Kaldığı yerden devam: ${fmtTime(start)}") }
        ui.title = items[idx].mediaMetadata.title?.toString() ?: ""
        updateEndBehavior(); refreshSubState(); updateNav(); itemChanged()
        return true
    }

    private fun nameOf(k: String): String = when {
        k.startsWith("/") -> File(k).name
        else -> Library.displayName(this, Uri.parse(k))
    }

    private fun uriOf(k: String): Uri = if (k.startsWith("/")) Uri.fromFile(File(k)) else Uri.parse(k)

    private fun buildItem(key: String, title: String, sub: MediaItem.SubtitleConfiguration?): MediaItem {
        val b = MediaItem.Builder().setUri(uriOf(key)).setMediaId(key)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setDisplayTitle(title).setMediaType(MediaMetadata.MEDIA_TYPE_VIDEO).build())
        val l = key.lowercase(Locale.ROOT)
        if (".m3u8" in l) b.setMimeType(MimeTypes.APPLICATION_M3U8) else if (".mpd" in l) b.setMimeType(MimeTypes.APPLICATION_MPD)
        if (sub != null) b.setSubtitleConfigurations(listOf(sub))
        return b.build()
    }

    private fun subSource(key: String): Uri? {
        Prefs.sub(key)?.let { return Uri.parse(it) }
        if (Prefs.autoSub && key.startsWith("/")) Subs.findSibling(File(key))?.let { return Uri.fromFile(it) }
        return null
    }

    private fun subFor(key: String): MediaItem.SubtitleConfiguration? =
        subSource(key)?.let { Subs.prepare(this, it, Prefs.subOff(key)) }

    private fun savedPos(key: String): Long {
        val p = Prefs.pos(key); val d = Prefs.dur(key)
        return if (p > 3000 && (d <= 0 || p < d - 5000)) p else 0
    }

    private fun onNewItem(reason: Int) {
        val k = curKey() ?: return
        val idx = player.currentMediaItemIndex
        val item = player.currentMediaItem ?: return
        val saved = if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) 0L else savedPos(k)
        val pos = if (Prefs.resumeMode == 2) 0L else saved
        if (item.localConfiguration?.subtitleConfigurations.isNullOrEmpty()) {
            val sub = subFor(k)
            if (sub != null) {
                player.replaceMediaItem(idx, item.buildUpon().setSubtitleConfigurations(listOf(sub)).build())
                player.seekTo(idx, pos)
            } else if (pos > 0) player.seekTo(pos)
        } else if (pos > 0) player.seekTo(pos)
        refreshSubState()
        if (Prefs.resumeMode == 1 && saved > 0) { player.pause(); ui.resumeAsk = saved }
        else if (pos > 0) toast("Kaldığı yerden devam: ${fmtTime(pos)}")
    }

    /** Öğe değişince: bölümler, yer imleri, favori durumu. */
    private fun itemChanged() {
        val k = curKey() ?: return
        ui.key = k; ui.fav = Favs.has(k); ui.bmVer++
        ui.chapters = emptyList()
        if (k.startsWith("/")) lifecycleScope.launch { ui.chapters = withContext(Dispatchers.IO) { Chapters.read(k) } }
        val ni = if (player.hasNextMediaItem()) player.getMediaItemAt(player.nextMediaItemIndex) else null
        ui.nextTitle = ni?.mediaMetadata?.title?.toString() ?: ""
    }

    fun answerResume(cont: Boolean) {
        val p = ui.resumeAsk ?: return
        ui.resumeAsk = null
        if (cont) player.seekTo(p) else player.seekTo(0)
        player.play()
    }

    /** Video bitince ne olacak: geri sayım / hemen sonraki / dur / uyku. */
    fun updateEndBehavior() {
        player.pauseAtEndOfMediaItems = Engine.sleepEnd ||
            (Prefs.autoNext != 1 && player.repeatMode != Player.REPEAT_MODE_ONE && player.mediaItemCount > 1)
    }

    private fun onItemEnded() {
        if (SystemClock.elapsedRealtime() - Engine.sleptAt < 1500) { updateEndBehavior(); toast("Uyku zamanlayıcısı: durduruldu"); return }
        if (Engine.audioOnly) return
        if (Prefs.autoNext == 0 && player.hasNextMediaItem()) {
            ui.countdown = 5; ui.showControls = false
            countJob?.cancel()
            countJob = lifecycleScope.launch {
                while ((ui.countdown ?: 0) > 0) { delay(1000); ui.countdown = (ui.countdown ?: 1) - 1 }
                if (ui.countdown == 0) playNextNow()
            }
        } else { ui.showControls = true; ui.poke++ }
    }

    fun playNextNow() { countJob?.cancel(); ui.countdown = null; player.seekToNextMediaItem(); player.play() }
    fun cancelCountdown() { countJob?.cancel(); ui.countdown = null; ui.showControls = true }

    fun refreshSubState() {
        val k = curKey()
        ui.hasExtSub = k != null && subSource(k) != null
        ui.subOff = if (k != null) Prefs.subOff(k) else 0
    }

    fun reloadSub() {
        val k = curKey() ?: return
        val idx = player.currentMediaItemIndex
        val item = player.currentMediaItem ?: return
        val pos = player.currentPosition
        val sub = subFor(k)
        player.replaceMediaItem(idx, item.buildUpon().setSubtitleConfigurations(listOfNotNull(sub)).build())
        player.seekTo(idx, pos)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
        refreshSubState()
    }

    fun setSubOffset(v: Long) { val k = curKey() ?: return; Prefs.setSubOff(k, v); ui.subOff = v; reloadSub() }

    fun setAudioOffset(v: Long) { Engine.delay.delayMs = v; ui.audioOff = v; player.seekTo(player.currentPosition) }

    private fun describe(e: PlaybackException): String = when (e.errorCode) {
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED -> "Bu videonun formatı (codec) bu telefonda desteklenmiyor."
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "Dosya bulunamadı. Silinmiş veya taşınmış olabilir."
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Ağ bağlantısı kurulamadı."
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Sunucu videoyu vermedi (HTTP hatası)."
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED, PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> "Dosya biçimi tanınmadı veya dosya bozuk."
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "Bu dosyaya erişim izni yok."
        else -> "Oynatılamadı: ${e.errorCodeName}"
    }

    // ---------------- kontroller ----------------
    fun toast(t: String) {
        ui.toast = t
        toastJob?.cancel(); toastJob = lifecycleScope.launch { delay(1800); ui.toast = null }
    }

    fun showInd(i: Ind?, hideAfter: Long = 0) {
        ui.ind = i; indJob?.cancel()
        if (hideAfter > 0) indJob = lifecycleScope.launch { delay(hideAfter); ui.ind = null }
    }

    fun togglePlay() {
        if (ui.casting) { castSession?.remoteMediaClient?.togglePlayback(); return }
        ui.resumeAsk?.let { answerResume(true); return }
        if (player.playbackState == Player.STATE_ENDED) { player.seekTo(0); player.play(); return }
        if (player.isPlaying) player.pause() else { if (player.playbackState == Player.STATE_IDLE) player.prepare(); player.play() }
        ui.poke++
    }

    fun seekBy(ms: Long) {
        if (ui.casting) { castSession?.remoteMediaClient?.let { it.seek(MediaSeekOptions.Builder().setPosition(it.approximateStreamPosition + ms).build()) }; return }
        player.seekTo((player.currentPosition + ms).coerceIn(0, maxOf(0, player.duration)))
    }

    fun seekTo(ms: Long) {
        if (ui.casting) { castSession?.remoteMediaClient?.seek(MediaSeekOptions.Builder().setPosition(ms).build()); return }
        player.seekTo(ms)
    }

    fun next() { if (player.hasNextMediaItem()) player.seekToNextMediaItem() }
    fun prev() { if (player.currentPosition > 5000 || !player.hasPreviousMediaItem()) player.seekTo(0) else player.seekToPreviousMediaItem() }

    private fun updateNav() { ui.hasNext = player.hasNextMediaItem(); ui.hasPrev = player.hasPreviousMediaItem() }

    fun setSpeed(s: Float) { val v = (Math.round(s * 100) / 100f).coerceIn(0.25f, 4f); player.setPlaybackSpeed(v); ui.speed = v }

    fun cycleAspect() { ui.aspect = (ui.aspect + 1) % ASPECTS.size; ui.zoom = 1f; ui.pan = Offset.Zero; toast(ASPECTS[ui.aspect]) }

    fun cycleOrient() { ui.orient = (ui.orient + 1) % ORIENTS.size; applyOrientation(); toast(ORIENTS[ui.orient]) }

    fun applyOrientation() {
        requestedOrientation = when (ui.orient) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            2 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            3 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else -> if (ui.vw == 0) ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            else if (ui.vw * ui.par >= ui.vh) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
    }

    private fun repeatModeOf(r: Int) = when (r) { 1 -> Player.REPEAT_MODE_ONE; 2 -> Player.REPEAT_MODE_ALL; else -> Player.REPEAT_MODE_OFF }
    fun cycleRepeat() {
        ui.repeat = (ui.repeat + 1) % 3; Prefs.repeat = ui.repeat; player.repeatMode = repeatModeOf(ui.repeat)
        updateEndBehavior(); toast(REPEATS[ui.repeat])
    }
    fun toggleShuffle() { ui.shuffle = !ui.shuffle; Prefs.shuffle = ui.shuffle; player.shuffleModeEnabled = ui.shuffle; toast(if (ui.shuffle) "Karışık çalma açık" else "Karışık çalma kapalı") }
    fun toggleBg() { ui.bg = !ui.bg; Prefs.bgPlay = ui.bg; toast(if (ui.bg) "Arka planda oynatma açık" else "Arka planda oynatma kapalı") }
    fun toggleNight() { ui.night = !ui.night; Engine.setNight(ui.night); toast(if (ui.night) "Gece modu açık: yüksek sesler kısılır, diyaloglar yükselir" else "Gece modu kapalı") }
    fun toggleFav() { val k = curKey() ?: return; ui.fav = Favs.toggle(k); toast(if (ui.fav) "Favorilere eklendi" else "Favorilerden çıkarıldı") }

    fun abStep() {
        when {
            Engine.abA < 0 -> { Engine.abA = player.currentPosition; toast("A noktası: ${fmtTime(Engine.abA)}") }
            Engine.abB < 0 -> {
                val p = player.currentPosition
                if (p <= Engine.abA + 500) { toast("B noktası A'dan sonra olmalı"); return }
                Engine.abB = p; player.seekTo(Engine.abA); toast("A-B tekrarı: ${fmtTime(Engine.abA)} – ${fmtTime(p)}")
            }
            else -> { Engine.abA = -1; Engine.abB = -1; toast("A-B tekrarı kapandı") }
        }
        ui.abA = Engine.abA; ui.abB = Engine.abB
    }

    fun setSleep(min: Int) {
        Engine.sleepAt = 0; Engine.sleepEnd = false
        when {
            min > 0 -> { Engine.sleepAt = SystemClock.elapsedRealtime() + min * 60_000L; toast("$min dakika sonra durdurulacak") }
            min == -1 -> { Engine.sleepEnd = true; toast("Bu video bitince durdurulacak") }
            else -> toast("Uyku zamanlayıcısı kapalı")
        }
        updateEndBehavior(); ui.sheet = null
    }

    // ---- yer imleri ----
    fun addBookmark() {
        val k = curKey() ?: return
        val t = player.currentPosition
        Bookmarks.add(k, Bookmark(t, "Yer imi · ${fmtTime(t)}")); ui.bmVer++
        toast("Yer imi eklendi: ${fmtTime(t)}")
    }

    // ---- ekran görüntüsü ----
    fun screenshot() {
        if (ui.vw == 0 || ui.vh == 0) { toast("Görüntü henüz hazır değil"); return }
        val sv = playerView?.videoSurfaceView as? SurfaceView ?: run { toast("Ekran görüntüsü alınamadı"); return }
        val s = minOf(1f, 1920f / maxOf(ui.vw, ui.vh))
        val bmp = Bitmap.createBitmap((ui.vw * s).toInt().coerceAtLeast(2), (ui.vh * s).toInt().coerceAtLeast(2), Bitmap.Config.ARGB_8888)
        ui.flash++
        try {
            PixelCopy.request(sv, bmp, { r ->
                if (r == PixelCopy.SUCCESS) lifecycleScope.launch {
                    val f = withContext(Dispatchers.IO) { MediaTools.saveImage(this@PlayerActivity, bmp, cleanTitle(ui.title)) }
                    toast(if (f != null) "Kaydedildi: Resimler/Perde" else "Kaydedilemedi")
                } else toast("Ekran görüntüsü alınamadı (korumalı içerik olabilir)")
            }, Handler(mainLooper))
        } catch (_: Exception) { toast("Ekran görüntüsü alınamadı") }
    }

    // ---- kesme / GIF ----
    fun trim(start: Long, end: Long) {
        val k = curKey() ?: return
        if (!k.startsWith("/")) { toast("Kesme sadece telefondaki videolarda yapılabilir"); return }
        ui.sheet = null; ui.busy = "Video kesiliyor"; ui.busyProgress = 0f
        MediaTools.trim(this, k, start, end, { ui.busyProgress = it / 100f }) { f, err ->
            ui.busy = null
            toast(if (f != null) "Kaydedildi: Filmler/Perde/${f.name}" else "Kesilemedi: ${err ?: "bilinmeyen hata"}")
        }
    }

    fun makeGif(start: Long, end: Long) {
        val k = curKey() ?: return
        if (!k.startsWith("/")) { toast("GIF sadece telefondaki videolardan yapılabilir"); return }
        ui.sheet = null; ui.busy = "GIF hazırlanıyor"; ui.busyProgress = 0f
        player.pause()
        lifecycleScope.launch {
            val f = MediaTools.gif(this@PlayerActivity, k, start, end) { ui.busyProgress = it }
            ui.busy = null
            toast(if (f != null) "GIF kaydedildi: Resimler/Perde" else "GIF yapılamadı")
        }
    }

    // ---- sadece ses ----
    fun audioOnly() {
        Engine.audioOnly = true
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true).build()
        if (!player.isPlaying) player.play()
        NowState.refresh()
        leavingForAudio = true
        startActivity(Intent(this, MainActivity::class.java).putExtra("openNow", true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }

    // ---- hareketler ----
    fun onTap(x: Float, w: Int) {
        if (ui.locked) { ui.lockHint++; return }
        val now = SystemClock.uptimeMillis()
        if (Prefs.gDouble && now - lastTap < 300) {
            tapJob?.cancel(); lastTap = now
            val f = x / w
            when {
                f < 0.35f -> doubleSeek(-1)
                f > 0.65f -> doubleSeek(1)
                else -> togglePlay()
            }
            return
        }
        lastTap = now
        tapJob?.cancel()
        tapJob = lifecycleScope.launch {
            if (Prefs.gDouble) delay(280)
            if (ui.tapSide == 0) { ui.showControls = !ui.showControls; ui.poke++ }
        }
    }

    private fun doubleSeek(side: Int) {
        if (ui.tapSide != side) { ui.tapSide = side; ui.tapTotal = 0 }
        ui.tapTotal += Prefs.seekStep
        seekBy(side * Prefs.seekStep * 1000L)
        playerView?.let { haptic(it) }
        tapHideJob?.cancel()
        tapHideJob = lifecycleScope.launch { delay(750); ui.tapSide = 0; ui.tapTotal = 0 }
    }

    private var dragStartVal = 0f
    private var seekStart = 0L
    private var lastLiveSeek = 0L
    var seekPreview: Long? = null

    fun seekDragStart() {
        if (ui.casting) return
        seekStart = player.currentPosition
        player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
    }
    fun seekDragUpdate(frac: Float) {
        if (ui.casting) return
        val d = player.duration.takeIf { it > 0 } ?: return
        val span = minOf(d, 600_000L).coerceAtLeast(60_000L)
        val target = (seekStart + (frac * span).toLong()).coerceIn(0, d)
        seekPreview = target
        val diff = target - seekStart
        showInd(Ind("seek", "${if (diff >= 0) "+" else "−"}${fmtTime(kotlin.math.abs(diff))}\n${fmtTime(target)} / ${fmtTime(d)}"))
        requestThumb(target)
        val now = SystemClock.uptimeMillis()
        if (now - lastLiveSeek > 150) { lastLiveSeek = now; player.seekTo(target) }
    }
    fun seekDragEnd() {
        if (ui.casting) return
        player.setSeekParameters(SeekParameters.EXACT)
        seekPreview?.let { player.seekTo(it) }
        seekPreview = null; requestThumb(null)
        showInd(ui.ind, 500)
    }

    fun brightness(): Float {
        val b = window.attributes.screenBrightness
        if (b >= 0) return b
        return try { Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f } catch (_: Exception) { 0.5f }
    }
    fun vDragStart(isBright: Boolean) { dragStartVal = if (isBright) brightness() else volume() }
    fun vDragUpdate(isBright: Boolean, delta: Float) {
        if (isBright) {
            val v = (dragStartVal + delta).coerceIn(0.01f, 1f)
            window.attributes = window.attributes.apply { screenBrightness = v }
            showInd(Ind("bright", "Parlaklık %${(v * 100).toInt()}", v))
        } else {
            val v = setVolume(dragStartVal + delta * (if (Prefs.boost) 1.3f else 1f))
            val txt = if (v > 1f) "Ses %${(v * 100).toInt()} (yükseltilmiş)" else "Ses %${(v * 100).toInt()}"
            showInd(Ind("vol", txt, v / (if (Prefs.boost) 2f else 1f)))
        }
    }
    fun vDragEnd() { showInd(ui.ind, 500) }

    fun volume(): Float {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max + boostLevel
    }
    fun setVolume(v: Float): Float {
        val c = v.coerceIn(0f, if (Prefs.boost) 2f else 1f)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        try { audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(minOf(1f, c) * max), 0) } catch (_: SecurityException) { }
        val nb = maxOf(0f, c - 1f)
        if (nb != boostLevel) { boostLevel = nb; Engine.setBoost((nb * 1500).toInt()) }
        return c
    }

    fun pinch(z: Float, p: Offset) {
        ui.zoom = (ui.zoom * z).coerceIn(0.5f, 5f)
        ui.pan = if (ui.zoom <= 1.01f) Offset.Zero else ui.pan + p
        showInd(Ind("zoom", "Yakınlaştırma %${(ui.zoom * 100).toInt()}"))
    }
    fun pinchEnd() { showInd(ui.ind, 500) }

    var longPressing = false
    private var speedBefore = 1f
    fun longStart() {
        if (ui.casting || !player.isPlaying) return
        longPressing = true; speedBefore = ui.speed
        player.setPlaybackSpeed(2f); showInd(Ind("fast", "2× hız"))
        playerView?.let { haptic(it) }
    }
    fun longEnd() {
        if (!longPressing) return
        longPressing = false; player.setPlaybackSpeed(speedBefore); showInd(null)
    }

    // ---- kilit ----
    fun lock() { ui.locked = true; ui.showControls = false; ui.sheet = null; toast("Ekran kilitlendi") }
    fun kidsLock() {
        ui.locked = true; ui.kids = true; ui.showControls = false; ui.sheet = null
        try { startLockTask() } catch (_: Exception) { }
        toast("Çocuk kilidi: açmak için kilit simgesine 3 saniye basılı tut")
    }
    fun unlock() {
        if (ui.kids) try { stopLockTask() } catch (_: Exception) { }
        ui.locked = false; ui.kids = false; ui.showControls = true; ui.poke++
    }

    // ---- izler ----
    data class TrackItem(val group: Tracks.Group, val index: Int, val label: String, val selected: Boolean)

    fun tracks(type: Int): List<TrackItem> {
        val out = ArrayList<TrackItem>()
        var n = 1
        for (g in player.currentTracks.groups) {
            if (g.type != type) continue
            for (i in 0 until g.length) {
                if (!g.isTrackSupported(i, true)) continue
                val f = g.getTrackFormat(i)
                val lang = f.language?.takeIf { it != "und" }?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale.forLanguageTag("tr")).replaceFirstChar { c -> c.titlecase() } }
                val extra = if (type == C.TRACK_TYPE_AUDIO) listOfNotNull(
                    f.sampleMimeType?.substringAfter('/')?.uppercase(), if (f.channelCount > 0) "${f.channelCount} kanal" else null
                ).joinToString(" · ") else f.sampleMimeType?.substringAfter('/')?.uppercase() ?: ""
                val name = listOfNotNull(f.label, lang).distinct().joinToString(" – ").ifEmpty { "Parça $n" }
                out += TrackItem(g, i, if (extra.isNotEmpty()) "$name  ($extra)" else name, g.isTrackSelected(i))
                n++
            }
        }
        return out
    }

    fun selectTrack(t: TrackItem, type: Int) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(type, false)
            .setOverrideForType(TrackSelectionOverride(t.group.mediaTrackGroup, t.index)).build()
    }
    fun subsOff() {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
    }
    val subsDisabled get() = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)

    fun removeExtSub() {
        val k = curKey() ?: return
        Prefs.setSub(k, null); Prefs.setSubOff(k, 0)
        val idx = player.currentMediaItemIndex; val item = player.currentMediaItem ?: return; val pos = player.currentPosition
        player.replaceMediaItem(idx, item.buildUpon().setSubtitleConfigurations(emptyList()).build())
        player.seekTo(idx, pos)
        ui.hasExtSub = false; ui.subOff = 0
    }

    /** Online bulunan altyazıyı videonun yanına (yazılamazsa uygulama klasörüne) kaydedip yükler. */
    fun useDownloadedSub(f: File) {
        val k = curKey() ?: return
        Prefs.setSub(k, Uri.fromFile(f).toString()); Prefs.setSubOff(k, 0)
        reloadSub(); toast("Altyazı indirildi ve yüklendi")
    }
    fun subDownloadDir(): File {
        val k = curKey()
        val d = if (k != null && k.startsWith("/")) File(k).parentFile else null
        return if (d != null && d.canWrite()) d else File(getExternalFilesDir(null) ?: filesDir, "altyazi")
    }
    fun subBaseName(): String = curKey()?.let { if (it.startsWith("/")) File(it).nameWithoutExtension else cleanTitle(ui.title) } ?: "altyazi"

    fun infoLines(): List<Pair<String, String>> {
        val l = ArrayList<Pair<String, String>>()
        val k = curKey() ?: ""
        l += "Ad" to ui.title
        if (k.startsWith("/")) { val f = File(k); l += "Konum" to (f.parent ?: ""); l += "Boyut" to fmtSize(f.length()) } else l += "Kaynak" to k
        l += "Süre" to fmtTime(ui.dur)
        player.videoFormat?.let { f ->
            l += "Görüntü" to "${f.width}×${f.height}" + (if (f.frameRate > 0) String.format(Locale.ROOT, " · %.2f fps", f.frameRate) else "")
            l += "Video codec" to (f.codecs ?: f.sampleMimeType ?: "?")
            if (f.bitrate > 0) l += "Video bit hızı" to "${f.bitrate / 1000} kbps"
        }
        player.audioFormat?.let { f ->
            l += "Ses codec" to (f.codecs ?: f.sampleMimeType ?: "?")
            l += "Ses" to "${f.sampleRate} Hz · ${f.channelCount} kanal"
        }
        if (Engine.videoDecoder.isNotEmpty()) l += "Video çözücü" to Engine.videoDecoder + if (Engine.videoDecoder.startsWith("c2.android") || Engine.videoDecoder.startsWith("OMX.google")) " (yazılım)" else " (donanım)"
        if (Engine.audioDecoder.isNotEmpty()) l += "Ses çözücü" to Engine.audioDecoder
        if (ui.chapters.isNotEmpty()) l += "Bölüm sayısı" to "${ui.chapters.size}"
        val watched = Prefs.sp.getLong("sv:$k", 0)
        if (watched > 0) l += "Toplam izleme" to fmtDur(watched)
        return l
    }

    fun applySubStyle() { ui.subStyleVer++ }

    // ---------------- PiP ----------------
    private fun pipSupported() = Build.VERSION.SDK_INT >= 26 && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private fun pipParams(): PictureInPictureParams {
        val b = PictureInPictureParams.Builder()
        if (ui.vw > 0 && ui.vh > 0) {
            val r = (ui.vw * ui.par / ui.vh).coerceIn(0.42f, 2.38f)
            b.setAspectRatio(Rational((r * 1000).toInt(), 1000))
        }
        val pi = PendingIntent.getBroadcast(this, 1, Intent(ACTION_PIP).setPackage(packageName), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val icon = Icon.createWithResource(this, if (ui.playing) R.drawable.ic_pause else R.drawable.ic_play)
        b.setActions(listOf(RemoteAction(icon, if (ui.playing) "Duraklat" else "Oynat", "Oynat/Duraklat", pi)))
        if (Build.VERSION.SDK_INT >= 31) b.setAutoEnterEnabled(Prefs.autoPip && ui.playing && !ui.locked && !ui.casting)
        return b.build()
    }

    fun updatePip() { if (pipSupported()) try { setPictureInPictureParams(pipParams()) } catch (_: Exception) { } }

    fun enterPip() {
        if (!pipSupported()) { toast("Bu telefon küçük pencereyi desteklemiyor"); return }
        try { enterPictureInPictureMode(pipParams()) } catch (_: Exception) { toast("Küçük pencere açılamadı") }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < 31 && Prefs.autoPip && player.isPlaying && !ui.locked && !ui.casting && !leavingForAudio) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        ui.inPip = isInPictureInPictureMode
        if (isInPictureInPictureMode) { ui.showControls = false; ui.sheet = null }
        else if (lifecycle.currentState == Lifecycle.State.CREATED) { if (!Prefs.bgPlay) player.pause() }
    }

    // ---------------- Chromecast ----------------
    private fun startCast(s: CastSession) {
        castSession = s
        val k = curKey() ?: return
        val url = if (k.startsWith("http")) k else { LocalServer.start(this); LocalServer.streamUrl(k) }
        if (url == null) { toast("Wi-Fi adresi bulunamadı"); return }
        val name = ui.title.ifEmpty { nameOf(k) }
        val meta = CastMeta(CastMeta.MEDIA_TYPE_MOVIE).apply { putString(CastMeta.KEY_TITLE, name) }
        val mime = when (nameOf(k).substringAfterLast('.').lowercase()) { "mkv" -> "video/x-matroska"; "webm" -> "video/webm"; "m3u8" -> "application/x-mpegURL"; else -> "video/mp4" }
        val info = MediaInfo.Builder(url).setStreamType(MediaInfo.STREAM_TYPE_BUFFERED).setContentType(mime).setMetadata(meta).build()
        val req = MediaLoadRequestData.Builder().setMediaInfo(info).setAutoplay(true).setCurrentTime(player.currentPosition).build()
        s.remoteMediaClient?.load(req)
        player.pause()
        ui.casting = true; ui.castDevice = s.castDevice?.friendlyName ?: "TV"; ui.showControls = true
    }

    private fun stopCast() {
        if (!ui.casting) return
        ui.casting = false; castSession = null
        toast("Yayın bitti, telefonda devam ediliyor")
    }

    fun endCast() { castCtx?.sessionManager?.endCurrentSession(true) }

    // ---------------- yaşam döngüsü ----------------
    private fun hideBars() {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.hide(WindowInsetsCompat.Type.systemBars())
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onResume() { super.onResume(); hideBars() }

    override fun onStop() {
        super.onStop()
        Engine.savePos()
        if (!isInPictureInPictureMode && !Prefs.bgPlay && !isChangingConfigurations && !ui.casting && !Engine.audioOnly) player.pause()
    }

    fun closePlayer() {
        Engine.savePos()
        if (ui.kids) try { stopLockTask() } catch (_: Exception) { }
        if (ui.casting) endCast()
        stopService(Intent(this, PlaybackService::class.java))
        player.removeListener(listener)
        Engine.release()
        finish()
    }

    override fun onDestroy() {
        if (Engine.player === player) player.removeListener(listener)
        try { unregisterReceiver(pipReceiver) } catch (_: Exception) { }
        castCtx?.sessionManager?.removeSessionManagerListener(castListener, CastSession::class.java)
        controller?.let { MediaController.releaseFuture(it) }
        val r = mmr; mmr = null
        kotlinx.coroutines.CoroutineScope(serial).launch { try { r?.release() } catch (_: Exception) { } }
        super.onDestroy()
    }
}
