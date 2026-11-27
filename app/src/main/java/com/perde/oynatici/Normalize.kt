package com.perde.oynatici

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Şarkılar arası ses eşitleme (Spotify'daki "ses düzeyini normalleştir" gibi).
 *
 * Her şarkının ortalama gücünü (RMS) ölçer ve hedef düzeye çeker. Şarkının ilk
 * saniyelerinde hızlı öğrenir, sonra kazanç neredeyse sabit kalır — böylece şarkı
 * içindeki sessiz/gürültülü bölümler ezilmez, sadece şarkılar birbirine denkleşir.
 * Öğrenilen kazanç şarkı için saklanır; aynı şarkı tekrar çalınca doğrudan doğru
 * düzeyde başlar. Yumuşak sınırlayıcı kırpılmayı (cızırtıyı) önler.
 */
class NormalizeProcessor : BaseAudioProcessor() {
    @Volatile var enabled = true
    /** Şarkı değiştiğinde Engine çağırır: öğrenilmiş kazancı (varsa) yükler. */
    @Volatile private var pendingKey: String? = null
    private var key: String? = null

    private var gain = 1f
    private var target = 1f
    private var sumSq = 0.0
    private var count = 0L
    private var learnedFrames = 0L
    private var sampleRate = 44100
    private var saved = false

    fun startSong(k: String) { pendingKey = k }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        sampleRate = inputAudioFormat.sampleRate.takeIf { it > 0 } ?: 44100
        return inputAudioFormat
    }

    private fun switchTo(k: String) {
        saveLearned()
        key = k; sumSq = 0.0; count = 0; learnedFrames = 0; saved = false
        val g = Prefs.sp.getFloat("ng:$k", -1f)
        if (g > 0f) { target = g; learnedFrames = Long.MAX_VALUE / 2 } else target = 1f
        // gain anında atlamaz; aşağıda örnek örnek yaklaşır
    }

    private fun saveLearned() {
        val k = key ?: return
        if (!saved && learnedFrames in (sampleRate * 20L)..(Long.MAX_VALUE / 4)) {
            Prefs.sp.edit().putFloat("ng:$k", target).apply(); saved = true
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        pendingKey?.let { pendingKey = null; if (it != key) switchTo(it) }
        val n = inputBuffer.remaining()
        if (n == 0) return
        val out = replaceOutputBuffer(n)
        if (!enabled) { gain = 1f; out.put(inputBuffer); out.flip(); return }
        val src = inputBuffer.order(ByteOrder.nativeOrder())
        val ch = maxOf(1, inputAudioFormat.channelCount)
        var i = 0
        while (src.remaining() >= 2) {
            val x = src.short / 32768f
            // ölçüm: sessiz kısımları (fade-in/out, ara boşluklar) sayma
            if (abs(x) > 0.003f) { sumSq += (x * x).toDouble(); count++ }
            i++
            if (i % (ch * 1024) == 0 && count > 4096) {
                learnedFrames += 1024
                val rms = sqrt(sumSq / count).toFloat()
                val want = (TARGET_RMS / maxOf(rms, 0.005f)).coerceIn(MIN_G, MAX_G)
                // ilk 15 sn hızlı, sonra çok yavaş: şarkı içi dinamik korunur
                val rate = if (learnedFrames < sampleRate * 15L) 0.08f else 0.004f
                target += (want - target) * rate
                if (!saved && learnedFrames > sampleRate * 20L && learnedFrames < Long.MAX_VALUE / 4) saveLearned()
            }
            gain += (target - gain) * 0.0005f
            var y = x * gain
            // yumuşak sınırlayıcı: 0.8'in üstünü tanh benzeri sıkıştır
            val a = abs(y)
            if (a > 0.8f) { val over = a - 0.8f; y = Math.copySign(0.8f + over / (1f + over * 5f), y) }
            out.putShort((y * 32767f).toInt().coerceIn(-32768, 32767).toShort())
        }
        out.flip()
    }

    override fun onReset() { saveLearned() }

    companion object {
        /** ≈ -16 dBFS RMS: yüksek kayıtları kısar, alçakları biraz yükseltir. */
        const val TARGET_RMS = 0.16f
        const val MIN_G = 0.3f
        const val MAX_G = 2.2f
    }
}
