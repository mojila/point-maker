# Konsep Aplikasi — Point Maker untuk Wear OS

Status: Konsep disetujui, siap implementasi
Tanggal: 2026-09-24
Pemilik produk: product-owner (keputusan mengikat)
Koordinator teknis: orchestrator
Target perangkat: **Xiaomi Watch 2** (`axolotlaxie`), Wear OS 4, API 34

---

## 0. Koreksi target: Wear OS, bukan watchOS

Permintaan awal menyebut "watchOS". Setelah verifikasi perangkat via adb, perangkat yang
terhubung **bukan Apple Watch** dan tidak menjalankan watchOS:

| Properti | Nilai terverifikasi |
|---|---|
| Model | `Xiaomi_Watch_2` / produk `axolotlaxie` |
| OS | Wear OS 4 (`ro.build.version.release=14`), **API 34** |
| Layar | 466x466 @ 320dpi, **bulat** (`FLAG_ROUND`, radius sudut 233px) → lebar efektif ~**233dp** |
| RAM | 1.82 GB, ditandai **`android.hardware.ram.low`** |
| ABI | **`armeabi-v7a` saja** (32-bit, tanpa arm64) |
| Speaker | Ada (`android.hardware.audio.output`, `audio.pro`) |
| Mikrofon | Ada |
| Vibrator | Ada |
| Rotary / bezel | **Tidak ada** — input hanya sentuh (`zinitix_ts`, `gpio-keys`) |
| TTS engine | `com.google.android.tts` build wear terpasang, `split_config.armeabi_v7a` |
| Locale sistem | `en-US`; `tts_default_synth=null` |
| Layar mati otomatis | `screen_off_timeout=30000` |
| Play Store | `com.android.vending` terpasang |

Konsekuensi: seluruh konsep di bawah adalah **konsep aplikasi Wear OS (Android)**. Tidak ada
bagian yang memakai API watchOS/WatchKit.

---

## 1. Visi versi jam

> Skor badminton selalu akurat dan selalu terdengar, tanpa perlu memegang apa pun —
> jam di pergelangan adalah wasit digital yang berdiri sendiri.

Perbedaan inti dari versi HP: pemain **tidak lagi meraih HP di bangku**. Jam sudah terpasang di
pergelangan, jadi biaya akses turun, tetapi presisi ketukan dengan jari berkeringat naik jadi
tantangan utama.

## 2. Model aplikasi: standalone penuh (keputusan mengikat)

- **v1 = aplikasi Wear OS standalone.** Skor, suara, dan haptik berjalan tanpa HP.
- `com.google.android.wearable.standalone = true` di manifest.
- **Tanpa** login, pairing wajib, companion app, atau sinkronisasi HP↔jam.
- Konsekuensi yang diterima: pertandingan di jam **tidak** muncul di HP dan sebaliknya. Satu
  perangkat dipilih sebagai pencatat per pertandingan. Tidak ada sinkronisasi parsial.
- Kriteria henti: bila ≥30% penguji melaporkan butuh melihat skor di HP, sinkronisasi naik ke
  v2 sebagai *read-only mirror*, bukan dua arah.

## 3. Ruang lingkup v1

### WAJIB (urut prioritas)

| # | Fitur | Kriteria inti |
|---|-------|---------------|
| 1 | Logika skoring BWF | **Pakai ulang `domain/` yang sama** dengan versi HP — nol duplikasi aturan |
| 2 | Ucapan skor TTS Bahasa Indonesia | Angka sebagai kata, sisi pencetak poin terakhir lebih dulu, satu reli satu ucapan |
| 3 | Tambah poin kiri/kanan | Dua zona ketuk, satu ketukan = satu poin |
| 4 | **Haptik konfirmasi** | Getaran tiap ketukan — kanal umpan balik kedua saat layar tidak dilihat |
| 5 | Undo satu langkah | Mengembalikan state sebelumnya |
| 6 | Reset / pertandingan baru | Wajib dialog konfirmasi |
| 7 | Akhir set & akhir pertandingan | Otomatis + ucapan pemenang |
| 8 | Persistensi state | Tahan layar mati dan process-kill (RAM rendah) |

### DITUNDA

Kustomisasi nama pemain, mini-score penuh gaya HP, indikator servis detail, semua polesan visual.

### OUT

Sinkronisasi HP↔jam, companion/kontrol dari HP, riwayat lintas sesi, mode ganda/mixed,
multi-bahasa, kustomisasi suara TTS, login/cloud, tile/complication/watch face, input rotary.

## 4. Aturan skoring

