package com.perde.oynatici

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DlTest {
    private fun job(audio: Boolean) = Job("t", "https://youtu.be/x", "t", null, DlOpts(audio), dir = "/tmp")

    /** Görüntü 0→100 (64→63 titremesiyle), sonra ses 0→100: yüzde hiç geri gitmemeli. */
    @Test fun progressNeverGoesBack() {
        val j = job(false)
        val raw = (0..64).map { it.toFloat() } + listOf(63f, 64f, 63.5f) + (65..100).map { it.toFloat() } + (0..100 step 5).map { it.toFloat() }
        var last = 0f
        raw.forEach { r ->
            Dl.onProgress(j, r, "[download]  $r% of 10.00MiB at 2.50MiB/s ETA 00:05")
            assertTrue("geri gitti: ${j.progress} < $last (ham $r)", j.progress >= last); last = j.progress
        }
        assertEquals(1, j.fileIdx)
        assertTrue(j.progress in 0.95f..0.99f)
        assertEquals("Birleştiriliyor…", j.phase)
        assertEquals("2,5 MB/sn", j.speed)
        assertEquals("00:05 kaldı", j.eta)
    }

    @Test fun audioPhases() {
        val j = job(true)
        Dl.onProgress(j, 50f, "[download]  50.0% of 3.00MiB at 512.00KiB/s ETA 00:03")
        assertEquals("İndiriliyor", j.phase); assertEquals("512 KB/sn", j.speed)
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
        assertEquals("https://www.youtube.com/@Tarkan/shorts", Web.normalize("https://www.youtube.com/@Tarkan/shorts"))
        assertEquals(listOf("https://a.com/1", "https://b.com/2"), Web.extractUrls("https://a.com/1\nhttps://b.com/2 ve https://a.com/1"))
        assertEquals("Türkçe Pop 2024", Dl.safeName("Türkçe: Pop / 2024"))
    }
}
