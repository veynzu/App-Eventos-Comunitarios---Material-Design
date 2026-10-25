package com.eventoscomunitarios.app.features.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventoscomunitarios.app.domain.model.Event
import com.eventoscomunitarios.app.domain.model.EventCategory
import com.eventoscomunitarios.app.core.util.RequestResult
import com.eventoscomunitarios.app.core.components.LoadingIndicator
import com.eventoscomunitarios.app.domain.model.UserRole
import com.eventoscomunitarios.app.features.auth.FakeUsers
import com.eventoscomunitarios.app.ui.theme.AppeventoscomunitariosTheme
import com.eventoscomunitarios.app.ui.theme.AzulClaro
import com.eventoscomunitarios.app.ui.theme.Fondo
import com.eventoscomunitarios.app.ui.theme.Gris
import com.eventoscomunitarios.app.ui.theme.TextoPrincipal
import com.eventoscomunitarios.app.ui.theme.TextoSecundario

@Composable
fun HomeScreen(
    userId: String,
    viewModel: HomeViewModel = viewModel(),
    onAbrirMapa: () -> Unit = {},
    onAbrirMisEventos: () -> Unit = {},
    onAbrirPerfil: () -> Unit = {},
    onCrearEvento: () -> Unit = {},
    onEventClick: (Int) -> Unit = {},
    onSessionInvalid: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(userId) { viewModel.loadUser(userId) }

    HomeScreenContent(
        state = state,
        onSearchRadiusChange = viewModel::onSearchRadiusChange,
        onFilterChange = viewModel::onFilterChange,
        onFavoriteClick = viewModel::onFavoriteClick,
        onAbrirMapa = onAbrirMapa,
        onAbrirMisEventos = onAbrirMisEventos,
        onAbrirPerfil = onAbrirPerfil,
        onCrearEvento = onCrearEvento,
        onEventClick = onEventClick,
        onRetry = { viewModel.loadUser(userId) },
        onSessionInvalid = onSessionInvalid
    )
}

