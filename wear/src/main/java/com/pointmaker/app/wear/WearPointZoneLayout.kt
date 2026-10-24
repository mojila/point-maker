package com.pointmaker.app.wear

data class WearPointZoneLayout(
    val showLabel: Boolean,
    val scoreMaxHeight: Int
) {
    companion object {

        fun compute(
            availableHeight: Int,
            labelHeight: Int,
            gapPx: Int,
            minScoreHeight: Int
        ): WearPointZoneLayout {
            val height = availableHeight.coerceAtLeast(0)
            val remaining = height - labelHeight - gapPx
            return if (remaining >= minScoreHeight) {
                WearPointZoneLayout(showLabel = true, scoreMaxHeight = remaining)
            } else {
                WearPointZoneLayout(showLabel = false, scoreMaxHeight = height)
            }
        }
    }
}
