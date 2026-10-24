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

    private fun stateAt(
        left: Int,
        right: Int,
        setsLeft: Int = 0,
        setsRight: Int = 0,
        currentSet: Int = 1,
        preset: ScoringPreset = ScoringPreset.TWENTY_ONE
    ) = MatchState(
        preset = preset,
        currentSet = currentSet,
        leftPoints = left,
        rightPoints = right,
        setsWonLeft = setsLeft,
        setsWonRight = setsRight
    )

    private fun initial21() = MatchState(preset = ScoringPreset.TWENTY_ONE)

    private fun initial15() = MatchState(preset = ScoringPreset.FIFTEEN)

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
        var state = initial21()
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
        var state = rally(initial21(), Side.RIGHT, 21)

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
        val initial = initial21()
        assertEquals(initial, MatchRules.undo(initial))
    }

    @Test
    fun `undo membatalkan poin biasa satu langkah`() {
        var state = initial21()
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
        var state = rally(initial21(), Side.LEFT, 21)
        state = MatchRules.startNextSet(state)
        state = rally(state, Side.RIGHT, 5)

        val reset = initial21()

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
        val leftWins = rally(initial21(), Side.LEFT, 21)
        assertEquals(Side.LEFT, leftWins.servingSide)
        assertEquals(Side.LEFT, MatchRules.startNextSet(leftWins).servingSide)

        val rightWins = rally(initial21(), Side.RIGHT, 21)
        assertEquals(Side.RIGHT, rightWins.servingSide)
        assertEquals(Side.RIGHT, MatchRules.startNextSet(rightWins).servingSide)
    }

    @Test
    fun `lastSetWinner mengembalikan pemenang set yang baru selesai`() {
        val state = rally(initial21(), Side.LEFT, 21)
        assertEquals(Side.LEFT, MatchRules.lastSetWinner(state))

        val next = MatchRules.startNextSet(state)
        assertEquals(Side.LEFT, MatchRules.lastSetWinner(next))
        assertNull(MatchRules.matchWinner(next))
    }

    @Test
    fun `state asli tidak termutasi oleh addPoint`() {
        val before = initial21()
        val after = MatchRules.addPoint(before, Side.LEFT)

        assertEquals(0, before.leftPoints)
        assertTrue(before.history.isEmpty())
        assertEquals(1, after.leftPoints)
        assertEquals(1, after.history.size)
    }

    @Test
    fun `history tidak menyimpan riwayat secara rekursif`() {
        var state = initial21()
        repeat(5) { state = MatchRules.addPoint(state, Side.LEFT) }

        assertEquals(5, state.history.size)
        assertTrue(state.history.all { it.history.isEmpty() })
    }

    @Test
    fun `set tiga dimulai dari nol dan pertandingan tiga set`() {
        var state = rally(initial21(), Side.LEFT, 21)
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

    @Test
    fun `preset 15 deuce 14-14 belum mengakhiri set`() {
        val deuce = stateAt(14, 14, preset = ScoringPreset.FIFTEEN)
        assertTrue(MatchRules.isDeuce(deuce))

        val after = MatchRules.addPoint(deuce, Side.LEFT)

        assertEquals(15, after.leftPoints)
        assertEquals(14, after.rightPoints)
        assertFalse(MatchRules.isSetFinished(after))
        assertNull(MatchRules.setWinner(after))
    }

    @Test
    fun `preset 15 15-14 belum mengakhiri set`() {
        val led = stateAt(15, 14, preset = ScoringPreset.FIFTEEN)

        assertFalse(MatchRules.isSetFinished(led))
        assertEquals(Side.LEFT, MatchRules.setWinner(stateAt(16, 14, preset = ScoringPreset.FIFTEEN)))
    }

    @Test
    fun `preset 15 16-14 mengakhiri set`() {
        val after = MatchRules.addPoint(stateAt(15, 14, preset = ScoringPreset.FIFTEEN), Side.LEFT)

        assertEquals(16, after.leftPoints)
        assertEquals(14, after.rightPoints)
        assertTrue(MatchRules.isSetFinished(after))
        assertEquals(Side.LEFT, MatchRules.setWinner(after))
        assertEquals(1, after.setsWonLeft)
        assertEquals(listOf(16 to 14), after.completedSets)
    }

    @Test
    fun `preset 15 deuce 20-20 ditutup 21-20 dan tidak pernah 22-20`() {
        val won = MatchRules.addPoint(stateAt(20, 20, preset = ScoringPreset.FIFTEEN), Side.LEFT)

        assertEquals(21, won.leftPoints)
        assertEquals(20, won.rightPoints)
        assertTrue(MatchRules.isSetFinished(won))
        assertEquals(Side.LEFT, MatchRules.setWinner(won))

        val frozen = MatchRules.addPoint(won, Side.LEFT)
        assertSame(won, frozen)
    }

    @Test
    fun `preset 15 best of 3 selesai setelah dua set`() {
        var state = rally(initial15(), Side.LEFT, 15)
        assertEquals(1, state.setsWonLeft)
        assertFalse(state.matchFinished)

        state = MatchRules.startNextSet(state)
        state = rally(state, Side.LEFT, 15)

        assertTrue(state.matchFinished)
        assertEquals(Side.LEFT, MatchRules.matchWinner(state))
        assertEquals(listOf(15 to 0, 15 to 0), state.completedSets)
    }

    @Test
    fun `preset 15 set ketiga hanya bila kedudukan 1-1`() {
        var state = rally(initial15(), Side.LEFT, 15)
        state = MatchRules.startNextSet(state)
        state = rally(state, Side.RIGHT, 15)

        assertEquals(1, state.setsWonLeft)
        assertEquals(1, state.setsWonRight)
        assertFalse(state.matchFinished)

        state = MatchRules.startNextSet(state)
        assertEquals(3, state.currentSet)

        state = rally(state, Side.LEFT, 15)
        assertTrue(state.matchFinished)
        assertEquals(listOf(15 to 0, 0 to 15, 15 to 0), state.completedSets)
    }

    @Test
    fun `set target dan set point mengikuti preset`() {
        assertEquals(15, MatchRules.setTarget(stateAt(14, 13, preset = ScoringPreset.FIFTEEN), Side.LEFT))
        assertTrue(MatchRules.isSetPoint(stateAt(14, 13, preset = ScoringPreset.FIFTEEN), Side.LEFT))
        assertFalse(MatchRules.isSetPoint(stateAt(14, 13, preset = ScoringPreset.FIFTEEN), Side.RIGHT))
        assertEquals(16, MatchRules.setTarget(stateAt(14, 14, preset = ScoringPreset.FIFTEEN), Side.LEFT))
        assertEquals(21, MatchRules.setTarget(stateAt(20, 20, preset = ScoringPreset.FIFTEEN), Side.LEFT))
        assertEquals(21, MatchRules.setTarget(stateAt(20, 18), Side.LEFT))
        assertEquals(30, MatchRules.setTarget(stateAt(29, 29), Side.LEFT))
        assertTrue(MatchRules.isSetPoint(stateAt(20, 18), Side.LEFT))
    }

    @Test
    fun `match point mengikuti preset`() {
        assertTrue(MatchRules.isMatchPoint(stateAt(14, 13, setsLeft = 1, preset = ScoringPreset.FIFTEEN), Side.LEFT))
        assertFalse(MatchRules.isMatchPoint(stateAt(14, 13, preset = ScoringPreset.FIFTEEN), Side.LEFT))
        assertTrue(MatchRules.isMatchPoint(stateAt(20, 18, setsLeft = 1), Side.LEFT))
        assertFalse(MatchRules.isMatchPoint(stateAt(14, 13, setsLeft = 1, preset = ScoringPreset.FIFTEEN), Side.RIGHT))
    }

    @Test
    fun `setPreset diterima saat state bersih`() {
        val clean = MatchState()
        assertEquals(ScoringPreset.FIFTEEN, clean.preset)
        assertTrue(MatchRules.canChangePreset(clean))

        val switched = MatchRules.setPreset(clean, ScoringPreset.TWENTY_ONE)

        assertEquals(ScoringPreset.TWENTY_ONE, switched.preset)
        assertEquals(0, switched.leftPoints)
    }

    @Test
    fun `setPreset ditolak saat pertandingan sudah berjalan`() {
        val cases = listOf(
            stateAt(1, 0),
            stateAt(0, 1),
            stateAt(0, 0, currentSet = 2),
            MatchState(completedSets = listOf(21 to 19), setsWonLeft = 1),
            MatchState(matchFinished = true)
        )

        cases.forEach { state ->
            assertFalse(MatchRules.canChangePreset(state))
            assertSame(state, MatchRules.setPreset(state, ScoringPreset.FIFTEEN))
        }
    }

    @Test
    fun `preset ikut tersimpan di history sehingga undo tidak mengubahnya`() {
        val started = MatchRules.setPreset(MatchState(), ScoringPreset.TWENTY_ONE)
        val after = MatchRules.addPoint(started, Side.LEFT)

        assertEquals(ScoringPreset.TWENTY_ONE, after.preset)
        assertEquals(ScoringPreset.TWENTY_ONE, after.history.single().preset)
        assertEquals(ScoringPreset.TWENTY_ONE, MatchRules.undo(after).preset)
    }

    @Test
    fun `startNextSet mempertahankan preset`() {
        var state = rally(initial15(), Side.LEFT, 15)
        state = MatchRules.startNextSet(state)

        assertEquals(ScoringPreset.FIFTEEN, state.preset)
    }
}
