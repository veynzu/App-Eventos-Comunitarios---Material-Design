package com.eventoscomunitarios.app.features.my_events

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
    val snackbar = snackbarHostState
    LaunchedEffect(state.feedback) {
        state.feedback?.let { snackbar.showSnackbar(it); viewModel.clearFeedback() }
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Mis eventos") }, navigationIcon = {
                   IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") }
               })
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize().testTag("my_events_screen")) {
                TabRow(selectedTabIndex = state.selectedTab.ordinal) {
                    MyEventsTab.entries.forEach { tab ->
                        Tab(selected = tab == state.selectedTab, onClick = { viewModel.onTabSelected(tab) },
                            text = { Text(if (tab == MyEventsTab.ENROLLED) "Inscritos" else "Creados") })
                    }
                }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                    if (state.selectedTab == MyEventsTab.ENROLLED) {
                        if (state.enrolledEvents.isEmpty()) item { Text("No estás inscrito en ningún evento.") }
                        items(state.enrolledEvents, key = { it.event.id }) { enrolled ->
                            val event = enrolled.event
                            EventSummary(event) {
                                Text(if (enrolled.status == EventRegistrationStatus.CONFIRMED) "Asistencia confirmada"
                                    else "Lista de espera · posición ${enrolled.waitlistPosition}")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { onEventClick(event.id) }) { Text("Ver detalle") }
                                    if (!event.isFinished && event.startsAt > System.currentTimeMillis()) {
                                        TextButton(onClick = { viewModel.requestAction(event.id, MyEventAction.CANCEL_REGISTRATION) }) { Text("Cancelar inscripción") }
                                    }
                                }
                            }
                        }
                    } else {
                        if (state.createdEvents.isEmpty()) item { Text("Aún no has creado ningún evento.") }
                        items(state.createdEvents, key = { it.id }) { event ->
                            EventSummary(event) {
                                Text("${event.attendeeCount}/${event.capacity} asistentes · ${state.waitlistCounts[event.id] ?: 0} en espera")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { onEventClick(event.id) }) { Text("Ver detalle") }
                                    if (!event.isFinished && event.startsAt > System.currentTimeMillis()) {
                                        OutlinedButton(onClick = { onAdministrarClick(event.id) }) { Text("Editar") }
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (event.hasEnded && event.moderationStatus == EventModerationStatus.APPROVED) {
                                        TextButton(onClick = { onRelaunch(event.id) }, modifier = Modifier.testTag("relaunch_event_${event.id}")) { Text(stringResource(R.string.relaunch_event)) }
                                    }
                                    if (!event.isFinished && event.moderationStatus == EventModerationStatus.APPROVED && event.startsAt <= System.currentTimeMillis()) {
                                        TextButton(onClick = { viewModel.requestAction(event.id, MyEventAction.FINISH) }) { Text("Finalizar") }
                                    }
                                    TextButton(onClick = { viewModel.requestAction(event.id, MyEventAction.DELETE) }) { Text("Eliminar publicación", color = MaterialTheme.colorScheme.error) }
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
            MyEventAction.DELETE -> "Eliminar publicación"
            MyEventAction.FINISH -> "Finalizar evento"
            else -> "Cancelar inscripción"
        }
        val explanation = when (state.action) {
            MyEventAction.DELETE -> "Se eliminarán la publicación, sus comentarios, votos e inscripciones."
            MyEventAction.FINISH -> "El evento quedará finalizado y no recibirá nuevas inscripciones."
            else -> if (state.enrolledEvents.firstOrNull { it.event.id == event.id }?.status == EventRegistrationStatus.WAITLIST)
                "Saldrás de la lista de espera. Si vuelves a inscribirte ocuparás el último lugar."
            else "Liberarás tu cupo. Si hay personas en espera, la primera recibirá una confirmación automática."
        }
        AlertDialog(onDismissRequest = viewModel::dismissAction, title = { Text(title) },
            text = { Text("${event.title}\n\n$explanation") },
            confirmButton = { TextButton(onClick = viewModel::confirmAction, enabled = !state.isSaving) { Text(if (state.isSaving) "Guardando…" else "Confirmar") } },
            dismissButton = { TextButton(onClick = viewModel::dismissAction, enabled = !state.isSaving) { Text("Volver") } })
    }
}

@Composable
private fun EventSummary(event: Event, actions: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (event.photoUri != null) RemoteImage(event.photoUri, event.title, Modifier.fillMaxWidth().height(120.dp))
            Text(event.title, style = MaterialTheme.typography.titleMedium)
            Text("${event.dateLabel} · ${event.timeLabel}", style = MaterialTheme.typography.bodySmall)
            Text("Fin: ${eventFormDate(event.endsAt)} · ${eventFormTime(event.endsAt)}", style = MaterialTheme.typography.bodySmall)
            Text(event.location, style = MaterialTheme.typography.bodySmall)
            Text(when {
                event.isFinished -> "Finalizado"
                event.moderationStatus == EventModerationStatus.PENDING -> "Pendiente de revisión"
                event.moderationStatus == EventModerationStatus.REJECTED -> "Rechazado"
                event.endsAt <= System.currentTimeMillis() -> "Fecha de finalización alcanzada"
                else -> "Aprobado"
            }, color = MaterialTheme.colorScheme.primary)
            event.rejectionReason?.let { Text("Motivo del rechazo: $it", color = MaterialTheme.colorScheme.error) }
            actions()
        }
    }
}
