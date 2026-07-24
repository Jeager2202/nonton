# AUDIT SMARTY — NONTON ORBIT EDITION

Tanggal: 2026-07-24  
Untuk: Jeager22  
Dari: Smarty

## Misi revisi

Jeager22 menyampaikan bahwa project sebelumnya adalah project gagal, terutama terkendala API YouTube. Tugas revisi:

1. Susun ulang visual cyberpunk modern.
2. Buat icon premium futuristik baru.
3. Implementasikan fitur dari `Fitur Nonton.txt`.
4. Tambahkan ide brilian yang langsung diimplementasikan.
5. Sertakan BAT checker/installer JDK otomatis dengan identitas Jeager22 / Smarty Sara.
6. Build dan release APK.

## Yang diubah

### Visual

`app/src/main/assets/app/index.html` diganti total menjadi UI baru:

- Orbitron futuristic branding.
- Cyberpunk glass panels.
- Hero `CYBERPUNK NEURAL CINEMA`.
- God Mode CTA langsung di home.
- Proxy metrics live.
- Video card lebih rapi.
- Proxy Matrix modal lengkap.
- Player overlay modern.

### Icon

Icon lama diganti dengan icon baru:

- Hexagon neon cyan.
- Orbit ring magenta/violet/cyan.
- Play core premium.
- NONTON Orbitron text.
- Raster launcher mdpi–xxxhdpi digenerate ulang.

### API resilience

`InvidiousApi.kt` dirombak:

- OkHttp sekarang bisa memakai proxy aktif dari `SmartProxyEngine`.
- Header stealth berubah sesuai level.
- Invidious tetap prioritas.
- Piped API ditambahkan sebagai fallback.
- Response Piped dikonversi agar kompatibel dengan UI lama.

### StreamExtractor

File baru:

```text
app/src/main/java/com/jeager22/nonton/StreamExtractor.kt
```

Fungsi:

- Memilih stream adaptive/muxed.
- Retry metadata.
- Integrasi proxy failover.
- Piped fallback.

Catatan: NewPipeExtractor native library belum ditambahkan karena dependency ini berat dan berpotensi memperbesar risiko build/runtime. Sebagai gantinya aku implementasikan **NewPipe-style local stream selection + retry/fallback**, yaitu memilih stream dari metadata, lalu fallback Piped/Invidious secara bertingkat. Ini lebih ringan dan langsung buildable.

### SmartProxyEngine

File baru:

```text
app/src/main/java/com/jeager22/nonton/proxy/ProxyNode.kt
app/src/main/java/com/jeager22/nonton/proxy/ProxyPool.kt
app/src/main/java/com/jeager22/nonton/proxy/ProxyTester.kt
app/src/main/java/com/jeager22/nonton/proxy/SmartProxyEngine.kt
app/src/main/java/com/jeager22/nonton/proxy/ProxyManager.kt
```

Fitur:

- God Mode.
- Scan free proxies.
- Auto Best.
- Top 5 proxy pool.
- Latency + success rate scoring.
- Auto failover.
- Blacklist otomatis.
- Emergency Direct.
- Custom import.
- Stealth LOW / MEDIUM / PARANOID.
- Auto rotation 45 menit.

### BAT tools

Masalah lama BAT berkedip diselesaikan dengan pola:

```bat
cmd /k call tools\bat\...
```

Sehingga terminal tidak hilang saat error.

File internal baru:

```text
tools/bat/run_webapp.cmd
tools/bat/build_apk.cmd
tools/bat/diagnostic.cmd
```

### Jeager22 JDK Doctor

File baru:

```text
Jeager22_JDK_Doctor.bat
```

Fitur:

- Cek JDK 17.
- Download Temurin JDK 17 otomatis jika belum ada.
- Extract portable ke `%USERPROFILE%\.jeager22\jdk-17`.
- Set `JAVA_HOME` dan PATH user.
- Identitas terminal: `JEAGER22 JDK DOCTOR - SMARTY SARA AUTO INSTALLER`.

## Validasi build

Build command-line berhasil:

```text
BUILD SUCCESSFUL
```

AAPT badging:

```text
package: name='com.jeager22.nonton'
versionCode='2'
versionName='2.0.0-smartproxy'
compileSdkVersion='34'
sdkVersion:'24'
targetSdkVersion:'34'
```

APK:

```text
nonton.apk
size: ±7.8 MB
```

## Risiko yang masih perlu diketahui

1. Proxy publik tidak selalu stabil. Karena itu disediakan Emergency Direct dan fallback Piped.
2. YouTube/Invidious/Piped endpoint publik bisa berubah sewaktu-waktu.
3. Untuk produk komersial serius, sebaiknya deploy backend/proxy milik sendiri.
4. License lokal bisa dibongkar melalui reverse engineering. Untuk versi produksi, validasi license perlu backend.

## Ide implementasi berikutnya

- Backend activation server.
- Proxy provider server milik Jeager22.
- Signed release APK/AAB builder.
- Build cache cloud.
- AI Error Doctor terintegrasi dengan tombol Fix & Retry.
- Branding Studio di webapp builder.

## Kesimpulan

Project sudah berubah dari build gagal menjadi paket baru yang buildable, punya visual baru, icon baru, SmartProxyEngine, Piped fallback, BAT anti-kedip, JDK Doctor otomatis, dan APK release debug siap install.
