package com.eventoscomunitarios.app.core.components

import com.eventoscomunitarios.app.core.util.localizedMessage

import androidx.compose.ui.res.stringResource
import com.eventoscomunitarios.app.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.eventoscomunitarios.app.core.util.eventFormDate
import com.eventoscomunitarios.app.core.util.eventFormTime
import com.eventoscomunitarios.app.domain.model.Event
import com.eventoscomunitarios.app.domain.model.EventModerationStatus

@Composable
fun ModerationEventCard(
    event: Event,
    compact: Boolean = false,
    onReject: () -> Unit,
    onDetail: () -> Unit,
    onApprove: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onFinish: (() -> Unit)? = null
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!compact && event.photoUri != null) {
                RemoteImage(event.photoUri, stringResource(R.string.ui_photo_of, event.title), Modifier.fillMaxWidth().height(160.dp))
            }
            Text(event.title, style = MaterialTheme.typography.titleMedium)
            Text(event.organizerName, style = MaterialTheme.typography.bodySmall)
            Text(if (event.isFinished) stringResource(R.string.ui_finished) else statusLabel(event.moderationStatus), color = MaterialTheme.colorScheme.primary)
            if (!compact) {
                Text(stringResource(R.string.ui_start_datetime, event.dateLabel, event.timeLabel), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.ui_end_datetime, eventFormDate(event.endsAt), eventFormTime(event.endsAt)), style = MaterialTheme.typography.bodySmall)
                Text("${event.category.localizedName()} · ${event.location}", style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.ui_attendees_count, event.attendeeCount, event.capacity), style = MaterialTheme.typography.bodySmall)
            }
            event.rejectionReason?.let { Text(stringResource(R.string.ui_reason, it), color = MaterialTheme.colorScheme.error) }
            OutlinedButton(onClick = onDetail) { Text(stringResource(R.string.ui_details)) }
            if (event.moderationStatus == EventModerationStatus.PENDING) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onReject) { Text(stringResource(R.string.ui_reject)) }
                    Button(onClick = onApprove) { Text(stringResource(R.string.ui_approve)) }
                }
            }
            if (onDelete != null || onFinish != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onFinish != null && event.moderationStatus == EventModerationStatus.APPROVED && !event.isFinished && event.startsAt <= System.currentTimeMillis()) {
                        TextButton(onClick = onFinish) { Text(stringResource(R.string.ui_finish)) }
                    }
                    if (onDelete != null) {
                        TextButton(onClick = onDelete) { Text(stringResource(R.string.ui_delete), color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

@Composable
fun ModerationActionDialog(
    title: String,
    description: String,
    reason: String? = null,
    error: String? = null,
    onReasonChange: (String) -> Unit = {},
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(description)
                if (reason != null) {
                    OutlinedTextField(
                        value = reason, onValueChange = onReasonChange,
                        label = { Text(stringResource(R.string.ui_rejection_reason)) }, minLines = 3,
                        isError = error != null,
                        supportingText = error?.let { message -> { Text(localizedMessage(message)) } },
                        modifier = Modifier.fillMaxWidth().testTag("rejection_reason")
                    )
                } else if (error != null) Text(localizedMessage(error), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.ui_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) } }
    )
}

@Composable
private fun statusLabel(status: EventModerationStatus): String = when (status) {
    EventModerationStatus.PENDING -> stringResource(R.string.ui_pending)
    EventModerationStatus.APPROVED -> stringResource(R.string.ui_approved)
    EventModerationStatus.REJECTED -> stringResource(R.string.ui_rejected)
}
