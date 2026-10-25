package com.eventoscomunitarios.app.features.create_event

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.eventoscomunitarios.app.core.data.FakeEvents
import com.eventoscomunitarios.app.core.util.RequestResult
import com.eventoscomunitarios.app.domain.model.Event
import com.eventoscomunitarios.app.domain.model.EventCategory
import com.eventoscomunitarios.app.domain.model.EventModerationStatus
import com.eventoscomunitarios.app.domain.model.UserRole
import com.eventoscomunitarios.app.features.auth.FakeUsers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CreateEventUiState(
    val userId: String = "",
    val title: String = "",
    val category: String = "",
    val date: String = "",
    val time: String = "",
    val address: String = "",
    val description: String = "",
    val capacity: String = "",
    val price: String = "",
    val isFree: Boolean = true,
    val photoUri: String? = null,
    val titleError: String? = null,
    val categoryError: String? = null,
    val dateError: String? = null,
    val timeError: String? = null,
    val addressError: String? = null,
    val capacityError: String? = null,
    val priceError: String? = null,
    val publishResult: RequestResult? = null
) {
    val isFormValid: Boolean
        get() = title.isNotBlank() &&
            category.isNotBlank() &&
            date.isNotBlank() &&
            time.isNotBlank() &&
            address.isNotBlank() &&
            capacity.isNotBlank() &&
            titleError == null &&
            categoryError == null &&
            dateError == null &&
            timeError == null &&
            addressError == null &&
            capacityError == null &&
            priceError == null
}

class CreateEventViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CreateEventUiState(userId = savedStateHandle["userId"] ?: "")
    )
    val uiState: StateFlow<CreateEventUiState> = _uiState.asStateFlow()

    fun onTitleChange(value: String) = update { copy(title = value, titleError = null, publishResult = null) }
    fun onCategoryChange(value: String) = update { copy(category = value, categoryError = null, publishResult = null) }
    fun onDateChange(value: String) = update { copy(date = value, dateError = null, publishResult = null) }
    fun onTimeChange(value: String) = update { copy(time = value, timeError = null, publishResult = null) }
    fun onAddressChange(value: String) = update { copy(address = value, addressError = null, publishResult = null) }
    fun onDescriptionChange(value: String) = update { copy(description = value, publishResult = null) }
    fun onCapacityChange(value: String) = update { copy(capacity = value.filter(Char::isDigit), capacityError = null, publishResult = null) }
    fun onPriceChange(value: String) = update { copy(price = value.filter(Char::isDigit), priceError = null, publishResult = null) }
    fun onPhotoSelected(uri: String?) = update { copy(photoUri = uri, publishResult = null) }

    fun onFreeChange(isFree: Boolean) {
        update {
            copy(
                isFree = isFree,
                price = if (isFree) "" else price,
                priceError = null,
                publishResult = null
            )
        }
    }

    fun publishEvent() {
        validateForm()
        val state = _uiState.value
        if (!state.isFormValid || state.publishResult == RequestResult.Loading) return

        val organizer = FakeUsers.users.firstOrNull { it.id == state.userId }
        if (organizer == null || organizer.role != UserRole.ORGANIZER) {
            _uiState.update {
                it.copy(publishResult = RequestResult.Failure("Solo un organizador puede crear eventos"))
            }
            return
        }

        _uiState.update { it.copy(publishResult = RequestResult.Loading) }
        val event = Event(
            id = (FakeEvents.events.maxOfOrNull { it.id } ?: 0) + 1,
            title = state.title.trim(),
            dateLabel = state.date,
            timeLabel = state.time,
            location = state.address.trim(),
            distanceKm = 0.0,
            price = if (state.isFree) 0 else state.price.toInt(),
            category = parseCategory(state.category)!!,
            attendeeCount = 0,
            description = state.description.trim(),
            capacity = state.capacity.toInt(),
            organizerId = organizer.id,
            organizerName = organizer.name,
            photoUri = state.photoUri,
            moderationStatus = EventModerationStatus.PENDING,
            submittedAtLabel = "ahora"
        )
        FakeEvents.events = FakeEvents.events + event
        _uiState.update { it.copy(publishResult = RequestResult.Success) }
    }

    fun clearPublishResult() {
        _uiState.update { it.copy(publishResult = null) }
    }

    private fun validateForm() {
        _uiState.update { state ->
            state.copy(
                titleError = if (state.title.isBlank()) "El título es obligatorio" else null,
                categoryError = when {
                    state.category.isBlank() -> "La categoría es obligatoria"
                    parseCategory(state.category) == null -> "Usa Deportes, Cultura o Comunidad"
                    else -> null
                },
                dateError = if (state.date.isBlank()) "La fecha es obligatoria" else null,
                timeError = if (state.time.isBlank()) "La hora es obligatoria" else null,
                addressError = if (state.address.isBlank()) "La dirección es obligatoria" else null,
                capacityError = if ((state.capacity.toIntOrNull() ?: 0) <= 0) "Ingresa un cupo válido" else null,
                priceError = when {
                    state.isFree -> null
                    state.price.toIntOrNull() == null -> "Ingresa un precio válido"
                    state.price.toInt() <= 0 -> "El precio debe ser mayor que cero"
                    else -> null
                }
            )
        }
    }

    private fun parseCategory(value: String): EventCategory? = EventCategory.entries.firstOrNull {
        it.name.equals(value.trim(), ignoreCase = true) ||
            it.displayName.equals(value.trim(), ignoreCase = true)
    }

    private inline fun update(transform: CreateEventUiState.() -> CreateEventUiState) {
        _uiState.update { it.transform() }
    }
}
