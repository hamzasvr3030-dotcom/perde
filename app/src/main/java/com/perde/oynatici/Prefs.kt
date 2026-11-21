package com.perde.oynatici

import android.content.Context
import android.content.SharedPreferences
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Tüm kalıcı ayarlar ve video başına hatırlanan bilgiler. */
object Prefs {
    lateinit var sp: SharedPreferences
    fun init(c: Context) { sp = c.getSharedPreferences("perde", Context.MODE_PRIVATE) }

    // Görünüm
    var accent by IntP("accent", 0)
    var amoled by BoolP("amoled", false)
    var haptics by BoolP("haptic", true)

    // Kütüphane
    var grid by BoolP("grid", false)
    var sortBy by IntP("sortBy", 0)          // 0 ad, 1 tarih, 2 boyut, 3 süre
    var sortDesc by BoolP("sortDesc", false)
    var useTrash by BoolP("trash", true)

    // Hareketler
    var gBright by BoolP("gBright", true)
    var gVolume by BoolP("gVolume", true)
    var gSeek by BoolP("gSeek", true)
    var gDouble by BoolP("gDouble", true)
    var gPinch by BoolP("gPinch", true)
    var gLong by BoolP("gLong", true)
    var seekStep by IntP("seekStep", 10)
    var seekThumbs by BoolP("seekThumbs", true)

    // Oynatıcı
    var orient by IntP("orient", 0)          // 0 videoya göre, 1 otomatik, 2 yatay, 3 dikey
    var resumeMode by IntP("resumeMode", 0)  // 0 otomatik devam, 1 sor, 2 hep baştan
    var autoNext by IntP("autoNext", 0)      // 0 geri sayımla, 1 hemen, 2 dur
    var bgPlay by BoolP("bgPlay", false)
    var autoPip by BoolP("autoPip", true)
    var decoderSw by BoolP("decSw", false)
    var boost by BoolP("boost", true)
    var night by BoolP("night", false)
    var repeat by IntP("repeat", 0)          // 0 kapalı, 1 tek video, 2 klasör
    var shuffle by BoolP("shuffle", false)

    // Altyazı
    var subScale by FloatP("subScale", 1f)
    var subColor by IntP("subColor", 0)
    var subBg by IntP("subBg", 1)            // 0 yok, 1 yarı saydam, 2 siyah
    var subEnc by IntP("subEnc", 0)          // 0 otomatik, 1 UTF-8, 2 Windows-1254
    var autoSub by BoolP("autoSub", true)
    var osKey by StrP("osKey", "")
    var osUser by StrP("osUser", "")
    var osPass by StrP("osPass", "")
    var osLangs by StrP("osLangs", "tr,en")

    // Ses efektleri
    var eqOn by BoolP("eqOn", false)
    var eqPreset by IntP("eqPreset", -1)
    var eqBands by StrP("eqBands", "")
    var bass by IntP("bass", 0)

    // Müzik
    var musicRepeat by IntP("mRepeat", 2)
    var musicShuffle by BoolP("mShuffle", false)
    var crossfade by IntP("xfade", 0)        // saniye: 0 kapalı
    var skipSilence by BoolP("skipSil", false)
    var onlineLyrics by BoolP("onLyr", true)
    var autoMeta by BoolP("autoMeta", false)
    var headsetResume by BoolP("hsResume", true)
    var rememberLevels by BoolP("remLevels", true)

    // Diğer
    var pinHash by StrP("pin", "")
    var bio by BoolP("bio", true)
    var netHistory by StrP("net", "")
    var smbServers by StrP("smb", "[]")
    var playlists by StrP("pl", "[]")
    var trashMeta by StrP("trashMeta", "{}")
    var lastKey by StrP("last", "")
    var lastTitle by StrP("lastTitle", "")
    var lastUpdateCheck by LongP("updChk", 0L)
    var skipVersion by StrP("skipVer", "")

    // Video başına
    fun pos(k: String) = sp.getLong("p:$k", 0)
    fun dur(k: String) = sp.getLong("d:$k", 0)
    fun playedAt(k: String) = sp.getLong("t:$k", 0)
    fun played(k: String) = sp.contains("d:$k")
    fun savePos(k: String, p: Long, d: Long) {
        sp.edit().putLong("p:$k", p).putLong("d:$k", d).putLong("t:$k", System.currentTimeMillis()).apply()
    }
    fun sub(k: String): String? = sp.getString("s:$k", null)
    fun setSub(k: String, v: String?) { sp.edit().apply { if (v == null) remove("s:$k") else putString("s:$k", v) }.apply() }
    fun subOff(k: String) = sp.getLong("so:$k", 0)
    fun setSubOff(k: String, v: Long) { sp.edit().putLong("so:$k", v).apply() }

    private val PER_KEY = listOf("p:", "d:", "t:", "s:", "so:", "bm:", "sv:", "pc:", "vol:", "br:")
    fun forget(k: String) { sp.edit().apply { PER_KEY.forEach { remove(it + k) } }.apply() }

    /** Dosya taşındığında/yeniden adlandırıldığında video bilgilerini yeni yola aktarır. */
    fun moveKey(old: String, new: String) {
        val all = sp.all
        val e = sp.edit()
        for (p in PER_KEY) {
            when (val v = all[p + old]) {
                is Long -> e.putLong(p + new, v)
                is String -> e.putString(p + new, v)
                is Int -> e.putInt(p + new, v)
                is Float -> e.putFloat(p + new, v)
                else -> continue
            }
            e.remove(p + old)
        }
        val fav = set(Favs.K)
        if (old in fav) e.putStringSet(Favs.K, HashSet(fav - old + new))
        if (lastKey == old) e.putString("last", new)
        e.apply()
        Playlists.renameKey(old, new)
    }

    fun set(k: String): Set<String> = sp.getStringSet(k, emptySet()) ?: emptySet()
    fun putSet(k: String, s: Set<String>) { sp.edit().putStringSet(k, HashSet(s)).apply() }
    fun toggleIn(k: String, v: String): Boolean {
        val s = set(k).toMutableSet()
        val added = if (v in s) { s.remove(v); false } else { s.add(v); true }
        putSet(k, s); return added
    }
}

class BoolP(private val k: String, private val d: Boolean) : ReadWriteProperty<Any?, Boolean> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) = Prefs.sp.getBoolean(k, d)
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) { Prefs.sp.edit().putBoolean(k, value).apply() }
}
class IntP(private val k: String, private val d: Int) : ReadWriteProperty<Any?, Int> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) = Prefs.sp.getInt(k, d)
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) { Prefs.sp.edit().putInt(k, value).apply() }
}
class LongP(private val k: String, private val d: Long) : ReadWriteProperty<Any?, Long> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) = Prefs.sp.getLong(k, d)
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Long) { Prefs.sp.edit().putLong(k, value).apply() }
}
class FloatP(private val k: String, private val d: Float) : ReadWriteProperty<Any?, Float> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) = Prefs.sp.getFloat(k, d)
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) { Prefs.sp.edit().putFloat(k, value).apply() }
}
class StrP(private val k: String, private val d: String) : ReadWriteProperty<Any?, String> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) = Prefs.sp.getString(k, d) ?: d
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) { Prefs.sp.edit().putString(k, value).apply() }
}
