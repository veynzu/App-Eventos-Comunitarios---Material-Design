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
import com.eventoscomunitarios.app.domain.model.Coordinates
import com.eventoscomunitarios.app.core.util.eventFormTime
import com.eventoscomunitarios.app.domain.usecase.EventUseCases
import com.eventoscomunitarios.app.domain.usecase.UserUseCases
import com.eventoscomunitarios.app.domain.usecase.ContentAnalysisUseCases
import com.eventoscomunitarios.app.domain.model.EventAssessment
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine

data class CreateEventUiState(
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
    val sourceEventId: Int? = null,
    val sourceReady: Boolean = true,
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
    val publishResult: RequestResult? = null,
    val isAnalyzing: Boolean = false,
    val assessment: EventAssessment? = null,
    val reviewedEvent: Event? = null,
    val showDuplicateDialog: Boolean = false
) {
    val isFormValid: Boolean
        get() = title.isNotBlank() && category.isNotBlank() && date.isNotBlank() &&
            time.isNotBlank() && endDate.isNotBlank() && endTime.isNotBlank() &&
            sourceReady && coordinates?.isValid == true && address.isNotBlank() && description.isNotBlank() && capacity.isNotBlank() &&
            listOf(titleError, categoryError, dateError, timeError, endDateError, endTimeError,
                addressError, descriptionError, capacityError, priceError).all { it == null }
}

