package com.perde.oynatici

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mserref.NtStatus
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import org.json.JSONArray
import org.json.JSONObject
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.EnumSet
import java.util.concurrent.TimeUnit

data class SmbServer(val name: String, val host: String, val share: String, val user: String, val pass: String, val domain: String) {
    /** "Filmler/Diziler" gibi alt klasörlü girişlerde sadece ilk kısım paylaşım adıdır. */
    val shareName get() = share.split('/', '\\').firstOrNull { it.isNotEmpty() } ?: ""
    val basePath get() = share.split('/', '\\').filter { it.isNotEmpty() }.drop(1).joinToString("\\")
    val id get() = "$host/$shareName".lowercase()
    val title get() = name.ifBlank { "$host/$share" }
}
data class SmbEntry(val name: String, val isDir: Boolean, val size: Long)

/** Yerel ağdaki paylaşılan klasörler (Windows paylaşımı, NAS) — SMB2/3. */
object Smb {
    fun servers(): List<SmbServer> = try {
        val a = JSONArray(Prefs.smbServers)
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            SmbServer(o.optString("name"), o.optString("host"), o.optString("share"), o.optString("user"), o.optString("pass"), o.optString("domain"))
        }
    } catch (_: Exception) { emptyList() }

    fun save(list: List<SmbServer>) {
        val a = JSONArray()
        list.forEach { s ->
            a.put(JSONObject().put("name", s.name).put("host", s.host).put("share", s.share)
                .put("user", s.user).put("pass", s.pass).put("domain", s.domain))
        }
        Prefs.smbServers = a.toString()
    }

    /** "\\192.168.1.5\Filmler", "smb://pc/Filmler/Dizi", "DOMAIN\kullanici" gibi girişleri düzeltir. */
    fun normalize(s: SmbServer): SmbServer {
        var host = s.host.trim().removePrefix("smb://").removePrefix("SMB://").trimStart('\\', '/')
        var share = s.share.trim().trim('\\', '/')
        val parts = host.split('\\', '/').filter { it.isNotEmpty() }
        if (parts.size > 1) { host = parts[0]; share = (parts.drop(1) + listOf(share).filter { it.isNotEmpty() }).joinToString("/") }
        var user = s.user.trim(); var domain = s.domain.trim()
        if ('\\' in user && domain.isEmpty()) { domain = user.substringBefore('\\'); user = user.substringAfter('\\') }
        return s.copy(host = host, share = share, user = user, domain = domain)
    }

    private val client by lazy {
        SMBClient(SmbConfig.builder()
            .withTimeout(30, TimeUnit.SECONDS)
            .withSoTimeout(45, TimeUnit.SECONDS)
            .withMultiProtocolNegotiate(true)
            .withSigningRequired(false)
            .withDfsEnabled(true)
            .build())
    }
    private val shares = HashMap<String, DiskShare>()

    @Synchronized
    fun share(s: SmbServer): DiskShare {
        shares[s.id]?.let { if (it.isConnected) return it }
        val (host, port) = s.host.substringBefore(':') to (s.host.substringAfter(':', "").toIntOrNull() ?: 445)
        val conn = client.connect(host, port)
        val auth = if (s.user.isBlank()) AuthenticationContext.guest()
        else AuthenticationContext(s.user, s.pass.toCharArray(), s.domain.ifBlank { null })
        val session = try { conn.authenticate(auth) } catch (e: Exception) {
            // misafir reddedildiyse anonim dene
            if (s.user.isBlank()) conn.authenticate(AuthenticationContext.anonymous()) else throw e
        }
        val sh = session.connectShare(s.shareName) as DiskShare
        shares[s.id] = sh
        return sh
    }

    @Synchronized
    fun drop(s: SmbServer) { try { shares.remove(s.id)?.close() } catch (_: Exception) { } }

    fun list(s: SmbServer, path: String): List<SmbEntry> =
        share(s).list(path).filter { it.fileName != "." && it.fileName != ".." && !it.fileName.startsWith(".") && !it.fileName.endsWith("$") }.map {
            SmbEntry(it.fileName, (it.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L, it.endOfFile)
        }.sortedWith(compareBy<SmbEntry>({ !it.isDir }).thenComparator { a, b -> NaturalOrder.compare(a.name, b.name) })

    /** Bağlantıyı dener, kullanıcıya gösterilecek sonucu döner. */
    fun test(s: SmbServer): String = try {
        if (s.shareName.isEmpty()) "Paylaşım adını yaz (örn. Filmler)"
        else { drop(s); val n = list(s, s.basePath).size; "✓ Bağlantı başarılı · $n öğe bulundu" }
    } catch (e: Throwable) { drop(s); explain(e) }

    fun explain(e: Throwable): String {
        var c: Throwable? = e
        while (c != null) {
            when (c) {
                is SMBApiException -> return when (c.status) {
                    NtStatus.STATUS_LOGON_FAILURE -> "Kullanıcı adı veya şifre yanlış.\nWindows'ta Microsoft hesabı kullanıyorsan kullanıcı adı e-posta adresin, şifre hesap şifrendir (PIN değil)."
                    NtStatus.STATUS_ACCESS_DENIED -> "Erişim reddedildi. Misafir girişi kapalı olabilir; kullanıcı adı ve şifreyle dene ya da klasörün paylaşım izinlerini kontrol et."
                    NtStatus.STATUS_BAD_NETWORK_NAME -> "\"Paylaşım adı\" bulunamadı. Bilgisayarda klasöre sağ tık → Özellikler → Paylaşım'daki adı birebir yaz."
                    NtStatus.STATUS_OBJECT_NAME_NOT_FOUND, NtStatus.STATUS_OBJECT_PATH_NOT_FOUND -> "Klasör bulunamadı (silinmiş veya adı değişmiş olabilir)."
                    NtStatus.STATUS_ACCOUNT_DISABLED -> "Bu hesapla ağ girişi yapılamıyor (şifresiz hesaplar ağda kullanılamaz)."
                    else -> "Sunucu hatası: ${c.status.name}"
                }
                is UnknownHostException -> return "Sunucu adı bulunamadı. Bilgisayar adı yerine IP adresini yaz (örn. 192.168.1.20)."
                is ConnectException, is NoRouteToHostException -> return "Sunucuya bağlanılamadı. IP adresi doğru mu, bilgisayar açık mı? Telefon aynı Wi-Fi'de olmalı ve bilgisayarda \"Dosya ve yazıcı paylaşımı\" açık olmalı."
                is SocketTimeoutException -> return "Sunucu yanıt vermedi (zaman aşımı). Güvenlik duvarı 445 portunu engelliyor olabilir."
            }
            val m = c.message ?: ""
            if ("dialect" in m.lowercase() || "negotiat" in m.lowercase() || "SMBv1" in m)
                return "Bu cihaz sadece eski SMB1 protokolünü destekliyor (eski modem/NAS). Kadraj SMB2/3 kullanır; cihaz ayarlarından SMB2'yi aç."
            c = c.cause
        }
        return "Bağlanılamadı: ${e.message ?: e.javaClass.simpleName}"
    }

    fun uriFor(s: SmbServer, path: String): String {
        val b = Uri.Builder().scheme("smb").authority(s.host).appendPath(s.shareName)
        path.split('\\', '/').filter { it.isNotEmpty() }.forEach { b.appendPath(it) }
        return b.build().toString()
    }

    fun resolve(uri: Uri): Pair<SmbServer, String> {
        val host = uri.authority ?: ""
        val segs = uri.pathSegments
        val shareName = segs.firstOrNull() ?: ""
        val path = segs.drop(1).joinToString("\\")
        val srv = servers().map { normalize(it) }.firstOrNull { it.host.equals(host, true) && it.shareName.equals(shareName, true) }
            ?.let { it.copy(share = shareName) } ?: SmbServer("", host, shareName, "", "", "")
        return srv to path
    }
}

