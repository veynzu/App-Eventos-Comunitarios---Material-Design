package com.eventoscomunitarios.app.core.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eventoscomunitarios.app.R
import com.eventoscomunitarios.app.domain.model.EventAssessment
import com.eventoscomunitarios.app.domain.model.TextAssessment

@Composable
fun TextAnalysisFeedback(assessment: TextAssessment?, onUseSuggestion: () -> Unit) {
    if (assessment == null || assessment.allowed) return
    val focus = remember { BringIntoViewRequester() }
    LaunchedEffect(assessment) { withFrameNanos { }; focus.bringIntoView() }
    Card(Modifier.fillMaxWidth().testTag("ai_text_rejection").bringIntoViewRequester(focus)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.ai_text_rejected), style = MaterialTheme.typography.titleMedium)
            assessment.reasons.forEach { Text(it) }
            assessment.suggestedText?.let {
                Text(stringResource(R.string.ai_suggestion), style = MaterialTheme.typography.labelLarge)
                Text(it)
                TextButton(onClick = onUseSuggestion) { Text(stringResource(R.string.ai_use_suggestion)) }
            }
        }
    }
}

@Composable
fun EventAnalysisFeedback(assessment: EventAssessment?, onUseSuggestion: () -> Unit) {
    TextAnalysisFeedback(assessment?.text, onUseSuggestion)
    if (assessment?.image?.relevant == false) {
        val focus = remember { BringIntoViewRequester() }
        LaunchedEffect(assessment.image) { withFrameNanos { }; focus.bringIntoView() }
        Text(stringResource(R.string.ai_image_rejected, assessment.image.reason),
            color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("ai_image_rejection").bringIntoViewRequester(focus))
    }
}

@Composable
fun DuplicateEventsDialog(
    assessment: EventAssessment,
    onOpenEvent: (Int) -> Unit,
    onConfirmDifferent: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ai_duplicates_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).testTag("ai_duplicate_list"),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.ai_duplicates_help))
                assessment.possibleDuplicates.forEach { duplicate ->
                    val candidate = assessment.candidates.firstOrNull { it.eventId == duplicate.eventId }
                    Text(candidate?.title ?: "#${duplicate.eventId}", style = MaterialTheme.typography.titleSmall)
                    Text(duplicate.reason)
                    TextButton(onClick = { onOpenEvent(duplicate.eventId) }) { Text(stringResource(R.string.ai_open_contribute)) }
                }
                if (assessment.candidatesTruncated) Text(stringResource(R.string.ai_search_truncated))
            }
        },
        confirmButton = { TextButton(onClick = onConfirmDifferent, modifier = Modifier.testTag("ai_confirm_different")) {
            Text(stringResource(R.string.ai_different_event))
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_back)) } }
    )
}
