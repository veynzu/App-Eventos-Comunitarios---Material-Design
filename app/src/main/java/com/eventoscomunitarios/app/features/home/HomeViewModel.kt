package com.eventoscomunitarios.app.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventoscomunitarios.app.core.data.FakeEvents
import com.eventoscomunitarios.app.core.util.RequestResult
import com.eventoscomunitarios.app.core.util.initials
import com.eventoscomunitarios.app.domain.model.User
import com.eventoscomunitarios.app.features.auth.FakeUsers
import com.eventoscomunitarios.app.features.auth.validationErrors
import com.eventoscomunitarios.app.domain.model.Event
import com.eventoscomunitarios.app.domain.model.EventCategory
import com.eventoscomunitarios.app.domain.model.EventRegistrationStatus
import com.eventoscomunitarios.app.domain.model.EventModerationStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

enum class HomeFilter(val label: String) {
    ALL("Todos"),
    TODAY("Hoy"),
    FREE("Gratis"),
    SPORTS("Deportes")
}

data class HomeUiState(
    val user: User? = null,
    val userErrors: Map<String, String> = emptyMap(),
    val loadResult: RequestResult = RequestResult.Loading,
    val address: String = "Cra 14 #12-45, Armenia",
    val searchRadiusKm: Float = 5f,
    val selectedFilter: HomeFilter = HomeFilter.ALL,
    val favoriteEventIds: Set<Int> = emptySet(),
    val confirmedEventIds: Set<Int> = emptySet(),
    val events: List<Event> = FakeEvents.events
) {
    val userName: String get() = user?.name.orEmpty()
    val userInitials: String get() = userName.initials()

    val filteredEvents: List<Event>
        get() = events.filter { event ->
            if (event.moderationStatus != EventModerationStatus.APPROVED) return@filter false
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

    private var currentUserId: String? = null
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(FakeEvents.eventsFlow, FakeEvents.registrationsFlow) { events, registrations ->
                events to registrations
            }.collect { (events, registrations) ->
                val userId = currentUserId
                _uiState.update { state ->
                    state.copy(
                        events = events,
                        confirmedEventIds = registrations
                            .filter {
                                it.userId == userId &&
                                    it.status == EventRegistrationStatus.CONFIRMED
                            }
                            .mapTo(mutableSetOf()) { it.eventId }
                    )
                }
            }
        }
    }

    fun loadUser(userId: String) {
        currentUserId = userId
        _uiState.update { it.copy(user = null, loadResult = RequestResult.Loading, userErrors = emptyMap()) }
        val user = FakeUsers.users.firstOrNull { it.id == userId }
        val errors = user?.validationErrors() ?: mapOf("user" to "No se encontró el usuario de esta sesión")
        _uiState.update {
            it.copy(
                user = user.takeIf { errors.isEmpty() },
                userErrors = errors,
                events = FakeEvents.events,
                confirmedEventIds = FakeEvents.registrations
                    .filter {
                        it.userId == userId &&
                            it.status == EventRegistrationStatus.CONFIRMED
                    }
                    .mapTo(mutableSetOf()) { it.eventId },
                loadResult = if (errors.isEmpty()) RequestResult.Success
                else RequestResult.Failure("No se puede cargar el perfil. Revisa los datos del usuario.")
            )
        }
    }

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
            if (eventId in updatedFavorites) updatedFavorites.remove(eventId)
            else updatedFavorites.add(eventId)
            state.copy(favoriteEventIds = updatedFavorites)
        }
    }

}
