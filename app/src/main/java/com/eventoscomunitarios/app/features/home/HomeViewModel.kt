package com.eventoscomunitarios.app.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventoscomunitarios.app.core.util.RequestResult
import com.eventoscomunitarios.app.core.util.initials
import com.eventoscomunitarios.app.domain.model.AccountStatus
import com.eventoscomunitarios.app.domain.model.Event
import com.eventoscomunitarios.app.domain.model.EventCategory
import com.eventoscomunitarios.app.domain.model.EventRegistrationStatus
import com.eventoscomunitarios.app.domain.model.User
import com.eventoscomunitarios.app.domain.usecase.CommunityUseCases
import com.eventoscomunitarios.app.domain.usecase.EventUseCases
import com.eventoscomunitarios.app.domain.usecase.UserUseCases
import com.eventoscomunitarios.app.domain.usecase.LocationUseCases
import com.eventoscomunitarios.app.domain.model.Coordinates
import com.eventoscomunitarios.app.domain.model.SearchLocation
import com.eventoscomunitarios.app.features.auth.validationErrors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.eventoscomunitarios.app.domain.model.EventSearchOptions
import com.eventoscomunitarios.app.domain.model.EventDateField
import com.eventoscomunitarios.app.domain.usecase.filterEvents
import javax.inject.Inject

enum class HomeFilter(val label: String) {
    ALL("Todos"), TODAY("Hoy"), FREE("Gratis"), FAVORITES("Favoritos")
}

