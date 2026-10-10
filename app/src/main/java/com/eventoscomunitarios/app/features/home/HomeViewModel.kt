package com.eventoscomunitarios.app.features.home

import androidx.lifecycle.ViewModel
import com.eventoscomunitarios.app.core.data.FakeEvents
import com.eventoscomunitarios.app.domain.model.Event
import com.eventoscomunitarios.app.domain.model.EventCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class HomeFilter(val label: String) {
    ALL("Todos"),
    TODAY("Hoy"),
    FREE("Gratis"),
    SPORTS("Deportes")
}

data class HomeUiState(
    val userName: String = "Ana",
    val userInitials: String = "AT",
    val address: String = "Cra 14 #12-45, Armenia",
    val searchRadiusKm: Float = 5f,
    val selectedFilter: HomeFilter = HomeFilter.ALL,
    val favoriteEventIds: Set<Int> = emptySet(),
    val events: List<Event> = FakeEvents.events
) {
    val filteredEvents: List<Event>
        get() = events.filter { event ->
            val isWithinRadius = event.distanceKm <= searchRadiusKm.toDouble()
            val matchesFilter = when (selectedFilter) {
                HomeFilter.ALL -> true
                HomeFilter.TODAY -> event.dateLabel.startsWith("Hoy", ignoreCase = true)
                HomeFilter.FREE -> event.isFree
                HomeFilter.SPORTS -> event.category == EventCategory.SPORTS
            }

            isWithinRadius && matchesFilter
        }
}

class HomeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun onSearchRadiusChange(newRadiusKm: Float) {
        _uiState.update { state ->
            state.copy(searchRadiusKm = newRadiusKm)
        }
    }

    fun onFilterChange(newFilter: HomeFilter) {
        _uiState.update { state ->
            state.copy(selectedFilter = newFilter)
        }
    }

    fun onFavoriteClick(eventId: Int) {
        _uiState.update { state ->
            val updatedFavorites = state.favoriteEventIds.toMutableSet()

            if (eventId in updatedFavorites) {
                updatedFavorites.remove(eventId)
            } else {
                updatedFavorites.add(eventId)
            }

            state.copy(favoriteEventIds = updatedFavorites)
        }
    }
}
