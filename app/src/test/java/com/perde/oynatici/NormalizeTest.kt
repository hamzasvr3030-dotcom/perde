package com.perde.oynatici

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin
import kotlin.math.sqrt

class NormalizeTest {
    /** amp genlikli 440 Hz sinüsü 20 sn işle, son 2 sn'nin RMS'ini döndür. */
    private fun run(amp: Float): Float {
        val p = NormalizeProcessor()
        p.configure(AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)); p.flush()
        var t = 0L; var sum = 0.0; var n = 0L
        val total = 44100L * 20
        while (t < total) {
            val frames = 4096
            val b = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder())
            repeat(frames) { val s = (sin(2 * Math.PI * 440 * (t + it) / 44100) * amp * 32767).toInt().toShort(); b.putShort(s); b.putShort(s) }
            b.flip(); p.queueInput(b)
            val o = p.output.order(ByteOrder.nativeOrder())
            while (o.remaining() >= 2) { val x = o.short / 32768f; if (t > total - 44100 * 2) { sum += x * x; n++ } }
            t += frames
        }
        return sqrt(sum / n).toFloat()
    }

    @Test fun loudAndQuietMeet() {
        val loud = run(0.9f)     // ≈ -4 dBFS RMS: çok yüksek kayıt
        val quiet = run(0.12f)   // ≈ -21 dBFS RMS: alçak kayıt
        println("loud=$loud quiet=$quiet")
        assertTrue("yüksek kısılmalı: $loud", loud < 0.30f)
        assertTrue("alçak açılmalı: $quiet", quiet > 0.12f)
        assertTrue("ikisi birbirine yakın olmalı: $loud / $quiet", loud / quiet < 1.6f)
    }
}
