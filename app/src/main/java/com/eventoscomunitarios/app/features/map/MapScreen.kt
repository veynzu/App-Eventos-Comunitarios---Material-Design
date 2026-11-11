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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.eventoscomunitarios.app.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.eventoscomunitarios.app.core.components.LoadingIndicator
import com.eventoscomunitarios.app.core.components.EventMap
import com.eventoscomunitarios.app.core.util.localizedMessage
import com.eventoscomunitarios.app.core.util.RequestResult
import com.eventoscomunitarios.app.domain.model.Event
import com.eventoscomunitarios.app.ui.theme.AppeventoscomunitariosTheme
import com.eventoscomunitarios.app.ui.theme.Fondo
import androidx.compose.material3.TextButton
import com.eventoscomunitarios.app.core.components.EventSearchControls
import com.eventoscomunitarios.app.domain.model.EventCategory
import com.eventoscomunitarios.app.domain.model.EventSearchOptions
import com.eventoscomunitarios.app.domain.model.EventDateField
import com.eventoscomunitarios.app.core.components.SearchLocationControls
import com.eventoscomunitarios.app.features.locationpicker.LocationPickerDialog
import com.eventoscomunitarios.app.ui.theme.TextoPrincipal
import com.eventoscomunitarios.app.ui.theme.TextoSecundario

@Composable
fun MapScreen(
    viewModel: MapViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onEventClick: (Int) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    MapContent(
        state = state,
        onChooseLocation = viewModel::openLocationPicker,
        onRadiusClick = viewModel::onFiltersClick,
        onRadiusChange = viewModel::onRadiusChange,
        onSearchOptionsChange = viewModel::onSearchOptionsChange,
        onCategoryChange = viewModel::onCategoryChange,
        onQueryChange = viewModel::onQueryChange,
        onDatePickerChange = viewModel::onDatePickerChange,
        onDateSelected = viewModel::onDateSelected,
        onResetFilters = viewModel::resetFilters,
        onFilterClick = viewModel::onFilterClick,
        onEventSelected = viewModel::onEventSelected,
        onCenterClick = viewModel::centerOnUser,
        onNavigateBack = onNavigateBack,
        onEventClick = onEventClick
    )
    if (state.showLocationPicker) LocationPickerDialog(state.searchLocation.coordinates, state.searchLocation.address, viewModel::closeLocationPicker, viewModel::selectSearchLocation)
}

@Composable
private fun MapContent(
    state: MapUiState,
    onChooseLocation: () -> Unit,
    onRadiusClick: () -> Unit,
    onFilterClick: (MapFilter) -> Unit,
    onEventSelected: (Int) -> Unit,
    onCenterClick: () -> Unit,
    onNavigateBack: () -> Unit,
    onEventClick: (Int) -> Unit,
    onRadiusChange: (Float) -> Unit = {},
    onSearchOptionsChange: (EventSearchOptions) -> Unit = {},
    onCategoryChange: (EventCategory?) -> Unit = {},
    onQueryChange: (String) -> Unit = {},
    onDatePickerChange: (EventDateField?) -> Unit = {},
    onDateSelected: (EventDateField, Long) -> Unit = { _, _ -> },
    onResetFilters: () -> Unit = {}
) {
    when (val result = state.loadResult) {
        RequestResult.Loading -> return LoadingIndicator()
        is RequestResult.Failure -> return Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(localizedMessage(result.message), color = MaterialTheme.colorScheme.error)
        }
        RequestResult.Success -> Unit
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Fondo),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                Box(Modifier.weight(1f).testTag("map_address")) {
                    SearchLocationControls(state.searchLocation, onChooseLocation, onCenterClick)
                }
                IconButton(onClick = onRadiusClick) { Icon(Icons.Default.Tune, stringResource(R.string.search_show_filters)) }
            }
        }
        item {
            TextButton(onClick = onRadiusClick, modifier = Modifier.padding(horizontal = 12.dp).testTag("map_search_filters")) {
                Text(stringResource(if (state.showFilters) R.string.search_hide_filters else R.string.search_show_filters))
                Text(" · " + stringResource(R.string.search_radius, state.radiusKm))
            }
        }
        if (state.showFilters) item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                OutlinedTextField(value = state.query, onValueChange = onQueryChange,
                    label = { Text(stringResource(R.string.search_query)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                EventSearchControls(radiusKm = state.radiusKm.toFloat(), onRadiusChange = onRadiusChange,
                    options = state.searchOptions, onOptionsChange = onSearchOptionsChange,
                    category = state.selectedCategory, onCategoryChange = onCategoryChange,
                    todayOnly = MapFilter.TODAY in state.activeFilters,
                    onTodayClick = { onFilterClick(MapFilter.TODAY) },
                    datePickerField = state.datePickerField, onDatePickerChange = onDatePickerChange,
                    onDateSelected = onDateSelected, onReset = onResetFilters)
            }
        }
        item {
            EventMap(events = state.visibleEvents, selectedEventId = state.selectedEventId,
                onEventSelected = onEventSelected, onCenterClick = onCenterClick,
                center = state.searchLocation.coordinates, radiusKm = state.radiusKm)
        }
        item {
            Text(stringResource(R.string.search_events_in_area, state.visibleEvents.size),
                fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp))
        }
        if (state.visibleEvents.isEmpty()) item {
            Text(stringResource(R.string.search_empty), modifier = Modifier.padding(16.dp), color = TextoSecundario)
        }
        items(state.visibleEvents, key = Event::id) { event ->
            Box(Modifier.padding(horizontal = 16.dp)) {
                MapEventCard(event = event, selected = event.id == state.selectedEventId,
                    onSelect = { onEventSelected(event.id) }, onOpen = { onEventClick(event.id) })
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
                Text("${stringResource(R.string.event_distance_km, event.distanceKm)} · ${event.dateLabel} ${event.timeLabel}", fontSize = 12.sp, color = TextoSecundario)
                Text(if (event.isFree) stringResource(R.string.search_free) else "$${event.price}", fontSize = 12.sp, color = Color(0xFF25703A), fontWeight = FontWeight.Medium)
            }
            Button(onClick = onOpen) { Text(stringResource(R.string.search_view)) }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Mapa cliente")
@Composable
private fun MapScreenPreview() {
    AppeventoscomunitariosTheme(darkTheme = false, dynamicColor = false) {
        MapContent(
            state = MapUiState(userId = "participant-ana", events = emptyList(), loadResult = RequestResult.Success),
            onChooseLocation = {}, onRadiusClick = {}, onFilterClick = {}, onEventSelected = {},
            onCenterClick = {}, onNavigateBack = {}, onEventClick = {},
        )
    }
}
