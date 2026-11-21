package com.perde.oynatici

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/** 1.4: internet servisleri (gerçek istek), ses dalgası, etiket yazma. */
class V14Test {
    private val scratch = File("C:/Users/hamza/AppData/Local/Temp/claude/52de5b9d9e860265/89beff69-27bc-429b-bf60-cd6e960b749a/scratchpad")

    @Test fun lyricsSynced() {
        val t = LyricsNet.fetchText("Tarkan", "Kuzu Kuzu (Official Video)", 242)
        assertTrue("zamanlı söz gelmedi: ${t.take(60)}", t.startsWith("["))
        val lines = LyricsNet.parse(t)
        assertTrue(lines.size > 10 && lines.all { it.first >= 0 })
    }

    @Test fun lyricsFromCombinedTitle() {
        // sanatçı bilinmiyorsa "Sanatçı - Şarkı" başlıktan ayrılır
        val t = LyricsNet.fetchText("Bilinmeyen sanatçı", "Tarkan - Şımarık", 0)
        assertTrue(t.isNotEmpty())
    }

    @Test fun metaFilm() {
        val m = MetaNet.search("Inception.2010.1080p.BluRay.x264.mkv")
        assertNotNull(m); assertEquals("film", m!!.kind)
        assertEquals("2010", m.year)
        assertTrue(m.summary.contains("Nolan"))
    }

    @Test fun metaSeries() {
        val m = MetaNet.search("Breaking.Bad.S01E02.720p.mkv")
        assertNotNull(m); assertEquals("dizi", m!!.kind)
        assertTrue(m.episode!!.startsWith("S01E02"))
        assertNotNull(m.poster)
    }

    @Test fun spotifyPlaylist() = runBlocking {
        val (name, tracks) = Spotify.read("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=abc")
        assertTrue(name.isNotBlank()); assertTrue(tracks.size >= 20)
        assertTrue(tracks.all { it.title.isNotBlank() && it.artist.isNotBlank() })
        assertTrue(Spotify.searchUrl(tracks[0]).startsWith("ytsearch1:"))
    }

    /** 1 kHz sinüs → en güçlü bant 1 kHz civarında olmalı; 8 kHz sinüs → daha yüksek bant. */
    @Test fun vizFft() {
        fun peakFor(freq: Double): Int {
            Viz.bands = FloatArray(32); Viz.enabled = true
            val n = 4096; val bb = ByteBuffer.allocate(n * 4).order(ByteOrder.nativeOrder())
            for (i in 0 until n) { val v = (sin(2 * PI * freq * i / 44100.0) * 20000).toInt().toShort(); bb.putShort(v); bb.putShort(v) }
            bb.flip(); Viz.feed(bb, 2)
            return Viz.bands.indices.maxBy { Viz.bands[it] }
        }
        val low = peakFor(1000.0); val high = peakFor(8000.0)
        assertTrue("1 kHz bandı $low, 8 kHz bandı $high", high > low)
        assertTrue(Viz.bands.max() > 0.3f)
    }

    @Test fun tagsWriteRead() {
        val src = File(scratch, "dltest3/TARKAN - Kuzu Kuzu.mp3")
        assumeTrue("örnek mp3 yok", src.exists())
        val f = File(scratch, "tagtest.mp3"); src.copyTo(f, true)
        val before = Tags.read(f.path)!!
        assertEquals("TARKAN", before.artist)
        val cover = ByteArray(0)  // kapağı sil
        assertEquals(null, Tags.writeFile(f.path, Tags.Info("Kuzu Kuzu (Düzenlendi)", "Tarkan", "Karma", "2001", "Pop", "3"), cover, scratch))
        val after = Tags.read(f.path)!!
        assertEquals("Kuzu Kuzu (Düzenlendi)", after.title); assertEquals("Tarkan", after.artist)
        assertEquals("Karma", after.album); assertEquals("2001", after.year); assertEquals("3", after.track)
        f.delete()
    }

    @Test fun queryName() {
        assertEquals("Inception" to "2010", MetaNet.queryName("Inception.2010.1080p.BluRay.x264"))
        assertEquals("Başlangıç", MetaNet.queryName("Başlangıç [YIFY]").first)
    }
}
