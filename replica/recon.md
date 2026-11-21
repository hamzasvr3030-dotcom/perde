# Recon Map: MX Player (Android) → kendi video oynatıcımız

> Tarih: 2026-11-21 · Platform: Android · Kapsam: **yerel video oynatma** (temel döngü)
> Kural: Uygulamanın sadece işlevleri ve kullanım kalıpları incelendi. Adı, logosu, görselleri, metinleri ve kodu kullanılmayacak.
> Uygulamanın kodu açılmadı, ağ trafiği izlenmedi. Bilgiler yalnızca herkese açık kaynaklardan toplandı.

## 1. Kapsam

| Soru | Cevap |
|---|---|
| Uygulama / platform | MX Player, Android |
| Dilim | Cihazdaki videoları bulmak, listelemek ve gelişmiş oynatıcıyla izlemek (hareketler, altyazı, ses parçası, hız, kilit). |
| Kimin için | Kullanıcının kendi telefonu. Kişisel kullanım, Play Store'a yüklemek şart değil. |
| Kapsam dışı | Online içerik kataloğu (MX'in diziler/filmler bölümü), reklamlar, oyunlar, sosyal akış. |

## 2. Kaynaklar

| Kaynak | Verdiği bilgi |
|---|---|
| [MX Player resmi özellik sayfası (eski Google Sites)](https://www.sites.google.com/site/mxvpen/features) | Çözücü modları (HW/HW+/SW), altyazı hareketleri, çocuk kilidi |
| [TechSpot indirme sayfası](https://www.techspot.com/downloads/6678-mx-player-for-android.html) | Özellik özeti |
| [Softpedia incelemesi](https://mobile.softpedia.com/android/MX-Player-Android-Review-2770.html) | Format listesi, hareketler |
| [MakeUseOf incelemesi](https://www.makeuseof.com/tag/mx-player-doitall-movie-player-android-android-21/) | Kilit modları, iki parmakla hız ayarı |
| [Guiding Tech ipuçları](https://www.guidingtech.com/mx-player-tips-tricks) | Ağ akışı (URL), A-B tekrar, kısayollar |
| [Guiding Tech MX ve XPlayer karşılaştırması](https://www.guidingtech.com/mx-player-vs-xplayer-comparison/) | Uyku zamanlayıcısı, SMB/IP ile sunucu |
| [APKMirror 1.10.57 sürüm notları](https://www.apkmirror.com/?p=690642) | Ekolayzer eklendi |
| [APKMirror 1.25.3](https://www.apkmirror.com/?p=1290170) / [1.25.7](https://www.apkmirror.com/?p=1326934) sürüm notları | SMB desteği, hızlı kısayollara A/B tekrar ve zamanlayıcı |
| [APKMirror 1.49.5 sürüm notları](https://www.apkmirror.com/?p=3875511) | Dosya aktarımı, Chromecast ile harici SRT |
| [91mobiles incelemesi](https://hub.91mobiles.com/app-review-mx-player-for-android/) | Genel akış |

Not: Kaynakların çoğu eski sürümlere ait, menü adları değişmiş olabilir. İşlevler ise yıllardır aynı.

## 3. Ekran envanteri

| ID | Ekran | Nasıl gidilir | Amaç | Ana bileşenler | Durumlar |
|---|---|---|---|---|---|
| S01 | İzin isteği | İlk açılış | Videolara erişim izni | Açıklama, izin butonu | izin yok, reddedildi (ayarlara yönlendir) |
| S02 | Klasör listesi | Ana ekran | Videoların bulunduğu klasörler | Üst çubuk, klasör satırı (ad, video sayısı), sıralama, arama | boş, taranıyor, dolu |
| S03 | Video listesi | S02 → klasör | Klasördeki videolar | Küçük resim, ad, süre, boyut, çözünürlük, "yeni" etiketi, izleme ilerleme çubuğu | boş, dolu, seçim modu |
| S04 | Liste/ızgara görünüm ve sıralama menüsü | S02/S03 → menü | Ad/tarih/boyut/süreye göre sırala, liste veya ızgara | Alt sayfa (bottom sheet), seçenekler | — |
| S05 | Arama | Üst çubuk → büyüteç | Video adında ara | Arama alanı, sonuç listesi | sonuç yok |
| S06 | Video uzun basma menüsü | S03 → uzun bas | Yeniden adlandır, sil, paylaş, özellikler, oynatma listesine ekle | Alt sayfa | onay penceresi |
| S07 | Video özellikleri | S06 → Özellikler | Dosya yolu, boyut, çözünürlük, codec, kare hızı | Bilgi listesi | — |
| S08 | **Oynatıcı** | S03 → video | İzleme | Video yüzeyi, üst çubuk (ad, menü), alt çubuk (zaman çubuğu, oynat/durdur, önceki/sonraki, kilit, en-boy oranı, döndürme), hareket katmanı | yükleniyor, oynuyor, duraklatıldı, desteklenmeyen format, hata |
| S09 | Hareket göstergeleri | S08 içinde | Parlaklık, ses, sarma ve yakınlaştırma göstergeleri | Ortada açılan gösterge | — |
| S10 | Ekran kilidi | S08 → kilit | Yanlışlıkla dokunmayı engellemek | Kilit simgesi, çocuk kilidi modu | kilitli, çocuk kilidi |
| S11 | Altyazı menüsü | S08 → altyazı | Altyazı seçme, harici dosya açma, kaydırma (senkron), stil | Liste, ±ms ayarı, yazı tipi/boyut/renk | altyazı yok |
| S12 | Ses parçası menüsü | S08 → ses | Ses dilini seçme, ses senkronu | Liste, ±ms ayarı | tek parça |
| S13 | Hız menüsü | S08 → hız | 0.25×–4× | Kaydırıcı, hazır değerler | — |
| S14 | Oynatıcı ayar menüsü | S08 → ⋮ | A-B tekrar, uyku zamanlayıcısı, ekolayzer, ekran yönü, çözücü (HW/SW), arka planda oynatma | Alt sayfa | — |
| S15 | Ekolayzer | S14 → Ekolayzer | Bantlar, hazır ayarlar, bas/sanal surround | Dikey kaydırıcılar | cihaz desteklemiyor |
| S16 | Resim içinde resim (PiP) | Oynatıcıdan ana ekrana çıkınca | Küçük pencerede izlemeye devam | Sistem PiP penceresi | — |
| S17 | Arka planda oynatma bildirimi | Ses modu | Ekran kapalıyken dinleme | Medya bildirimi | — |
| S18 | Ağ akışı | Menü → Ağ akışı | URL ile video açma | URL alanı, geçmiş | geçersiz URL |
| S19 | Ayarlar | Menü → Ayarlar | Liste, oynatıcı, altyazı, çözücü, dokunma kontrolleri | Kategorili ayar listesi | — |
| S20 | Son izlenenler / devam et | S02 üst kısım veya menü | Kalınan yerden devam | Son video kartı | yok |
| S21 | Gizli klasörler (özel klasör) | Ayarlar → özel klasör | Videoları gizlemek | PIN girişi, liste | PIN yok |

## 4. Kullanıcı akışları

```
F01 Video izle (temel döngü)
    S01 izin → S02 klasör → S03 video → S08 oynat
    Mutlu yol: 3 dokunuş (izin verildikten sonra 2)
    uç durum: desteklenmeyen codec, bozuk dosya, SD kart çıkarıldı

F02 Kaldığı yerden devam et
    S02 → S20 "devam et" kartı → S08 (son konumdan)
    uç durum: dosya silinmiş

F03 Hareketlerle kontrol
    S08: sol yarıda dikey kaydırma = parlaklık, sağ yarıda dikey = ses,
         yatay = ileri/geri sarma, iki parmakla sıkıştırma = yakınlaştırma,
         çift dokunma = duraklat (veya kenarlarda ±10 sn), uzun basma = 2× hız
    uç durum: kilitliyken hareketler kapalı

F04 Altyazı ekle ve senkronla
    S08 → S11 → harici .srt seç → kaydırma ±0.1 sn → stil
    uç durum: yanlış karakter kodlaması (Türkçe ş, ğ, ı → Windows-1254)

F05 Ses dilini değiştir
    S08 → S12 → parça seç

F06 Ekranı kilitle / çocuk kilidi
    S08 → kilit → S10 → (aç: kilit simgesine bas veya çocuk kilidinde özel hareket)

F07 PiP / arka planda dinle
    S08 → ana ekran tuşu → S16  |  S14 → arka planda oynat → S17

F08 Dosya yönetimi
    S03 → uzun bas → S06 → yeniden adlandır / sil / paylaş / özellikler (S07)

F09 Ağdan video aç
    Menü → S18 → URL yapıştır → S08

F10 A-B tekrarı ve uyku zamanlayıcısı
    S08 → S14 → A noktası → B noktası → döngü  |  zamanlayıcı 15/30/60 dk
```

## 5. Bileşenler

| Bileşen | Varyantlar | Durumlar | Kullanıldığı ekranlar |
|---|---|---|---|
| Üst uygulama çubuğu | başlık, arama, seçim modu | — | S02, S03, S05 |
| Klasör satırı | liste / ızgara | normal, basılı | S02 |
| Video satırı / kartı | liste / ızgara, ilerleme çubuklu | yeni, izlendi, seçili | S03, S05, S20 |
| Alt sayfa menüsü (bottom sheet) | eylemler, sıralama, ayarlar | — | S04, S06, S14 |
| Oynatıcı kontrol katmanı | dikey / yatay | görünür, gizli (otomatik gizlenme) | S08 |
| Zaman çubuğu (seekbar) | önizlemeli | sürükleniyor | S08 |
| Hareket göstergesi | parlaklık, ses, sarma (+10 sn), yakınlaştırma (%) | — | S09 |
| Seçim listesi (radyo) | altyazı, ses parçası | — | S11, S12 |
| Kaydırıcı (slider) | hız, senkron, ekolayzer bandı | — | S13, S15 |
| Kilit katmanı | normal, çocuk | — | S10 |
| Onay penceresi | sil, yeniden adlandır | — | S06 |
| Bildirim (toast/snackbar) | bilgi, hata | — | hepsi |

## 6. Çıkarılan veri modeli

```
Video      id (MediaStore), uri, title, folder_path, duration_ms, size_bytes,
           width, height, date_added, mime
           kaynak: S03 satır bilgileri, S07 özellikler   güven: yüksek (MediaStore'dan gelir)

Folder     path, name, video_count, total_size
           kaynak: S02   güven: yüksek (videolardan türetilir)

PlaybackState  video_uri, position_ms, last_played_at, audio_track, subtitle_track,
               subtitle_uri (harici), subtitle_offset_ms, speed, aspect_mode, zoom
               kaynak: F02 devam etme, video başına hatırlanan ayarlar   güven: orta (tahmin)

Settings   gesture_flags, double_tap_mode, seek_step_s, default_orientation,
           decoder (hw/sw), subtitle_style{font,size,color,bg}, list_view(list|grid),
           sort_by, show_hidden, private_pin_hash
           kaynak: S19   güven: orta

NetworkHistory  url, title, last_opened_at   kaynak: S18   güven: orta (tahmin)
Playlist (ops.) id, name, items[]           kaynak: S06 "listeye ekle"   güven: düşük
```

## 7. Özellik matrisi

`features.csv` dosyasında.

## 8. Kopyalanamayanlar / kapsam dışı

- **Online içerik kataloğu:** MX'in dizi ve film bölümü lisanslı içerik, kapsam dışı.
- **Reklam ve hesap sistemi:** Gerek yok.
- **Kendi codec paketleri (MX'in özel FFmpeg yapısı ve AC3/DTS lisans durumu):** Bunların yerine açık kaynak Media3 (ExoPlayer) ve isteğe bağlı FFmpeg eklentisi kullanılabilir. AC3/DTS sesler cihaza göre çalışmayabilir.
- **Adı, logosu, simgeleri ve renk kimliği:** Kullanılmayacak, kendi markamızı yapacağız.

## 9. Boyut

- **Ekran:** 21 · **Akış:** 10 · **Varlık:** 5
- **Zor kısımlar:**
  1. **Hareket sistemi:** Parlaklık, ses, sarma, yakınlaştırma ve iki parmak hareketlerinin birbirine karışmadan çalışması.
  2. **Altyazı:** Harici SRT/ASS dosyası, Türkçe karakter kodlaması (UTF-8 ve Windows-1254) ve senkron kaydırma.
  3. **Codec kapsamı:** MKV/AVI ve eski formatlar. Media3 modern formatların çoğunu oynatır, geri kalanı için FFmpeg eklentisi gerekir.
- **Ek:** PiP, arka planda oynatma (MediaSession bildirimi), video başına konum hatırlama.
- **Boyut tahmini: M.** Temel oynatıcı ve dosya listesi birkaç günlük iş, tüm "should" özellikleriyle birkaç hafta.
- **Teknik öneri:** Yerel Android uygulaması (Kotlin + Jetpack Compose + Media3/ExoPlayer). Bu bilgisayarda JDK 17 ve Android SDK hazır. APK, GitHub Releases bağlantısıyla telefona yüklenir.