**Tidak berubah dan tidak boleh ditulis ulang.** Seluruh aturan memakai `MatchRules` yang sudah
ada (rally point 21, deuce 20-20 selisih 2, batas 30 pada 29-29, best of 3). Versi jam hanya
mengonsumsi `MatchState` + `MatchRules` yang sama, sehingga paritas aturan dijamin oleh
konstruksi, bukan oleh pengujian ulang terpisah.

## 5. Format ucapan

Identik dengan PRD §5. Tidak ada format baru untuk jam. Satu-satunya penambahan adalah
**kanal haptik** yang mendampingi ucapan.

## 6. Desain layar: satu layar, dua zona ketuk

Layar bulat 233dp tidak bisa menampung dua tombol persegi besar seperti versi HP (sudut terpotong).
Pola yang dipakai:

```
        ┌─────────────────────┐
        │   Set 2 · S1 21-18  │   ← header set + mini-score ringkas
        │                     │
        │  ┌───────┬───────┐  │
        │  │ KIRI  │ KANAN │  │   ← dua zona ketuk separuh lingkaran
        │  │  17   │  15   │  │     skor besar, terbaca sekilas
        │  └───────┴───────┘  │
        │      ▲ gutter       │   ← gutter tengah mati 20dp
        │                     │
        │   ⟲ Undo   ⚙ Suara  │   ← kontrol di busur bawah
        └─────────────────────┘
```

- **Separuh kiri = +1 Kiri, separuh kanan = +1 Kanan.** Mental model "dua sisi" dipertahankan.
- **Gutter tengah mati ±20dp** mencegah salah sisi.
- Target efektif tiap zona **≥48dp**; turun ke 40dp hanya bila ruang tidak cukup.
- **Undo** adalah tombol terpisah di busur bawah, jauh dari zona poin agar tidak salah tekan.
- Skor besar (≈64–80sp) di tengah, terbaca tanpa mengangkat pergelangan.
- Tanpa hierarki navigasi >1 level. Pengaturan hanya satu toggle suara.

**Ditolak:** swipe (bentrok gesture sistem), long-press/double-tap (tersembunyi & bentrok sistem),
tombol fisik (perangkat tidak punya), meniru tombol persegi HP apa adanya (terpotong di layar bulat).

### 6.1 Adaptasi bentuk layar: bulat vs kotak (keputusan pemilik produk, 2026-09-24)

Versi awal hanya mengasumsikan layar kotak penuh, sehingga di jam bulat (Xiaomi Watch 2, 466x466
@320dpi, radius 233px) header, sudut zona poin, dan tombol bawah **terpotong busur lingkaran**.
Perbaikan ini disetujui sebagai item 6b (setelah aksesibilitas, sebelum polesan visual).

- Deteksi bentuk memakai `Configuration.isScreenRound` (bukan setting manual).
- Inset dan radius dipilih lewat helper murni `WearLayoutPolicy` (`WearScreenShape.kt`) agar
  teruji di JVM: bulat → inset tepi lebih besar + kontrol bawah menyempit; kotak → seperti semula.
- Ukuran font skor **adaptif terhadap tinggi zona** (`BoxWithConstraints`), bukan konstanta per
  bentuk. Ini menutup bug skor dua digit terpotong saat banner set point menyusutkan zona.
- **Tidak berubah:** aturan skoring, format ucapan, aksi satu ketukan = satu poin, Undo/Reset,
  haptik, persistensi, dan semua teks Bahasa Indonesia.
- Kriteria teruji: di jam bulat, header + kedua zona + ketiga tombol bawah utuh di dalam
  lingkaran pada state dasar (3-2), set point (20-0), set selesai, dan dialog Reset; target
  sentuh zona ≥48dp; gutter tengah ≥20dp.

## 7. Suara dan getaran

- **TTS speaker tetap fitur inti (P1).** Bermanfaat untuk *kedua* pemain di lapangan, bukan hanya
  pemakai jam. Audio attributes diarahkan ke `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` +
  `CONTENT_TYPE_SPEECH` agar keluar lewat speaker jam, bukan tertahan sebagai media.
- **Haptik wajib (P1).** Memberi kepastian "ketukan tercatat" tanpa melihat layar — melindungi
  metrik "aksi inti satu ketukan" dan guardrail "tidak ada poin hilang".
- Pola haptik (P2): tick = poin biasa, double-click = set point/match point, click = undo,
  pola lebih tegas = set/pertandingan berakhir.
- **Fallback:** bila TTS Bahasa Indonesia tidak tersedia → tidak crash, haptik tetap jalan,
  skor besar tetap tampil, indikator status suara tampil jelas.

