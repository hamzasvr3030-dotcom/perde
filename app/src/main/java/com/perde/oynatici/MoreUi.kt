@file:OptIn(ExperimentalMaterial3Api::class)

package com.perde.oynatici

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ============================ Ağ ============================
@Composable
private fun NetCard(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Surf).border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(22.dp))
        .pressable(onClick = onClick).padding(horizontal = 18.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Accent, modifier = Modifier.size(34.dp))
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(sub, color = Muted, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 3.dp))
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Accent)
    }
}

@Composable
fun NetScreen(go: (Route) -> Unit) {
    val ctx = LocalContext.current
    val servers = remember { Smb.servers() }
    val ip = remember { LocalServer.ip() }
    Page("Ağ", null) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            NetCard(Icons.Filled.Download, "Bağlantı aç ve indir", "YouTube, Instagram, TikTok, Spotify listeleri ve desteklenen diğer kaynaklardan oynatma veya indirme.") { AppState.tab = 2 }
            NetCard(Icons.Filled.Public, "Tarayıcı", "Herhangi bir sitede gez, sayfadaki videoyu yakalayıp oynat veya indir.") {
                ctx.startActivity(android.content.Intent(ctx, BrowserActivity::class.java)) }
            NetCard(Icons.Filled.Computer, "Ağ klasörleri (SMB)", if (servers.isEmpty()) "Bilgisayardaki paylaşılan klasörlerden dosyaları indirmeden izle." else "${servers.size} kayıtlı sunucu · " + servers.joinToString { it.title }.take(60)) { go(Route.SmbList) }
            NetCard(Icons.Filled.Wifi, "Wi-Fi aktarım", "Bilgisayardan telefona veya telefondan bilgisayara dosya aktar." + (ip?.let { "\nTelefonun adresi: $it" } ?: "")) { go(Route.Transfer) }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(AccentDeep, Accent.copy(alpha = 0.35f)))).padding(20.dp)) {
                Column {
                    Text("Televizyona yansıtma", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Cast, null, tint = Accent, modifier = Modifier.size(40.dp))
                        Text("Bir video açıp üst çubuktaki yayın simgesine dokun; Chromecast veya Google TV'de oynar.", color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp, modifier = Modifier.padding(start = 14.dp))
                    }
                }
            }
        }
    }
}

