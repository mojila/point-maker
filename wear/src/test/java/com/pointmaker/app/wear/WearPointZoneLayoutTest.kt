package com.pointmaker.app.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearPointZoneLayoutTest {

    @Test
    fun `label tampil saat sisa ruang cukup untuk skor minimum`() {
        val layout = WearPointZoneLayout.compute(
            availableHeight = 200,
            labelHeight = 40,
            gapPx = 4,
            minScoreHeight = 60
        )

        assertTrue(layout.showLabel)
        assertEquals(156, layout.scoreMaxHeight)
    }

    @Test
    fun `label disembunyikan dan skor memakai seluruh tinggi saat ruang sempit`() {
        val layout = WearPointZoneLayout.compute(
            availableHeight = 90,
            labelHeight = 40,
            gapPx = 4,
            minScoreHeight = 60
        )

        assertFalse(layout.showLabel)
        assertEquals(90, layout.scoreMaxHeight)
    }

    @Test
    fun `label tetap tampil saat sisa ruang persis sama dengan skor minimum`() {
        val layout = WearPointZoneLayout.compute(
            availableHeight = 104,
            labelHeight = 40,
            gapPx = 4,
            minScoreHeight = 60
        )

        assertTrue(layout.showLabel)
        assertEquals(60, layout.scoreMaxHeight)
    }

    @Test
    fun `tinggi skor tidak pernah negatif`() {
        val layout = WearPointZoneLayout.compute(
            availableHeight = 0,
            labelHeight = 40,
            gapPx = 4,
            minScoreHeight = 60
        )

        assertFalse(layout.showLabel)
        assertEquals(0, layout.scoreMaxHeight)
    }
}
