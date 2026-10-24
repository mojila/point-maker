package com.pointmaker.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pointmaker.app.R
import com.pointmaker.app.audio.PointCuePlayer
import com.pointmaker.app.audio.ScoreCueSequencer
import com.pointmaker.app.domain.AnnouncementText
import com.pointmaker.app.domain.MatchRules
import com.pointmaker.app.domain.MatchState
import com.pointmaker.app.domain.Side
import com.pointmaker.app.tts.ScoreAnnouncer

@Composable
fun MatchScreen(viewModel: MatchViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycleCompat()
    val voiceEnabled by viewModel.voiceEnabled.collectAsStateWithLifecycleCompat()
    val leftLabel by viewModel.leftLabel.collectAsStateWithLifecycleCompat()
    val rightLabel by viewModel.rightLabel.collectAsStateWithLifecycleCompat()

    val announcer = remember { ScoreAnnouncer(viewModel.getApplication()) }
    val cuePlayer = remember { PointCuePlayer(viewModel.getApplication()) }
    val sequencer = remember {
        ScoreCueSequencer(
            playCue = { onComplete ->
                announcer.stop()
                cuePlayer.play(onComplete)
            },
            speak = { texts -> announcer.speak(texts) }
        )
    }
    val latestVoiceEnabled by rememberUpdatedState(voiceEnabled)

    var settingsVisible by remember { mutableStateOf(false) }
    var resetConfirmationVisible by remember { mutableStateOf(false) }
    var announceTick by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.announcements.collect { event ->
            if (latestVoiceEnabled) {
                if (event.playCue) sequencer.onPoint(event.texts) else sequencer.onPlain(event.texts)
            }
            announceTick++
        }
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            cuePlayer.release()
            announcer.shutdown()
        }
    }

    KeepScreenOn()

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
    val showVoiceRetry = voiceEnabled &&
        (announcerStatus.recoveryExhausted || !announcerStatus.engineReady)

    Surface(modifier = Modifier.fillMaxSize()) {
        MatchContent(
            state = state,
            leftLabel = leftLabel,
            rightLabel = rightLabel,
            voiceStatus = voiceStatus,
            showVoiceRetry = showVoiceRetry,
            announceTick = announceTick,
            onAddPoint = viewModel::addPoint,
            onUndo = viewModel::undo,
            onStartNextSet = viewModel::startNextSet,
            onResetRequested = { resetConfirmationVisible = true },
            onRetryVoice = { announcer.retryNow() },
            onOpenSettings = { settingsVisible = true }
        )
    }

    if (settingsVisible) {
        MatchSettingsSheet(
            voiceEnabled = voiceEnabled,
            leftLabel = leftLabel,
            rightLabel = rightLabel,
            preset = state.preset,
            canChangePreset = MatchRules.canChangePreset(state),
            onVoiceEnabledChange = viewModel::setVoiceEnabled,
            onLeftLabelChange = viewModel::setLeftLabel,
            onRightLabelChange = viewModel::setRightLabel,
            onPresetChange = viewModel::setPreset,
            onResetWithPreset = { preset ->
                announcer.resetRecoveryBudget()
                viewModel.reset(preset)
            },
            onDismiss = { settingsVisible = false }
        )
    }

    if (resetConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { resetConfirmationVisible = false },
            title = { Text(text = MatchUiFormatter.confirmResetTitle()) },
            text = { Text(text = MatchUiFormatter.confirmResetMessage()) },
            confirmButton = {
                TextButton(
                    onClick = {
                        announcer.resetRecoveryBudget()
                        viewModel.reset()
                        resetConfirmationVisible = false
                    },
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics { contentDescription = MatchUiFormatter.resetDescription() }
                ) {
                    Text(text = MatchUiFormatter.confirmResetConfirm())
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { resetConfirmationVisible = false },
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics { contentDescription = MatchUiFormatter.confirmResetDismiss() }
                ) {
                    Text(text = MatchUiFormatter.confirmResetDismiss())
                }
            }
        )
    }
}

@Composable
private fun MatchContent(
    state: MatchState,
    leftLabel: String,
    rightLabel: String,
    voiceStatus: String,
    showVoiceRetry: Boolean,
    announceTick: Int,
    onAddPoint: (Side) -> Unit,
    onUndo: () -> Unit,
    onStartNextSet: () -> Unit,
    onResetRequested: () -> Unit,
    onRetryVoice: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val canAdd = MatchUiFormatter.canAddPoint(state)
    val scoringSide = if (state.leftPoints == state.rightPoints) {
        state.servingSide
    } else if (state.leftPoints > state.rightPoints) {
        Side.LEFT
    } else {
        Side.RIGHT
    }
    val banner = AnnouncementText.statusBanner(state, scoringSide)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MatchHeader(
            state = state,
            voiceStatus = voiceStatus,
            showVoiceRetry = showVoiceRetry,
            onRetryVoice = onRetryVoice,
            onOpenSettings = onOpenSettings
        )

        SetMarkersRow(state = state)

        StatusBanner(
            banner = banner,
            canStartNextSet = MatchUiFormatter.canStartNextSet(state),
            onStartNextSet = onStartNextSet
        )

        AddPointButton(
            label = leftLabel,
            points = state.leftPoints,
            enabled = canAdd,
            flashKey = announceTick,
            alternate = true,
            onClick = { onAddPoint(Side.LEFT) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.35f)
        )

        AddPointButton(
            label = rightLabel,
            points = state.rightPoints,
            enabled = canAdd,
            flashKey = announceTick,
            alternate = false,
            onClick = { onAddPoint(Side.RIGHT) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.35f)
        )

        BottomControls(
            state = state,
            canUndo = state.history.isNotEmpty(),
            onUndo = onUndo,
            onReset = onResetRequested
        )
    }
}

