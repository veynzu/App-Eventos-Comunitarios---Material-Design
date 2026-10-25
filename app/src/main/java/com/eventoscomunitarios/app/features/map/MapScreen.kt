package com.eventoscomunitarios.app.features.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventoscomunitarios.app.core.components.LoadingIndicator
import com.eventoscomunitarios.app.core.components.EventMap
import com.eventoscomunitarios.app.core.data.FakeEvents
import com.eventoscomunitarios.app.core.util.RequestResult
import com.eventoscomunitarios.app.domain.model.Event
import com.eventoscomunitarios.app.ui.theme.AppeventoscomunitariosTheme
import com.eventoscomunitarios.app.ui.theme.Fondo
import com.eventoscomunitarios.app.ui.theme.TextoPrincipal
import com.eventoscomunitarios.app.ui.theme.TextoSecundario

@Composable
fun MapScreen(
    viewModel: MapViewModel = viewModel(),
    onNavigateBack: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    onMyEventsClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onEventClick: (Int) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    MapContent(
        state = state,
        onAddressChange = viewModel::onAddressChange,
        onRadiusClick = viewModel::onRadiusClick,
        onFilterClick = viewModel::onFilterClick,
        onEventSelected = viewModel::onEventSelected,
        onCenterClick = viewModel::centerOnUser,
        onNavigateBack = onNavigateBack,
        onHomeClick = onHomeClick,
        onMyEventsClick = onMyEventsClick,
        onProfileClick = onProfileClick,
        onEventClick = onEventClick
    )
}

@Composable
private fun MapContent(
    state: MapUiState,
    onAddressChange: (String) -> Unit,
    onRadiusClick: () -> Unit,
    onFilterClick: (MapFilter) -> Unit,
    onEventSelected: (Int) -> Unit,
    onCenterClick: () -> Unit,
    onNavigateBack: () -> Unit,
    onHomeClick: () -> Unit,
    onMyEventsClick: () -> Unit,
    onProfileClick: () -> Unit,
    onEventClick: (Int) -> Unit
) {
    when (val result = state.loadResult) {
        RequestResult.Loading -> return LoadingIndicator()
        is RequestResult.Failure -> return Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(result.message, color = MaterialTheme.colorScheme.error)
        }
        RequestResult.Success -> Unit
    }

    Scaffold(
        containerColor = Fondo,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(false, onHomeClick, { Icon(Icons.Default.Home, "Inicio") }, modifier = Modifier.testTag("map_nav_home"), label = { Text("Inicio") })
                NavigationBarItem(true, {}, { Icon(Icons.Default.Map, "Mapa") }, modifier = Modifier.testTag("map_nav_map"), label = { Text("Mapa") })
                NavigationBarItem(false, onMyEventsClick, { Icon(Icons.Default.CalendarToday, "Mis eventos") }, modifier = Modifier.testTag("map_nav_events"), label = { Text("Mis eventos") })
                NavigationBarItem(false, onProfileClick, { Icon(Icons.Default.Person, "Perfil") }, modifier = Modifier.testTag("map_nav_profile"), label = { Text("Perfil") })
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás")
                }
                OutlinedTextField(
                    value = state.address,
                    onValueChange = onAddressChange,
                    modifier = Modifier.weight(1f).testTag("map_address"),
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    leadingIcon = { Icon(Icons.Default.LocationOn, null) }
                )
                IconButton(onClick = {}) { Icon(Icons.Default.Tune, "Filtros") }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = true, onClick = onRadiusClick, label = { Text("${state.radiusKm} km") })
                MapFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = filter in state.activeFilters,
                        onClick = { onFilterClick(filter) },
                        label = { Text(filter.label) }
                    )
                }
            }

            EventMap(
                events = state.visibleEvents,
                selectedEventId = state.selectedEventId,
                onEventSelected = onEventSelected,
                onCenterClick = onCenterClick
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Box(
                        Modifier.width(42.dp).height(4.dp).background(Color(0xFFBFC7CE), RoundedCornerShape(2.dp))
                            .align(Alignment.CenterHorizontally)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("${state.visibleEvents.size} eventos en esta zona", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
                Icon(Icons.Default.FilterAlt, "Ordenar")
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.visibleEvents, key = Event::id) { event ->
                    MapEventCard(
                        event = event,
                        selected = event.id == state.selectedEventId,
                        onSelect = { onEventSelected(event.id) },
                        onOpen = { onEventClick(event.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MapEventCard(event: Event, selected: Boolean, onSelect: () -> Unit, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect)
            .then(if (selected) Modifier.border(2.dp, Color(0xFF2B638B), RoundedCornerShape(16.dp)) else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFFD8EBFF) else Color.White)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(56.dp), color = Color(0xFFD8EBFF), shape = RoundedCornerShape(12.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.CalendarToday, null, tint = Color(0xFF2B638B)) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(event.title, fontWeight = FontWeight.SemiBold, color = TextoPrincipal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${event.distanceKm} km · ${event.dateLabel} ${event.timeLabel}", fontSize = 12.sp, color = TextoSecundario)
                Text(if (event.isFree) "Gratis" else "$${event.price}", fontSize = 12.sp, color = Color(0xFF25703A), fontWeight = FontWeight.Medium)
            }
            Button(onClick = onOpen) { Text("Ver") }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Mapa cliente")
@Composable
private fun MapScreenPreview() {
    AppeventoscomunitariosTheme(darkTheme = false, dynamicColor = false) {
        MapContent(
            state = MapUiState(userId = "participant-ana", events = FakeEvents.events, loadResult = RequestResult.Success),
            onAddressChange = {}, onRadiusClick = {}, onFilterClick = {}, onEventSelected = {},
            onCenterClick = {}, onNavigateBack = {}, onHomeClick = {}, onMyEventsClick = {},
            onProfileClick = {}, onEventClick = {}
        )
    }
}
