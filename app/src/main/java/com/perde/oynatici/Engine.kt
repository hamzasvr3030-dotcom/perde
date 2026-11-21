package com.perde.oynatici

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import java.nio.ByteBuffer
import kotlin.math.abs

/** Uygulama genelinde tek oynatıcı: hem ekran hem arka plan servisi bunu kullanır. */
object Engine {
    var player: ExoPlayer? = null
        private set
    private var builtSw = false
    val delay = AudioDelayProcessor()
    val night = NightProcessor()
    val viz = VizProcessor()
    val fx = FxProcessor()
    var audioOnly = false
    var sleepEnd = false
    var sleptAt = 0L
    private var appCtx: Context? = null

    var eq: Equalizer? = null; private set
    var bassBoost: BassBoost? = null; private set
    private var loud: LoudnessEnhancer? = null
    private var effectsSid = C.AUDIO_SESSION_ID_UNSET

    var abA = -1L
    var abB = -1L
    var sleepAt = 0L
    var videoDecoder = ""
    var audioDecoder = ""

    private val handler = Handler(Looper.getMainLooper())
    private var lastSave = 0L
    private var tickN = 0
    private var playKey: String? = null
    private var playMs = 0L
    private var counted = false

    private val ticker = object : Runnable {
        override fun run() {
            val p = player ?: return
            if (abA >= 0 && abB > abA && p.currentPosition >= abB) p.seekTo(abA)
            if (sleepAt > 0 && SystemClock.elapsedRealtime() >= sleepAt) { sleepAt = 0; p.pause() }
            fade(p)
            if (++tickN % 5 == 0) {
                val k = p.currentMediaItem?.mediaId
                if (k != playKey) { playKey = k; playMs = 0; counted = false }
                if (p.isPlaying) {
                    Stats.tick(k, 250)
                    playMs += 250
                    // 30 sn'den fazla dinlenen şarkı "çalındı" sayılır (akıllı listeler için)
                    if (!counted && playMs >= 30_000 && k != null) { counted = true; Stats.countPlay(k); ListenLog.add(k, p.currentMediaItem?.mediaMetadata) }
                }
                if (p.isPlaying && SystemClock.elapsedRealtime() - lastSave > 5000) savePos()
            }
            if (Viz.enabled && !p.isPlaying) Viz.decay()
            handler.postDelayed(this, 50)
        }
    }

    private var lastMusicKey: String? = null

    // ---- gerçek karıştırma: liste fiziksel olarak karıştırılır, kapatınca eski sıraya döner ----
    var shuffled = false; private set
    private var origOrder: List<MediaItem> = emptyList()

    fun setShuffle(on: Boolean) {
        val p = player ?: return
        if (p.shuffleModeEnabled) p.shuffleModeEnabled = false
        val n = p.mediaItemCount; val cur = p.currentMediaItemIndex
        if (on && !shuffled && n > 1) {
            origOrder = (0 until n).map { p.getMediaItemAt(it) }
            val curItem = origOrder[cur]
            val now = System.currentTimeMillis()
            val mixed = SmartShuffle.order(origOrder.filterIndexed { i, _ -> i != cur }, { it.mediaMetadata.artist?.toString() ?: "" },
                { now - Prefs.playedAt(it.mediaId) < 12 * 3_600_000L }, curItem.mediaMetadata.artist?.toString())
            if (cur + 1 < n) p.removeMediaItems(cur + 1, n)
            if (cur > 0) p.removeMediaItems(0, cur)
            p.addMediaItems(mixed)
        } else if (!on && shuffled && origOrder.isNotEmpty() && n > 0) {
            val now = (0 until n).map { p.getMediaItemAt(it) }
            val nowIds = now.map { it.mediaId }.toHashSet(); val origIds = origOrder.map { it.mediaId }.toHashSet()
            val full = origOrder.filter { it.mediaId in nowIds } + now.filter { it.mediaId !in origIds }
            val oi = full.indexOfFirst { it.mediaId == p.currentMediaItem?.mediaId }
            if (oi >= 0) {
                if (cur + 1 < n) p.removeMediaItems(cur + 1, n)
                if (cur > 0) p.removeMediaItems(0, cur)
                if (oi > 0) p.addMediaItems(0, full.subList(0, oi))
                if (oi + 1 < full.size) p.addMediaItems(full.subList(oi + 1, full.size))
            }
        }
        if (!on) origOrder = emptyList()
        shuffled = on && n > 1
        NowState.refresh()
    }

