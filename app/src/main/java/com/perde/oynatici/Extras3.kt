@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.perde.oynatici

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Size
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import java.util.Locale

// ============================ Uygulama içi tarayıcı ============================
/** Herhangi bir sitede gezerken sayfadaki videoyu yakalayıp oynatma/indirme. */
class BrowserActivity : ComponentActivity() {
    var web: WebView? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CookieManager.getInstance().setAcceptCookie(true)
        onBackPressedDispatcher.addCallback(this) { if (web?.canGoBack() == true) web?.goBack() else finish() }
        setContent { PerdeTheme { androidx.compose.material3.Surface(color = Bg, contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onBackground) { BrowserScreen(this, intent.getStringExtra("url")) } } }
    }
    override fun onPause() { super.onPause(); web?.onPause(); Cookies.save(this) }
    override fun onResume() { super.onResume(); web?.onResume() }
    override fun onDestroy() { web?.destroy(); web = null; super.onDestroy() }
}

private val QUICK_SITES = listOf(
    Triple("YouTube", "https://m.youtube.com", Color(0xFFFF3B30)), Triple("Instagram", "https://www.instagram.com", Color(0xFFE1306C)),
    Triple("TikTok", "https://www.tiktok.com", Color(0xFF25F4EE)), Triple("X", "https://x.com", Color(0xFFDDDDDD)),
    Triple("Facebook", "https://m.facebook.com", Color(0xFF1877F2)), Triple("Vimeo", "https://vimeo.com", Color(0xFF1AB7EA)),
    Triple("Dailymotion", "https://www.dailymotion.com", Color(0xFF00AAFF)), Triple("Reddit", "https://www.reddit.com", Color(0xFFFF4500)),
    Triple("SoundCloud", "https://m.soundcloud.com", Color(0xFFFF7700)), Triple("Google", "https://www.google.com", Color(0xFF4285F4))
)
private val MEDIA_RX = Regex("\\.(mp4|m4v|webm|mkv|mov|m3u8|mpd|mp3|m4a|aac|ogg|opus)(\\?|$)", RegexOption.IGNORE_CASE)

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun BrowserScreen(a: BrowserActivity, start: String?) {
    var address by remember { mutableStateOf(start ?: "") }
    var page by remember { mutableStateOf(start ?: "") }
    var title by remember { mutableStateOf("") }
    var progress by remember { mutableIntStateOf(100) }
    var desktop by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val found = remember { mutableStateListOf<String>() }
    val mobileUa = remember { android.webkit.WebSettings.getDefaultUserAgent(a) }
    val desktopUa = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Safari/537.36"

    fun go(input: String) {
        val t = input.trim(); if (t.isEmpty()) return
        val u = when {
            Web.isHttp(t) -> t
            '.' in t && ' ' !in t -> "https://$t"
            else -> "https://www.google.com/search?q=" + Uri.encode(t)
        }
        page = u; address = u; a.web?.loadUrl(u)
    }

    Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { a.finish() }) { Icon(Icons.Filled.Close, "Kapat") }
            OutlinedTextField(address, { address = it }, singleLine = true, modifier = Modifier.weight(1f).height(52.dp), shape = CircleShape,
                placeholder = { Text("Ara veya adres yaz") }, textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                leadingIcon = { Icon(if (page.startsWith("https")) Icons.Filled.Lock else Icons.Filled.Public, null, Modifier.size(18.dp), tint = Muted) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go), keyboardActions = KeyboardActions(onGo = { go(address) }))
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Menü") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text(if (desktop) "Mobil görünüm" else "Masaüstü görünümü") }, {
                        menu = false; desktop = !desktop; a.web?.settings?.userAgentString = if (desktop) desktopUa else mobileUa; a.web?.reload()
                    }, leadingIcon = { Icon(Icons.Filled.Computer, null) })
                    DropdownMenuItem({ Text("Ana sayfa") }, { menu = false; page = ""; address = ""; found.clear() }, leadingIcon = { Icon(Icons.Filled.Home, null) })
                    DropdownMenuItem({ Text("Kadraj'da indir sekmesinde aç") }, { menu = false; if (page.isNotEmpty()) openInDownloads(a, page) }, leadingIcon = { Icon(Icons.Filled.Download, null) })
                }
            }
        }
        if (progress < 100) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth().height(2.dp), color = Accent, trackColor = Color.Transparent)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(factory = { c ->
                WebView(c).apply {
                    a.web = this
                    settings.javaScriptEnabled = true; settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = true; settings.loadWithOverviewMode = true; settings.useWideViewPort = true
                    settings.builtInZoomControls = true; settings.displayZoomControls = false
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val s = request.url.scheme ?: return true
                            return s != "http" && s != "https"   // intent://, market:// vb. uygulama dışına çıkmasın
                        }
                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) { page = url; address = url; found.clear() }
                        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) { page = url; address = url }
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                            val u = request.url.toString()
                            if (MEDIA_RX.containsMatchIn(u.substringBefore('#')) && "googlevideo" !in u && "blob:" !in u)
                                view.post { if (u !in found && found.size < 40) found.add(0, u) }
                            return null
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, p: Int) { progress = p }
                        override fun onReceivedTitle(view: WebView, t: String?) { title = t ?: "" }
                    }
                    if (page.isNotEmpty()) loadUrl(page)
                }
            }, modifier = Modifier.fillMaxSize())
            if (page.isEmpty()) BrowserHome { go(it) }
            // yakalanan videolar
            val pageOk = Web.isPage(page) && "google." !in page
            if (page.isNotEmpty() && (found.isNotEmpty() || pageOk))
                ExtendedFloatingActionButton(onClick = { sheet = true }, containerColor = Accent, contentColor = Color.White,
                    icon = { Icon(Icons.Filled.Download, null) }, text = { Text(if (found.isEmpty()) "İndir" else "İndir (${found.size})") },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp))
        }
        Row(Modifier.fillMaxWidth().background(Surf).padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            IconButton(onClick = { a.web?.goBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") }
            IconButton(onClick = { a.web?.goForward() }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "İleri") }
            IconButton(onClick = { a.web?.reload() }) { Icon(Icons.Filled.Refresh, "Yenile") }
            IconButton(onClick = { page = ""; address = ""; found.clear() }) { Icon(Icons.Filled.Home, "Ana sayfa") }
        }
    }

    if (sheet) ModalBottomSheet(onDismissRequest = { sheet = false }, containerColor = Surf) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Bu sayfadaki videolar", fontSize = 21.sp, fontWeight = FontWeight.Bold)
            if (Web.isPage(page) && "google." !in page) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(16.dp)).background(Surf2).clickable { sheet = false; openInDownloads(a, page) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.TravelExplore, null, tint = Accent)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text("Sayfanın videosunu incele", fontWeight = FontWeight.SemiBold)
                        Text("${Web.site(page)} · ${title.ifEmpty { page }}", color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = Muted)
                }
            }
            if (found.isNotEmpty()) Text("Yakalanan dosyalar", color = Accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
            found.forEach { u ->
                val name = Uri.parse(u).lastPathSegment ?: u
                val audio = Regex("\\.(mp3|m4a|aac|ogg|opus)(\\?|$)", RegexOption.IGNORE_CASE).containsMatchIn(u)
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (audio) Icons.Filled.MusicNote else Icons.Filled.Movie, null, tint = Muted)
                    Text(name, Modifier.weight(1f).padding(horizontal = 10.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                    IconButton(onClick = {
                        Uri.parse(u).host?.let { h -> Web.headers[h] = mapOf("Referer" to page, "User-Agent" to (a.web?.settings?.userAgentString ?: "")) }
                        PlayerActivity.openKeys(a, listOf(u), listOf(name), 0)
                    }) { Icon(Icons.Filled.PlayArrow, "Oynat", tint = Accent) }
                    IconButton(onClick = {
                        Dl.addSingles(a, listOf(Triple(u, title.ifEmpty { name }, null)), DlOpts.defaults(audio)); toast(a, "İndirme başladı")
                    }) { Icon(Icons.Filled.Download, "İndir", tint = Accent) }
                }
            }
            if (found.isEmpty() && !Web.isPage(page)) Text("Henüz video yakalanmadı. Videoyu oynatmayı dene; oynayan video buraya düşer.", color = Muted)
        }
    }
}

