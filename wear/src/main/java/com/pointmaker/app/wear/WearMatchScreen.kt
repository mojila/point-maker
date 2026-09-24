package com.pointmaker.app.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import androidx.wear.compose.material3.TextButtonDefaults
import com.pointmaker.app.audio.ScoreCueSequencer
import com.pointmaker.app.domain.AnnouncementText
import com.pointmaker.app.domain.MatchState
import com.pointmaker.app.domain.Side
import com.pointmaker.app.ui.MatchUiFormatter

@Composable
fun WearMatchRoute(viewModel: WearMatchViewModel = viewModel()) {
    val context = LocalContext.current
    val announcer = remember { WearScoreAnnouncer(context) }
    val haptics = remember { WearHaptics(context) }
    val cuePlayer = remember { WearPointCuePlayer(context) }
    val sequencer = remember {
        ScoreCueSequencer(
            playCue = { onComplete ->
                announcer.stop()
                cuePlayer.play(onComplete)
            },
            speak = { texts -> announcer.speak(texts) }
        )
    }

    val state by viewModel.state.collectAsState()
    val voiceEnabled by viewModel.voiceEnabled.collectAsState()
    val latestVoiceEnabled by rememberUpdatedState(voiceEnabled)

    LaunchedEffect(Unit) {
        announcer.prewarm()
        viewModel.announcements.collect { event ->
            when (event.feedback) {
                WearFeedback.POINT -> haptics.pointTick()
                WearFeedback.EMPHASIS -> haptics.emphasis()
                WearFeedback.UNDO -> haptics.undoTick()
                WearFeedback.NONE -> Unit
            }
            if (latestVoiceEnabled) {
                if (event.playCue) sequencer.onPoint(event.texts) else sequencer.onPlain(event.texts)
            }
        }
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            cuePlayer.release()
            announcer.shutdown()
        }
    }

    var voiceStatus by remember {
        mutableStateOf(
            AnnouncementText.voiceStatus(
                announcer.isIndonesianAvailable,
                announcer.isEngineReady,
                voiceEnabled
            )
        )
    }
    LaunchedEffect(voiceEnabled) {
        while (true) {
            voiceStatus = AnnouncementText.voiceStatus(
                announcer.isIndonesianAvailable,
                announcer.isEngineReady,
                voiceEnabled
            )
            kotlinx.coroutines.delay(500)
        }
    }

    WearMatchScreen(
        state = state,
        voiceStatus = voiceStatus,
        voiceEnabled = voiceEnabled,
        onAddPoint = viewModel::addPoint,
        onUndo = viewModel::undo,
        onStartNextSet = viewModel::startNextSet,
        onReset = viewModel::reset,
        onToggleVoice = { viewModel.setVoiceEnabled(!voiceEnabled) }
    )
}

@Composable
fun WearMatchScreen(
    state: MatchState,
    voiceStatus: String,
    voiceEnabled: Boolean,
    onAddPoint: (Side) -> Unit,
    onUndo: () -> Unit,
    onStartNextSet: () -> Unit,
    onReset: () -> Unit,
    onToggleVoice: () -> Unit
) {
    var resetConfirmationVisible by remember { mutableStateOf(false) }
    val canAdd = MatchUiFormatter.canAddPoint(state)
    val canUndo = state.history.isNotEmpty()
    val canStartNextSet = MatchUiFormatter.canStartNextSet(state)
    val scoringSide = if (state.leftPoints == state.rightPoints) {
        state.servingSide
    } else if (state.leftPoints > state.rightPoints) {
        Side.LEFT
    } else {
        Side.RIGHT
    }
    val banner = AnnouncementText.statusBanner(state, scoringSide)
    val shape = WearLayoutPolicy.shapeFor(
        LocalConfiguration.current.isScreenRound
    )
    val insets = WearLayoutPolicy.insetsFor(shape)
    val round = shape == WatchShape.ROUND

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = insets.horizontalDp.dp,
                end = insets.horizontalDp.dp,
                top = insets.topDp.dp,
                bottom = insets.bottomDp.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Header(state = state, voiceStatus = voiceStatus)

        if (banner.isNotEmpty()) {
            Text(
                text = banner,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
            )
        }

        if (canStartNextSet) {
            Button(
                onClick = onStartNextSet,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = insets.controlsExtraHorizontalDp.dp,
                        end = insets.controlsExtraHorizontalDp.dp,
                        top = 2.dp,
                        bottom = 2.dp
                    )
                    .semantics { contentDescription = MatchUiFormatter.nextSetDescription() }
            ) {
                Text(text = "Lanjut set berikutnya", fontSize = 12.sp)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 4.dp)
        ) {
            PointZone(
                side = Side.LEFT,
                label = MatchUiFormatter.sideLabel(Side.LEFT),
                points = state.leftPoints,
                enabled = canAdd,
                cornerRadius = if (round) 32.dp else 24.dp,
                onClick = { onAddPoint(Side.LEFT) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )

            Spacer(
                modifier = Modifier
                    .width(20.dp)
                    .fillMaxHeight()
            )

            PointZone(
                side = Side.RIGHT,
                label = MatchUiFormatter.sideLabel(Side.RIGHT),
                points = state.rightPoints,
                enabled = canAdd,
                cornerRadius = if (round) 32.dp else 24.dp,
                onClick = { onAddPoint(Side.RIGHT) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }

        BottomControls(
            canUndo = canUndo,
            voiceStatus = voiceStatus,
            voiceEnabled = voiceEnabled,
            extraHorizontalPadding = insets.controlsExtraHorizontalDp.dp,
            onUndo = onUndo,
            onToggleVoice = onToggleVoice,
            onResetRequested = { resetConfirmationVisible = true }
        )
    }

    if (resetConfirmationVisible) {
        AlertDialog(
            visible = true,
            onDismissRequest = { resetConfirmationVisible = false },
            title = { Text(text = MatchUiFormatter.confirmResetTitle()) },
            text = { Text(text = MatchUiFormatter.confirmResetMessage()) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onReset()
                        resetConfirmationVisible = false
                    },
                    modifier = Modifier.semantics { contentDescription = MatchUiFormatter.resetDescription() }
                ) {
                    Text(text = MatchUiFormatter.confirmResetConfirm())
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { resetConfirmationVisible = false },
                    modifier = Modifier.semantics { contentDescription = MatchUiFormatter.confirmResetDismiss() }
                ) {
                    Text(text = MatchUiFormatter.confirmResetDismiss())
                }
            }
        )
    }
}

