# Point Maker 🏸

Aplikasi Android pencatat **live skor badminton** yang mengumumkan skor lewat suara (TTS Bahasa
Indonesia) setiap kali skor berubah — misalnya *"tujuh belas, lima belas"*. Dirancang untuk dipakai
satu tangan di pinggir lapangan, tanpa login dan tanpa setup.

Tersedia dua target:

| Modul | Target | Package | APK |
|---|---|---|---|
| `:app` | Ponsel / tablet (Android 7.0+) | `com.pointmaker.app` | `app-debug.apk` |
| `:wear` | Jam Wear OS 3+ (standalone) | `com.pointmaker.app.wear` | `wear-debug.apk` |

---

## 1. Fitur

- Aturan skoring BWF: rally point 21, deuce 20-20 (selisih 2), batas 30, best of 3.
- Ucapan skor otomatis dalam Bahasa Indonesia, angka sebagai kata, angka sisi pencetak poin terakhir lebih dulu.
- Tambah poin kiri/kanan dengan satu ketukan.
- Undo satu langkah, reset dengan dialog konfirmasi.
- Akhir set & akhir pertandingan otomatis, plus ucapan pemenang.
- Persistensi state — skor aman saat layar mati atau aplikasi di-kill.
- Versi jam: haptik konfirmasi tiap ketukan, bunyi cue sebelum TTS, layout adaptif layar bulat.

---

## 2. Prasyarat

| Kebutuhan | Versi | Catatan |
|---|---|---|
| JDK | **17** | Toolchain proyek memakai Java 17 |
| Android SDK | Platform **37** + Build-Tools **36.0.0** | `compileSdk = 37` |
| Android Studio | Ladybug atau lebih baru | Opsional, tapi paling mudah untuk mengelola SDK |
| ADB | platform-tools terbaru | Untuk install ke perangkat |
| Gradle | — | Memakai Gradle Wrapper (`gradlew`), tidak perlu install Gradle |

Toolchain terverifikasi: **Gradle 8.11.1 · AGP 8.7.3 · Kotlin 2.1.20 · JDK 17 · compileSdk 37**.

### Menyiapkan `local.properties`

File ini **tidak** ikut ke repo (berisi path lokal). Buat di root proyek:

**Windows**
```properties
sdk.dir=C\:\\Users\\<NAMA_ANDA>\\AppData\\Local\\Android\\Sdk
```

**macOS / Linux**
```properties
sdk.dir=/Users/<NAMA_ANDA>/Library/Android/sdk
```

> Alternatif: set environment variable `ANDROID_HOME` / `ANDROID_SDK_ROOT`, atau buka proyek di
> Android Studio yang akan membuat file ini otomatis.

---

## 3. Build

Semua perintah dijalankan dari **root proyek**.

### Windows (PowerShell)
```powershell
.\gradlew.bat build
```

### macOS / Linux
```bash
./gradlew build
```

### Perintah berguna

| Perintah | Kegunaan |
|---|---|
| `./gradlew build` | Build semua modul + jalankan test |
| `./gradlew test` | Hanya unit test (JVM) |
| `./gradlew :app:assembleDebug` | APK debug ponsel |
| `./gradlew :wear:assembleDebug` | APK debug jam |
| `./gradlew clean` | Bersihkan output |
| `./gradlew :shared:test` | Test logika skoring & format ucapan |
| `./gradlew lint` | Android lint |

### Hasil build (APK)

```
app/build/outputs/apk/debug/app-debug.apk
wear/build/outputs/apk/debug/wear-debug.apk
```

Versi release (`app-release-unsigned.apk`, `wear-release-unsigned.apk`) **belum ditandatangani**.
Untuk memasangnya, buat keystore dan konfigurasi `signingConfigs` di `build.gradle.kts`, atau
pakai `apksigner` secara manual. Untuk pemakaian pribadi, APK debug sudah cukup.

---

## 4. Install ke ponsel

### Cara A — lewat ADB (USB)

1. Aktifkan **Opsi Pengembang** di ponsel (tap *Build number* 7× di *About phone*).
2. Aktifkan **USB debugging**, sambungkan kabel, lalu setujui prompt di ponsel.
3. Cek koneksi:
   ```bash
   adb devices
   ```
4. Install:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
5. Buka aplikasi **Point Maker** di launcher.

### Cara B — lewat ADB (Wi-Fi / wireless debugging)