@Composable
private fun BrowserHome(open: (String) -> Unit) {
    Column(Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { KLogo(40.dp); Text("Kadraj Tarayıcı", fontWeight = FontWeight.Black, fontSize = 24.sp, modifier = Modifier.padding(start = 12.dp)) }
        Text("Bir siteye gir, videoyu aç; alttaki İndir düğmesiyle kaydet.", color = Muted, modifier = Modifier.padding(top = 6.dp, bottom = 20.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), maxItemsInEachRow = 4) {
            QUICK_SITES.forEach { (n, u, c) ->
                Column(Modifier.width(74.dp).clip(RoundedCornerShape(16.dp)).clickable { open(u) }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(c.copy(alpha = 0.18f)).border(1.dp, c.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center) { Text(n.take(1), color = c, fontWeight = FontWeight.Black, fontSize = 22.sp) }
                    Text(n, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp), maxLines = 1)
                }
            }
        }
    }
}

private fun openInDownloads(ctx: Context, url: String) {
    Cookies.save(ctx)
    ctx.startActivity(Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, url)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
}

// ============================ Hızlı ayar kutucukları ============================
private fun TileService.launch(i: Intent) {
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
    else @Suppress("DEPRECATION") startActivityAndCollapse(i)
}

/** Bildirim panelinden müzik/video çal–duraklat. */
class PlayTile : TileService() {
    override fun onStartListening() { refresh() }
    override fun onClick() {
        val p = Engine.player
        if (p != null && p.mediaItemCount > 0) { if (p.isPlaying) p.pause() else p.play(); refresh() }
        else launch(Intent(this, MainActivity::class.java).putExtra("tab", 1))
    }
    private fun refresh() {
        val t = qsTile ?: return
        val p = Engine.player
        val playing = p?.isPlaying == true
        t.state = if (p != null && p.mediaItemCount > 0) (if (playing) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE) else Tile.STATE_INACTIVE
        t.label = if (playing) "Duraklat" else "Kadraj çal"
        if (Build.VERSION.SDK_INT >= 29) t.subtitle = NowState.title.ifEmpty { "Müzik" }.take(30)
        t.updateTile()
    }
    companion object {
        fun request(ctx: Context) = try { requestListeningState(ctx, ComponentName(ctx, PlayTile::class.java)) } catch (_: Exception) { }
    }
}