### 7.1 Bunyi cue (keputusan pemilik produk, 2026-09-24)

Aplikasi jam memutar bunyi cue pendek **sebelum** TTS saat poin bertambah, demi paritas dengan
`:app` (fitur cue di `:app` ditambahkan manual oleh pemilik produk).

- Aset: `wear/src/main/res/raw/point_cue.mp3` (durasi terukur **150 ms**).
- Orkestrasi: `ScoreCueSequencer` (dipindah ke `:shared`, satu sumber kebenaran) +
  `WearPointCuePlayer` (MediaPlayer).
- `playCue` adalah flag **terpisah** dari `WearFeedback`. Cue diputar HANYA saat poin bertambah;
  ganti set, undo, dan reset tidak memutar cue (sesuai perilaku `:app`).
- **Haptik tetap dipicu SEGERA** saat event diterima, tidak menunggu cue selesai.
- **Risiko latensi (terukur):** karena cue 150 ms diputar sebelum TTS, waktu-ke-suara pertama
  bertambah. Pengukuran tap → synthesis request = **305–316 ms** (dari awal tap). Metrik PRD §8 #2
  (< 300 ms) berisiko terlampaui. Ini dicatat sebagai temuan QA, bukan pemblokir (keputusan pemilik
  produk: cue tetap ada).

## 8. Kriteria rilis v1 (dapat diuji)

1. **Paritas aturan:** suite unit test `domain/` yang sama lulus 100% (20-20 selisih 2,
   29-29 → 30-29, best of 3 berhenti tepat saat 2 set).
2. **Akurasi ucapan:** setiap perubahan skor memicu **tepat satu** ucapan "X - Y", angka sebagai
   kata, angka sisi pencetak poin terakhir lebih dulu (server-first); latensi awal **< 300 ms** terukur di perangkat target; tidak ada
   antrean menumpuk saat ketukan cepat.
3. **Degradasi suara aman:** TTS/voice Indonesia tidak tersedia → tidak crash, haptik tetap
   berfungsi, indikator status suara jelas.
4. **Ketukan benar & pemulihan:** satu ketukan menambah tepat satu poin ke sisi benar; Undo
   mengembalikan tepat satu langkah; Reset tidak pernah menghapus skor tanpa konfirmasi.
5. **Persistensi:** setelah layar mati (30s) atau proses di-kill sistem, membuka ulang
   menampilkan skor identik.
6. **Standalone tanpa setup:** tanpa HP terhubung, aplikasi jalan dari cold start sampai poin
   pertama tanpa pairing/login/setup.

## 9. Kontrak teknis (mengikat untuk implementasi)

### Struktur modul (terimplementasi)

```
:shared   Kotlin JVM library murni — com.pointmaker.app.domain.* dan com.pointmaker.app.audio.ScoreCueSequencer
          (MatchRules, MatchState, Side, ScoreFormatter, AnnouncementText, Announcement, ScoreCueSequencer)
          + unit test domain & sequencer. Tanpa dependensi Android.
:app      Aplikasi HP (perilaku tetap; fitur cue ditambahkan manual oleh pemilik produk) — depend ke :shared
:wear     Aplikasi Wear OS — depend ke :shared
```

`:shared` adalah satu sumber kebenaran aturan skoring dan format ucapan. `:app` dan `:wear`
tidak boleh menduplikasi logika tersebut.

**Versi toolchain terpasang (terverifikasi):** Gradle 8.11.1, AGP 8.7.3, **Kotlin 2.1.20**
(dinaikkan dari 2.0.21 karena Wear Compose Material3 1.5.0 menuntut Compose UI 1.8.2),
Wear Compose Material3 **1.5.0**, JDK 17, compileSdk 37, buildTools 36.0.0.
`:app` tetap memakai Compose BOM 2024.12.01.

### Modul `:wear`

- **Plugin:** `com.android.application`, `org.jetbrains.kotlin.android`, `org.jetbrains.kotlin.plugin.compose`
- **namespace / applicationId:** `com.pointmaker.app.wear`
- **minSdk 30** (Wear OS 3 baseline), **targetSdk 34**, **compileSdk 37**
- **ABI filter wajib `armeabi-v7a`** — perangkat 32-bit saja; mencegah dependensi native
  arm64-only ikut masuk dan membuat APK tidak bisa dipasang
- **Dependensi UI:** `androidx.wear.compose:compose-material3` + `compose-foundation` +
  `compose-ui-tooling-preview` (Wear Compose Material 3, bukan `compose-material` lama dan
  **bukan** `androidx.compose.material3` ponsel)
