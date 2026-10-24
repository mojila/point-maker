package com.pointmaker.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsRecoveryPolicyTest {

    @Test
    fun `delay retry berjenjang satu dua lima detik`() {
        assertEquals(listOf(1_000L, 2_000L, 5_000L), TtsRecoveryPolicy.initRetryDelaysMillis.toList())
    }

    @Test
    fun `percobaan pertama sampai ketiga masih boleh retry`() {
        assertTrue(TtsRecoveryPolicy.shouldRetryInit(0))
        assertTrue(TtsRecoveryPolicy.shouldRetryInit(1))
        assertTrue(TtsRecoveryPolicy.shouldRetryInit(2))
    }

    @Test
    fun `percobaan keempat sudah di luar jangkauan`() {
        assertFalse(TtsRecoveryPolicy.shouldRetryInit(3))
    }

    @Test
    fun `percobaan negatif tidak boleh retry`() {
        assertFalse(TtsRecoveryPolicy.shouldRetryInit(-1))
        assertFalse(TtsRecoveryPolicy.shouldRetryInit(Int.MIN_VALUE))
    }

    @Test
    fun `delay sesuai indeks percobaan`() {
        assertEquals(1_000L, TtsRecoveryPolicy.delayMillisForAttempt(0))
        assertEquals(2_000L, TtsRecoveryPolicy.delayMillisForAttempt(1))
        assertEquals(5_000L, TtsRecoveryPolicy.delayMillisForAttempt(2))
    }

    @Test
    fun `delay nol di luar jangkauan`() {
        assertEquals(0L, TtsRecoveryPolicy.delayMillisForAttempt(3))
        assertEquals(0L, TtsRecoveryPolicy.delayMillisForAttempt(-1))
        assertEquals(0L, TtsRecoveryPolicy.delayMillisForAttempt(1_000))
    }

    @Test
    fun `budget rebuild dua kali per pertandingan`() {
        assertEquals(2, TtsRecoveryPolicy.MAX_AUTO_REBUILDS_PER_MATCH)
        assertTrue(TtsRecoveryPolicy.shouldAutoRebuild(0))
        assertTrue(TtsRecoveryPolicy.shouldAutoRebuild(1))
        assertFalse(TtsRecoveryPolicy.shouldAutoRebuild(2))
    }

    @Test
    fun `rebuild negatif dianggap belum memakai budget`() {
        assertFalse(TtsRecoveryPolicy.shouldAutoRebuild(-1))
    }

    @Test
    fun `hitung rebuild naik satu per pemakaian`() {
        assertEquals(1, TtsRecoveryPolicy.nextRebuildCount(0))
        assertEquals(2, TtsRecoveryPolicy.nextRebuildCount(1))
    }

    @Test
    fun `hitung rebuild tidak melewati batas`() {
        assertEquals(2, TtsRecoveryPolicy.nextRebuildCount(2))
        assertEquals(2, TtsRecoveryPolicy.nextRebuildCount(99))
    }

    @Test
    fun `hitung rebuild dari nilai negatif mulai dari satu`() {
        assertEquals(1, TtsRecoveryPolicy.nextRebuildCount(-3))
    }

    @Test
    fun `rebuild budget habis tetap habis setelah dihitung`() {
        var used = 0
        while (TtsRecoveryPolicy.shouldAutoRebuild(used)) {
            used = TtsRecoveryPolicy.nextRebuildCount(used)
        }
        assertEquals(TtsRecoveryPolicy.MAX_AUTO_REBUILDS_PER_MATCH, used)
        assertFalse(TtsRecoveryPolicy.shouldAutoRebuild(used))
    }
}
