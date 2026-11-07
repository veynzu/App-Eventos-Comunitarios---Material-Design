package com.eventoscomunitarios.app.features.edit_event

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import com.eventoscomunitarios.app.core.util.RequestResult
import com.eventoscomunitarios.app.core.util.parseEventDateTime
import com.eventoscomunitarios.app.core.util.eventFormDate
import com.eventoscomunitarios.app.core.util.eventFormTime
import com.eventoscomunitarios.app.domain.model.Event
import com.eventoscomunitarios.app.domain.model.EventCategory
import com.eventoscomunitarios.app.domain.model.EventModerationStatus
import com.eventoscomunitarios.app.domain.model.Coordinates
import com.eventoscomunitarios.app.domain.usecase.EventUseCases
import com.eventoscomunitarios.app.domain.usecase.UserUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine

data class EditEventUiState(
    val userId: String = "",
    val title: String = "",
    val category: String = "",
    val date: String = "",
    val time: String = "",
    val endDate: String = "",
    val endTime: String = "",
    val address: String = "",
    val coordinates: Coordinates? = null,
    val showLocationPicker: Boolean = false,
    val description: String = "",
    val capacity: String = "",
    val price: String = "",
    val isFree: Boolean = true,
    val isOutdoor: Boolean = false,
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
    val eventId: Int? = null,
    val loadResult: RequestResult = RequestResult.Loading,
    val rejectionReason: String? = null,
    val saveResult: RequestResult? = null
) {
    val isFormValid: Boolean
        get() = title.isNotBlank() && category.isNotBlank() && date.isNotBlank() &&
            time.isNotBlank() && endDate.isNotBlank() && endTime.isNotBlank() &&
            coordinates?.isValid == true && address.isNotBlank() && description.isNotBlank() && capacity.isNotBlank() &&
            listOf(titleError, categoryError, dateError, timeError, endDateError, endTimeError,
                addressError, descriptionError, capacityError, priceError).all { it == null }
}

