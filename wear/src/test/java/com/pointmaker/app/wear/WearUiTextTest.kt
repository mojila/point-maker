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
    fun `deskripsi zona menyebut aksi dan skor`() {
        assertEquals("Tambah poin Kiri, skor 17", WearUiText.zoneDescription("Kiri", 17))
        assertEquals("Tambah poin Kanan, skor 0", WearUiText.zoneDescription("Kanan", 0))
    }
}