- **Dependensi lain:** `activity-compose`, `lifecycle-viewmodel-compose`, `datastore-preferences`,
  `core-ktx`, `:shared`
- **Larangan:** dependensi native arm64-only; library ponsel-only; Compose Material 3 ponsel
  untuk UI jam

### Manifest `:wear`

```xml
<uses-feature android:name="android.hardware.type.watch" android:required="true" />
<uses-feature android:name="android.hardware.audio.output" android:required="false" />
<application ...>
  <meta-data android:name="com.google.android.wearable.standalone" android:value="true" />
  <activity android:name=".WearMainActivity" android:exported="true" ...>
    <intent-filter>
      <action android:name="android.intent.action.MAIN" />
      <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
  </activity>
</application>
```

### Skema state & persistensi

Skema `MatchState` **tidak berubah**. Persistensi memakai DataStore Preferences seperti versi HP,
dengan penekanan khusus: **tulis snapshot segera setiap perubahan poin** (bukan menunggu
lifecycle) karena perangkat ditandai `ram.low` dan proses sering dimatikan sistem.

### TTS di jam

- `TextToSpeech` dibuat dengan `AudioAttributes` eksplisit (`USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`,
  `CONTENT_TYPE_SPEECH`) agar suara keluar lewat speaker jam.
- Pre-warm saat aplikasi dibuka; engine Wear OS butuh ~10 detik siap setelah boot, jadi
  **jangan** ucapkan apa pun sebelum `onInit` sukses.
- Baris pertama satu reli = `QUEUE_FLUSH`, baris berikutnya = `QUEUE_ADD` (kontrak sama dengan HP).
- Deteksi ketersediaan Bahasa Indonesia lewat hasil `setLanguage(Locale("id","ID"))`; Wear OS TTS
  bawaan **tidak** memuat Bahasa Indonesia secara default sehingga fallback harus benar-benar
  teruji, bukan teoretis.

### Haptik

`VibratorManager`/`Vibrator` dengan `VibrationEffect.createPredefined`, dibungkus try/catch dan
dicek `hasVibrator()`. Tidak boleh membuat aplikasi crash pada perangkat tanpa vibrator.

### Layar

- `FLAG_KEEP_SCREEN_ON` selama aplikasi di foreground (perilaku sama dengan versi HP).
- Risiko daya diterima dan dicatat; alternatif ambient mode ditunda ke v2.

### Bahasa

Seluruh teks UI dan ucapan berbahasa Indonesia.

## 10. Risiko khusus jam & mitigasi

| # | Risiko | Dampak | Mitigasi |
|---|---|---|---|
| R1 | Voice TTS Indonesia tidak preloaded di Wear OS | Suara tidak berfungsi | Deteksi bahasa + arahkan unduh voice data; fallback haptik + skor besar + indikator status |
| R2 | Speaker jam pelan vs lapangan bising | Skor tidak terdengar | Haptik wajib sebagai kanal kedua; kalimat pendek; volume media maksimum |
| R3 | RAM 1.82 GB, `ram.low` → proses sering dimatikan | Skor hilang di tengah pertandingan | Persist segera setiap poin; tanpa aset besar |
| R4 | Layar bulat kecil + jari basah | Salah ketuk sisi | Dua zona besar + gutter tengah mati + Undo terpisah |
| R5 | ABI hanya `armeabi-v7a` | Dependensi native arm64 gagal pasang | `abiFilters = armeabi-v7a`; verifikasi APK terpasang |
| R6 | Tanpa rotary/bezel, layar mati 30s | Kehilangan konteks | Satu layar tanpa hierarki; state dipulihkan saat bangun; haptik konfirmasi |
| R7 | TTS ~10 detik siap setelah boot | Poin pertama tanpa suara | Pre-warm saat dibuka; tidak mengucapkan sebelum `onInit` sukses |

## 11. Yang belum tervalidasi (wajib diukur QA)

- Volume speaker jam relatif terhadap kebisingan lapangan badminton — **lemah**
- Latensi tap → suara: terukur **305–316 ms** dari awal tap (5 sampel, termasuk cue 150 ms);
  anggaran PRD < 300 ms **berisiko terlampaui** — perlu keputusan lanjutan
- **Ketersediaan voice data Bahasa Indonesia: TERKONFIRMASI TERSEDIA** di perangkat target
  (`GoogleTTS: locale ind-IDN`, voice `id-id-x-dfz-lstm-embedded`). Asumsi awal PO bahwa voice ID
  tidak preloaded **tidak berlaku** untuk perangkat ini.
