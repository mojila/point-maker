package com.pointmaker.app.domain

object MatchRules {

    const val SETS_TO_WIN_MATCH = 2
    const val MAX_SETS = 3

    fun initial(): MatchState = MatchState()

    fun addPoint(state: MatchState, side: Side): MatchState {
        if (state.matchFinished || isSetFinished(state)) return state
        return concludeSetIfNeeded(withPoint(state, side)).pushHistory(state)
    }

    fun scoredOnly(state: MatchState, side: Side): MatchState = withPoint(state, side)

    fun undo(state: MatchState): MatchState =
        if (state.history.isEmpty()) state
        else state.history.last().copy(history = state.history.dropLast(1))

    fun startNextSet(state: MatchState): MatchState {
        if (!isSetFinished(state) || state.matchFinished) return state
        return state.copy(currentSet = state.currentSet + 1, leftPoints = 0, rightPoints = 0).pushHistory(state)
    }

    fun canChangePreset(state: MatchState): Boolean =
        state.leftPoints == 0 &&
            state.rightPoints == 0 &&
            state.currentSet == 1 &&
            state.completedSets.isEmpty() &&
            !state.matchFinished

    fun setPreset(state: MatchState, preset: ScoringPreset): MatchState =
        if (!canChangePreset(state) || state.preset == preset) state else state.copy(preset = preset)

    fun isSetFinished(state: MatchState): Boolean = setWinner(state) != null

    fun setWinner(state: MatchState): Side? {
        val left = state.leftPoints
        val right = state.rightPoints
        val preset = state.preset
        if (left >= preset.hardCap) return Side.LEFT
        if (right >= preset.hardCap) return Side.RIGHT
        if (left >= preset.pointsToWin && left - right >= 2) return Side.LEFT
        if (right >= preset.pointsToWin && right - left >= 2) return Side.RIGHT
        return null
    }

    fun isGameFinished(state: MatchState): Boolean =
        state.matchFinished || state.setsWonLeft >= SETS_TO_WIN_MATCH || state.setsWonRight >= SETS_TO_WIN_MATCH

    fun lastSetWinner(state: MatchState): Side? =
        state.completedSets.lastOrNull()
            ?.let { (left, right) -> if (left > right) Side.LEFT else Side.RIGHT }

    fun matchWinner(state: MatchState): Side? = when {
        state.setsWonLeft >= SETS_TO_WIN_MATCH -> Side.LEFT
        state.setsWonRight >= SETS_TO_WIN_MATCH -> Side.RIGHT
        else -> null
    }

    fun setTarget(state: MatchState, side: Side): Int =
        minOf(state.preset.hardCap, maxOf(state.preset.pointsToWin, state.pointsOf(side.opponent()) + 2))

    fun pointsToWinSet(state: MatchState, side: Side): Int =
        setTarget(state, side) - state.pointsOf(side)

    fun isSetPoint(state: MatchState, side: Side): Boolean =
        !isSetFinished(state) && isSetPointBeforeSetClosure(state, side)

    fun isMatchPoint(state: MatchState, side: Side): Boolean =
        isSetPoint(state, side) && state.setsWonOf(side) == SETS_TO_WIN_MATCH - 1

    fun isSetPointBeforeSetClosure(state: MatchState, side: Side): Boolean =
        state.pointsOf(side) < setTarget(state, side) && state.pointsOf(side) + 1 >= setTarget(state, side)

    fun isMatchPointBeforeSetClosure(state: MatchState, side: Side): Boolean =
        isSetPointBeforeSetClosure(state, side) && state.setsWonOf(side) == SETS_TO_WIN_MATCH - 1

    fun isDeuce(state: MatchState): Boolean =
        state.leftPoints == state.preset.deucePoints && state.rightPoints == state.preset.deucePoints

    private fun withPoint(state: MatchState, side: Side): MatchState =
        if (side == Side.LEFT) state.copy(leftPoints = state.leftPoints + 1)
        else state.copy(rightPoints = state.rightPoints + 1)

    private fun concludeSetIfNeeded(state: MatchState): MatchState {
        val winner = setWinner(state) ?: return state
        val setsWonLeft = state.setsWonLeft + if (winner == Side.LEFT) 1 else 0
        val setsWonRight = state.setsWonRight + if (winner == Side.RIGHT) 1 else 0
        return state.copy(
            setsWonLeft = setsWonLeft,
            setsWonRight = setsWonRight,
            completedSets = state.completedSets + (state.leftPoints to state.rightPoints),
            servingSide = winner,
            matchFinished = setsWonLeft >= SETS_TO_WIN_MATCH || setsWonRight >= SETS_TO_WIN_MATCH
        )
    }

    private fun MatchState.pushHistory(previous: MatchState): MatchState =
        copy(history = previous.history + previous.minimal())
}
