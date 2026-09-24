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
