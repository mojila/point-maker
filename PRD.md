# PRD — Point Maker (MVP)

Status: Final untuk MVP
Tanggal: 2026-09-21
Pemilik produk: product-owner
Koordinator teknis: orchestrator
Sumber keputusan: konsultasi product-owner (mengikat). Aturan skoring divalidasi terhadap BWF Laws of Badminton, Law 7.

---

## 1. Visi & Masalah

**Visi:** wasit digital satu tangan untuk badminton — skor selalu akurat, selalu terdengar, tanpa perlu melihat layar.

**Masalah utama:** pemain/rekan main harus mengingat dan mengumumkan skor manual sambil bermain, sehingga sering lupa skor, salah hitung saat deuce, dan ribut soal angka.

## 2. Persona & Konteks Pemakaian

- Pemain badminton rekreasi–klub, usia 20–45, tunggal/ganda, lapangan indoor, sering tanpa wasit.
- HP diletakkan di bangku pinggir lapangan atau lantai dekat net — **tidak digenggam** saat reli.
- Menambah poin: satu tangan meraih HP, menekan satu tombol besar, lalu kembali bermain. Interaksi maksimal **1 ketukan**.
- Kondisi lingkungan: layar lembap karena keringat, cahaya lapangan beragam, suara bising (kok, pukulan, lompatan).

## 3. Ruang Lingkup MVP

### IN (wajib, urut prioritas)

| # | Fitur | Kriteria inti |
|---|-------|---------------|
| 1 | Logika skoring BWF | Rally point 21, deuce 20-20, batas 30, best of 3 |
| 2 | Tambah poin kiri/kanan | Dua tombol besar, satu ketukan per poin |
| 3 | Ucapan skor TTS Bahasa Indonesia | Otomatis setiap skor berubah, angka sebagai kata |
| 4 | Undo satu langkah | Mengembalikan state skor sebelumnya |
| 5 | Reset / pertandingan baru | Wajib dialog konfirmasi |
| 6 | Akhir set otomatis | Ucapan pemenang set + lanjut set berikutnya |
| 7 | Persistensi state | Aman dari rotasi dan app di-kill |
| 8 | Aksesibilitas dasar | Target sentuh >= 48dp, label screen reader |

### OUT (ditunda)

Lihat §9.

## 4. Aturan Skoring (Mengikat)

1. **Set:** dimenangkan pihak yang pertama mencapai **21** poin.
2. **Deuce:** pada **20-20**, set berakhir hanya bila salah satu pihak unggul **2 poin** (22-20, 23-21, dst).
3. **Batas 30:** pada **29-29**, pihak yang mencetak poin ke-**30** menang set (30-29). Tidak ada skor di atas 30.
4. **Best of 3:** pertandingan berakhir saat satu pihak menang **2 set**.
5. **Set berakhir:** penambahan poin berhenti otomatis, aplikasi mengucapkan pemenang set, skor set dicatat; set berikutnya mulai 0-0 setelah aksi lanjut (tanpa konfirmasi karena tidak destruktif).
6. **Pertandingan berakhir:** pertandingan ditandai selesai, tombol tambah poin nonaktif, aplikasi mengucapkan pemenang pertandingan; aksi "pertandingan baru" bersifat destruktif → butuh konfirmasi.
7. **Servis:** pihak pemenang set melakukan servis pertama di set berikutnya, dicatat sebagai status servis sederhana (bukan penilaian fault).

## 5. Format Ucapan TTS (Mengikat)

Pola: skor dibaca **mulai dari sisi pencetak poin terakhir (server-first), lalu angka lawan**. Angka diucapkan sebagai **kata**, bukan digit.

| Situasi | Ucapan |
|---------|--------|
| Poin biasa | "Tujuh belas, lima belas" |
| Skor nol | "Tiga, nol" |
| Set point | "Dua puluh, delapan belas. Set point untuk kiri" |
| Match point | "Dua puluh, sembilan belas. Match point untuk kanan" |
| Deuce | "Dua puluh sama. Deuce" |
| Set berakhir | "Set pertama untuk kiri, dua puluh satu delapan belas" |
| Ganti set | "Set kedua, mulai" |
| Pertandingan berakhir | "Pertandingan selesai. Pemenang: kiri, dua set nol" |
| Batas 30 | "Tiga puluh dua puluh sembilan. Set point untuk kiri" |
| Mendekati akhir set | "Dua puluh, sembilan belas. Set point untuk kanan" |

**Catatan teknis TTS (validasi Android API):**
- TTS hanya boleh dipakai setelah inisialisasi sukses (`onInit` = SUCCESS).
- `speak()` bersifat async; gunakan `QUEUE_FLUSH` agar antrean tidak menumpuk saat tombol ditekan cepat.
- Gunakan `UtteranceProgressListener` + `KEY_PARAM_UTTERANCE_ID` untuk mendeteksi kegagalan.
- Latensi target < 300 ms adalah **asumsi**, wajib diukur di perangkat target.

## 6. Layar MVP

### Layar Utama (single screen, tanpa navigasi tambahan)
- **Header:** identitas set (set ke-1/2/3) dan status "best of 3".
- **Skor besar kiri & kanan:** angka besar, terbaca dari jarak 1–2 meter.
- **Tombol tambah poin kiri/kanan:** dua area besar, target >= 48dp.
- **Tombol Undo & Reset:** Reset nonaktif sampai dikonfirmasi.
- **Indikator servis** (opsional): pihak yang servis.
- **Mini-score set 1/2/3:** menandai set yang sudah dimenangkan.
- **Banner status:** "Set point", "Match point", "Set 2 dimulai", "Pertandingan selesai".
- **Dialog konfirmasi** Reset / pertandingan baru.