/** Bildirim panelinden: kopyalanan bağlantıyı Kadraj'da indir. */
class LinkTile : TileService() {
    override fun onStartListening() { qsTile?.apply { state = Tile.STATE_INACTIVE; label = "Bağlantıyı indir"; updateTile() } }
    override fun onClick() { launch(Intent(this, MainActivity::class.java).putExtra("fromClipTile", true)) }
}

// ============================ Büyük widget'lar ============================
private fun scaled(b: Bitmap?, px: Int): Bitmap? = b?.let {
    val s = minOf(1f, px.toFloat() / maxOf(it.width, it.height))
    if (s >= 1f) it else Bitmap.createScaledBitmap(it, (it.width * s).toInt().coerceAtLeast(1), (it.height * s).toInt().coerceAtLeast(1), true)
}

/** 4×2 müzik çalar widget'ı. */
class MusicWidget : AppWidgetProvider() {
    companion object {
        const val PREV = "com.perde.oynatici.MW_PREV"; const val TOGGLE = "com.perde.oynatici.MW_TOGGLE"; const val NEXT = "com.perde.oynatici.MW_NEXT"
        const val SHUF = "com.perde.oynatici.MW_SHUF"; const val REP = "com.perde.oynatici.MW_REP"; const val FAV = "com.perde.oynatici.MW_FAV"
        @Volatile private var artKey = ""; @Volatile private var art: Bitmap? = null

        fun update(ctx: Context) {
            val m = AppWidgetManager.getInstance(ctx)
            val ids = try { m.getAppWidgetIds(ComponentName(ctx, MusicWidget::class.java)) } catch (_: Exception) { intArrayOf() }
            if (ids.isEmpty()) return
            m.updateAppWidget(ids, views(ctx))
            val key = NowState.key
            if (key.isNotEmpty() && key != artKey) Thread {
                art = loadArt(ctx, key); artKey = key
                try { m.updateAppWidget(ids, views(ctx)) } catch (_: Exception) { }
            }.start()
        }

        private fun loadArt(ctx: Context, key: String): Bitmap? = try {
            val s = Music.byKey(key)
            var b: Bitmap? = null
            if (s != null && Build.VERSION.SDK_INT >= 29) b = try { ctx.contentResolver.loadThumbnail(s.uri, Size(256, 256), null) } catch (_: Exception) { null }
            if (b == null && key.startsWith("/")) b = MediaMetadataRetriever().run {
                setDataSource(key); val pic = embeddedPicture; release(); pic?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            }
            scaled(b, 256)
        } catch (_: Exception) { null }

        private fun bc(ctx: Context, a: String, rc: Int) = PendingIntent.getBroadcast(ctx, rc, Intent(ctx, MusicWidget::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        fun views(ctx: Context): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_music)
            val p = Engine.player
            val active = p != null && p.mediaItemCount > 0
            v.setTextViewText(R.id.mw_title, if (active) NowState.title.ifEmpty { "Kadraj" } else "Müzik çalmıyor")
            v.setTextViewText(R.id.mw_artist, if (active) NowState.artist.ifEmpty { if (NowState.isMusic) "" else "Video" } else "Dokun ve bir şarkı seç")
            val a = art
            if (active && a != null && artKey == NowState.key) { v.setImageViewBitmap(R.id.mw_art, a); v.setImageViewBitmap(R.id.mw_bg, a) }
            else { v.setImageViewResource(R.id.mw_art, R.drawable.ic_launcher_foreground); v.setImageViewResource(R.id.mw_bg, android.R.color.transparent) }
            v.setImageViewResource(R.id.mw_play, if (p?.isPlaying == true) R.drawable.ic_pause_dark else R.drawable.ic_play_dark)
            v.setImageViewResource(R.id.mw_shuffle, if (p?.shuffleModeEnabled == true) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle)
            v.setImageViewResource(R.id.mw_repeat, when (p?.repeatMode) { androidx.media3.common.Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat_on; androidx.media3.common.Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one; else -> R.drawable.ic_repeat })
            v.setImageViewResource(R.id.mw_fav, if (active && Favs.has(NowState.key)) R.drawable.ic_heart_on else R.drawable.ic_heart)
            val ni = p?.nextMediaItemIndex ?: -1
            v.setTextViewText(R.id.mw_next_t, if (active && p != null && ni >= 0 && ni < p.mediaItemCount) "Sıradaki: " + (p.getMediaItemAt(ni).mediaMetadata.title ?: "") else "")
            val open = PendingIntent.getActivity(ctx, 21, Intent(ctx, MainActivity::class.java).putExtra(if (active) "openNow" else "tab", if (active) true else 1)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            v.setOnClickPendingIntent(R.id.mw_root, open)
            v.setOnClickPendingIntent(R.id.mw_prev, bc(ctx, PREV, 22))
            v.setOnClickPendingIntent(R.id.mw_play, if (active) bc(ctx, TOGGLE, 23) else open)
            v.setOnClickPendingIntent(R.id.mw_next, bc(ctx, NEXT, 24))
            v.setOnClickPendingIntent(R.id.mw_shuffle, bc(ctx, SHUF, 25))
            v.setOnClickPendingIntent(R.id.mw_repeat, bc(ctx, REP, 26))
            v.setOnClickPendingIntent(R.id.mw_fav, bc(ctx, FAV, 27))
            return v
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) { appWidgetManager.updateAppWidget(appWidgetIds, views(context)) }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val p = Engine.player
        when (intent.action) {
            TOGGLE -> p?.let { if (it.isPlaying) it.pause() else it.play() }
            PREV -> p?.let { if (it.currentPosition > 4000) it.seekTo(0) else it.seekToPreviousMediaItem() }
            NEXT -> p?.seekToNextMediaItem()
            SHUF -> p?.let { Engine.setShuffle(!it.shuffleModeEnabled) }
            REP -> p?.let { it.repeatMode = when (it.repeatMode) { androidx.media3.common.Player.REPEAT_MODE_OFF -> androidx.media3.common.Player.REPEAT_MODE_ALL; androidx.media3.common.Player.REPEAT_MODE_ALL -> androidx.media3.common.Player.REPEAT_MODE_ONE; else -> androidx.media3.common.Player.REPEAT_MODE_OFF } }
            FAV -> NowState.key.takeIf { it.isNotEmpty() }?.let { Favs.toggle(it) }
        }
        if (intent.action in setOf(TOGGLE, PREV, NEXT, SHUF, REP, FAV)) update(context)
    }
}

/** 4×2 "Son eklenenler" widget'ı: en yeni 4 video. */
class RecentWidget : AppWidgetProvider() {
    companion object {
        private val SLOTS = listOf(Triple(R.id.rw_img1, R.id.rw_t1, R.id.rw_s1), Triple(R.id.rw_img2, R.id.rw_t2, R.id.rw_s2),
            Triple(R.id.rw_img3, R.id.rw_t3, R.id.rw_s3), Triple(R.id.rw_img4, R.id.rw_t4, R.id.rw_s4))

        fun refresh(ctx: Context) {
            val m = AppWidgetManager.getInstance(ctx)
            val ids = try { m.getAppWidgetIds(ComponentName(ctx, RecentWidget::class.java)) } catch (_: Exception) { intArrayOf() }
            if (ids.isEmpty()) return
            Thread { try { m.updateAppWidget(ids, build(ctx)) } catch (_: Exception) { } }.start()
        }

        private fun latest(ctx: Context): List<Triple<Long, String, String>> {
            val out = ArrayList<Triple<Long, String, String>>()
            val app = (ctx.getExternalFilesDir(null) ?: ctx.filesDir).absolutePath
            try {
                ctx.contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DATA, MediaStore.Video.Media.DISPLAY_NAME),
                    null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC")?.use { c ->
                    while (c.moveToNext() && out.size < 4) {
                        val path = c.getString(1) ?: continue
                        if (path.startsWith(app)) continue
                        out += Triple(c.getLong(0), path, c.getString(2) ?: File(path).name)
                    }
                }
            } catch (_: Exception) { }
            return out
        }