// ============================ Daha fazla ============================
@Composable
fun MoreScreen(vm: LibVM, go: (Route) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    val trashN = remember(vm.ver) { try { Trash.list(ctx).size } catch (_: Exception) { 0 } }
    fun checkUpdate() {
        if (checking) return; checking = true
        scope.launch {
            val r = Updater.check(); checking = false
            when {
                r == null -> toast(ctx, Updater.lastError ?: "Sunucuya ulaşılamadı")
                Updater.newer(r.version, Updater.current(ctx)) -> AppState.update = r
                else -> toast(ctx, "En güncel sürümü kullanıyorsun (${r.version})")
            }
        }
    }
    Page("Daha fazla", null) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            // profil kartı
            Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(AccentDeep, Surf))).border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(20.dp))
                .pressable { go(Route.SettingsPage(6)) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                KLogo(52.dp)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text("Kadraj", fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Text("Sürüm ${Updater.current(ctx)} · ${vm.videos.size} video · ${vm.songs.size} şarkı", color = Muted, fontSize = 13.sp)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Accent)
            }

            Group("Araçlar") {
                GroupRow(Icons.Filled.Lock, "Gizli klasör", "PIN ve parmak iziyle korunur") { go(Route.Private) }
                GroupRow(Icons.Filled.Delete, "Çöp kutusu", trailing = if (trashN > 0) "$trashN" else null) { go(Route.TrashR) }
                GroupRow(Icons.Filled.BarChart, "İstatistikler") { go(Route.StatsR) }
                GroupRow(Icons.Filled.CleaningServices, "Yer aç", "İzlediğin eski videoları sil") { go(Route.CleanupR) }
                GroupRow(Icons.Filled.Storage, "Depolama analizi") { go(Route.StorageR) }
                GroupRow(Icons.Filled.ContentCopy, "Kopya dosyalar") { go(Route.DupR) }
                GroupRow(Icons.Filled.Slideshow, "Kısa videolar") { ctx.startActivity(android.content.Intent(ctx, ShortsActivity::class.java)) }
                GroupRow(Icons.Filled.Favorite, "Favoriler", tint = Color(0xFFFF5C7A)) { go(Route.Favorites) }
                GroupRow(Icons.AutoMirrored.Filled.PlaylistPlay, "Listeler", last = true) { go(Route.Playlists) }
            }
            Group("Oynatıcı") {
                GroupRow(Icons.Filled.SmartDisplay, "Video oynatıcı ayarları", "Hareketler, kaldığı yer, gece modu") { go(Route.SettingsPage(0)) }
                GroupRow(Icons.Filled.MusicNote, "Müzik çalar ayarları", "Yumuşak geçiş, sessizlik, şarkı sözleri") { go(Route.SettingsPage(1)) }
                GroupRow(Icons.Filled.Subtitles, "Altyazı tercihleri", "Boyut, kodlama, online altyazı", last = true) { go(Route.SettingsPage(2)) }
            }
            Group("Görünüm") {
                GroupRow(Icons.Filled.Palette, "Tema", ThemeState.ACCENTS.getOrNull(ThemeState.accentIdx)?.second + if (ThemeState.amoled) " · OLED" else "") { go(Route.SettingsPage(3)) }
                GroupRow(Icons.Filled.ViewAgenda, "Liste ve kart düzeni", last = true) { go(Route.SettingsPage(4)) }
            }
            Group("İndirme") {
                GroupRow(Icons.Filled.Download, "İndirme ayarları", "Aynı anda ${DlPrefs.parallel} indirme" + if (DlPrefs.wifiOnly) " · sadece Wi-Fi" else "") { go(Route.SettingsPage(5)) }
                GroupRow(Icons.Filled.History, "İndirme geçmişi", last = true) { go(Route.DlHistory) }
            }
            Group("Depolama") {
                GroupRow(Icons.Filled.Folder, "Gizlenen klasörler") { go(Route.HiddenFolders) }
                GroupRow(Icons.Filled.CleaningServices, "Önbellek ve temizleme", last = true) { go(Route.StorageR) }
            }
            Group("Uygulama") {
                GroupRow(Icons.Filled.SystemUpdate, "Güncellemeleri denetle", if (checking) "Denetleniyor…" else "Sürüm ${Updater.current(ctx)}", chevron = !checking) { checkUpdate() }
                GroupRow(Icons.Filled.AutoGraph, "Aylık özet", "Bu ay ne dinledin, ne izledin") { go(Route.SummaryR) }
                GroupRow(Icons.Filled.ContentCopy, "Yinelenen şarkılar", "Aynı şarkının farklı kalitedeki dosyaları") { go(Route.SongDupR) }
                GroupRow(Icons.Filled.History, "Dinleme geçmişi", "Ne zaman ne dinledin") { go(Route.ListenHistR) }
                GroupRow(Icons.Filled.BugReport, "Hata raporu", "Sorun olursa kopyalayıp gönder") { go(Route.ReportR) }
                GroupRow(Icons.Filled.Info, "Uygulama hakkında", last = true) { go(Route.SettingsPage(6)) }
            }
        }
    }
}

// ============================ Ayar alt sayfaları ============================
val SETTING_TITLES = listOf("Video oynatıcı", "Müzik çalar", "Altyazı", "Tema", "Liste ve kart düzeni", "İndirme", "Hakkında")

