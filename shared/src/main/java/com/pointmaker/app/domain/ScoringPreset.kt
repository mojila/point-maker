package com.pointmaker.app.domain

enum class ScoringPreset(
    val storageKey: String,
    val pointsToWin: Int,
    val deucePoints: Int,
    val hardCap: Int,
    val label: String
) {
    FIFTEEN("15x3", 15, 14, 21, "15 poin"),
    TWENTY_ONE("21x3", 21, 20, 30, "21 poin");

    companion object {
        val DEFAULT: ScoringPreset = FIFTEEN

        const val MAX_POINTS: Int = 30

        fun fromStorageKey(raw: String?): ScoringPreset =
            entries.firstOrNull { it.storageKey == raw } ?: DEFAULT

        /**
         * Preset hasil pembacaan DataStore, dengan aturan kompatibilitas data lama.
         *
         * - kunci ada dan dikenal: pakai nilai itu;
         * - kunci ada tetapi tidak dikenal: [DEFAULT] (15x3), tanpa crash;
         * - kunci tidak ada (data lama sebelum preset diperkenalkan): bila pertandingan sudah
         *   berjalan, pakai [TWENTY_ONE] agar skor 21 poin yang tersimpan tidak berubah arti;
         *   bila belum berjalan, [DEFAULT].
         */
        fun fromStorage(raw: String?, matchInProgress: Boolean): ScoringPreset = when {
            raw == null -> if (matchInProgress) TWENTY_ONE else DEFAULT
            else -> fromStorageKey(raw)
        }
    }
}
