package com.eventoscomunitarios.app.features.event_detail

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventoscomunitarios.app.core.components.LoadingIndicator
import com.eventoscomunitarios.app.core.data.FakeEvents
import com.eventoscomunitarios.app.core.util.initials
import com.eventoscomunitarios.app.domain.model.EventCategory
import com.eventoscomunitarios.app.domain.model.EventRegistrationStatus
import com.eventoscomunitarios.app.ui.theme.AppeventoscomunitariosTheme
import com.eventoscomunitarios.app.ui.theme.TextoPrincipal
import com.eventoscomunitarios.app.ui.theme.TextoSecundario

@Composable
fun EventDetailScreen(
    viewModel: EventDetailViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    EventDetailContent(
        state = state,
        onNavigateBack = onNavigateBack,
        onConfirm = { eventId ->
            if (viewModel.confirmAttendance()) onConfirm(eventId)
        },
        onToggleFavorite = viewModel::toggleFavorite,
        onToggleFollow = viewModel::toggleFollow,
        onShareClick = {
            state.event?.let { event ->
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "${event.title}\n${event.dateLabel}, ${event.timeLabel}\n${event.location}"
                    )
                }
                context.startActivity(Intent.createChooser(shareIntent, "Compartir evento"))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventDetailContent(
    state: EventDetailUiState,
    onNavigateBack: () -> Unit,
    onConfirm: (Int) -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleFollow: () -> Unit,
    onShareClick: () -> Unit
) {
    if (state.isLoading) {
        LoadingIndicator()
        return
    }

    val event = state.event
    if (event == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = state.error ?: "Ocurrió un error", color = TextoPrincipal)
        }
        return
    }

    val headerBackground = when (event.category) {
        EventCategory.SPORTS -> Color(0xFFD9ECFF)
        EventCategory.CULTURE -> Color(0xFFEEDFFF)
        EventCategory.COMMUNITY -> Color(0xFFDDEFEA)
    }
    val registrationText = when (state.registrationStatus) {
        EventRegistrationStatus.CONFIRMED -> "Asistencia confirmada"
        EventRegistrationStatus.WAITLIST -> "En lista de espera"
        null -> "Confirmar asistencia"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    IconButton(onClick = onShareClick) {
                        Icon(Icons.Default.Share, contentDescription = "Compartir")
                    }
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (state.isFavorite) Icons.Filled.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorito",
                            tint = if (state.isFavorite) Color(0xFFD94D68) else TextoPrincipal
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = headerBackground)
            )
        },
        bottomBar = {
            if (state.canRegister) Surface(color = Color.White, shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Entrada", fontSize = 12.sp, color = TextoSecundario)
                        Text(
                            text = if (event.isFree) "Gratis" else "\$${event.price}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextoPrincipal
                        )
                    }
                    Button(
                        onClick = { onConfirm(event.id) },
                        enabled = state.registrationStatus == null,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B5B7E)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(registrationText, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(headerBackground)
                .padding(top = paddingValues.calculateTopPadding(), bottom = paddingValues.calculateBottomPadding())
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = Color.White
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TagBadge(text = event.category.displayName, color = Color(0xFFE4F0FF))
                        if (event.isFree) TagBadge(text = "Gratis", color = Color(0xFFDDF4D6))
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(event.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextoPrincipal)

                    Spacer(modifier = Modifier.height(24.dp))
                    InfoRow(title = event.dateLabel, subtitle = event.timeLabel)
                    Spacer(modifier = Modifier.height(16.dp))
                    InfoRow(title = event.location, subtitle = "A ${formatDistance(event.distanceKm)} km de ti")
                    Spacer(modifier = Modifier.height(16.dp))
                    InfoRow(
                        title = "${event.attendeeCount} asistentes confirmados",
                        subtitle = "Cupo máximo ${event.capacity} personas"
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider(color = Color(0xFFE0E6EB))
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Organiza", fontSize = 14.sp, color = TextoPrincipal, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(40.dp).background(Color(0xFFEEDFFF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(event.organizerName.initials(), color = Color(0xFF6941A5), fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            event.organizerName,
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                        Button(
                            onClick = onToggleFollow,
                            colors = if (state.isFollowing) {
                                ButtonDefaults.buttonColors(containerColor = Color(0xFFF0F4F8), contentColor = TextoPrincipal)
                            } else {
                                ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2B5B7E))
                            },
                            border = if (state.isFollowing) null else BorderStroke(1.dp, Color(0xFF2B5B7E))
                        ) {
                            Text(if (state.isFollowing) "Siguiendo" else "Seguir", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Descripción", fontSize = 14.sp, color = TextoPrincipal, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = event.description.ifBlank { "Sin descripción" },
                        fontSize = 14.sp,
                        color = TextoSecundario,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun TagBadge(text: String, color: Color) {
    Surface(color = color, shape = RoundedCornerShape(12.dp)) {
        Text(text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 12.sp)
    }
}

@Composable
private fun InfoRow(title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(40.dp).background(Color(0xFFF0F4F8), RoundedCornerShape(8.dp)))
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextoPrincipal)
            Text(subtitle, fontSize = 12.sp, color = TextoSecundario)
        }
    }
}

private fun formatDistance(distanceKm: Double): String =
    if (distanceKm % 1.0 == 0.0) distanceKm.toInt().toString() else distanceKm.toString()

@Preview(showBackground = true, showSystemUi = true, name = "Vista previa - Detalle")
@Composable
private fun EventDetailScreenPreview() {
    AppeventoscomunitariosTheme(darkTheme = false, dynamicColor = false) {
        EventDetailContent(
            state = EventDetailUiState(event = FakeEvents.events.first(), isLoading = false),
            onNavigateBack = {},
            onConfirm = {},
            onToggleFavorite = {},
            onToggleFollow = {},
            onShareClick = {}
        )
    }
}
