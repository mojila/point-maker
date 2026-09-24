# Jev (System One) — decision tool portabel

Tool `jev_decide` (namespace `jev`) memanggil **TypeSafe Jev 1.13**
(`jev-1-13-free`) lewat endpoint kenari `POST /v1/systemone`. Jev adalah model
keputusan bertipe: menerima `state` + pertanyaan bernama, mengembalikan jawaban
bertipe (`noul` / `choice` / `score`) beserta probabilitas — bukan prosa.

Konfigurasi ini **portabel**: ikut ter-clone bersama repo, tanpa `npm install`.

## Isi

| Path | Fungsi |
|---|---|
| `.opencode/plugins/jev-decision.ts` | Plugin OpenCode V2 yang mendaftarkan tool `jev_decide`. Tanpa import `@opencode/plugin`. |
| `.opencode/agents/decision-agent.md` | Subagent yang hanya boleh memanggil Jev (edit & shell ditolak). |
| `.opencode/commands/jev.md` | Command `/jev <permintaan>`. |

Plugin di `.opencode/plugins/` **ter-discover otomatis** oleh OpenCode, jadi
tidak perlu entri `plugins` di config.

## Prasyarat

Satu hal yang tidak bisa di-portabel-kan: **API key**. Set salah satu:

```powershell
# Opsi 1 — environment variable (disarankan)
$env:KENARI_API_KEY = "kn-..."
```

```jsonc
// Opsi 2 — lewat opsi plugin di .opencode/opencode.json
// (perhatikan: jangan commit key ke repo)
{
  "plugins": [
    { "package": "./.opencode/plugins/jev-decision.ts",
      "options": { "apiKey": "{env:KENARI_API_KEY}" } }
  ]
}
```

Tanpa key, plugin tetap ter-load tetapi tool gagal dengan pesan yang jelas.

## Pemakaian

- **Command:** `/jev <permintaan>`
- **Subagent:** minta agent utama memakai `decision-agent`
- **Langsung:** agent mana pun dapat memanggil `jev_decide`

Contoh:

```text
/jev Tinjau tiket #4821: pelanggan bilang tagihannya terpotong dua kali pagi ini
```

## Tipe pertanyaan

| Tipe | Bentuk `criteria` | Jawaban |
|---|---|---|
| `noul` | objek opsional `{true, false}` | `P(yes)` di [0,1] |
| `choice` | objek wajib `opsi -> deskripsi` (2–255 opsi) | opsi terpilih + confidence + distribusi |
| `score` | array wajib berurutan rendah→tinggi (2–10 level) | nilai + confidence + legend + distribusi |

## Batas

- `state` maksimum 24.000 karakter.
- Maksimum 32 pertanyaan per panggilan.
- Timeout 60 detik; retry otomatis pada HTTP 429.
- Tier gratis dibatasi **1 request sukses per menit** sebelum top-up pertama.
- Jev **bukan** model chat: jangan pakai untuk menulis kode, prosa, atau
  penalaran terbuka. Probabilitas = ketidakpastian terkalibrasi, bukan fakta.

## Verifikasi

```powershell
# plugin ter-load tanpa error?
Select-String -Path "$env:USERPROFILE\.local\share\opencode\log\opencode.log" `
  -Pattern "jev-decision" | Select-Object -Last 5
```

```text
# uji cepat di TUI
/jev Apakah perubahan ini aman dijalankan tanpa review manusia?
```
