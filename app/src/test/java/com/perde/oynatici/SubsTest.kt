package com.perde.oynatici

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class SubsTest {
    private val tr = "Şişli'de ığdır çğüöı ŞĞÜÖİÇ"

    @Test fun utf8() = assertEquals(tr, Subs.decode(tr.toByteArray(Charsets.UTF_8), 0))
    @Test fun utf8Bom() = assertEquals(tr, Subs.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + tr.toByteArray(), 0))
    @Test fun cp1254Auto() = assertEquals(tr, Subs.decode(tr.toByteArray(Charset.forName("windows-1254")), 0))
    @Test fun utf16() = assertEquals(tr, Subs.decode(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + tr.toByteArray(Charsets.UTF_16LE), 0))

    @Test fun srtShift() {
        val s = "1\n00:00:01,500 --> 00:00:03,000\nMerhaba 12:34:56,789\n"
        val out = Subs.shift(s, "srt", 1600)
        assertTrue(out, out.contains("00:00:03,100 --> 00:00:04,600"))
        assertTrue("metin satırı değişmemeli", out.contains("Merhaba 12:34:56,789"))
    }
    @Test fun srtNegativeClamp() = assertTrue(Subs.shift("00:00:00,200 --> 00:00:01,000", "srt", -500).startsWith("00:00:00,000 --> 00:00:00,500"))
    @Test fun srtHourRollover() = assertTrue(Subs.shift("00:59:59,900 --> 01:00:00,100", "srt", 200).startsWith("01:00:00,100 --> 01:00:00,300"))
    @Test fun vttShortForm() = assertTrue(Subs.shift("00:05.000 --> 00:07.250", "vtt", 1000).startsWith("00:00:06.000 --> 00:00:08.250"))
    @Test fun assShift() {
        val l = "Dialogue: 0,0:00:01.50,0:00:03.00,Default,,0,0,0,,Saat 0:00:09.99 oldu"
        assertEquals("Dialogue: 0,0:00:02.50,0:00:04.00,Default,,0,0,0,,Saat 0:00:09.99 oldu", Subs.shift(l, "ass", 1000))
    }
    @Test fun naturalSort() {
        val l = listOf("Bölüm 10", "Bölüm 2", "bölüm 1", "Çay", "Cam").sortedWith(NaturalOrder)
        assertEquals(listOf("bölüm 1", "Bölüm 2", "Bölüm 10", "Cam", "Çay"), l)
    }
}
