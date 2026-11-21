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

    private val ticker = object : Runnable {
        override fun run() {
            val p = player ?: return
            if (abA >= 0 && abB > abA && p.currentPosition >= abB) p.seekTo(abA)
            if (sleepAt > 0 && SystemClock.elapsedRealtime() >= sleepAt) { sleepAt = 0; p.pause() }
            if (p.isPlaying && SystemClock.elapsedRealtime() - lastSave > 5000) savePos()
            handler.postDelayed(this, 250)
        }
    }

    fun get(ctx: Context): ExoPlayer {
        player?.let { if (builtSw == Prefs.decoderSw || it.mediaItemCount > 0) return it; release() }
        val app = ctx.applicationContext
        val rf = object : DefaultRenderersFactory(app) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(false)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessorChain(DefaultAudioSink.DefaultAudioProcessorChain(delay))
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
            .build()
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setPreferredTextLanguage("tr").setPreferredAudioLanguage("tr")
            .setSelectUndeterminedTextLanguage(true).build()
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { if (!isPlaying) savePos() else ensureEffects() }
            override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) {
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                    val k = old.mediaItem?.mediaId ?: return
                    val d = Prefs.dur(k).takeIf { it > 0 } ?: old.positionMs
                    Prefs.savePos(k, d, d)
                }
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

    fun release() {
        savePos()
        handler.removeCallbacks(ticker)
        releaseEffects()
        player?.release()
        player = null
        abA = -1; abB = -1; sleepAt = 0
        delay.delayMs = 0
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

/** http(s)/file/content adreslerini varsayılan kaynağa, smb:// adreslerini SMB kaynağına yönlendirir. */
class AnyDataSource(private val ctx: Context) : DataSource {
    private val listeners = ArrayList<TransferListener>()
    private var cur: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) { listeners += transferListener }

    override fun open(dataSpec: DataSpec): Long {
        val ds: DataSource = if (dataSpec.uri.scheme.equals("smb", true)) SmbDataSource()
        else DefaultDataSource.Factory(ctx, DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true).setUserAgent("Perde/1.0")).createDataSource()
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
