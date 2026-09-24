package com.pointmaker.app.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WearScreenShapeTest {

    @Test
    fun `layar bulat dipetakan ke ROUND`() {
        assertEquals(WatchShape.ROUND, WearLayoutPolicy.shapeFor(isScreenRound = true))
    }

    @Test
    fun `layar kotak dipetakan ke SQUARE`() {
        assertEquals(WatchShape.SQUARE, WearLayoutPolicy.shapeFor(isScreenRound = false))
    }

    @Test
    fun `inset layar bulat positif di semua sisi`() {
        val insets = WearLayoutPolicy.insetsFor(WatchShape.ROUND)

        assertTrue(insets.horizontalDp > 0)
        assertTrue(insets.topDp > 0)
        assertTrue(insets.bottomDp > 0)
    }

    @Test
    fun `inset layar bulat berbeda dari layar kotak`() {
        val round = WearLayoutPolicy.insetsFor(WatchShape.ROUND)
        val square = WearLayoutPolicy.insetsFor(WatchShape.SQUARE)

        assertTrue(round.horizontalDp > square.horizontalDp)
        assertTrue(round.topDp > square.topDp)
        assertTrue(round.bottomDp > square.bottomDp)
    }

    @Test
    fun `layar bulat memberi inset tambahan pada kontrol bawah`() {
        val round = WearLayoutPolicy.insetsFor(WatchShape.ROUND)
        val square = WearLayoutPolicy.insetsFor(WatchShape.SQUARE)

        assertTrue(round.controlsExtraHorizontalDp > 0)
        assertEquals(0, square.controlsExtraHorizontalDp)
    }
}