@Composable
private fun MatchHeader(
    state: MatchState,
    voiceStatus: String,
    showVoiceRetry: Boolean,
    onRetryVoice: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.mipmap.ic_pointmaker_mark),
            contentDescription = null,
            modifier = Modifier
                .padding(end = 8.dp)
                .size(40.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = MatchUiFormatter.setHeader(state),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = MatchUiFormatter.presetHeaderLabel(state),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics {
                    contentDescription = MatchUiFormatter.presetHeaderDescription(state)
                }
            )
        }

        Text(
            text = MatchUiFormatter.serveIndicator(state),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp),
            textAlign = TextAlign.Center
        )

        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .semantics { contentDescription = MatchUiFormatter.settingsDescription() }
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (voiceStatus == "Suara aktif") "\uD83D\uDD0A" else "\uD83D\uDD07",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(end = 4.dp)
        )
        Text(
            text = voiceStatus,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .semantics {
                    contentDescription = MatchUiFormatter.voiceStatusDescription(voiceStatus)
                }
        )
        if (showVoiceRetry) {
            TextButton(
                onClick = onRetryVoice,
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics { contentDescription = MatchUiFormatter.voiceRetryDescription() }
            ) {
                Text(
                    text = MatchUiFormatter.voiceRetryLabel(),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun SetMarkersRow(state: MatchState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MatchUiFormatter.setMarkers(state).forEach { marker ->
            val container = when (marker.state) {
                SetMarkerState.WON_LEFT, SetMarkerState.WON_RIGHT -> MaterialTheme.colorScheme.primaryContainer
                SetMarkerState.CURRENT -> MaterialTheme.colorScheme.secondaryContainer
                SetMarkerState.PENDING -> MaterialTheme.colorScheme.surfaceVariant
            }
            Surface(
                color = container,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Set ${marker.number}",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = MatchUiFormatter.setMarkerText(marker),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBanner(
    banner: String,
    canStartNextSet: Boolean,
    onStartNextSet: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = banner,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (canStartNextSet) {
                Button(
                    onClick = onStartNextSet,
                    modifier = Modifier
                        .sizeIn(minHeight = 48.dp)
                        .semantics { contentDescription = MatchUiFormatter.nextSetDescription() }
                ) {
                    Text(text = "Lanjut set berikutnya")
                }
            }
        }
    }
}

@Composable
private fun AddPointButton(
    label: String,
    points: Int,
    enabled: Boolean,
    flashKey: Int,
    alternate: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var flash by remember { mutableStateOf(false) }
    LaunchedEffect(flashKey) {
        if (flashKey > 0) {
            flash = true
            kotlinx.coroutines.delay(220)
            flash = false
        }
    }

    val container = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant
        alternate -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val flashed = if (flash) MaterialTheme.colorScheme.primary else container

    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = flashed,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxHeight()
            .semantics {
                contentDescription = "${MatchUiFormatter.addPointDescription(label)}, skor ${points}"
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            ScoreBox(
                points = points,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

@Composable
private fun ScoreBox(points: Int, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val measurer = rememberTextMeasurer()
        val text = MatchUiFormatter.scoreText(points)
        val scoreStyle = LocalTextStyle.current.merge(
            TextStyle(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        )
        val availableWidth = constraints.maxWidth
        val availableHeight = constraints.maxHeight
        val fontSize = remember(measurer, text, scoreStyle, availableWidth, availableHeight) {
            fitScoreFontSize(
                measurer = measurer,
                text = text,
                baseStyle = scoreStyle,
                maxWidth = availableWidth,
                maxHeight = availableHeight
            )
        }
        Text(
            text = text,
            style = scoreStyle.copy(fontSize = fontSize),
            maxLines = 1,
            softWrap = false
        )
    }
}

private fun fitScoreFontSize(
    measurer: TextMeasurer,
    text: String,
    baseStyle: TextStyle,
    maxWidth: Int,
    maxHeight: Int
): TextUnit {
    if (maxWidth <= 0 || maxHeight <= 0) return MIN_SCORE_FONT
    var candidateSp = MAX_SCORE_FONT.value
    while (true) {
        val size = measurer.measure(
            text = AnnotatedString(text),
            style = baseStyle.copy(fontSize = candidateSp.sp),
            maxLines = 1,
            softWrap = false
        ).size
        if (size.width <= maxWidth && size.height <= maxHeight) return candidateSp.sp
        if (candidateSp <= MIN_SCORE_FONT.value) break
        candidateSp = (candidateSp - SCORE_FONT_STEP.value).coerceAtLeast(MIN_SCORE_FONT.value)
    }
    // Lantai 56sp dipertahankan walau hasil pengukuran menyatakan tidak muat: skor harus
    // tetap terbaca dari jarak jauh, dan ScoreBox sudah diberi ruang nyata lewat weight(1f).
    return MIN_SCORE_FONT
}

private val MAX_SCORE_FONT = 112.sp
private val MIN_SCORE_FONT = 56.sp
private val SCORE_FONT_STEP = 4.sp

@Composable
private fun BottomControls(
    state: MatchState,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onUndo,
            enabled = canUndo,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 64.dp)
                .semantics { contentDescription = MatchUiFormatter.undoDescription() }
        ) {
            Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
            Text(text = "  Undo", style = MaterialTheme.typography.titleMedium)
        }

        Button(
            onClick = onReset,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 64.dp)
                .semantics { contentDescription = MatchUiFormatter.resetDescription() }
        ) {
            Text(text = "Reset", style = MaterialTheme.typography.titleMedium)
        }
    }
}
