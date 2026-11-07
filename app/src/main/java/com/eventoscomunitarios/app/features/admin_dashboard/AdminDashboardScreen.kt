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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.eventoscomunitarios.app.core.components.AdminBottomBar
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
    onProfileClick: () -> Unit = {},
    onEventDetail: (Int) -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    onCreateEventClick: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    when (val result = state.loadResult) {
        RequestResult.Loading -> return LoadingIndicator()
        is RequestResult.Failure -> return Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(result.message) }
        RequestResult.Success -> Unit
    }
    Scaffold(bottomBar = {
        AdminBottomBar(selected = AdminDestination.DASHBOARD, onDashboardClick = {},
            onModerationClick = onModerationClick, onUsersClick = onUsersClick, onProfileClick = onProfileClick)
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("admin_dashboard_screen"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Panel de control", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = onNotificationsClick) { Icon(Icons.Default.Notifications, "Notificaciones") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onHomeClick) { Text("Explorar eventos") }
                    OutlinedButton(onClick = onCreateEventClick) { Text("Crear evento") }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard(state.activeEvents, "Eventos activos", Modifier.weight(1f))
                    MetricCard(state.pendingEvents.size, "Por aprobar", Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard(state.totalUsers, "Usuarios", Modifier.weight(1f))
                    MetricCard(state.totalAttendance, "Asistencias confirmadas", Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard(state.waitingCount, "En lista de espera", Modifier.weight(1f))
                    MetricCard(state.finishedCount, "Finalizados", Modifier.weight(1f))
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Publicaciones por categoría", style = MaterialTheme.typography.titleMedium)
                        state.categoryCounts.forEach { (category, count) ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(category.displayName)
                                Text(count.toString())
                            }
                        }
                        Text("Rechazados: ${state.rejectedCount}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Requieren tu atención", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onModerationClick) { Text("Ver todo") }
                }
            }
            if (state.pendingEvents.isEmpty()) item { Text("No hay eventos pendientes de moderación.") }
            items(state.pendingEvents.take(3), key = { it.id }) { event ->
                ModerationEventCard(event = event, compact = true,
                    onReject = { viewModel.reject(event.id) }, onDetail = { onEventDetail(event.id) },
                    onApprove = { viewModel.approve(event.id) })
            }
        }
    }
    if (state.actionResult == RequestResult.Loading && state.rejectionEvent == null) LoadingIndicator()
    state.rejectionEvent?.let { event ->
        ModerationActionDialog(title = "Rechazar evento", description = "${event.title}: explica qué debe corregir el autor.",
            reason = state.rejectionReason, error = if (state.actionResult == RequestResult.Loading) "Guardando…" else state.reasonError,
            onReasonChange = viewModel::onReasonChange, onConfirm = viewModel::confirmRejection,
            onDismiss = viewModel::dismissRejection)
    }
    if (state.actionResult != null && state.actionResult != RequestResult.Loading && state.rejectionEvent == null) {
        AlertDialog(onDismissRequest = viewModel::clearActionResult,
            title = { Text(if (state.actionResult == RequestResult.Success) "Acción completada" else "No se pudo completar") },
            text = { Text(state.actionMessage.orEmpty()) },
            confirmButton = { TextButton(onClick = viewModel::clearActionResult) { Text("Aceptar") } },
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
