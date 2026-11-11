package com.eventoscomunitarios.app.features.my_events

import com.eventoscomunitarios.app.core.util.localizedMessage

import androidx.compose.ui.platform.LocalContext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.eventoscomunitarios.app.core.components.LoadingIndicator
import com.eventoscomunitarios.app.core.components.RemoteImage
import androidx.compose.foundation.layout.height
import com.eventoscomunitarios.app.core.util.eventFormDate
import com.eventoscomunitarios.app.core.util.eventFormTime
import com.eventoscomunitarios.app.domain.model.Event
import com.eventoscomunitarios.app.domain.model.EventModerationStatus
import com.eventoscomunitarios.app.domain.model.EventRegistrationStatus
import com.eventoscomunitarios.app.R
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyEventsScreen(
    onNavigateBack: () -> Unit,
    onAdministrarClick: (Int) -> Unit,
    viewModel: MyEventsViewModel = hiltViewModel(),
    onEventClick: (Int) -> Unit = {},
    onRelaunch: (Int) -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbar = snackbarHostState
    LaunchedEffect(state.feedback) {
        state.feedback?.let { snackbar.showSnackbar(context.localizedMessage(it)); viewModel.clearFeedback() }
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.ui_my_events)) }, navigationIcon = {
                   IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.ui_back_alt)) }
               })
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize().testTag("my_events_screen")) {
                TabRow(selectedTabIndex = state.selectedTab.ordinal) {
                    MyEventsTab.entries.forEach { tab ->
                        Tab(selected = tab == state.selectedTab, onClick = { viewModel.onTabSelected(tab) },
                            text = { Text(if (tab == MyEventsTab.ENROLLED) stringResource(R.string.ui_enrolled) else stringResource(R.string.ui_created)) })
                    }
                }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.error?.let { item { Text(localizedMessage(it), color = MaterialTheme.colorScheme.error) } }
                    if (state.selectedTab == MyEventsTab.ENROLLED) {
                        if (state.enrolledEvents.isEmpty()) item { Text(stringResource(R.string.ui_no_enrolled)) }
                        items(state.enrolledEvents, key = { it.event.id }) { enrolled ->
                            val event = enrolled.event
                            EventSummary(event) {
                                Text(if (enrolled.status == EventRegistrationStatus.CONFIRMED) stringResource(R.string.ui_attendance_confirmed)
                                    else stringResource(R.string.ui_waiting_position, enrolled.waitlistPosition?.toString() ?: "—"))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { onEventClick(event.id) }) { Text(stringResource(R.string.ui_details)) }
                                    if (!event.isFinished && event.startsAt > System.currentTimeMillis()) {
                                        TextButton(onClick = { viewModel.requestAction(event.id, MyEventAction.CANCEL_REGISTRATION) }) { Text(stringResource(R.string.ui_cancel_registration)) }
                                    }
                                }
                            }
                        }
                    } else {
                        if (state.createdEvents.isEmpty()) item { Text(stringResource(R.string.ui_no_created)) }
                        items(state.createdEvents, key = { it.id }) { event ->
                            EventSummary(event) {
                                Text(stringResource(R.string.ui_attendees_waiting_count, event.attendeeCount, event.capacity, state.waitlistCounts[event.id] ?: 0))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { onEventClick(event.id) }) { Text(stringResource(R.string.ui_details)) }
                                    if (!event.isFinished && event.startsAt > System.currentTimeMillis()) {
                                        OutlinedButton(onClick = { onAdministrarClick(event.id) }) { Text(stringResource(R.string.ui_edit)) }
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (event.hasEnded && event.moderationStatus == EventModerationStatus.APPROVED) {
                                        TextButton(onClick = { onRelaunch(event.id) }, modifier = Modifier.testTag("relaunch_event_${event.id}")) { Text(stringResource(R.string.relaunch_event)) }
                                    }
                                    if (!event.isFinished && event.moderationStatus == EventModerationStatus.APPROVED && event.startsAt <= System.currentTimeMillis()) {
                                        TextButton(onClick = { viewModel.requestAction(event.id, MyEventAction.FINISH) }) { Text(stringResource(R.string.ui_finish)) }
                                    }
                                    TextButton(onClick = { viewModel.requestAction(event.id, MyEventAction.DELETE) }) { Text(stringResource(R.string.ui_delete_post), color = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (state.isLoading) LoadingIndicator()
    state.actionEvent?.let { event ->
        val title = when (state.action) {
            MyEventAction.DELETE -> stringResource(R.string.ui_delete_post)
            MyEventAction.FINISH -> stringResource(R.string.ui_finish_event)
            else -> stringResource(R.string.ui_cancel_registration)
        }
        val explanation = when (state.action) {
            MyEventAction.DELETE -> stringResource(R.string.ui_delete_post_help)
            MyEventAction.FINISH -> stringResource(R.string.ui_finish_event_help)
            else -> if (state.enrolledEvents.firstOrNull { it.event.id == event.id }?.status == EventRegistrationStatus.WAITLIST)
                stringResource(R.string.ui_leave_waitlist_help)
            else stringResource(R.string.ui_cancel_place_help)
        }
        AlertDialog(onDismissRequest = viewModel::dismissAction, title = { Text(title) },
            text = { Text("${event.title}\n\n$explanation") },
            confirmButton = { TextButton(onClick = viewModel::confirmAction, enabled = !state.isSaving) { Text(if (state.isSaving) stringResource(R.string.ui_saving) else stringResource(R.string.ui_confirm)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissAction, enabled = !state.isSaving) { Text(stringResource(R.string.ui_back)) } })
    }
}

@Composable
private fun EventSummary(event: Event, actions: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (event.photoUri != null) RemoteImage(event.photoUri, event.title, Modifier.fillMaxWidth().height(120.dp))
            Text(event.title, style = MaterialTheme.typography.titleMedium)
            Text("${event.dateLabel} · ${event.timeLabel}", style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.ui_end_datetime, eventFormDate(event.endsAt), eventFormTime(event.endsAt)), style = MaterialTheme.typography.bodySmall)
            Text(event.location, style = MaterialTheme.typography.bodySmall)
            Text(when {
                event.isFinished -> stringResource(R.string.ui_finished)
                event.moderationStatus == EventModerationStatus.PENDING -> stringResource(R.string.ui_pending)
                event.moderationStatus == EventModerationStatus.REJECTED -> stringResource(R.string.ui_rejected)
                event.endsAt <= System.currentTimeMillis() -> stringResource(R.string.ui_end_reached)
                else -> stringResource(R.string.ui_approved)
            }, color = MaterialTheme.colorScheme.primary)
            event.rejectionReason?.let { Text(stringResource(R.string.ui_rejection, it), color = MaterialTheme.colorScheme.error) }
            actions()
        }
    }
}