```bash
# Di ponsel: Settings → Developer options → Wireless debugging → Pair device with pairing code
adb pair 192.168.1.50:41234        # masukkan kode pairing yang tampil
adb connect 192.168.1.50:5555      # gunakan IP:port dari layar Wireless debugging
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Cara C — copy APK langsung

Copy `app-debug.apk` ke ponsel (kabel, Google Drive, dsb), lalu buka file tersebut dan izinkan
**Install unknown apps** untuk aplikasi pengelola file. Cara ini tidak butuh ADB.

---

## 5. Install ke jam Wear OS

Aplikasi jam bersifat **standalone** — skor, suara, dan haptik berjalan tanpa ponsel. Tidak perlu
companion app atau pairing.

### Cara A — wireless debugging (direkomendasikan, jam modern)

Jam Wear OS 3+ mendukung *Wireless debugging* langsung, jadi tidak perlu ponsel perantara.

1. Di jam: **Settings → System → About → Versions**, tap *Build number* 7× untuk membuka
   **Developer options**.
2. Di jam: **Developer options → Wireless debugging → ON**.
3. Buka **Pair new device** di jam, catat `IP:PORT` dan kode 6 digit.
4. Di komputer:
   ```bash
   adb pair <IP_JAM>:<PORT_PAIRING>
   # masukkan kode 6 digit saat diminta
   adb connect <IP_JAM>:<PORT_DEBUG>
   adb devices
   ```
5. Install:
   ```bash
   adb install -r wear/build/outputs/apk/debug/wear-debug.apk
   ```
6. Aplikasi muncul di daftar aplikasi jam.

> Jam dan komputer harus berada di **jaringan Wi-Fi yang sama**.
> Jika `adb devices` menampilkan `unauthorized`, setujui prompt **Allow debugging?** di jam.

### Cara B — lewat ADB bridge ponsel

Jika jam sudah terhubung ke ponsel via Bluetooth dan ponsel dalam mode wireless debugging:

```bash
adb connect <IP_PONSEL>:5555
adb devices          # jam akan tampil sebagai perangkat terpisah
adb -s <SERIAL_JAM> install -r wear/build/outputs/apk/debug/wear-debug.apk
```

### Cara C — Android Studio

1. Buka proyek, pilih run configuration **wear**.
2. Pilih perangkat jam dari dropdown target.
3. Klik **Run**.

### Cara D — Play Store internal testing

Untuk distribusi ke tester: upload AAB release yang sudah ditandatangani ke Play Console →
*Internal testing* → install lewat Play Store di jam. Butuh signing config produksi.

---

## 6. Pemakaian singkat

### Ponsel
- **Tombol kiri / kanan** — tambah 1 poin untuk sisi tersebut. Setiap perubahan skor diucapkan.
- **Undo** — kembalikan satu langkah.
- **Reset** — mulai pertandingan baru (butuh konfirmasi).

### Jam
- **Separuh kiri / kanan layar** — tambah 1 poin. Ada gutter tengah mati ±20dp agar tidak salah sisi.
- **Undo** — tombol di busur bawah, terpisah dari zona poin.
- **Toggle suara** — satu-satunya pengaturan.
- Getaran konfirmasi setiap ketukan, jadi skor bisa dicatat tanpa melihat layar.

---

## 7. Struktur proyek

```
:shared   Kotlin JVM library murni — SATU sumber kebenaran aturan skoring & format ucapan
          com.pointmaker.app.domain.* (MatchRules, MatchState, Side, ScoreFormatter,
          AnnouncementText, Announcement) + audio.ScoreCueSequencer
:app      Aplikasi ponsel (Jetpack Compose + Material 3) — depend ke :shared
:wear     Aplikasi Wear OS (Wear Compose Material 3) — depend ke :shared
```

`:app` dan `:wear` **tidak boleh** menduplikasi logika skoring atau format ucapan. Paritas aturan
dijamin oleh konstruksi lewat `:shared`, bukan oleh pengujian terpisah.

---

## 8. Troubleshooting

| Gejala | Penyebab & solusi |
|---|---|
| `SDK location not found` | `local.properties` belum ada / `sdk.dir` salah. Lihat §2. |
| `Unsupported class file major version` | JDK bukan 17. Set `JAVA_HOME` ke JDK 17. |
| APK jam gagal dipasang (`INSTALL_FAILED_NO_MATCHING_ABIS`) | Perangkat butuh `armeabi-v7a`. Modul `:wear` sudah memfilter ABI ini; jangan tambahkan dependensi native arm64-only. |
| `adb devices` kosong | Cek kabel/USB debugging, atau ulangi `adb connect`. Coba `adb kill-server` lalu `adb start-server`. |
| Jam tidak muncul setelah `adb pair` | Pastikan `adb connect` memakai port dari **Wireless debugging** (bukan port pairing). |
| TTS tidak bersuara di jam | Wear OS mungkin belum mengunduh voice Bahasa Indonesia. Buka **Settings → Accessibility → Text-to-speech** di jam, pastikan engine Google TTS aktif dan voice Indonesia terpasang. Aplikasi tidak crash — haptik dan skor besar tetap bekerja. |
| TTS tidak bersuara di ponsel | Cek volume media, lalu **Settings → Accessibility → Text-to-speech → Install voice data** untuk Bahasa Indonesia. |
| Suara tertahan / pelan | Volume media maksimumkan. Audio diarahkan ke jalur *navigation guidance* agar keluar lewat speaker. |

---

## 9. Kontribusi

1. `./gradlew build` harus hijau sebelum commit.
2. Perubahan aturan skoring **wajib** disertai unit test di `:shared`.
3. Semua teks UI dan ucapan suara berbahasa Indonesia.
4. Target sentuh minimal 48dp, kontras tinggi, label konten untuk screen reader.
5. Jangan commit kredensial, `local.properties`, atau direktori `build/`.

---

## 10. Referensi

- [BWF Laws of Badminton — Law 7 (Scoring System)](https://corporate.bwfbadminton.com/statutes/)
- [Wear OS standalone apps](https://developer.android.com/training/wearables/apps/standalone-apps)
- [Android TextToSpeech](https://developer.android.com/reference/android/speech/tts/TextToSpeech)