@Composable
fun SettingsPage(vm: LibVM, section: Int, back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var ver by remember { mutableIntStateOf(0) }
    Page(SETTING_TITLES[section], back) {
        key(ver) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
                when (section) {
                    0 -> {
                        Group("Oynatma") {
                            Column { SetChoice("Kaldığı yer", listOf("Otomatik devam et", "Her seferinde sor", "Her zaman baştan başla"), Prefs.resumeMode) { Prefs.resumeMode = it; ver++ }
                                SetChoice("Video bitince", listOf("5 sn geri sayımla sonrakine geç", "Hemen sonrakine geç", "Dur"), Prefs.autoNext) { Prefs.autoNext = it; ver++ }
                                SetChoice("Varsayılan ekran yönü", PlayerActivity.ORIENTS, Prefs.orient) { Prefs.orient = it; ver++ }
                                SetChoice("Çözücü", listOf("Donanım (önerilen, pil dostu)", "Yazılım öncelikli (AC3/DTS ses sorunlarında)"), if (Prefs.decoderSw) 1 else 0) { Prefs.decoderSw = it == 1; ver++ } }
                        }
                        Group("Özellikler") {
                            GroupSwitch(Icons.Filled.PlayCircleOutline, "Arka planda oynat", "Ekran kapanınca ses devam eder", Prefs.bgPlay) { Prefs.bgPlay = it }
                            GroupSwitch(Icons.Filled.PictureInPictureAlt, "Otomatik küçük pencere", "Ana ekrana çıkınca video küçük pencerede sürer", Prefs.autoPip) { Prefs.autoPip = it }
                            GroupSwitch(Icons.Filled.NightsStay, "Gece modu", "Yüksek sesleri kısar, konuşmaları yükseltir", Prefs.night) { Engine.setNight(it) }
                            GroupSwitch(Icons.Filled.VolumeUp, "Ses yükseltme (%200)", "Sağ tarafta yukarı kaydırınca", Prefs.boost) { Prefs.boost = it }
                            GroupSwitch(Icons.Filled.Image, "Sarma önizlemesi", "Sararken o anın küçük resmi", Prefs.seekThumbs) { Prefs.seekThumbs = it }
                            GroupSwitch(Icons.Filled.SkipNext, "Klasördeki sonraki bölüme geç", "Tek video açınca da klasördeki sıradaki video sıraya girer", Prefs.folderNext) { Prefs.folderNext = it }
                            GroupSwitch(Icons.Filled.ScreenRotation, "Yön ve yakınlaştırmayı hatırla", "Her video için en son kullandığın", Prefs.rememberView) { Prefs.rememberView = it }
                            GroupSwitch(Icons.Filled.Tune, "Ses ve parlaklığı hatırla", "Her video için en son ayarladığın değerler", Prefs.rememberLevels, last = true) { Prefs.rememberLevels = it }
                        }
                        Group("Dokunma hareketleri") {
                            GroupSwitch(Icons.Filled.BrightnessMedium, "Sol tarafta kaydır: parlaklık", null, Prefs.gBright) { Prefs.gBright = it }
                            GroupSwitch(Icons.Filled.VolumeUp, "Sağ tarafta kaydır: ses", null, Prefs.gVolume) { Prefs.gVolume = it }
                            GroupSwitch(Icons.Filled.SwapHoriz, "Yatay kaydır: ileri/geri sar", null, Prefs.gSeek) { Prefs.gSeek = it }
                            GroupSwitch(Icons.Filled.TouchApp, "Çift dokun: sar / duraklat", null, Prefs.gDouble) { Prefs.gDouble = it }
                            GroupSwitch(Icons.Filled.ZoomIn, "İki parmakla yakınlaştır", null, Prefs.gPinch) { Prefs.gPinch = it }
                            GroupSwitch(Icons.Filled.FastForward, "Basılı tut: 2× hız", null, Prefs.gLong) { Prefs.gLong = it }
                            val steps = listOf(5, 10, 15, 30)
                            SetChoice("Sarma adımı", steps.map { "$it saniye" }, steps.indexOf(Prefs.seekStep).coerceAtLeast(0)) { Prefs.seekStep = steps[it]; ver++ }
                        }
                    }
                    1 -> {
                        Group("Geçiş") {
                            SetChoice("Yumuşak geçiş", listOf("Kapalı", "2 sn", "4 sn", "6 sn", "8 sn", "12 sn"), listOf(0, 2, 4, 6, 8, 12).indexOf(Prefs.crossfade).coerceAtLeast(0)) { Prefs.crossfade = listOf(0, 2, 4, 6, 8, 12)[it]; ver++ }
                            GroupSwitch(Icons.Filled.SkipNext, "Sessizliği atla", "Şarkı başı ve sonundaki sessizlik", Prefs.skipSilence) { Prefs.skipSilence = it; Engine.player?.skipSilenceEnabled = it }
                            GroupSwitch(Icons.Filled.History, "Şarkıları kaldığı yerden sürdür", "Yarıda bıraktığın şarkı oradan başlar", Prefs.resumeMusic) { Prefs.resumeMusic = it }
                            GroupRow(Icons.Filled.BatteryChargingFull, "Ekran kapanınca kesilmesin", if (BgGuard.ok(ctx)) "Pil kısıtlaması kapalı ✓" else "Pil kısıtlamasını kapat") { BgGuard.request(ctx) }
                            if (BgGuard.xiaomi) GroupRow(Icons.Filled.RestartAlt, "Otomatik başlatma (Xiaomi)", "Xiaomi'de müziğin kesilmemesi için aç") { BgGuard.autostart(ctx) }
                            GroupSwitch(Icons.Filled.Image, "Eksik kapakları internetten bul", "Kapağı olmayan şarkıya kapak bulup kalıcı kaydeder", Prefs.fetchCovers) { Prefs.fetchCovers = it }
                            GroupRow(Icons.Filled.ImageSearch, "Eksik kapakları şimdi bul", "Bütün şarkılar taranır") { toast(ctx, "Kapaklar aranıyor…"); CoverNet.fetchAll(ctx, vm.songs) { n -> toast(ctx, if (n > 0) "$n kapak bulundu" else "Yeni kapak bulunamadı") } }
                            GroupSwitch(Icons.Filled.Palette, "Kapak rengini tema rengi yap", "Çalan şarkının kapağına göre uygulamanın rengi değişir", Prefs.coverAccent) { Prefs.coverAccent = it; CoverTint.update(ctx, if (it) NowState.key.takeIf { k -> k.isNotEmpty() } else null) }
                            GroupSwitch(Icons.Filled.Headphones, "Kulaklık takınca devam et", "Kablolu ya da Bluetooth kulaklık bağlanınca son müzik sürer", Prefs.headsetResume) { Prefs.headsetResume = it }
                            GroupSwitch(Icons.Filled.VolumeUp, "Şarkıya göre sesi hatırla", null, Prefs.rememberLevels, last = true) { Prefs.rememberLevels = it }
                        }
                        Group("Sözler ve ses") {
                            GroupSwitch(Icons.Filled.Lyrics, "İnternetten şarkı sözü", ".lrc yoksa LRCLIB'den bulunur", Prefs.onlineLyrics) { Prefs.onlineLyrics = it }
                            GroupRow(Icons.Filled.Equalizer, "Ekolayzer", "Bas, tiz ve hazır ayarlar", last = true) { AppState.eqSheet = true }
                        }
                    }
                    2 -> {
                        Group("Altyazı") {
                            GroupSwitch(Icons.Filled.Subtitles, "Aynı adlı altyazıyı otomatik yükle", "film.mp4 yanında film.srt varsa açılır", Prefs.autoSub) { Prefs.autoSub = it }
                            SetChoice("Karakter kodlaması", listOf("Otomatik", "UTF-8", "Türkçe (Windows-1254)"), Prefs.subEnc) { Prefs.subEnc = it; ver++ }
                            val sizes = listOf(0.8f, 1f, 1.3f, 1.6f, 2f)
                            SetChoice("Yazı boyutu", listOf("Küçük", "Normal", "Büyük", "Çok büyük", "Dev"), sizes.indexOfFirst { kotlin.math.abs(it - Prefs.subScale) < 0.05f }.coerceAtLeast(1)) { Prefs.subScale = sizes[it]; ver++ }
                        }
                        Group("İnternet") { OnlineSubSettings() }
                    }
                    3 -> {
                        Group("Vurgu rengi") {
                            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                ThemeState.ACCENTS.forEachIndexed { i, (c, n) ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(Modifier.size(42.dp).clip(CircleShape).background(Color(c))
                                            .border(3.dp, if (ThemeState.accentIdx == i) Color.White else Color.Transparent, CircleShape)
                                            .clickable { ThemeState.accentIdx = i; Prefs.accent = i; PerdeWidget.update(ctx) }, contentAlignment = Alignment.Center) {
                                            if (ThemeState.accentIdx == i) Icon(Icons.Filled.Check, null, tint = Color.White)
                                        }
                                        Text(n, fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 4.dp))
                                    }
                                }
                            }
                        }
                        Group("Ekran") {
                            GroupSwitch(Icons.Filled.Contrast, "Tam siyah tema (OLED)", "OLED ekranlarda pil tasarrufu", Prefs.amoled) { Prefs.amoled = it; ThemeState.amoled = it }
                            GroupSwitch(Icons.Filled.Vibration, "Dokunma titreşimi", "Butonlara basınca hafif titreşim", Prefs.haptics, last = true) { Prefs.haptics = it }
                        }
                    }
                    4 -> {
                        Group("Kütüphane") {
                            GroupSwitch(Icons.Filled.GridView, "Izgara görünümü", "Videoları kart olarak göster", Prefs.grid) { Prefs.grid = it }
                            SetChoice("Sıralama", listOf("Ada göre", "Tarihe göre", "Boyuta göre", "Süreye göre"), Prefs.sortBy) { Prefs.sortBy = it; ver++ }
                            GroupSwitch(Icons.Filled.SwapVert, "Ters sırala", null, Prefs.sortDesc) { Prefs.sortDesc = it }
                            GroupSwitch(Icons.Filled.Delete, "Silinenleri çöp kutusuna taşı", "30 gün içinde geri alınabilir", Prefs.useTrash, last = true) { Prefs.useTrash = it }
                        }
                    }
                    5 -> Group { DlSettingsContent() }
                    6 -> {
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            KLogo(84.dp)
                            Text("Kadraj", fontWeight = FontWeight.Black, fontSize = 28.sp, modifier = Modifier.padding(top = 12.dp))
                            Text("Sürüm ${Updater.current(ctx)}", color = Muted)
                            Text("Kişisel video ve müzik oynatıcı", color = Muted, fontSize = 13.sp)
                        }
                        var yt by remember { mutableStateOf("…") }
                        LaunchedEffect(Unit) { yt = withContext(Dispatchers.IO) { Web.version(ctx) } }
                        Group("Bileşenler") {
                            GroupRow(Icons.Filled.Movie, "Oynatıcı", "Media3 (ExoPlayer) + FFmpeg ses çözücü", chevron = false) {}
                            GroupRow(Icons.Filled.Download, "İndirme motoru", "yt-dlp $yt", chevron = false) {}
                            GroupRow(Icons.Filled.Lyrics, "Şarkı sözleri", "LRCLIB", chevron = false, last = true) {}
                        }
                        Group("Bakım") {
                            GroupRow(Icons.Filled.SystemUpdate, "Güncellemeleri denetle") {
                                scope.launch {
                                    val r = Updater.check()
                                    when { r == null -> toast(ctx, Updater.lastError ?: "Sunucuya ulaşılamadı")
                                        Updater.newer(r.version, Updater.current(ctx)) -> AppState.update = r
                                        else -> toast(ctx, "En güncel sürümü kullanıyorsun (${r.version})") }
                                }
                            }
                            GroupRow(Icons.Filled.History, "İzleme geçmişini temizle", "Kaldığı yer bilgileri sıfırlanır") {
                                val e = Prefs.sp.edit()
                                Prefs.sp.all.keys.filter { it.startsWith("p:") || it.startsWith("d:") || it.startsWith("t:") }.forEach { e.remove(it) }
                                e.remove("last").apply(); vm.bump(); toast(ctx, "İzleme geçmişi temizlendi")
                            }
                            GroupRow(Icons.Filled.BarChart, "İstatistikleri sıfırla", last = true) { Stats.clear(); toast(ctx, "İstatistikler sıfırlandı") }
                        }
                    }
                }
            }
        }
    }
}
