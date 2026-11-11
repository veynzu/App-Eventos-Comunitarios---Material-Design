package com.eventoscomunitarios.app.features.notifications

import com.eventoscomunitarios.app.core.util.localizedMessage
import com.eventoscomunitarios.app.core.util.localizedNotificationMessage

import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.res.stringResource
import com.eventoscomunitarios.app.R

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.eventoscomunitarios.app.core.components.LoadingIndicator
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    viewModel: NotificationsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onOpenEvent: (Int) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbar = snackbarHostState
    LaunchedEffect(state.openEventId) {
        state.openEventId?.let { eventId ->
            viewModel.clearNavigation()
            onOpenEvent(eventId)
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(context.localizedMessage(it))
            viewModel.clearMessage()
        }
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.ui_notifications)) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.ui_back)) }
            }
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isLoading -> LoadingIndicator()
                state.error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(localizedMessage(state.error.orEmpty()), color = MaterialTheme.colorScheme.error)
                }
                state.items.isEmpty() -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.NotificationsNone, contentDescription = null, modifier = Modifier.size(56.dp))
                    Text(stringResource(R.string.ui_no_notifications), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
                    Text(stringResource(R.string.ui_notifications_help), modifier = Modifier.padding(top = 8.dp))
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (state.isSaving) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.ui_unread_count, state.unreadCount), modifier = Modifier.weight(1f))
                            TextButton(onClick = viewModel::markAllRead, enabled = state.unreadCount > 0 && !state.isSaving) { Text(stringResource(R.string.ui_mark_all_read)) }
                        }
                    }
                    items(state.items, key = { it.notification.id }) { item ->
                        val notification = item.notification
                        Card(
                            onClick = { viewModel.openNotification(notification.id) },
                            enabled = !state.isSaving,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            colors = CardDefaults.cardColors(containerColor =
                                if (notification.isRead) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (!notification.isRead) Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                                    Text(localizedMessage(notification.title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                }
                                Text(localizedNotificationMessage(notification))
                                Text(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT, androidx.compose.ui.platform.LocalConfiguration.current.locales[0]).format(Date(notification.createdAt)),
                                    style = MaterialTheme.typography.bodySmall)
                                Text(
                                    when {
                                        item.canOpenEvent -> stringResource(R.string.ui_view_post)
                                        notification.eventId != null -> stringResource(R.string.ui_post_unavailable)
                                        notification.isRead -> stringResource(R.string.ui_read)
                                        else -> stringResource(R.string.ui_mark_read)
                                    }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    item { Text("", Modifier.padding(bottom = 8.dp)) }
                }
            }
        }
    }
}