### Layar Pengaturan Minimal
- Label sisi: "Kiri"/"Kanan" atau nama pemain.
- Nyala/mati suara.

Tidak ada layar login, riwayat, atau setup awal.

## 7. Kriteria Rilis MVP (Acceptance)

1. Pada 20-20, poin berikutnya **tidak** mengakhiri set; set berakhir hanya saat selisih 2.
2. Pada 29-29, poin ke-30 mengakhiri set 30-29.
3. Pertandingan berakhir tepat saat satu pihak menang 2 set; tombol tambah poin berhenti bekerja.
4. Setiap perubahan skor memicu **tepat satu** ucapan, angka sebagai kata (bukan digit), angka sisi pencetak poin terakhir lebih dulu (server-first).
5. Tidak ada ucapan tumpang-tindih atau antrean menumpuk saat tombol ditekan cepat berulang.
6. Undo mengembalikan state satu langkah; Reset meminta konfirmasi dan tidak menghapus skor tanpa konfirmasi.
7. State skor utuh setelah rotasi layar dan setelah proses aplikasi dimatikan sistem lalu dibuka lagi.
8. Semua tombol utama target sentuh >= 48dp, punya label aksesibilitas, konten terbaca screen reader.
9. Aplikasi menangani kasus TTS tidak tersedia / Bahasa Indonesia tidak didukung **tanpa crash**, dengan indikator status suara.

## 8. Metrik Sukses MVP

| # | Metrik | Target | Sumber |
|---|--------|--------|--------|
| 1 | Akurasi aturan skoring (BWF Law 7) | 100% cocok pada kasus deuce, batas 30, best of 3 | Pengujian terarah / QA |
| 2 | Akurasi & ketepatan ucapan | 100% perubahan skor memicu tepat satu ucapan benar; latensi awal < 300 ms (asumsi, wajib diukur) | Pengujian manual QA di perangkat target |
| 3 | Pemulihan state | 100% skor utuh setelah rotasi dan setelah app di-kill | Pengujian manual QA |

## 9. Fitur OUT & Alasan Penundaan

| Fitur | Alasan |
|-------|--------|
| Login / akun / sinkronisasi cloud | Menambah friksi sebelum skor pertama; tidak ada nilai di lapangan |
| Riwayat pertandingan lintas sesi & statistik lanjutan | Tidak mengubah pengalaman selagi bermain |
| Mode ganda/mixed & aturan turnamen khusus | Kompleksitas aturan tinggi, bukan inti v1 |
| Multi-bahasa | Menambah permukaan pengujian TTS; Bahasa Indonesia cukup untuk pasar awal |
| Kustomisasi TTS lanjutan (pilihan suara, kecepatan, nada) | Tidak esensial untuk akurasi dan latensi |
| Widget, notifikasi, smartwatch, Bluetooth | Menambah platform surface tanpa bukti kebutuhan |

## 10. Batasan & Risiko

| Risiko | Dampak | Mitigasi MVP |
|--------|--------|--------------|
| Kebisingan lapangan | Ucapan tidak terdengar | Volume media, kalimat pendek dan jelas |
| TTS tidak tersedia / ID tidak didukung | Suara tidak berfungsi | Fallback + indikator status, tanpa crash |
| Latensi TTS tidak terjamin | Terasa terlambat | Ukur di perangkat target; < 300 ms masih asumsi |
| Layar basah/keringat & cahaya | Salah tekan | Tombol besar + konfirmasi aksi destruktif |
| Fokus pemain turun saat pakai HP | Interaksi lambat | Maksimal 1 ketukan per poin |
| Pengguna memakai aturan rumah non-21 | Aplikasi tidak sesuai | Di luar lingkup v1 |

## 11. Kontrak Teknis untuk Implementasi

- **Package name:** `com.pointmaker.app`
- **Stack:** Kotlin, Jetpack Compose, Material 3, minSdk 24, targetSdk terbaru stabil.
- **Bahasa:** seluruh teks UI dan ucapan TTS berbahasa Indonesia.
- **Arsitektur state:** satu sumber kebenaran `MatchState` (skor set saat ini, skor per set, set yang dimenangkan, status servis, status selesai) dengan riwayat undo; logika skoring murni dan teruji unit.
- **Skema data inti:**
  - `currentSet: Int (1..3)`
  - `leftPoints: Int`, `rightPoints: Int`
  - `setsWonLeft: Int`, `setsWonRight: Int`
  - `completedSets: List<Pair<Int, Int>>`
  - `servingSide: LEFT | RIGHT`
  - `matchFinished: Boolean`
  - `history: Stack<MatchState>` untuk undo
- **Persistensi:** state dipertahankan melewati rotasi dan proses di-kill (mis. `SavedStateHandle` + penyimpanan lokal).

## 12. Referensi Validasi

- BWF Laws of Badminton, Law 7 (Scoring System) — best of three (7.1), game 21 poin (7.2), deuce 20-20 selisih 2 (7.4), batas 30 pada 29-29 (7.5), pemenang set servis pertama set berikutnya (7.6). Kekuatan bukti: kuat.
- Android `TextToSpeech` & `UtteranceProgressListener` — pemakaian setelah `onInit`, sifat async `speak()`, deteksi error via `UtteranceProgressListener` + `KEY_PARAM_UTTERANCE_ID`. Kekuatan bukti: kuat.
- Ambang latensi TTS < 300 ms dan target sentuh 48dp — 48dp sesuai panduan Android (kuat); < 300 ms praktik umum, belum diukur pada perangkat target (lemah, wajib divalidasi QA).
