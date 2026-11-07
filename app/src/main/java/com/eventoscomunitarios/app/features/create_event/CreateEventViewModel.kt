package com.eventoscomunitarios.app.features.create_event

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import com.eventoscomunitarios.app.core.util.RequestResult
import com.eventoscomunitarios.app.core.util.parseEventDateTime
import com.eventoscomunitarios.app.domain.model.Event
import com.eventoscomunitarios.app.domain.model.EventCategory
import com.eventoscomunitarios.app.domain.model.EventModerationStatus
import com.eventoscomunitarios.app.domain.usecase.EventUseCases
import com.eventoscomunitarios.app.domain.usecase.UserUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
    val endDate: String = "",
    val endTime: String = "",
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
    val endDateError: String? = null,
    val endTimeError: String? = null,
    val addressError: String? = null,
    val descriptionError: String? = null,
    val capacityError: String? = null,
    val priceError: String? = null,
    val publishResult: RequestResult? = null
) {
    val isFormValid: Boolean
        get() = title.isNotBlank() && category.isNotBlank() && date.isNotBlank() &&
            time.isNotBlank() && endDate.isNotBlank() && endTime.isNotBlank() &&
            address.isNotBlank() && description.isNotBlank() && capacity.isNotBlank() &&
            listOf(titleError, categoryError, dateError, timeError, endDateError, endTimeError,
                addressError, descriptionError, capacityError, priceError).all { it == null }
}

@HiltViewModel
class CreateEventViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val events: EventUseCases,
    private val users: UserUseCases
) : ViewModel() {
    private val userId: String = savedStateHandle["userId"] ?: ""
    private val _uiState = MutableStateFlow(CreateEventUiState(userId = userId))
    val uiState: StateFlow<CreateEventUiState> = _uiState.asStateFlow()

    fun onTitleChange(value: String) = editForm { copy(title = value, titleError = null, publishResult = null) }
    fun onCategoryChange(value: String) = editForm { copy(category = value, categoryError = null, publishResult = null) }
    fun onDateChange(value: String) = editForm { copy(date = value, dateError = null, publishResult = null) }
    fun onTimeChange(value: String) = editForm { copy(time = value, timeError = null, publishResult = null) }
    fun onEndDateChange(value: String) = editForm { copy(endDate = value, endDateError = null, publishResult = null) }
    fun onEndTimeChange(value: String) = editForm { copy(endTime = value, endTimeError = null, publishResult = null) }
    fun onAddressChange(value: String) = editForm { copy(address = value, addressError = null, publishResult = null) }
    fun onDescriptionChange(value: String) = editForm { copy(description = value, descriptionError = null, publishResult = null) }
    fun onCapacityChange(value: String) = editForm { copy(capacity = value.filter(Char::isDigit), capacityError = null, publishResult = null) }
    fun onPriceChange(value: String) = editForm { copy(price = value.filter(Char::isDigit), priceError = null, publishResult = null) }
    fun onPhotoSelected(uri: String?) = editForm { copy(photoUri = uri, publishResult = null) }
    fun onFreeChange(value: Boolean) = editForm {
        copy(isFree = value, price = if (value) "" else price, priceError = null, publishResult = null)
    }

    fun publishEvent() {
        if (_uiState.value.publishResult == RequestResult.Loading || _uiState.value.publishResult == RequestResult.Success) return
        validateForm()
        val state = _uiState.value
        if (!state.isFormValid) return
        val author = users.findUser(userId)
        if (author == null || !events.canPublish(userId)) {
            update { copy(publishResult = RequestResult.Failure("No tienes permiso para publicar eventos")) }
            return
        }
        update { copy(publishResult = RequestResult.Loading) }
        val event = Event(
            id = 0, title = state.title.trim(),
            startsAt = parseEventDateTime(state.date, state.time)!!,
            endsAt = parseEventDateTime(state.endDate, state.endTime)!!,
            location = state.address.trim(), distanceKm = 0.0,
            price = if (state.isFree) 0 else state.price.toInt(), category = parseCategory(state.category)!!,
            attendeeCount = 0, description = state.description.trim(), capacity = state.capacity.toInt(),
            organizerId = author.id, organizerName = author.name, photoUri = state.photoUri,
            moderationStatus = EventModerationStatus.PENDING, submittedAtLabel = "ahora"
        )
        viewModelScope.launch {
        events.create(userId, event).fold(
            onSuccess = { update { copy(publishResult = RequestResult.Success) } },
            onFailure = { error -> update { copy(publishResult = RequestResult.Failure(error.message ?: "No se pudo publicar el evento")) } }
        )
        }
    }

    fun clearPublishResult() = update { copy(publishResult = null) }

    private fun validateForm() {
        val now = System.currentTimeMillis()
        update {
            val start = parseEventDateTime(date, time)
            val end = parseEventDateTime(endDate, endTime)
            copy(
                titleError = if (title.isBlank()) "El título es obligatorio" else null,
                categoryError = if (parseCategory(category) == null) "Selecciona una categoría" else null,
                dateError = when {
                    start == null -> "Selecciona una fecha y hora válidas"
                    start <= now -> "El inicio debe estar en el futuro"
                    else -> null
                },
                timeError = if (time.isBlank()) "Selecciona la hora de inicio" else null,
                endDateError = when {
                    end == null -> "Selecciona una fecha y hora válidas"
                    start != null && end <= start -> "El fin debe ser posterior al inicio"
                    else -> null
                },
                endTimeError = if (endTime.isBlank()) "Selecciona la hora de fin" else null,
                addressError = if (address.isBlank()) "La dirección es obligatoria" else null,
                descriptionError = if (description.isBlank()) "Describe la actividad" else null,
                capacityError = if ((capacity.toIntOrNull() ?: 0) <= 0) "Ingresa un cupo válido" else null,
                priceError = if (!isFree && (price.toIntOrNull() ?: 0) <= 0) "Ingresa un precio mayor que cero" else null
            )
        }
    }

    private fun parseCategory(value: String): EventCategory? = EventCategory.entries.firstOrNull {
        it.name.equals(value.trim(), true) || it.displayName.equals(value.trim(), true)
    }

    /** Un callback del teclado o selector no puede habilitar otro envío mientras se guarda. */
    private inline fun editForm(transform: CreateEventUiState.() -> CreateEventUiState) {
        _uiState.update { state ->
            if (state.publishResult == RequestResult.Loading || state.publishResult == RequestResult.Success) state
            else state.transform()
        }
    }

    private inline fun update(transform: CreateEventUiState.() -> CreateEventUiState) {
        _uiState.update { it.transform() }
    }
}
