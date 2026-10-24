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
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.LocalTextStyle
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

    var announcerStatus by remember(announcer) { mutableStateOf(announcer.statusSnapshot()) }
    LaunchedEffect(announcer) {
        while (true) {
            announcerStatus = announcer.statusSnapshot()
            kotlinx.coroutines.delay(500)
        }
    }
    val voiceStatus = AnnouncementText.voiceStatus(
        announcerStatus.languageAvailable,
        announcerStatus.engineReady,
        voiceEnabled
    )

    WearMatchScreen(
        state = state,
        voiceStatus = voiceStatus,
        voiceEnabled = voiceEnabled,
        voiceEngineReady = announcerStatus.engineReady,
        onAddPoint = viewModel::addPoint,
        onUndo = viewModel::undo,
        onStartNextSet = viewModel::startNextSet,
        onReset = {
            announcer.resetRecoveryBudget()
            viewModel.reset()
        },
        onToggleVoice = {
            // Saat suara menyala tetapi engine mati, ketukan berfungsi sebagai "coba lagi".
            // Saat pengguna sengaja membisukan suara, ketukan tetap mengubah preferensi mute
            // (paritas dengan ponsel: tombol "Coba lagi" hanya tampil bila suara menyala).
            if (voiceEnabled && !announcerStatus.engineReady) {
                announcer.retryNow()
            } else {
                viewModel.setVoiceEnabled(!voiceEnabled)
            }
        }
    )
}

@Composable
fun WearMatchScreen(
    state: MatchState,
    voiceStatus: String,
    voiceEnabled: Boolean,
    voiceEngineReady: Boolean,
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
            voiceEngineReady = voiceEngineReady,
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
            text = WearUiText.headerLine(state),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = WearUiText.statusLine(state, voiceStatus),
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

/**
 * Zona poin satu pemain.
 *
 * Prioritas tata letak: angka skor tidak boleh terpotong karena itu informasi paling
 * kritis saat bertanding. Label sisi ("Kiri"/"Kanan") boleh disembunyikan saat ruang
 * vertikal sempit (mis. layar bulat dengan banner panjang); sisi tetap dapat dikenali
 * lewat `contentDescription` dan warna/posisi zona.
 */
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
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val scoreText = MatchUiFormatter.scoreText(points)
            val baseTextStyle = LocalTextStyle.current
            val scoreStyle = remember(baseTextStyle) {
                baseTextStyle.merge(TextStyle(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
            }
            val labelStyle = remember(baseTextStyle) {
                baseTextStyle.merge(TextStyle(fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center))
            }
            val availableWidth = constraints.maxWidth
            val availableHeight = constraints.maxHeight
            val gapPx = with(density) { SCORE_GAP.roundToPx() }
            val minScoreHeight = remember(measurer, scoreStyle) {
                measurer.measure(
                    text = AnnotatedString("0"),
                    style = scoreStyle.copy(fontSize = MIN_SCORE_SP.sp),
                    maxLines = 1,
                    softWrap = false
                ).size.height
            }
            val labelHeight = remember(measurer, label, labelSize, labelStyle) {
                measurer.measure(
                    text = AnnotatedString(label),
                    style = labelStyle.copy(fontSize = labelSize),
                    maxLines = 1,
                    softWrap = false
                ).size.height
            }
            val layout = WearPointZoneLayout.compute(
                availableHeight = availableHeight,
                labelHeight = labelHeight,
                gapPx = gapPx,
                minScoreHeight = minScoreHeight
            )
            val showLabel = layout.showLabel
            val scoreMaxHeight = layout.scoreMaxHeight
            val scoreSize = remember(
                measurer,
                scoreText,
                scoreStyle,
                availableWidth,
                availableHeight,
                scoreMaxHeight
            ) {
                fitScoreFontSize(
                    measurer = measurer,
                    text = scoreText,
                    baseStyle = scoreStyle,
                    maxWidth = availableWidth,
                    maxHeight = scoreMaxHeight,
                    maxSizeSp = (zoneHeightDp * 0.46f).coerceIn(24f, 72f)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (showLabel) {
                    Text(
                        text = label,
                        fontSize = labelSize,
                        fontWeight = FontWeight.SemiBold,
                        color = content,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.height(SCORE_GAP))
                }
                Text(
                    text = scoreText,
                    style = scoreStyle.copy(fontSize = scoreSize),
                    color = content,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

private fun fitScoreFontSize(
    measurer: TextMeasurer,
    text: String,
    baseStyle: TextStyle,
    maxWidth: Int,
    maxHeight: Int,
    maxSizeSp: Float
): TextUnit {
    if (maxWidth <= 0 || maxHeight <= 0) return MIN_SCORE_SP.sp
    var candidate = maxSizeSp
    while (true) {
        val size = measurer.measure(
            text = AnnotatedString(text),
            style = baseStyle.copy(fontSize = candidate.sp),
            maxLines = 1,
            softWrap = false
        ).size
        if (size.width <= maxWidth && size.height <= maxHeight) return candidate.sp
        if (candidate <= MIN_SCORE_SP) break
        candidate = (candidate - SCORE_STEP_SP).coerceAtLeast(MIN_SCORE_SP)
    }
    return MIN_SCORE_SP.sp
}

private const val MIN_SCORE_SP = 18f
private const val SCORE_STEP_SP = 2f
private val SCORE_GAP = 2.dp

@Composable
private fun BottomControls(
    canUndo: Boolean,
    voiceStatus: String,
    voiceEnabled: Boolean,
    voiceEngineReady: Boolean,
    extraHorizontalPadding: Dp,
    onUndo: () -> Unit,
    onToggleVoice: () -> Unit,
    onResetRequested: () -> Unit
) {
    // "Coba lagi" hanya relevan bila pengguna memang ingin suara menyala (paritas dengan ponsel).
    val showRetry = voiceEnabled && !voiceEngineReady
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
                .semantics {
                    contentDescription = if (showRetry) {
                        MatchUiFormatter.voiceRetryDescription()
                    } else {
                        MatchUiFormatter.voiceStatusDescription(voiceStatus)
                    }
                }
        ) {
            Text(
                text = if (showRetry) {
                    MatchUiFormatter.voiceRetryLabel()
                } else {
                    if (voiceEnabled) "Suara on" else "Suara off"
                },
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