    /** Yeni liste yüklenince karıştırma durumunu sıfırla. */
    fun resetShuffle() { shuffled = false; origOrder = emptyList(); player?.let { if (it.shuffleModeEnabled) it.shuffleModeEnabled = false } }

    fun enqueue(ctx: Context, songs: List<Song>, next: Boolean) {
        val p = player
        if (p == null || p.mediaItemCount == 0 || !NowState.isMusic) { playSongs(ctx, songs, 0); return }
        val items = songs.map { Music.item(it) }
        if (next) p.addMediaItems(p.currentMediaItemIndex + 1, items) else p.addMediaItems(items)
        if (shuffled) origOrder = origOrder + items
    }
    private var musicBoostOn = false

    /** Müzikte ses yükseltme: %100 + musicBoost (en çok %200), videodaki gibi LoudnessEnhancer ile. */
    fun applyMusicBoost() {
        val mb = Prefs.musicBoost * 15   // %100 ek ≈ 1500 mB
        setBoost(mb); musicBoostOn = mb > 0
    }

    /** Son müzik kuyruğunu sakla (kulaklık takınca devam için) ve şarkıya göre ses seviyesini hatırla. */
    private fun musicBookkeeping(p: ExoPlayer) {
        val item = p.currentMediaItem ?: return
        fx.enabled = true
        if (item.mediaMetadata.mediaType != androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC) {
            if (musicBoostOn) { setBoost(0); musicBoostOn = false }   // müzik yükseltmesi videoya taşmasın
            fx.enabled = false
            return
        }
        applyMusicBoost()
        LastQueue.save((0 until minOf(p.mediaItemCount, 500)).map { p.getMediaItemAt(it).mediaId }, p.currentMediaItemIndex, p.currentPosition)
        val k = item.mediaId
        if (k == lastMusicKey) return
        // her şarkı kaldığı yerden sürer (sonuna kadar dinlenenler baştan başlar)
        if (Prefs.resumeMusic && lastMusicKey != null || Prefs.resumeMusic && p.currentPosition < 1500) {
            val sp = Prefs.pos(k); val d = Prefs.dur(k)
            if (sp > 5000 && (d <= 0 || sp < d - 10_000) && p.currentPosition < 3000) p.seekTo(sp)
        }
        val am = appCtx?.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return
        val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).toFloat()
        lastMusicKey?.let { Levels.saveVol(it, am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) / max) }
        Levels.vol(k)?.let { v -> try { am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, Math.round(v.coerceIn(0f, 1f) * max), 0) } catch (_: Exception) { } }
        lastMusicKey = k
        appCtx?.let { CoverTint.update(it, k) }
    }

    /** Müzikte şarkı sonunda sesi yavaşça kıs, yeni şarkıda yavaşça aç. */
    private fun fade(p: ExoPlayer) {
        val cf = Prefs.crossfade * 1000L
        val music = p.currentMediaItem?.mediaMetadata?.mediaType == androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC
        if (cf <= 0 || !music) { if (p.volume != 1f) p.volume = 1f; return }
        val d = p.duration; val pos = p.currentPosition
        if (d <= 0 || d == C.TIME_UNSET || d < cf * 3) { if (p.volume != 1f) p.volume = 1f; return }
        val v = when {
            d - pos < cf && p.hasNextMediaItem() -> ((d - pos).toFloat() / cf).coerceIn(0.05f, 1f)
            pos < cf -> (pos.toFloat() / cf).coerceIn(0.05f, 1f)
            else -> 1f
        }
        if (kotlin.math.abs(p.volume - v) > 0.01f) p.volume = v
    }

    fun get(ctx: Context): ExoPlayer {
        player?.let { if (builtSw == Prefs.decoderSw || it.mediaItemCount > 0) return it; release() }
        val app = ctx.applicationContext
        appCtx = app
        night.on = Prefs.night
        fx.eightD = Prefs.sp.getBoolean("fx8d", false); fx.reverb = Prefs.sp.getInt("fxRev", 0)
        val rf = object : DefaultRenderersFactory(app) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(false)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessorChain(DefaultAudioSink.DefaultAudioProcessorChain(delay, night, fx, viz))
                    .build()
        }
        rf.setExtensionRendererMode(
            if (Prefs.decoderSw) DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
            else DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
        )
        rf.setEnableDecoderFallback(true)
        builtSw = Prefs.decoderSw

        val p = ExoPlayer.Builder(app, rf)
            .setMediaSourceFactory(DefaultMediaSourceFactory(AnyDataSource.Factory(app)))
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        p.skipSilenceEnabled = false
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setPreferredTextLanguage("tr").setPreferredAudioLanguage("tr")
            .setSelectUndeterminedTextLanguage(true).build()
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { if (!isPlaying) { savePos(); Stats.flush() } else { ensureEffects(); appCtx?.let { ServiceLink.ensure(it) } } }
            // bildirimden/kulaklıktan karıştırma açılırsa da listeyi gerçekten karıştır (sıra ekranında görünsün)
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { if (shuffleModeEnabled) handler.post { setShuffle(true) } }
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_IS_PLAYING_CHANGED)) musicBookkeeping(p)
                if (events.containsAny(Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_MEDIA_METADATA_CHANGED, Player.EVENT_TIMELINE_CHANGED)) {
                    NowState.refresh(); appCtx?.let { PerdeWidget.update(it); MusicWidget.update(it); PlayTile.request(it) }
                }
            }
            override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) {
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                    val k = old.mediaItem?.mediaId ?: return
                    val d = Prefs.dur(k).takeIf { it > 0 } ?: old.positionMs
                    Prefs.savePos(k, d, d)
                }
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (playWhenReady || reason != Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) return
                if (sleepEnd) { sleepEnd = false; sleptAt = SystemClock.elapsedRealtime(); p.pauseAtEndOfMediaItems = false }
                else if (audioOnly && p.hasNextMediaItem()) { p.seekToNextMediaItem(); p.play() }
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    val k = p.currentMediaItem?.mediaId ?: return
                    if (p.duration > 0) Prefs.savePos(k, p.duration, p.duration)
                }
            }
        })
        p.addAnalyticsListener(object : AnalyticsListener {
            override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) { videoDecoder = decoderName }
            override fun onAudioDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) { audioDecoder = decoderName }
        })
        player = p
        handler.removeCallbacks(ticker); handler.post(ticker)
        return p
    }

    fun savePos() {
        val p = player ?: return
        val k = p.currentMediaItem?.mediaId ?: return
        if (k.isEmpty()) return
        val d = p.duration
        if (d > 0 && d != C.TIME_UNSET) Prefs.savePos(k, p.currentPosition, d)
        Prefs.lastKey = k
        Prefs.lastTitle = p.currentMediaItem?.mediaMetadata?.title?.toString() ?: ""
        lastSave = SystemClock.elapsedRealtime()
    }

    fun ensureEffects() {
        val sid = player?.audioSessionId ?: return
        if (sid == C.AUDIO_SESSION_ID_UNSET || sid == effectsSid) return
        releaseEffects()
        effectsSid = sid
        eq = try { Equalizer(0, sid) } catch (_: Throwable) { null }
        bassBoost = try { BassBoost(0, sid) } catch (_: Throwable) { null }
        loud = try { LoudnessEnhancer(sid) } catch (_: Throwable) { null }
        applyEq()
    }

    fun applyEq() {
        eq?.let { e ->
            try {
                if (Prefs.eqPreset >= 0 && Prefs.eqPreset < e.numberOfPresets) e.usePreset(Prefs.eqPreset.toShort())
                else Prefs.eqBands.split(',').mapNotNull { it.toShortOrNull() }.forEachIndexed { i, v ->
                    if (i < e.numberOfBands) e.setBandLevel(i.toShort(), v)
                }
                e.enabled = Prefs.eqOn
            } catch (_: Throwable) { }
        }
        bassBoost?.let { b ->
            try { if (b.strengthSupported) b.setStrength(Prefs.bass.toShort()); b.enabled = Prefs.eqOn && Prefs.bass > 0 } catch (_: Throwable) { }
        }
    }

    /** Sesi %100'ün üstüne çıkarmak için millibel cinsinden kazanç. */
    fun setBoost(mb: Int) {
        ensureEffects()
        loud?.let { try { it.setTargetGain(mb); it.enabled = mb > 0 } catch (_: Throwable) { } }
    }

    private fun releaseEffects() {
        try { eq?.release() } catch (_: Throwable) { }
        try { bassBoost?.release() } catch (_: Throwable) { }
        try { loud?.release() } catch (_: Throwable) { }
        eq = null; bassBoost = null; loud = null; effectsSid = C.AUDIO_SESSION_ID_UNSET
    }

    /** Müzik çalmaya başla (video oynatıcıyla aynı motoru kullanır). */
    fun playSongs(ctx: Context, songs: List<Song>, index: Int, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        savePos()          // yarım kalan video/şarkının yeri kaybolmasın
        musicSnap = null
        audioOnly = false
        val p = get(ctx)
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false).build()
        p.pauseAtEndOfMediaItems = false
        resetShuffle()
        p.setMediaItems(songs.map { Music.item(it) }, index.coerceIn(0, songs.size - 1), 0)
        if (shuffle) setShuffle(true)
        p.repeatMode = when (Prefs.musicRepeat) { 1 -> Player.REPEAT_MODE_ONE; 2 -> Player.REPEAT_MODE_ALL; else -> Player.REPEAT_MODE_OFF }
        p.setPlaybackSpeed(1f)
        p.skipSilenceEnabled = Prefs.skipSilence
        p.prepare(); p.play()
        NowState.refresh()
        ServiceLink.ensure(ctx)
    }

    // ---- video açılınca müzik sırası kaybolmasın ----
    private class MusicSnap(val items: List<MediaItem>, val idx: Int, val pos: Long, val shuffled: Boolean, val orig: List<MediaItem>, val repeat: Int)
    private var musicSnap: MusicSnap? = null
    val hasMusicSnap get() = musicSnap != null

    /** Video yüklenmeden hemen önce çağrılır: o an müzik varsa sırasını ve yerini saklar. */
    fun snapMusic() {
        val p = player ?: return
        if (p.mediaItemCount == 0 || p.currentMediaItem?.mediaMetadata?.mediaType != androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC) return
        savePos()
        musicSnap = MusicSnap((0 until p.mediaItemCount).map { p.getMediaItemAt(it) }, p.currentMediaItemIndex, p.currentPosition.coerceAtLeast(0),
            shuffled, origOrder, p.repeatMode)
    }

    /** Video kapanınca müziği kaldığı yerde, duraklatılmış olarak geri yükler. */
    fun restoreMusic(ctx: Context): Boolean {
        val s = musicSnap ?: return false
        musicSnap = null
        val p = get(ctx)
        audioOnly = false
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false).build()
        p.pauseAtEndOfMediaItems = false
        resetShuffle()
        p.setMediaItems(s.items, s.idx.coerceIn(0, s.items.size - 1), s.pos)
        shuffled = s.shuffled; origOrder = s.orig
        p.repeatMode = s.repeat
        p.setPlaybackSpeed(1f)
        p.skipSilenceEnabled = Prefs.skipSilence
        p.playWhenReady = false
        p.prepare()
        NowState.refresh()
        ServiceLink.ensure(ctx)
        appCtx?.let { PerdeWidget.update(it); MusicWidget.update(it) }
        return true
    }

    fun setNight(on: Boolean) {
        Prefs.night = on; night.on = on
        player?.let { it.seekTo(it.currentPosition) }
    }

    fun release() {
        ServiceLink.drop()
        Stats.flush()
        savePos()
        handler.removeCallbacks(ticker)
        releaseEffects()
        player?.release()
        player = null
        abA = -1; abB = -1; sleepAt = 0; sleepEnd = false; audioOnly = false; ThemeState.dyn = null
        delay.delayMs = 0
        NowState.refresh(); appCtx?.let { PerdeWidget.update(it); MusicWidget.update(it); PlayTile.request(it) }
    }
}

