---
description: Typed decision via Jev 1.13 (System One) — noul/choice/score
agent: decision-agent
subagent: true
---

Susun satu panggilan `jev_decide` untuk permintaan berikut, lalu laporkan hasilnya.

Aturan:
- Tulis `state` berisi fakta minimum yang benar-benar mengubah jawaban. Jangan sertakan seluruh percakapan.
- Pilih tipe pertanyaan sesuai kebutuhan: `noul` (ya/tidak + P(yes)), `choice` (satu dari 2-255 opsi bernama), `score` (skala berurutan 2-10 level).
- Ajukan semua pertanyaan independen dalam SATU panggilan.
- Tulis `state` dan `instructions` dalam bahasa yang sama dengan permintaan di bawah.

Laporkan: keputusan tiap pertanyaan, sinyal ketidakpastian (confidence/distribusi), dan satu rekomendasi tindakan berikutnya. Jangan mengarang jawaban yang tidak dikembalikan Jev.

Permintaan:
$ARGUMENTS
