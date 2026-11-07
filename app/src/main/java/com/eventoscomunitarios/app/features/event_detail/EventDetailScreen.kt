package com.eventoscomunitarios.app.features.event_detail

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.eventoscomunitarios.app.core.components.LoadingIndicator
import com.eventoscomunitarios.app.core.util.initials
import com.eventoscomunitarios.app.domain.model.EventCategory
import com.eventoscomunitarios.app.domain.model.EventModerationStatus
import com.eventoscomunitarios.app.domain.model.EventRegistrationStatus
import com.eventoscomunitarios.app.ui.theme.TextoPrincipal
import com.eventoscomunitarios.app.ui.theme.TextoSecundario
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    viewModel: EventDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.attendanceConfirmedEventId) {
        state.attendanceConfirmedEventId?.let { eventId ->
            viewModel.clearAttendanceNavigation()
            onConfirm(eventId)
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    val event = state.event
    val headerBackground = when (event?.category) {
        EventCategory.SPORTS -> Color(0xFFD9ECFF)
        EventCategory.CULTURE -> Color(0xFFEEDFFF)
        else -> Color(0xFFDDEFEA)
    }
    Scaffold(
        modifier = Modifier.imePadding(),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Detalle del evento") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    if (event != null) {
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT,
                                    "${event.title}\nInicio: ${formatDateTime(event.startsAt)}\nFin: ${formatDateTime(event.endsAt)}\n${event.location}")
                            }
                            context.startActivity(Intent.createChooser(intent, "Compartir evento"))
                        }) { Icon(Icons.Default.Share, contentDescription = "Compartir") }
                        IconButton(onClick = viewModel::toggleFavorite, enabled = state.canFavorite && !state.isSaving) {
                            Icon(
                                if (state.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (state.isFavorite) "Quitar de favoritos" else "Agregar a favoritos",
                                tint = if (state.isFavorite) Color(0xFFD94D68) else TextoPrincipal
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = headerBackground)
            )
        },
        bottomBar = {
            if (event != null && (state.canRegister || state.registrationStatus != null)) {
                Surface(shadowElevation = 8.dp) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (event.isFree) "Entrada gratis" else "Entrada: \$${event.price}", fontWeight = FontWeight.SemiBold)
                            Text("${event.attendeeCount}/${event.capacity} cupos", color = TextoSecundario)
                        }
                        if (state.registrationStatus == null) {
                            Button(
                                onClick = viewModel::confirmAttendance,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = state.canRegister && !state.isSaving,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B5B7E))
                            ) {
                                Text(if (event.attendeeCount >= event.capacity) "Unirme a la lista de espera" else "Confirmar asistencia")
                            }
                        } else {
                            Text(
                                if (state.registrationStatus == EventRegistrationStatus.CONFIRMED) "Asistencia confirmada"
                                else "Lista de espera · posición ${state.waitlistPosition ?: "—"}",
                                fontWeight = FontWeight.Medium
                            )
                            if (state.canCancelRegistration) {
                                OutlinedButton(onClick = viewModel::cancelAttendance, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
                                    Text("Cancelar inscripción")
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        when {
            state.isLoading -> LoadingIndicator()
            event == null -> Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(state.error ?: "Evento no disponible")
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (state.isSaving) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                state.syncError?.let { message ->
                    item { Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 24.dp)) }
                }
                event.photoUri?.takeIf { it.isNotBlank() }?.let { photo ->
                    item {
                        AsyncImage(model = photo, contentDescription = "Foto de ${event.title}",
                            modifier = Modifier.fillMaxWidth().height(220.dp), contentScale = ContentScale.Crop)
                    }
                }
                item {
                    Column(Modifier.fillMaxWidth().background(headerBackground).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(event.category.displayName, style = MaterialTheme.typography.labelLarge)
                        Text(event.title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextoPrincipal)
                        Text(when {
                            event.moderationStatus == EventModerationStatus.PENDING -> "Pendiente de moderación"
                            event.moderationStatus == EventModerationStatus.REJECTED -> "Publicación rechazada"
                            event.isFinished || event.endsAt <= System.currentTimeMillis() -> "Evento finalizado"
                            event.startsAt <= System.currentTimeMillis() -> "Evento en curso · inscripciones cerradas"
                            else -> "Publicación aprobada"
                        }, fontWeight = FontWeight.SemiBold)
                        event.rejectionReason?.takeIf { it.isNotBlank() }?.let {
                            Text("Motivo del rechazo: $it", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        InfoRow("Inicio", formatDateTime(event.startsAt))
                        InfoRow("Finalización", formatDateTime(event.endsAt))
                        InfoRow("Lugar", event.location)
                        InfoRow("Asistencia", "${event.attendeeCount} confirmados · cupo máximo ${event.capacity}")
                        Text("${state.waitlistCount} personas en espera", color = TextoSecundario)
                        Text("Si se libera un cupo antes del inicio, la primera persona en espera recibe la confirmación y una notificación.",
                            style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
                        HorizontalDivider()
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Organiza", style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).background(Color(0xFFEEDFFF), CircleShape), contentAlignment = Alignment.Center) {
                                Text(event.organizerName.initials(), color = Color(0xFF6941A5), fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(event.organizerName, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            if (state.canFollow) {
                                TextButton(onClick = viewModel::toggleFollow, enabled = !state.isSaving) { Text(if (state.isFollowing) "Siguiendo" else "Seguir") }
                            }
                        }
                        Text("Descripción", style = MaterialTheme.typography.titleMedium)
                        Text(event.description.ifBlank { "Sin descripción" }, color = TextoSecundario)
                        HorizontalDivider()
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Valoración de la comunidad", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FilterChip(
                                selected = state.myVote == 1, enabled = state.canVote && !state.isSaving,
                                onClick = { viewModel.vote(1) },
                                label = { Text("${state.positiveVotes} positivos") },
                                leadingIcon = { Icon(Icons.Default.ThumbUp, contentDescription = null, Modifier.size(18.dp)) }
                            )
                            FilterChip(
                                selected = state.myVote == -1, enabled = state.canVote && !state.isSaving,
                                onClick = { viewModel.vote(-1) },
                                label = { Text("${state.negativeVotes} negativos") },
                                leadingIcon = { Icon(Icons.Default.ThumbDown, contentDescription = null, Modifier.size(18.dp)) }
                            )
                        }
                        Text(when {
                            state.canVote -> "Toca otra vez tu voto para retirarlo. Solo cuenta un voto por persona."
                            event.moderationStatus != EventModerationStatus.APPROVED -> "La valoración estará disponible cuando se apruebe la publicación."
                            else -> "Puedes consultar los votos; el autor no puede votar su propia publicación."
                        },
                            style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
                        HorizontalDivider()
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Comentarios (${state.comments.size})", style = MaterialTheme.typography.titleMedium)
                        if (state.canComment) {
                            OutlinedTextField(
                                value = state.commentText, onValueChange = viewModel::onCommentChange,
                                enabled = !state.isSaving,
                                modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5,
                                label = { Text("Escribe un comentario") },
                                isError = state.commentError != null,
                                supportingText = { Text(state.commentError ?: "${state.commentText.length}/1000") }
                            )
                            Button(onClick = viewModel::addComment, modifier = Modifier.align(Alignment.End), enabled = state.commentText.isNotBlank() && !state.isSaving) {
                                Text("Publicar comentario")
                            }
                        } else {
                            Text("Los comentarios estarán disponibles cuando se apruebe la publicación.", color = TextoSecundario)
                        }
                        if (state.comments.isEmpty()) Text("Todavía no hay comentarios.", color = TextoSecundario)
                    }
                }
                items(state.comments, key = { it.id }) { comment ->
                    Card(Modifier.fillMaxWidth().padding(horizontal = 24.dp), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(comment.authorName, fontWeight = FontWeight.SemiBold)
                                    Text(formatDateTime(comment.createdAt), style = MaterialTheme.typography.bodySmall)
                                }
                                if (comment.userId == state.currentUserId || state.isModerator) {
                                    IconButton(onClick = { viewModel.requestDeleteComment(comment.id) }, enabled = !state.isSaving) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar comentario")
                                    }
                                }
                            }
                            Text(comment.text)
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
    if (state.commentToDelete != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteComment,
            title = { Text("Eliminar comentario") }, text = { Text("El comentario se retirará de esta publicación.") },
            confirmButton = { TextButton(onClick = viewModel::deleteComment, enabled = !state.isSaving) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = viewModel::dismissDeleteComment, enabled = !state.isSaving) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun InfoRow(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextoPrincipal)
        Text(subtitle, fontSize = 14.sp, color = TextoSecundario)
    }
}

private fun formatDateTime(timestamp: Long): String =
    SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale.forLanguageTag("es-CO")).format(Date(timestamp))
