package com.eventoscomunitarios.app.features.event_detail

import com.eventoscomunitarios.app.core.util.localizedMessage

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.eventoscomunitarios.app.core.components.LoadingIndicator
import com.eventoscomunitarios.app.core.components.localizedName
import androidx.compose.ui.platform.LocalConfiguration
import com.eventoscomunitarios.app.core.util.initials
import com.eventoscomunitarios.app.domain.model.EventCategory
import com.eventoscomunitarios.app.domain.model.EventModerationStatus
import com.eventoscomunitarios.app.domain.model.EventRegistrationStatus
import com.eventoscomunitarios.app.ui.theme.TextoPrincipal
import com.eventoscomunitarios.app.ui.theme.TextoSecundario
import java.util.Date
import java.util.Locale
import com.eventoscomunitarios.app.R
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    viewModel: EventDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onConfirm: (Int) -> Unit,
    onRelaunch: (Int) -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val snackbar = snackbarHostState
    LaunchedEffect(state.attendanceConfirmedEventId) {
        state.attendanceConfirmedEventId?.let { eventId ->
            viewModel.clearAttendanceNavigation()
            onConfirm(eventId)
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    val event = state.event
    val headerBackground = when (event?.category) {
        EventCategory.SPORTS -> Color(0xFFD9ECFF)
        EventCategory.CULTURE -> Color(0xFFEEDFFF)
        else -> Color(0xFFDDEFEA)
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        TopAppBar(
            title = { Text(stringResource(R.string.ui_detail_title)) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.ui_back_alt))
                }
            },
            actions = {
                if (event != null) {
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT,
                                context.getString(R.string.ui_share_message, event.title, formatDateTime(event.startsAt, locale), formatDateTime(event.endsAt, locale), event.location))
                        }
                        context.startActivity(Intent.createChooser(intent, context.getString(R.string.ui_share_event)))
                    }) { Icon(Icons.Default.Share, contentDescription = stringResource(R.string.ui_share)) }
                    IconButton(onClick = viewModel::toggleFavorite, enabled = state.canFavorite && !state.isSaving) {
                        Icon(
                            if (state.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (state.isFavorite) stringResource(R.string.ui_remove_favorite) else stringResource(R.string.ui_add_favorite),
                            tint = if (state.isFavorite) Color(0xFFD94D68) else TextoPrincipal
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = headerBackground)
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isLoading -> LoadingIndicator()
                event == null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(localizedMessage(state.error ?: stringResource(R.string.ui_event_unavailable)))
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (state.isSaving) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    state.syncError?.let { message ->
                        item { Text(localizedMessage(message), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 24.dp)) }
                    }
                    event.photoUri?.takeIf { it.isNotBlank() }?.let { photo ->
                        item {
                            AsyncImage(model = photo, contentDescription = stringResource(R.string.ui_photo_of, event.title),
                                modifier = Modifier.fillMaxWidth().height(220.dp), contentScale = ContentScale.Crop)
                        }
                    }
                    item {
                        Column(Modifier.fillMaxWidth().background(headerBackground).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(event.category.localizedName(), style = MaterialTheme.typography.labelLarge)
                            Text(event.title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextoPrincipal)
                            Text(when {
                                event.moderationStatus == EventModerationStatus.PENDING -> stringResource(R.string.ui_moderation_pending)
                                event.moderationStatus == EventModerationStatus.REJECTED -> stringResource(R.string.ui_post_rejected)
                                event.isFinished || event.endsAt <= System.currentTimeMillis() -> stringResource(R.string.ui_event_finished)
                                event.startsAt <= System.currentTimeMillis() -> stringResource(R.string.ui_event_started)
                                else -> stringResource(R.string.ui_post_approved)
                            }, fontWeight = FontWeight.SemiBold)
                            event.rejectionReason?.takeIf { it.isNotBlank() }?.let {
                                Text(stringResource(R.string.ui_rejection, it), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    item {
                        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            InfoRow(stringResource(R.string.ui_start), formatDateTime(event.startsAt, locale))
                            InfoRow(stringResource(R.string.ui_end), formatDateTime(event.endsAt, locale))
                            InfoRow(stringResource(R.string.ui_place), event.location)
                            InfoRow(stringResource(R.string.ui_attendance), stringResource(R.string.ui_attendance_count, event.attendeeCount, event.capacity))
                            Text(stringResource(R.string.ui_waiting_count, state.waitlistCount), color = TextoSecundario)
                            Text(stringResource(R.string.ui_waitlist_promotion),
                                style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
                            HorizontalDivider()
                        }
                    }
                    item {
                        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.ui_organized_by), style = MaterialTheme.typography.titleMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(40.dp).background(Color(0xFFEEDFFF), CircleShape), contentAlignment = Alignment.Center) {
                                    Text(event.organizerName.initials(), color = Color(0xFF6941A5), fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(event.organizerName, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                if (state.canFollow) {
                                    TextButton(onClick = viewModel::toggleFollow, enabled = !state.isSaving) { Text(if (state.isFollowing) stringResource(R.string.ui_following) else stringResource(R.string.ui_follow)) }
                                }
                            }
                            Text(stringResource(R.string.ui_description), style = MaterialTheme.typography.titleMedium)
                            Text(event.description.ifBlank { stringResource(R.string.ui_no_description) }, color = TextoSecundario)
                            HorizontalDivider()
                        }
                    }
                    item {
                        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.ui_community_rating), style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                FilterChip(
                                    selected = state.myVote == 1, enabled = state.canVote && !state.isSaving,
                                    onClick = { viewModel.vote(1) },
                                    label = { Text(stringResource(R.string.ui_positive_votes, state.positiveVotes)) },
                                    leadingIcon = { Icon(Icons.Default.ThumbUp, contentDescription = null, Modifier.size(18.dp)) }
                                )
                                FilterChip(
                                    selected = state.myVote == -1, enabled = state.canVote && !state.isSaving,
                                    onClick = { viewModel.vote(-1) },
                                    label = { Text(stringResource(R.string.ui_negative_votes, state.negativeVotes)) },
                                    leadingIcon = { Icon(Icons.Default.ThumbDown, contentDescription = null, Modifier.size(18.dp)) }
                                )
                            }
                            Text(when {
                                state.canVote -> stringResource(R.string.ui_vote_help)
                                event.moderationStatus != EventModerationStatus.APPROVED -> stringResource(R.string.ui_votes_pending)
                                else -> stringResource(R.string.ui_author_vote_help)
                            },
                                style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
                            HorizontalDivider()
                        }
                    }
                    item {
                        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (state.canRelaunch) {
                                OutlinedButton(onClick = { onRelaunch(event.id) }, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.relaunch_event))
                                }
                            }
                            Text(stringResource(R.string.ui_comments_count, state.comments.size), style = MaterialTheme.typography.titleMedium)
                            if (state.canComment || state.canContribute) {
                                if (state.canContribute) Text(stringResource(R.string.ai_contribution_help))
                                OutlinedTextField(
                                    value = state.commentText, onValueChange = viewModel::onCommentChange,
                                    enabled = !state.isSaving,
                                    modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5,
                                    label = { Text(stringResource(R.string.ui_write_comment)) },
                                    isError = state.commentError != null,
                                    supportingText = { Text(localizedMessage(state.commentError ?: "${state.commentText.length}/1000")) }
                                )
                                com.eventoscomunitarios.app.core.components.TextAnalysisFeedback(state.textAssessment, viewModel::useSuggestedComment)
                                Button(onClick = { viewModel.addComment(if (locale.language == "en") "en" else "es") }, modifier = Modifier.align(Alignment.End), enabled = state.commentText.isNotBlank() && !state.isSaving) {
                                    Text(stringResource(if (state.isAnalyzingComment) R.string.ai_analyzing_text else if (state.canContribute) R.string.ai_contribution_publish else R.string.ui_publish_comment))
                                }
                            } else {
                                Text(stringResource(R.string.ui_comments_pending), color = TextoSecundario)
                            }
                            if (!state.canComment && !state.canContribute) Text(stringResource(R.string.comments_participants_only), style = MaterialTheme.typography.bodySmall)
                            if (state.comments.isEmpty()) Text(stringResource(R.string.ui_no_comments), color = TextoSecundario)
                        }
                    }
                    items(state.comments, key = { it.id }) { comment ->
                        Card(Modifier.fillMaxWidth().padding(horizontal = 24.dp), shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(comment.authorName, fontWeight = FontWeight.SemiBold)
                                        if (comment.isContribution) Text(stringResource(R.string.ai_contribution), style = MaterialTheme.typography.labelSmall)
                                        Text(formatDateTime(comment.createdAt, locale), style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (comment.userId == state.currentUserId || state.isModerator) {
                                        IconButton(onClick = { viewModel.requestDeleteComment(comment.id) }, enabled = !state.isSaving) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.ui_delete_comment))
                                        }
                                    }
                                }
                                Text(comment.text)
                            }
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
        if (event != null && (state.canRegister || state.registrationStatus != null)) {
            Surface(shadowElevation = 8.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (event.isFree) stringResource(R.string.ui_free_admission) else stringResource(R.string.ui_paid_admission, event.price), fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.ui_places_count, event.attendeeCount, event.capacity), color = TextoSecundario)
                    }
                    if (state.registrationStatus == null) {
                        Button(
                            onClick = viewModel::confirmAttendance,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = state.canRegister && !state.isSaving,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B5B7E))
                        ) {
                            Text(if (event.attendeeCount >= event.capacity) stringResource(R.string.ui_join_waitlist) else stringResource(R.string.ui_confirm_attendance))
                        }
                    } else {
                        Text(
                            if (state.registrationStatus == EventRegistrationStatus.CONFIRMED) stringResource(R.string.ui_attendance_confirmed)
                            else stringResource(R.string.ui_waiting_position, state.waitlistPosition?.toString() ?: "—"),
                            fontWeight = FontWeight.Medium
                        )
                        if (state.canCancelRegistration) {
                            OutlinedButton(onClick = viewModel::cancelAttendance, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.ui_cancel_registration))
                            }
                        }
                    }
                }
            }
        }
    }
    if (state.commentToDelete != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteComment,
            title = { Text(stringResource(R.string.ui_delete_comment)) }, text = { Text(stringResource(R.string.ui_delete_comment_help)) },
            confirmButton = { TextButton(onClick = viewModel::deleteComment, enabled = !state.isSaving) { Text(stringResource(R.string.ui_delete)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissDeleteComment, enabled = !state.isSaving) { Text(stringResource(R.string.ui_cancel)) } }
        )
    }
}

@Composable
private fun InfoRow(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextoPrincipal)
        Text(subtitle, fontSize = 14.sp, color = TextoSecundario)
    }
}

private fun formatDateTime(timestamp: Long, locale: Locale): String =
    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT, locale).format(Date(timestamp))
