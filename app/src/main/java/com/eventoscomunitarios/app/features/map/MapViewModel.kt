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
import java.util.Calendar
import com.eventoscomunitarios.app.core.util.RequestResult
import com.eventoscomunitarios.app.domain.model.AccountStatus
import com.eventoscomunitarios.app.domain.model.Event
import com.eventoscomunitarios.app.domain.model.EventModerationStatus
import com.eventoscomunitarios.app.domain.model.UserRole
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
    val activeFilters: Set<MapFilter> = emptySet(),
    val events: List<Event> = emptyList(),
    val selectedEventId: Int? = null,
    val loadResult: RequestResult = RequestResult.Loading
) {
    val visibleEvents: List<Event>
        get() = events.map { event -> event.copy(distanceKm = event.coordinates?.distanceKmTo(searchLocation.coordinates) ?: Double.POSITIVE_INFINITY) }.filter { event ->
            event.moderationStatus == EventModerationStatus.APPROVED && !event.isFinished &&
                event.endsAt > System.currentTimeMillis() && event.distanceKm <= radiusKm &&
                (MapFilter.TODAY !in activeFilters || isToday(event)) &&
                (MapFilter.OUTDOOR !in activeFilters || event.isOutdoor)
        }

    private fun isToday(event: Event): Boolean {
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        return event.startsAt < end.timeInMillis && event.endsAt > start.timeInMillis
    }

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

    fun onRadiusClick() {
        _uiState.update { it.copy(radiusKm = if (it.radiusKm == 5) 10 else 5) }
    }

    fun onFilterClick(filter: MapFilter) {
        _uiState.update { state ->
            val filters = state.activeFilters.toMutableSet()
            if (!filters.add(filter)) filters.remove(filter)
            state.copy(activeFilters = filters, selectedEventId = null)
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
