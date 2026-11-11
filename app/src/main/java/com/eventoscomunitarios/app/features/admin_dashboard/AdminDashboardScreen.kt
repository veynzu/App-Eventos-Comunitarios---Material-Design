package com.eventoscomunitarios.app.features.admin_dashboard

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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import com.eventoscomunitarios.app.core.components.localizedName
import androidx.compose.ui.platform.LocalContext
import com.eventoscomunitarios.app.core.util.localizedMessage
import com.eventoscomunitarios.app.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.eventoscomunitarios.app.core.components.AdminSectionTabs
import com.eventoscomunitarios.app.core.components.AdminDestination
import com.eventoscomunitarios.app.core.components.LoadingIndicator
import com.eventoscomunitarios.app.core.components.ModerationActionDialog
import com.eventoscomunitarios.app.core.components.ModerationEventCard
import com.eventoscomunitarios.app.core.util.RequestResult

@Composable
fun AdminDashboardScreen(
    viewModel: AdminDashboardViewModel = hiltViewModel(),
    onModerationClick: () -> Unit = {},
    onUsersClick: () -> Unit = {},
    onEventDetail: (Int) -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    onCreateEventClick: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    when (val result = state.loadResult) {
        RequestResult.Loading -> return LoadingIndicator()
        is RequestResult.Failure -> return Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(localizedMessage(result.message)) }
        RequestResult.Success -> Unit
    }
    Column(Modifier.fillMaxSize()) {
        AdminSectionTabs(selected = AdminDestination.DASHBOARD, onDashboardClick = {},
            onModerationClick = onModerationClick, onUsersClick = onUsersClick)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize().testTag("admin_dashboard_screen"),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.verification_ui_dashboard), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = onNotificationsClick) { Icon(Icons.Default.Notifications, stringResource(R.string.verification_ui_notifications)) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onHomeClick) { Text(stringResource(R.string.verification_ui_explore)) }
                        OutlinedButton(onClick = onCreateEventClick) { Text(stringResource(R.string.verification_ui_create_event)) }
                    }
                }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.verification_pending_count, state.pendingOrganizers), style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = onUsersClick) { Text(stringResource(R.string.verification_review_organizers)) }
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricCard(state.activeEvents, stringResource(R.string.verification_ui_active_events), Modifier.weight(1f))
                        MetricCard(state.pendingEvents.size, stringResource(R.string.verification_ui_awaiting_approval), Modifier.weight(1f))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricCard(state.totalUsers, stringResource(R.string.verification_ui_users), Modifier.weight(1f))
                        MetricCard(state.totalAttendance, stringResource(R.string.verification_ui_attendance), Modifier.weight(1f))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricCard(state.waitingCount, stringResource(R.string.verification_ui_waitlist), Modifier.weight(1f))
                        MetricCard(state.finishedCount, stringResource(R.string.verification_ui_finished), Modifier.weight(1f))
                    }
                }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.verification_ui_categories), style = MaterialTheme.typography.titleMedium)
                            state.categoryCounts.forEach { (category, count) ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(category.localizedName())
                                    Text(count.toString())
                                }
                            }
                            Text(stringResource(R.string.verification_ui_rejected_count, state.rejectedCount), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.verification_ui_attention), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = onModerationClick) { Text(stringResource(R.string.verification_ui_view_all)) }
                    }
                }
                if (state.pendingEvents.isEmpty()) item { Text(stringResource(R.string.verification_ui_no_pending)) }
                items(state.pendingEvents.take(3), key = { it.id }) { event ->
                    ModerationEventCard(event = event, compact = true,
                        onReject = { viewModel.reject(event.id) }, onDetail = { onEventDetail(event.id) },
                        onApprove = { viewModel.approve(event.id) })
                }
            }
        }
    }
    if (state.actionResult == RequestResult.Loading && state.rejectionEvent == null) LoadingIndicator()
    state.rejectionEvent?.let { event ->
        ModerationActionDialog(title = stringResource(R.string.verification_ui_reject_event), description = stringResource(R.string.verification_ui_rejection_help, event.title),
            reason = state.rejectionReason, error = if (state.actionResult == RequestResult.Loading) stringResource(R.string.verification_ui_saving) else state.reasonError,
            onReasonChange = viewModel::onReasonChange, onConfirm = viewModel::confirmRejection,
            onDismiss = viewModel::dismissRejection)
    }
    if (state.actionResult != null && state.actionResult != RequestResult.Loading && state.rejectionEvent == null) {
        AlertDialog(onDismissRequest = viewModel::clearActionResult,
            title = { Text(if (state.actionResult == RequestResult.Success) stringResource(R.string.verification_ui_completed) else stringResource(R.string.verification_ui_failed)) },
            text = { Text(localizedMessage(state.actionMessage.orEmpty())) },
            confirmButton = { TextButton(onClick = viewModel::clearActionResult) { Text(stringResource(R.string.verification_ui_accept)) } },
            modifier = Modifier.testTag(if (state.actionResult == RequestResult.Success) "admin_action_success" else "admin_action_error"))
    }
}

@Composable
private fun MetricCard(value: Int, label: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value.toString(), style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}
