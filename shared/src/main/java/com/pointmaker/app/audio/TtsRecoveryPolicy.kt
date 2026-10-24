package com.pointmaker.app.audio

/**
 * Kebijakan pemulihan TextToSpeech murni (tanpa Android) agar bisa diuji unit.
 *
 * Dua batas yang mengikat:
 * 1. `onInit` yang gagal dicoba ulang dengan jeda berjenjang 1s / 2s / 5s (maksimal 3 percobaan
 *    per instance engine) sebelum engine dibangun ulang.
 * 2. Pembangunan ulang engine otomatis dibatasi `MAX_AUTO_REBUILDS_PER_MATCH` per pertandingan;
 *    setelah budget habis pemulihan hanya boleh lewat aksi manual pengguna (`retryNow`).
 */
object TtsRecoveryPolicy {

    val initRetryDelaysMillis: LongArray = longArrayOf(1_000L, 2_000L, 5_000L)

    const val MAX_AUTO_REBUILDS_PER_MATCH: Int = 2

    fun shouldRetryInit(attemptIndex: Int): Boolean = attemptIndex in initRetryDelaysMillis.indices

    fun delayMillisForAttempt(attemptIndex: Int): Long =
        if (shouldRetryInit(attemptIndex)) initRetryDelaysMillis[attemptIndex] else 0L

    fun shouldAutoRebuild(rebuildsUsed: Int): Boolean =
        rebuildsUsed in 0 until MAX_AUTO_REBUILDS_PER_MATCH

    fun nextRebuildCount(rebuildsUsed: Int): Int =
        (rebuildsUsed + 1).coerceIn(1, MAX_AUTO_REBUILDS_PER_MATCH)
}
