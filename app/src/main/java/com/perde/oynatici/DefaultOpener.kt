package com.perde.oynatici

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * Kadraj'ı video / müzik dosyalarının varsayılan açıcısı yapma.
 * Android sessizce varsayılan yapmaya izin vermez: başka uygulama varsayılansa onun
 * "Varsayılanları temizle" sayfası açılır, değilse "Her zaman" seçilecek pencere gösterilir.
 * Pencereden Kadraj seçilince gelen deneme dosyası (kadraj-varsayilan.*) oynatılmaz.
 */
object DefaultOpener {
    enum class Kind(val mime: String, val ext: String, val label: String) {
        VIDEO("video/mp4", "mp4", "video"), AUDIO("audio/mpeg", "mp3", "müzik")
    }

    private const val PROBE = "kadraj-varsayilan"

    fun currentDefault(ctx: Context, k: Kind): String? {
        val ri = ctx.packageManager.resolveActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://x/a.${k.ext}"), k.mime), PackageManager.MATCH_DEFAULT_ONLY
        ) ?: return null
        val pkg = ri.activityInfo?.packageName ?: return null
        if (pkg == "android" || pkg.contains("resolver") || pkg.contains("intentresolver")) return null
        return pkg
    }

    fun isDefault(ctx: Context, k: Kind) = currentDefault(ctx, k) == ctx.packageName

    fun label(ctx: Context, pkg: String): String = runCatching {
        ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    fun summary(ctx: Context, k: Kind): String {
        val cur = currentDefault(ctx, k)
        return when {
            cur == ctx.packageName -> "Kadraj varsayılan ✓"
            cur != null -> "Şu an: ${label(ctx, cur)} — dokun, Kadraj'a geçir"
            else -> "Her seferinde soruluyor — dokun, Kadraj'ı seç"
        }
    }

    /** Döndürdüğü metin kullanıcıya ne yapacağını söyler. */
    fun start(ctx: Context, k: Kind): String {
        val cur = currentDefault(ctx, k)
        if (cur == ctx.packageName) { openAppDefaults(ctx); return "Kadraj zaten varsayılan" }
        if (cur != null) {
            runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$cur")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            return "${label(ctx, cur)} varsayılan. Açılan sayfada \"Varsayılan olarak aç\" > \"Varsayılanları temizle\"ye dokun, sonra tekrar dene."
        }
        return try {
            val f = File(ctx.cacheDir, "probe/$PROBE.${k.ext}"); f.parentFile?.mkdirs(); if (!f.exists()) f.writeBytes(ByteArray(16))
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
            ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, k.mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
            "Listeden Kadraj'ı seç ve \"Her zaman\"a dokun."
        } catch (_: Exception) { openAppDefaults(ctx); "Ayarlarda \"Varsayılan olarak aç\"ı düzenle." }
    }

    fun openAppDefaults(ctx: Context) {
        val i = if (Build.VERSION.SDK_INT >= 31) Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, Uri.parse("package:${ctx.packageName}"))
        else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
        runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun isProbe(uri: Uri?) = uri?.lastPathSegment?.startsWith(PROBE) == true
}
