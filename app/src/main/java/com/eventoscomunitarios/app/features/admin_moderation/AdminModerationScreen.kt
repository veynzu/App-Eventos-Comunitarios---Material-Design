package com.eventoscomunitarios.app.features.admin_moderation

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.eventoscomunitarios.app.domain.model.EventCategory

@Composable
fun AdminModerationScreen(
    viewModel: AdminModerationViewModel = hiltViewModel(),
    onDashboardClick: () -> Unit = {},
    onUsersClick: () -> Unit = {},
    onEventDetail: (Int) -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val state by viewModel.uiState.collectAsState()
    when (val result = state.loadResult) {
        RequestResult.Loading -> return LoadingIndicator()
        is RequestResult.Failure -> return Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(result.message) }
        RequestResult.Success -> Unit
    }
    val snackbar = snackbarHostState
    LaunchedEffect(state.feedback) {
        state.feedback?.let { snackbar.showSnackbar(it); viewModel.clearFeedback() }
    }
    Column(Modifier.fillMaxSize()) {
        AdminSectionTabs(selected = AdminDestination.MODERATION,
                   onDashboardClick = onDashboardClick, onModerationClick = {}, onUsersClick = onUsersClick)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize().testTag("admin_moderation_screen")) {
                Text("Moderación", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
                TabRow(selectedTabIndex = state.selectedTab.ordinal) {
                    ModerationTab.entries.forEach { tab ->
                        Tab(selected = tab == state.selectedTab, onClick = { viewModel.selectTab(tab) },
                            text = { Text(if (tab == ModerationTab.PENDING) "${tab.label} ${state.pendingCount}" else tab.label) })
                    }
                }
                OutlinedTextField(value = state.query, onValueChange = viewModel::onQueryChange,
                    label = { Text("Buscar por título o autor") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = state.category == null, onClick = { viewModel.selectCategory(null) }, label = { Text("Todas") })
                    EventCategory.entries.forEach { category ->
                        FilterChip(selected = state.category == category, onClick = { viewModel.selectCategory(category) }, label = { Text(category.displayName) })
                    }
                }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.visibleEvents.isEmpty()) item { Text("No hay eventos que coincidan con los filtros.") }
                    items(state.visibleEvents, key = { it.id }) { event ->
                        ModerationEventCard(event = event,
                            onReject = { viewModel.reject(event.id) }, onDetail = { onEventDetail(event.id) },
                            onApprove = { viewModel.approve(event.id) }, onDelete = { viewModel.requestDelete(event.id) },
                            onFinish = { viewModel.requestFinish(event.id) })
                    }
                }
            }
        }
    }
    if (state.isSaving && state.actionEvent == null) LoadingIndicator()
    state.actionEvent?.let { event ->
        val isRejection = state.action == ModerationAction.REJECT
        ModerationActionDialog(
            title = when (state.action) {
                ModerationAction.REJECT -> "Rechazar evento"
                ModerationAction.DELETE -> "Eliminar publicación"
                else -> "Finalizar evento"
            },
            description = when (state.action) {
                ModerationAction.REJECT -> "${event.title}: explica qué debe corregir el autor."
                ModerationAction.DELETE -> "${event.title}: se eliminarán también comentarios, votos e inscripciones."
                else -> "${event.title}: dejará de recibir nuevas inscripciones."
            },
            reason = if (isRejection) state.rejectionReason else null,
            error = if (state.isSaving) "Guardando…" else state.reasonError, onReasonChange = viewModel::onReasonChange,
            onConfirm = viewModel::confirmAction, onDismiss = viewModel::dismissAction
        )
    }
}
