package com.eventoscomunitarios.app.features.map

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventoscomunitarios.app.core.data.FakeEvents
import com.eventoscomunitarios.app.core.util.RequestResult
import com.eventoscomunitarios.app.domain.model.AccountStatus
import com.eventoscomunitarios.app.domain.model.Event
import com.eventoscomunitarios.app.domain.model.EventModerationStatus
import com.eventoscomunitarios.app.domain.model.UserRole
import com.eventoscomunitarios.app.features.auth.FakeUsers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MapFilter(val label: String) {
    TODAY("Hoy"),
    OUTDOOR("Aire libre")
}

data class MapUiState(
    val userId: String = "",
    val address: String = "Barrio La Castilla",
    val radiusKm: Int = 5,
    val activeFilters: Set<MapFilter> = emptySet(),
    val events: List<Event> = emptyList(),
    val selectedEventId: Int? = null,
    val loadResult: RequestResult = RequestResult.Loading
) {
    val visibleEvents: List<Event>
        get() = events.filter { event ->
            event.moderationStatus == EventModerationStatus.APPROVED &&
                event.distanceKm <= radiusKm &&
                (MapFilter.TODAY !in activeFilters || event.id == 1) &&
                (MapFilter.OUTDOOR !in activeFilters || event.location.contains("Parque", true))
        }
}

class MapViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val userId: String = savedStateHandle["userId"] ?: ""
    private val _uiState = MutableStateFlow(MapUiState(userId = userId))
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            FakeEvents.eventsFlow.collect { events ->
                _uiState.update { state ->
                    val visibleIds = events
                        .filter { it.moderationStatus == EventModerationStatus.APPROVED }
                        .mapTo(mutableSetOf()) { it.id }
                    state.copy(
                        events = events,
                        selectedEventId = state.selectedEventId?.takeIf { it in visibleIds }
                    )
                }
            }
        }
    }

    fun onAddressChange(value: String) {
        _uiState.update { it.copy(address = value.take(60)) }
    }

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
        _uiState.update { it.copy(address = "Barrio La Castilla", radiusKm = 5) }
    }

    private fun load() {
        val user = FakeUsers.users.firstOrNull { it.id == userId }
        val valid = user != null && user.role != UserRole.ADMINISTRATOR &&
            user.accountStatus == AccountStatus.ACTIVE
        _uiState.update {
            it.copy(
                events = FakeEvents.events,
                loadResult = if (valid) RequestResult.Success
                else RequestResult.Failure("No tienes acceso al mapa de clientes")
            )
        }
    }
}
