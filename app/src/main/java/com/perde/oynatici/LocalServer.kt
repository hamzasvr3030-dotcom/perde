package com.perde.oynatici

import android.content.Context
import android.net.Uri
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URLEncoder

/**
 * Yerel ağ sunucusu. İki iş yapar:
 *  1) Wi-Fi aktarımı: aynı ağdaki bilgisayar/telefon tarayıcıdan videoları indirir veya yükler.
 *  2) Chromecast: televizyon videoyu bu sunucudan çeker.
 */
class LocalServer(private val ctx: Context) : NanoHTTPD(PORT) {
    companion object {
        const val PORT = 8765
        private var inst: LocalServer? = null
        var transferEnabled = false
        val uploaded = mutableListOf<String>()

        @Synchronized fun start(ctx: Context): Boolean {
            if (inst?.isAlive == true) return true
            return try { LocalServer(ctx.applicationContext).also { it.start(SOCKET_READ_TIMEOUT, false); inst = it }; true } catch (_: Exception) { false }
        }
        @Synchronized fun stop() { inst?.stop(); inst = null }
        val running get() = inst?.isAlive == true

        fun ip(): String? = try {
            NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
                .sortedBy { if (it.name.startsWith("wlan")) 0 else 1 }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress
        } catch (_: Exception) { null }

        fun streamUrl(key: String): String? {
            val ip = ip() ?: return null
            return "http://$ip:$PORT/v?k=" + URLEncoder.encode(key, "UTF-8")
        }
    }

    private fun mime(name: String) = when (name.substringAfterLast('.').lowercase()) {
        "mp4", "m4v" -> "video/mp4"; "mkv" -> "video/x-matroska"; "webm" -> "video/webm"; "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"; "3gp" -> "video/3gpp"; "ts" -> "video/mp2t"; "srt" -> "application/x-subrip"
        "vtt" -> "text/vtt"; else -> "application/octet-stream"
    }

    override fun serve(s: IHTTPSession): Response = try {
        when (s.uri) {
            "/v" -> stream(s, false)
            "/d" -> if (transferEnabled) stream(s, true) else forbidden()
            "/up" -> if (transferEnabled) upload(s) else forbidden()
            "/" -> if (transferEnabled) newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", page()) else forbidden()
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Bulunamadı")
        }
    } catch (e: Exception) {
        newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain; charset=utf-8", "Hata: ${e.message}")
    }

    private fun forbidden() = newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain; charset=utf-8", "Aktarım kapalı. Telefonda Wi-Fi Aktarım ekranını açın.")

    private fun allowed(key: String): Boolean {
        if (key.startsWith("content://")) return true
        val f = File(key)
        return f.isFile && (isVideoName(f.name) || f.extension.lowercase() in Subs.EXT)
    }

