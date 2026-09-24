package com.pointmaker.app.ui

import com.pointmaker.app.domain.MatchRules
import com.pointmaker.app.domain.MatchState
import com.pointmaker.app.domain.ScoreFormatter
import com.pointmaker.app.domain.Side

enum class SetMarkerState {
    WON_LEFT,
    WON_RIGHT,
    CURRENT,
    PENDING
}

/**
 * Status satu set pada mini-score: set ke-1, ke-2, atau ke-3.
 *
 * [points] terisi hanya untuk set yang sudah selesai (skor akhirnya tercatat di
 * `completedSets`); set yang sedang berjalan atau belum dimainkan bernilai `null`.
 */
data class SetMarker(
    val number: Int,
    val state: SetMarkerState,
    val points: Pair<Int, Int>? = null
)

object MatchUiFormatter {

    fun setHeader(state: MatchState): String = "Set ke-${state.currentSet}"

    fun bestOfLabel(): String = "Best of 3"

    fun scoreText(points: Int): String = points.coerceAtLeast(0).toString()

    fun canAddPoint(state: MatchState): Boolean =
        !state.matchFinished && !MatchRules.isSetFinished(state) && !MatchRules.isGameFinished(state)

    fun canStartNextSet(state: MatchState): Boolean =
        MatchRules.isSetFinished(state) && !MatchRules.isGameFinished(state)

    fun serveIndicator(state: MatchState, label: String = sideLabel(state.servingSide)): String =
        "Servis: $label"

    fun sideLabel(side: Side): String = if (side == Side.LEFT) "Kiri" else "Kanan"

    fun setMarkers(state: MatchState): List<SetMarker> =
        (1..MatchRules.MAX_SETS).map { number ->
            val completed = completedAt(state, number)
            when {
                completed != null && completed.first > completed.second ->
                    SetMarker(number, SetMarkerState.WON_LEFT, completed)

                completed != null ->
                    SetMarker(number, SetMarkerState.WON_RIGHT, completed)

                number == state.currentSet && state.currentSet in 1..MatchRules.MAX_SETS ->
                    SetMarker(number, SetMarkerState.CURRENT)

                else -> SetMarker(number, SetMarkerState.PENDING)
            }
        }

    fun setMarkerText(marker: SetMarker): String = when (marker.state) {
        SetMarkerState.WON_LEFT -> "${marker.points?.first ?: "-"}"
        SetMarkerState.WON_RIGHT -> "${marker.points?.second ?: "-"}"
        SetMarkerState.CURRENT -> "berjalan"
        SetMarkerState.PENDING -> "-"
    }

    fun setCounter(state: MatchState): String = "${state.setsWonLeft} - ${state.setsWonRight}"

    fun scoreDescription(label: String, points: Int): String =
        "Skor $label ${ScoreFormatter.number(points)}"

    fun addPointDescription(label: String): String = "Tambah poin $label"

    fun settingsDescription(): String = "Buka pengaturan"

    fun voiceStatusDescription(status: String): String = "Status suara: $status"

    fun undoDescription(): String = "Batalkan poin terakhir"

    fun resetDescription(): String = "Reset pertandingan"

    fun nextSetDescription(): String = "Lanjut set berikutnya"

    fun confirmResetTitle(): String = "Reset pertandingan?"

    fun confirmResetMessage(): String = "Skor set ini dan set yang sudah selesai akan dihapus."

    fun confirmResetConfirm(): String = "Reset"

    fun confirmResetDismiss(): String = "Batal"

    fun settingsTitle(): String = "Pengaturan"

    fun voiceSwitchLabel(): String = "Suara"

    fun leftLabelCaption(): String = "Label sisi kiri"

    fun rightLabelCaption(): String = "Label sisi kanan"

    fun closeSettingsDescription(): String = "Tutup pengaturan"

    private fun completedAt(state: MatchState, number: Int): Pair<Int, Int>? =
        state.completedSets.getOrNull(number - 1)
}