/**
 * ExoPlayer'ın smb:// adreslerini okuyabilmesi için veri kaynağı.
 * Ağ gidiş-dönüşünü azaltmak için 1 MB'lık parçalar hâlinde okur; bağlantı koparsa bir kez yeniden bağlanır.
 */
class SmbDataSource : BaseDataSource(true) {
    private var file: com.hierynomus.smbj.share.File? = null
    private var srv: SmbServer? = null
    private var path = ""
    private var uri: Uri? = null
    private var pos = 0L
    private var remaining = 0L
    private var opened = false
    private val buf = ByteArray(1 shl 20)
    private var bufStart = 0L
    private var bufLen = 0

    private fun openFile(s: SmbServer, p: String) =
        Smb.share(s).openFile(p, EnumSet.of(AccessMask.GENERIC_READ), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null)

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val (s, p) = Smb.resolve(dataSpec.uri)
        srv = s; path = p
        val f = try { openFile(s, p) } catch (e: Exception) { Smb.drop(s); openFile(s, p) }
        file = f
        val size = f.fileInformation.standardInformation.endOfFile
        pos = dataSpec.position
        remaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else size - pos
        bufLen = 0
        opened = true
        transferStarted(dataSpec)
        return remaining
    }

    private fun fill(): Int {
        val want = minOf(buf.size.toLong(), remaining).toInt()
        var n = try { file!!.read(buf, pos, 0, want) } catch (e: Exception) {
            // bağlantı koptu: yeniden bağlan ve aynı yerden devam et
            val s = srv ?: throw e
            try { file?.close() } catch (_: Exception) { }
            Smb.drop(s); file = openFile(s, path)
            file!!.read(buf, pos, 0, want)
        }
        if (n < 0) n = 0
        bufStart = pos; bufLen = n
        return n
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining <= 0) return C.RESULT_END_OF_INPUT
        if (pos < bufStart || pos >= bufStart + bufLen) { if (fill() <= 0) return C.RESULT_END_OF_INPUT }
        val inBuf = (bufStart + bufLen - pos).toInt()
        val n = minOf(length.toLong(), inBuf.toLong(), remaining).toInt()
        System.arraycopy(buf, (pos - bufStart).toInt(), buffer, offset, n)
        pos += n; remaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        try { file?.close() } catch (_: Exception) { }
        file = null; bufLen = 0
        if (opened) { opened = false; transferEnded() }
    }
}
