package com.perde.oynatici

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

/** Arka planda oynatma ve kilit ekranı / bildirim kontrolleri. */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    private fun build(p: androidx.media3.common.Player): MediaSession {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return MediaSession.Builder(this, p).setSessionActivity(pi).build()
    }

    override fun onCreate() {
        super.onCreate()
        session = build(Engine.get(this))
    }

    /** Oynatıcı kapatılıp yeniden açıldıysa oturumu yeni oynatıcıya bağla. */
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        val p = Engine.get(this)
        if (session?.player !== p) {
            session?.let { removeSession(it); it.release() }
            session = build(p)
        }
        return session
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = Engine.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }
}

/** Chromecast: Google'ın varsayılan alıcısı kullanılır (ek kurulum gerekmez). */
class PerdeCastOptions : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder().setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID).build()
    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
