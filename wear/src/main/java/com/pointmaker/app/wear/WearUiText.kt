package com.pointmaker.app.wear

import com.pointmaker.app.domain.MatchState
import com.pointmaker.app.ui.MatchUiFormatter
import com.pointmaker.app.ui.SetMarkerState

object WearUiText {

    fun miniScore(state: MatchState): String =
        MatchUiFormatter.setMarkers(state).joinToString(" \u00B7 ") { marker ->
            val points = marker.points
            when {
                points == null -> "S${marker.number}"
                marker.state == SetMarkerState.WON_LEFT -> "S${marker.number} ${points.first}-${points.second}"
                else -> "S${marker.number} ${points.first}-${points.second}"
            }
        }

    fun compactScore(state: MatchState): String =
        MatchUiFormatter.setMarkers(state)
            .mapNotNull { marker ->
                val points = marker.points ?: return@mapNotNull null
                "S${marker.number} ${points.first}-${points.second}"
            }
            .joinToString(" \u00B7 ")

    fun headerLine(state: MatchState): String =
        listOfNotNull(
            MatchUiFormatter.setHeader(state),
            compactScore(state).takeIf { it.isNotEmpty() }
        ).joinToString(" \u00B7 ")

    fun statusLine(state: MatchState, voiceStatus: String): String =
        "$voiceStatus \u00B7 ${MatchUiFormatter.presetLabel(state)}"

    fun zoneDescription(label: String, points: Int): String =
        "${MatchUiFormatter.addPointDescription(label)}, skor $points"
}