@HiltViewModel
class CreateEventViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val events: EventUseCases,
    private val users: UserUseCases,
    private val analysis: ContentAnalysisUseCases
) : ViewModel() {
    private val userId: String = savedStateHandle["userId"] ?: ""
    private val sourceEventId: Int? = savedStateHandle["sourceEventId"]
    private val _uiState = MutableStateFlow(CreateEventUiState(userId = userId, sourceEventId = sourceEventId, sourceReady = sourceEventId == null))
    val uiState: StateFlow<CreateEventUiState> = _uiState.asStateFlow()

    init {
        if (sourceEventId != null) viewModelScope.launch {
            combine(events.events, events.syncState) { _, sync -> sync }.collect { sync ->
                if (_uiState.value.sourceReady || sync.isLoading) return@collect
                val source = events.findEvent(sourceEventId)
                if (source == null || !events.canRelaunch(userId, source)) {
                    update { copy(publishResult = RequestResult.Failure(sync.error ?: "No puedes volver a lanzar este evento")) }
                } else update { copy(sourceReady = true, title = source.title, category = source.category.displayName,
                    time = eventFormTime(source.startsAt), endTime = eventFormTime(source.endsAt),
                    address = source.location, coordinates = source.coordinates, description = source.description,
                    capacity = source.capacity.toString(), isFree = source.isFree, price = if (source.isFree) "" else source.price.toString(),
                    photoUri = source.photoUri, isOutdoor = source.isOutdoor, publishResult = null) }
            }
        }
    }

    fun openLocationPicker() = editForm { copy(showLocationPicker = true) }
    fun closeLocationPicker() = update { copy(showLocationPicker = false) }
    fun onLocationSelected(point: Coordinates, address: String) = editForm {
        copy(coordinates = point.takeIf { it.isValid }, address = address.trim(), addressError = null, showLocationPicker = false, publishResult = null)
    }

    fun onTitleChange(value: String) = editForm { copy(title = value, titleError = null, publishResult = null) }
    fun onCategoryChange(value: String) = editForm { copy(category = value, categoryError = null, publishResult = null) }
    fun onDateChange(value: String) = editForm { copy(date = value, dateError = null, publishResult = null) }
    fun onTimeChange(value: String) = editForm { copy(time = value, timeError = null, publishResult = null) }
    fun onEndDateChange(value: String) = editForm { copy(endDate = value, endDateError = null, publishResult = null) }
    fun onEndTimeChange(value: String) = editForm { copy(endTime = value, endTimeError = null, publishResult = null) }
    fun onAddressChange(value: String) = editForm { copy(address = value, coordinates = null, addressError = null, publishResult = null) }
    fun onDescriptionChange(value: String) = editForm { copy(description = value, descriptionError = null, publishResult = null) }
    fun onCapacityChange(value: String) = editForm { copy(capacity = value.filter(Char::isDigit), capacityError = null, publishResult = null) }
    fun onPriceChange(value: String) = editForm { copy(price = value.filter(Char::isDigit), priceError = null, publishResult = null) }
    fun onPhotoSelected(uri: String?) = editForm { copy(photoUri = uri, publishResult = null) }
    fun onFreeChange(value: Boolean) = editForm {
        copy(isFree = value, price = if (value) "" else price, priceError = null, publishResult = null)
    }
    fun onOutdoorChange(value: Boolean) = editForm { copy(isOutdoor = value, publishResult = null) }

    fun publishEvent(language: String = "es") {
        if (_uiState.value.publishResult == RequestResult.Loading || _uiState.value.publishResult == RequestResult.Success) return
        if (_uiState.value.reviewedEvent != null && _uiState.value.assessment?.possibleDuplicates?.isNotEmpty() == true) {
            update { copy(showDuplicateDialog = true) }; return
        }
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
            latitude = state.coordinates!!.latitude, longitude = state.coordinates.longitude, locationConfirmed = true,
            isOutdoor = state.isOutdoor,
            moderationStatus = EventModerationStatus.PENDING, submittedAtLabel = "ahora"
        )
        viewModelScope.launch {
            update { copy(isAnalyzing = true, assessment = null, reviewedEvent = null) }
            analysis.reviewEvent(userId, event, sourceEventId, language).fold(
                onSuccess = { review ->
                    when {
                        !review.text.allowed || review.image.relevant == false -> update {
                            copy(isAnalyzing = false, assessment = review, publishResult = null)
                        }
                        review.reviewId == null -> update {
                            copy(isAnalyzing = false, assessment = review, publishResult = RequestResult.Failure("No se recibió la validación de publicación. Inténtalo de nuevo."))
                        }
                        else -> {
                            val duplicates = review.possibleDuplicates.isNotEmpty()
                            update { copy(isAnalyzing = false, assessment = review,
                                reviewedEvent = event.copy(photoUri = review.approvedPhotoUri, aiReviewId = review.reviewId),
                                showDuplicateDialog = duplicates, publishResult = if (duplicates) null else RequestResult.Loading) }
                            if (!duplicates) saveReviewedEvent(false, fromAnalysis = true)
                        }
                    }
                },
                onFailure = { error -> update { copy(isAnalyzing = false, publishResult = RequestResult.Failure(error.message ?: "No se pudo completar el análisis. Conserva el borrador e inténtalo de nuevo.")) } }
            )
        }
    }

    fun confirmDifferentEvent() { update { copy(showDuplicateDialog = false) }; saveReviewedEvent(true) }
    fun dismissDuplicates() = update { copy(showDuplicateDialog = false) }
    fun useSuggestedDescription() { _uiState.value.assessment?.text?.suggestedText?.let(::onDescriptionChange) }

    private fun saveReviewedEvent(confirmed: Boolean, fromAnalysis: Boolean = false) {
        if ((_uiState.value.publishResult == RequestResult.Loading && !fromAnalysis) || _uiState.value.publishResult == RequestResult.Success) return
        val event = _uiState.value.reviewedEvent?.copy(aiDuplicateConfirmed = confirmed) ?: return
        update { copy(publishResult = RequestResult.Loading) }
        viewModelScope.launch {
        (if (sourceEventId == null) events.create(userId, event) else events.relaunch(userId, sourceEventId, event)).fold(
            onSuccess = { update { copy(publishResult = RequestResult.Success, assessment = null, reviewedEvent = null) } },
            onFailure = { error -> update { copy(assessment = null, reviewedEvent = null, publishResult = RequestResult.Failure(error.message ?: "No se pudo publicar el evento")) } }
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
    private inline fun editForm(transform: CreateEventUiState.() -> CreateEventUiState) {
        val before = _uiState.value
        _uiState.update { state ->
            if (!state.sourceReady || state.publishResult == RequestResult.Loading || state.publishResult == RequestResult.Success) state
            else state.transform().copy(assessment = null, reviewedEvent = null, showDuplicateDialog = false)
        }
        if (before.assessment != null && _uiState.value.assessment == null) viewModelScope.launch { analysis.discard(before.assessment) }
    }

    private inline fun update(transform: CreateEventUiState.() -> CreateEventUiState) {
        _uiState.update { it.transform() }
    }
}
