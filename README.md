# Nonton — Aplikasi Android WebView (Hybrid Cyberpunk)

**Package:** `com.jeager22.nonton` · **Min SDK:** 24 (Android 7.0) · **Target/Compile SDK:** 34 · **Bahasa:** Kotlin · **Developer:** Jeager22

Aplikasi streaming video berbasis **arsitektur hybrid**: semua HTTP request ke Invidious API dilakukan dari sisi Kotlin native (OkHttp) untuk mem-bypass CORS, sementara WebView hanya merender UI lokal dari `assets/app/index.html`. Komunikasi dua arah via `@JavascriptInterface` (`AndroidBridge.sendCommand`) dan `window.receiveData(key, json)`.

---

## Daftar Isi
1. [Fitur Lengkap](#fitur-lengkap)
2. [Struktur Proyek](#struktur-proyek)
3. [Cara Build APK](#cara-build-apk)
4. [License Keys](#license-keys)
5. [Catatan Implementasi](#catatan-implementasi)
6. [Kepatuhan](#kepatuhan)

---

## Fitur Lengkap

### Player & Playback
- **ExoPlayer native** (Media3) untuk pemutaran utama — bukan HTML5 `<video>`.
- **Custom WebView controls**: play/pause, seek bar, waktu, kualitas, fullscreen, PiP — semua di-overlay di atas PlayerView native.
- **Double-tap seek**: tap kiri = mundur 10 detik, tap kanan = maju 10 detik.
- **Swipe vertikal**: sisi kiri = brightness, sisi kanan = volume.
- **Quality selector**: 1080p / 720p / 480p / 360p / 240p / 144p. Default 720p. Disimpan di SharedPreferences.
- **Picture-in-Picture**: otomatis masuk PiP saat user tekan Home/Back saat video bermain (API 26+).
- **Mini player**: tombol minimize mengecilkan player ke baris bawah 64dp dengan thumbnail + judul + play/pause + close.
- **Background audio**: `PlaybackService` (MediaSessionService) menjaga audio tetap bermain saat app di-minimize, dengan notification controls play/pause/stop.

### Konten & Navigasi
- **Trending**: grid 2 kolom, filter region (Indonesia/Global) dan type (Musik/Gaming/Film). Pull-to-refresh via tombol tab.
- **Trending Musik**: tab khusus dengan `type=music`.
- **Search**: search bar di header + native EditText (dua jalur sinkron). Search history lokal (max 20, bisa hapus satu/semua).
- **Related videos**: ditampilkan di bawah watch page dari `recommendedVideos` API response.
- **Favorites**: bookmark di SQLite (Room) — tombol ★ di setiap watch page. Halaman "Favorit" menampilkan semua.
- **Watch History**: otomatis menyimpan video yang ditonton (max 200 entri, auto-trim). Halaman "Riwayat" dengan tombol hapus per-item dan "Hapus Semua".

### Download
- **DownloadManager Android** untuk mengunduh video/audio.
- **Pilihan**: Video (1080p–360p) atau Audio only (128/64 kbps).
- **Lokasi**: `/storage/emulated/0/Download/Nonton/`.
- **Progress** di notification bar; toast "Download selesai" saat complete.

### Sistem Lisensi
- **EncryptedSharedPreferences** (AES-256-GCM via Android Keystore) — bukan plaintext.
- **Trial 30 hari** otomatis setelah install pertama.
- **Tiga jenis license key** (case-sensitive, exact match):
  - 6 bulan: `Jeager22 - 22021987 - 6 - BLN`
  - 12 bulan: `Jeager22 - 22-02-1987 - 12`
  - Lifetime: `Jeager22 - 2202 - 1987`
- **UI lisensi**: popup cyberpunk dengan status badge (TRIAL kuning / ACTIVE hijau / EXPIRED merah), info Installed/Expiry/Remaining/License/Identity.
- **License display**: SELALU ditampilkan sebagai `★ ★ ★ ★ ★` — key asli tidak pernah tampil di UI.
- **Expired blocking**: saat expired, hanya perintah `activate` dan `license` yang diproses.

### Desain Visual
- **Tema cyberpunk** dengan palette: `#050508` bg, `#0e0e18` surface, `#00f0ff` cyan, `#ff00aa` magenta, `#7b2fff` ungu.
- **Font**: Orbitron (header/brand), Rajdhani (body/UI), JetBrains Mono (info teknis) — di-load dari Google Fonts di HTML.
- **Animated neon edges**: garis gradient cyan→magenta→ungu yang berflow di top + bottom (CSS animation), plus static neon line di native header/footer.
- **Grid background** Tron-like opacity 6% + particle floating animation.
- **Glassmorphism** pada search bar header (backdrop-filter blur).
- **App icon**: hexagon vector + play triangle + neon stroke. Adaptive icon (Android 8+) dengan foreground/background terpisah. PNG raster untuk mdpi–xxxhdpi.

---

## Struktur Proyek

```
Nonton/
├── build.gradle.kts                     # root build
├── settings.gradle.kts
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
├── app/
│   ├── build.gradle.kts                 # app build + dependencies
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/app/
│       │   └── index.html               # UI cyberpunk lengkap (HTML+CSS+JS)
│       ├── java/com/jeager22/nonton/
│       │   ├── App.kt                   # Application class + notification channels
│       │   ├── MainActivity.kt          # Single activity, WebView, ExoPlayer, bridge
│       │   ├── InvidiousApi.kt          # OkHttp client + instance fallback
│       │   ├── LicenseManager.kt        # EncryptedSharedPreferences license
│       │   ├── data/
│       │   │   ├── dao/                 # FavoriteDao, HistoryDao, SearchHistoryDao
│       │   │   ├── entity/              # FavoriteEntity, HistoryEntity, SearchHistoryEntity
│       │   │   └── db/AppDatabase.kt    # Room database
│       │   ├── service/
│       │   │   ├── PlaybackService.kt   # MediaSessionService (background audio)
│       │   │   └── DownloadReceiver.kt  # BroadcastReceiver for download complete
│       │   └── util/
│       │       └── DownloadHelper.kt    # DownloadManager wrapper
│       └── res/
│           ├── layout/activity_main.xml # header + WebView + footer + PlayerView + mini player
│           ├── drawable/
│           │   ├── ic_launcher_foreground.xml
│           │   ├── ic_launcher_background.xml
│           │   ├── ic_stat_play.xml     # notification icon
│           │   ├── neon_line.xml        # gradient neon strip
│           │   └── search_bg.xml        # search bar bg
│           ├── mipmap-anydpi-v26/       # adaptive icon (v26+)
│           ├── mipmap-mdpi..xxxhdpi/    # PNG raster icons
│           ├── values/
│           │   ├── strings.xml
│           │   ├── colors.xml
│           │   └── styles.xml
│           └── xml/network_security_config.xml
└── README.md
```

---

## Cara Build APK

### Prasyarat
- **Android Studio** Hedgehog (2023.1.1) atau yang lebih baru
- **JDK 17** (ter-install bersama Android Studio)
- **Android SDK Platform 34** + **Build-Tools 34.0.0**
- Internet untuk download Gradle dependencies

### Langkah Build
1. **Buka** folder `Nonton/` sebagai project Gradle di Android Studio (File → Open → pilih folder `Nonton`).
2. Tunggu **Gradle Sync** selesai. Android Studio akan otomatis mendownload:
   - Gradle 8.9
   - Kotlin 1.9.24
   - Android Gradle Plugin 8.5.2
   - Semua dependensi (Media3, OkHttp, Room, Security-crypto, dll.)
3. Pilih menu **Build → Build APK(s)** atau **Build → Generate Signed Bundle / APK**.
4. APK debug akan muncul di: `app/build/outputs/apk/debug/app-debug.apk`
5. **Rename** file menjadi `Nonton.apk`.

### Build Signed (Release)
1. **Build → Generate Signed Bundle / APK → APK**.
2. Buat keystore baru (atau gunakan yang existing):
   - Key alias: bebas
   - Password: minimal 6 karakter
   - Validity: 25+ tahun
3. Pilih variant **release**, finish.
4. APK signed akan ada di `app/release/app-release.apk` — rename ke `Nonton.apk`.

### Build via Command Line
```bash
cd Nonton
./gradlew assembleDebug
# hasil: app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease
# hasil: app/build/outputs/apk/release/app-release.apk (butuh signing config)
```

---

## License Keys

| Tipe        | Key                              | Masa Aktif           |
|-------------|----------------------------------|----------------------|
| 6 bulan     | `Jeager22 - 22021987 - 6 - BLN`  | 180 hari dari install |
| 12 bulan    | `Jeager22 - 22-02-1987 - 12`     | 365 hari dari install |
| Lifetime    | `Jeager22 - 2202 - 1987`         | Tanpa batas           |

Ketik persis seperti di atas (case-sensitive, termasuk spasi dan tanda strip) di field "License Key" pada popup lisensi.

---

## Catatan Implementasi

1. **Invidious instances**: aplikasi mencoba 8 instance publik secara berurutan (`inv.nadeko.net`, `yewtu.be`, `invidious.nerdvpn.de`, dll.). Instance pertama yang sukses akan di-cache sebagai preferensi. Instance publik tidak stabil — untuk produksi, gunakan instance yang Anda kelola sendiri.

2. **Stream URL**: implementasi saat ini memilih `adaptiveFormats` (DASH) pertama yang cocok dengan quality yang dipilih, atau fallback ke `formatStreams` (muxed MP4). Untuk DASH true-adaptive dengan audio+video terpisah, diperlukan integrasi `MediaSource` & `MergingMediaSource` ExoPlayer yang lebih dalam — implementasi ini sudah siap foundation-nya.

3. **Keystore encrypted prefs**: `androidx.security.crypto.EncryptedSharedPreferences` menggunakan Android Keystore master key AES-256-GCM. Pada perangkat lama yang keystore-nya bermasalah, fallback ke `SharedPreferences` biasa (lalu di-log).

4. **PiP mode**: aktif di Android 8.0+ (API 26+). Pada perangkat lebih lama, tombol PiP tidak melakukan apa-apa.

5. **Background audio**: `PlaybackService` extends `MediaSessionService` dari Media3. Notification controls otomatis dibuat oleh sistem. Pada Android 13+, runtime permission `POST_NOTIFICATIONS` perlu di-grant oleh user.

6. **Download folder**: `/storage/emulated/0/Download/Nonton/`. Pada Android 10+ (scoped storage), `DownloadManager` masih bisa menulis ke folder public Downloads tanpa `WRITE_EXTERNAL_STORAGE` (gunakan `MediaStore` untuk kontrol lebih penuh di update mendatang).

7. **Gestur player**: double-tap seek (10s) dan swipe brightness/volume diimplementasikan via `GestureDetector` + raw touch events pada `PlayerView`.

---

## Kepatuhan & Operasional

- Gunakan hanya konten yang Anda berhak akses/unduh. Patuhi syarat layanan sumber konten serta hukum yang berlaku di yurisdiksi Anda.
- Endpoint publik Invidious bersifat **tidak stabil** dan bisa berubah/rate-limited. Untuk produksi, deploy instance Invidious sendiri dengan kebijakan privasi & rate limit yang sesuai.
- **License validation** dilakukan lokal di APK. Karena APK mudah di-reverse-engineer, ini **bukan DRM kuat**. Untuk produk nyata, lakukan aktivasi/validasi di server milik sendiri dan jangan taruh rahasia server di APK.

---

## Versioning

- **v1.0.0** — Initial release dengan semua fitur wajib dari spesifikasi prompt.

## Identity

- Developer: **Jeager22**
- Identity string (fix): `Jeager22`
