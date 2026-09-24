package com.pointmaker.app.ui

import com.pointmaker.app.domain.MatchRules
import com.pointmaker.app.domain.MatchState
import com.pointmaker.app.domain.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchSnapshotTest {

    @Test
    fun roundTripKeepsScoringState() {
        val state = MatchState(
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
        val state = MatchRules.addPoint(MatchRules.initial(), Side.LEFT)
        assertTrue(state.history.isNotEmpty())

        val restored = MatchStateSnapshot.fromState(state).toState()

        assertTrue(restored.history.isEmpty())
    }

    @Test
    fun pointsAreCoercedToValidRange() {
        val restored = MatchStateSnapshot(leftPoints = 99, rightPoints = -5).toState()

        assertEquals(MatchRules.HARD_CAP, restored.leftPoints)
        assertEquals(0, restored.rightPoints)
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
            completedSets = listOf(99 to -4, 21 to 19, 15 to 21, 30 to 29)
        ).toState()

        assertEquals(listOf(30 to 0, 21 to 19, 15 to 21), restored.completedSets)
    }

    @Test
    fun negativeAndHugeValuesNeverLeakThrough() {
        val restored = MatchStateSnapshot(
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
