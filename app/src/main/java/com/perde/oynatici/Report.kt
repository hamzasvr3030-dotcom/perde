@file:OptIn(ExperimentalMaterial3Api::class)

package com.perde.oynatici

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Hata raporu: çökme kaydı + son 200 olay (indirme/oynatma hataları).
 * Kullanıcı tek dokunuşla kopyalayıp/paylaşıp bana gönderebilir.
 */
object Report {
    private var dir: File? = null
    private val fmt = SimpleDateFormat("dd.MM HH:mm:ss", Locale.ROOT)

    fun init(ctx: Context) {
        dir = File(ctx.filesDir, "rapor").apply { mkdirs() }
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = StringWriter(); e.printStackTrace(PrintWriter(sw))
                File(dir, "cokme.txt").writeText("${fmt.format(Date())} · iş parçacığı: ${t.name}\n$sw")
                log("ÇÖKME", e.toString())
            } catch (_: Throwable) { }
            prev?.uncaughtException(t, e)
        }
    }

    @Synchronized
    /** Yutulan hatayı rapora yazar (ne olduğu sonradan görülebilsin). */
    fun err(tag: String, e: Throwable) = log(tag, "${e.javaClass.simpleName}: ${e.message ?: ""}")

    @Synchronized fun log(tag: String, msg: String) {
        val d = dir ?: return
        try {
            val f = File(d, "olaylar.txt")
            val lines = (if (f.exists()) f.readLines() else emptyList()).takeLast(199) + "${fmt.format(Date())} [$tag] ${msg.replace('\n', ' ').take(400)}"
            f.writeText(lines.joinToString("\n"))
        } catch (_: Exception) { }
    }

    fun crash(): String? = dir?.let { File(it, "cokme.txt") }?.takeIf { it.exists() }?.readText()
    fun newCrash(): Boolean = dir?.let { File(it, "cokme.txt") }?.let { it.exists() && !File(dir, "cokme.goruldu").exists() } == true
    fun markSeen() { dir?.let { File(it, "cokme.goruldu").writeText("1") } }

    fun text(ctx: Context): String = buildString {
        append("KADRAJ HATA RAPORU\n")
        append("Sürüm: ${Updater.current(ctx)} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}\n")
        append("İndirme motoru: yt-dlp ${try { Web.version(ctx) } catch (_: Exception) { "?" }}\n")
        append("Ayarlar: aynı anda ${DlPrefs.parallel} indirme, hızlı=${DlPrefs.fast}, wifi=${DlPrefs.wifiOnly}, akıllı kalite=${DlPrefs.smartQuality}\n")
        val runs = Dl.jobs.filter { it.state == Job.DONE && it.timing.isNotEmpty() }.take(5)
        if (runs.isNotEmpty()) { append("\nSon indirme süreleri:\n"); runs.forEach { append("• ${it.title.take(40)}: ${it.timing}\n") } }
        crash()?.let { append("\nSON ÇÖKME:\n").append(it.take(6000)).append('\n') }
        val ev = dir?.let { File(it, "olaylar.txt") }?.takeIf { it.exists() }?.readLines()?.takeLast(60)
        if (!ev.isNullOrEmpty()) { append("\nSON OLAYLAR:\n"); ev.forEach { append(it).append('\n') } }
    }

    fun clear() { dir?.listFiles()?.forEach { it.delete() } }

    fun copy(ctx: Context) {
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Kadraj raporu", text(ctx)))
    }

    fun share(ctx: Context) {
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Kadraj hata raporu").putExtra(Intent.EXTRA_TEXT, text(ctx)), "Raporu gönder"))
    }
}

@Composable
fun ReportScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    var ver by remember { mutableIntStateOf(0) }
    val txt = remember(ver) { Report.text(ctx) }
    Page("Hata raporu", back) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("Bir şey çökerse ya da indirme/oynatma hata verirse burada kaydı tutulur. \"Kopyala\"ya dokunup bana sohbete yapıştırman yeterli; kişisel dosya içeriği yoktur.",
                color = Muted, fontSize = 13.sp)
            Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { Report.copy(ctx); toast(ctx, "Rapor kopyalandı") }) { Icon(Icons.Filled.ContentCopy, null); Spacer(Modifier.width(6.dp)); Text("Kopyala") }
                FilledTonalButton(onClick = { Report.share(ctx) }) { Icon(Icons.Filled.Share, null); Spacer(Modifier.width(6.dp)); Text("Paylaş") }
                TextButton(onClick = { Report.clear(); ver++ }) { Text("Temizle") }
            }
            SelectionContainer {
                Text(txt, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Surf).padding(12.dp).horizontalScroll(rememberScrollState()))
            }
        }
    }
}

@Composable
fun CrashDialog() {
    val ctx = LocalContext.current
    var show by remember { mutableStateOf(Report.newCrash()) }
    if (!show) return
    AlertDialog(onDismissRequest = { show = false; Report.markSeen() },
        icon = { Icon(Icons.Filled.BugReport, null, tint = Accent) },
        title = { Text("Kadraj geçen sefer beklenmedik şekilde kapandı") },
        text = { Text("Nedenini bulabilmem için raporu kopyalayıp bana gönderebilirsin.") },
        confirmButton = { TextButton(onClick = { Report.copy(ctx); toast(ctx, "Rapor kopyalandı"); show = false; Report.markSeen() }) { Text("Raporu kopyala") } },
        dismissButton = { TextButton(onClick = { show = false; Report.markSeen() }) { Text("Kapat") } })
}

/**
 * Dosyayı önce geçici dosyaya yazıp sonra yerine koyar: yazma yarıda kesilirse (uygulama kapanır, pil biter)
 * eski dosya sağlam kalır. Eskiden yarım yazılan dosya bir sonraki açılışta okunamıyor ve geçmiş sıfırlanıyordu.
 */
fun File.writeSafe(text: String) {
    val tmp = File(parentFile, "$name.tmp")
    tmp.writeText(text)
    if (!tmp.renameTo(this)) { delete(); if (!tmp.renameTo(this)) { writeText(text); tmp.delete() } }
}

/** Bozuk dosyayı silmeden kenara alır (okunamadıysa üzerine yazılıp kaybolmasın). */
fun File.keepBroken(tag: String, e: Throwable) {
    Report.err(tag, e)
    try { if (exists()) copyTo(File(parentFile, "$name.bozuk"), overwrite = true) } catch (_: Exception) { }
}
