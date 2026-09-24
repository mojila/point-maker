package com.pointmaker.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MatchSettingsSheet(
    voiceEnabled: Boolean,
    leftLabel: String,
    rightLabel: String,
    onVoiceEnabledChange: (Boolean) -> Unit,
    onLeftLabelChange: (String) -> Unit,
    onRightLabelChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        SettingsPanelContent(
            voiceEnabled = voiceEnabled,
            leftLabel = leftLabel,
            rightLabel = rightLabel,
            onVoiceEnabledChange = onVoiceEnabledChange,
            onLeftLabelChange = onLeftLabelChange,
            onRightLabelChange = onRightLabelChange,
            onDismiss = onDismiss
        )
    }
}

@Composable
private fun SettingsPanelContent(
    voiceEnabled: Boolean,
    leftLabel: String,
    rightLabel: String,
    onVoiceEnabledChange: (Boolean) -> Unit,
    onLeftLabelChange: (String) -> Unit,
    onRightLabelChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var leftDraft by remember(leftLabel) { mutableStateOf(leftLabel) }
    var rightDraft by remember(rightLabel) { mutableStateOf(rightLabel) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = MatchUiFormatter.settingsTitle(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = MatchUiFormatter.voiceSwitchLabel(),
                style = MaterialTheme.typography.titleLarge
            )
            Switch(
                checked = voiceEnabled,
                onCheckedChange = onVoiceEnabledChange,
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics { contentDescription = MatchUiFormatter.voiceSwitchLabel() }
            )
        }

        OutlinedTextField(
            value = leftDraft,
            onValueChange = {
                leftDraft = it
                onLeftLabelChange(it)
            },
            label = { Text(MatchUiFormatter.leftLabelCaption()) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = 48.dp)
                .semantics { contentDescription = MatchUiFormatter.leftLabelCaption() }
        )

        OutlinedTextField(
            value = rightDraft,
            onValueChange = {
                rightDraft = it
                onRightLabelChange(it)
            },
            label = { Text(MatchUiFormatter.rightLabelCaption()) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = 48.dp)
                .semantics { contentDescription = MatchUiFormatter.rightLabelCaption() }
        )

        TextButton(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = 48.dp)
                .semantics { contentDescription = MatchUiFormatter.closeSettingsDescription() }
        ) {
            Text(text = "Tutup", style = MaterialTheme.typography.titleMedium)
        }
    }
}
