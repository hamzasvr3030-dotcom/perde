package com.perde.oynatici

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale

/** Harici altyazılar: bulma, Türkçe karakter kodlaması çözme ve zaman kaydırma. */
object Subs {
    val EXT = listOf("srt", "ass", "ssa", "vtt")

    fun siblings(video: File): List<File> {
        val base = video.nameWithoutExtension
        val dir = video.parentFile ?: return emptyList()
        return (dir.listFiles() ?: emptyArray()).filter {
            it.isFile && it.extension.lowercase(Locale.ROOT) in EXT && it.name.startsWith(base)
        }
    }

    /** Videoyla aynı adlı altyazı: önce birebir aynı ad, sonra .tr / .tur, sonra herhangi biri. */
    fun findSibling(video: File): File? {
        val list = siblings(video); if (list.isEmpty()) return null
        val base = video.nameWithoutExtension
        return list.firstOrNull { it.nameWithoutExtension == base && it.extension.lowercase() == "srt" }
            ?: list.firstOrNull { it.nameWithoutExtension == base }
            ?: list.firstOrNull { val s = it.nameWithoutExtension.lowercase(); s.endsWith(".tr") || s.endsWith(".tur") || s.endsWith("turkish") }
            ?: list.first()
    }

    fun decode(bytes: ByteArray, mode: Int): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte())
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return String(bytes, Charsets.UTF_16LE).trimStart('﻿')
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return String(bytes, Charsets.UTF_16BE).trimStart('﻿')
        val cp1254 = Charset.forName("windows-1254")
        return when (mode) {
            1 -> String(bytes, Charsets.UTF_8)
            2 -> String(bytes, cp1254)
            else -> try {
                Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            } catch (_: CharacterCodingException) { String(bytes, cp1254) }
        }
    }

    private val srtTime = Regex("(?:(\\d+):)?(\\d{1,2}):(\\d{2})([,.])(\\d{3})")
    private val assTime = Regex("(\\d+):(\\d{2}):(\\d{2})\\.(\\d{2})")

    fun shift(text: String, ext: String, off: Long): String {
        if (off == 0L) return text
        val isAss = ext == "ass" || ext == "ssa"
        return text.lineSequence().joinToString("\n") { line ->
            if (isAss) {
                if (!line.startsWith("Dialogue:")) line
                else {
                    var n = 0
                    assTime.replace(line) { m ->
                        if (n++ >= 2) m.value else {
                            val (h, mi, s, cs) = m.destructured
                            val t = maxOf(0L, ((h.toLong() * 3600 + mi.toLong() * 60 + s.toLong()) * 1000 + cs.toLong() * 10) + off)
                            String.format(Locale.ROOT, "%d:%02d:%02d.%02d", t / 3600000, t / 60000 % 60, t / 1000 % 60, t % 1000 / 10)
                        }
                    }
                }
            } else if (!line.contains("-->")) line
            else srtTime.replace(line) { m ->
                val h = m.groupValues[1].ifEmpty { "0" }.toLong()
                val t = maxOf(0L, (h * 3600 + m.groupValues[2].toLong() * 60 + m.groupValues[3].toLong()) * 1000 + m.groupValues[5].toLong() + off)
                String.format(Locale.ROOT, "%02d:%02d:%02d%s%03d", t / 3600000, t / 60000 % 60, t / 1000 % 60, m.groupValues[4], t % 1000)
            }
        }
    }

    fun mimeFor(ext: String) = when (ext) {
        "ass", "ssa" -> MimeTypes.TEXT_SSA
        "vtt" -> MimeTypes.TEXT_VTT
        else -> MimeTypes.APPLICATION_SUBRIP
    }

    /** Altyazıyı UTF-8'e çevirip (gerekirse kaydırıp) önbelleğe yazar ve oynatıcıya verilecek yapılandırmayı döner. */
    fun prepare(ctx: Context, src: Uri, offsetMs: Long): MediaItem.SubtitleConfiguration? = try {
        val name = Library.displayName(ctx, src)
        val ext = name.substringAfterLast('.', "srt").lowercase(Locale.ROOT).let { if (it in EXT) it else "srt" }
        val bytes = ctx.contentResolver.openInputStream(src)?.use { it.readBytes() } ?: throw IllegalStateException()
        val text = shift(decode(bytes, Prefs.subEnc), ext, offsetMs)
        val dir = File(ctx.cacheDir, "subs").apply { mkdirs() }
        val out = File(dir, "s${(src.toString() + offsetMs).hashCode().toUInt()}_${System.nanoTime()}.$ext")
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }?.forEach { it.delete() }
        out.writeText(text, Charsets.UTF_8)
        MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(out))
            .setMimeType(mimeFor(ext))
            .setLabel("📄 $name")
            .setLanguage("tr")
            .setId("harici")
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
    } catch (_: Exception) { null }
}
