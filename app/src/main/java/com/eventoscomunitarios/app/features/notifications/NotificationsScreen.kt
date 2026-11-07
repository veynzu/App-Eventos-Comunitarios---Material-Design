package com.eventoscomunitarios.app.features.notifications

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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    viewModel: NotificationsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onOpenEvent: (Int) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.openEventId) {
        state.openEventId?.let { eventId ->
            viewModel.clearNavigation()
            onOpenEvent(eventId)
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Notificaciones") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
                }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> LoadingIndicator()
            state.error != null -> Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
            }
            state.items.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.NotificationsNone, contentDescription = null, modifier = Modifier.size(56.dp))
                Text("Aún no tienes notificaciones", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
                Text("Aquí encontrarás las novedades de tus eventos, comentarios y lista de espera.", modifier = Modifier.padding(top = 8.dp))
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.isSaving) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${state.unreadCount} sin leer", modifier = Modifier.weight(1f))
                        TextButton(onClick = viewModel::markAllRead, enabled = state.unreadCount > 0 && !state.isSaving) { Text("Marcar todas leídas") }
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
                                Text(notification.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                            Text(notification.message)
                            Text(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-CO")).format(Date(notification.createdAt)),
                                style = MaterialTheme.typography.bodySmall)
                            Text(
                                when {
                                    item.canOpenEvent -> "Ver publicación"
                                    notification.eventId != null -> "La publicación ya no está disponible"
                                    notification.isRead -> "Leída"
                                    else -> "Toca para marcar como leída"
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