@HiltViewModel
class EditEventViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val events: EventUseCases,
    private val users: UserUseCases
) : ViewModel() {
    private val userId: String = savedStateHandle["userId"] ?: ""
    private val eventId: Int? = savedStateHandle["eventId"]
    private val _uiState = MutableStateFlow(EditEventUiState(userId = userId, eventId = eventId))
    val uiState: StateFlow<EditEventUiState> = _uiState.asStateFlow()

    private var formLoaded = false
    init {
        loadEvent()
        viewModelScope.launch {
            combine(events.events, events.syncState, users.users, users.syncState) { _, _, _, _ -> Unit }.collect { loadEvent() }
        }
    }

    fun onTitleChange(value: String) = editForm { copy(title = value, titleError = null, saveResult = null) }
    fun onCategoryChange(value: String) = editForm { copy(category = value, categoryError = null, saveResult = null) }
    fun onDateChange(value: String) = editForm { copy(date = value, dateError = null, saveResult = null) }
    fun onTimeChange(value: String) = editForm { copy(time = value, timeError = null, saveResult = null) }
    fun onEndDateChange(value: String) = editForm { copy(endDate = value, endDateError = null, saveResult = null) }
    fun onEndTimeChange(value: String) = editForm { copy(endTime = value, endTimeError = null, saveResult = null) }
    fun onAddressChange(value: String) = editForm { copy(address = value, coordinates = null, addressError = null, saveResult = null) }
    fun openLocationPicker() = editForm { copy(showLocationPicker = true) }
    fun closeLocationPicker() = update { copy(showLocationPicker = false) }
    fun onLocationSelected(point: Coordinates, address: String) = editForm {
        copy(coordinates = point.takeIf { it.isValid }, address = address.trim(), addressError = null, showLocationPicker = false, saveResult = null)
    }
    fun onDescriptionChange(value: String) = editForm { copy(description = value, descriptionError = null, saveResult = null) }
    fun onCapacityChange(value: String) = editForm { copy(capacity = value.filter(Char::isDigit), capacityError = null, saveResult = null) }
    fun onPriceChange(value: String) = editForm { copy(price = value.filter(Char::isDigit), priceError = null, saveResult = null) }
    fun onPhotoSelected(uri: String?) = editForm { copy(photoUri = uri, saveResult = null) }
    fun onFreeChange(value: Boolean) = editForm {
        copy(isFree = value, price = if (value) "" else price, priceError = null, saveResult = null)
    }
    fun onOutdoorChange(value: Boolean) = editForm { copy(isOutdoor = value, saveResult = null) }

    fun saveChanges() {
        if (_uiState.value.saveResult == RequestResult.Loading || _uiState.value.saveResult == RequestResult.Success) return
        validateForm()
        val state = _uiState.value
        if (!state.isFormValid) return
        val current = state.eventId?.let(events::findEvent)
        if (current == null || !events.canManage(userId, current)) {
            update { copy(saveResult = RequestResult.Failure("No tienes permiso para editar este evento")) }
            return
        }
        update { copy(saveResult = RequestResult.Loading) }
        val updated = current.copy(
            title = state.title.trim(),
            category = parseCategory(state.category)!!,
            startsAt = parseEventDateTime(state.date, state.time)!!,
            endsAt = parseEventDateTime(state.endDate, state.endTime)!!,
            location = state.address.trim(),
            latitude = state.coordinates!!.latitude, longitude = state.coordinates.longitude, locationConfirmed = true,
            isOutdoor = state.isOutdoor,
            description = state.description.trim(),
            capacity = state.capacity.toInt(),
            price = if (state.isFree) 0 else state.price.toInt(),
            photoUri = state.photoUri
        )
        viewModelScope.launch {
        events.update(userId, updated).fold(
            onSuccess = { update { copy(saveResult = RequestResult.Success) } },
            onFailure = { error -> update { copy(saveResult = RequestResult.Failure(error.message ?: "No se pudieron guardar los cambios")) } }
        )
        }
    }

    fun clearSaveResult() = update { copy(saveResult = null) }

    private fun loadEvent() {
        val syncError = events.syncState.value.error ?: users.syncState.value.error
        if (events.syncState.value.isLoading || users.syncState.value.isLoading) {
            if (!formLoaded) update { copy(loadResult = RequestResult.Loading) }
            return
        }
        if (syncError != null) {
            update { copy(loadResult = RequestResult.Failure(syncError)) }
            return
        }
        val event = eventId?.let(events::findEvent)
        update {
            when {
                event == null -> copy(loadResult = RequestResult.Failure("Evento no encontrado"))
                !events.canManage(userId, event) -> copy(loadResult = RequestResult.Failure("No tienes permiso para editar este evento"))
                event.isFinished || event.startsAt <= System.currentTimeMillis() ->
                    copy(loadResult = RequestResult.Failure("Solo se pueden editar eventos que aún no han iniciado"))
                formLoaded -> copy(loadResult = RequestResult.Success)
                else -> copy(
                    title = event.title, category = event.category.displayName,
                    date = eventFormDate(event.startsAt), time = eventFormTime(event.startsAt),
                    endDate = eventFormDate(event.endsAt), endTime = eventFormTime(event.endsAt),
                    address = event.location, coordinates = event.coordinates, description = event.description,
                    capacity = event.capacity.toString(), price = if (event.isFree) "" else event.price.toString(),
                    isFree = event.isFree, photoUri = event.photoUri, isOutdoor = event.isOutdoor,
                    rejectionReason = event.rejectionReason, loadResult = RequestResult.Success
                )
            }
        }
        if (_uiState.value.loadResult == RequestResult.Success) formLoaded = true
    }

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
                addressError = if (coordinates?.isValid != true || address.isBlank()) "Elige y confirma la ubicación en el mapa" else null,
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
    private inline fun editForm(transform: EditEventUiState.() -> EditEventUiState) {
        _uiState.update { state ->
            if (state.saveResult == RequestResult.Loading || state.saveResult == RequestResult.Success) state
            else state.transform()
        }
    }

    private inline fun update(transform: EditEventUiState.() -> EditEventUiState) {
        _uiState.update { it.transform() }
    }
}
