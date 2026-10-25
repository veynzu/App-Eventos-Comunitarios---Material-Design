package com.eventoscomunitarios.app.features.admin_moderation

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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventoscomunitarios.app.core.components.AdminBottomBar
import com.eventoscomunitarios.app.core.components.AdminDestination
import com.eventoscomunitarios.app.core.components.LoadingIndicator
import com.eventoscomunitarios.app.core.components.ModerationEventCard
import com.eventoscomunitarios.app.core.data.FakeEvents
import com.eventoscomunitarios.app.core.util.RequestResult
import com.eventoscomunitarios.app.domain.model.EventCategory
import com.eventoscomunitarios.app.ui.theme.AppeventoscomunitariosTheme
import com.eventoscomunitarios.app.ui.theme.Fondo

@Composable
fun AdminModerationScreen(
    viewModel: AdminModerationViewModel = viewModel(),
    onDashboardClick: () -> Unit = {},
    onUsersClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onEventDetail: (Int) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    AdminModerationContent(
        state = state,
        onTabSelected = viewModel::selectTab,
        onCategorySelected = viewModel::selectCategory,
        onApprove = viewModel::approve,
        onReject = viewModel::reject,
        onFeedbackShown = viewModel::clearFeedback,
        onDashboardClick = onDashboardClick,
        onUsersClick = onUsersClick,
        onProfileClick = onProfileClick,
        onEventDetail = onEventDetail
    )
}

@Composable
private fun AdminModerationContent(
    state: AdminModerationUiState,
    onTabSelected: (ModerationTab) -> Unit,
    onCategorySelected: (EventCategory?) -> Unit,
    onApprove: (Int) -> Unit,
    onReject: (Int) -> Unit,
    onFeedbackShown: () -> Unit,
    onDashboardClick: () -> Unit,
    onUsersClick: () -> Unit,
    onProfileClick: () -> Unit,
    onEventDetail: (Int) -> Unit
) {
    when (val result = state.loadResult) {
        RequestResult.Loading -> return LoadingIndicator()
        is RequestResult.Failure -> return Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(result.message) }
        RequestResult.Success -> Unit
    }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.feedback) {
        state.feedback?.let {
            snackbar.showSnackbar(it)
            onFeedbackShown()
        }
    }
    Scaffold(
        containerColor = Fondo,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            AdminBottomBar(
                selected = AdminDestination.MODERATION,
                onDashboardClick = onDashboardClick,
                onModerationClick = {},
                onUsersClick = onUsersClick,
                onProfileClick = onProfileClick
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).testTag("admin_moderation_screen")) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {}) { Icon(Icons.Default.Menu, "Menú") }
                Text("Moderación", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = {}) { Icon(Icons.Default.Search, "Buscar") }
            }
            TabRow(selectedTabIndex = state.selectedTab.ordinal) {
                ModerationTab.entries.forEach { tab ->
                    Tab(
                        selected = tab == state.selectedTab,
                        onClick = { onTabSelected(tab) },
                        text = { Text(if (tab == ModerationTab.PENDING) "${tab.label} ${state.pendingCount}" else tab.label) }
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = state.category == null, onClick = { onCategorySelected(null) }, label = { Text("Más antiguos") })
                FilterChip(selected = state.category == EventCategory.SPORTS, onClick = { onCategorySelected(EventCategory.SPORTS) }, label = { Text("Deportes") })
                FilterChip(selected = state.category == EventCategory.CULTURE, onClick = { onCategorySelected(EventCategory.CULTURE) }, label = { Text("Cultura") })
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.visibleEvents.isEmpty()) {
                    item { Text("No hay eventos en esta sección.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(state.visibleEvents, key = { it.id }) { event ->
                    ModerationEventCard(
                        event = event,
                        onReject = { onReject(event.id) },
                        onDetail = { onEventDetail(event.id) },
                        onApprove = { onApprove(event.id) }
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun AdminModerationPreview() {
    AppeventoscomunitariosTheme(darkTheme = false, dynamicColor = false) {
        AdminModerationContent(
            state = AdminModerationUiState(loadResult = RequestResult.Success, events = FakeEvents.events),
            onTabSelected = {}, onCategorySelected = {}, onApprove = {}, onReject = {},
            onFeedbackShown = {}, onDashboardClick = {}, onUsersClick = {}, onProfileClick = {}, onEventDetail = {}
        )
    }
}
