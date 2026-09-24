# Instruksi Proyek — Point Maker

## Web search & fetch

- Untuk pencarian web, **selalu** gunakan tool MCP kenari `kenari_web_search`
  (Code Mode: `tools.kenari.kenari_web_search`). Ini adalah default web search
  untuk proyek ini.
- Untuk mengambil isi satu halaman web, gunakan `kenari_web_fetch`
  (`tools.kenari.kenari_web_fetch`).
- Jangan gunakan tool `websearch` bawaan OpenCode; tool itu dinonaktifkan
  (`websearch: false` di `.opencode/opencode.json`).
- Tool kenari lain yang tersedia: `kenari_x_search` (pencarian X/Twitter) dan
  `kenari_search_docs` (dokumentasi kenari). Panggilan ke tool kenari ini
  ditagih ke wallet, jadi gunakan secukupnya.

## Keputusan terstruktur dengan Jev (wajib untuk semua agent)

Jev 1.13 (`jev-1-13-free`, TypeSafe System One) tersedia sebagai tool
`jev_decide` (namespace `jev`). Ini **default untuk semua titik keputusan** di
proyek ini — bukan pilihan opsional.

**Pakai `jev_decide` untuk setiap keputusan terbatas (bounded decision):**

- routing / klasifikasi / triage (mis. tiket, temuan QA, tugas)
- risk gate & gerbang tinjauan manusia (mis. "aman dijalankan?", "perlu review?")
- policy pre-screening (mis. patuh aturan produk, dalam/luar lingkup)
- prioritas dan kelayakan (mis. urgensi, keparahan, tingkat keyakinan)
- menerima/menolak hasil kerja subagent

**Jangan pakai Jev untuk:** menulis kode, teks prosa, penjelasan, atau
penalaran terbuka. Jev mengembalikan jawaban bertipe (probabilitas/confidence),
bukan kalimat. Pekerjaan kreatif tetap dikerjakan dengan cara biasa.

Aturan pemakaian:

- Ajukan semua pertanyaan independen dalam **satu** panggilan.
- `state` berisi fakta minimum yang benar-benar mengubah jawaban; jangan
  menempelkan seluruh percakapan.
- Pilih tipe pertanyaan: `noul` (ya/tidak + P(yes)), `choice` (satu dari 2-255
  opsi bernama), `score` (skala berurutan 2-10 level).
- Perlakukan probabilitas sebagai ketidakpastian terkalibrasi, **bukan fakta**.
  Bila confidence rendah atau distribusi hampir seragam, eskalasi ke manusia
  atau model reasoning — jangan memaksakan keputusan.
- Jev kuat dalam bahasa Inggris. Tulis `state`/`instructions` dalam bahasa
  Indonesia bila konteksnya berbahasa Indonesia; jika tidak, pakai Inggris.
- Jangan mengarang jawaban yang tidak dikembalikan Jev. Bila tool gagal,
  laporkan error apa adanya.

Pintasan: command `/jev <permintaan>` menjalankan subagent `decision-agent`
yang khusus menangani panggilan Jev.