data class HomeUiState(
    val user: User? = null,
    val userErrors: Map<String, String> = emptyMap(),
    val loadResult: RequestResult = RequestResult.Loading,
    val address: String = "Armenia, Quindío",
    val searchLocation: SearchLocation = SearchLocation(),
    val showLocationPicker: Boolean = false,
    val searchRadiusKm: Float = 5f,
    val searchOptions: EventSearchOptions = EventSearchOptions(),
    val datePickerField: EventDateField? = null,
    val selectedFilter: HomeFilter = HomeFilter.ALL,
    val selectedCategory: EventCategory? = null,
    val query: String = "",
    val canCreateEvent: Boolean = false,
    val unreadNotifications: Int = 0,
    val syncError: String? = null,
    val isSaving: Boolean = false,
    val favoriteEventIds: Set<Int> = emptySet(),
    val confirmedEventIds: Set<Int> = emptySet(),
    val events: List<Event> = emptyList()
) {
    val userName: String get() = user?.name.orEmpty()
    val userInitials: String get() = userName.initials()

    val filteredEvents: List<Event>
        get() = filterEvents(
            events = events,
            origin = searchLocation.coordinates,
            radiusKm = searchRadiusKm.toDouble(),
            options = searchOptions.copy(freeOnly = searchOptions.freeOnly || selectedFilter == HomeFilter.FREE),
            category = selectedCategory,
            query = query,
            todayOnly = selectedFilter == HomeFilter.TODAY,
            favoriteIds = favoriteEventIds.takeIf { selectedFilter == HomeFilter.FAVORITES }
        )
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val events: EventUseCases,
    private val users: UserUseCases,
    private val community: CommunityUseCases,
    private val locations: LocationUseCases
) : ViewModel() {
    private var currentUserId: String? = null
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { locations.search.collect { point -> _uiState.update { it.copy(searchLocation = point, address = point.address) } } }
        viewModelScope.launch {
            merge(events.events.map { Unit }, events.registrations.map { Unit }, users.users.map { Unit },
                community.favorites.map { Unit }, community.notifications.map { Unit }, events.syncState.map { Unit },
                users.syncState.map { Unit }, community.syncState.map { Unit }).collect { refresh() }
        }
    }

    fun loadUser(userId: String) {
        currentUserId = userId
        viewModelScope.launch { locations.initialize(userId) }
        refresh()
    }

    private fun refresh() {
        val userId = currentUserId ?: return
        if (users.syncState.value.isLoading || events.syncState.value.isLoading) {
            _uiState.update { it.copy(loadResult = RequestResult.Loading) }
            return
        }
        val user = users.findUser(userId)
        val errors = user?.validationErrors()?.toMutableMap()
            ?: mutableMapOf("user" to "No se encontró el usuario")
        if (user?.accountStatus == AccountStatus.SUSPENDED) errors["status"] = "La cuenta está suspendida"
        _uiState.update {
            it.copy(
                user = user.takeIf { errors.isEmpty() },
                userErrors = errors,
                events = events.events.value.map { event ->
                    users.findUser(event.organizerId)?.let { author -> event.copy(organizerName = author.name) } ?: event
                },
                syncError = events.syncState.value.error ?: users.syncState.value.error ?: community.syncState.value.error,
                canCreateEvent = events.canPublish(userId),
                favoriteEventIds = events.events.value.filter { event -> community.favorites.value.any { it.userId == userId && it.eventId == event.seriesId } }
                    .mapTo(mutableSetOf()) { event -> event.id },
                unreadNotifications = community.notifications.value.count { notification -> notification.userId == userId && !notification.isRead },
                confirmedEventIds = events.registrations.value.filter { registration ->
                    registration.userId == userId && registration.status == EventRegistrationStatus.CONFIRMED
                }.mapTo(mutableSetOf()) { registration -> registration.eventId },
                loadResult = if (errors.isEmpty()) RequestResult.Success else RequestResult.Failure("No se puede cargar el perfil")
            )
        }
    }

    fun onSearchRadiusChange(value: Float) = _uiState.update { it.copy(searchRadiusKm = value.coerceIn(1f, 100f)) }
    fun onSearchOptionsChange(value: EventSearchOptions) = _uiState.update { it.copy(searchOptions = value) }
    fun onDatePickerChange(field: EventDateField?) = _uiState.update { it.copy(datePickerField = field) }
    fun onDateSelected(field: EventDateField, date: Long) = _uiState.update {
        it.copy(searchOptions = it.searchOptions.withDate(field, date),
            selectedFilter = if (it.selectedFilter == HomeFilter.TODAY) HomeFilter.ALL else it.selectedFilter)
    }
    fun onTodayClick() = _uiState.update {
        it.copy(selectedFilter = if (it.selectedFilter == HomeFilter.TODAY) HomeFilter.ALL else it.selectedFilter,
            searchOptions = it.searchOptions.copy(fromDate = null, untilDate = null,
                todayOnly = !(it.searchOptions.todayOnly || it.selectedFilter == HomeFilter.TODAY)))
    }
    fun resetFilters() = _uiState.update {
        it.copy(searchRadiusKm = 5f, searchOptions = EventSearchOptions(), selectedFilter = HomeFilter.ALL,
            selectedCategory = null, query = "", datePickerField = null)
    }
    fun openLocationPicker() = _uiState.update { it.copy(showLocationPicker = true) }
    fun closeLocationPicker() = _uiState.update { it.copy(showLocationPicker = false) }
    fun selectSearchLocation(point: Coordinates, address: String) { locations.select(point, address, currentUserId); closeLocationPicker() }
    fun useDeviceLocation() { viewModelScope.launch { locations.useDeviceLocation() } }
    fun onFilterChange(value: HomeFilter) = _uiState.update { it.copy(selectedFilter = value) }
    fun onCategoryChange(value: EventCategory?) = _uiState.update { it.copy(selectedCategory = value) }
    fun onQueryChange(value: String) = _uiState.update { it.copy(query = value.take(100)) }
    fun onFavoriteClick(eventId: Int) {
        val id = currentUserId ?: return
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = community.toggleFavorite(id, eventId)
            refresh()
            _uiState.update { it.copy(isSaving = false, syncError = result.exceptionOrNull()?.message ?: it.syncError) }
        }
    }
}
