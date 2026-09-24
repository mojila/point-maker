package com.pointmaker.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchRulesTest {

    private fun rally(state: MatchState, side: Side, times: Int = 1): MatchState {
        var current = state
        repeat(times) { current = MatchRules.addPoint(current, side) }
        return current
    }

    private fun stateAt(left: Int, right: Int, setsLeft: Int = 0, setsRight: Int = 0, currentSet: Int = 1) =
        MatchState(
            currentSet = currentSet,
            leftPoints = left,
            rightPoints = right,
            setsWonLeft = setsLeft,
            setsWonRight = setsRight
        )

    @Test
    fun `deuce 20-20 poin berikutnya tidak mengakhiri set`() {
        val deuce = stateAt(20, 20)
        assertTrue(MatchRules.isDeuce(deuce))

        val after = MatchRules.addPoint(deuce, Side.LEFT)

        assertEquals(21, after.leftPoints)
        assertEquals(20, after.rightPoints)
        assertFalse(MatchRules.isSetFinished(after))
        assertNull(MatchRules.setWinner(after))
        assertEquals(1, after.currentSet)
    }

    @Test
    fun `21-20 poin ke pihak tertinggal tetap berjalan`() {
        val led = stateAt(21, 20)

        val after = MatchRules.addPoint(led, Side.RIGHT)

        assertEquals(21, after.leftPoints)
        assertEquals(21, after.rightPoints)
        assertFalse(MatchRules.isSetFinished(after))
        assertTrue(MatchRules.isDeuce(after) || after.leftPoints == after.rightPoints)
    }

    @Test
    fun `deuce sampai 22-20 menang set`() {
        var state = stateAt(20, 20)
        state = MatchRules.addPoint(state, Side.LEFT)
        state = MatchRules.addPoint(state, Side.LEFT)

        assertEquals(22, state.leftPoints)
        assertEquals(20, state.rightPoints)
        assertTrue(MatchRules.isSetFinished(state))
        assertEquals(Side.LEFT, MatchRules.setWinner(state))
        assertEquals(1, state.setsWonLeft)
        assertEquals(listOf(22 to 20), state.completedSets)
    }

    @Test
    fun `29-29 poin ke-30 menang set 30-29 dan tidak ada poin ke-31`() {
        val state = stateAt(29, 29)

        val won = MatchRules.addPoint(state, Side.LEFT)

        assertEquals(30, won.leftPoints)
        assertEquals(29, won.rightPoints)
        assertTrue(MatchRules.isSetFinished(won))
        assertEquals(Side.LEFT, MatchRules.setWinner(won))

        val frozen = MatchRules.addPoint(won, Side.RIGHT)
        assertEquals(30, frozen.leftPoints)
        assertEquals(29, frozen.rightPoints)
        assertEquals(won, frozen)
    }

    @Test
    fun `set point terdeteksi pada 20-18 dan 29-29`() {
        assertTrue(MatchRules.isSetPoint(stateAt(20, 18), Side.LEFT))
        assertFalse(MatchRules.isSetPoint(stateAt(20, 18), Side.RIGHT))
        assertTrue(MatchRules.isSetPoint(stateAt(29, 29), Side.LEFT))
        assertTrue(MatchRules.isSetPoint(stateAt(29, 29), Side.RIGHT))
        assertFalse(MatchRules.isSetPoint(stateAt(20, 20), Side.LEFT))
        assertTrue(MatchRules.isSetPoint(stateAt(29, 28), Side.LEFT))
    }

    @Test
    fun `match point hanya saat set berikutnya menutup pertandingan`() {
        assertTrue(MatchRules.isMatchPoint(stateAt(20, 18, setsLeft = 1), Side.LEFT))
        assertFalse(MatchRules.isMatchPoint(stateAt(20, 18, setsLeft = 0), Side.LEFT))
        assertFalse(MatchRules.isMatchPoint(stateAt(20, 18, setsLeft = 1), Side.RIGHT))
    }

    @Test
    fun `best of 3 selesai setelah dua set dan addPoint menjadi no-op`() {
        var state = MatchRules.initial()
        state = rally(state, Side.LEFT, 21)
        assertEquals(1, state.setsWonLeft)
        assertFalse(state.matchFinished)

        state = MatchRules.startNextSet(state)
        assertEquals(0, state.leftPoints)
        assertEquals(2, state.currentSet)

        state = rally(state, Side.LEFT, 21)

        assertTrue(state.matchFinished)
        assertTrue(MatchRules.isGameFinished(state))
        assertEquals(Side.LEFT, MatchRules.matchWinner(state))
        assertEquals(2, state.setsWonLeft)

        val frozen = MatchRules.addPoint(state, Side.RIGHT)
        assertSame(state, frozen)
        assertTrue(frozen === state)
    }

    @Test
    fun `set selesai menghentikan poin dan startNextSet tidak destruktif`() {
        var state = rally(MatchRules.initial(), Side.RIGHT, 21)

        assertEquals(Side.RIGHT, MatchRules.setWinner(state))
        assertEquals(1, state.currentSet)
        assertEquals(1, state.setsWonRight)
        assertFalse(state.matchFinished)

        val next = MatchRules.startNextSet(state)
        assertEquals(2, next.currentSet)
        assertEquals(0, next.leftPoints)
        assertEquals(0, next.rightPoints)
        assertEquals(1, next.setsWonRight)
        assertEquals(listOf(0 to 21), next.completedSets)

        val frozen = MatchRules.addPoint(state, Side.LEFT)
        assertEquals(21, frozen.rightPoints)
        assertEquals(0, frozen.leftPoints)
    }

    @Test
    fun `undo mengembalikan state persis sebelumnya termasuk membatalkan set`() {
        var state = rally(stateAt(20, 17), Side.LEFT)
        assertTrue(MatchRules.isSetFinished(state))

        val before = state.history.last()
        val undone = MatchRules.undo(state)

        assertEquals(before, undone)
        assertEquals(20, undone.leftPoints)
        assertEquals(17, undone.rightPoints)
        assertEquals(0, undone.setsWonLeft)
        assertEquals(emptyList<Pair<Int, Int>>(), undone.completedSets)
        assertEquals(1, undone.currentSet)
        assertFalse(MatchRules.isSetFinished(undone))
    }

    @Test
    fun `undo pada state awal tidak berubah`() {
        val initial = MatchRules.initial()
        assertEquals(initial, MatchRules.undo(initial))
    }

    @Test
    fun `undo membatalkan poin biasa satu langkah`() {
        var state = MatchRules.initial()
        state = MatchRules.addPoint(state, Side.LEFT)
        state = MatchRules.addPoint(state, Side.RIGHT)
        state = MatchRules.addPoint(state, Side.RIGHT)

        val undone = MatchRules.undo(state)

        assertEquals(1, undone.leftPoints)
        assertEquals(1, undone.rightPoints)
        assertEquals(2, undone.history.size)
        assertEquals(3, state.history.size)
    }

    @Test
    fun `reset kembali ke 0-0 set 1`() {
        var state = rally(MatchRules.initial(), Side.LEFT, 21)
        state = MatchRules.startNextSet(state)
        state = rally(state, Side.RIGHT, 5)

        val reset = MatchRules.initial()

        assertEquals(0, reset.leftPoints)
        assertEquals(0, reset.rightPoints)
        assertEquals(1, reset.currentSet)
        assertEquals(0, reset.setsWonLeft)
        assertEquals(0, reset.setsWonRight)
        assertEquals(emptyList<Pair<Int, Int>>(), reset.completedSets)
        assertEquals(Side.LEFT, reset.servingSide)
        assertFalse(reset.matchFinished)
        assertTrue(reset.history.isEmpty())
    }

    @Test
    fun `pemenang set servis pertama di set berikutnya`() {
        val leftWins = rally(MatchRules.initial(), Side.LEFT, 21)
        assertEquals(Side.LEFT, leftWins.servingSide)
        assertEquals(Side.LEFT, MatchRules.startNextSet(leftWins).servingSide)

        val rightWins = rally(MatchRules.initial(), Side.RIGHT, 21)
        assertEquals(Side.RIGHT, rightWins.servingSide)
        assertEquals(Side.RIGHT, MatchRules.startNextSet(rightWins).servingSide)
    }

    @Test
    fun `lastSetWinner mengembalikan pemenang set yang baru selesai`() {
        val state = rally(MatchRules.initial(), Side.LEFT, 21)
        assertEquals(Side.LEFT, MatchRules.lastSetWinner(state))

        val next = MatchRules.startNextSet(state)
        assertEquals(Side.LEFT, MatchRules.lastSetWinner(next))
        assertNull(MatchRules.matchWinner(next))
    }

    @Test
    fun `state asli tidak termutasi oleh addPoint`() {
        val before = MatchRules.initial()
        val after = MatchRules.addPoint(before, Side.LEFT)

        assertEquals(0, before.leftPoints)
        assertTrue(before.history.isEmpty())
        assertEquals(1, after.leftPoints)
        assertEquals(1, after.history.size)
    }

    @Test
    fun `history tidak menyimpan riwayat secara rekursif`() {
        var state = MatchRules.initial()
        repeat(5) { state = MatchRules.addPoint(state, Side.LEFT) }

        assertEquals(5, state.history.size)
        assertTrue(state.history.all { it.history.isEmpty() })
    }

    @Test
    fun `set tiga dimulai dari nol dan pertandingan tiga set`() {
        var state = rally(MatchRules.initial(), Side.LEFT, 21)
        state = MatchRules.startNextSet(state)
        state = rally(state, Side.RIGHT, 21)
        assertEquals(1, state.setsWonLeft)
        assertEquals(1, state.setsWonRight)
        assertFalse(state.matchFinished)

        state = MatchRules.startNextSet(state)
        assertEquals(3, state.currentSet)
        assertEquals(0, state.leftPoints)
        assertEquals(Side.RIGHT, state.servingSide)

        state = rally(state, Side.LEFT, 21)
        assertTrue(state.matchFinished)
        assertEquals(Side.LEFT, MatchRules.matchWinner(state))
        assertEquals(listOf(21 to 0, 0 to 21, 21 to 0), state.completedSets)
    }
}
