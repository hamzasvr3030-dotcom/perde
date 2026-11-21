package com.perde.oynatici

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DlTest {
    private fun job(audio: Boolean, files: Int) = Job("t", "https://youtu.be/x", "t", null, DlOpts(audio), dir = "/tmp").apply { expectFiles = files }

    private fun feed(j: Job, size: String, from: Int, to: Int, step: Int = 1, check: (Float) -> Unit = {}) {
        var r = from
        while (r <= to) { Dl.onProgress(j, r.toFloat(), "[download] ${r}.0% of $size at 2.50MiB/s ETA 00:05"); check(j.progress); r += step }
    }

    /** Kullanıcının gördüğü hata: önce altyazı (küçük dosya) iniyor, video başlamadan %80'e atlıyordu. */
    @Test fun subtitleFirstDoesNotJump() {
        val j = job(false, 2)
        feed(j, "1.20KiB", 0, 100, 50)                    // altyazı
        assertTrue("altyazı ilerlemeyi oynatmamalı: ${j.progress}", j.progress == 0f)
        Dl.onProgress(j, 1f, "[download]   1.0% of 100.00MiB at 2.50MiB/s ETA 00:40")
        assertTrue("video yeni başladı, ${j.progress}", j.progress < 0.03f)
        assertEquals(0, j.fileIdx)
    }

    /** Görüntü + ses: hiç geri gitmez, MB doğru, sonunda birleştirme. */
    @Test fun videoThenAudioMonotonicWithMb() {
        val j = job(false, 2)
        var last = 0f
        val mono: (Float) -> Unit = { assertTrue("geri gitti $it < $last", it >= last); last = it }
        feed(j, "100.00MiB", 0, 64, check = mono)
        Dl.onProgress(j, 63f, "[download]  63.0% of 100.00MiB"); mono(j.progress)   // titreme
        feed(j, "100.00MiB", 65, 100, check = mono)
        assertTrue("görüntü bitince ses payı kalmalı: ${j.progress}", j.progress in 0.84f..0.88f)
        assertEquals(100L * 1048576, j.gotBytes)
        feed(j, "10.00MiB", 0, 100, 5, check = mono)
        assertEquals(1, j.fileIdx)
        assertEquals(110L * 1048576, j.totalBytes)
        assertEquals(0.97f, j.progress, 0.001f)
        assertEquals("Birleştiriliyor…", j.phase)
        assertEquals("2,5 MB/sn", j.speed)
    }

    /** Tek dosyalı video (Instagram vb.): %80'de takılmamalı. */
    @Test fun singleFileReachesEnd() {
        val j = job(false, 1).let { Job("t", "https://instagram.com/p/x", "t", null, DlOpts(false), dir = "/tmp").apply { expectFiles = 1 } }
        feed(j, "~20.00MiB", 0, 100, 10)
        assertEquals(0.97f, j.progress, 0.001f)
        assertEquals("Kaydediliyor…", j.phase)
        assertTrue("tahmini boyut ~ ile işaretlenmeli", !j.totalExact)
    }

    @Test fun audioPhases() {
        val j = job(true, 1)
        Dl.onProgress(j, 50f, "[download]  50.0% of 3.00MiB at 512.00KiB/s ETA 00:03")
        assertEquals("İndiriliyor", j.phase); assertEquals("512 KB/sn", j.speed)
        assertEquals((1.5 * 1048576).toLong(), j.gotBytes)
        Dl.onProgress(j, 100f, "[download] 100% of 3.00MiB")
        assertEquals("Dönüştürülüyor…", j.phase)
        assertTrue(j.progress < 1f)
    }

    @Test fun ids() {
        assertEquals("dQw4w9WgXcQ", Web.idOf("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PL1"))
        assertEquals("dQw4w9WgXcQ", Web.idOf("https://youtu.be/dQw4w9WgXcQ?si=abc"))
        assertEquals("https://www.youtube.com/playlist?list=PLabc", Web.playlistOf("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLabc&index=3"))
        assertEquals(null, Web.playlistOf("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=RDdQw4w9WgXcQ"))
        assertEquals("https://www.youtube.com/@Tarkan/videos", Web.normalize("https://www.youtube.com/@Tarkan"))
        assertEquals(listOf("https://a.com/1", "https://b.com/2"), Web.extractUrls("https://a.com/1\nhttps://b.com/2 ve https://a.com/1"))
        assertEquals("Türkçe Pop 2024", Dl.safeName("Türkçe: Pop / 2024"))
    }
}
