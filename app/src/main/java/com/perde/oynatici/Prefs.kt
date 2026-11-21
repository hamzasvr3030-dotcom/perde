package com.perde.oynatici

import android.content.Context
import android.content.SharedPreferences
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Tüm kalıcı ayarlar ve video başına hatırlanan bilgiler. */
object Prefs {
    lateinit var sp: SharedPreferences
    fun init(c: Context) { sp = c.getSharedPreferences("perde", Context.MODE_PRIVATE) }

    // Kütüphane
    var grid by BoolP("grid", false)
    var sortBy by IntP("sortBy", 0)          // 0 ad, 1 tarih, 2 boyut, 3 süre
    var sortDesc by BoolP("sortDesc", false)

    // Hareketler
    var gBright by BoolP("gBright", true)
    var gVolume by BoolP("gVolume", true)
    var gSeek by BoolP("gSeek", true)
    var gDouble by BoolP("gDouble", true)
    var gPinch by BoolP("gPinch", true)
    var gLong by BoolP("gLong", true)
    var seekStep by IntP("seekStep", 10)

    // Oynatıcı
    var orient by IntP("orient", 0)          // 0 videoya göre, 1 otomatik, 2 yatay, 3 dikey
    var resume by BoolP("resume", true)
    var bgPlay by BoolP("bgPlay", false)
    var autoPip by BoolP("autoPip", true)
    var decoderSw by BoolP("decSw", false)
    var boost by BoolP("boost", true)
    var repeat by IntP("repeat", 0)          // 0 kapalı, 1 tek video, 2 klasör
    var shuffle by BoolP("shuffle", false)

    // Altyazı
    var subScale by FloatP("subScale", 1f)
    var subColor by IntP("subColor", 0)
    var subBg by IntP("subBg", 1)            // 0 yok, 1 yarı saydam, 2 siyah
    var subEnc by IntP("subEnc", 0)          // 0 otomatik, 1 UTF-8, 2 Windows-1254
    var autoSub by BoolP("autoSub", true)

    // Ses efektleri
    var eqOn by BoolP("eqOn", false)
    var eqPreset by IntP("eqPreset", -1)
    var eqBands by StrP("eqBands", "")
    var bass by IntP("bass", 0)

    // Diğer
    var pinHash by StrP("pin", "")
    var netHistory by StrP("net", "")
    var smbServers by StrP("smb", "[]")
    var lastKey by StrP("last", "")
    var lastTitle by StrP("lastTitle", "")

    // Video başına
    fun pos(k: String) = sp.getLong("p:$k", 0)
    fun dur(k: String) = sp.getLong("d:$k", 0)
    fun played(k: String) = sp.contains("d:$k")
    fun savePos(k: String, p: Long, d: Long) { sp.edit().putLong("p:$k", p).putLong("d:$k", d).apply() }
    fun sub(k: String): String? = sp.getString("s:$k", null)
    fun setSub(k: String, v: String?) { sp.edit().apply { if (v == null) remove("s:$k") else putString("s:$k", v) }.apply() }
    fun subOff(k: String) = sp.getLong("so:$k", 0)
    fun setSubOff(k: String, v: Long) { sp.edit().putLong("so:$k", v).apply() }
    fun forget(k: String) { sp.edit().remove("p:$k").remove("d:$k").remove("s:$k").remove("so:$k").apply() }
}

class BoolP(private val k: String, private val d: Boolean) : ReadWriteProperty<Any?, Boolean> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) = Prefs.sp.getBoolean(k, d)
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) { Prefs.sp.edit().putBoolean(k, value).apply() }
}
class IntP(private val k: String, private val d: Int) : ReadWriteProperty<Any?, Int> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) = Prefs.sp.getInt(k, d)
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) { Prefs.sp.edit().putInt(k, value).apply() }
}
class FloatP(private val k: String, private val d: Float) : ReadWriteProperty<Any?, Float> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) = Prefs.sp.getFloat(k, d)
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) { Prefs.sp.edit().putFloat(k, value).apply() }
}
class StrP(private val k: String, private val d: String) : ReadWriteProperty<Any?, String> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) = Prefs.sp.getString(k, d) ?: d
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) { Prefs.sp.edit().putString(k, value).apply() }
}
