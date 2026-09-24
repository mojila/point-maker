package com.pointmaker.app.wear

enum class WatchShape { ROUND, SQUARE }

data class WearContentInsets(
    val horizontalDp: Int,
    val topDp: Int,
    val bottomDp: Int,
    val controlsExtraHorizontalDp: Int
)

object WearLayoutPolicy {

    fun shapeFor(isScreenRound: Boolean): WatchShape =
        if (isScreenRound) WatchShape.ROUND else WatchShape.SQUARE

    fun insetsFor(shape: WatchShape): WearContentInsets = when (shape) {
        WatchShape.ROUND -> WearContentInsets(
            horizontalDp = 22,
            topDp = 22,
            bottomDp = 30,
            controlsExtraHorizontalDp = 22
        )
        WatchShape.SQUARE -> WearContentInsets(
            horizontalDp = 10,
            topDp = 6,
            bottomDp = 6,
            controlsExtraHorizontalDp = 0
        )
    }
}
