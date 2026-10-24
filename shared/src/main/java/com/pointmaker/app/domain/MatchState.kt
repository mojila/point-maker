package com.pointmaker.app.domain

data class MatchState(
    val preset: ScoringPreset = ScoringPreset.DEFAULT,
    val currentSet: Int = 1,
    val leftPoints: Int = 0,
    val rightPoints: Int = 0,
    val setsWonLeft: Int = 0,
    val setsWonRight: Int = 0,
    val completedSets: List<Pair<Int, Int>> = emptyList(),
    val servingSide: Side = Side.LEFT,
    val matchFinished: Boolean = false,
    val history: List<MatchState> = emptyList()
) {
    fun pointsOf(side: Side): Int = if (side == Side.LEFT) leftPoints else rightPoints

    fun setsWonOf(side: Side): Int = if (side == Side.LEFT) setsWonLeft else setsWonRight

    fun minimal(): MatchState = copy(history = emptyList())
}
