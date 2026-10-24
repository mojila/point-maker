package com.pointmaker.app.wear

import com.pointmaker.app.domain.MatchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WearUiTextTest {

    @Test
    fun `mini score hanya menampilkan nomor set saat belum ada set selesai`() {
        val text = WearUiText.miniScore(MatchState(currentSet = 2, leftPoints = 5, rightPoints = 3))

        assertEquals("S1 \u00B7 S2 \u00B7 S3", text)
    }

    @Test
    fun `mini score menampilkan skor set yang sudah selesai`() {
        val state = MatchState(
            currentSet = 2,
            leftPoints = 4,
            rightPoints = 1,
            setsWonLeft = 1,
            completedSets = listOf(21 to 18)
        )

        val text = WearUiText.miniScore(state)

        assertTrue(text.contains("S1 21-18"))
        assertTrue(text.contains("S2"))
    }

    @Test
    fun `compact score kosong saat belum ada set selesai`() {
        val state = MatchState(currentSet = 1, leftPoints = 5, rightPoints = 3)

        assertEquals("", WearUiText.compactScore(state))
    }

    @Test
    fun `compact score menampilkan satu set yang sudah selesai`() {
        val state = MatchState(
            currentSet = 2,
            leftPoints = 4,
            rightPoints = 1,
            setsWonLeft = 1,
            completedSets = listOf(21 to 16)
        )

        assertEquals("S1 21-16", WearUiText.compactScore(state))
    }

    @Test
    fun `compact score menampilkan dua set yang sudah selesai`() {
        val state = MatchState(
            currentSet = 3,
            leftPoints = 0,
            rightPoints = 0,
            setsWonLeft = 1,
            setsWonRight = 1,
            completedSets = listOf(21 to 16, 19 to 21)
        )

        assertEquals("S1 21-16 \u00B7 S2 19-21", WearUiText.compactScore(state))
    }

    @Test
    fun `compact score tidak memuat set yang belum dimainkan`() {
        val state = MatchState(
            currentSet = 2,
            leftPoints = 0,
            rightPoints = 0,
            setsWonLeft = 1,
            completedSets = listOf(21 to 16)
        )

        val text = WearUiText.compactScore(state)

        assertTrue(text.contains("S1 21-16"))
        assertTrue(!text.contains("S2"))
        assertTrue(!text.contains("S3"))
    }

    @Test
    fun `baris header pertama hanya memuat set aktif saat belum ada set selesai`() {
        val state = MatchState(currentSet = 1, leftPoints = 3, rightPoints = 2)

        assertEquals("Set ke-1", WearUiText.headerLine(state))
    }

    @Test
    fun `baris header pertama memuat set aktif dan set selesai`() {
        val state = MatchState(
            currentSet = 2,
            leftPoints = 0,
            rightPoints = 0,
            setsWonLeft = 1,
            completedSets = listOf(21 to 16)
        )

        assertEquals("Set ke-2 \u00B7 S1 21-16", WearUiText.headerLine(state))
    }

    @Test
    fun `baris status memuat status suara dan label preset`() {
        val state = MatchState()

        assertEquals(
            "Suara aktif \u00B7 15 poin",
            WearUiText.statusLine(state, "Suara aktif")
        )
    }

    @Test
    fun `deskripsi zona menyebut aksi dan skor`() {
        assertEquals("Tambah poin Kiri, skor 17", WearUiText.zoneDescription("Kiri", 17))
        assertEquals("Tambah poin Kanan, skor 0", WearUiText.zoneDescription("Kanan", 0))
    }
}
