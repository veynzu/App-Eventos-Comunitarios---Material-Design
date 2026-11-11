package com.eventoscomunitarios.app.features.admin_users

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.height
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.platform.LocalContext
import com.eventoscomunitarios.app.core.util.localizedMessage
import com.eventoscomunitarios.app.R
import com.eventoscomunitarios.app.domain.model.OrganizerVerificationStatus
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.eventoscomunitarios.app.core.components.AdminSectionTabs
import com.eventoscomunitarios.app.core.components.AdminDestination
import com.eventoscomunitarios.app.core.components.LoadingIndicator
import com.eventoscomunitarios.app.core.util.RequestResult
import com.eventoscomunitarios.app.core.util.initials
import com.eventoscomunitarios.app.domain.model.AccountStatus
import com.eventoscomunitarios.app.domain.model.User
import com.eventoscomunitarios.app.domain.model.UserRole
import com.eventoscomunitarios.app.ui.theme.AppeventoscomunitariosTheme
import com.eventoscomunitarios.app.ui.theme.Fondo
import com.eventoscomunitarios.app.ui.theme.TextoPrincipal
import com.eventoscomunitarios.app.ui.theme.TextoSecundario

@Composable
fun AdminUsersScreen(
    viewModel: AdminUsersViewModel = hiltViewModel(),
    onDashboardClick: () -> Unit = {},
    onModerationClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val state by viewModel.uiState.collectAsState()
    AdminUsersContent(
        snackbarHostState = snackbarHostState,
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onFilterSelected = viewModel::selectFilter,
        onToggleStatus = viewModel::toggleAccountStatus,
        onReviewIdentity = viewModel::reviewIdentity,
        onDismissIdentity = viewModel::dismissIdentity,
        onConfirmIdentity = viewModel::confirmIdentity,
        onFeedbackShown = viewModel::clearFeedback,
        onDashboardClick = onDashboardClick,
        onModerationClick = onModerationClick,
        onNotificationsClick = onNotificationsClick,
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
    onNotificationsClick: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onReviewIdentity: (String) -> Unit = {},
    onDismissIdentity: () -> Unit = {},
    onConfirmIdentity: (Boolean) -> Unit = {}
) {
    state.reviewingUser?.let { user ->
        val bitmap = remember(state.identity) { state.identity?.imageBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
        AlertDialog(onDismissRequest = onDismissIdentity,
            title = { Text(stringResource(R.string.verification_review_title, user.name)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.verification_review_explanation))
                    when (val identityResult = state.identityResult) {
                        RequestResult.Loading -> CircularProgressIndicator()
                        is RequestResult.Failure -> Text(localizedMessage(identityResult.message))
                        else -> if (bitmap != null) Image(bitmap, stringResource(R.string.verification_identity_label),
                            Modifier.fillMaxWidth().height(260.dp), contentScale = ContentScale.Fit)
                        else Text(stringResource(R.string.verification_image_error))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onConfirmIdentity(true) }, enabled = !state.isSaving && bitmap != null &&
                    user.organizerVerificationStatus == OrganizerVerificationStatus.PENDING) { Text(stringResource(R.string.verification_approve)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { onConfirmIdentity(false) }, enabled = !state.isSaving && bitmap != null &&
                        user.organizerVerificationStatus == OrganizerVerificationStatus.PENDING) { Text(stringResource(R.string.verification_block)) }
                    TextButton(onClick = onDismissIdentity, enabled = !state.isSaving) { Text(stringResource(R.string.verification_close)) }
                }
            })
    }
    when (val result = state.loadResult) {
        RequestResult.Loading -> return LoadingIndicator()
        is RequestResult.Failure -> return Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(localizedMessage(result.message)) }
        RequestResult.Success -> Unit
    }
    val context = LocalContext.current
    val snackbar = snackbarHostState
    LaunchedEffect(state.feedback) {
        state.feedback?.let {
            snackbar.showSnackbar(context.localizedMessage(it))
            onFeedbackShown()
        }
    }
    Column(Modifier.fillMaxSize().background(Fondo)) {
        AdminSectionTabs(
            selected = AdminDestination.USERS,
            onDashboardClick = onDashboardClick,
            onModerationClick = onModerationClick,
            onUsersClick = {}
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize().testTag("admin_users_screen")) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.verification_ui_users), fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onNotificationsClick) { Icon(Icons.Default.Notifications, stringResource(R.string.verification_ui_notifications)) }
                    IconButton(onClick = { onFilterSelected(UserFilter.entries[(state.selectedFilter.ordinal + 1) % UserFilter.entries.size]) }) { Icon(Icons.Default.FilterList, stringResource(R.string.verification_ui_change_filter)) }
                }
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    placeholder = { Text(stringResource(R.string.verification_ui_search_users)) },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp)
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UserFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = state.selectedFilter == filter,
                            onClick = { onFilterSelected(filter) },
                            label = { Text(stringResource(when(filter) { UserFilter.ALL -> R.string.verification_filter_all; UserFilter.PARTICIPANTS -> R.string.verification_role_participant; UserFilter.ORGANIZERS -> R.string.verification_filter_organizers; UserFilter.PENDING -> R.string.verification_filter_pending })) }
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.verification_ui_user_count, state.visibleUsers.size), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.verification_ui_suspended_count, state.suspendedCount), fontSize = 12.sp, color = Color(0xFFB3261E))
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.visibleUsers, key = User::id) { user ->
                        UserCard(user = user, enabled = !state.isSaving, onToggleStatus = { onToggleStatus(user.id) }, onReviewIdentity = { onReviewIdentity(user.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun UserCard(user: User, enabled: Boolean = true, onToggleStatus: () -> Unit, onReviewIdentity: () -> Unit) {
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
                    if (user.role == UserRole.ORGANIZER) {
                        AssistChip(onClick = onReviewIdentity, label = { Text(stringResource(when(user.organizerVerificationStatus) {
                            OrganizerVerificationStatus.VERIFIED -> R.string.verification_status_verified
                            OrganizerVerificationStatus.REJECTED -> R.string.verification_status_rejected
                            else -> R.string.verification_status_pending
                        }), fontSize = 10.sp) })
                    }
                    if (user.accountStatus == AccountStatus.SUSPENDED) {
                        AssistChip(onClick = {}, label = { Text(stringResource(R.string.verification_ui_suspended), fontSize = 10.sp, color = Color(0xFFB3261E)) })
                    }
                }
            }
            Box {
                IconButton(onClick = { expanded = true }, enabled = enabled) { Icon(Icons.Default.MoreVert, stringResource(R.string.verification_ui_options, user.name)) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    if (user.role == UserRole.ORGANIZER) DropdownMenuItem(
                        text = { Text(stringResource(R.string.verification_review_action)) },
                        onClick = { expanded = false; onReviewIdentity() })
                    DropdownMenuItem(
                        text = { Text(if (user.accountStatus == AccountStatus.ACTIVE) stringResource(R.string.verification_ui_suspend) else stringResource(R.string.verification_ui_reactivate)) },
                        onClick = { expanded = false; onToggleStatus() }
                    )
                }
            }
        }
    }
}

@Composable
private fun roleLabel(role: UserRole): String = when (role) {
    UserRole.ADMINISTRATOR -> stringResource(R.string.verification_role_admin)
    UserRole.ORGANIZER -> stringResource(R.string.verification_role_organizer)
    UserRole.PARTICIPANT -> stringResource(R.string.verification_role_participant)
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun AdminUsersPreview() {
    AppeventoscomunitariosTheme(darkTheme = false, dynamicColor = false) {
        AdminUsersContent(
            state = AdminUsersUiState(loadResult = RequestResult.Success, users = emptyList()),
            onQueryChange = {}, onFilterSelected = {}, onToggleStatus = {}, onFeedbackShown = {},
            onDashboardClick = {}, onModerationClick = {}, onNotificationsClick = {}
        )
    }
}