@Composable
private fun HomeScreenContent(
    state: HomeUiState,
    onSearchRadiusChange: (Float) -> Unit,
    onFilterChange: (HomeFilter) -> Unit,
    onFavoriteClick: (Int) -> Unit,
    onAbrirMapa: () -> Unit,
    onAbrirMisEventos: () -> Unit,
    onAbrirPerfil: () -> Unit,
    onCrearEvento: () -> Unit,
    onEventClick: (Int) -> Unit,
    onRetry: () -> Unit,
    onSessionInvalid: () -> Unit
) {
    val azulPrincipal = MaterialTheme.colorScheme.primary

    when (val result = state.loadResult) {
        RequestResult.Loading -> {
            LoadingIndicator()
            return
        }
        is RequestResult.Failure -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(result.message, style = MaterialTheme.typography.titleMedium)
                state.userErrors.values.forEach { error ->
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
                Button(onClick = onRetry) { Text("Reintentar") }
                TextButton(onClick = onSessionInvalid) { Text("Volver al inicio de sesión") }
            }
            return
        }
        RequestResult.Success -> Unit
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Fondo,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            HomeBottomBar(
                azulPrincipal = azulPrincipal,
                onAbrirMapa = onAbrirMapa,
                onAbrirMisEventos = onAbrirMisEventos,
                onAbrirPerfil = onAbrirPerfil
            )
        },
        floatingActionButton = {
            if (state.user?.role == UserRole.ORGANIZER) {
                ExtendedFloatingActionButton(
                    onClick = onCrearEvento,
                    icon = {
                        Text(
                            text = "+",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    text = {
                        Text(
                            text = "Crear evento",
                            fontSize = 12.sp
                        )
                    },
                    containerColor = azulPrincipal,
                    contentColor = Color.White
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier.padding(innerPadding)
        ) {
            HomeContent(
                state = state,
                userName = state.userName,
                userInitials = state.userInitials,
                onSearchRadiusChange = onSearchRadiusChange,
                onFilterChange = onFilterChange,
                onFavoriteClick = onFavoriteClick,
                onAbrirMapa = onAbrirMapa,
                onAbrirPerfil = onAbrirPerfil,
                onEventClick = onEventClick
            )
        }
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    userName: String,
    userInitials: String,
    onSearchRadiusChange: (Float) -> Unit,
    onFilterChange: (HomeFilter) -> Unit,
    onFavoriteClick: (Int) -> Unit,
    onAbrirMapa: () -> Unit,
    onAbrirPerfil: () -> Unit,
    onEventClick: (Int) -> Unit
) {
    val azulPrincipal = MaterialTheme.colorScheme.primary

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Fondo
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Hola, $userName",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextoPrincipal
                        )
                        Text(
                            text = "Descubre qué pasa cerca de ti",
                            fontSize = 12.sp,
                            color = TextoSecundario
                        )
                    }

                    IconButton(
                        onClick = {},
                        modifier = Modifier.size(36.dp)
                    ) {
                        IconoNotificaciones()
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(AzulClaro, CircleShape)
                            .clickable(onClick = onAbrirPerfil),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = userInitials,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = azulPrincipal
                        )
                    }
                }
            }

            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Tu dirección",
                        fontSize = 11.sp,
                        color = TextoSecundario
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp),
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = Gris
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = 12.dp,
                                vertical = 10.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconoUbicacion(
                                color = TextoSecundario,
                                modifier = Modifier.size(17.dp)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = state.address,
                                fontSize = 12.sp,
                                color = TextoPrincipal,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            IconoObjetivo(
                                color = TextoSecundario,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Radio de búsqueda",
                            fontSize = 11.sp,
                            color = TextoSecundario
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        Text(
                            text = "${state.searchRadiusKm.toInt()} km",
                            fontSize = 11.sp,
                            color = azulPrincipal,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Slider(
                        value = state.searchRadiusKm,
                        onValueChange = onSearchRadiusChange,
                        valueRange = 1f..10f,
                        steps = 8,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(30.dp)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HomeFilter.values().forEach { filter ->
                        FilterChip(
                            selected = state.selectedFilter == filter,
                            onClick = {
                                onFilterChange(filter)
                            },
                            label = {
                                Text(
                                    text = filter.label,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cerca de ti",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextoPrincipal
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = "Ver mapa",
                        fontSize = 11.sp,
                        color = azulPrincipal,
                        modifier = Modifier.clickable(onClick = onAbrirMapa)
                    )
                }
            }

            if (state.filteredEvents.isEmpty()) {
                item {
                    Text(
                        text = "No hay eventos que coincidan con estos filtros.",
                        fontSize = 13.sp,
                        color = TextoSecundario,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                    )
                }
            } else {
                items(
                    items = state.filteredEvents,
                    key = { event -> event.id }
                ) { event ->
                    EventCard(
                        event = event,
                        isFavorite = event.id in state.favoriteEventIds,
                        isConfirmed = event.id in state.confirmedEventIds,
                        onFavoriteClick = { onFavoriteClick(event.id) },
                        onEventClick = { onEventClick(event.id) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun EventCard(
    event: Event,
    isFavorite: Boolean,
    isConfirmed: Boolean,
    onFavoriteClick: () -> Unit,
    onEventClick: () -> Unit
) {
    val backgroundIcon = when (event.category) {
        EventCategory.SPORTS -> Color(0xFFD9ECFF)
        EventCategory.CULTURE -> Color(0xFFEEDFFF)
        EventCategory.COMMUNITY -> Color(0xFFDDEFEA)
    }

    val azulPrincipal = MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEventClick),
        shape = RoundedCornerShape(11.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConfirmed) Color(0xFFC8F6B7) else Color.White
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isConfirmed) Color(0xFF5F9E4B) else Color(0xFFE0E6EB)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        )
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(
                        color = backgroundIcon,
                        shape = RoundedCornerShape(9.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                EventCategoryIcon(
                    category = event.category,
                    modifier = Modifier.size(28.dp),
                    color = azulPrincipal
                )
            }

            Spacer(modifier = Modifier.width(9.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = event.title,
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextoPrincipal,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    IconButton(
                        onClick = onFavoriteClick,
                        modifier = Modifier.size(23.dp)
                    ) {
                        IconoCorazon(
                            isFavorite = isFavorite,
                            color = if (isFavorite) {
                                Color(0xFFD94D68)
                            } else {
                                TextoSecundario
                            },
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${event.dateLabel} • ${event.timeLabel}",
                        fontSize = 9.sp,
                        color = TextoSecundario,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconoUbicacion(
                        color = TextoSecundario,
                        modifier = Modifier.size(11.dp)
                    )

                    Spacer(modifier = Modifier.width(3.dp))

                    Text(
                        text = event.location,
                        fontSize = 9.sp,
                        color = TextoSecundario,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    EventTag(
                        text = "${formatDistance(event.distanceKm)} km",
                        background = Color(0xFFE4F0FF),
                        textColor = Color(0xFF255D8A)
                    )

                    EventTag(
                        text = formatPrice(event.price),
                        background = if (event.isFree) {
                            Color(0xFFDDF4D6)
                        } else {
                            Color(0xFFEDE2FF)
                        },
                        textColor = if (event.isFree) {
                            Color(0xFF26733A)
                        } else {
                            Color(0xFF6941A5)
                        }
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = "${event.attendeeCount} van",
                        fontSize = 8.sp,
                        color = TextoSecundario,
                        maxLines = 1
                    )

                    if (isConfirmed) {
                        Spacer(modifier = Modifier.width(4.dp))
                        IconoCheck(
                            color = Color(0xFF26733A),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EventTag(
    text: String,
    background: Color,
    textColor: Color
) {
    Surface(
        color = background,
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 8.sp,
            modifier = Modifier.padding(
                horizontal = 6.dp,
                vertical = 2.dp
            )
        )
    }
}

@Composable
private fun HomeBottomBar(
    azulPrincipal: Color,
    onAbrirMapa: () -> Unit,
    onAbrirMisEventos: () -> Unit,
    onAbrirPerfil: () -> Unit
) {
    NavigationBar(
        containerColor = Color.White
    ) {
        NavigationBarItem(
            selected = true,
            onClick = {},
            icon = {
                IconoNavegacionHome(
                    tipo = "Inicio",
                    selected = true,
                    color = azulPrincipal
                )
            },
            label = {
                Text("Inicio", fontSize = 9.sp)
            }
        )

        NavigationBarItem(
            selected = false,
            onClick = onAbrirMapa,
            icon = {
                IconoNavegacionHome(
                    tipo = "Mapa",
                    selected = false,
                    color = TextoSecundario
                )
            },
            label = {
                Text("Mapa", fontSize = 9.sp)
            }
        )

        NavigationBarItem(
            selected = false,
            onClick = onAbrirMisEventos,
            icon = {
                IconoNavegacionHome(
                    tipo = "Mis eventos",
                    selected = false,
                    color = TextoSecundario
                )
            },
            label = {
                Text("Mis eventos", fontSize = 9.sp)
            }
        )

        NavigationBarItem(
            selected = false,
            onClick = onAbrirPerfil,
            icon = {
                IconoNavegacionHome(
                    tipo = "Perfil",
                    selected = false,
                    color = TextoSecundario
                )
            },
            label = {
                Text("Perfil", fontSize = 9.sp)
            }
        )
    }
}

@Composable
private fun IconoNavegacionHome(
    tipo: String,
    selected: Boolean,
    color: Color
) {
    Canvas(
        modifier = Modifier.size(20.dp)
    ) {
        val strokeWidth = if (selected) 1.8.dp.toPx() else 1.5.dp.toPx()

        when (tipo) {
            "Inicio" -> {
                val house = Path().apply {
                    moveTo(size.width * 0.12f, size.height * 0.48f)
                    lineTo(size.width * 0.50f, size.height * 0.13f)
                    lineTo(size.width * 0.88f, size.height * 0.48f)
                    lineTo(size.width * 0.80f, size.height * 0.48f)
                    lineTo(size.width * 0.80f, size.height * 0.88f)
                    lineTo(size.width * 0.57f, size.height * 0.88f)
                    lineTo(size.width * 0.57f, size.height * 0.64f)
                    lineTo(size.width * 0.42f, size.height * 0.64f)
                    lineTo(size.width * 0.42f, size.height * 0.88f)
                    lineTo(size.width * 0.20f, size.height * 0.88f)
                    lineTo(size.width * 0.20f, size.height * 0.48f)
                    close()
                }
                drawPath(house, color, style = Stroke(width = strokeWidth))
            }

            "Mapa" -> {
                val mapPath = Path().apply {
                    moveTo(size.width * 0.10f, size.height * 0.22f)
                    lineTo(size.width * 0.38f, size.height * 0.10f)
                    lineTo(size.width * 0.65f, size.height * 0.22f)
                    lineTo(size.width * 0.90f, size.height * 0.10f)
                    lineTo(size.width * 0.90f, size.height * 0.78f)
                    lineTo(size.width * 0.65f, size.height * 0.90f)
                    lineTo(size.width * 0.38f, size.height * 0.78f)
                    lineTo(size.width * 0.10f, size.height * 0.90f)
                    close()
                }
                drawPath(mapPath, color, style = Stroke(width = strokeWidth))
                drawLine(color, Offset(size.width * 0.38f, size.height * 0.10f), Offset(size.width * 0.38f, size.height * 0.78f), strokeWidth)
                drawLine(color, Offset(size.width * 0.65f, size.height * 0.22f), Offset(size.width * 0.65f, size.height * 0.90f), strokeWidth)
            }

            "Mis eventos" -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * 0.18f, size.height * 0.16f),
                    size = Size(size.width * 0.64f, size.height * 0.72f),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                    style = Stroke(width = strokeWidth)
                )
                drawLine(color, Offset(size.width * 0.18f, size.height * 0.37f), Offset(size.width * 0.82f, size.height * 0.37f), strokeWidth)
                drawLine(color, Offset(size.width * 0.34f, size.height * 0.08f), Offset(size.width * 0.34f, size.height * 0.26f), strokeWidth)
                drawLine(color, Offset(size.width * 0.66f, size.height * 0.08f), Offset(size.width * 0.66f, size.height * 0.26f), strokeWidth)
                drawCircle(color, 1.dp.toPx(), Offset(size.width * 0.36f, size.height * 0.54f))
                drawCircle(color, 1.dp.toPx(), Offset(size.width * 0.60f, size.height * 0.54f))
            }

            else -> {
                drawCircle(
                    color = color,
                    radius = size.width * 0.18f,
                    center = Offset(size.width * 0.50f, size.height * 0.29f),
                    style = Stroke(width = strokeWidth)
                )
                drawArc(
                    color = color,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.18f, size.height * 0.39f),
                    size = Size(size.width * 0.64f, size.height * 0.50f),
                    style = Stroke(width = strokeWidth)
                )
            }
        }
    }
}

@Composable
private fun IconoNotificaciones() {
    Canvas(
        modifier = Modifier.size(20.dp)
    ) {
        val color = TextoPrincipal
        val strokeWidth = 1.5.dp.toPx()

        drawArc(
            color = color,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(size.width * 0.24f, size.height * 0.10f),
            size = Size(size.width * 0.52f, size.height * 0.70f),
            style = Stroke(width = strokeWidth)
        )
        drawLine(color, Offset(size.width * 0.20f, size.height * 0.72f), Offset(size.width * 0.80f, size.height * 0.72f), strokeWidth)
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(size.width * 0.40f, size.height * 0.67f),
            size = Size(size.width * 0.20f, size.height * 0.20f),
            style = Stroke(width = strokeWidth)
        )
    }
}

@Composable
private fun IconoUbicacion(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val pin = Path().apply {
            moveTo(size.width * 0.50f, size.height * 0.95f)
            cubicTo(
                size.width * 0.42f, size.height * 0.78f,
                size.width * 0.12f, size.height * 0.52f,
                size.width * 0.12f, size.height * 0.36f
            )
            cubicTo(
                size.width * 0.12f, size.height * 0.05f,
                size.width * 0.88f, size.height * 0.05f,
                size.width * 0.88f, size.height * 0.36f
            )
            cubicTo(
                size.width * 0.88f, size.height * 0.52f,
                size.width * 0.58f, size.height * 0.78f,
                size.width * 0.50f, size.height * 0.95f
            )
            close()
        }
        drawPath(pin, color, style = Stroke(width = 1.4.dp.toPx()))
        drawCircle(
            color = color,
            radius = size.minDimension * 0.12f,
            center = Offset(size.width * 0.50f, size.height * 0.34f),
            style = Stroke(width = 1.2.dp.toPx())
        )
    }
}

@Composable
private fun IconoObjetivo(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        drawCircle(color, radius = size.minDimension * 0.42f, style = Stroke(width = 1.4.dp.toPx()))
        drawCircle(color, radius = size.minDimension * 0.23f, style = Stroke(width = 1.4.dp.toPx()))
        drawCircle(color, radius = size.minDimension * 0.06f)
    }
}

@Composable
private fun IconoCheck(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.2f, size.height * 0.5f)
            lineTo(size.width * 0.45f, size.height * 0.75f)
            lineTo(size.width * 0.85f, size.height * 0.25f)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun EventCategoryIcon(
    category: EventCategory,
    modifier: Modifier = Modifier,
    color: Color
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 1.5.dp.toPx()

        when (category) {
            EventCategory.SPORTS -> {
                drawCircle(
                    color = color,
                    radius = size.width * 0.39f,
                    style = Stroke(width = strokeWidth)
                )
                drawLine(color, Offset(size.width * 0.23f, size.height * 0.20f), Offset(size.width * 0.72f, size.height * 0.78f), strokeWidth)
                drawLine(color, Offset(size.width * 0.18f, size.height * 0.69f), Offset(size.width * 0.78f, size.height * 0.29f), strokeWidth)
                drawCircle(color, size.width * 0.13f, Offset(size.width * 0.50f, size.height * 0.50f), style = Stroke(width = strokeWidth))
            }

            EventCategory.CULTURE -> {
                drawLine(color, Offset(size.width * 0.50f, size.height * 0.18f), Offset(size.width * 0.50f, size.height * 0.84f), strokeWidth)
                drawLine(color, Offset(size.width * 0.50f, size.height * 0.25f), Offset(size.width * 0.18f, size.height * 0.17f), strokeWidth)
                drawLine(color, Offset(size.width * 0.50f, size.height * 0.25f), Offset(size.width * 0.82f, size.height * 0.17f), strokeWidth)
                drawLine(color, Offset(size.width * 0.18f, size.height * 0.17f), Offset(size.width * 0.18f, size.height * 0.76f), strokeWidth)
                drawLine(color, Offset(size.width * 0.82f, size.height * 0.17f), Offset(size.width * 0.82f, size.height * 0.76f), strokeWidth)
                drawLine(color, Offset(size.width * 0.18f, size.height * 0.76f), Offset(size.width * 0.48f, size.height * 0.84f), strokeWidth)
                drawLine(color, Offset(size.width * 0.82f, size.height * 0.76f), Offset(size.width * 0.52f, size.height * 0.84f), strokeWidth)
            }

            EventCategory.COMMUNITY -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * 0.20f, size.height * 0.25f),
                    size = Size(size.width * 0.60f, size.height * 0.50f),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                    style = Stroke(width = strokeWidth)
                )
                drawLine(color, Offset(size.width * 0.32f, size.height * 0.44f), Offset(size.width * 0.68f, size.height * 0.44f), strokeWidth)
                drawLine(color, Offset(size.width * 0.32f, size.height * 0.56f), Offset(size.width * 0.58f, size.height * 0.56f), strokeWidth)
                drawLine(color, Offset(size.width * 0.50f, size.height * 0.12f), Offset(size.width * 0.50f, size.height * 0.25f), strokeWidth)
                drawLine(color, Offset(size.width * 0.44f, size.height * 0.19f), Offset(size.width * 0.50f, size.height * 0.12f), strokeWidth)
                drawLine(color, Offset(size.width * 0.56f, size.height * 0.19f), Offset(size.width * 0.50f, size.height * 0.12f), strokeWidth)
            }
        }
    }
}

@Composable
private fun IconoCorazon(
    isFavorite: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val heart = Path().apply {
            moveTo(size.width * 0.50f, size.height * 0.88f)
            cubicTo(
                size.width * 0.40f, size.height * 0.78f,
                size.width * 0.10f, size.height * 0.55f,
                size.width * 0.10f, size.height * 0.32f
            )
            cubicTo(
                size.width * 0.10f, size.height * 0.08f,
                size.width * 0.38f, size.height * 0.04f,
                size.width * 0.50f, size.height * 0.28f
            )
            cubicTo(
                size.width * 0.62f, size.height * 0.04f,
                size.width * 0.90f, size.height * 0.08f,
                size.width * 0.90f, size.height * 0.32f
            )
            cubicTo(
                size.width * 0.90f, size.height * 0.55f,
                size.width * 0.60f, size.height * 0.78f,
                size.width * 0.50f, size.height * 0.88f
            )
            close()
        }

        drawPath(
            path = heart,
            color = color,
            style = if (isFavorite) {
                androidx.compose.ui.graphics.drawscope.Fill
            } else {
                Stroke(width = 1.5.dp.toPx())
            }
        )
    }
}

private fun formatDistance(distanceKm: Double): String {
    return if (distanceKm % 1.0 == 0.0) {
        distanceKm.toInt().toString()
    } else {
        distanceKm.toString()
    }
}

private fun formatPrice(price: Int): String {
    if (price == 0) return "Gratis"

    return "\$${price.toString().reversed().chunked(3).joinToString(".").reversed()}"
}

@Preview(
    showBackground = true,
    showSystemUi = true,
    name = "Vista previa - Home"
)
@Composable
private fun HomeScreenPreview() {
    AppeventoscomunitariosTheme(
        darkTheme = false,
        dynamicColor = false
    ) {
        HomeScreenContent(
            state = HomeUiState(
                user = FakeUsers.users.first(),
                loadResult = RequestResult.Success
            ),
            onSearchRadiusChange = {},
            onFilterChange = {},
            onFavoriteClick = {},
            onAbrirMapa = {},
            onAbrirMisEventos = {},
            onAbrirPerfil = {},
            onCrearEvento = {},
            onEventClick = {},
            onRetry = {},
            onSessionInvalid = {}
        )
    }
}