package com.eventoscomunitarios.app.features.edit_event

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.eventoscomunitarios.app.core.data.FakeEvents
import com.eventoscomunitarios.app.core.util.RequestResult
import com.eventoscomunitarios.app.domain.model.EventCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class EditEventUiState(
    val userId: String = "",
    val eventId: Int? = null,
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
    val loadResult: RequestResult = RequestResult.Loading,
    val saveResult: RequestResult? = null
) {
    val isFormValid: Boolean
        get() = title.isNotBlank() && category.isNotBlank() && date.isNotBlank() &&
            time.isNotBlank() && address.isNotBlank() && capacity.isNotBlank() &&
            titleError == null && categoryError == null && dateError == null &&
            timeError == null && addressError == null && capacityError == null && priceError == null
}

class EditEventViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {

    private val userId: String = savedStateHandle["userId"] ?: ""
    private val eventId: Int? = savedStateHandle["eventId"]
    private val _uiState = MutableStateFlow(EditEventUiState(userId = userId, eventId = eventId))
    val uiState: StateFlow<EditEventUiState> = _uiState.asStateFlow()

    init {
        loadEvent()
    }

    fun onTitleChange(value: String) = update { copy(title = value, titleError = null, saveResult = null) }
    fun onCategoryChange(value: String) = update { copy(category = value, categoryError = null, saveResult = null) }
    fun onDateChange(value: String) = update { copy(date = value, dateError = null, saveResult = null) }
    fun onTimeChange(value: String) = update { copy(time = value, timeError = null, saveResult = null) }
    fun onAddressChange(value: String) = update { copy(address = value, addressError = null, saveResult = null) }
    fun onDescriptionChange(value: String) = update { copy(description = value, saveResult = null) }
    fun onCapacityChange(value: String) = update { copy(capacity = value.filter(Char::isDigit), capacityError = null, saveResult = null) }
    fun onPriceChange(value: String) = update { copy(price = value.filter(Char::isDigit), priceError = null, saveResult = null) }
    fun onPhotoSelected(uri: String?) = update { copy(photoUri = uri, saveResult = null) }

    fun onFreeChange(isFree: Boolean) {
        update { copy(isFree = isFree, price = if (isFree) "" else price, priceError = null, saveResult = null) }
    }

    fun saveChanges() {
        validateForm()
        val state = _uiState.value
        if (!state.isFormValid || state.saveResult == RequestResult.Loading) return
        val currentEvent = FakeEvents.events.firstOrNull { it.id == state.eventId }
        if (currentEvent == null || currentEvent.organizerId != state.userId) {
            _uiState.update { it.copy(saveResult = RequestResult.Failure("No tienes permiso para editar este evento")) }
            return
        }

        _uiState.update { it.copy(saveResult = RequestResult.Loading) }
        val updatedEvent = currentEvent.copy(
            title = state.title.trim(),
            category = parseCategory(state.category)!!,
            dateLabel = state.date,
            timeLabel = state.time,
            location = state.address.trim(),
            description = state.description.trim(),
            capacity = state.capacity.toInt(),
            price = if (state.isFree) 0 else state.price.toInt(),
            photoUri = state.photoUri
        )
        FakeEvents.events = FakeEvents.events.map { if (it.id == updatedEvent.id) updatedEvent else it }
        _uiState.update { it.copy(saveResult = RequestResult.Success) }
    }

    fun clearSaveResult() {
        _uiState.update { it.copy(saveResult = null) }
    }

    private fun loadEvent() {
        val event = FakeEvents.events.firstOrNull { it.id == eventId }
        _uiState.update { state ->
            when {
                event == null -> state.copy(loadResult = RequestResult.Failure("Evento no encontrado"))
                event.organizerId != userId -> state.copy(loadResult = RequestResult.Failure("No tienes permiso para editar este evento"))
                else -> state.copy(
                    title = event.title,
                    category = event.category.displayName,
                    date = event.dateLabel,
                    time = event.timeLabel,
                    address = event.location,
                    description = event.description,
                    capacity = event.capacity.toString(),
                    price = if (event.isFree) "" else event.price.toString(),
                    isFree = event.isFree,
                    photoUri = event.photoUri,
                    loadResult = RequestResult.Success
                )
            }
        }
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
        it.name.equals(value.trim(), ignoreCase = true) || it.displayName.equals(value.trim(), ignoreCase = true)
    }

    private inline fun update(transform: EditEventUiState.() -> EditEventUiState) {
        _uiState.update { it.transform() }
    }
}
