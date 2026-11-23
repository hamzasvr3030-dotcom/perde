package com.perde.oynatici

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Uygulama simgesi seçimi. Her simge manifestte bir activity-alias; yalnızca biri açık tutulur.
 * Seçim Android'in paket ayarlarında saklanır: güncellemede ve yeniden başlatmada korunur.
 * DİKKAT: alias adları (IconClassic, IconCam...) değiştirilmemeli, yoksa seçim sıfırlanır.
 */
object AppIcons {
    data class Opt(val alias: String, val name: String, val bg: Int, val fg: Int)

    val ALL = listOf(
        Opt("IconClassic", "Klasik", 0, R.drawable.ic_launcher_foreground),
        Opt("IconCam", "Turkuaz", R.drawable.ic_bg_cam, R.drawable.ic_fg_cam),
        Opt("IconSunset", "Gün Batımı", R.drawable.ic_bg_sunset, R.drawable.ic_fg_sunset),
        Opt("IconNeon", "Neon", R.drawable.ic_bg_neon, R.drawable.ic_fg_neon),
        Opt("IconPastel", "Pastel", R.drawable.ic_bg_pastel, R.drawable.ic_fg_pastel),
        Opt("IconGold", "Altın", R.drawable.ic_bg_gold, R.drawable.ic_fg_gold),
    )

    private fun cn(ctx: Context, alias: String) = ComponentName(ctx.packageName, "com.perde.oynatici.$alias")

    /** Şu an açık olan simge (paket yöneticisinden okunur, uygulama verisine bağlı değil). */
    fun current(ctx: Context): Int {
        val pm = ctx.packageManager
        ALL.forEachIndexed { i, o ->
            val st = pm.getComponentEnabledSetting(cn(ctx, o.alias))
            val on = st == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || (st == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && i == 0)
            if (on) return i
        }
        return 0
    }

    /** Önce yenisini açar, sonra ötekileri kapatır: hiçbir an simgesiz kalınmaz. */
    fun set(ctx: Context, idx: Int) {
        val pm = ctx.packageManager
        val target = ALL.getOrNull(idx) ?: return
        try {
            pm.setComponentEnabledSetting(cn(ctx, target.alias), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            ALL.forEach { o ->
                if (o !== target) pm.setComponentEnabledSetting(cn(ctx, o.alias), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
            Report.log("simge", target.name)
        } catch (e: Exception) { Report.err("simge değiştirme", e) }
    }
}

@Composable
fun AppIconPreview(o: AppIcons.Opt, size: Int = 58) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.3f).dp)).background(Color(0xFF121318))) {
        if (o.bg != 0) Image(painterResource(o.bg), null, Modifier.fillMaxSize())
        // adaptif simgenin görünen kısmı ortadaki ~%66: önizlemede de öyle kırp
        Image(painterResource(o.fg), null, Modifier.requiredSize((size * 1.5f).dp).align(Alignment.Center))
    }
}

@Composable
fun AppIconPicker() {
    val ctx = LocalContext.current
    var cur by remember { mutableIntStateOf(AppIcons.current(ctx)) }
    var ask by remember { mutableStateOf<Int?>(null) }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        AppIcons.ALL.forEachIndexed { i, o ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { if (i != cur) ask = i }) {
                Box {
                    Box(Modifier.border(3.dp, if (i == cur) Accent else Color.Transparent, RoundedCornerShape(20.dp)).padding(4.dp)) { AppIconPreview(o) }
                    if (i == cur) Box(Modifier.align(Alignment.TopEnd).size(22.dp).clip(RoundedCornerShape(11.dp)).background(Accent), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
                    }
                }
                Text(o.name, fontSize = 12.sp, color = if (i == cur) Ink else Muted, fontWeight = if (i == cur) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
    ask?.let { i ->
        AlertDialog(onDismissRequest = { ask = null },
            title = { Text("Simge değişsin mi?") },
            text = { Text("\"${AppIcons.ALL[i].name}\" simgesine geçilecek. Ana ekrandaki Kadraj simgesi birkaç saniye içinde yenilenir; bazı telefonlarda simgeyi ana ekrana yeniden eklemen gerekebilir. Seçimin güncellemelerde korunur.") },
            confirmButton = { TextButton(onClick = { AppIcons.set(ctx, i); cur = i; ask = null; toast(ctx, "Simge değişti") }) { Text("Değiştir") } },
            dismissButton = { TextButton(onClick = { ask = null }) { Text("Vazgeç") } })
    }
}
