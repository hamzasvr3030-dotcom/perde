package com.perde.oynatici

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors

/**
 * İnce ilerleme çubuğu: sürüklerken kalınlaşır, dokununca o yere gider.
 * onDrag sürükleme boyunca (0..1), onDone bırakınca çağrılır.
 */
@Composable
fun SeekLine(frac: Float, modifier: Modifier = Modifier, active: Color = Color.White, track: Color = Color.White.copy(alpha = 0.25f),
             marks: List<Pair<Float, Color>> = emptyList(), onDrag: (Float) -> Unit, onDone: (Float) -> Unit) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val h by animateDpAsState(if (drag != null) 8.dp else 4.dp, label = "sk")
    val r by animateDpAsState(if (drag != null) 10.dp else 7.dp, label = "sr")
    val cur = rememberUpdatedState(onDrag); val done = rememberUpdatedState(onDone)
    Canvas(modifier.fillMaxWidth().height(32.dp)
        .pointerInput(Unit) {
            detectTapGestures { o -> val f = (o.x / size.width).coerceIn(0f, 1f); done.value(f) }
        }
        .pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { o -> val f = (o.x / size.width).coerceIn(0f, 1f); drag = f; cur.value(f) },
                onDragEnd = { drag?.let { done.value(it) }; drag = null },
                onDragCancel = { drag?.let { done.value(it) }; drag = null }
            ) { ch, dx -> ch.consume(); val f = ((drag ?: 0f) + dx / size.width).coerceIn(0f, 1f); drag = f; cur.value(f) }
        }) {
        val f = (drag ?: frac).coerceIn(0f, 1f)
        val th = h.toPx(); val y = size.height / 2; val cr = CornerRadius(th / 2)
        drawRoundRect(track, Offset(0f, y - th / 2), Size(size.width, th), cr)
        drawRoundRect(active, Offset(0f, y - th / 2), Size(size.width * f, th), cr)
        marks.forEach { (m, c) -> drawCircle(c, 2.5.dp.toPx(), Offset(size.width * m, y - th / 2 - 5.dp.toPx())) }
        drawCircle(active, r.toPx(), Offset(size.width * f, y))
    }
}

/** Bildirim / kilit ekranı kapakları: "kadrajart:" adresleri uygulamanın kendi kapak yükleyicisiyle açılır. */
@UnstableApi
class ArtBitmapLoader(ctx: Context) : BitmapLoader {
    private val app = ctx.applicationContext
    private val def = DataSourceBitmapLoader(app)
    private val ex = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    override fun supportsMimeType(mimeType: String) = def.supportsMimeType(mimeType)
    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = def.decodeBitmap(data)
    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        if (uri.scheme != SCHEME) return def.loadBitmap(uri)
        return ex.submit<Bitmap> {
            val key = uri.schemeSpecificPart
            val s = Music.byKey(key)
            runBlocking { Art.load(app, key, s?.uri, s?.path ?: key.takeIf { it.startsWith("/") }, 512) }
                ?: throw IllegalStateException("kapak yok")
        }
    }

    companion object {
        const val SCHEME = "kadrajart"
        fun uriFor(key: String): Uri = Uri.fromParts(SCHEME, key, null)
    }
}

/** MediaMetadata'da kapak yoksa (video) boş geç. */
fun MediaMetadata.Builder.kadrajArt(key: String) = setArtworkUri(ArtBitmapLoader.uriFor(key))
