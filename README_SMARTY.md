# NONTON — SMARTY ORBIT EDITION

Halo Jeager22, ini paket baru yang sudah disusun ulang dari project gagal menjadi **NONTON Orbit Edition**: visual cyberpunk modern, icon premium baru, SmartProxyEngine, dan APK release debug siap install.

## Output penting

- APK siap install: `nonton.apk`
- Source lengkap + builder: `Nonton_fixed.zip`
- Versi app: `2.0.0-smartproxy`
- Package: `com.jeager22.nonton`

## Visual baru

UI `assets/app/index.html` sudah didesain ulang:

- Cyberpunk glassmorphism.
- Font Orbitron, Rajdhani, JetBrains Mono.
- Hero panel modern: **CYBERPUNK NEURAL CINEMA**.
- Metrics: Proxy Matrix, Stealth Level, Top Pool.
- God Mode button dengan gradient pelangi dan glow.
- Grid video lebih rapi dan elegan.
- Modal Proxy Matrix futuristik.
- Player overlay modern.

## Icon baru

Icon app premium sudah dibuat ulang:

- Hexagon neon.
- Orbit ring futuristik.
- Play core glowing.
- NONTON Orbitron text.
- PNG launcher mdpi sampai xxxhdpi sudah digenerate ulang.

File icon master:

```text
app-icon-premium.png
docs/app-icon-premium.png
```

## Fitur dari `Fitur Nonton.txt` yang diimplementasikan

### 1. SmartProxyEngine + ProxyPool

File baru:

```text
app/src/main/java/com/jeager22/nonton/proxy/SmartProxyEngine.kt
app/src/main/java/com/jeager22/nonton/proxy/ProxyPool.kt
app/src/main/java/com/jeager22/nonton/proxy/ProxyTester.kt
app/src/main/java/com/jeager22/nonton/proxy/ProxyNode.kt
app/src/main/java/com/jeager22/nonton/proxy/ProxyManager.kt
```

Fitur:

- God Mode.
- Intelligent top-5 proxy pool.
- Auto Best proxy.
- Auto failover.
- Proxy scoring: success rate + latency + freshness.
- Blacklist proxy mati.
- Emergency Direct.
- Clear Blacklist.
- Custom import proxy list.
- Auto rotation 45 menit.
- Stealth level: LOW, MEDIUM, PARANOID.

### 2. Proxy Matrix Dialog

Di UI baru, tekan tombol **⚡** atau **ACTIVATE GOD MODE**.

Fitur dialog:

- Judul `PROXY MATRIX` dengan glow Orbitron.
- Subtitle `GOD TIER NEURAL BYPASS v2.1`.
- Tombol God Mode gradient.
- Auto Best.
- Scan Free Proxies.
- Stealth Level.
- Auto Rotation switch.
- Custom Import.
- Emergency Direct.
- Clear Blacklist.
- Realtime log.
- Top proxy list dengan latency dan score.

### 3. Resilience / failover

File baru:

```text
app/src/main/java/com/jeager22/nonton/StreamExtractor.kt
```

`InvidiousApi.kt` juga sudah dirombak:

- Invidious tetap dipakai.
- Piped API ditambahkan sebagai fallback.
- Header stealth berubah sesuai level.
- Proxy aktif dipakai untuk request native OkHttp.
- Jika request gagal, SmartProxyEngine mencatat failure dan melakukan failover.

Alur baru:

```text
User klik video
  -> ambil metadata via Invidious
  -> StreamExtractor memilih adaptive/muxed stream
  -> jika gagal: retry metadata + proxy failover
  -> jika tetap gagal: Piped fallback
  -> jika proxy kacau: Emergency Direct tersedia
```

## Tools BAT anti-kedip

Semua `.bat` root sekarang memakai `cmd /k`, jadi terminal tidak langsung hilang jika error.

File utama:

```text
RUN_NOW.bat
RUN_SUPER_RELIABLE.bat
RUN_DIAGNOSTIC.bat
build_apk_now.bat
```

Script internal:

```text
tools/bat/run_webapp.cmd
tools/bat/build_apk.cmd
tools/bat/diagnostic.cmd
```

## Jeager22 JDK Doctor

Aku menambahkan tools kreatif khusus:

```text
Jeager22_JDK_Doctor.bat
```

Identitas alias:

```text
JEAGER22 JDK DOCTOR - SMARTY SARA AUTO INSTALLER
```

Fungsi:

- Cek apakah JDK 17 sudah ada.
- Jika belum, download Temurin JDK 17 otomatis.
- Extract portable ke:

```text
%USERPROFILE%\.jeager22\jdk-17
```

- Set `JAVA_HOME` user environment.
- Tambahkan JDK ke PATH user.
- Tidak harus install Android Studio.

## Cara pakai

### 1. Jika belum punya JDK 17

Jalankan:

```bat
Jeager22_JDK_Doctor.bat
```

Tutup dan buka ulang CMD/Explorer jika PATH belum terbaca.

### 2. Jalankan webapp builder

```bat
RUN_NOW.bat
```

### 3. Build APK langsung

```bat
build_apk_now.bat
```

## Validasi build Smarty

Build berhasil tanpa Android Studio:

```text
BUILD SUCCESSFUL
package: com.jeager22.nonton
versionCode: 2
versionName: 2.0.0-smartproxy
APK size: ±7.8 MB
```

## Ide brilian berikutnya

1. Proxy telemetry dashboard harian.
2. Server activation API agar license tidak hanya lokal.
3. Release signing center: generate keystore + signed APK/AAB.
4. Cloud Builder mode: build di GitHub/VPS tanpa membebani laptop user.
5. AI Error Doctor: membaca log Gradle dan otomatis memberikan tombol Fix & Retry.
6. Branding Studio: ubah nama app, package id, icon, warna, dan splash screen dari webapp.

Love u too, Jeager22. — Smarty
