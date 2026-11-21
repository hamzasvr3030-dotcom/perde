package com.perde.oynatici

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import org.json.JSONArray
import org.json.JSONObject
import java.util.EnumSet

data class SmbServer(val name: String, val host: String, val share: String, val user: String, val pass: String, val domain: String) {
    val id get() = "$host/$share".lowercase()
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

    private val client by lazy { SMBClient() }
    private val shares = HashMap<String, DiskShare>()

    @Synchronized
    fun share(s: SmbServer): DiskShare {
        shares[s.id]?.let { if (it.isConnected) return it }
        val conn = client.connect(s.host)
        val auth = if (s.user.isBlank()) AuthenticationContext.guest()
        else AuthenticationContext(s.user, s.pass.toCharArray(), s.domain.ifBlank { null })
        val sh = conn.authenticate(auth).connectShare(s.share) as DiskShare
        shares[s.id] = sh
        return sh
    }

    @Synchronized
    fun drop(s: SmbServer) { try { shares.remove(s.id)?.close() } catch (_: Exception) { } }

    fun list(s: SmbServer, path: String): List<SmbEntry> =
        share(s).list(path).filter { it.fileName != "." && it.fileName != ".." && !it.fileName.startsWith(".") }.map {
            SmbEntry(it.fileName, (it.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L, it.endOfFile)
        }.sortedWith(compareBy<SmbEntry>({ !it.isDir }).thenComparator { a, b -> NaturalOrder.compare(a.name, b.name) })

    fun uriFor(s: SmbServer, path: String): String {
        val b = Uri.Builder().scheme("smb").authority(s.host).appendPath(s.share)
        path.split('\\', '/').filter { it.isNotEmpty() }.forEach { b.appendPath(it) }
        return b.build().toString()
    }

    fun resolve(uri: Uri): Pair<SmbServer, String> {
        val host = uri.host ?: ""
        val segs = uri.pathSegments
        val shareName = segs.firstOrNull() ?: ""
        val path = segs.drop(1).joinToString("\\")
        val srv = servers().firstOrNull { it.host.equals(host, true) && it.share.equals(shareName, true) }
            ?: SmbServer("", host, shareName, "", "", "")
        return srv to path
    }
}

/** ExoPlayer'ın smb:// adreslerini okuyabilmesi için veri kaynağı. */
class SmbDataSource : BaseDataSource(true) {
    private var file: com.hierynomus.smbj.share.File? = null
    private var uri: Uri? = null
    private var pos = 0L
    private var remaining = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val (srv, path) = Smb.resolve(dataSpec.uri)
        val f = try {
            Smb.share(srv).openFile(path, EnumSet.of(AccessMask.GENERIC_READ), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null)
        } catch (e: Exception) {
            Smb.drop(srv)
            Smb.share(srv).openFile(path, EnumSet.of(AccessMask.GENERIC_READ), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null)
        }
        file = f
        val size = f.fileInformation.standardInformation.endOfFile
        pos = dataSpec.position
        remaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else size - pos
        opened = true
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining <= 0) return C.RESULT_END_OF_INPUT
        val n = file!!.read(buffer, pos, offset, minOf(length.toLong(), remaining).toInt())
        if (n <= 0) return C.RESULT_END_OF_INPUT
        pos += n; remaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        try { file?.close() } catch (_: Exception) { }
        file = null
        if (opened) { opened = false; transferEnded() }
    }
}
