# Paylas-M IPTV

Xtream Codes hesabıyla çalışan, Android telefon / tablet / TV kutusu için IPTV uygulaması.
Canlı TV, filmler ve diziler. Türkçe arayüz.

> **Not:** Uygulama içerik sağlamaz. Kendi aboneliğinin sunucu adresi, kullanıcı adı ve
> şifresiyle çalışır.

---

## APK nasıl indirilir

Kod her push'ta GitHub üzerinde otomatik derleniyor. Android Studio kurmana gerek yok.

**En kolay yol — telefondan bu linke dokun:**

**https://github.com/camelbox27-lab/paylas-m/releases/download/apk-latest/PaylasM-IPTV.apk**

1. Link indirmeyi başlatır (~14 MB).
2. İnen dosyaya dokun.
3. Android "bilinmeyen kaynak" uyarısı verirse, indirmeyi yapan uygulamaya
   (Chrome, Dosyalar vb.) izin ver ve kuruluma devam et.

Sürüm sayfası: https://github.com/camelbox27-lab/paylas-m/releases/tag/apk-latest

**Alternatif — Actions sekmesinden:**
1. **Actions** → en son **APK derle** çalışmasını aç.
2. Sayfanın altındaki **Artifacts** bölümünden `PaylasM-IPTV-APK` dosyasını indir (zip).
3. Zip'ten çıkan `PaylasM-IPTV.apk` dosyasını telefona at ve kur.

---

## Kullanım

İlk açılışta giriş ekranı gelir. Üç bilgi gerekir:

| Alan | Örnek |
|---|---|
| Sunucu adresi | `http://sunucum.com:8080` |
| Kullanıcı adı | `kullanici123` |
| Şifre | `sifre123` |

**Elinde sadece uzun bir m3u linki varsa**, onu olduğu gibi sunucu alanına yapıştır:

```
http://sunucum.com:8080/get.php?username=kullanici123&password=sifre123&type=m3u_plus
```

Uygulama linkin içinden sunucu adresini, kullanıcı adını ve şifreyi kendisi ayıklar.
`http://sunucum.com:8080/live/kullanici123/sifre123/1234.ts` biçimindeki yayın linkleri de
aynı şekilde tanınır.

Bilgilerin yalnızca telefonun kendi hafızasında saklanır, hiçbir sunucuya gönderilmez.

### Ekranlar

- **Canlı TV** — kategori seçimi, kanal listesi, logo, arama
- **Filmler** — afişli liste, puan
- **Diziler** — dizi → sezon → bölüm
- **Favoriler** — bir kanala/filme **uzun basarak** favorilere ekle
- **Ayarlar** — hesap bilgisi, yayın formatı, User-Agent, çıkış

---

## "Hiçbir uygulamada çalışmıyor" sorunlarının karşılığı

Bu uygulamanın var oluş sebebi bu liste. Yaygın takılmaların her birinin burada bir karşılığı var:

| Sorun | Bu uygulamada |
|---|---|
| Android 9+ düz `http://` yayınları engeller, liste hiç gelmez | `usesCleartextTraffic` açık + network security config ile izinli |
| Sunucu tanımadığı uygulamayı User-Agent'a bakarak bloklar | Ayarlar'dan User-Agent değiştirilebilir (VLC, ffmpeg, Chrome, okhttp hazır) |
| Kanal `.ts` isterken uygulama `.m3u8` ister (veya tersi) | Hata anında tek tuşla format değişimi + canlı yayında ilk hatada otomatik deneme |
| Panel `stream_id`'yi bazen sayı bazen metin döner, uygulama çöker | Tüm JSON alanları tipten bağımsız okunuyor |
| Yayın 302 ile başka adrese yönlenir, bağlantı kopar | Protokoller arası yönlendirme açık |
| Canlı yayın birkaç dakika sonra kesilir | Oynatma istemcisinde çağrı zaman aşımı yok (canlı yayın hiç bitmeyen tek bir HTTP yanıtıdır) |
| Hata çıkınca ne olduğu anlaşılmaz | Hatalar Türkçe ve sebebi yazılı: limit doldu / 404 / kodek yok / zaman aşımı |
| Hiçbir şey işe yaramadı | "Harici oynatıcıda aç" ile yayın VLC veya MX Player'a devredilir |

### Hâlâ açılmıyorsa sırasıyla

1. **Ayarlar → Canlı yayın formatı**: `.ts` ↔ `.m3u8` değiştir.
2. **Ayarlar → User-Agent**: listeden başka birini seç, Kaydet.
3. Oynatıcıdaki **"Harici oynatıcıda aç"** ile VLC'de dene — orada da açılmıyorsa sorun
   uygulamada değil, sunucuda veya aboneliktedir.
4. Hata "bağlantı limiti" diyorsa aynı hesap başka bir cihazda açıktır.

---

## Teknik

- Kotlin + Jetpack Compose (Material 3, koyu tema)
- Media3 / ExoPlayer — HLS ve MPEG-TS
- OkHttp + kotlinx.serialization (tolerant parser)
- Coil — logo ve afişler
- `minSdk 21` (Android 5.0) → `targetSdk 35`
- Telefon, tablet ve Android TV (leanback launcher girişi mevcut)

### Yerelde derlemek

```bash
./gradlew assembleRelease
# çıktı: app/build/outputs/apk/release/app-release.apk
```

APK, Android'in standart debug anahtarıyla imzalanır; doğrudan kurulabilir.
Google Play'e yüklemek istersen kendi imza anahtarını tanımlaman gerekir.