    private fun stream(s: IHTTPSession, download: Boolean): Response {
        val key = s.parameters["k"]?.firstOrNull() ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "k yok")
        if (!allowed(key)) return forbidden()
        val name: String; val len: Long; val open: () -> java.io.InputStream
        if (key.startsWith("content://")) {
            val u = Uri.parse(key)
            name = Library.displayName(ctx, u)
            len = ctx.contentResolver.openAssetFileDescriptor(u, "r")?.use { it.length } ?: -1L
            open = { ctx.contentResolver.openInputStream(u)!! }
        } else {
            val f = File(key); name = f.name; len = f.length(); open = { FileInputStream(f) }
        }
        val mt = mime(name)
        val range = s.headers["range"]
        val r: Response
        if (range != null && range.startsWith("bytes=") && len > 0) {
            val (a, b) = range.removePrefix("bytes=").split("-").let { it[0] to it.getOrElse(1) { "" } }
            val start = a.toLongOrNull() ?: 0L
            val end = b.toLongOrNull()?.coerceAtMost(len - 1) ?: (len - 1)
            if (start >= len) return newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, "text/plain", "").apply { addHeader("Content-Range", "bytes */$len") }
            val ins = open(); var skip = start; while (skip > 0) { val k = ins.skip(skip); if (k <= 0) break; skip -= k }
            r = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mt, ins, end - start + 1)
            r.addHeader("Content-Range", "bytes $start-$end/$len")
        } else {
            r = if (len >= 0) newFixedLengthResponse(Response.Status.OK, mt, open(), len) else newChunkedResponse(Response.Status.OK, mt, open())
        }
        r.addHeader("Accept-Ranges", "bytes")
        r.addHeader("Access-Control-Allow-Origin", "*")
        if (download) r.addHeader("Content-Disposition", "attachment; filename*=UTF-8''" + URLEncoder.encode(name, "UTF-8").replace("+", "%20"))
        return r
    }

    private fun upload(s: IHTTPSession): Response {
        if (s.method != Method.POST) return newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, "text/plain", "")
        val files = HashMap<String, String>()
        s.parseBody(files)
        val tmp = files["file"] ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "dosya yok")
        val orig = (s.parameters["file"]?.firstOrNull() ?: "video.mp4").substringAfterLast('/').substringAfterLast('\\')
        val dir = Library.publicDir()
        var dest = File(dir, orig); var n = 1
        while (dest.exists()) { dest = File(dir, orig.substringBeforeLast('.') + " ($n)." + orig.substringAfterLast('.', "mp4")); n++ }
        val src = File(tmp)
        if (!src.renameTo(dest)) { src.inputStream().use { i -> dest.outputStream().use { o -> i.copyTo(o, 1 shl 16) } }; src.delete() }
        Library.scan(ctx, dest.path)
        synchronized(uploaded) { uploaded.add(0, dest.name) }
        return newFixedLengthResponse(Response.Status.OK, "text/plain; charset=utf-8", "ok")
    }

    private fun esc(t: String) = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun page(): String {
        val vids = Library.sort(Library.cache)
        val rows = vids.joinToString("") { v ->
            "<tr><td><a href=\"/d?k=${URLEncoder.encode(v.path, "UTF-8")}\">${esc(v.title)}</a></td><td>${esc(File(v.folder).name)}</td><td>${fmtTime(v.duration)}</td><td>${fmtSize(v.size)}</td></tr>"
        }
        return """<!doctype html><html lang="tr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Perde · Wi-Fi Aktarım</title><style>
body{font-family:system-ui,sans-serif;background:#0e0f13;color:#eee;margin:0;padding:20px;max-width:960px;margin:auto}
h1{color:#ff7a45;margin:0 0 4px}p{color:#aaa}.box{border:2px dashed #ff7a45;border-radius:14px;padding:26px;text-align:center;margin:18px 0}
button{background:#ff7a45;color:#111;border:0;border-radius:10px;padding:12px 22px;font-weight:700;font-size:16px;cursor:pointer}
table{width:100%;border-collapse:collapse;font-size:14px}td,th{padding:8px;border-bottom:1px solid #2a2d36;text-align:left}a{color:#ffb38f}
#log div{padding:4px 0;color:#9fd}input[type=search]{width:100%;padding:10px;border-radius:10px;border:1px solid #333;background:#17191f;color:#eee;margin:10px 0}
</style></head><body><h1>Perde · Wi-Fi Aktarım</h1><p>Telefona video göndermek için dosyaları seç ya da buraya sürükle. Yüklenenler <b>Filmler/Perde</b> klasörüne kaydedilir.</p>
<div class="box" id="drop"><input type="file" id="f" multiple accept="video/*,.srt,.ass,.vtt" style="display:none">
<button onclick="document.getElementById('f').click()">Dosya seç</button><p>veya dosyaları buraya bırak</p><div id="log"></div></div>
<h2>Telefondaki videolar (${vids.size})</h2><input type="search" placeholder="Ara..." oninput="q(this.value)">
<table id="t"><tr><th>Ad (indirmek için tıkla)</th><th>Klasör</th><th>Süre</th><th>Boyut</th></tr>$rows</table>
<script>
function q(s){s=s.toLowerCase();document.querySelectorAll('#t tr').forEach((r,i)=>{if(i)r.style.display=r.textContent.toLowerCase().includes(s)?'':'none'})}
async function up(files){const log=document.getElementById('log');for(const f of files){const d=document.createElement('div');log.appendChild(d);
await new Promise(res=>{const x=new XMLHttpRequest();const fd=new FormData();fd.append('file',f,f.name);
x.upload.onprogress=e=>{d.textContent=f.name+' — %'+Math.round(e.loaded*100/e.total)};x.onload=()=>{d.textContent=f.name+(x.status==200?' — yüklendi ✓':' — hata');res()};
x.onerror=()=>{d.textContent=f.name+' — bağlantı hatası';res()};x.open('POST','/up');x.send(fd)})}}
document.getElementById('f').onchange=e=>up(e.target.files);const dr=document.getElementById('drop');
dr.ondragover=e=>{e.preventDefault()};dr.ondrop=e=>{e.preventDefault();up(e.dataTransfer.files)};
</script></body></html>"""
    }
}
