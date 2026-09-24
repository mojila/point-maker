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

    fun zoneDescription(label: String, points: Int): String =
        "${MatchUiFormatter.addPointDescription(label)}, skor $points"
}
