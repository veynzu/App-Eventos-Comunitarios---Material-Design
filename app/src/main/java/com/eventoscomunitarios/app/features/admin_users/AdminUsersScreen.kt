package com.eventoscomunitarios.app.features.admin_users

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventoscomunitarios.app.core.components.AdminBottomBar
import com.eventoscomunitarios.app.core.components.AdminDestination
import com.eventoscomunitarios.app.core.components.LoadingIndicator
import com.eventoscomunitarios.app.core.util.RequestResult
import com.eventoscomunitarios.app.core.util.initials
import com.eventoscomunitarios.app.domain.model.AccountStatus
import com.eventoscomunitarios.app.domain.model.User
import com.eventoscomunitarios.app.domain.model.UserRole
import com.eventoscomunitarios.app.features.auth.FakeUsers
import com.eventoscomunitarios.app.ui.theme.AppeventoscomunitariosTheme
import com.eventoscomunitarios.app.ui.theme.Fondo
import com.eventoscomunitarios.app.ui.theme.TextoPrincipal
import com.eventoscomunitarios.app.ui.theme.TextoSecundario

@Composable
fun AdminUsersScreen(
    viewModel: AdminUsersViewModel = viewModel(),
    onDashboardClick: () -> Unit = {},
    onModerationClick: () -> Unit = {},
    onProfileClick: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    AdminUsersContent(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onFilterSelected = viewModel::selectFilter,
        onToggleStatus = viewModel::toggleAccountStatus,
        onFeedbackShown = viewModel::clearFeedback,
        onDashboardClick = onDashboardClick,
        onModerationClick = onModerationClick,
        onProfileClick = onProfileClick
    )
}

@Composable
private fun AdminUsersContent(
    state: AdminUsersUiState,
    onQueryChange: (String) -> Unit,
    onFilterSelected: (UserFilter) -> Unit,
    onToggleStatus: (String) -> Unit,
    onFeedbackShown: () -> Unit,
    onDashboardClick: () -> Unit,
    onModerationClick: () -> Unit,
    onProfileClick: () -> Unit
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
                selected = AdminDestination.USERS,
                onDashboardClick = onDashboardClick,
                onModerationClick = onModerationClick,
                onUsersClick = {},
                onProfileClick = onProfileClick
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).testTag("admin_users_screen")) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Usuarios", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = {}) { Icon(Icons.Default.Notifications, "Notificaciones") }
                IconButton(onClick = {}) { Icon(Icons.Default.FilterList, "Filtros") }
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Buscar por nombre o correo") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp)
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UserFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.selectedFilter == filter,
                        onClick = { onFilterSelected(filter) },
                        label = { Text(filter.label) }
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${state.visibleUsers.size} usuarios", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("${state.suspendedCount} suspendidos", fontSize = 12.sp, color = Color(0xFFB3261E))
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.visibleUsers, key = User::id) { user ->
                    UserCard(user = user, onToggleStatus = { onToggleStatus(user.id) })
                }
            }
        }
    }
}

@Composable
private fun UserCard(user: User, onToggleStatus: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).background(Color(0xFFD8EBFF), CircleShape),
                contentAlignment = Alignment.Center
            ) { Text(user.name.initials(), color = Color(0xFF2B638B), fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(user.name, color = TextoPrincipal, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(user.email, color = TextoSecundario, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(onClick = {}, label = { Text(roleLabel(user.role), fontSize = 10.sp) })
                    if (user.accountStatus == AccountStatus.SUSPENDED) {
                        AssistChip(onClick = {}, label = { Text("Suspendido", fontSize = 10.sp, color = Color(0xFFB3261E)) })
                    }
                }
            }
            Box {
                IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, "Opciones de ${user.name}") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text(if (user.accountStatus == AccountStatus.ACTIVE) "Suspender" else "Reactivar") },
                        onClick = { expanded = false; onToggleStatus() }
                    )
                }
            }
        }
    }
}

private fun roleLabel(role: UserRole): String = when (role) {
    UserRole.ADMINISTRATOR -> "Administrador"
    UserRole.ORGANIZER -> "Organizador"
    UserRole.PARTICIPANT -> "Cliente"
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun AdminUsersPreview() {
    AppeventoscomunitariosTheme(darkTheme = false, dynamicColor = false) {
        AdminUsersContent(
            state = AdminUsersUiState(loadResult = RequestResult.Success, users = FakeUsers.users),
            onQueryChange = {}, onFilterSelected = {}, onToggleStatus = {}, onFeedbackShown = {},
            onDashboardClick = {}, onModerationClick = {}, onProfileClick = {}
        )
    }
}
