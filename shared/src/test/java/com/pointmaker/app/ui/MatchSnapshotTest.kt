package com.pointmaker.app.ui

import com.pointmaker.app.domain.MatchRules
import com.pointmaker.app.domain.MatchState
import com.pointmaker.app.domain.ScoringPreset
import com.pointmaker.app.domain.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchSnapshotTest {

    @Test
    fun roundTripKeepsScoringState() {
        val state = MatchState(
            preset = ScoringPreset.TWENTY_ONE,
            currentSet = 2,
            leftPoints = 21,
            rightPoints = 19,
            setsWonLeft = 1,
            setsWonRight = 1,
            completedSets = listOf(21 to 19, 15 to 21),
            servingSide = Side.RIGHT,
            matchFinished = true
        )

        val restored = MatchStateSnapshot.fromState(state).toState()

        assertEquals(state.preset, restored.preset)
        assertEquals(state.currentSet, restored.currentSet)
        assertEquals(state.leftPoints, restored.leftPoints)
        assertEquals(state.rightPoints, restored.rightPoints)
        assertEquals(state.setsWonLeft, restored.setsWonLeft)
        assertEquals(state.setsWonRight, restored.setsWonRight)
        assertEquals(state.completedSets, restored.completedSets)
        assertEquals(state.servingSide, restored.servingSide)
        assertEquals(state.matchFinished, restored.matchFinished)
    }

    @Test
    fun historyIsDropped() {
        val state = MatchRules.addPoint(MatchState(), Side.LEFT)
        assertTrue(state.history.isNotEmpty())

        val restored = MatchStateSnapshot.fromState(state).toState()

        assertTrue(restored.history.isEmpty())
    }

    @Test
    fun presetSurvivesRoundTrip() {
        ScoringPreset.entries.forEach { preset ->
            val restored = MatchStateSnapshot.fromState(MatchState(preset = preset)).toState()

            assertEquals(preset, restored.preset)
        }
    }

    @Test
    fun unknownPresetKeyFallsBackTo15x3() {
        assertEquals(ScoringPreset.FIFTEEN, ScoringPreset.fromStorageKey("99x"))
        assertEquals(ScoringPreset.FIFTEEN, ScoringPreset.fromStorageKey(null))
        assertEquals(ScoringPreset.FIFTEEN, ScoringPreset.fromStorageKey(""))
        assertEquals(ScoringPreset.FIFTEEN, ScoringPreset.fromStorageKey("15x3"))
        assertEquals(ScoringPreset.TWENTY_ONE, ScoringPreset.fromStorageKey("21x3"))
    }

    @Test
    fun missingPresetKeyUses21x3OnlyWhenMatchAlreadyRunning() {
        assertEquals(ScoringPreset.FIFTEEN, ScoringPreset.fromStorage(null, matchInProgress = false))
        assertEquals(ScoringPreset.TWENTY_ONE, ScoringPreset.fromStorage(null, matchInProgress = true))
        assertEquals(ScoringPreset.FIFTEEN, ScoringPreset.fromStorage("15x3", matchInProgress = true))
        assertEquals(ScoringPreset.TWENTY_ONE, ScoringPreset.fromStorage("21x3", matchInProgress = false))
        assertEquals(ScoringPreset.FIFTEEN, ScoringPreset.fromStorage("tidak-dikenal", matchInProgress = true))
    }

    @Test
    fun pointsAreCoercedToValidRange() {
        val restored = MatchStateSnapshot(
            preset = ScoringPreset.TWENTY_ONE,
            leftPoints = 99,
            rightPoints = -5
        ).toState()

        assertEquals(ScoringPreset.MAX_POINTS, restored.leftPoints)
        assertEquals(0, restored.rightPoints)
    }

    @Test
    fun pointsAreCoercedToPresetHardCap() {
        val fifteen = MatchStateSnapshot(preset = ScoringPreset.FIFTEEN, leftPoints = 99, rightPoints = 99).toState()
        val twentyOne = MatchStateSnapshot(preset = ScoringPreset.TWENTY_ONE, leftPoints = 99, rightPoints = 99).toState()

        assertEquals(21, fifteen.leftPoints)
        assertEquals(21, fifteen.rightPoints)
        assertEquals(30, twentyOne.leftPoints)
        assertEquals(30, twentyOne.rightPoints)
    }

    @Test
    fun completedSetsAreCoercedToPresetHardCap() {
        val restored = MatchStateSnapshot(
            preset = ScoringPreset.FIFTEEN,
            completedSets = listOf(99 to 99)
        ).toState()

        assertEquals(listOf(21 to 21), restored.completedSets)
    }

    @Test
    fun setsWonAreCoercedToValidRange() {
        val restored = MatchStateSnapshot(setsWonLeft = 9, setsWonRight = -3).toState()

        assertEquals(MatchRules.SETS_TO_WIN_MATCH, restored.setsWonLeft)
        assertEquals(0, restored.setsWonRight)
    }

    @Test
    fun currentSetIsCoercedToValidRange() {
        assertEquals(MatchRules.MAX_SETS, MatchStateSnapshot(currentSet = 7).toState().currentSet)
        assertEquals(1, MatchStateSnapshot(currentSet = 0).toState().currentSet)
        assertEquals(1, MatchStateSnapshot(currentSet = -2).toState().currentSet)
    }

    @Test
    fun completedSetsAreCoercedAndBounded() {
        val restored = MatchStateSnapshot(
            preset = ScoringPreset.TWENTY_ONE,
            completedSets = listOf(99 to -4, 21 to 19, 15 to 21, 30 to 29)
        ).toState()

        assertEquals(listOf(30 to 0, 21 to 19, 15 to 21), restored.completedSets)
    }

    @Test
    fun negativeAndHugeValuesNeverLeakThrough() {
        val restored = MatchStateSnapshot(
            preset = ScoringPreset.TWENTY_ONE,
            currentSet = 99,
            leftPoints = -100,
            rightPoints = 1000,
            setsWonLeft = 99,
            setsWonRight = -99
        ).toState()

        assertEquals(3, restored.currentSet)
        assertEquals(0, restored.leftPoints)
        assertEquals(30, restored.rightPoints)
        assertEquals(2, restored.setsWonLeft)
        assertEquals(0, restored.setsWonRight)
    }
}