        private fun build(ctx: Context): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_recent)
            val list = latest(ctx)
            v.setOnClickPendingIntent(R.id.rw_root, PendingIntent.getActivity(ctx, 30, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE))
            SLOTS.forEachIndexed { i, (img, t, slot) ->
                val e = list.getOrNull(i)
                if (e == null) { v.setViewVisibility(slot, android.view.View.INVISIBLE); return@forEachIndexed }
                v.setViewVisibility(slot, android.view.View.VISIBLE)
                val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, e.first)
                val bmp = try {
                    if (Build.VERSION.SDK_INT >= 29) ctx.contentResolver.loadThumbnail(uri, Size(320, 180), null)
                    else @Suppress("DEPRECATION") MediaStore.Video.Thumbnails.getThumbnail(ctx.contentResolver, e.first, MediaStore.Video.Thumbnails.MINI_KIND, null)
                } catch (_: Exception) { null }
                scaled(bmp, 320)?.let { v.setImageViewBitmap(img, it) } ?: v.setImageViewResource(img, R.drawable.ic_launcher_foreground)
                v.setTextViewText(t, e.third.substringBeforeLast('.'))
                val play = Intent(ctx, PlayerActivity::class.java)
                    .putStringArrayListExtra(PlayerActivity.EXTRA_LIST, arrayListOf(e.second))
                    .putStringArrayListExtra(PlayerActivity.EXTRA_TITLES, arrayListOf(e.third))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                v.setOnClickPendingIntent(slot, PendingIntent.getActivity(ctx, 31 + i, play, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            }
            return v
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pr = goAsync()
        Thread { try { appWidgetManager.updateAppWidget(appWidgetIds, build(context)) } catch (_: Exception) { } finally { pr.finish() } }.start()
    }
}
