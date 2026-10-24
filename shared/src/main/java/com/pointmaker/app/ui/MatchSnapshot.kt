package com.pointmaker.app.ui

import com.pointmaker.app.domain.MatchRules
import com.pointmaker.app.domain.MatchState
import com.pointmaker.app.domain.ScoringPreset
import com.pointmaker.app.domain.Side

/**
 * Bentuk serialisasi datar dari [MatchState] untuk DataStore Preferences.
 *
 * `history` / stack undo sengaja TIDAK dipersistensi: ini keterbatasan yang diterima, sehingga
 * undo tidak tersedia lagi setelah proses aplikasi dimatikan. Pemetaan ini murni Kotlin agar
 * logika coercion dapat diuji unit tanpa Android.
 */
data class MatchStateSnapshot(
    val preset: ScoringPreset = ScoringPreset.DEFAULT,
    val currentSet: Int = 1,
    val leftPoints: Int = 0,
    val rightPoints: Int = 0,
    val setsWonLeft: Int = 0,
    val setsWonRight: Int = 0,
    val completedSets: List<Pair<Int, Int>> = emptyList(),
    val servingSide: Side = Side.LEFT,
    val matchFinished: Boolean = false
) {
    fun toState(): MatchState = MatchState(
        preset = preset,
        currentSet = currentSet.coerceIn(1, MatchRules.MAX_SETS),
        leftPoints = leftPoints.coerceIn(0, preset.hardCap),
        rightPoints = rightPoints.coerceIn(0, preset.hardCap),
        setsWonLeft = setsWonLeft.coerceIn(0, MatchRules.SETS_TO_WIN_MATCH),
        setsWonRight = setsWonRight.coerceIn(0, MatchRules.SETS_TO_WIN_MATCH),
        completedSets = completedSets
            .take(MatchRules.MAX_SETS)
            .map { (left, right) ->
                left.coerceIn(0, preset.hardCap) to right.coerceIn(0, preset.hardCap)
            },
        servingSide = servingSide,
        matchFinished = matchFinished,
        history = emptyList()
    )

    companion object {
        fun fromState(state: MatchState): MatchStateSnapshot = MatchStateSnapshot(
            preset = state.preset,
            currentSet = state.currentSet,
            leftPoints = state.leftPoints,
            rightPoints = state.rightPoints,
            setsWonLeft = state.setsWonLeft,
            setsWonRight = state.setsWonRight,
            completedSets = state.completedSets,
            servingSide = state.servingSide,
            matchFinished = state.matchFinished
        )
    }
}
