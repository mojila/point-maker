package com.pointmaker.app.domain

object AnnouncementText {

    fun voiceStatus(languageAvailable: Boolean, engineReady: Boolean): String =
        voiceStatus(languageAvailable, engineReady, voiceEnabled = true)

    /**
     * Status suara untuk indikator layar utama.
     *
     * `voiceEnabled = false` berarti pengguna mematikan suara secara sengaja; itu harus terlihat
     * berbeda dari kasus TTS rusak, karena pada kasus rusak pengguna perlu tahu fitur intinya tidak
     * tersedia (PRD §7.9), sedangkan pada kasus bisu aplikasi berperilaku sesuai keinginan pengguna.
     */
    fun voiceStatus(languageAvailable: Boolean, engineReady: Boolean, voiceEnabled: Boolean): String = when {
        !voiceEnabled -> "Suara mati"
        !engineReady -> "Suara tidak tersedia"
        !languageAvailable -> "Suara Bahasa Indonesia tidak tersedia"
        else -> "Suara aktif"
    }

    fun statusBanner(state: MatchState, scoringSide: Side): String = when {
        MatchRules.isGameFinished(state) -> "Pertandingan selesai"
        MatchRules.isSetFinished(state) -> {
            val winner = MatchRules.setWinner(state)
            requireNotNull(winner) { "set selesai tanpa pemenang" }
            "Set ${ScoreFormatter.setOrdinal(state.currentSet)} untuk ${ScoreFormatter.sideName(winner)}"
        }
        MatchRules.isMatchPoint(state, scoringSide) -> "Match point"
        MatchRules.isSetPoint(state, scoringSide) -> "Set point"
        MatchRules.isDeuce(state) -> "Deuce"
        else -> ""
    }

    fun controlLabels(): String = listOf(
        "Tambah poin kiri",
        "Tambah poin kanan",
        "Undo",
        "Reset",
        "Pengaturan",
        "Suara aktif"
    ).joinToString("\n")
}