@Composable
private fun Header(state: MatchState, voiceStatus: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "${MatchUiFormatter.setHeader(state)} \u00B7 ${WearUiText.miniScore(state)}",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = voiceStatus,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics {
                contentDescription = MatchUiFormatter.voiceStatusDescription(voiceStatus)
            }
        )
    }
}

@Composable
private fun PointZone(
    side: Side,
    label: String,
    points: Int,
    enabled: Boolean,
    cornerRadius: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var previousPoints by remember { mutableIntStateOf(points) }
    var flash by remember { mutableStateOf(false) }

    LaunchedEffect(points) {
        val increased = points > previousPoints
        previousPoints = points
        if (increased) {
            flash = true
            kotlinx.coroutines.delay(200)
            flash = false
        }
    }

    val base = when {
        !enabled -> MaterialTheme.colorScheme.surfaceContainerHigh
        side == Side.LEFT -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val container = if (flash) MaterialTheme.colorScheme.primary else base
    val content = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        side == Side.LEFT -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onTertiaryContainer
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(container)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = WearUiText.zoneDescription(label, points) }
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (enabled) 1f else 0.7f),
            contentAlignment = Alignment.Center
        ) {
            val zoneHeightDp = maxHeight.value
            val labelSize = (zoneHeightDp * 0.16f).coerceIn(11f, 14f).sp
            val scoreSize = (zoneHeightDp * 0.46f).coerceIn(24f, 72f).sp

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = label,
                    fontSize = labelSize,
                    fontWeight = FontWeight.SemiBold,
                    color = content,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = MatchUiFormatter.scoreText(points),
                    fontSize = scoreSize,
                    fontWeight = FontWeight.Bold,
                    color = content,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun BottomControls(
    canUndo: Boolean,
    voiceStatus: String,
    voiceEnabled: Boolean,
    extraHorizontalPadding: Dp,
    onUndo: () -> Unit,
    onToggleVoice: () -> Unit,
    onResetRequested: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = extraHorizontalPadding,
                end = extraHorizontalPadding,
                top = 2.dp
            ),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = onUndo,
            enabled = canUndo,
            colors = TextButtonDefaults.textButtonColors(),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .semantics { contentDescription = MatchUiFormatter.undoDescription() }
        ) {
            Text(text = "Undo", fontSize = 11.sp, maxLines = 1)
        }

        TextButton(
            onClick = onToggleVoice,
            colors = TextButtonDefaults.textButtonColors(),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .semantics { contentDescription = MatchUiFormatter.voiceStatusDescription(voiceStatus) }
        ) {
            Text(
                text = if (voiceEnabled) "Suara on" else "Suara off",
                fontSize = 11.sp,
                maxLines = 1
            )
        }

        TextButton(
            onClick = onResetRequested,
            colors = TextButtonDefaults.textButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .semantics { contentDescription = MatchUiFormatter.resetDescription() }
        ) {
            Text(text = "Reset", fontSize = 11.sp, maxLines = 1)
        }
    }
}
