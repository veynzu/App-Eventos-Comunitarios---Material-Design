package com.eventoscomunitarios.app.features.map

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventoscomunitarios.app.domain.usecase.EventUseCases
import com.eventoscomunitarios.app.domain.usecase.UserUseCases
import com.eventoscomunitarios.app.domain.usecase.LocationUseCases
import com.eventoscomunitarios.app.domain.model.Coordinates
import com.eventoscomunitarios.app.domain.model.SearchLocation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.eventoscomunitarios.app.domain.model.EventSearchOptions
import com.eventoscomunitarios.app.domain.model.EventCategory
import com.eventoscomunitarios.app.domain.model.EventDateField
import com.eventoscomunitarios.app.domain.usecase.filterEvents
import com.eventoscomunitarios.app.core.util.RequestResult
import com.eventoscomunitarios.app.domain.model.AccountStatus
import com.eventoscomunitarios.app.domain.model.Event
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

enum class MapFilter(val label: String) {
    TODAY("Hoy"),
    OUTDOOR("Aire libre")
}

data class MapUiState(
    val userId: String = "",
    val address: String = "Armenia, Quindío",
    val searchLocation: SearchLocation = SearchLocation(),
    val showLocationPicker: Boolean = false,
    val radiusKm: Int = 5,
    val searchOptions: EventSearchOptions = EventSearchOptions(),
    val selectedCategory: EventCategory? = null,
    val query: String = "",
    val showFilters: Boolean = false,
    val datePickerField: EventDateField? = null,
    val activeFilters: Set<MapFilter> = emptySet(),
    val events: List<Event> = emptyList(),
    val selectedEventId: Int? = null,
    val loadResult: RequestResult = RequestResult.Loading
) {
    val visibleEvents: List<Event>
        get() = filterEvents(events = events, origin = searchLocation.coordinates,
            radiusKm = radiusKm.toDouble(), options = searchOptions.copy(
                outdoorOnly = searchOptions.outdoorOnly || MapFilter.OUTDOOR in activeFilters),
            category = selectedCategory, query = query, todayOnly = MapFilter.TODAY in activeFilters,
            upcomingOnly = true)

}

@HiltViewModel
class MapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val eventUseCases: EventUseCases,
    private val userUseCases: UserUseCases,
    private val locations: LocationUseCases
) : ViewModel() {
    private val userId: String = savedStateHandle["userId"] ?: ""
    private val _uiState = MutableStateFlow(MapUiState(userId = userId))
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { locations.search.collect { point -> _uiState.update { it.copy(searchLocation = point, address = point.address, selectedEventId = null) } } }
        viewModelScope.launch { locations.initialize(userId) }
        load()
        viewModelScope.launch {
            combine(eventUseCases.events, eventUseCases.syncState, userUseCases.users, userUseCases.syncState) { _, _, _, _ -> Unit }
                .collect { load() }
        }
    }

    fun openLocationPicker() = _uiState.update { it.copy(showLocationPicker = true) }
    fun closeLocationPicker() = _uiState.update { it.copy(showLocationPicker = false) }
    fun selectSearchLocation(point: Coordinates, address: String) { locations.select(point, address, userId); closeLocationPicker() }

    fun onFiltersClick() = _uiState.update { it.copy(showFilters = !it.showFilters) }
    fun onRadiusChange(value: Float) = _uiState.update { it.copy(radiusKm = value.toInt().coerceIn(1, 100), selectedEventId = null) }
    fun onSearchOptionsChange(value: EventSearchOptions) = _uiState.update { it.copy(searchOptions = value, selectedEventId = null) }
    fun onCategoryChange(value: EventCategory?) = _uiState.update { it.copy(selectedCategory = value, selectedEventId = null) }
    fun onQueryChange(value: String) = _uiState.update { it.copy(query = value.take(100), selectedEventId = null) }
    fun onDatePickerChange(field: EventDateField?) = _uiState.update { it.copy(datePickerField = field) }
    fun onDateSelected(field: EventDateField, date: Long) = _uiState.update {
        it.copy(searchOptions = it.searchOptions.withDate(field, date), activeFilters = it.activeFilters - MapFilter.TODAY, selectedEventId = null)
    }
    fun resetFilters() = _uiState.update {
        it.copy(radiusKm = 5, searchOptions = EventSearchOptions(), selectedCategory = null, query = "",
            activeFilters = emptySet(), datePickerField = null, selectedEventId = null)
    }

    fun onRadiusClick() {
        _uiState.update { it.copy(radiusKm = if (it.radiusKm == 5) 10 else 5) }
    }

    fun onFilterClick(filter: MapFilter) {
        _uiState.update { state ->
            val filters = state.activeFilters.toMutableSet()
            if (!filters.add(filter)) filters.remove(filter)
            state.copy(activeFilters = filters, selectedEventId = null,
                searchOptions = if (filter == MapFilter.TODAY) state.searchOptions.copy(fromDate = null, untilDate = null) else state.searchOptions)
        }
    }

    fun onEventSelected(eventId: Int) {
        _uiState.update { state ->
            state.copy(selectedEventId = eventId.takeIf { it != state.selectedEventId })
        }
    }

    fun centerOnUser() {
        viewModelScope.launch { locations.useDeviceLocation() }
    }

    private fun load() {
        val user = userUseCases.findUser(userId)
        val valid = user != null && user.accountStatus == AccountStatus.ACTIVE
        val loading = eventUseCases.syncState.value.isLoading || userUseCases.syncState.value.isLoading
        val error = eventUseCases.syncState.value.error ?: userUseCases.syncState.value.error
        _uiState.update {
            it.copy(
                events = eventUseCases.events.value.map { event ->
                    userUseCases.findUser(event.organizerId)?.let { author -> event.copy(organizerName = author.name) } ?: event
                },
                loadResult = when {
                    loading -> RequestResult.Loading
                    error != null -> RequestResult.Failure(error)
                    valid -> RequestResult.Success
                    else -> RequestResult.Failure("No tienes acceso al mapa")
                }
            )
        }
        _uiState.update { state -> state.copy(selectedEventId = state.selectedEventId?.takeIf { id -> state.visibleEvents.any { it.id == id } }) }
    }
}
