package com.perde.oynatici

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gerçek yt-dlp çıktısı (16 paralel parça, görüntü 24,8 MB + ses 9,8 MB) ile ilerleme hesabı. */
class DlRealTest {
    private fun lines() = javaClass.classLoader!!.getResource("progress_real.log")!!.readText().lines().filter { it.isNotBlank() }
    private val rx = Regex("\\]\\s+([\\d.]+)%")

    private fun run(durSec: Int): List<Float> {
        val j = Job("t", "https://youtu.be/aqz-KE-bpKQ", "t", null, DlOpts(false), dir = "/tmp").apply { expectFiles = 2; this.durSec = durSec }
        val out = ArrayList<Float>()
        lines().forEach { l -> rx.find(l)?.let { Dl.onProgress(j, it.groupValues[1].toFloat(), l); out += j.progress } }
        assertEquals("ses dosyasına geçilmeli", 1, j.fileIdx)
        assertEquals("Birleştiriliyor…", j.phase)
        assertTrue("inen ≈ 34,6 MB olmalı: ${j.gotBytes}", j.gotBytes in 33_000_000L..37_500_000L)
        return out
    }

    @Test fun realLogMonotonicAndSmooth() {
        val p = run(durSec = 635)   // Big Buck Bunny 10:35
        for (i in 1 until p.size) assertTrue("geri gitti: ${p[i - 1]} → ${p[i]}", p[i] >= p[i - 1])
        assertEquals(0.97f, p.last(), 0.001f)
        // ses inerken yüzde ilerlemeli: ses aşamasının son 5 satırında değişim olmalı (takılı kalmamalı)
        val audioPart = p.takeLast(14)
        assertTrue("ses inerken yüzde takılı kaldı: $audioPart", audioPart.last() - audioPart.first() > 0.05f)
    }
}
