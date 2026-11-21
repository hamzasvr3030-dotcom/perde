package com.perde.oynatici

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.random.Random

class ExtrasTest {
    private fun paletteRgb(i: Int) = ((i / 42) * 255 / 5 shl 16) or ((i / 6 % 7) * 255 / 6 shl 8) or ((i % 6) * 255 / 5)

    /** Yazdığımız GIF'i Java'nın kendi okuyucusuyla çözüp her pikseli karşılaştırır. */
    @Test fun gifRoundTrip() {
        val w = 97; val h = 61
        val rnd = Random(7)
        // hem rastgele (sözlük taşması / temizleme kodu) hem düzgün (uzun eşleşmeler) kareler
        val frames = listOf(
            ByteArray(w * h) { rnd.nextInt(252).toByte() },
            ByteArray(w * h) { ((it / 7) % 252).toByte() },
            ByteArray(w * h) { 5 }
        )
        val out = ByteArrayOutputStream()
        val enc = GifEncoder(out, w, h, 10)
        frames.forEach { enc.addIndexed(it) }
        enc.finish()
        val reader = ImageIO.getImageReadersByFormatName("gif").next()
        reader.input = ImageIO.createImageInputStream(ByteArrayInputStream(out.toByteArray()))
        assertEquals(3, reader.getNumImages(true))
        frames.forEachIndexed { f, idx ->
            val img = reader.read(f)
            for (y in 0 until h) for (x in 0 until w) {
                assertEquals("kare $f ($x,$y)", paletteRgb(idx[y * w + x].toInt() and 0xFF), img.getRGB(x, y) and 0xFFFFFF)
            }
        }
    }

    @Test fun bigGifManyClears() {
        val w = 320; val h = 240; val rnd = Random(3)
        val idx = ByteArray(w * h) { rnd.nextInt(252).toByte() }
        val out = ByteArrayOutputStream()
        GifEncoder(out, w, h, 10).apply { addIndexed(idx); finish() }
        val img = ImageIO.read(ByteArrayInputStream(out.toByteArray()))
        for (i in idx.indices step 997) assertEquals(paletteRgb(idx[i].toInt() and 0xFF), img.getRGB(i % w, i / w) and 0xFFFFFF)
    }

    @Test fun lrc() {
        val l = Lrc.parse("[ti:Şarkı]\n[00:01.50]Birinci satır\n[00:03.00][00:10.25]Tekrar\n[01:02]Son\n[offset:500]")
        assertEquals(listOf(1500L - 500, 3000L - 500, 10250L - 500, 62000L - 500), l.map { it.first })
        assertEquals("Birinci satır", l[0].second)
    }

    @Test fun versions() {
        assertTrue(Updater.newer("1.1.0", "1.0.0"))
        assertTrue(Updater.newer("1.10", "1.9.9"))
        assertFalse(Updater.newer("1.1.0", "1.1.0"))
        assertFalse(Updater.newer("1.0.9", "1.1"))
    }

    @Test fun titles() {
        assertEquals("Dizi Adi S01E02", cleanTitle("Dizi.Adi.S01E02.1080p.WEB-DL.x264.mkv"))
        assertEquals("Film Adı", cleanTitle("Film Adı [YIFY].mp4"))
    }
}
