package com.eventoscomunitarios.app.features.event_detail

import android.widget.Toast
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
import androidx.compose.material3.Divider
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
import com.eventoscomunitarios.app.domain.model.EventCategory
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
        onConfirm = onConfirm,
        onToggleFavorite = { viewModel.toggleFavorite() },
        onToggleFollow = { viewModel.toggleFollow() },
        onShareClick = {
            Toast.makeText(context, "Enlace copiado con éxito", Toast.LENGTH_SHORT).show()
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = headerBackground,
                    navigationIconContentColor = TextoPrincipal,
                    actionIconContentColor = TextoPrincipal
                )
            )
        },
        bottomBar = {
            Surface(
                color = Color.White,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
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
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B5B7E)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Confirmar asistencia", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(headerBackground)
                .padding(bottom = paddingValues.calculateBottomPadding())
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TagBadge(text = if (event.category == EventCategory.SPORTS) "Deportes" else "Comunidad", color = Color(0xFFE4F0FF))
                        if (event.isFree) {
                            TagBadge(text = "Gratis", color = Color(0xFFDDF4D6))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = event.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextoPrincipal)
                    Text(text = "Copa comunitaria La Castilla 2026", fontSize = 14.sp, color = TextoSecundario)

                    Spacer(modifier = Modifier.height(24.dp))
                    InfoRow(title = "${event.dateLabel} de septiembre", subtitle = "${event.timeLabel} - 7:00 p.m.")
                    Spacer(modifier = Modifier.height(16.dp))
                    InfoRow(title = "${event.location}, cancha 2", subtitle = "Cra 14 #12-45 - a ${event.distanceKm} km de ti")
                    Spacer(modifier = Modifier.height(16.dp))
                    InfoRow(title = "${event.attendeeCount} asistentes confirmados", subtitle = "Cupo máximo 60 personas")

                    Spacer(modifier = Modifier.height(24.dp))
                    Divider(color = Color(0xFFE0E6EB))
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(text = "Organiza", fontSize = 14.sp, color = TextoPrincipal, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(40.dp).background(Color(0xFFEEDFFF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("JC", color = Color(0xFF6941A5), fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Junta de Acción Comunal", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text("Verificado · 12 eventos", fontSize = 12.sp, color = TextoSecundario)
                        }
                        Button(
                            onClick = onToggleFollow,
                            colors = if (state.isFollowing) {
                                ButtonDefaults.buttonColors(containerColor = Color(0xFFF0F4F8), contentColor = TextoPrincipal)
                            } else {
                                ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2B5B7E))
                            },
                            border = if (state.isFollowing) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2B5B7E))
                        ) {
                            Text(if (state.isFollowing) "Siguiendo" else "Seguir", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Text(text = "Descripción", fontSize = 14.sp, color = TextoPrincipal, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Torneo abierto para equipos del barrio. Inscribe tu equipo de 5 jugadores. Habrá hidratación y premiación para los tres primeros puestos.",
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
        Text(text = text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 12.sp, color = TextoPrincipal)
    }
}

@Composable
private fun InfoRow(title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(40.dp).background(Color(0xFFF0F4F8), RoundedCornerShape(8.dp)))
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextoPrincipal)
            Text(text = subtitle, fontSize = 12.sp, color = TextoSecundario)
        }
    }
}

@Preview(
    showBackground = true,
    showSystemUi = true,
    name = "Vista previa - Detalle"
)
@Composable
private fun EventDetailScreenPreview() {
    AppeventoscomunitariosTheme(
        darkTheme = false,
        dynamicColor = false
    ) {
        EventDetailContent(
            state = EventDetailUiState(
                event = FakeEvents.events.first(),
                isLoading = false,
                error = null
            ),
            onNavigateBack = {},
            onConfirm = {},
            onToggleFavorite = {},
            onToggleFollow = {},
            onShareClick = {}
        )
    }
}