/**
 * Ses senkronu: pozitif değer sesi geciktirir (başa sessizlik ekler), negatif değer sesi öne alır
 * (baştan örnek atar). Her sarma/temizlemede yeniden uygulanır.
 */
class AudioDelayProcessor : BaseAudioProcessor() {
    @Volatile var delayMs = 0L
    private var silence = 0L
    private var drop = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT)
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        return inputAudioFormat
    }

    override fun onFlush() {
        val f = inputAudioFormat
        val bytes = abs(delayMs) * f.sampleRate / 1000 * f.bytesPerFrame
        silence = if (delayMs > 0) bytes else 0
        drop = if (delayMs < 0) bytes else 0
    }

    override fun onReset() { silence = 0; drop = 0 }

    override fun queueInput(inputBuffer: ByteBuffer) {
        var rem = inputBuffer.remaining()
        if (drop > 0) {
            val d = minOf(drop, rem.toLong()).toInt()
            inputBuffer.position(inputBuffer.position() + d); drop -= d; rem -= d
        }
        val pad = silence.toInt()
        if (rem == 0 && pad == 0) return
        val out = replaceOutputBuffer(pad + rem)
        repeat(pad) { out.put(0) }
        silence = 0
        out.put(inputBuffer)
        out.flip()
    }
}

