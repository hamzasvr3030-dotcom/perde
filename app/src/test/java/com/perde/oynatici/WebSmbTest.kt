package com.perde.oynatici

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSmbTest {
    @Test fun pageVsDirect() {
        assertTrue(Web.isPage("https://www.youtube.com/watch?v=abc"))
        assertTrue(Web.isPage("https://youtu.be/abc?si=x"))
        assertTrue(Web.isPage("https://www.instagram.com/reel/XYZ/"))
        assertTrue(Web.isPage("https://vm.tiktok.com/ZM123/"))
        assertFalse(Web.isPage("https://site.com/film.mp4"))
        assertFalse(Web.isPage("https://cdn.site.com/live/index.m3u8?token=1"))
        assertFalse(Web.isPage("/storage/emulated/0/a.mp4"))
    }

    @Test fun extract() {
        assertEquals("https://youtu.be/abc", Web.extractUrl("Şu videoya bak: https://youtu.be/abc."))
        assertEquals(null, Web.extractUrl("bağlantı yok"))
    }

    @Test fun explain() {
        assertTrue(Web.explain(Exception("ERROR: [vimeo] 1: The web client only works when logged-in. Use --cookies")).contains("giriş"))
        assertTrue(Web.explain(Exception("ERROR: Unsupported URL: https://x")).contains("desteklenmiyor"))
        assertTrue(Web.explain(Exception("ERROR: [youtube] x: Video unavailable")).contains("kaldırılmış"))
    }

    private fun s(host: String, share: String = "", user: String = "") = Smb.normalize(SmbServer("", host, share, user, "", ""))

    @Test fun smbNormalize() {
        s("\\\\192.168.1.20\\Filmler").let { assertEquals("192.168.1.20", it.host); assertEquals("Filmler", it.shareName); assertEquals("", it.basePath) }
        s("smb://nas/Medya/Diziler/2024").let { assertEquals("nas", it.host); assertEquals("Medya", it.shareName); assertEquals("Diziler\\2024", it.basePath) }
        s("192.168.1.5", "/Filmler/").let { assertEquals("Filmler", it.shareName) }
        s("pc", "Filmler", "EV\\hamza").let { assertEquals("hamza", it.user); assertEquals("EV", it.domain) }
    }
}