- Ergonomi ketuk dengan jari berkeringat — **sedang**

---

## 12. Rencana eksekusi

| # | Tugas | Agen | Gerbang verifikasi | Status |
|---|---|---|---|---|
| 1 | Ekstrak `:shared` (domain + test), rewire `:app` | android-developer | `:app:assembleDebug`, `:shared:test`, `:app:testDebugUnitTest` hijau | SELESAI |
| 2 | Scaffold modul `:wear` (Gradle, manifest, theme, standalone, abiFilter) + layar | android-developer | `:wear:assembleDebug` hijau | SELESAI |
| 3 | Bunyi cue di `:wear` + pindah `ScoreCueSequencer` ke `:shared` | android-developer | build + test hijau | SELESAI |
| 4 | TTS jam + haptik + persistensi low-RAM | android-developer | build + unit test hijau | SELESAI |
| 4b | Perbaikan 2 error lint (`MissingPermission VIBRATE`, `ProduceState`) | android-developer | `build test` hijau, lint 0 error | SELESAI |
| 5 | Verifikasi perangkat: 6 kriteria rilis | qa-tester | laporan bug berbukti | BERJALAN |
| 6 | Integrasi akhir | orchestrator | `./gradlew build test` hijau | SELESAI |
| 6b | Adaptasi layout jam bulat vs kotak (§6.1) | android-developer | `:wear:assembleDebug`, `:wear:testDebugUnitTest`, screenshot bulat tanpa clipping | SELESAI |

### Catatan gerbang 6b
- Temuan QA pertama: skor dua digit **terpotong di bawah** saat banner set point (20-0) menyusutkan
  tinggi zona — hanya muncul di state tepi, tidak tertangkap oleh screenshot state dasar.
- Perbaikan: ukuran font skor dihitung dari `maxHeight` zona (`BoxWithConstraints`), bukan konstanta.
- Verifikasi ulang: 20-0 dan 3-2 keduanya utuh; `.\gradlew.bat build test` hijau (81 test).

### Bug yang ditemukan gerbang lint (penting)
`WearHaptics.kt` memanggil `Vibrator.vibrate()` **tanpa** mendeklarasikan
`android.permission.VIBRATE` di manifest. Akibatnya `SecurityException` ditelan `try/catch` dan
**haptik tidak pernah berbunyi** — melanggar persyaratan P1 "haptik wajib". Bug ini tidak
tertangkap unit test (haptik tidak dapat diuji di JVM) maupun pengujian manual (getaran tidak
terukur otomatis); hanya lint yang menangkapnya. Sudah diperbaiki.

### Gerbang integrasi final (terverifikasi)
- `.\gradlew.bat clean build test` → **BUILD SUCCESSFUL**
- Unit test: **76 hijau** (`:shared` 68, `:app` 2, `:wear` 6), 0 gagal
- Lint: `:app` 0 error / 37 warning, `:wear` 0 error / 6 warning
- APK: `app-debug.apk` 9,3 MB · `wear-debug.apk` 24,4 MB

### Bukti terpasang (orchestrator, sebelum QA)
- APK `:wear` **berhasil dipasang** di Xiaomi Watch 2 dengan hanya `lib/armeabi-v7a` (gerbang ABI lulus).
- Cold start tanpa crash; label "Point Maker" terdaftar di launcher.
- Uji ketuk terkontrol: satu tap = satu poin ke sisi benar (4-2 → 5-2 → 5-3).
- Undo mengembalikan satu langkah; force-stop lalu buka ulang mempertahankan skor.
- Dialog Reset muncul dengan konfirmasi ("Reset pertandingan?" / Batal / Reset).
- TTS Bahasa Indonesia berbunyi; cue MP3 diputar (`c2.android.mp3.decoder`, `audio/mpeg`).
- 76 unit test hijau (`:shared` 68, `:app` 2, `:wear` 6).

---

## 13. Referensi

- BWF Laws of Badminton, Law 7 (skoring) — kuat
- Wear OS standalone apps (`com.google.android.wearable.standalone`) — kuat
- Wear OS touch target 48dp / 40dp, layar bulat ~22% lebih kecil — kuat
- Wear OS TTS baru (Wear OS 4+): 7 bahasa preloaded tanpa Indonesia, siap ~10s setelah boot — kuat
- Wear OS audio: speaker built-in untuk use case non-media — kuat
- Wear OS power: sinkronisasi/internet di jam mahal — sedang–kuat