/**
 * Gece modu: dinamik aralık sıkıştırma. Patlama/müzik gibi yüksek sesleri bastırır,
 * fısıltı gibi alçak sesleri yükseltir. Yalnızca 16 bit PCM üzerinde çalışır.
 */
class NightProcessor : BaseAudioProcessor() {
    @Volatile var on = false
    private var env = 0f
    private var gain = 1f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        return inputAudioFormat
    }

    override fun onFlush() { env = 0f; gain = 1f }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val n = inputBuffer.remaining()
        if (n == 0) return
        val out = replaceOutputBuffer(n)
        if (!on) { out.put(inputBuffer); out.flip(); return }
        val src = inputBuffer.order(java.nio.ByteOrder.nativeOrder())
        while (src.remaining() >= 2) {
            val x = src.short / 32768f
            val a = abs(x)
            env += if (a > env) (a - env) * 0.02f else (a - env) * 0.0004f
            val target = (0.2f / maxOf(env, 0.02f)).coerceIn(0.35f, 3.2f)
            gain += (target - gain) * 0.0008f
            var y = (x * gain).coerceIn(-1.5f, 1.5f)
            y -= y * y * y / 6.75f
            out.putShort((y * 32767f).toInt().coerceIn(-32768, 32767).toShort())
        }
        out.flip()
    }
}

