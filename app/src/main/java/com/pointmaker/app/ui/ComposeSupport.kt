package com.pointmaker.app.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.flow.StateFlow

@Composable
fun <T> StateFlow<T>.collectAsStateWithLifecycleCompat(): State<T> = collectAsState()

@Composable
internal fun KeepScreenOn() {
    val view = LocalView.current
    val context = view.context
    DisposableEffect(view) {
        val activity = context as? Activity
        if (activity == null) {
            onDispose { }
        } else {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val controller = WindowCompat.getInsetsController(activity.window, view)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            onDispose {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
}

@Composable
internal fun previewState(
    leftPoints: Int = 0,
    rightPoints: Int = 0,
    setsWonLeft: Int = 0,
    setsWonRight: Int = 0,
    completedSets: List<Pair<Int, Int>> = emptyList(),
    currentSet: Int = 1
) = remember(leftPoints, rightPoints, setsWonLeft, setsWonRight, completedSets, currentSet) {
    com.pointmaker.app.domain.MatchState(
        currentSet = currentSet,
        leftPoints = leftPoints,
        rightPoints = rightPoints,
        setsWonLeft = setsWonLeft,
        setsWonRight = setsWonRight,
        completedSets = completedSets
    )
}