/** http(s)/file/content adreslerini varsayılan kaynağa, smb:// adreslerini SMB kaynağına yönlendirir. */
class AnyDataSource(private val ctx: Context) : DataSource {
    private val listeners = ArrayList<TransferListener>()
    private var cur: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) { listeners += transferListener }

    override fun open(dataSpec: DataSpec): Long {
        val ds: DataSource = if (dataSpec.uri.scheme.equals("smb", true)) SmbDataSource()
        else {
            val hdr = dataSpec.uri.host?.let { Web.headers[it] } ?: emptyMap()
            val ua = hdr.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value ?: "Mozilla/5.0 (Linux; Android) Kadraj/1.3"
            val http = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true).setUserAgent(ua)
                .setConnectTimeoutMs(15_000).setReadTimeoutMs(20_000)
                .setDefaultRequestProperties(hdr.filterKeys { !it.equals("User-Agent", true) })
            DefaultDataSource.Factory(ctx, http).createDataSource()
        }
        listeners.forEach { ds.addTransferListener(it) }
        cur = ds
        return ds.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = cur!!.read(buffer, offset, length)
    override fun getUri(): Uri? = cur?.uri
    override fun getResponseHeaders(): Map<String, List<String>> = cur?.responseHeaders ?: emptyMap()
    override fun close() { try { cur?.close() } finally { cur = null } }

    class Factory(private val ctx: Context) : DataSource.Factory {
        override fun createDataSource(): DataSource = AnyDataSource(ctx)
    }
}
